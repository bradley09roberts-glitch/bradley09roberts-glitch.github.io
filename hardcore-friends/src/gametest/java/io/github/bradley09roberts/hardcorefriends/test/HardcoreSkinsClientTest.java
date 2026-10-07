package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;

/**
 * In-game test in a fresh Hardcore world (Hardcore forces cheats off). Recruits all nine friends with food using
 * real chat commands sent by the player, screenshots their skins from the front and back, checks that a friend's
 * death drops their backpack, and finally checks that the player's own Hardcore permadeath still works.
 */
@SuppressWarnings("UnstableApiUsage")
public class HardcoreSkinsClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
			.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE))
			.create()) {
			context.waitTicks(40);
			Report report = new Report("client-hardcore-skins");

			boolean hardcore = world.getServer().computeOnServer(MinecraftServer::isHardcore);
			boolean commandsAllowed = context.computeOnClient(mc -> mc.getSingleplayerServer().getWorldData().isAllowCommands());
			report.check("World is Hardcore", hardcore);
			report.check("Cheats/commands disabled in this world", !commandsAllowed);

			// The player stocks up on bread the normal way (simulated by giving it): 9 friends x 2 = 18.
			world.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				p.getInventory().add(new ItemStack(Items.BREAD, 10));
				p.getInventory().add(new ItemStack(Items.APPLE, 8));
				p.getInventory().add(new ItemStack(Items.CARROT, 1));
				ServerLevel level = p.level();
				level.registryAccess().get(WorldClocks.OVERWORLD).ifPresent(h -> server.clockManager().setTotalTicks(h, 6000));
			});

			// A vanilla cheat command must be refused for this non-op player.
			context.runOnClient(mc -> mc.player.connection.sendCommand("time set midnight"));
			context.waitTicks(10);
			long timeAfterCheat = world.getServer().computeOnServer(server -> server.overworld().getOverworldClockTime() % 24000);
			report.check("Vanilla cheat command (/time set) refused without cheats", timeAfterCheat < 12000);

			for (FriendId id : FriendId.values()) {
				context.runOnClient(mc -> mc.player.connection.sendCommand("friends recruit " + id.key()));
				context.waitTicks(5);
			}
			context.waitTicks(20);
			int recruited = world.getServer().computeOnServer(server -> Companions.all().size());
			int foodLeft = world.getServer().computeOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				return p.getInventory().countItem(Items.BREAD) + p.getInventory().countItem(Items.APPLE) + p.getInventory().countItem(Items.CARROT);
			});
			report.check("All nine friends recruited with /friends (no cheats)", recruited == 9);
			report.check("Recruiting cost exactly 18 common food (19 -> 1 left)", foodLeft == 1);

			// One more recruit of the same friend must be refused.
			context.runOnClient(mc -> mc.player.connection.sendCommand("friends recruit fern"));
			context.waitTicks(5);
			report.check("Duplicate recruit refused", world.getServer().computeOnServer(server -> Companions.all().size()) == 9);

			// Line them up facing the camera for skin screenshots.
			world.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				BlockPos base = p.blockPosition();
				p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() - 7.5, 0.0F, 8.0F);
				List<CompanionEntity> all = sorted();
				for (int i = 0; i < all.size(); i++) {
					CompanionEntity c = all.get(i);
					c.setMode(CompanionMode.STAY, null);
					c.snapTo(base.getX() + 0.5 + (i - 4) * 1.6, base.getY(), base.getZ() + 0.5, 180.0F, 0.0F);
					c.setYHeadRot(180.0F);
					c.setYBodyRot(180.0F);
					c.setNoAi(true);
				}
			});
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(60);
			context.takeScreenshot(TestScreenshotOptions.of("friends-front").withSize(1600, 900).disableCounterPrefix());

			world.getServer().runOnServer(server -> {
				for (CompanionEntity c : sorted()) {
					c.setYRot(0.0F);
					c.setYHeadRot(0.0F);
					c.setYBodyRot(0.0F);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("friends-back").withSize(1600, 900).disableCounterPrefix());

			// Close-ups in three groups of three, front view.
			for (int group = 0; group < 3; group++) {
				int g = group;
				world.getServer().runOnServer(server -> {
					List<CompanionEntity> all = sorted();
					ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
					CompanionEntity middle = all.get(g * 3 + 1);
					for (CompanionEntity c : all) {
						c.setYRot(180.0F);
						c.setYHeadRot(180.0F);
						c.setYBodyRot(180.0F);
					}
					p.snapTo(middle.getX(), middle.getY(), middle.getZ() - 3.2, 0.0F, 12.0F);
				});
				context.waitTicks(20);
				context.takeScreenshot(TestScreenshotOptions.of("friends-closeup-" + (group + 1)).withSize(1200, 900).disableCounterPrefix());
			}
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});

			// A friend can die: their backpack drops and the team mourns.
			int unityBefore = world.getServer().computeOnServer(server -> Camp.data(server).unity());
			boolean backpackDropped = world.getServer().computeOnServer(server -> {
				CompanionEntity fern = Companions.find(FriendId.FERN).orElseThrow();
				fern.setNoAi(false);
				fern.backpack().insert(new ItemStack(Items.WHEAT_SEEDS, 12));
				ServerLevel level = (ServerLevel) fern.level();
				AABB box = fern.getBoundingBox().inflate(3);
				fern.kill(level);
				return !level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ModItems.BACKPACK)).isEmpty();
			});
			context.waitTicks(20);
			report.check("Fallen friend drops a backpack item", backpackDropped);
			CampData.LifeState fernState = world.getServer().computeOnServer(server -> Camp.data(server).ledger(FriendId.FERN).state);
			report.check("Ledger records Fern as fallen", fernState == CampData.LifeState.DEAD);
			context.runOnClient(mc -> mc.player.connection.sendCommand("friends recruit fern"));
			context.waitTicks(5);
			report.check("Fallen friend cannot be recruited straight back (mourning period)",
				Companions.find(FriendId.FERN).isEmpty());
			report.note("Unity before/after Fern's death: " + unityBefore + " -> "
				+ world.getServer().computeOnServer(server -> Camp.data(server).unity()));

			// Player permadeath must be unchanged: Game Over screen, then spectator.
			world.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				p.kill(p.level());
			});
			context.waitForScreen(DeathScreen.class);
			context.waitTicks(30);
			String title = context.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
			report.check("Death screen title is 'Game Over!' (Hardcore)", "Game Over!".equals(title));
			context.takeScreenshot(TestScreenshotOptions.of("hardcore-game-over").withSize(1280, 720).disableCounterPrefix());
			context.clickScreenButton("deathScreen.spectate");
			context.waitTicks(40);
			GameType mode = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().gameMode());
			report.check("After death the player becomes a spectator (no extra life)", mode == GameType.SPECTATOR);
			report.write();
			report.assertAllPassed();
		}
	}

	private static List<CompanionEntity> sorted() {
		List<CompanionEntity> all = new ArrayList<>(Companions.all());
		all.sort((a, b) -> Integer.compare(a.friendId().ordinal(), b.friendId().ordinal()));
		return all;
	}
}
