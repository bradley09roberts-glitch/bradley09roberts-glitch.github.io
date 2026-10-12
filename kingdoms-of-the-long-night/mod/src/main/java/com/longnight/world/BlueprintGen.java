package com.longnight.world;

import com.longnight.LongNight;
import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.content.Faith;
import com.longnight.registry.ModBlocks;
import com.longnight.sim.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Parametric, culture-styled building generator. Deterministic for a given (type, culture,
 * level, seed) so a building's plan, cost and progress always agree across saves.
 */
public final class BlueprintGen {
    private BlueprintGen() {}

    private static final Map<String, Blueprint> CACHE = new HashMap<>();

    public static Blueprint get(BuildingType type, String culture, int level, long seed, String faith) {
        String key = type.id() + "|" + culture + "|" + level + "|" + seed + "|" + faith;
        Blueprint bp = CACHE.get(key);
        if (bp == null) {
            bp = generate(type, culture, level, seed, faith);
            if (CACHE.size() > 512) CACHE.clear();
            CACHE.put(key, bp);
        }
        return bp;
    }

    static Blueprint generate(BuildingType type, String cultureId, int level, long seed, String faith) {
        Culture c = Content.culture(cultureId);
        G g = new G(type.width, type.depth, c, new Random(seed), faith);
        switch (type) {
            case HOUSE -> house(g, false);
            case LARGE_HOUSE -> house(g, true);
            case FARM -> farm(g);
            case LUMBER_CAMP -> lumberCamp(g);
            case QUARRY -> quarry(g);
            case MINE -> mine(g);
            case FISHERY -> workshop(g, Blocks.BARREL, Blocks.BARREL, Blocks.COMPOSTER, "fishery");
            case HUNTING_LODGE -> workshop(g, Blocks.SMOKER, Blocks.BARREL, Blocks.FLETCHING_TABLE, "lodge");
            case PASTURE -> pasture(g);
            case MILL -> mill(g);
            case BAKERY -> workshop(g, Blocks.SMOKER, Blocks.FURNACE, Blocks.CRAFTING_TABLE, "bakery");
            case CARPENTRY -> workshop(g, Blocks.CRAFTING_TABLE, Blocks.STONECUTTER, Blocks.BARREL, "carpentry");
            case SMITHY -> smithy(g);
            case WEAVERY -> workshop(g, Blocks.LOOM, Blocks.LOOM, Blocks.BARREL, "weavery");
            case TAVERN -> tavern(g);
            case STOREHOUSE -> storehouse(g, false);
            case GRANARY -> storehouse(g, true);
            case MARKET -> market(g);
            case SHRINE -> shrine(g);
            case TEMPLE -> temple(g);
            case WATCHTOWER -> watchtower(g);
            case BARRACKS -> barracks(g);
            case TOWN_HALL -> townHall(g);
            case SCHOOL -> workshop(g, Blocks.LECTERN, Blocks.BOOKSHELF, Blocks.CARTOGRAPHY_TABLE, "school");
            case LIBRARY -> library(g);
            case HEALER_HOUSE -> workshop(g, Blocks.BREWING_STAND, Blocks.CAULDRON, Blocks.BARREL, "healer");
            case GRAVEYARD -> graveyard(g);
            case WELL -> well(g);
            case BUILDERS_YARD -> buildersYard(g);
            case CAMPSITE -> campsite(g);
        }
        g.bp.height = g.maxY;
        return g.bp;
    }

    // ------------------------------------------------------------------ generator state & primitives

    static final class G {
        final Blueprint bp;
        final Culture c;
        final Random r;
        final String faith;
        final int w, d;
        final Map<Long, Integer> index = new HashMap<>();
        int maxY;

        G(int w, int d, Culture c, Random r, String faith) {
            this.bp = new Blueprint(w, d);
            this.w = w;
            this.d = d;
            this.c = c;
            this.r = r;
            this.faith = faith;
        }

        BlockState pal(String role, Block fallback) {
            String id = c.block(role, BuiltInRegistries.BLOCK.getKey(fallback).toString());
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            if (b == Blocks.AIR && !id.endsWith("air")) b = fallback;
            BlockState s = b.defaultBlockState();
            if (s.hasProperty(LeavesBlock.PERSISTENT)) s = s.setValue(LeavesBlock.PERSISTENT, true);
            return s;
        }

        void set(int x, int y, int z, BlockState s) {
            long key = BlockPos.asLong(x, y, z);
            Integer old = index.remove(key);
            if (old != null) bp.steps.set(old, null);
            index.put(key, bp.steps.size());
            bp.steps.add(new Blueprint.Step(x, y, z, s));
            if (y > maxY) maxY = y;
        }

        void set(int x, int y, int z, Block b) {
            set(x, y, z, b.defaultBlockState());
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int y = y1; y <= y2; y++)
                for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) set(x, y, z, s);
        }

        void mark(String name, int x, int y, int z) {
            bp.marks.put(name, new BlockPos(x, y, z));
        }

        Blueprint finish() {
            bp.steps.removeIf(java.util.Objects::isNull);
            index.clear();
            return bp;
        }
    }

    static BlockState stairs(BlockState base, Direction facing) {
        if (base.getBlock() instanceof StairBlock) return base.setValue(StairBlock.FACING, facing);
        return base;
    }

    static BlockState slab(BlockState s, SlabType t) {
        return s.getBlock() instanceof SlabBlock ? s.setValue(SlabBlock.TYPE, t) : s;
    }

    static BlockState facing(Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, d);
        else if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        return s;
    }

    static BlockState axis(BlockState s, Direction.Axis a) {
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, a) : s;
    }

    static void foundation(G g, int w, int d) {
        BlockState f = g.pal("foundation", Blocks.COBBLESTONE);
        g.fill(0, -1, 0, w - 1, -1, d - 1, f);
    }

    static void floor(G g, int w, int d) {
        BlockState fl = g.pal("floor", Blocks.SPRUCE_PLANKS);
        g.fill(0, 0, 0, w - 1, 0, d - 1, fl);
    }

    /** Timber-framed (or plain) walls on the footprint perimeter, y = 1..h. */
    static void walls(G g, int w, int d, int h) {
        BlockState wall = g.pal("wall", Blocks.OAK_PLANKS);
        BlockState frame = g.pal("frame", Blocks.STRIPPED_SPRUCE_LOG);
        for (int y = 1; y <= h; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    if (!edge) continue;
                    boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    boolean post = (z == 0 || z == d - 1) ? x % 4 == 0 : z % 4 == 0;
                    if (corner || post) g.set(x, y, z, axis(frame, Direction.Axis.Y));
                    else if (y == h) g.set(x, y, z, axis(frame, (z == 0 || z == d - 1) ? Direction.Axis.X : Direction.Axis.Z));
                    else g.set(x, y, z, wall);
                }
            }
        }
    }

    static void windows(G g, int w, int d, int y, int doorX) {
        BlockState win = g.pal("window", Blocks.GLASS_PANE);
        for (int x = 2; x < w - 2; x += 2) {
            if (x % 4 == 0) continue;
            if (Math.abs(x - doorX) > 1) g.set(x, y, 0, win);
            g.set(x, y, d - 1, win);
        }
        for (int z = 2; z < d - 2; z += 2) {
            if (z % 4 == 0) continue;
            g.set(0, y, z, win);
            g.set(w - 1, y, z, win);
        }
    }

    static void door(G g, int x, int z) {
        BlockState door = g.pal("door", Blocks.SPRUCE_DOOR);
        if (!(door.getBlock() instanceof DoorBlock)) door = Blocks.SPRUCE_DOOR.defaultBlockState();
        door = door.setValue(DoorBlock.FACING, Direction.SOUTH);
        g.set(x, 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        g.set(x, 2, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        g.mark("door", x, 1, z - 1);
    }

    static void bed(G g, int x, int z, Direction towardsHead) {
        BlockState bed = g.pal("bed", Blocks.RED_BED);
        if (!(bed.getBlock() instanceof BedBlock)) bed = Blocks.RED_BED.defaultBlockState();
        bed = bed.setValue(BedBlock.FACING, towardsHead);
        g.set(x, 1, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
        g.set(x + towardsHead.getStepX(), 1, z + towardsHead.getStepZ(), bed.setValue(BedBlock.PART, BedPart.HEAD));
        if (!g.bp.marks.containsKey("bed")) g.mark("bed", x, 1, z);
    }

    static void lantern(G g, int x, int y, int z) {
        BlockState l = g.pal("light", Blocks.LANTERN);
        if (l.hasProperty(LanternBlock.HANGING)) l = l.setValue(LanternBlock.HANGING, false);
        g.set(x, y, z, l);
    }

    static int roofY(G g, int h, int z, int d) {
        return switch (g.c.roofStyle) {
            case "flat" -> h + 1;
            case "turf" -> h + 1 + Math.min(2, Math.min(z, d - 1 - z));
            default -> h + 1 + Math.min(z, d - 1 - z);
        };
    }

    static void roof(G g, int w, int d, int h) {
        BlockState st = g.pal("roof", Blocks.DARK_OAK_STAIRS);
        BlockState sl = g.pal("roof_slab", Blocks.DARK_OAK_SLAB);
        BlockState rb = g.pal("roof_block", Blocks.DARK_OAK_PLANKS);
        BlockState wall = g.pal("wall", Blocks.OAK_PLANKS);
        switch (g.c.roofStyle) {
            case "flat" -> {
                g.fill(0, h + 1, 0, w - 1, h + 1, d - 1, rb);
                for (int x = 0; x < w; x++) {
                    if (x % 2 == 0) {
                        g.set(x, h + 2, 0, slab(sl, SlabType.BOTTOM));
                        g.set(x, h + 2, d - 1, slab(sl, SlabType.BOTTOM));
                    }
                }
                for (int z = 2; z < d - 1; z += 2) {
                    g.set(0, h + 2, z, slab(sl, SlabType.BOTTOM));
                    g.set(w - 1, h + 2, z, slab(sl, SlabType.BOTTOM));
                }
            }
            case "turf" -> {
                g.fill(0, h + 1, 0, w - 1, h + 1, d - 1, rb);
                if (w > 2 && d > 2) g.fill(1, h + 2, 1, w - 2, h + 2, d - 2, rb);
                if (w > 4 && d > 4) g.fill(2, h + 3, 2, w - 3, h + 3, d - 3, rb);
            }
            default -> {
                for (int i = 0; ; i++) {
                    int z1 = i, z2 = d - 1 - i, y = h + 1 + i;
                    if (z1 > z2) break;
                    for (int x = 0; x < w; x++) {
                        if (z1 == z2) g.set(x, y, z1, slab(sl, SlabType.BOTTOM));
                        else {
                            g.set(x, y, z1, stairs(st, Direction.SOUTH));
                            g.set(x, y, z2, stairs(st, Direction.NORTH));
                        }
                    }
                    // gable ends
                    for (int z = z1 + 1; z < z2; z++) {
                        g.set(0, y, z, wall);
                        g.set(w - 1, y, z, wall);
                    }
                }
            }
        }
    }

    static void chimney(G g, int x, int z, int h, int d) {
        BlockState ch = g.pal("chimney", Blocks.COBBLESTONE);
        g.set(x, 1, z, facing(Blocks.FURNACE, Direction.SOUTH));
        int top = roofY(g, h, z, d) + 1;
        for (int y = 2; y <= top; y++) g.set(x, y, z, ch);
        g.set(x, top + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
    }

    static void shell(G g, int h, int doorX) {
        foundation(g, g.w, g.d);
        floor(g, g.w, g.d);
        walls(g, g.w, g.d, h);
        roof(g, g.w, g.d, h);
        door(g, doorX, 0);
        windows(g, g.w, g.d, 2, doorX);
    }

    static void sign(G g, int x, int y, int z) {
        g.set(x, y, z, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8));
        g.mark("sign", x, y, z);
    }

    // ------------------------------------------------------------------ building types

    static void house(G g, boolean large) {
        int h = 3;
        int doorX = g.w / 2;
        shell(g, h, doorX);
        int back = g.d - 2;
        bed(g, 1, back - 1, Direction.SOUTH);
        bed(g, g.w - 2, back - 1, Direction.SOUTH);
        if (large) bed(g, g.w / 2, back - 1, Direction.SOUTH);
        chimney(g, g.w - 2, 1, h, g.d);
        g.set(1, 1, 1, Blocks.CRAFTING_TABLE);
        g.set(1, 1, 2, facing(Blocks.BARREL, Direction.UP));
        g.set(g.w / 2, 1, g.d / 2, g.pal("fence", Blocks.SPRUCE_FENCE));
        g.set(g.w / 2, 2, g.d / 2, Blocks.OAK_PRESSURE_PLATE);
        lantern(g, g.w - 2, 1, g.d / 2);
        g.mark("work", g.w / 2, 1, 2);
        g.finish();
    }

    static void workshop(G g, Block a, Block b, Block c, String kind) {
        int h = 3;
        int doorX = g.w / 2;
        shell(g, h, doorX);
        g.set(1, 1, g.d - 2, facing(a, Direction.NORTH));
        g.set(g.w - 2, 1, g.d - 2, facing(b, Direction.NORTH));
        g.set(1, 1, 2, facing(c, Direction.EAST));
        if (kind.equals("bakery") || kind.equals("lodge")) chimney(g, g.w - 2, 1, h, g.d);
        else g.set(g.w - 2, 1, 1, facing(Blocks.BARREL, Direction.UP));
        if (kind.equals("school")) {
            for (int x = 2; x < g.w - 2; x++) g.set(x, 1, 3, stairs(g.pal("roof", Blocks.SPRUCE_STAIRS), Direction.SOUTH));
            g.set(g.w - 2, 1, 3, Blocks.BOOKSHELF);
        }
        if (kind.equals("healer")) bed(g, 1, 3, Direction.SOUTH);
        if (kind.equals("fishery")) {
            g.set(g.w - 2, 1, 3, facing(Blocks.BARREL, Direction.UP));
        }
        lantern(g, g.w / 2, 1, g.d - 2);
        sign(g, doorX - 1 < 1 ? 1 : doorX - 1, 1, 1);
        g.mark("work", g.w / 2, 1, g.d - 3);
        g.finish();
    }

    static void smithy(G g) {
        // open-fronted forge: back and side walls, roof on posts
        foundation(g, g.w, g.d);
        BlockState floor = g.pal("foundation", Blocks.COBBLESTONE);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, floor);
        BlockState wall = g.pal("chimney", Blocks.COBBLESTONE);
        BlockState frame = axis(g.pal("frame", Blocks.STRIPPED_SPRUCE_LOG), Direction.Axis.Y);
        int h = 3;
        for (int y = 1; y <= h; y++) {
            for (int x = 0; x < g.w; x++) g.set(x, y, g.d - 1, wall);
            for (int z = 1; z < g.d - 1; z++) {
                g.set(0, y, z, wall);
                g.set(g.w - 1, y, z, wall);
            }
            g.set(0, y, 0, frame);
            g.set(g.w - 1, y, 0, frame);
        }
        roof(g, g.w, g.d, h);
        g.set(2, 1, g.d - 2, facing(Blocks.BLAST_FURNACE, Direction.NORTH));
        g.set(3, 1, g.d - 2, facing(Blocks.FURNACE, Direction.NORTH));
        g.set(3, 1, 3, Blocks.ANVIL.defaultBlockState());
        g.set(g.w - 2, 1, g.d - 2, Blocks.SMITHING_TABLE);
        g.set(1, 1, 2, facing(Blocks.GRINDSTONE, Direction.EAST));
        g.set(g.w - 2, 1, 2, facing(Blocks.BARREL, Direction.UP));
        int top = roofY(g, h, g.d - 2, g.d) + 1;
        for (int y = 2; y <= top; y++) g.set(2, y, g.d - 2, wall);
        g.set(2, top + 1, g.d - 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
        lantern(g, g.w - 2, 1, 1);
        sign(g, 1, 1, 0);
        g.mark("work", 3, 1, 2);
        g.mark("door", 3, 1, 0);
        g.finish();
    }

    static void storehouse(G g, boolean granary) {
        int h = 4;
        shell(g, h, g.w / 2);
        g.set(g.w / 2, 1, g.d - 2, ModBlocks.STOREHOUSE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        for (int z = 1; z < g.d - 1; z++) {
            g.set(1, 1, z, granary ? Blocks.HAY_BLOCK.defaultBlockState() : facing(Blocks.BARREL, Direction.UP));
            g.set(g.w - 2, 1, z, facing(Blocks.BARREL, Direction.UP));
            if (z % 2 == 1) g.set(1, 2, z, facing(Blocks.BARREL, Direction.UP));
        }
        lantern(g, g.w / 2 + 1, 1, g.d - 2);
        sign(g, g.w / 2 - 1, 1, 1);
        g.mark("work", g.w / 2, 1, g.d - 3);
        g.finish();
    }

    static void farm(G g) {
        BlockState fence = g.pal("fence", Blocks.SPRUCE_FENCE);
        BlockState gate = g.pal("gate", Blocks.SPRUCE_FENCE_GATE);
        BlockState crop = g.pal("crop", Blocks.WHEAT);
        if (!(crop.getBlock() instanceof CropBlock)) crop = Blocks.WHEAT.defaultBlockState();
        g.fill(0, -1, 0, g.w - 1, -1, g.d - 1, Blocks.DIRT.defaultBlockState());
        int waterZ = g.d / 2;
        for (int x = 0; x < g.w; x++) {
            for (int z = 0; z < g.d; z++) {
                boolean edge = x == 0 || z == 0 || x == g.w - 1 || z == g.d - 1;
                if (edge) {
                    g.set(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
                } else if (z == waterZ) {
                    g.set(x, 0, z, (x == 1 || x == g.w - 2) ? g.pal("log", Blocks.SPRUCE_LOG) : Blocks.WATER.defaultBlockState());
                } else {
                    g.set(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                }
            }
        }
        for (int x = 0; x < g.w; x++) {
            for (int z = 0; z < g.d; z++) {
                boolean edge = x == 0 || z == 0 || x == g.w - 1 || z == g.d - 1;
                if (edge) g.set(x, 1, z, (z == 0 && x == g.w / 2) ? gate : fence);
            }
        }
        for (int x = 2; x < g.w - 2; x++) {
            for (int z = 1; z < g.d - 1; z++) {
                if (z == waterZ) continue;
                g.set(x, 1, z, crop);
            }
        }
        g.set(1, 1, 1, Blocks.COMPOSTER);
        g.set(g.w - 2, 1, 1, Blocks.HAY_BLOCK);
        g.set(g.w - 2, 2, 1, Blocks.CARVED_PUMPKIN.defaultBlockState());
        g.mark("work", g.w / 2, 1, 1);
        g.mark("door", g.w / 2, 1, -1);
        g.finish();
    }

    static void pasture(G g) {
        BlockState fence = g.pal("fence", Blocks.SPRUCE_FENCE);
        BlockState gate = g.pal("gate", Blocks.SPRUCE_FENCE_GATE);
        g.fill(0, -1, 0, g.w - 1, -1, g.d - 1, Blocks.DIRT.defaultBlockState());
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int x = 0; x < g.w; x++) {
            for (int z = 0; z < g.d; z++) {
                boolean edge = x == 0 || z == 0 || x == g.w - 1 || z == g.d - 1;
                if (edge) g.set(x, 1, z, (z == 0 && x == g.w / 2) ? gate : fence);
            }
        }
        g.set(2, 1, g.d - 2, Blocks.HAY_BLOCK);
        g.set(3, 1, g.d - 2, Blocks.HAY_BLOCK);
        g.set(g.w - 3, 1, g.d - 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        g.mark("work", g.w / 2, 1, 2);
        g.mark("door", g.w / 2, 1, -1);
        g.mark("animals", g.w / 2, 1, g.d / 2);
        g.finish();
    }

    static void lumberCamp(G g) {
        BlockState post = axis(g.pal("log", Blocks.SPRUCE_LOG), Direction.Axis.Y);
        BlockState sl = g.pal("roof_slab", Blocks.SPRUCE_SLAB);
        BlockState logX = axis(g.pal("log", Blocks.SPRUCE_LOG), Direction.Axis.X);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, Blocks.COARSE_DIRT.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            g.set(1, y, 3, post);
            g.set(g.w - 2, y, 3, post);
            g.set(1, y, g.d - 1, post);
            g.set(g.w - 2, y, g.d - 1, post);
        }
        g.fill(1, 4, 3, g.w - 2, 4, g.d - 1, slab(sl, SlabType.BOTTOM));
        g.fill(2, 1, g.d - 1, g.w - 3, 2, g.d - 1, logX);
        g.set(3, 1, 1, Blocks.CRAFTING_TABLE);
        g.set(2, 1, 5, g.pal("log", Blocks.SPRUCE_LOG));
        BlockState sapling = g.pal("sapling", Blocks.SPRUCE_SAPLING);
        g.set(0, 1, 0, sapling);
        g.set(g.w - 1, 1, 0, sapling);
        lantern(g, g.w - 3, 1, 4);
        sign(g, 1, 1, 1);
        g.mark("work", 3, 1, 2);
        g.mark("door", 3, 1, 0);
        g.finish();
    }

    static void quarry(G g) {
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, Blocks.GRAVEL.defaultBlockState());
        g.fill(1, -1, 1, g.w - 2, -1, g.d - 2, Blocks.COBBLESTONE.defaultBlockState());
        g.set(2, 1, 2, facing(Blocks.STONECUTTER, Direction.NORTH));
        g.set(3, 1, 2, facing(Blocks.GRINDSTONE, Direction.NORTH));
        g.fill(g.w - 3, 1, g.d - 3, g.w - 2, 1, g.d - 2, Blocks.COBBLESTONE.defaultBlockState());
        g.set(g.w - 2, 2, g.d - 2, Blocks.COBBLESTONE.defaultBlockState());
        g.set(1, 1, g.d - 2, Blocks.CHAIN.defaultBlockState());
        lantern(g, 1, 1, 1);
        sign(g, 2, 1, 1);
        g.mark("work", g.w / 2, 1, g.d / 2);
        g.mark("door", g.w / 2, 1, 0);
        g.finish();
    }

    static void mine(G g) {
        BlockState post = axis(g.pal("log", Blocks.SPRUCE_LOG), Direction.Axis.Y);
        BlockState beam = axis(g.pal("log", Blocks.SPRUCE_LOG), Direction.Axis.X);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, Blocks.GRAVEL.defaultBlockState());
        int cx = g.w / 2, cz = g.d / 2;
        for (int y = 1; y <= 3; y++) {
            g.set(cx - 2, y, cz - 1, post);
            g.set(cx + 2, y, cz - 1, post);
            g.set(cx - 2, y, cz + 1, post);
            g.set(cx + 2, y, cz + 1, post);
        }
        g.fill(cx - 2, 4, cz - 1, cx + 2, 4, cz + 1, beam);
        // a lined shaft going down with a ladder
        BlockState stone = g.pal("foundation", Blocks.COBBLESTONE);
        for (int y = -4; y <= -1; y++) {
            g.set(cx - 1, y, cz, stone);
            g.set(cx + 1, y, cz, stone);
            g.set(cx, y, cz - 1, stone);
            g.set(cx, y, cz + 1, stone);
        }
        g.set(cx, -5, cz, stone);
        for (int y = -4; y <= 0; y++) g.set(cx, y, cz, Blocks.AIR.defaultBlockState());
        g.set(cx - 1, 1, cz - 2, Blocks.CHAIN);
        g.set(1, 1, 1, facing(Blocks.BARREL, Direction.UP));
        g.set(2, 1, 1, Blocks.COAL_BLOCK);
        lantern(g, g.w - 2, 1, 1);
        sign(g, 3, 1, 1);
        g.mark("work", cx, 1, cz - 2);
        g.mark("door", cx, 1, 0);
        g.finish();
    }

    static void mill(G g) {
        int h = 5;
        shell(g, h, g.w / 2);
        g.set(g.w / 2, 1, g.d - 2, facing(Blocks.GRINDSTONE, Direction.NORTH));
        g.set(1, 1, g.d - 2, facing(Blocks.BARREL, Direction.UP));
        g.set(g.w - 2, 1, g.d - 2, Blocks.HAY_BLOCK);
        // sails on the front gable
        BlockState sail = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState hub = axis(g.pal("log", Blocks.SPRUCE_LOG), Direction.Axis.Z);
        int cx = g.w / 2;
        g.set(cx, 4, -0, hub);
        g.set(cx - 1, 5, 0, sail);
        g.set(cx + 1, 3, 0, sail);
        g.set(cx - 1, 3, 0, sail);
        g.set(cx + 1, 5, 0, sail);
        lantern(g, 1, 1, 1);
        sign(g, cx - 1, 1, 1);
        g.mark("work", cx, 1, g.d - 3);
        g.finish();
    }

    static void tavern(G g) {
        int h = 4;
        shell(g, h, g.w / 2);
        BlockState counter = g.pal("roof_block", Blocks.SPRUCE_PLANKS);
        for (int x = 1; x < g.w - 1; x++) if (x != g.w / 2) g.set(x, 1, g.d - 3, counter);
        g.set(1, 1, g.d - 2, facing(Blocks.BARREL, Direction.NORTH));
        g.set(2, 1, g.d - 2, facing(Blocks.BARREL, Direction.NORTH));
        g.set(3, 1, g.d - 2, Blocks.BREWING_STAND);
        chimney(g, g.w - 2, g.d - 2, h, g.d);
        BlockState fence = g.pal("fence", Blocks.SPRUCE_FENCE);
        BlockState seat = stairs(g.pal("roof", Blocks.SPRUCE_STAIRS), Direction.EAST);
        for (int[] t : new int[][]{{2, 2}, {g.w - 3, 2}}) {
            g.set(t[0], 1, t[1], fence);
            g.set(t[0], 2, t[1], Blocks.OAK_PRESSURE_PLATE);
            g.set(t[0] - 1, 1, t[1], seat);
        }
        bed(g, g.w - 2, 1, Direction.SOUTH);
        lantern(g, g.w / 2 - 1, 1, 2);
        lantern(g, g.w / 2 + 1, 1, g.d - 2);
        sign(g, g.w / 2 - 1, 1, 1);
        g.mark("work", g.w / 2, 1, g.d - 2);
        g.mark("social", g.w / 2, 1, 2);
        g.finish();
    }

    static void market(G g) {
        BlockState path = g.pal("path", Blocks.DIRT_PATH);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, path);
        BlockState post = g.pal("fence", Blocks.SPRUCE_FENCE);
        Block[] cloths = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.BLUE_WOOL, Blocks.GREEN_WOOL};
        int stall = 0;
        for (int[] s : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
            int x = s[0], z = s[1];
            for (int y = 1; y <= 2; y++) {
                g.set(x, y, z, post);
                g.set(x + 2, y, z, post);
            }
            for (int dx = 0; dx <= 2; dx++) {
                g.set(x + dx, 3, z, cloths[stall % cloths.length].defaultBlockState());
                g.set(x + dx, 3, z + 1, cloths[(stall + 1) % cloths.length].defaultBlockState());
            }
            g.set(x + 1, 1, z, facing(Blocks.BARREL, Direction.UP));
            g.set(x + 1, 1, z + 1, ModBlocks.MARKET_STALL.get().defaultBlockState());
            stall++;
        }
        g.set(g.w / 2, 1, g.d / 2, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR));
        lantern(g, 0, 1, g.d / 2);
        lantern(g, g.w - 1, 1, g.d / 2);
        g.mark("work", g.w / 2, 1, g.d / 2 - 1);
        g.mark("social", g.w / 2, 1, g.d / 2 + 1);
        g.mark("door", g.w / 2, 1, 0);
        g.finish();
    }

    static BlockState shrineBlock(G g) {
        Faith f = Content.faith(g.faith);
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(f.shrineBlock));
        if (b == Blocks.AIR) b = Blocks.CAMPFIRE;
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(CampfireBlock.SIGNAL_FIRE)) s = s.setValue(CampfireBlock.SIGNAL_FIRE, false);
        return s;
    }

    static void shrine(G g) {
        BlockState stone = g.pal("foundation", Blocks.COBBLESTONE);
        BlockState sl = g.pal("roof_slab", Blocks.SPRUCE_SLAB);
        BlockState post = axis(g.pal("frame", Blocks.STRIPPED_SPRUCE_LOG), Direction.Axis.Y);
        g.fill(0, -1, 0, g.w - 1, -1, g.d - 1, stone);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, stone);
        for (int y = 1; y <= 3; y++) {
            g.set(0, y, 0, post);
            g.set(g.w - 1, y, 0, post);
            g.set(0, y, g.d - 1, post);
            g.set(g.w - 1, y, g.d - 1, post);
        }
        g.fill(0, 4, 0, g.w - 1, 4, g.d - 1, slab(sl, SlabType.BOTTOM));
        g.set(g.w / 2, 1, g.d / 2, shrineBlock(g));
        g.set(g.w / 2, 1, g.d / 2 + 1, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        g.set(g.w / 2 - 1, 1, g.d / 2 + 1, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true)
                .setValue(BlockStateProperties.CANDLES, 2));
        g.mark("work", g.w / 2, 1, 1);
        g.mark("door", g.w / 2, 1, 0);
        g.finish();
    }

    static void temple(G g) {
        int h = 6;
        shell(g, h, g.w / 2);
        // tall stained windows
        BlockState win = Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState();
        if (g.faith.equals("tide") || g.faith.equals("stars")) win = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
        if (g.faith.equals("grove")) win = Blocks.LIME_STAINED_GLASS_PANE.defaultBlockState();
        if (g.faith.equals("ember")) win = Blocks.ORANGE_STAINED_GLASS_PANE.defaultBlockState();
        for (int z = 2; z < g.d - 2; z += 2) {
            for (int y = 2; y <= 4; y++) {
                g.set(0, y, z, win);
                g.set(g.w - 1, y, z, win);
            }
        }
        BlockState pew = stairs(g.pal("roof", Blocks.SPRUCE_STAIRS), Direction.SOUTH);
        for (int z = 2; z <= g.d - 4; z += 2) {
            for (int x = 2; x < g.w - 2; x++) if (x != g.w / 2) g.set(x, 1, z, pew);
        }
        g.set(g.w / 2, 1, g.d - 2, shrineBlock(g));
        g.set(g.w / 2 - 1, 1, g.d - 2, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        g.set(g.w / 2 + 1, 1, g.d - 2, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        g.set(g.w / 2, 1, g.d - 3, facing(Blocks.LECTERN, Direction.NORTH));
        lantern(g, 1, 1, 1);
        lantern(g, g.w - 2, 1, 1);
        g.mark("work", g.w / 2, 1, g.d - 4);
        g.mark("social", g.w / 2, 1, 3);
        g.finish();
    }

    static void watchtower(G g) {
        BlockState wall = g.pal("foundation", Blocks.COBBLESTONE);
        BlockState frame = axis(g.pal("frame", Blocks.STRIPPED_SPRUCE_LOG), Direction.Axis.Y);
        BlockState floor = g.pal("floor", Blocks.SPRUCE_PLANKS);
        BlockState fence = g.pal("fence", Blocks.SPRUCE_FENCE);
        int top = 8;
        g.fill(0, -1, 0, g.w - 1, -1, g.d - 1, wall);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, floor);
        for (int y = 1; y < top; y++) {
            for (int x = 0; x < g.w; x++) {
                for (int z = 0; z < g.d; z++) {
                    boolean edge = x == 0 || z == 0 || x == g.w - 1 || z == g.d - 1;
                    if (!edge) continue;
                    boolean corner = (x == 0 || x == g.w - 1) && (z == 0 || z == g.d - 1);
                    g.set(x, y, z, corner ? frame : wall);
                }
            }
        }
        g.fill(0, top, 0, g.w - 1, top, g.d - 1, floor);
        g.set(g.w / 2, top, g.d / 2, Blocks.AIR.defaultBlockState());
        for (int y = 1; y <= top; y++) {
            g.set(g.w / 2, y, g.d / 2 + 1 > g.d - 2 ? g.d - 2 : g.d / 2 + 1, wall);
            g.set(g.w / 2, y, g.d / 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        for (int x = 0; x < g.w; x++) {
            for (int z = 0; z < g.d; z++) {
                boolean edge = x == 0 || z == 0 || x == g.w - 1 || z == g.d - 1;
                if (edge) g.set(x, top + 1, z, fence);
            }
        }
        for (int y = top + 2; y <= top + 3; y++) {
            g.set(0, y, 0, fence);
            g.set(g.w - 1, y, 0, fence);
            g.set(0, y, g.d - 1, fence);
            g.set(g.w - 1, y, g.d - 1, fence);
        }
        g.fill(0, top + 4, 0, g.w - 1, top + 4, g.d - 1, slab(g.pal("roof_slab", Blocks.SPRUCE_SLAB), SlabType.BOTTOM));
        g.set(1, top + 1, 1, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR));
        lantern(g, g.w - 2, top + 1, g.d - 2);
        // door
        door(g, g.w / 2, 0);
        g.mark("work", g.w / 2, top + 1, 1);
        g.finish();
    }

    static void barracks(G g) {
        int h = 3;
        shell(g, h, g.w / 2);
        for (int x = 1; x < g.w - 1; x += 2) bed(g, x, g.d - 3, Direction.SOUTH);
        g.set(1, 1, 1, Blocks.TARGET);
        g.set(g.w - 2, 1, 1, facing(Blocks.BARREL, Direction.UP));
        g.set(g.w - 2, 1, 2, Blocks.SMITHING_TABLE);
        lantern(g, g.w / 2 + 1, 1, 2);
        sign(g, g.w / 2 - 1, 1, 1);
        g.mark("work", g.w / 2, 1, 2);
        g.finish();
    }

    static void townHall(G g) {
        int h = 5;
        shell(g, h, g.w / 2);
        BlockState carpet = Blocks.RED_CARPET.defaultBlockState();
        for (int z = 1; z < g.d - 2; z++) g.set(g.w / 2, 1, z, carpet);
        g.set(g.w / 2, 1, g.d - 2, facing(Blocks.LECTERN, Direction.NORTH));
        BlockState seat = stairs(g.pal("roof", Blocks.SPRUCE_STAIRS), Direction.SOUTH);
        g.set(g.w / 2 - 1, 1, g.d - 2, seat);
        g.set(g.w / 2 + 1, 1, g.d - 2, seat);
        g.set(1, 1, g.d - 2, Blocks.BOOKSHELF);
        g.set(g.w - 2, 1, g.d - 2, Blocks.BOOKSHELF);
        g.set(1, 1, 1, Blocks.CARTOGRAPHY_TABLE);
        chimney(g, g.w - 2, 1, h, g.d);
        lantern(g, 1, 1, g.d / 2);
        lantern(g, g.w - 2, 1, g.d / 2);
        sign(g, g.w / 2 - 2, 1, 1);
        g.mark("work", g.w / 2, 1, g.d - 3);
        g.mark("social", g.w / 2, 1, 2);
        g.finish();
    }

    static void library(G g) {
        int h = 4;
        shell(g, h, g.w / 2);
        for (int z = 1; z < g.d - 1; z++) {
            g.set(1, 1, z, Blocks.BOOKSHELF);
            g.set(1, 2, z, Blocks.BOOKSHELF);
            g.set(g.w - 2, 1, z, Blocks.BOOKSHELF);
        }
        g.set(g.w / 2, 1, g.d - 2, facing(Blocks.LECTERN, Direction.NORTH));
        lantern(g, g.w / 2, 1, g.d / 2);
        sign(g, g.w / 2 - 1, 1, 1);
        g.mark("work", g.w / 2, 1, g.d - 3);
        g.finish();
    }

    static void graveyard(G g) {
        BlockState fence = g.pal("fence", Blocks.SPRUCE_FENCE);
        BlockState gate = g.pal("gate", Blocks.SPRUCE_FENCE_GATE);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int z = 1; z < g.d - 1; z++) g.set(g.w / 2, 0, z, Blocks.GRAVEL.defaultBlockState());
        for (int x = 0; x < g.w; x++) {
            for (int z = 0; z < g.d; z++) {
                boolean edge = x == 0 || z == 0 || x == g.w - 1 || z == g.d - 1;
                if (edge) g.set(x, 1, z, (z == 0 && x == g.w / 2) ? gate : fence);
            }
        }
        lantern(g, g.w / 2, 1, g.d - 2);
        g.set(g.w / 2 - 1, 1, g.d - 2, Blocks.LILY_OF_THE_VALLEY);
        g.mark("work", g.w / 2, 1, 1);
        g.mark("door", g.w / 2, 1, -1);
        g.finish();
    }

    /** Free grave plots inside a graveyard, in local coordinates (x, z). */
    public static int[][] gravePlots(int w, int d) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int z = 2; z < d - 2; z += 2) {
            for (int x = 1; x < w - 1; x++) {
                if (x == w / 2) continue;
                out.add(new int[]{x, z});
            }
        }
        return out.toArray(new int[0][]);
    }

    static void well(G g) {
        BlockState stone = g.pal("foundation", Blocks.COBBLESTONE);
        BlockState path = g.pal("path", Blocks.DIRT_PATH);
        BlockState fence = g.pal("fence", Blocks.SPRUCE_FENCE);
        BlockState sl = g.pal("roof_slab", Blocks.SPRUCE_SLAB);
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, path);
        g.fill(1, -3, 1, 3, -1, 3, stone);
        g.fill(2, -2, 2, 2, 0, 2, Blocks.WATER.defaultBlockState());
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (x == 2 && z == 2) continue;
                g.set(x, 1, z, stone);
            }
        }
        g.set(1, 2, 1, fence);
        g.set(3, 2, 1, fence);
        g.set(1, 2, 3, fence);
        g.set(3, 2, 3, fence);
        g.set(1, 3, 1, fence);
        g.set(3, 3, 1, fence);
        g.set(1, 3, 3, fence);
        g.set(3, 3, 3, fence);
        g.fill(1, 4, 1, 3, 4, 3, slab(sl, SlabType.BOTTOM));
        g.set(2, 3, 2, Blocks.CHAIN);
        g.set(0, 1, 0, ModBlocks.NOTICE_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        lantern(g, g.w - 1, 1, 0);
        lantern(g, 0, 1, g.d - 1);
        g.mark("work", 2, 1, 0);
        g.mark("social", 0, 1, 2);
        g.mark("door", 2, 1, 0);
        g.finish();
    }

    static void buildersYard(G g) {
        g.fill(0, 0, 0, g.w - 1, 0, g.d - 1, Blocks.COARSE_DIRT.defaultBlockState());
        BlockState post = axis(g.pal("frame", Blocks.STRIPPED_SPRUCE_LOG), Direction.Axis.Y);
        BlockState sl = g.pal("roof_slab", Blocks.SPRUCE_SLAB);
        for (int y = 1; y <= 3; y++) {
            g.set(0, y, g.d - 3, post);
            g.set(g.w - 1, y, g.d - 3, post);
            g.set(0, y, g.d - 1, post);
            g.set(g.w - 1, y, g.d - 1, post);
        }
        g.fill(0, 4, g.d - 3, g.w - 1, 4, g.d - 1, slab(sl, SlabType.BOTTOM));
        g.set(1, 1, g.d - 2, Blocks.CRAFTING_TABLE);
        g.set(2, 1, g.d - 2, facing(Blocks.STONECUTTER, Direction.NORTH));
        g.fill(g.w - 3, 1, g.d - 2, g.w - 2, 1, g.d - 2, g.pal("accent", Blocks.OAK_PLANKS));
        g.set(1, 1, 1, Blocks.SCAFFOLDING.defaultBlockState());
        g.set(g.w - 2, 1, 1, Blocks.COBBLESTONE);
        lantern(g, 3, 1, g.d - 2);
        sign(g, 3, 1, 0);
        g.mark("work", 3, 1, 2);
        g.mark("door", 3, 1, 0);
        g.finish();
    }

    static void campsite(G g) {
        g.set(g.w / 2, 0, g.d / 2, Blocks.COBBLESTONE);
        g.set(g.w / 2, 1, g.d / 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
        Block[] cloths = {Blocks.BROWN_WOOL, Blocks.WHITE_WOOL, Blocks.GREEN_WOOL};
        int t = 0;
        for (int[] tent : new int[][]{{0, 0}, {6, 0}, {0, 6}}) {
            BlockState cloth = cloths[t++ % cloths.length].defaultBlockState();
            int x = tent[0], z = tent[1];
            for (int dz = 0; dz < 3; dz++) {
                g.set(x, 1, z + dz, cloth);
                g.set(x + 2, 1, z + dz, cloth);
                g.set(x + 1, 2, z + dz, cloth);
            }
            bed(g, x + 1, z, Direction.SOUTH);
        }
        g.set(g.w - 2, 1, g.d - 2, facing(Blocks.BARREL, Direction.UP));
        g.set(g.w - 3, 1, g.d - 2, Blocks.CRAFTING_TABLE);
        g.mark("work", g.w / 2 + 1, 1, g.d / 2);
        g.mark("social", g.w / 2 - 1, 1, g.d / 2);
        g.mark("door", g.w / 2, 1, g.d / 2 + 2);
        g.finish();
    }

    static {
        LongNight.LOG.debug("BlueprintGen ready");
    }
}
