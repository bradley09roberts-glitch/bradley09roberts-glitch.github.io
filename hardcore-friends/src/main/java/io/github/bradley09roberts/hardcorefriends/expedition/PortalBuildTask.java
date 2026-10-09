package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.BlueprintTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The camp's Nether portal. Once Sage's plan says the camp is ready for the Nether (10 obsidian and a flint and steel),
 * the builder puts up the portal's frame through the ordinary blueprint system ({@link Blueprints#NETHER_PORTAL}: real
 * obsidian from the chest, a proper site in the camp, the guard's {@code BUILD} rules), then fetches the flint and
 * steel and lights it by setting fire in the opening, which is what makes a portal for a player too. Everyone on the
 * server hears that it is lit. It is lit once: a portal that goes out later is the players' to relight. Not built when
 * the players already go through a portal of their own at the camp, and only in the overworld or the Nether, where
 * portals work. Checked at most every {@value #CHECK_INTERVAL} ticks.
 */
public final class PortalBuildTask extends BlueprintTask {
	static final String ID = "oak.nether_portal";
	private static final int CHECK_INTERVAL = 200;
	private static final int LIGHT_TIME = 20 * 60;

	private long checkedAt = Long.MIN_VALUE / 2;
	private boolean wanted;
	private boolean lighting;
	private boolean fetched;
	private int lightTicks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return lighting ? "lighting the Nether portal" : super.describe();
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.BUILD;
	}

	@Override
	protected double baseScore() {
		return 50;
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now - checkedAt >= CHECK_INTERVAL || now < checkedAt) {
			checkedAt = now;
			wanted = wanted(c, level, data);
		}
		return wanted && !isSetAside(c, Structures.NETHER_PORTAL) ? Blueprints.NETHER_PORTAL : null;
	}

	/** Whether there is a portal to build or light now (see the class description). */
	private static boolean wanted(CompanionEntity c, ServerLevel level, CampData data) {
		MinecraftServer server = level.getServer();
		if (!ProgressPlan.enabled() || !ProgressPlan.isDone(server, Milestone.NETHER_READY)
			|| level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) {
			return false;
		}
		ExpeditionData expeditions = ExpeditionData.get(server);
		if (expeditions.campPortalLit() || data.campPos().isEmpty()) {
			return false;
		}
		Container chest = SupplyChest.of(level).orElse(null);
		if (data.isCompleted(Structures.NETHER_PORTAL)) {
			return hasFlintAndSteel(c, chest); // built: only the lighting is left
		}
		// A portal the players already use at the camp will do.
		BlockPos centre = data.campPos().get();
		int near = Camp.radius(data) + 16;
		if (expeditions.wayBetween(Travel.dimId(level), centre, Level.NETHER.identifier().toString(), near) != null) {
			return false;
		}
		Map<Stock, Integer> need = new EnumMap<>(Stock.class);
		if (data.site(Structures.NETHER_PORTAL).isPresent()) {
			for (Placement p : BuildJob.missing(level, data, Blueprints.NETHER_PORTAL, 32)) {
				Stock s = p.entry().material().stock();
				if (s != null) {
					need.merge(s, 1, Integer::sum);
				}
			}
		} else {
			for (Blueprint.Entry e : Blueprints.NETHER_PORTAL.entries()) {
				Stock s = e.material().stock();
				if (s != null) {
					need.merge(s, 1, Integer::sum);
				}
			}
		}
		for (Map.Entry<Stock, Integer> e : need.entrySet()) {
			if (!Supplies.canMake(c, chest, e.getKey(), e.getValue())) {
				return false;
			}
		}
		return hasFlintAndSteel(c, chest);
	}

	private static boolean hasFlintAndSteel(CompanionEntity c, @Nullable Container chest) {
		return c.actions().has(s -> s.is(Items.FLINT_AND_STEEL))
			|| chest != null && SupplyChest.count(chest, s -> s.is(Items.FLINT_AND_STEEL)) > 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		checkedAt = Long.MIN_VALUE / 2; // look again with fresh eyes
		CampData data = Camp.data(c.level().getServer());
		lighting = data.isCompleted(Structures.NETHER_PORTAL);
		fetched = false;
		lightTicks = 0;
		if (lighting) {
			return choose(c, data) != null;
		}
		return super.start(c);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		return lighting ? light(c) : super.tick(c);
	}

	@Override
	public void stop(CompanionEntity c) {
		super.stop(c);
		c.actions().reset();
		lighting = false;
	}

	@Override
	public int failureCooldown() {
		return lighting ? 1200 : super.failureCooldown();
	}

	// ---------------------------------------------------------------- lighting

	/** Fetches the flint and steel, walks up to the frame and sets fire in its opening. */
	private TaskStatus light(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		Optional<CampData.Site> site = data.site(Structures.NETHER_PORTAL);
		if (site.isEmpty() || ++lightTicks > LIGHT_TIME) {
			return TaskStatus.FAILURE;
		}
		BlockPos fire = Blueprints.at(site.get(), Blueprints.PORTAL_FIRE);
		if (!level.isLoaded(fire)) {
			return TaskStatus.FAILURE;
		}
		if (level.getBlockState(fire).is(Blocks.NETHER_PORTAL)) {
			lit(c, level, fire, false); // somebody lit it already
			return TaskStatus.SUCCESS;
		}
		if (!BuildJob.missing(level, data, Blueprints.NETHER_PORTAL, 1).isEmpty()) {
			return TaskStatus.FAILURE; // the frame has a gap: it cannot hold a portal
		}
		if (!openingClear(level, site.get())) {
			Speech.say(c, Line.NEED_MATERIALS, "the portal's opening cleared");
			return TaskStatus.FAILURE;
		}
		if (!c.actions().has(s -> s.is(Items.FLINT_AND_STEEL))) {
			if (fetched) {
				return TaskStatus.FAILURE;
			}
			switch (ChestWalk.tick(c)) {
				case ARRIVED -> {
					ChestWalk.chest(c).ifPresent(chest -> SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.FLINT_AND_STEEL), 1));
					fetched = true;
				}
				case FAILED -> {
					return TaskStatus.FAILURE;
				}
				case WALKING -> {
				}
			}
			return TaskStatus.RUNNING;
		}
		if (!c.actions().canReach(fire)) {
			c.actions().walkTo(fire, 2.5);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING;
		}
		ItemStack held = c.getMainHandItem();
		Item before = held.isEmpty() || held.is(Items.FLINT_AND_STEEL) ? null : held.getItem();
		if (!c.actions().equip(s -> s.is(Items.FLINT_AND_STEEL))) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(fire));
		BlockState flame = BaseFireBlock.getState(level, fire);
		boolean placed = WorldEditGuard.placeBlock(c, fire, flame, WorldEditGuard.Reason.BUILD);
		if (placed) {
			c.swingArm();
			level.playSound(null, fire, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, c.getRandom().nextFloat() * 0.4F + 0.8F);
			c.damageMainHandTool(1);
		}
		if (before != null) {
			c.actions().equip(s -> s.is(before));
		}
		if (placed && level.getBlockState(fire).is(Blocks.NETHER_PORTAL)) {
			lit(c, level, fire, true);
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.FAILURE;
	}

	/** The 2×3 opening holds nothing but air (or fire). */
	private static boolean openingClear(ServerLevel level, CampData.Site site) {
		for (int dx = 1; dx <= 2; dx++) {
			for (int dy = 1; dy <= 3; dy++) {
				BlockPos p = Blueprint.worldPos(site.origin, site.rotation, dx, dy, 0);
				BlockState s = level.getBlockState(p);
				if (!s.isAir() && !s.is(net.minecraft.tags.BlockTags.FIRE)) {
					return false;
				}
			}
		}
		return true;
	}

	/** The portal is lit: remembered, said, and everyone on the server hears where. */
	private static void lit(CompanionEntity c, ServerLevel level, BlockPos fire, boolean byUs) {
		ExpeditionData data = ExpeditionData.get(level.getServer());
		data.setCampPortalLit(true);
		data.addPlace(ExpeditionData.CAMP_PORTAL, Travel.dimId(level), fire, level.getGameTime());
		if (byUs) {
			Speech.say(c, Line.PORTAL_LIT);
			Speech.announce(level.getServer(), Component.literal(c.displayName() + " has lit a Nether portal at the camp ("
				+ fire.getX() + " " + fire.getY() + " " + fire.getZ() + "). Take friends along with /friends party.")
				.withStyle(ChatFormatting.GOLD));
		}
	}
}
