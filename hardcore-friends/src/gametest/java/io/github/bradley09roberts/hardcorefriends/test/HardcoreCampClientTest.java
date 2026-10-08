package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Long-run test in a separate Hardcore world with normal terrain, mobs and a running day cycle. The player sets a
 * camp, links a chest stocked with a modest early-game kit, recruits all nine friends with food through chat
 * commands, and then watches from a sealed glass box while the server runs several in-game days at sprint speed.
 *
 * <p>Checks: player-made builds near the camp are untouched, and every block change friends made stayed inside its
 * allowed zone. Everything else (what got built, harvested, mined, who survived) is recorded honestly in the report.
 */
@SuppressWarnings("UnstableApiUsage")
public class HardcoreCampClientTest implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hardcorefriends.soakDays", 3);
	private static final int CHUNK = 6000;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("hardcorefriends.skipCamp")) {
			return; // lets the quick skin test run on its own
		}
		context.getInput().resizeWindow(1600, 900);
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
				s.setSeed("hardcore-friends-camp");
			})
			.create()) {
			context.waitTicks(100);
			Report report = new Report("client-hardcore-camp-life");
			report.check("World is Hardcore", world.getServer().computeOnServer(MinecraftServer::isHardcore));
			report.check("Cheats disabled", !context.computeOnClient(mc -> mc.getSingleplayerServer().getWorldData().isAllowCommands()));

			ConcurrentLinkedQueue<WorldEditGuard.EditEvent> edits = new ConcurrentLinkedQueue<>();
			WorldEditGuard.listener = edits::add;
			Map<BlockPos, BlockState> fixtures = new LinkedHashMap<>();

			BlockPos camp = world.getServer().computeOnServer(server -> setUp(server, fixtures));
			report.note("Camp at " + camp.toShortString() + "; " + fixtures.size() + " player-built fixture blocks recorded");

			context.runOnClient(mc -> mc.player.connection.sendCommand("friends camp set"));
			context.waitTicks(10);
			for (FriendId id : FriendId.values()) {
				context.runOnClient(mc -> mc.player.connection.sendCommand("friends recruit " + id.key()));
				context.waitTicks(4);
			}
			context.waitTicks(20);
			report.check("Nine friends recruited via chat commands", world.getServer().computeOnServer(s -> Companions.all().size()) == 9);
			report.check("Supply chest linked by /friends camp set",
				world.getServer().computeOnServer(s -> Camp.data(s).chestPos().isPresent()));

			// Watch from a sealed glass box above the camp so the observer cannot die during the run.
			world.getServer().runOnServer(server -> moveObserver(server, camp));
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(60);
			context.takeScreenshot(TestScreenshotOptions.of("camp-start").withSize(1600, 900).disableCounterPrefix());

			List<String> timeline = new ArrayList<>();
			int chunks = DAYS * 24000 / CHUNK;
			long realStart = System.currentTimeMillis();
			for (int i = 1; i <= chunks; i++) {
				// The test harness steps the server in lockstep with this software-rendered client, so draw fewer
				// frames while sprinting. View and simulation distance stay at 7 chunks so the whole gathering ring
				// (about 88 blocks) keeps ticking.
				context.runOnClient(mc -> {
					mc.options.framerateLimit().set(10);
					mc.options.renderDistance().set(7);
					mc.options.simulationDistance().set(7);
				});
				world.getServer().runOnServer(server -> server.tickRateManager().requestGameToSprint(CHUNK));
				world.getServer().waitFor(server -> !server.tickRateManager().isSprinting(), ClientGameTestContext.NO_TIMEOUT);
				context.runOnClient(mc -> mc.options.framerateLimit().set(60));
				context.waitTicks(40);
				String line = world.getServer().computeOnServer(server -> status(server));
				timeline.add(String.format("%.2f days: %s", i * CHUNK / 24000.0, line));
				System.out.println("[HardcoreFriendsTest] " + timeline.getLast());
				if (i % 2 == 0 || i == chunks) {
					context.takeScreenshot(TestScreenshotOptions.of(String.format("camp-day-%.1f", i * CHUNK / 24000.0))
						.withSize(1600, 900).disableCounterPrefix());
				}
			}
			long realSeconds = (System.currentTimeMillis() - realStart) / 1000;

			// A daytime ground-level view of the camp.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD)
					.ifPresent(h -> server.clockManager().setTotalTicks(h, level.getOverworldClockTime() / 24000 * 24000 + 24000 + 5000));
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, camp.offset(0, 0, -14));
				p.connection.teleport(ground.getX() + 0.5, ground.getY() + 2.5, ground.getZ() + 0.5, 0.0F, 18.0F);
			});
			context.waitTicks(80);
			context.takeScreenshot(TestScreenshotOptions.of("camp-ground-view").withSize(1600, 900).disableCounterPrefix());
			world.getServer().runOnServer(server -> moveObserver(server, camp));

			// --- Checks ---
			int changed = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				int n = 0;
				for (Map.Entry<BlockPos, BlockState> e : fixtures.entrySet()) {
					if (!level.getBlockState(e.getKey()).equals(e.getValue())) {
						n++;
						System.out.println("[HardcoreFriendsTest] fixture changed at " + e.getKey().toShortString()
							+ ": " + e.getValue() + " -> " + level.getBlockState(e.getKey()));
					}
				}
				return n;
			});
			report.check("All " + fixtures.size() + " player-built fixture blocks are untouched", changed == 0);

			int maxCamp = FriendsConfig.get().maxCampRadius;
			int ring = maxCamp + FriendsConfig.get().resourceRadius;
			AtomicInteger outOfZone = new AtomicInteger();
			EnumMap<WorldEditGuard.Reason, Integer> byReason = new EnumMap<>(WorldEditGuard.Reason.class);
			for (WorldEditGuard.EditEvent e : edits) {
				byReason.merge(e.reason(), 1, Integer::sum);
				double d = Math.sqrt(Camp.horizontalDistSqr(camp, e.pos()));
				boolean campOnly = switch (e.reason()) {
					case BUILD, INVENT, LANDSCAPE -> true;
					case FARM -> !e.state().is(Blocks.SWEET_BERRY_BUSH);
					default -> false;
				};
				double limit = campOnly ? maxCamp + 0.5 : ring + 0.5;
				if (d > limit) {
					outOfZone.incrementAndGet();
					System.out.println("[HardcoreFriendsTest] out-of-zone edit: " + e.reason() + " " + e.verb() + " at " + e.pos().toShortString());
				}
			}
			WorldEditGuard.listener = null;
			report.check("All " + edits.size() + " friend block changes stayed inside their zones (camp " + maxCamp + ", gathering ring " + ring + ")",
				outOfZone.get() == 0);
			report.note("Block changes by reason: " + byReason);
			report.note("Simulated " + DAYS + " in-game days in " + realSeconds + " s of real time (sprint mode, software rendering).");
			for (String t : timeline) {
				report.note(t);
			}
			String finalState = world.getServer().computeOnServer(server -> finalSummary(server));
			report.note(finalState);
			report.write();
			report.assertAllPassed();
		} finally {
			WorldEditGuard.listener = null;
		}
	}

	private static BlockPos setUp(MinecraftServer server, Map<BlockPos, BlockState> fixtures) {
		ServerLevel level = server.overworld();
		ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
		BlockPos camp = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
		level.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD)
			.ifPresent(h -> server.clockManager().setTotalTicks(h, 1000));

		// Supply chest the player stocked with an early-game kit.
		BlockPos chestPos = surface(level, camp.east(2));
		level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
		Container chest = SupplyChest.at(level, chestPos).orElseThrow();
		for (ItemStack s : List.of(new ItemStack(Items.OAK_LOG, 24), new ItemStack(Items.COBBLESTONE, 48), new ItemStack(Items.COAL, 12),
			new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.BREAD, 8), new ItemStack(Items.WATER_BUCKET),
			new ItemStack(Items.STICK, 16), new ItemStack(Items.GLASS, 6), new ItemStack(Items.IRON_INGOT, 6))) {
			SupplyChest.insert(chest, s);
		}

		// Player-made things the friends must leave alone: a little plank hut with a door and a chest, a cobblestone
		// wall inside the camp, and a log wall out in the gathering ring.
		BlockPos hut = surface(level, camp.offset(9, 0, 7));
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				for (int dy = 0; dy < 3; dy++) {
					boolean wall = dx == 0 || dx == 2 || dz == 0 || dz == 2 || dy == 2;
					BlockPos q = hut.offset(dx, dy, dz);
					if (wall && !(dx == 1 && dz == 0 && dy < 2)) {
						fixture(level, fixtures, q, Blocks.SPRUCE_PLANKS.defaultBlockState());
					} else if (!wall) {
						fixture(level, fixtures, q, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		fixture(level, fixtures, hut.offset(1, 0, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
		fixture(level, fixtures, hut.offset(1, 1, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
			.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		fixture(level, fixtures, hut.offset(1, 0, 1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		BlockPos wall = surface(level, camp.offset(-12, 0, -9));
		for (int i = 0; i < 5; i++) {
			BlockPos q = surface(level, wall.east(i));
			fixture(level, fixtures, q, Blocks.COBBLESTONE.defaultBlockState());
			fixture(level, fixtures, q.above(), Blocks.COBBLESTONE.defaultBlockState());
		}
		BlockPos logs = surface(level, camp.offset(34, 0, 3));
		for (int i = 0; i < 4; i++) {
			fixture(level, fixtures, logs.above(i), Blocks.OAK_LOG.defaultBlockState());
			fixture(level, fixtures, logs.east().above(i), Blocks.OAK_LOG.defaultBlockState());
		}

		p.connection.teleport(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5, 0.0F, 0.0F);
		p.getInventory().add(new ItemStack(Items.BREAD, 18));
		return camp;
	}

	private static BlockPos surface(ServerLevel level, BlockPos pos) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
	}

	private static void fixture(ServerLevel level, Map<BlockPos, BlockState> fixtures, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 3);
		fixtures.put(pos.immutable(), level.getBlockState(pos));
	}

	private static void moveObserver(MinecraftServer server, BlockPos camp) {
		ServerLevel level = server.overworld();
		BlockPos box = camp.above(16).north(12);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos q = box.offset(dx, dy, dz);
					boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == 2;
					level.setBlock(q, shell ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
		p.connection.teleport(box.getX() + 0.5, box.getY(), box.getZ() + 0.5, 0.0F, 52.0F);
		p.getFoodData().setFoodLevel(20);
	}

	private static String status(MinecraftServer server) {
		CampData data = Camp.data(server);
		int alive = Companions.all().size();
		ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
		p.getFoodData().setFoodLevel(20); // the observer is not the subject of this test
		return "stage " + Camp.stageName(data.stage()) + ", unity " + data.unity() + " (" + Unity.levelName(Unity.level(data.unity())) + ")"
			+ ", friends alive " + alive + ", built " + data.completed();
	}

	private static String finalSummary(MinecraftServer server) {
		CampData data = Camp.data(server);
		StringBuilder sb = new StringBuilder("Final: ").append(status(server)).append(". Stats: ").append(data.stats()).append(". Friends: ");
		for (FriendId id : FriendId.values()) {
			CampData.Ledger l = data.ledger(id);
			sb.append(id.displayName()).append('=').append(l.state);
			if (l.state == CampData.LifeState.DEAD) {
				sb.append(" (").append(l.deathCause).append(')');
			}
			Companions.find(id).ifPresent(c -> sb.append(String.format(" %.0fhp [%s]", c.getHealth(), c.activity())));
			sb.append("; ");
		}
		return sb.toString();
	}
}
