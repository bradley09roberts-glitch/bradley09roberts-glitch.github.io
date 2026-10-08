package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.CampProgress;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Terra plants greenery with saplings and small flowers from the backpack or the supply chest. Saplings go on a ring
 * near the camp edge (between radius − 6 and radius − 2), at least 5 blocks from each other, paths, fields and
 * building sites. Flowers go in small clusters a few blocks from finished buildings and the supply chest. With a
 * Settlement, 12 flowers and 6 trees make the flower gardens.
 */
public final class PlantTask implements CompanionTask {
	private static final int MAX_TREES = 12;
	private static final int MAX_FLOWERS = 24;
	private static final int GARDEN_FLOWERS = 12;
	private static final int GARDEN_TREES = 6;
	private static final int TREES_PER_RUN = 2;
	private static final int CLUSTER = 4;
	private static final double TREE_SPACING = 5;
	private static final int CHECK_INTERVAL = 200;
	private static final double WORK_REACH = 2.5;

	private static final Predicate<ItemStack> SAPLING = s -> s.is(ItemTags.SAPLINGS) && s.getItem() instanceof BlockItem;
	private static final Predicate<ItemStack> FLOWER = s -> s.is(BlockItemTags.SMALL_FLOWERS.item()) && !s.is(Items.WITHER_ROSE)
		&& s.getItem() instanceof BlockItem;

	private enum Kind {
		TREE,
		FLOWER
	}

	private int flowerCount;
	private int treeCount;
	private long checkedAt = -100_000;

	private Kind kind = Kind.TREE;
	private final List<BlockPos> spots = new ArrayList<>();
	private boolean fetching;
	private int planted;

	@Override
	public String id() {
		return "terra.plant";
	}

	@Override
	public String describe() {
		return "planting greenery";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (data.campPos().isEmpty() || !Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		if (level.getGameTime() - checkedAt >= CHECK_INTERVAL) {
			checkedAt = level.getGameTime();
			int[] counts = Garden.prune(level, data);
			flowerCount = counts[0];
			treeCount = counts[1];
			checkGardens(c, data);
		}
		return wantTree(c) || wantFlower(c) ? 40 : 0;
	}

	private boolean wantTree(CompanionEntity c) {
		return treeCount < MAX_TREES && CampNeeds.available(c, SAPLING);
	}

	private boolean wantFlower(CompanionEntity c) {
		return flowerCount < MAX_FLOWERS && CampNeeds.available(c, FLOWER);
	}

	private void checkGardens(CompanionEntity c, CampData data) {
		if (data.stage() >= 4 && !data.isCompleted(Structures.GARDENS) && flowerCount >= GARDEN_FLOWERS && treeCount >= GARDEN_TREES) {
			CampProgress.complete(c, Structures.GARDENS);
		}
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		spots.clear();
		planted = 0;
		boolean tree = wantTree(c);
		boolean flower = wantFlower(c);
		kind = tree && (!flower || treeCount < GARDEN_TREES || treeCount * 2 <= flowerCount) ? Kind.TREE : Kind.FLOWER;
		spots.addAll(kind == Kind.TREE ? treeSpots(c, level, data) : flowerCluster(c, level, data));
		if (spots.isEmpty() && kind == Kind.TREE && flower) {
			kind = Kind.FLOWER;
			spots.addAll(flowerCluster(c, level, data));
		}
		if (spots.isEmpty()) {
			return false;
		}
		fetching = !c.backpack().has(material());
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	private Predicate<ItemStack> material() {
		return kind == Kind.TREE ? SAPLING : FLOWER;
	}

	// ----------------------------------------------------------------- spots

	/** Up to two sapling spots on the outer ring, preferring Terra's side of the camp. */
	private static List<BlockPos> treeSpots(CompanionEntity c, ServerLevel level, CampData data) {
		List<BlockPos> chosen = new ArrayList<>();
		BlockPos centre = data.campPos().orElseThrow();
		int radius = WorldEditGuard.campRadius(c);
		double inner = Math.max(4, radius - 6);
		double outer = Math.max(inner + 1, radius - 2);
		double bearing = Math.atan2(c.getZ() - centre.getZ(), c.getX() - centre.getX());
		RandomSource random = c.getRandom();
		List<BlockPos> grown = Garden.trees(data);
		for (int i = 0; i < 40 && chosen.size() < TREES_PER_RUN; i++) {
			double angle = i < 24 ? bearing + (random.nextDouble() * 2 - 1) * Math.PI / 3 : random.nextDouble() * Math.PI * 2;
			double r = inner + random.nextDouble() * (outer - inner);
			int x = centre.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = centre.getZ() + (int) Math.round(Math.sin(angle) * r);
			BlockPos ground = Landscape.ground(level, x, z, centre.getY() + 4, centre.getY() - 6);
			if (ground == null) {
				continue;
			}
			BlockPos spot = ground.above();
			if (farFrom(spot, chosen, TREE_SPACING) && farFrom(spot, grown, TREE_SPACING) && isTreeSpot(c, level, data, ground)) {
				chosen.add(spot);
			}
		}
		return chosen;
	}

	private static boolean isTreeSpot(CompanionEntity c, ServerLevel level, CampData data, BlockPos ground) {
		BlockPos spot = ground.above();
		if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK) || !level.getBlockState(spot).isAir()
			|| !level.getBlockState(spot.above()).isAir() || !WorldEditGuard.inCamp(c, spot)) {
			return false;
		}
		if (Landscape.nearestSiteDistance(data, spot, false) < 10 || PathPlan.nearPath(data, spot, 4)) {
			return false;
		}
		if (data.chestPos().map(chest -> chest.distSqr(spot) < 25).orElse(false) || WorldEditGuard.touchesFluid(level, spot)) {
			return false;
		}
		if (Landscape.anyNear(level, ground, 4, 0, 0, s -> s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND))) {
			return false;
		}
		if (Landscape.anyNear(level, spot, 4, -1, 2, s -> s.is(BlockTags.SAPLINGS) || s.is(BlockTags.LOGS))) {
			return false;
		}
		return !WorldEditGuard.looksPlayerBuilt(level, spot, 2, data);
	}

	/** A small cluster of flower spots a few blocks from a finished building or the supply chest. */
	private static List<BlockPos> flowerCluster(CompanionEntity c, ServerLevel level, CampData data) {
		List<BlockPos> anchors = new ArrayList<>();
		data.sites().forEach((id, site) -> {
			if (data.isCompleted(id)) {
				anchors.add(site.origin);
			}
		});
		data.chestPos().ifPresent(anchors::add);
		if (anchors.isEmpty()) {
			return List.of();
		}
		List<BlockPos> flowers = Garden.flowers(data);
		anchors.sort(Comparator.comparingInt((BlockPos a) -> countNear(flowers, a, 7))
			.thenComparingDouble(a -> a.distSqr(c.blockPosition())));
		RandomSource random = c.getRandom();
		for (BlockPos anchor : anchors) {
			for (int i = 0; i < 16; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double r = 3 + random.nextDouble() * 3;
				int x = anchor.getX() + (int) Math.round(Math.cos(angle) * r);
				int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * r);
				BlockPos ground = Landscape.ground(level, x, z, anchor.getY() + 3, anchor.getY() - 3);
				if (ground == null || !isFlowerSpot(c, level, data, ground)) {
					continue;
				}
				List<BlockPos> cluster = new ArrayList<>();
				cluster.add(ground.above());
				List<BlockPos> around = new ArrayList<>();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							around.add(ground.offset(dx, 0, dz));
						}
					}
				}
				Util.shuffle(around, random);
				for (BlockPos g : around) {
					if (cluster.size() >= CLUSTER) {
						break;
					}
					if (isFlowerSpot(c, level, data, g)) {
						cluster.add(g.above());
					}
				}
				return cluster;
			}
		}
		return List.of();
	}

	private static boolean isFlowerSpot(CompanionEntity c, ServerLevel level, CampData data, BlockPos ground) {
		BlockPos spot = ground.above();
		BlockState floor = level.getBlockState(ground);
		if (!(floor.is(Blocks.GRASS_BLOCK) || floor.is(Blocks.DIRT)) || !level.getBlockState(spot).isAir()
			|| !WorldEditGuard.inCamp(c, spot) || PathPlan.onPath(data, spot)) {
			return false;
		}
		if (Landscape.nearestSiteDistance(data, spot, true) < 8 || WorldEditGuard.touchesFluid(level, spot)) {
			return false;
		}
		if (Pen.site(level).map(p -> p.covers(spot)).orElse(false)) {
			return false; // the animal pen: out of reach behind its fence, and the animals' grazing
		}
		if (Landscape.anyNear(level, ground, 2, 0, 0, s -> s.is(Blocks.FARMLAND))) {
			return false;
		}
		return !WorldEditGuard.looksPlayerBuilt(level, spot, 1, data);
	}

	private static int countNear(List<BlockPos> list, BlockPos centre, int r) {
		int n = 0;
		for (BlockPos p : list) {
			if (p.distSqr(centre) <= r * r) {
				n++;
			}
		}
		return n;
	}

	private static boolean farFrom(BlockPos p, List<BlockPos> others, double distance) {
		for (BlockPos o : others) {
			if (Camp.horizontalDistSqr(o, p) < distance * distance) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ work

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (fetching) {
			ChestFetch.Result r = ChestFetch.step(c, material(), kind == Kind.TREE ? 4 : 8);
			if (r == ChestFetch.Result.RUNNING) {
				return TaskStatus.RUNNING;
			}
			fetching = false;
		}
		if (spots.isEmpty()) {
			return finish(c, level, data);
		}
		ItemStack stock = c.backpack().find(material());
		if (stock.isEmpty() || !(stock.getItem() instanceof BlockItem blockItem)) {
			return finish(c, level, data);
		}
		BlockPos spot = spots.getFirst();
		if (!c.actions().canReach(spot) || c.position().distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 3.5 * 3.5) {
			c.actions().walkTo(spot, WORK_REACH);
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				spots.removeFirst();
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		BlockState state = blockItem.getBlock().defaultBlockState();
		if (!level.getBlockState(spot).isAir() || !state.canSurvive(level, spot)) {
			spots.removeFirst();
			return TaskStatus.RUNNING;
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, spot, state, Reason.LANDSCAPE);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				spots.removeFirst();
			}
			return TaskStatus.RUNNING;
		}
		Item item = stock.getItem();
		if (c.actions().place(spot, state, s -> s.is(item), Reason.LANDSCAPE)) {
			planted++;
			if (kind == Kind.TREE) {
				Garden.addTree(data, spot);
				treeCount++;
			} else {
				Garden.addFlower(data, spot);
				flowerCount++;
			}
		}
		spots.removeFirst();
		return TaskStatus.RUNNING;
	}

	private TaskStatus finish(CompanionEntity c, ServerLevel level, CampData data) {
		checkGardens(c, data);
		if (planted > 0) {
			data.addStat(kind == Kind.TREE ? "trees_planted" : "flowers_planted", planted);
		}
		return planted > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		spots.clear();
		fetching = false;
		planted = 0;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
