package com.longnight.world;

import com.longnight.LongNight;
import com.longnight.block.GravestoneBlockEntity;
import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.content.Faith;
import com.longnight.registry.ModBlocks;
import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Calendar;
import com.longnight.sim.Construction;
import com.longnight.sim.Person;
import com.longnight.sim.Planner;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Turns paid-for construction progress into real blocks. Before the first block of a building
 * the site is surveyed: too steep, flooded, claimed or occupied by something built by someone
 * else, and the building moves to another plot instead of bulldozing it.
 */
public final class Placer {
    private Placer() {}

    private static final Set<UUID> PREPARED = new HashSet<>();
    private static final Set<UUID> DECAYED = new HashSet<>();
    /** largest height difference (blocks) a plot may have; terraces and foundations absorb the rest */
    public static final int MAX_SPREAD = 9;
    /** GameTests only: the test arena is roofed with barriers, so tests state where the ground is. */
    public static Integer testGroundY;

    /** Every chunk under the plot and its margin is loaded (chunks load in any order, so the corners are not enough). */
    public static boolean areaLoaded(ServerLevel level, Building b) {
        BlockPos o = b.origin;
        for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + b.width() + 2) >> 4; cx++) {
            for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + b.depth() + 2) >> 4; cz++) {
                if (!Places.ready(level, cx, cz)) return false;
            }
        }
        return true;
    }

    /** Survey and ready the plot. Returns false if not ready (unloaded) or relocated. */
    public static boolean prepare(ServerLevel level, WorldSim sim, Settlement s, Building b) {
        if (b.origin == null || !areaLoaded(level, b)) return false;
        int w = b.width(), d = b.depth();
        List<Integer> hs = new ArrayList<>();
        int water = 0, cols = 0, built = 0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                int wx = b.origin.getX() + x, wz = b.origin.getZ() + z;
                int h = testGroundY != null ? testGroundY + 8 : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                // look through tree trunks and plants to the real ground
                BlockPos p = new BlockPos(wx, h, wz);
                while (p.getY() > level.getMinBuildHeight() + 1 && isSurfaceClutter(level.getBlockState(p))) p = p.below();
                hs.add(p.getY());
                cols++;
                BlockState surface = level.getBlockState(p);
                if (!Materials.isNaturalOrReplaceable(surface) && !surface.is(net.minecraft.tags.BlockTags.LOGS)) built++;
                if (!level.getFluidState(p.above()).isEmpty() || !level.getFluidState(p).isEmpty()) water++;
            }
        }
        int[] arr = hs.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(arr);
        int y = arr[arr.length / 2];
        int spread = arr[(int) (arr.length * 0.9)] - arr[(int) (arr.length * 0.1)];
        boolean waterOk = b.type == BuildingType.FISHERY || b.type == BuildingType.WELL;
        String problem = null;
        if (built > 0) problem = "Something built by other hands stands on that plot.";
        else if (spread > MAX_SPREAD) problem = "The ground there is too steep.";
        else if (!waterOk && water * 2 > cols) problem = "That plot is under water.";
        else if (sim.claimed(b.origin, Math.max(w, d))) problem = "That land is claimed by someone else.";
        Blueprint bp = Construction.blueprint(s, b);
        if (problem == null) {
            outer:
            for (int x = -1; x <= w; x++) {
                for (int z = -1; z <= d; z++) {
                    for (int yy = y + 1; yy <= y + bp.height + 2; yy++) {
                        BlockState st = level.getBlockState(new BlockPos(b.origin.getX() + x, yy, b.origin.getZ() + z));
                        if (!Materials.isNaturalOrReplaceable(st) && !Trees.isLog(st)) {
                            problem = "Something built by other hands stands on that plot.";
                            break outer;
                        }
                    }
                }
            }
        }
        if (problem != null) {
            LongNight.LOG.debug("{}: relocating {} ({})", s.name, b.type, problem);
            if (b.placed == 0) Planner.relocate(sim, s, b, problem);
            else b.problem = problem;
            return false;
        }
        b.origin = new BlockPos(b.origin.getX(), y, b.origin.getZ());
        // clear the volume and lay a foundation down to firm ground
        Culture c = Content.culture(s.culture);
        BlockState fill = Blocks.DIRT.defaultBlockState();
        BlockState footing = pathBlock(c).is(Blocks.DIRT_PATH) ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                int wx = b.origin.getX() + x, wz = b.origin.getZ() + z;
                for (int yy = y + 1; yy <= y + bp.height + 2; yy++) {
                    BlockPos p = new BlockPos(wx, yy, wz);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
                boolean inside = x >= 0 && z >= 0 && x < w && z < d;
                for (int yy = y; yy >= y - 10; yy--) {
                    BlockPos p = new BlockPos(wx, yy, wz);
                    BlockState st = level.getBlockState(p);
                    boolean wet = !level.getFluidState(p).isEmpty();
                    if (st.isAir() || st.canBeReplaced() || wet && b.type != BuildingType.FISHERY) {
                        level.setBlock(p, wet ? footing : yy == y && !inside ? grassOrDirt(level, p) : fill, 2);
                    } else break;
                }
                if (!inside && (x == -1 || z == -1 || x == w || z == d)) {
                    BlockPos p = new BlockPos(wx, y, wz);
                    BlockState st = level.getBlockState(p);
                    if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT)) {
                        level.setBlock(p, pathBlock(c), 2);
                    }
                }
            }
        }
        PREPARED.add(b.id);
        sim.setDirty();
        return true;
    }

    static boolean isSurfaceClutter(BlockState st) {
        return st.isAir() || st.canBeReplaced() || Trees.isLog(st) || st.is(net.minecraft.tags.BlockTags.LEAVES)
                || st.is(Blocks.SNOW) || st.is(net.minecraft.tags.BlockTags.FLOWERS);
    }

    static BlockState grassOrDirt(ServerLevel level, BlockPos p) {
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    static BlockState pathBlock(Culture c) {
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(c.block("path", "minecraft:dirt_path")));
        return (b == Blocks.AIR ? Blocks.DIRT_PATH : b).defaultBlockState();
    }

    public static BlockPos nextStepPos(ServerLevel level, Settlement s, Building b) {
        if (b.origin == null) return null;
        Blueprint bp = Construction.blueprint(s, b);
        if (b.placed >= bp.steps.size()) return null;
        Blueprint.Step st = bp.steps.get(b.placed);
        return Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
    }

    /** Place the next already-paid step. Free: the material was charged when progress advanced. */
    public static boolean placeNext(ServerLevel level, WorldSim sim, Settlement s, Building b) {
        if (b.placed >= b.progress || b.origin == null) return false;
        // only an untouched plot is ever surveyed and cleared; a half-built house is never re-cleared
        if (b.placed == 0 && !PREPARED.contains(b.id)) {
            if (!prepare(level, sim, s, b)) return false;
        }
        Blueprint bp = Construction.blueprint(s, b);
        if (b.placed >= bp.steps.size()) return false;
        Blueprint.Step st = bp.steps.get(b.placed);
        BlockPos wp = Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
        if (!Places.ready(level, wp)) return false;
        BlockState state = st.state().rotate(level, wp, Blueprint.rotation(b.rotation));
        BlockState cur = level.getBlockState(wp);
        // after the survey the plot held only natural blocks; anything else was put there by a player since
        boolean foreign = !Materials.isNaturalOrReplaceable(cur) && !Trees.isLog(cur) && !cur.is(state.getBlock());
        if (!foreign && !sim.claimed(wp, 0)) {
            level.setBlock(wp, state, 3);
            if (state.getBlock() instanceof SignBlock) writeSign(level, wp, s, b);
        }
        b.placed++;
        sim.setDirty();
        if (b.placed >= b.total && b.usable()) finished(level, sim, s, b);
        return true;
    }

    static void writeSign(ServerLevel level, BlockPos p, Settlement s, Building b) {
        if (level.getBlockEntity(p) instanceof SignBlockEntity sign) {
            String title = b.name.isEmpty() ? b.type.displayName : b.name;
            SignText text = new SignText();
            String[] words = title.split(" ");
            StringBuilder l1 = new StringBuilder(), l2 = new StringBuilder();
            for (String word : words) {
                if (l1.length() + word.length() < 15 && l2.length() == 0) l1.append(l1.length() == 0 ? "" : " ").append(word);
                else l2.append(l2.length() == 0 ? "" : " ").append(word);
            }
            text = text.setMessage(0, Component.literal(l1.toString()));
            text = text.setMessage(1, Component.literal(l2.toString()));
            text = text.setMessage(3, Component.literal(s.name));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
        }
    }

    static void finished(ServerLevel level, WorldSim sim, Settlement s, Building b) {
        if (b.type == BuildingType.PASTURE) {
            BlockPos m = Places.mark(level, s, b, "animals");
            if (m != null) {
                for (int i = 0; i < 3; i++) {
                    var sheep = EntityType.SHEEP.spawn(level, m.offset(i - 1, 0, 0), MobSpawnType.STRUCTURE);
                    if (sheep != null) sheep.setPersistenceRequired();
                }
            }
        }
    }

    /** Index of a blueprint step whose block is missing from a finished building (air where something should be), or -1. */
    public static int findMissing(ServerLevel level, Settlement s, Building b, int from, int max) {
        if (b.origin == null || !b.usable() || b.placed < b.total) return -1;
        Blueprint bp = Construction.blueprint(s, b);
        WorldSim sim = WorldSim.get(level.getServer());
        int n = bp.steps.size();
        for (int k = 0; k < Math.min(max, n); k++) {
            int i = (from + k) % n;
            Blueprint.Step st = bp.steps.get(i);
            if (st.state().isAir() || st.y() < 0) continue;
            BlockPos wp = Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
            if (!Places.ready(level, wp)) return -1;
            if (sim.claimed(wp, 0)) continue;
            BlockState cur = level.getBlockState(wp);
            if (cur.isAir() && !(st.state().getBlock() instanceof net.minecraft.world.level.block.CropBlock)
                    && !(st.state().getBlock() instanceof net.minecraft.world.level.block.SaplingBlock)
                    && !st.state().is(Blocks.WATER)) return i;
        }
        return -1;
    }

    /** Re-place one missing step, charging its materials again (it was lost). Returns false if the stores lack them. */
    public static boolean repairStep(ServerLevel level, WorldSim sim, Settlement s, Building b, int i) {
        Blueprint bp = Construction.blueprint(s, b);
        Blueprint.Step st = bp.steps.get(i);
        BlockPos wp = Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
        if (!Places.loadedAround(level, wp, 1)) return true; // try again when the neighbourhood is loaded; no charge
        if (sim.claimed(wp, 0) || !level.getBlockState(wp).isAir()) return true; // nothing to mend here: no charge
        var cost = bp.stepCost(i);
        if (!s.stock.takeAll(cost, 1)) {
            b.problem = "Repairs wait for materials.";
            return false;
        }
        level.setBlock(wp, st.state().rotate(level, wp, Blueprint.rotation(b.rotation)), 3);
        if (b.state == Building.State.DAMAGED) b.state = Building.State.COMPLETE;
        b.problem = "";
        sim.setDirty();
        return true;
    }

    public static BlockPos stepPos(Settlement s, Building b, int i) {
        Blueprint bp = Construction.blueprint(s, b);
        Blueprint.Step st = bp.steps.get(i);
        return Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
    }

    /** Catch the world up with progress made while nobody watched (also raises pre-existing towns). */
    public static int catchUp(ServerLevel level, WorldSim sim, Settlement s, int budget) {
        int done = 0;
        for (Building b : new ArrayList<>(s.buildings)) {
            if (done >= budget) break;
            if (b.origin == null) continue;
            while (b.placed < b.progress && done < budget) {
                if (!placeNext(level, sim, s, b)) break;
                done++;
            }
            if (b.state == Building.State.RUINED && b.placed >= b.progress && b.progress > 0 && !b.decayed) {
                b.decayed = true; // once, ever: no loot refill after restarts
                decay(level, s, b, new Random(b.seed));
                sim.setDirty();
            }
        }
        return done;
    }

    public static boolean pending(Settlement s) {
        for (Building b : s.buildings) if (b.origin != null && b.placed < b.progress) return true;
        return false;
    }

    /** Ruins: knock holes in walls and roofs, let moss and vines in, leave a chest of old books. */
    static void decay(ServerLevel level, Settlement s, Building b, Random r) {
        Blueprint bp = Construction.blueprint(s, b);
        for (int i = 0; i < b.placed && i < bp.steps.size(); i++) {
            Blueprint.Step st = bp.steps.get(i);
            BlockPos wp = Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
            if (!Places.ready(level, wp)) continue;
            BlockState cur = level.getBlockState(wp);
            if (cur.isAir()) continue;
            float roll = r.nextFloat();
            if (st.y() >= 2 && roll < 0.3f) level.setBlock(wp, Blocks.AIR.defaultBlockState(), 2);
            else if (roll < 0.45f && cur.is(Blocks.COBBLESTONE)) level.setBlock(wp, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
            else if (roll < 0.45f && cur.is(Blocks.STONE_BRICKS)) level.setBlock(wp, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 2);
            else if (roll < 0.5f && st.y() == 0) level.setBlock(wp, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
            else if (cur.getBlock() instanceof net.minecraft.world.level.block.BedBlock || cur.is(Blocks.LANTERN))
                level.setBlock(wp, Blocks.AIR.defaultBlockState(), 2);
        }
        if (b.type == BuildingType.TEMPLE || b.type == BuildingType.WATCHTOWER) {
            BlockPos c = Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, 1, 1, bp.depth - 2);
            Loot.ruinChest(level, c, s, r);
        }
    }

    // ------------------------------------------------------------------ graves

    /** Lay a person to rest in their settlement's burial ground. Returns true if a grave was placed. */
    public static boolean bury(ServerLevel level, WorldSim sim, Settlement s, Person p) {
        if (p.buried || p.alive) return false;
        Building yard = null;
        for (Building b : s.buildings) if (b.type == BuildingType.GRAVEYARD && b.usable() && b.placed >= b.total) yard = b;
        if (yard == null) return false;
        Blueprint bp = Construction.blueprint(s, yard);
        for (int[] plot : BlueprintGen.gravePlots(bp.width, bp.depth)) {
            BlockPos wp = Blueprint.toWorld(yard.origin, yard.rotation, bp.width, bp.depth, plot[0], 1, plot[1]);
            if (!Places.ready(level, wp)) return false;
            if (!level.getBlockState(wp).isAir()) continue;
            BlockState stone = ModBlocks.GRAVESTONE.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Blueprint.rotation(yard.rotation).rotate(Direction.NORTH));
            level.setBlock(wp, stone, 3);
            if (level.getBlockEntity(wp) instanceof GravestoneBlockEntity g) {
                Faith f = Content.faith(p.faith);
                g.person = p.id;
                g.name = p.fullName();
                int dpy = sim.rules.daysPerYear;
                g.dates = Calendar.year(p.birthDay, dpy) + " - " + Calendar.year(p.deathDay, dpy);
                g.cause = p.deathCause;
                g.faith = f.name;
                g.rite = f.burialText;
                g.epitaph = Content.epitaph(p.faith, new Random(p.id.getLeastSignificantBits())).replace("{name}", p.given);
                g.setChanged();
            }
            // the rite leaves its own mark
            BlockPos beside = Blueprint.toWorld(yard.origin, yard.rotation, bp.width, bp.depth, plot[0], 1, plot[1] + 1);
            BlockState mark = switch (Content.faith(p.faith).burial) {
                case "tree" -> Blocks.OAK_SAPLING.defaultBlockState();
                case "cremation" -> Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
                case "sea" -> Blocks.BLUE_CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
                case "sky" -> Blocks.AMETHYST_CLUSTER.defaultBlockState();
                case "tomb" -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                default -> Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
            };
            if (level.getBlockState(beside).isAir() && Places.ready(level, beside)) {
                if (mark.is(Blocks.OAK_SAPLING) && !level.getBlockState(beside.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
                    mark = Blocks.POPPY.defaultBlockState();
                }
                level.setBlock(beside, mark, 3);
            }
            p.buried = true;
            sim.setDirty();
            return true;
        }
        return false;
    }
}
