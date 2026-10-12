package com.grandline.world.settlement;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.Arch.RoofKind;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Settlement buildings. Every type has its own designed layout (massing, roof, facade, furnished interior) built from
 * the architectural kit through {@link Arch}; the island's {@link BuildStyle} decides the materials and roof form, so a
 * tavern in Alabasta (flat roof, sandstone, lattice windows, awnings) and one in Drum (stone plinth, timber framing,
 * slate gables, smoking chimney) share a plan but not a look. Local frame: entrance on the north side (z = 0), floor
 * at y = 0, walls from y = 1; doorways are three blocks tall so large races fit. Small things (porch steps, sills,
 * shutters, eaves) may sit one block outside the footprint; the settlement layout keeps three blocks between plots.
 * Gameplay markers (NPC roles, boards, the jail cell, the town centre, the shipwright's launch point) and loot tables
 * are the same as before the overhaul.
 */
public final class Buildings {
    public static final String LOOT_HOUSE = "grandline:chests/house";
    public static final String LOOT_TAVERN = "grandline:chests/tavern";
    public static final String LOOT_SHOP = "grandline:chests/shop";
    public static final String LOOT_SMITH = "grandline:chests/blacksmith";
    public static final String LOOT_MARINE = "grandline:chests/marine";
    public static final String LOOT_WAREHOUSE = "grandline:chests/warehouse";
    public static final String LOOT_RUIN = "grandline:chests/ruin";
    public static final String LOOT_TREASURE = "grandline:chests/treasure";
    public static final String LOOT_WRECK = "grandline:chests/wreck";
    public static final String LOOT_SHRINE = "grandline:chests/shrine";
    public static final String LOOT_CACHE = "grandline:chests/cache";

    static final int H = 4; // storey height: three clear blocks plus the floor above

    public static void build(Plot plot, BuildStyle style, Builder b) {
        Mats m = Mats.of(style, b.seed);
        if (m.stilts && STILTED.contains(plot.type())) b = stilts(b, m, plot.type());
        switch (plot.type()) {
            case HOUSE_SMALL -> cottage(b, m);
            case HOUSE -> townhouse(b, m);
            case HOUSE_LARGE -> largeHouse(b, m);
            case MANSION -> mansion(b, m);
            case TAVERN -> tavern(b, m);
            case SHOP -> shop(b, m);
            case BLACKSMITH -> smithy(b, m);
            case INN -> inn(b, m);
            case CHURCH -> {
                if (style == BuildStyle.WANO) shrine(b, m);
                else church(b, m);
            }
            case MARINE_POST -> marinePost(b);
            case WATCHTOWER -> watchtower(b, m);
            case LIGHTHOUSE -> lighthouse(b, m);
            case WAREHOUSE -> warehouse(b, m);
            case MARKET_STALL -> stall(b, m);
            case WELL -> well(b, m);
            case PLAZA -> plaza(b, m);
            case FARM -> farm(b, m);
            case RUINED_HOUSE -> ruin(b, m);
            case BOUNTY_BOARD -> bountyBoard(b, m);
            case DOJO -> dojo(b, m);
            case SHIPWRIGHT -> shipwright(b, m);
            case HUT -> hut(b, m);
            case LANDMARK -> {}
            case BURIED_TREASURE -> buriedTreasure(b);
            case HIDDEN_CACHE -> hiddenCache(b);
            case LOST_SHRINE -> lostShrine(b, m);
            case SHIPWRECK -> shipwreck(b);
        }
    }

    public enum Interior { HOME, TAVERN, SHOP, SMITH, INN, MANSION, WAREHOUSE, DOJO }

    private static CompoundTag npc(String role) {
        CompoundTag t = new CompoundTag();
        t.putString("role", role);
        return t;
    }

    /**
     * General-purpose building for landmark set pieces that place a house of a given footprint, storey count and use
     * (kept for the landmarks that call it); the settlement types below have their own plans.
     */
    public static void house(Builder b, BuildStyle st, int floors, Interior in) {
        Mats m = Mats.of(st, b.seed);
        int w = b.w, d = b.d;
        // walls inset by one so the eaves, sills and the door step stay inside the footprint a landmark gives it
        int x1 = 1, z1 = 1, x2 = w - 2, z2 = d - 2, ix = x1 + 1, iz = z1 + 1, ax = x2 - 1, az = z2 - 1;
        site(b, m, x1, z1, x2, z2, floors * H + w / 2 + 4);
        shell(b, m, x1, z1, x2, z2, floors);
        roof(b, m, x1, z1, x2, z2, floors * H + 1, x2 - x1 >= z2 - z1);
        door(b, m, w / 2, z1, Direction.NORTH, m.roofKind != RoofKind.FLAT);
        windows(b, m, x1, z1, x2, z2, floors, w / 2);
        if (floors > 1) stairsUp(b, m, ix, az - 3, Direction.SOUTH, floors);
        switch (in) {
            case TAVERN -> {
                for (int x = ix + 1; x < ax; x++) b.set(x, 1, az - 2, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
                for (int x = ix + 1; x < ax; x += 2) b.barrel(x, 1, az, LOOT_TAVERN);
                b.marker("npc", w / 2, 1, az - 1, npc("bartender"));
                Arch.tableSet(b, ix + 1, 1, iz + 1, chair(m));
                b.marker("npc", ix + 1, 1, iz + 2, npc("patron"));
                b.marker("npc", ax - 1, 1, iz + 1, npc("patron"));
                b.marker("quest_board", ix, 2, iz + 1, new CompoundTag());
            }
            case SHOP -> {
                for (int x = ix + 1; x <= ax; x++) b.set(x, 1, az - 2, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
                for (int x = ix; x <= ax; x++) b.set(x, 1, az, Arch.decor(ArchBlocks.GOODS_SHELF, Direction.NORTH));
                b.barrel(ix, 1, iz, LOOT_SHOP);
                b.barrel(ax, 1, iz, LOOT_SHOP);
                b.marker("npc", w / 2, 1, az - 1, npc("shopkeeper"));
            }
            case SMITH -> {
                b.set(ix, 1, az, Blocks.ANVIL);
                b.set(ax, 1, az, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, Direction.NORTH));
                b.chest(ix, 1, iz, Direction.EAST, LOOT_SMITH);
                b.marker("npc", w / 2, 1, az - 1, npc("blacksmith"));
            }
            case INN -> {
                b.set(ax, 1, az - 1, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
                b.marker("npc", w / 2, 1, az - 1, npc("innkeeper"));
            }
            case MANSION -> {
                b.chest(ax, 1, az, Direction.WEST, LOOT_TREASURE);
                b.marker("npc", w / 2, 1, d / 2, npc("noble"));
                b.marker("npc", ix, 1, iz, npc("guard"));
            }
            case WAREHOUSE -> {
                for (int x = ix; x <= ax; x += 2) for (int z = az - 1; z <= az; z++) Arch.cargo(b, x, 1, z, x + z);
                b.barrel(ax, 1, iz, LOOT_WAREHOUSE);
                b.barrel(ix, 1, iz, LOOT_WAREHOUSE);
                b.marker("npc", w / 2, 1, iz + 1, npc("dockworker"));
            }
            case DOJO -> {
                b.fill(ix, 0, iz, ax, 0, az, Blocks.BIRCH_PLANKS.defaultBlockState());
                for (int x = ix + 1; x < ax; x += 3) b.set(x, 1, az, Blocks.TARGET);
                b.marker("npc", w / 2, 1, az - 1, npc("master"));
                b.marker("npc", ix + 1, 1, iz + 2, npc("student"));
            }
            default -> {
                b.bed(ix, 1, az - 1, Blocks.RED_BED, Direction.SOUTH);
                b.chest(ax, 1, iz, Direction.WEST, LOOT_HOUSE);
                Arch.tableSet(b, w / 2, 1, d / 2, chair(m));
                b.marker("npc", w / 2, 1, d / 2 + 1, npc("resident"));
            }
        }
        b.hanging(w / 2, H - 1, d / 2 - 1, m.lantern.getBlock());
    }

    // ================================================================== shared pieces

    /** Foundation down to the terrain, a solid plinth layer at y = 0 and clear air above (for the roof too). */
    static void site(Builder b, Mats m, int x1, int z1, int x2, int z2, int clearH) {
        if (!m.stilts) b.foundation(x1, z1, x2, z2, m.base.getBlock(), 14);
        b.clear(x1 - 1, 1, z1 - 1, x2 + 1, clearH, z2 + 1);
        b.fill(x1, 0, z1, x2, 0, z2, m.base);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, m.floor);
    }

    /**
     * Walls of a box of {@code floors} storeys: corner posts in the frame material, a stone plinth course, the upper
     * storeys in the upper-wall material over a beam band at each floor line, and the floors between storeys.
     */
    static void shell(Builder b, Mats m, int x1, int z1, int x2, int z2, int floors) {
        int top = floors * H;
        for (int f = 0; f < floors; f++) {
            int y0 = 1 + f * H;
            BlockState wall = f == 0 ? m.wall : m.upperWall;
            wallsState(b, x1, y0, z1, x2, y0 + H - 2, z2, wall);
            // floor line: a beam band in the wall planes, and the floor itself inside
            int fy = y0 + H - 1;
            band(b, m, x1, z1, x2, z2, fy);
            if (f < floors - 1) b.fill(x1 + 1, fy, z1 + 1, x2 - 1, fy, z2 - 1, m.floor);
        }
        if (m.style != BuildStyle.JUNGLE) {
            for (int x = x1; x <= x2; x++) {
                b.set(x, 1, z1, m.base);
                b.set(x, 1, z2, m.base);
            }
            for (int z = z1; z <= z2; z++) {
                b.set(x1, 1, z, m.base);
                b.set(x2, 1, z, m.base);
            }
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 1, c[1], c[0], top, c[1], m.frame);
        if (m.style == BuildStyle.FUTURE) // rounded-off glass corners
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 2, c[1], c[0], top - 1, c[1], Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        if (m.style == BuildStyle.WANO) // a skirt roof at every inner floor line
            for (int f = 1; f < floors; f++) Arch.skirt(b, m, x1, z1, x2, z2, f * H);
    }

    private static void wallsState(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        b.fill(x1, y1, z1, x2, y2, z1, s);
        b.fill(x1, y1, z2, x2, y2, z2, s);
        b.fill(x1, y1, z1, x1, y2, z2, s);
        b.fill(x2, y1, z1, x2, y2, z2, s);
    }

    /** Horizontal band at height y in the four wall planes: beams for timber styles, trim otherwise. */
    static void band(Builder b, Mats m, int x1, int z1, int x2, int z2, int y) {
        BlockState bx = m.timbered() ? m.beam(Direction.Axis.X) : m.trim;
        BlockState bz = m.timbered() ? m.beam(Direction.Axis.Z) : m.trim;
        b.fill(x1, y, z1, x2, y, z1, bx);
        b.fill(x1, y, z2, x2, y, z2, bx);
        b.fill(x1, y, z1, x1, y, z2, bz);
        b.fill(x2, y, z1, x2, y, z2, bz);
    }

    /** The style's roof over a wall box topping out at y-1; alongX picks the ridge direction for gables. */
    static int roof(Builder b, Mats m, int x1, int z1, int x2, int z2, int y, boolean alongX) {
        return switch (m.roofKind) {
            case FLAT -> {
                Arch.flat(b, m, x1, z1, x2, z2, y - 1, false, true);
                terrace(b, m, x1, z1, x2, z2, y);
                yield y + 1;
            }
            case STEEP -> {
                int across = alongX ? z2 - z1 + 1 : x2 - x1 + 1;
                if (across <= 9) yield Arch.gable(b, m, x1, z1, x2, z2, y, alongX, 1, m.upperWall);
                Mats g = Mats.of(m.style, b.seed);
                g.roofKind = RoofKind.GABLE;
                yield Arch.gable(b, g, x1, z1, x2, z2, y, alongX, 1, m.upperWall);
            }
            case HIP, FLARED -> Arch.hip(b, m, x1, z1, x2, z2, y, m.roofKind == RoofKind.FLARED ? 2 : 1);
            default -> Arch.gable(b, m, x1, z1, x2, z2, y, alongX, 1, m.upperWall);
        };
    }

    /** Types raised on stilts in jungle towns (floor two blocks up, posts and a stair to the door). */
    static final java.util.EnumSet<BuildingType> STILTED = java.util.EnumSet.of(BuildingType.HOUSE_SMALL, BuildingType.HOUSE,
            BuildingType.HOUSE_LARGE, BuildingType.TAVERN, BuildingType.SHOP, BuildingType.INN, BuildingType.HUT, BuildingType.MANSION, BuildingType.DOJO);

    /** Posts under the footprint, a landing and steps up to the door; returns the builder two blocks higher. */
    static Builder stilts(Builder b, Mats m, BuildingType t) {
        int w = b.w, d = b.d;
        Block post = m.frame.getBlock();
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean ns = z == 0 || z == d - 1, we = x == 0 || x == w - 1;
                boolean isPost = ns && (x % 3 == 0 || x == w - 1) || we && (z % 3 == 0 || z == d - 1);
                if (!isPost) continue;
                b.clear(x, 0, z, x, 1, z);
                b.set(x, 0, z, m.frame);
                b.set(x, 1, z, m.frame);
                b.foundation(x, z, x, z, post, 10);
            }
        int dx = t == BuildingType.SHOP || t == BuildingType.HUT ? 2 : w / 2;
        b.set(dx, 2, -1, m.floor);
        b.foundation(dx, -1, dx, -1, post, 10);
        b.fill(dx, 0, -1, dx, 1, -1, m.frame);
        b.clear(dx, 1, -3, dx, 4, -2);
        b.set(dx, 1, -2, Arch.stair(stairsOf(m), Direction.SOUTH, Half.BOTTOM));
        b.set(dx, 0, -3, Arch.stair(stairsOf(m), Direction.SOUTH, Half.BOTTOM));
        b.foundation(dx, -2, dx, -2, post, 10);
        b.foundation(dx, -3, dx, -3, post, 10);
        b.set(dx, 0, -2, m.frame);
        return new Builder(b.sink, b.ox, b.oy + 2, b.oz, w, d, b.rot, b.seed);
    }

    /** Flat-roof terraces: a pergola with an awning canopy, a bench and planters (desert); solar panels and a mast (future). */
    static void terrace(Builder b, Mats m, int x1, int z1, int x2, int z2, int y) {
        if (x2 - x1 < 5 || z2 - z1 < 5) return;
        if (m.style == BuildStyle.FUTURE) {
            for (int x = x1 + 2; x <= x1 + 4; x++)
                for (int z = z1 + 2; z <= z1 + 3; z++) b.set(x, y, z, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
            b.fill(x2 - 2, y, z2 - 2, x2 - 2, y + 2, z2 - 2, Arch.connect(Blocks.IRON_BARS.defaultBlockState(), false, false, false, false));
            b.set(x2 - 2, y + 3, z2 - 2, Blocks.LIGHTNING_ROD.defaultBlockState());
            return;
        }
        if (m.style != BuildStyle.DESERT) return;
        int px = x2 - 3, pz = z2 - 3;
        for (int[] c : new int[][]{{px, pz}, {px + 2, pz}, {px, pz + 2}, {px + 2, pz + 2}})
            b.fill(c[0], y, c[1], c[0], y + 1, c[1], Arch.connect(m.fence, false, false, false, false));
        BlockState aw = Arch.facing((m.awning != null ? m.awning : ArchBlocks.YELLOW_AWNING.get()).defaultBlockState(), Direction.NORTH);
        for (int x = px; x <= px + 2; x++)
            for (int z = pz; z <= pz + 2; z++) b.set(x, y + 2, z, z == pz ? aw : Arch.slab(Blocks.WHITE_WOOL.defaultBlockState(), SlabType.BOTTOM));
        b.set(px + 1, y, pz + 1, Arch.decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
        b.set(x1 + 1, y, z1 + 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        b.set(x1 + 2, y, z1 + 1, Blocks.POTTED_CACTUS.defaultBlockState());
    }

    /** A white plaster dome with a brass finial on a flat roof (Alabasta). */
    static void desertDome(Builder b, Mats m, int cx, int cz, int y, int r) {
        b.cylinder(cx, cz, y, y, r, m.wall.getBlock(), false);
        Arch.dome(b, cx, y + 1, cz, r, ArchBlocks.WHITE_PLASTER.get().defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), true);
    }

    /** Front door at (x, z) in a wall facing {@code out}: three-tall doorway, a step, a lantern and, if wanted, a hood. */
    static void door(Builder b, Mats m, int x, int z, Direction out, boolean hood) {
        b.bigDoor(x, 1, z, m.door.getBlock(), out);
        b.set(x, 3, z, Blocks.AIR);
        int ox = x + out.getStepX(), oz = z + out.getStepZ();
        b.foundation(ox, oz, ox, oz, m.base.getBlock(), 6);
        b.set(ox, 0, oz, Arch.stair(m.step != null ? m.step : stairsOf(m), out.getOpposite(), Half.BOTTOM));
        b.clear(ox, 1, oz, ox, 3, oz);
        Direction.Axis along = out.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        int sx = along == Direction.Axis.X ? 1 : 0, sz = along == Direction.Axis.Z ? 1 : 0;
        Arch.wallLantern(b, m, x + sx * 2, 3, z + sz * 2, out);
        if (hood && m.roofKind != RoofKind.FLAT) {
            // a small door hood: slab canopy on two brackets
            for (int k = -1; k <= 1; k++) b.set(ox + sx * k, 4, oz + sz * k, Arch.slab(m.roofSlab, SlabType.BOTTOM));
            b.set(ox - sx, 3, oz - sz, Arch.stair(m.roofStairs, out.getOpposite(), Half.TOP));
            b.set(ox + sx, 3, oz + sz, Arch.stair(m.roofStairs, out.getOpposite(), Half.TOP));
        } else if (m.awning != null) {
            for (int k = -1; k <= 1; k++) b.set(ox + sx * k, 4, oz + sz * k, Arch.facing(m.awning.defaultBlockState(), out));
        }
    }

    /** Windows on all four walls of a box, every other free bay, skipping the doorway column on the front. */
    static void windows(Builder b, Mats m, int x1, int z1, int x2, int z2, int floors, int doorX) {
        for (int f = 0; f < floors; f++) {
            int y = 2 + f * H;
            boolean shut = f == 0 || m.style != BuildStyle.WANO;
            for (int x = x1 + 2; x <= x2 - 2; x += 2) {
                if (!(f == 0 && Math.abs(x - doorX) <= 1) && (x - x1) % 4 == 2) Arch.window(b, m, x, y, z1, Direction.NORTH, 2, shut);
                if ((x - x1) % 4 == 2) Arch.window(b, m, x, y, z2, Direction.SOUTH, 2, shut);
            }
            for (int z = z1 + 2; z <= z2 - 2; z += 4) {
                Arch.window(b, m, x1, y, z, Direction.WEST, 2, shut);
                Arch.window(b, m, x2, y, z, Direction.EAST, 2, shut);
            }
        }
    }

    /** Straight staircase against the west wall from floor 0 up through each storey, with the floor opened above it. */
    static void stairsUp(Builder b, Mats m, int x, int z, Direction dir, int floors) {
        BlockState st = stairsOf(m);
        for (int f = 0; f < floors - 1; f++) {
            int y = 1 + f * H;
            for (int i = 0; i < H; i++) { // H steps: the last one lies in the plane of the floor above
                int sx = x + dir.getStepX() * i, sz = z + dir.getStepZ() * i;
                b.clear(sx, y + i + 1, sz, sx, y + i + 3, sz);
                b.set(sx, y + i, sz, Arch.stair(st, dir, Half.BOTTOM));
            }
            // railing on the upper floor round the open stairwell
            int ry = y + H;
            for (int i = -1; i < H - 1; i++) {
                int sx = x + dir.getStepX() * i + (dir.getAxis() == Direction.Axis.Z ? 1 : 0), sz = z + dir.getStepZ() * i + (dir.getAxis() == Direction.Axis.X ? 1 : 0);
                if (i >= 0) b.set(sx, ry, sz, Arch.along(m.fence, dir.getAxis()));
            }
            b.set(x - dir.getStepX(), ry, z - dir.getStepZ(), Arch.along(m.fence, dir.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z));
        }
    }

    static BlockState stairsOf(Mats m) {
        return switch (m.style) {
            case DESERT -> Blocks.SANDSTONE_STAIRS.defaultBlockState();
            case MARINE, PALACE, FUTURE -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
            case CANDY -> Blocks.CHERRY_STAIRS.defaultBlockState();
            case CORAL -> Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState();
            case CLOUD -> Blocks.BIRCH_STAIRS.defaultBlockState();
            case GOTHIC -> Blocks.DARK_OAK_STAIRS.defaultBlockState();
            case JUNGLE -> Blocks.BAMBOO_STAIRS.defaultBlockState();
            default -> Blocks.SPRUCE_STAIRS.defaultBlockState();
        };
    }

    static BlockState chair(Mats m) {
        return switch (m.style) {
            case CANDY -> Blocks.CHERRY_STAIRS.defaultBlockState();
            case CLOUD -> Blocks.BIRCH_STAIRS.defaultBlockState();
            case JUNGLE -> Blocks.BAMBOO_STAIRS.defaultBlockState();
            case GOTHIC -> Blocks.DARK_OAK_STAIRS.defaultBlockState();
            default -> Blocks.SPRUCE_STAIRS.defaultBlockState();
        };
    }

    static void stove(Builder b, Mats m, int x, int y, int z, Direction facing) {
        b.set(x, y, z, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, facing));
    }

    // ================================================================== homes

    /** Cottage (7x7): one storey under a steep roof, chimney with smoke, a homely one-room interior. */
    static void cottage(Builder b, Mats m) {
        int w = b.w, d = b.d;
        boolean alongX = b.rand(1, 3) != 0;
        site(b, m, 0, 0, w - 1, d - 1, 12);
        shell(b, m, 0, 0, w - 1, d - 1, 1);
        int top = roof(b, m, 0, 0, w - 1, d - 1, H + 1, alongX);
        door(b, m, w / 2, 0, Direction.NORTH, true);
        Arch.window(b, m, 1, 2, 0, Direction.NORTH, 2, true);
        Arch.window(b, m, w - 2, 2, 0, Direction.NORTH, 2, true);
        Arch.window(b, m, 0, 2, d / 2, Direction.WEST, 2, true);
        Arch.window(b, m, w - 1, 2, d / 2, Direction.EAST, 2, true);
        Arch.window(b, m, w / 2, 2, d - 1, Direction.SOUTH, 2, true);
        // interior: bed in the back corner, stove and chimney, table, chest
        b.bed(1, 1, d - 3, Blocks.RED_BED, Direction.SOUTH);
        b.set(1, 1, d - 4, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        stove(b, m, w - 2, 1, d - 2, Direction.NORTH);
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, w - 1, b.rand(4, 2) == 0 ? d - 1 : d / 2 + 1, 1, top + 1, true);
        if (b.rand(8, 3) == 0 && m.roofKind != RoofKind.FLAT) { // a woodshed lean-to on the west side
            Arch.leanTo(b, m, -1, 2, -1, d - 2, H, Direction.WEST);
            for (int z = 3; z <= d - 3; z++) b.fill(-1, 1, z, -1, 2, z, Arch.axis(Blocks.OAK_LOG.defaultBlockState(), Direction.Axis.Z));
        }
        b.set(w - 2, 1, d - 3, Blocks.CRAFTING_TABLE);
        b.chest(1, 1, 1, Direction.EAST, LOOT_HOUSE);
        b.set(w - 2, 1, 1, Arch.stair(chair(m), Direction.WEST, Half.BOTTOM));
        b.set(w - 3, 1, 2, Arch.decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(w - 3, 2, 2, Blocks.POTTED_POPPY);
        b.hanging(w / 2, H - 1, d / 2, m.lantern.getBlock());
        b.marker("npc", w / 2, 1, d / 2, npc("resident"));
        porchBench(b, m, w, 0);
    }

    /** A bench beside the front door for homely styles. */
    static void porchBench(Builder b, Mats m, int w, int z) {
        if (m.style == BuildStyle.FUTURE || m.style == BuildStyle.MARINE) return;
        b.set(1, 1, z - 1, Arch.decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
    }

    /** Townhouse (9x9): two storeys, upper storey framed over a beam band, stair inside, kitchen below, bedrooms above. */
    static void townhouse(Builder b, Mats m) {
        int w = b.w, d = b.d;
        boolean alongX = b.rand(2, 4) != 0;
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 8);
        shell(b, m, 0, 0, w - 1, d - 1, 2);
        int top = roof(b, m, 0, 0, w - 1, d - 1, 2 * H + 1, alongX);
        door(b, m, w / 2, 0, Direction.NORTH, true);
        windows(b, m, 0, 0, w - 1, d - 1, 2, w / 2);
        if (m.roofKind == RoofKind.GABLE && alongX && b.rand(5, 2) == 0) Arch.dormer(b, m, w / 2, 0, 2 * H + 1);
        if (m.style == BuildStyle.DESERT && b.rand(6, 3) == 0) desertDome(b, m, w / 2, d / 2, 2 * H + 1, 2);
        stairsUp(b, m, 1, 2, Direction.SOUTH, 2);
        // ground floor: kitchen and parlour
        stove(b, m, w - 2, 1, d - 2, Direction.NORTH);
        b.set(w - 3, 1, d - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(w - 4, 1, d - 2, Blocks.CRAFTING_TABLE);
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, w - 1, d - 1, 1, top + 1, b.rand(3, 2) == 0);
        Arch.tableSet(b, w / 2 + 1, 1, d / 2, chair(m));
        b.chest(w - 2, 1, 1, Direction.WEST, LOOT_HOUSE);
        b.set(1, 1, d - 2, Blocks.BOOKSHELF);
        b.hanging(w / 2, H - 1, d / 2, m.lantern.getBlock());
        b.marker("npc", w / 2, 1, d / 2 + 1, npc("resident"));
        // upper floor: two beds, wardrobe, chest
        int u = H + 1;
        b.bed(w - 3, u, d - 3, Blocks.BLUE_BED, Direction.SOUTH);
        b.bed(w - 5, u, d - 3, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.chest(w - 2, u, 1, Direction.WEST, LOOT_HOUSE);
        b.set(w - 2, u, 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.WEST));
        b.set(w / 2, u, 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.SOUTH));
        b.hanging(w / 2 + 1, u + H - 2, d / 2, m.lantern.getBlock());
        porchBench(b, m, w, 0);
    }

    /** Large house (11x9): two storeys with a cross-gabled entrance bay, chimney stack and a bigger household. */
    static void largeHouse(Builder b, Mats m) {
        int w = b.w, d = b.d;
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 9);
        shell(b, m, 0, 0, w - 1, d - 1, 2);
        int top = roof(b, m, 0, 0, w - 1, d - 1, 2 * H + 1, true);
        if (m.roofKind == RoofKind.GABLE || m.roofKind == RoofKind.LOW_GABLE) {
            // cross gable over the entrance bay, its gable facing the street
            Arch.gable(b, m, w / 2 - 2, 0, w / 2 + 2, 2, 2 * H + 1, false, 1, m.upperWall);
            Arch.window(b, m, w / 2, 2 * H + 1, 0, Direction.NORTH, 1, false);
        }
        door(b, m, w / 2, 0, Direction.NORTH, true);
        windows(b, m, 0, 0, w - 1, d - 1, 2, w / 2);
        stairsUp(b, m, 1, 2, Direction.SOUTH, 2);
        // ground: dining room and kitchen divided by a half wall
        b.fill(w / 2 + 1, 1, d / 2, w - 2, 1, d / 2, m.trim.hasProperty(RotatedPillarBlock.AXIS) ? Arch.axis(m.trim, Direction.Axis.X) : m.trim);
        b.set(w / 2 + 1, 2, d / 2, m.fence);
        stove(b, m, w - 2, 1, d - 2, Direction.NORTH);
        b.set(w - 3, 1, d - 2, Blocks.CAULDRON);
        b.set(w - 4, 1, d - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, w - 1, d / 2, 1, top + 1, true);
        Arch.tableSet(b, w / 2 + 2, 1, 2, chair(m));
        Arch.tableSet(b, 4, 1, d - 2, chair(m));
        b.chest(w - 2, 1, 1, Direction.WEST, LOOT_HOUSE);
        b.hanging(w / 2, H - 1, d / 2, m.lantern.getBlock());
        b.marker("npc", w / 2 + 2, 1, 3, npc("resident"));
        int u = H + 1;
        b.bed(w - 3, u, d - 3, Blocks.BLUE_BED, Direction.SOUTH);
        b.bed(w - 6, u, d - 3, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.bed(4, u, 1, Blocks.YELLOW_BED, Direction.NORTH);
        b.chest(w - 2, u, 1, Direction.WEST, LOOT_HOUSE);
        b.set(w - 2, u, 2, Blocks.BOOKSHELF);
        b.hanging(w / 2, u + H - 2, d / 2, m.lantern.getBlock());
        porchBench(b, m, w, 0);
    }

    /** Mansion (15x13): symmetrical two-storey house with a columned portico, hip or style roof, grand hall and study. */
    static void mansion(Builder b, Mats m) {
        int w = b.w, d = b.d, cx = w / 2;
        // the main block sits two blocks back so the portico stands inside the plot
        int z1 = 2;
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 9);
        shell(b, m, 0, z1, w - 1, d - 1, 2);
        Mats rm = m;
        if (m.roofKind == RoofKind.GABLE || m.roofKind == RoofKind.LOW_GABLE) {
            rm = Mats.of(m.style, b.seed);
            rm.roofKind = RoofKind.HIP;
        }
        int top = roof(b, rm, 0, z1, w - 1, d - 1, 2 * H + 1, true);
        // portico: four columns, a balcony deck and a pediment
        BlockState col = m.style == BuildStyle.DESERT ? Arch.axis(ArchBlocks.SANDSTONE_COLUMN.get().defaultBlockState(), Direction.Axis.Y)
                : m.style == BuildStyle.WANO || m.style == BuildStyle.JUNGLE || m.style == BuildStyle.GIANT ? m.frame
                : Arch.axis(ArchBlocks.MARBLE_COLUMN.get().defaultBlockState(), Direction.Axis.Y);
        b.fill(cx - 3, 0, 0, cx + 3, 0, 1, m.base);
        b.clear(cx - 3, 1, 0, cx + 3, H - 1, 1);
        for (int x : new int[]{cx - 3, cx - 1, cx + 1, cx + 3}) b.fill(x, 1, 0, x, H - 1, 0, col);
        b.fill(cx - 3, H, 0, cx + 3, H, 1, m.base);
        for (int x = cx - 3; x <= cx + 3; x++) b.set(x, H + 1, 0, Arch.along(m.fence, Direction.Axis.X));
        door(b, m, cx, z1, Direction.NORTH, false);
        b.set(cx, 0, 1, m.base);
        b.set(cx, H + 1, z1, Blocks.AIR);
        b.set(cx, H + 2, z1, Blocks.AIR);
        b.door(cx, H + 1, z1, m.door.getBlock(), Direction.NORTH); // balcony door
        windows(b, m, 0, z1, w - 1, d - 1, 2, cx);
        if (m.style == BuildStyle.DESERT) desertDome(b, m, cx, (z1 + d) / 2, 2 * H + 1, 3);
        // chimneys at both ends
        if (m.roofKind != RoofKind.FLAT) {
            Arch.chimney(b, m, 1, d - 3, 1, top, true);
            Arch.chimney(b, m, w - 2, d - 3, 1, top, false);
        }
        // grand hall with a central staircase to the gallery
        b.fill(cx - 1, 1, z1 + 1, cx + 1, 1, z1 + 3, Blocks.RED_CARPET);
        BlockState st = stairsOf(m);
        for (int i = 0; i < H; i++) { // a two-wide straight flight; the top step lies in the upper floor
            int z = d - 1 - H + i;
            if (i < H - 1) b.clear(cx - 1, H, z, cx, H, z);
            b.set(cx - 1, 1 + i, z, Arch.stair(st, Direction.SOUTH, Half.BOTTOM));
            b.set(cx, 1 + i, z, Arch.stair(st, Direction.SOUTH, Half.BOTTOM));
        }
        // study and dining wings
        b.fill(1, 1, d - 2, 3, 2, d - 2, Blocks.BOOKSHELF);
        b.set(2, 1, z1 + 2, Blocks.LECTERN);
        Arch.tableSet(b, w - 4, 1, z1 + 3, chair(m));
        Arch.tableSet(b, w - 4, 1, z1 + 5, chair(m));
        b.set(w - 2, 1, d - 2, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.WEST));
        b.hanging(cx, H - 1, z1 + 4, m.lantern.getBlock());
        b.marker("npc", cx, 1, z1 + 4, npc("noble"));
        b.marker("npc", cx + 2, 1, z1 + 1, npc("guard"));
        // upstairs: master bedroom with the treasure
        int u = H + 1;
        b.bed(2, u, d - 3, Blocks.PURPLE_BED, Direction.SOUTH);
        b.chest(1, u, z1 + 1, Direction.EAST, LOOT_TREASURE);
        b.set(w - 2, u, d - 2, Blocks.BOOKSHELF);
        b.bed(w - 3, u, d - 3, Blocks.WHITE_BED, Direction.SOUTH);
        b.hanging(cx, u + H - 2, z1 + 4, m.lantern.getBlock());
        // a low garden wall either side of the portico with planters behind it
        for (int x = 0; x < w; x++) {
            if (Math.abs(x - cx) <= 3) continue;
            boolean end = x == 0 || x == w - 1 || Math.abs(x - cx) == 4;
            b.set(x, 1, 0, Arch.connect(m.gardenWall, false, x < w - 1 && Math.abs(x + 1 - cx) > 3, false, x > 0 && Math.abs(x - 1 - cx) > 3)
                    .setValue(WallBlock.UP, end));
            if (!end) b.set(x, 1, 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        }
    }

    // ================================================================== trades

    /** Tavern (13x11): two storeys, a covered terrace with benches, bar with kegs, tables, hearth, rooms upstairs. */
    static void tavern(Builder b, Mats m) {
        int w = b.w, d = b.d;
        int z1 = 2; // a two-deep terrace in front
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 10);
        shell(b, m, 0, z1, w - 1, d - 1, 2);
        int top = roof(b, m, 0, z1, w - 1, d - 1, 2 * H + 1, true);
        // terrace: deck, posts, a lean-to roof (or awnings) and benches
        b.fill(0, 0, 0, w - 1, 0, z1 - 1, m.floor);
        for (int x : new int[]{0, w / 2 - 2, w / 2 + 2, w - 1}) b.fill(x, 1, 0, x, H - 1, 0, m.frame);
        if (m.roofKind == RoofKind.FLAT) {
            for (int x = 0; x < w; x++) for (int z = 0; z < z1; z++) b.set(x, H, z, Arch.facing(m.awning != null ? m.awning.defaultBlockState() : ArchBlocks.RED_AWNING.get().defaultBlockState(), Direction.NORTH));
        } else Arch.leanTo(b, m, 0, -1, w - 1, z1 - 1, H, Direction.NORTH);
        b.set(2, 1, 1, Arch.decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
        b.set(w - 3, 1, 1, Arch.decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
        b.set(3, 1, 0, Arch.decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(w - 4, 1, 0, Arch.decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.hanging(w / 2 - 2, H - 1, 1, m.lantern.getBlock());
        b.hanging(w / 2 + 2, H - 1, 1, m.lantern.getBlock());
        // wide double door
        b.bigDoor(w / 2, 1, z1, m.door.getBlock(), Direction.NORTH);
        b.bigDoor(w / 2 - 1, 1, z1, m.door.getBlock(), Direction.NORTH);
        b.set(w / 2 - 1, 1, z1, m.door.setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT));
        b.set(w / 2 - 1, 2, z1, m.door.setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT)
                .setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        windows(b, m, 0, z1, w - 1, d - 1, 2, w / 2);
        // a hanging sign board: banner over the door
        b.set(w / 2 + 2, H + 1, z1 - 1, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        barRoom(b, m, w, d);
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, 0, d - 3, 1, top + 1, true);
        b.set(1, 1, d - 3, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        stairsUp(b, m, w - 2, z1 + 1, Direction.SOUTH, 2);
        // rooms upstairs
        int u = H + 1;
        for (int x = 1; x < w - 4; x += 3) b.bed(x, u, d - 3, Blocks.WHITE_BED, Direction.SOUTH);
        b.chest(1, u, z1 + 1, Direction.EAST, LOOT_TAVERN);
        b.hanging(w / 2, u + H - 2, (z1 + d) / 2, m.lantern.getBlock());
    }

    /** The tavern's bar room: counter with stools, kegs behind, tables, markers (bartender, two patrons, quest board). */
    static void barRoom(Builder b, Mats m, int w, int d) {
        for (int x = 2; x < w - 3; x++) b.set(x, 1, d - 4, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        for (int x = 2; x <= w - 3; x += 2) {
            b.barrel(x, 1, d - 2, LOOT_TAVERN);
            b.set(x + 1, 1, d - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH));
            b.set(x, 2, d - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH));
        }
        for (int x = 3; x < w - 3; x += 3) b.set(x, 1, d - 5, Arch.stair(chair(m), Direction.SOUTH, Half.BOTTOM));
        b.marker("npc", w / 2, 1, d - 3, npc("bartender"));
        int tz = Math.max(4, d / 2 - 1);
        Arch.tableSet(b, 3, 1, tz, chair(m));
        Arch.tableSet(b, w - 5, 1, tz, chair(m));
        b.marker("npc", 3, 1, tz + 1, npc("patron"));
        b.marker("npc", w - 5, 1, tz + 1, npc("patron"));
        b.marker("quest_board", 1, 2, d / 2, new CompoundTag());
        b.hanging(w / 2, H - 1, d / 2, m.lantern.getBlock());
        b.hanging(3, H - 1, tz, m.lantern.getBlock());
    }

    /** Shop (9x9): shopfront with a display window and awning, counter, goods shelves; the shopkeeper lives above. */
    static void shop(Builder b, Mats m) {
        int w = b.w, d = b.d;
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 8);
        shell(b, m, 0, 0, w - 1, d - 1, 2);
        int top = roof(b, m, 0, 0, w - 1, d - 1, 2 * H + 1, true);
        b.bigDoor(2, 1, 0, m.door.getBlock(), Direction.NORTH);
        b.set(2, 0, -1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
        // display window: three wide, two tall, with an awning over the whole shopfront
        for (int x = 4; x <= 6; x++) {
            b.set(x, 1, 0, m.base);
            b.fill(x, 2, 0, x, 3, 0, Arch.along(m.window, Direction.Axis.X));
            b.set(x, 1, -1, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        }
        BlockState aw = Arch.facing((m.awning != null ? m.awning : ArchBlocks.RED_AWNING.get()).defaultBlockState(), Direction.NORTH);
        for (int x = 1; x <= w - 2; x++) b.set(x, H, -1, aw);
        Arch.wallLantern(b, m, 0, 3, 0, Direction.NORTH);
        windows(b, m, 0, 0, w - 1, d - 1, 2, 2);
        shopRoom(b, m, w, d);
        stairsUp(b, m, 1, 1, Direction.SOUTH, 2);
        int u = H + 1;
        b.bed(w - 2, u, d - 3, Blocks.GREEN_BED, Direction.SOUTH);
        b.chest(w - 2, u, 1, Direction.WEST, LOOT_HOUSE);
        b.set(w - 3, u, 1, Arch.decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, w - 1, d - 1, H + 1, top + 1, false);
    }

    static void shopRoom(Builder b, Mats m, int w, int d) {
        for (int x = 3; x < w - 1; x++) b.set(x, 1, d - 4, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        for (int x = 2; x < w - 1; x++) b.set(x, 1, d - 2, Arch.decor(ArchBlocks.GOODS_SHELF, Direction.NORTH));
        for (int x = 2; x < w - 1; x++) b.set(x, 2, d - 2, Arch.decor(ArchBlocks.GOODS_SHELF, Direction.NORTH));
        b.barrel(1, 1, d - 3, LOOT_SHOP);
        b.barrel(w - 2, 1, 1, LOOT_SHOP);
        b.set(w - 2, 1, 2, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.set(w - 2, 1, 3, ArchBlocks.CARGO_CRATE.get().defaultBlockState());
        b.marker("npc", w / 2 + 1, 1, d - 3, npc("shopkeeper"));
        b.hanging(w / 2, H - 1, d / 2, m.lantern.getBlock());
    }

    /** Smithy (9x9): enclosed forge at the back with a smoking chimney, open workshop under a lean-to in front. */
    static void smithy(Builder b, Mats m) {
        int w = b.w, d = b.d, z1 = 4;
        site(b, m, 0, 0, w - 1, d - 1, 14);
        b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.STONE_BRICKS.defaultBlockState());
        b.fill(1, 0, 1, w - 2, 0, z1 - 1, Blocks.COBBLESTONE.defaultBlockState());
        shell(b, m, 0, z1, w - 1, d - 1, 1);
        int top = roof(b, m, 0, z1, w - 1, d - 1, H + 1, true);
        // open workshop: posts and a lean-to roof against the forge
        for (int x : new int[]{0, w - 1}) b.fill(x, 1, 0, x, H - 1, 0, m.frame);
        if (m.roofKind == RoofKind.FLAT) {
            BlockState aw = Arch.facing((m.awning != null ? m.awning : ArchBlocks.YELLOW_AWNING.get()).defaultBlockState(), Direction.NORTH);
            for (int x = 0; x < w; x++) for (int z = 0; z < z1; z++) b.set(x, H, z, aw);
        } else Arch.leanTo(b, m, 0, -1, w - 1, z1 - 1, H + 1, Direction.NORTH);
        b.bigDoor(w / 2, 1, z1, m.door.getBlock(), Direction.NORTH);
        b.set(2, 1, 1, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
        b.set(w - 3, 1, 1, Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        b.set(w - 2, 1, 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.set(1, 1, 2, Blocks.SMITHING_TABLE);
        b.set(w - 2, 1, 1, ArchBlocks.CARGO_CRATE.get().defaultBlockState());
        // the forge: blast furnace under the chimney, coal, tool rack
        b.set(w - 2, 1, d - 2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, Direction.NORTH));
        b.set(w - 3, 1, d - 2, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
        b.set(1, 1, d - 2, Blocks.COAL_BLOCK);
        b.chest(1, 1, z1 + 1, Direction.EAST, LOOT_SMITH);
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, w - 1, d - 1, 1, top + 1, true);
        else Arch.chimney(b, m, w - 1, d - 1, 1, H + 2, true);
        Arch.window(b, m, 0, 2, z1 + 2, Direction.WEST, 2, true);
        Arch.window(b, m, w - 1, 2, z1 + 2, Direction.EAST, 2, false);
        b.hanging(w / 2, H - 1, z1 + 2, m.lantern.getBlock());
        b.hanging(w / 2, H - 1, 1, m.lantern.getBlock());
        b.marker("npc", w / 2, 1, d - 3, npc("blacksmith"));
    }

    /** Inn (11x11): two storeys plus attic rooms, reception with counter, dining tables, guest beds upstairs. */
    static void inn(Builder b, Mats m) {
        int w = b.w, d = b.d;
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 10);
        shell(b, m, 0, 0, w - 1, d - 1, 2);
        int top = roof(b, m, 0, 0, w - 1, d - 1, 2 * H + 1, true);
        door(b, m, w / 2, 0, Direction.NORTH, true);
        windows(b, m, 0, 0, w - 1, d - 1, 2, w / 2);
        if (m.style == BuildStyle.DESERT) desertDome(b, m, w / 2, d / 2, 2 * H + 1, 2);
        else if (m.roofKind == RoofKind.GABLE) {
            Arch.dormer(b, m, 3, 0, 2 * H + 1);
            Arch.dormer(b, m, w - 4, 0, 2 * H + 1);
        }
        // a hanging sign: banner by the door
        b.set(w / 2 + 2, H, -1, Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        b.set(w / 2 + 2, H, 0, m.wall);
        // reception and dining
        for (int x = w - 4; x <= w - 2; x++) b.set(x, 1, 3, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.WEST));
        b.set(w - 2, 1, 1, Blocks.LECTERN);
        b.marker("npc", w - 3, 1, 2, npc("innkeeper"));
        Arch.tableSet(b, 4, 1, d - 3, chair(m));
        Arch.tableSet(b, w - 5, 1, d - 3, chair(m));
        b.set(2, 1, d - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(w - 2, 1, d - 2, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.NORTH));
        if (m.roofKind != RoofKind.FLAT) Arch.chimney(b, m, w - 1, d - 1, 1, top + 1, true);
        stairsUp(b, m, 1, 2, Direction.SOUTH, 2);
        b.hanging(w / 2, H - 1, d / 2, m.lantern.getBlock());
        // guest rooms: beds in a row with chests between
        int u = H + 1;
        for (int x = 3; x < w - 1; x += 2) b.bed(x, u, d - 3, Blocks.WHITE_BED, Direction.SOUTH);
        for (int x = 4; x < w - 1; x += 4) b.chest(x, u, 1, Direction.SOUTH, LOOT_HOUSE);
        b.hanging(w / 2, u + H - 2, d / 2, m.lantern.getBlock());
    }

    /** Training hall (13x11): one tall storey, polished floor, weapon racks, targets and a raised master's dais. */
    static void dojo(Builder b, Mats m) {
        int w = b.w, d = b.d;
        Mats rm = m;
        if (m.roofKind == RoofKind.GABLE || m.roofKind == RoofKind.LOW_GABLE) {
            rm = Mats.of(m.style, b.seed);
            rm.roofKind = RoofKind.HIP;
        }
        site(b, m, 0, 0, w - 1, d - 1, 16);
        b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.BIRCH_PLANKS.defaultBlockState());
        shell(b, m, 0, 0, w - 1, d - 1, 1);
        // a taller hall: raise the walls one more block
        b.walls(0, H, 0, w - 1, H, d - 1, m.wall.getBlock());
        band(b, m, 0, 0, w - 1, d - 1, H + 1);
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) b.fill(c[0], 1, c[1], c[0], H + 1, c[1], m.frame);
        roof(b, rm, 0, 0, w - 1, d - 1, H + 2, true);
        door(b, m, w / 2, 0, Direction.NORTH, true);
        for (int x = 2; x < w - 2; x += 4) {
            Arch.window(b, m, x, 2, d - 1, Direction.SOUTH, 2, false);
        }
        for (int z = 2; z < d - 2; z += 3) {
            Arch.window(b, m, 0, 2, z, Direction.WEST, 2, false);
            Arch.window(b, m, w - 1, 2, z, Direction.EAST, 2, false);
        }
        // dais at the back, racks and targets
        b.fill(2, 1, d - 3, w - 3, 1, d - 2, Arch.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), SlabType.BOTTOM));
        b.set(w / 2, 2, d - 2, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        for (int z = 2; z < d - 4; z += 2) {
            b.set(1, 1, z, Blocks.TARGET);
            b.set(w - 2, 1, z, Arch.axis(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Direction.Axis.Y));
            b.set(w - 2, 2, z, m.fence);
        }
        b.hanging(w / 2, H, d / 2, m.lantern.getBlock());
        b.hanging(3, H, 3, m.lantern.getBlock());
        b.hanging(w - 4, H, 3, m.lantern.getBlock());
        b.marker("npc", w / 2, 2, d - 3, npc("master"));
        b.marker("npc", 3, 1, 4, npc("student"));
    }

    // ================================================================== civic

    /** Church (9x15): nave under a steep roof, front bell tower with a spire, tall windows, pews and an altar. */
    static void church(Builder b, Mats m) {
        int w = b.w, d = b.d, tz = 4;
        site(b, m, 0, 0, w - 1, d - 1, 24);
        // nave behind the tower
        shell(b, m, 0, tz, w - 1, d - 1, 1);
        b.walls(0, H, tz, w - 1, H + 1, d - 1, m.wall.getBlock());
        for (int[] c : new int[][]{{0, tz}, {w - 1, tz}, {0, d - 1}, {w - 1, d - 1}}) b.fill(c[0], 1, c[1], c[0], H + 1, c[1], m.frame);
        if (m.roofKind == RoofKind.FLAT) {
            Arch.flat(b, m, 0, tz, w - 1, d - 1, H + 2, false, true);
            if (m.style == BuildStyle.DESERT) desertDome(b, m, w / 2, (tz + d) / 2 + 1, H + 3, 3);
        }
        else {
            Mats rm = m;
            if (m.roofKind != RoofKind.GABLE) {
                rm = Mats.of(m.style, b.seed);
                rm.roofKind = RoofKind.GABLE;
            }
            Arch.gable(b, rm, 0, tz, w - 1, d - 1, H + 2, false, 1, m.wall);
        }
        // tall windows down both sides, a round window at the back
        for (int z = tz + 2; z < d - 1; z += 3) {
            b.fill(0, 2, z, 0, 4, z, Arch.along(Blocks.GLASS_PANE.defaultBlockState(), Direction.Axis.Z));
            b.fill(w - 1, 2, z, w - 1, 4, z, Arch.along(Blocks.GLASS_PANE.defaultBlockState(), Direction.Axis.Z));
        }
        b.fill(w / 2, 3, d - 1, w / 2, 5, d - 1, Arch.along(Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState(), Direction.Axis.X));
        // tower: 5 wide in front, belfry openings, spire
        int tx1 = w / 2 - 2, tx2 = w / 2 + 2, th = 12;
        b.fill(tx1, 1, 0, tx2, th, tz, m.wall);
        b.clear(tx1 + 1, 1, 1, tx2 - 1, th - 1, tz - 1);
        for (int[] c : new int[][]{{tx1, 0}, {tx2, 0}, {tx1, tz}, {tx2, tz}}) b.fill(c[0], 1, c[1], c[0], th, c[1], m.frame);
        b.fill(tx1, 1, 0, tx2, 1, 0, m.base);
        band(b, m, tx1, 0, tx2, tz, H + 1);
        band(b, m, tx1, 0, tx2, tz, th);
        b.fill(tx1 + 1, H + 1, 1, tx2 - 1, H + 1, tz - 1, m.floor);
        b.fill(tx1 + 1, th - 3, 1, tx2 - 1, th - 3, tz - 1, m.floor);
        b.clear(w / 2, th - 2, 0, w / 2, th - 1, 0);
        b.clear(w / 2, th - 2, tz, w / 2, th - 1, tz);
        b.clear(tx1, th - 2, 2, tx1, th - 1, 2);
        b.clear(tx2, th - 2, 2, tx2, th - 1, 2);
        b.set(w / 2, th - 1, 2, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        Mats sm = Mats.of(m.style, b.seed);
        sm.roofKind = RoofKind.HIP;
        sm.eaveBrackets = false;
        if (m.roofKind == RoofKind.FLAT) {
            b.fill(tx1, th + 1, 0, tx2, th + 1, tz, m.roof);
            b.dome(w / 2, th + 1, 2, 2, m.roof.getBlock());
            b.set(w / 2, th + 4, 2, m.trim);
        } else {
            int sp = Arch.hip(b, sm, tx1, 0, tx2, tz, th + 1, 0);
            b.fill(w / 2, sp + 1, 2, w / 2, sp + 2, 2, Blocks.IRON_BARS.defaultBlockState());
        }
        // tower stair (ladder) and the west door into the tower, main door at the tower front
        b.ladderUp(tx1 + 1, 1, tz - 1, th - 3, Direction.NORTH);
        b.clear(tx1 + 1, H + 1, tz - 1, tx1 + 1, H + 1, tz - 1);
        b.clear(tx1 + 1, th - 3, tz - 1, tx1 + 1, th - 3, tz - 1);
        door(b, m, w / 2, 0, Direction.NORTH, false);
        b.clear(w / 2, 1, tz, w / 2, 3, tz);
        // pews and altar
        for (int z = tz + 2; z < d - 4; z += 2) {
            b.fill(1, 1, z, w / 2 - 1, 1, z, Arch.stair(chair(m), Direction.SOUTH, Half.BOTTOM));
            b.fill(w / 2 + 1, 1, z, w - 2, 1, z, Arch.stair(chair(m), Direction.SOUTH, Half.BOTTOM));
        }
        b.fill(1, 1, d - 3, w - 2, 1, d - 2, Arch.slab(Blocks.STONE_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
        b.set(w / 2, 1, d - 2, Blocks.CHISELED_STONE_BRICKS);
        b.set(w / 2, 2, d - 2, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        b.set(w / 2 - 2, 2, d - 2, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
        b.set(w / 2 + 2, 2, d - 2, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
        b.hanging(w / 2, H + 1, (tz + d) / 2, m.lantern.getBlock());
        b.marker("npc", w / 2, 1, d - 4, npc("priest"));
    }

    /**
     * Wano shrine (9x15, the CHURCH plot in Wano towns): a vermilion torii over a stone path between stone lanterns,
     * then a hall on a raised stone base with a veranda, dark timber and white plaster, under a flared kawara roof.
     */
    static void shrine(Builder b, Mats m) {
        int w = b.w, d = b.d, hz = 6;
        site(b, m, 0, 0, w - 1, d - 1, 16);
        b.fill(0, 0, 0, w - 1, 0, hz - 1, Blocks.GRAVEL.defaultBlockState());
        b.fill(w / 2 - 1, 0, 0, w / 2 + 1, 0, hz - 1, Blocks.STONE_BRICKS.defaultBlockState());
        // torii
        BlockState red = Blocks.RED_TERRACOTTA.defaultBlockState();
        b.fill(1, 1, 1, 1, 5, 1, red);
        b.fill(w - 2, 1, 1, w - 2, 5, 1, red);
        b.fill(1, 4, 1, w - 2, 4, 1, red);
        b.fill(0, 6, 1, w - 1, 6, 1, red);
        b.fill(0, 7, 1, w - 1, 7, 1, Arch.slab(Blocks.BLACKSTONE_SLAB.defaultBlockState(), SlabType.BOTTOM));
        b.set(0, 7, 1, Arch.stair(Blocks.BLACKSTONE_STAIRS.defaultBlockState(), Direction.EAST, Half.BOTTOM));
        b.set(w - 1, 7, 1, Arch.stair(Blocks.BLACKSTONE_STAIRS.defaultBlockState(), Direction.WEST, Half.BOTTOM));
        b.set(w / 2, 5, 1, red);
        // stone lanterns along the path
        for (int z : new int[]{3}) for (int x : new int[]{1, w - 2}) {
            b.set(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            b.set(x, 2, z, ArchBlocks.WHITE_PAPER_LANTERN.get().defaultBlockState());
            b.set(x, 3, z, Arch.slab(Blocks.STONE_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
        }
        // raised base and veranda
        b.fill(0, 0, hz, w - 1, 1, d - 1, Blocks.STONE_BRICKS.defaultBlockState());
        b.fill(0, 1, hz, w - 1, 1, d - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = w / 2 - 1; x <= w / 2 + 1; x++) b.set(x, 1, hz - 1, Arch.stair(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.SOUTH, Half.BOTTOM));
        b.foundation(w / 2 - 1, hz - 1, w / 2 + 1, hz - 1, Blocks.STONE_BRICKS, 6);
        // hall: posts, plaster panels between beams, lattice screens at the front
        int x1 = 1, x2 = w - 2, z1 = hz + 1, z2 = d - 2;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (!edge) continue;
                boolean post = (x == x1 || x == x2) && (z == z1 || z == z2 || (z - z1) % 3 == 0) || (z == z1 || z == z2) && (x - x1) % 3 == 0;
                for (int y = 2; y <= 5; y++) b.set(x, y, z, post ? m.frame : y == 2 ? m.base : m.wall);
            }
        band(b, m, x1, z1, x2, z2, 6);
        for (int x = x1 + 1; x < x2; x++) b.fill(x, 2, z1, x, 4, z1, Arch.along(m.window, Direction.Axis.X));
        b.clear(w / 2, 2, z1, w / 2, 4, z1);
        for (int z = z1 + 2; z < z2; z += 3) {
            b.fill(x1, 3, z, x1, 4, z, Arch.along(m.window, Direction.Axis.Z));
            b.fill(x2, 3, z, x2, 4, z, Arch.along(m.window, Direction.Axis.Z));
        }
        // veranda railing
        for (int z = hz; z <= d - 1; z++) {
            b.set(0, 2, z, Arch.along(m.fence, Direction.Axis.Z));
            b.set(w - 1, 2, z, Arch.along(m.fence, Direction.Axis.Z));
        }
        Mats rm = Mats.of(BuildStyle.WANO, b.seed);
        Arch.hip(b, rm, x1, z1, x2, z2, 7, 2);
        // inside: altar, offerings, candles
        b.fill(x1 + 1, 2, z2 - 1, x2 - 1, 2, z2 - 1, Arch.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), SlabType.TOP));
        b.set(w / 2, 3, z2 - 1, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        b.set(w / 2 - 1, 3, z2 - 1, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        b.set(w / 2 + 1, 3, z2 - 1, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        b.set(w / 2, 2, z1 + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(w / 2, 6, z1 + 1, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        b.hanging(w / 2, 5, (z1 + z2) / 2, ArchBlocks.RED_PAPER_LANTERN.get());
        b.marker("npc", w / 2, 2, z2 - 3, npc("priest"));
    }

    /**
     * Marine post (13x13): symmetrical two-storey barracks in pale masonry with blue trim, flag masts, a hip roof,
     * a front desk with the bounty board, a barred holding cell (marker "cell") and the officer's room upstairs.
     */
    static void marinePost(Builder b) {
        Mats m = Mats.of(BuildStyle.MARINE, b.seed);
        int w = b.w, d = b.d, cx = w / 2;
        site(b, m, 0, 0, w - 1, d - 1, 2 * H + 10);
        b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.POLISHED_ANDESITE.defaultBlockState());
        shell(b, m, 0, 1, w - 1, d - 1, 2);
        int top = Arch.hip(b, m, 0, 1, w - 1, d - 1, 2 * H + 1, 1);
        // blue band at each floor line and quoins at the corners
        b.fill(0, H, 1, w - 1, H, 1, Blocks.BLUE_CONCRETE.defaultBlockState());
        b.fill(0, H, d - 1, w - 1, H, d - 1, Blocks.BLUE_CONCRETE.defaultBlockState());
        b.fill(0, H, 1, 0, H, d - 1, Blocks.BLUE_CONCRETE.defaultBlockState());
        b.fill(w - 1, H, 1, w - 1, H, d - 1, Blocks.BLUE_CONCRETE.defaultBlockState());
        // entrance: projecting porch with a blue awning and two flag masts
        b.fill(cx - 2, 0, 0, cx + 2, 0, 0, ArchBlocks.PALE_MASONRY.get().defaultBlockState());
        b.fill(cx - 2, 1, 0, cx - 2, H - 1, 0, Arch.axis(ArchBlocks.MARBLE_COLUMN.get().defaultBlockState(), Direction.Axis.Y));
        b.fill(cx + 2, 1, 0, cx + 2, H - 1, 0, Arch.axis(ArchBlocks.MARBLE_COLUMN.get().defaultBlockState(), Direction.Axis.Y));
        for (int x = cx - 2; x <= cx + 2; x++) b.set(x, H, 0, ArchBlocks.PALE_MASONRY_SLAB.get().defaultBlockState());
        for (int x = cx - 1; x <= cx + 1; x++) b.set(x, H - 1, -1, Arch.facing(ArchBlocks.BLUE_AWNING.get().defaultBlockState(), Direction.NORTH));
        b.bigDoor(cx, 1, 1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.set(cx, 0, -1, Arch.stair(ArchBlocks.PALE_MASONRY_STAIRS.get().defaultBlockState(), Direction.SOUTH, Half.BOTTOM));
        for (int x : new int[]{1, w - 2}) {
            b.fill(x, 1, 0, x, 2 * H + 3, 0, Arch.connect(Blocks.IRON_BARS.defaultBlockState(), false, false, false, false));
            b.set(x, 2 * H + 3, -1, Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
            b.set(x, 2 * H + 2, -1, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        }
        windows(b, m, 0, 1, w - 1, d - 1, 2, cx);
        // ground floor: front desk, records, bounty board, the holding cell at the back left
        b.set(cx + 2, 1, 3, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.set(cx + 3, 1, 3, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.set(cx + 3, 1, 4, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
        b.set(w - 2, 1, 2, Blocks.CARTOGRAPHY_TABLE);
        b.chest(w - 2, 1, d - 2, Direction.WEST, LOOT_MARINE);
        b.fill(1, 1, d - 5, 4, 3, d - 5, Arch.along(Blocks.IRON_BARS.defaultBlockState(), Direction.Axis.X));
        b.set(4, 1, d - 5, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
        b.set(4, 2, d - 5, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        b.fill(4, 1, d - 4, 4, 3, d - 2, Arch.along(Blocks.IRON_BARS.defaultBlockState(), Direction.Axis.Z));
        b.marker("cell", 2, 1, d - 3, new CompoundTag());
        b.bed(1, 1, d - 3, Blocks.WHITE_BED, Direction.SOUTH);
        b.marker("bounty_board", cx - 2, 2, 2, new CompoundTag());
        b.set(cx - 2, 2, 2, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH));
        b.hanging(cx, H - 1, d / 2, ArchBlocks.BRASS_LANTERN.get());
        b.marker("npc", cx, 1, 4, npc("marine"));
        b.marker("npc", cx - 2, 1, d / 2, npc("marine"));
        // stairs up and the officer's floor
        stairsUp(b, m, w - 2, 5, Direction.SOUTH, 2);
        int u = H + 1;
        for (int x = 1; x < cx; x += 2) b.bed(x, u, d - 3, Blocks.BLUE_BED, Direction.SOUTH);
        b.set(cx + 1, u, 3, Arch.decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(cx + 1, u + 1, 3, Blocks.POTTED_FERN);
        b.set(cx + 2, u, 3, Arch.stair(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.WEST, Half.BOTTOM));
        b.set(cx - 1, u, 2, Blocks.BOOKSHELF);
        b.hanging(cx, u + H - 2, d / 2, ArchBlocks.BRASS_LANTERN.get());
        b.marker("npc", cx, u, d / 2, npc("marine_officer"));
        // roof lookout: a short mast with the Marine flag
        b.fill(cx, top + 1, (d + 1) / 2, cx, top + 4, (d + 1) / 2, Arch.connect(Blocks.IRON_BARS.defaultBlockState(), false, false, false, false));
        b.set(cx, top + 4, (d + 1) / 2 - 1, Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
    }

    /** Watchtower (5x5): stone base, timber legs braced to a lookout platform with railings and a small hip roof. */
    static void watchtower(Builder b, Mats m) {
        int h = 12;
        site(b, m, 0, 0, 4, 4, h + 8);
        b.fill(0, 1, 0, 4, 2, 4, m.base);
        b.clear(1, 1, 1, 3, 2, 3);
        b.bigDoor(2, 1, 0, m.door.getBlock(), Direction.NORTH);
        for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) b.fill(c[0], 3, c[1], c[0], h + 3, c[1], m.frame);
        // cross braces every four blocks
        for (int y = 5; y < h; y += 4) {
            BlockState bx = m.timbered() ? m.beam(Direction.Axis.X) : m.trim;
            BlockState bz = m.timbered() ? m.beam(Direction.Axis.Z) : m.trim;
            b.fill(1, y, 0, 3, y, 0, bx);
            b.fill(1, y, 4, 3, y, 4, bx);
            b.fill(0, y, 1, 0, y, 3, bz);
            b.fill(4, y, 1, 4, y, 3, bz);
        }
        b.fill(0, h, 0, 4, h, 4, m.floor);
        for (int x = 0; x <= 4; x++) {
            b.set(x, h + 1, 0, Arch.along(m.fence, Direction.Axis.X));
            b.set(x, h + 1, 4, Arch.along(m.fence, Direction.Axis.X));
        }
        for (int z = 1; z <= 3; z++) {
            b.set(0, h + 1, z, Arch.along(m.fence, Direction.Axis.Z));
            b.set(4, h + 1, z, Arch.along(m.fence, Direction.Axis.Z));
        }
        Mats rm = Mats.of(m.style, b.seed);
        rm.roofKind = m.roofKind == RoofKind.FLARED ? RoofKind.FLARED : RoofKind.HIP;
        rm.eaveBrackets = false;
        Arch.hip(b, rm, 0, 0, 4, 4, h + 4, 1);
        b.set(2, 1, 4, m.base);
        b.fill(2, 3, 4, 2, h - 1, 4, m.frame);
        b.ladderUp(2, 1, 3, h, Direction.NORTH); // the top rung fills the hatch in the platform
        b.hanging(2, h + 3, 2, m.lantern.getBlock());
        b.marker("npc", 1, h + 1, 1, npc("lookout"));
    }

    /** Lighthouse (7x7): banded round tower narrowing upward, a gallery with railings, glazed lamp room and cap. */
    static void lighthouse(Builder b, Mats m) {
        site(b, m, 0, 0, 6, 6, 26);
        b.cylinder(3, 3, 0, 0, 3, Blocks.STONE_BRICKS, false);
        b.cylinder(3, 3, 1, 2, 3, m.base.getBlock(), true);
        for (int y = 3; y <= 15; y++) {
            Block band = (y / 3) % 2 == 0 ? ArchBlocks.WHITE_PLASTER.get() : Blocks.RED_TERRACOTTA;
            b.cylinder(3, 3, y, y, 3, band, true);
        }
        for (int y = 5; y <= 13; y += 4) {
            b.set(0, y, 3, Arch.along(Blocks.GLASS_PANE.defaultBlockState(), Direction.Axis.Z));
            b.set(6, y + 2, 3, Arch.along(Blocks.GLASS_PANE.defaultBlockState(), Direction.Axis.Z));
        }
        // gallery
        b.cylinder(3, 3, 16, 16, 3, Blocks.SMOOTH_STONE, false);
        for (int x = 0; x <= 6; x++)
            for (int z = 0; z <= 6; z++) {
                double dd = Math.hypot(x - 3, z - 3);
                if (dd > 2.6 && dd <= 3.5) b.set(x, 17, z, Arch.connect(Blocks.IRON_BARS.defaultBlockState(), true, true, true, true));
            }
        b.cylinder(3, 3, 17, 18, 1, Blocks.GLASS, true);
        b.set(3, 17, 3, Blocks.SEA_LANTERN);
        b.set(3, 18, 3, Blocks.GLOWSTONE);
        b.cylinder(3, 3, 19, 19, 2, Blocks.RED_TERRACOTTA, false);
        b.cylinder(3, 3, 20, 20, 1, Blocks.RED_TERRACOTTA, false);
        b.set(3, 21, 3, Blocks.LIGHTNING_ROD);
        b.ladderUp(3, 1, 5, 16, Direction.NORTH); // against the back wall, through a hatch in the gallery floor
        b.door(3, 1, 0, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.marker("npc", 3, 1, 3, npc("keeper"));
    }

    /** Warehouse (13x9): long store with big loading doors, a hoist beam at the gable, racked cargo inside. */
    static void warehouse(Builder b, Mats m) {
        int w = b.w, d = b.d;
        site(b, m, 0, 0, w - 1, d - 1, 16);
        shell(b, m, 0, 0, w - 1, d - 1, 1);
        b.walls(0, H, 0, w - 1, H + 1, d - 1, m.upperWall.getBlock());
        wallsState(b, 0, H, 0, w - 1, H + 1, d - 1, m.upperWall);
        band(b, m, 0, 0, w - 1, d - 1, H + 2);
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) b.fill(c[0], 1, c[1], c[0], H + 2, c[1], m.frame);
        int top = roof(b, m, 0, 0, w - 1, d - 1, H + 3, true);
        // loading doors: a three-wide, four-tall opening with a lintel beam, and a hoist above
        b.clear(w / 2 - 1, 1, 0, w / 2 + 1, H, 0);
        b.fill(w / 2 - 1, H + 1, 0, w / 2 + 1, H + 1, 0, m.timbered() ? m.beam(Direction.Axis.X) : m.trim);
        b.fill(w / 2, H + 2, -1, w / 2, H + 2, -2, m.timbered() ? m.beam(Direction.Axis.Z) : m.trim);
        b.set(w / 2, H + 1, -2, Blocks.CHAIN.defaultBlockState());
        b.set(w / 2, H, -2, Blocks.CHAIN.defaultBlockState());
        b.set(w / 2, H - 1, -2, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        b.fill(w / 2 - 1, 0, -1, w / 2 + 1, 0, -1, m.floor);
        Arch.wallLantern(b, m, w / 2 - 3, 3, 0, Direction.NORTH);
        Arch.wallLantern(b, m, w / 2 + 3, 3, 0, Direction.NORTH);
        for (int x = 2; x < w - 2; x += 4) Arch.window(b, m, x, H, d - 1, Direction.SOUTH, 1, false);
        Arch.window(b, m, 0, H, d / 2, Direction.WEST, 1, false);
        Arch.window(b, m, w - 1, H, d / 2, Direction.EAST, 1, false);
        // a rack along the back wall: slab shelf over goods, sealed barrels (the warehouse's loot) on both levels
        for (int x = 1; x < w - 1; x++) {
            if (Math.abs(x - w / 2) <= 1) continue;
            int z = d - 2;
            b.set(x, 3, z, Arch.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), SlabType.BOTTOM));
            if (x % 2 == 1) b.barrel(x, 1, z, LOOT_WAREHOUSE);
            else Arch.cargo(b, x, 1, z, x * 3 + z);
            if (x % 3 == 0 || x % 4 == 0) b.barrel(x, 4, z, LOOT_WAREHOUSE);
        }
        for (int x = 1; x < w - 1; x += 2) if (Math.abs(x - w / 2) > 2) b.barrel(x, 1, 2, LOOT_WAREHOUSE);
        for (int x = 2; x < w - 1; x += 4) if (Math.abs(x - w / 2) > 2) Arch.cargo(b, x, 1, 2, x + 1);
        b.barrel(1, 1, d / 2, LOOT_WAREHOUSE);
        b.barrel(w - 2, 1, d / 2, LOOT_WAREHOUSE);
        b.barrel(w - 2, 1, d / 2 + 1, LOOT_WAREHOUSE);
        b.hanging(w / 2, H + 1, d / 2, m.lantern.getBlock());
        b.marker("npc", w / 2, 1, 2, npc("dockworker"));
    }

    /** Market stall (5x5): four posts, a striped canvas roof, counter with goods, the merchant behind. */
    static void stall(Builder b, Mats m) {
        site(b, m, 0, 0, 4, 4, 6);
        b.fill(0, 0, 0, 4, 0, 4, m.floor);
        for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], m.frame.hasProperty(RotatedPillarBlock.AXIS) ? m.frame : m.fence);
        Block awn = m.awning != null ? m.awning : ArchBlocks.RED_AWNING.get();
        for (int x = 0; x <= 4; x++) {
            b.set(x, 4, 0, Arch.facing(awn.defaultBlockState(), Direction.NORTH));
            b.set(x, 4, 4, Arch.facing(awn.defaultBlockState(), Direction.SOUTH));
            for (int z = 1; z <= 3; z++) b.set(x, 5, z, Arch.slab(Blocks.WHITE_WOOL.defaultBlockState(), SlabType.BOTTOM));
        }
        for (int x = 0; x <= 4; x++) for (int z = 1; z <= 3; z++) b.set(x, 4, z, (x % 2 == 0 ? Blocks.WHITE_WOOL : Blocks.RED_WOOL).defaultBlockState());
        for (int x = 1; x <= 3; x++) b.set(x, 1, 1, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.set(1, 2, 1, Blocks.MELON);
        b.set(3, 2, 1, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        b.barrel(1, 1, 3, LOOT_SHOP);
        b.barrel(3, 1, 3, LOOT_SHOP);
        b.set(2, 3, 3, Arch.lantern(m.lantern, true));
        b.set(-1, 0, 1, Blocks.AIR);
        b.marker("npc", 2, 1, 2, npc("merchant"));
    }

    /** Well (3x3): stone kerb around water, two posts carrying a little roof and the winch chain. */
    static void well(Builder b, Mats m) {
        b.foundation(0, 0, 2, 2, m.base.getBlock(), 6);
        b.fill(0, -4, 0, 2, 0, 2, Blocks.STONE_BRICKS.defaultBlockState());
        b.fill(1, -3, 1, 1, 0, 1, Blocks.WATER.defaultBlockState());
        b.clear(0, 1, 0, 2, 5, 2);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 2; z++)
                if (!(x == 1 && z == 1)) b.set(x, 1, z, Arch.slab(Blocks.STONE_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
        b.fill(0, 1, 1, 0, 3, 1, m.frame.hasProperty(RotatedPillarBlock.AXIS) ? m.frame : m.fence);
        b.fill(2, 1, 1, 2, 3, 1, m.frame.hasProperty(RotatedPillarBlock.AXIS) ? m.frame : m.fence);
        b.fill(0, 4, 1, 2, 4, 1, m.timbered() ? m.beam(Direction.Axis.X) : m.trim);
        b.set(1, 3, 1, Blocks.CHAIN.defaultBlockState());
        b.set(1, 2, 1, Blocks.CHAIN.defaultBlockState());
        for (int x = 0; x <= 2; x++) {
            b.set(x, 5, 0, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
            b.set(x, 5, 2, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM));
            b.set(x, 5, 1, Arch.slab(m.roofSlab, SlabType.TOP));
        }
        b.marker("npc", 1, 0, -1, npc("villager"));
    }

    /** Plaza (9x9): patterned paving, a tiered fountain, lamp posts, benches and planters; the town centre marker. */
    static void plaza(Builder b, Mats m) {
        int w = b.w, d = b.d, cx = w / 2, cz = d / 2;
        b.foundation(0, 0, w - 1, d - 1, m.base.getBlock(), 8);
        b.clear(0, 1, 0, w - 1, 6, d - 1);
        BlockState pave = pave(m), pave2 = pave2(m);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                int r = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                b.set(x, 0, z, r % 2 == 0 ? pave : pave2);
            }
        // fountain: basin, column, upper bowl
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int z = cz - 2; z <= cz + 2; z++) {
                boolean rim = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
                if (rim) b.set(x, 1, z, Arch.slab(Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), SlabType.BOTTOM));
                else b.set(x, 0, z, Blocks.WATER.defaultBlockState());
            }
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int z = cz - 2; z <= cz + 2; z++)
                if (Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2) b.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
        b.set(cx, 0, cz, Blocks.STONE_BRICKS.defaultBlockState());
        b.set(cx, 1, cz, Blocks.STONE_BRICK_WALL.defaultBlockState());
        b.set(cx, 2, cz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        b.set(cx, 3, cz, Blocks.WATER.defaultBlockState());
        // lamp posts at the corners, benches facing the fountain, planters
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            b.fill(c[0], 1, c[1], c[0], 2, c[1], Arch.connect(m.fence, false, false, false, false));
            b.set(c[0], 3, c[1], Arch.lantern(m.lantern, false));
        }
        b.set(cx, 1, 0, Arch.decor(ArchBlocks.PLANK_BENCH, Direction.SOUTH));
        b.set(cx, 1, d - 1, Arch.decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
        b.set(1, 1, 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        b.set(w - 2, 1, d - 2, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        b.marker("npc", 1, 1, cz, npc("villager"));
        b.marker("npc", w - 2, 1, cz, npc("villager"));
        b.marker("npc", cx, 1, 1, npc("child"));
        b.marker("town_center", cx, 1, cz, new CompoundTag());
    }

    static BlockState pave(Mats m) {
        return switch (m.style) {
            case DESERT -> ArchBlocks.DRESSED_SANDSTONE.get().defaultBlockState();
            case CANDY -> Blocks.PINK_TERRACOTTA.defaultBlockState();
            case CLOUD -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case CORAL -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case JUNGLE -> Blocks.PACKED_MUD.defaultBlockState();
            case FUTURE -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            case MARINE, PALACE -> ArchBlocks.PALE_MASONRY.get().defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    static BlockState pave2(Mats m) {
        return switch (m.style) {
            case DESERT -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case CANDY -> Blocks.WHITE_TERRACOTTA.defaultBlockState();
            case CLOUD -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case CORAL -> Blocks.DARK_PRISMARINE.defaultBlockState();
            case JUNGLE -> Blocks.MUD_BRICKS.defaultBlockState();
            case FUTURE -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case MARINE, PALACE -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            default -> ArchBlocks.FIELDSTONE.get().defaultBlockState();
        };
    }

    /** Farm (9x9): fenced field of two crops around an irrigation channel, a scarecrow, hay and a composter. */
    static void farm(Builder b, Mats m) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.DIRT, 6);
        b.clear(0, 1, 0, w - 1, 4, d - 1);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge) {
                    b.set(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
                    boolean gate = z == 0 && x == w / 2;
                    if (!gate) b.set(x, 1, z, Arch.connect(m.fence, z > 0 && x == 0 || z > 0 && x == w - 1, x < w - 1 && (z == 0 || z == d - 1), z < d - 1 && (x == 0 || x == w - 1), x > 0 && (z == 0 || z == d - 1)));
                    continue;
                }
                if (x == w / 2) {
                    b.set(x, 0, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                b.set(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                Block crop = x < w / 2 ? Blocks.WHEAT : (b.rand(9, 2) == 0 ? Blocks.CARROTS : Blocks.POTATOES);
                b.set(x, 1, z, crop.defaultBlockState().setValue(CropBlock.AGE, 3 + b.rand(x * 31 + z, 5)));
            }
        b.set(w / 2, 1, 0, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
        b.set(w / 2, 0, 0, Blocks.COARSE_DIRT.defaultBlockState());
        // scarecrow
        b.set(2, 1, d - 2, Blocks.OAK_FENCE.defaultBlockState());
        b.set(2, 2, d - 2, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(2, 3, d - 2, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.NORTH));
        b.set(1, 2, d - 2, Blocks.OAK_FENCE.defaultBlockState());
        b.set(3, 2, d - 2, Blocks.OAK_FENCE.defaultBlockState());
        b.set(1, 1, 1, Blocks.COMPOSTER.defaultBlockState());
        b.set(w - 2, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
        b.marker("npc", 0, 0, -1, npc("farmer"));
    }

    /** Ruined house (9x9): the legible shell of a house of the style, its roof fallen in, overgrown, bandits camping. */
    static void ruin(Builder b, Mats m) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, m.base.getBlock(), 10);
        b.clear(0, 1, 0, w - 1, 8, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, m.base);
        for (int x = 1; x < w - 1; x++)
            for (int z = 1; z < d - 1; z++)
                b.set(x, 0, z, b.rand(x * 7 + z, 3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : b.rand(x + z * 5, 4) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : m.floor);
        // walls standing to ragged heights: corners highest (the frame held), gaps where the openings were
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge) continue;
                boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                int h = corner ? 5 : 1 + b.rand(x * 13 + z * 7, 4);
                if (!corner && x == w / 2 && z == 0) continue; // the old doorway, open
                if (!corner && x % 4 == 2 && z == d - 1) h = Math.min(h, 1);
                for (int y = 1; y <= h; y++) {
                    BlockState s = y == 1 ? m.base : corner ? m.frame : (b.rand(x + y * 3 + z, 4) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : m.wall);
                    b.set(x, y, z, s);
                }
                if (!corner && h >= 2 && b.rand(x * 3 + z, 3) == 0) b.set(x, h + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            }
        // fallen roof beams and rubble inside
        BlockState beam = m.timbered() ? m.beam(Direction.Axis.X) : Arch.axis(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Direction.Axis.X);
        for (int x = 1; x < w - 2; x++) b.set(x, 1, d / 2 + (x % 3 == 0 ? 1 : 0), beam);
        b.set(2, 1, 2, Arch.stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
        b.set(3, 1, 2, m.roof);
        b.set(w - 3, 1, d - 3, Arch.slab(m.roofSlab, SlabType.BOTTOM));
        b.set(w / 2, 2, d / 2 - 1, Blocks.COBWEB.defaultBlockState());
        b.set(1, 1, d - 3, Blocks.FERN.defaultBlockState());
        b.set(w - 2, 1, 2, Blocks.SWEET_BERRY_BUSH.defaultBlockState());
        b.set(0, 2, 2, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
        // the bandits' camp: a campfire and bedrolls
        b.set(w / 2 + 1, 1, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        b.set(w - 2, 1, d - 2, Blocks.BROWN_CARPET.defaultBlockState());
        b.chest(1, 1, d - 2, Direction.EAST, LOOT_RUIN);
        b.marker("npc", w / 2, 1, 2, npc("bandit"));
        b.marker("npc", w / 2 + 1, 1, d - 3, npc("bandit"));
    }

    /** Bounty board (3x3): a framed notice board under its own little roof, lantern-lit. */
    static void bountyBoard(Builder b, Mats m) {
        b.foundation(0, 0, 2, 2, m.base.getBlock(), 4);
        b.clear(0, 1, 0, 2, 4, 2);
        b.fill(0, 0, 0, 2, 0, 2, pave(m));
        BlockState post = m.frame.hasProperty(RotatedPillarBlock.AXIS) ? m.frame : Arch.axis(ArchBlocks.DARK_BEAM.get().defaultBlockState(), Direction.Axis.Y);
        b.fill(0, 1, 1, 0, 3, 1, post);
        b.fill(2, 1, 1, 2, 3, 1, post);
        b.fill(1, 1, 1, 1, 3, 1, ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState());
        b.set(1, 2, 0, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
        for (int x = 0; x <= 2; x++) b.set(x, 4, 1, Arch.slab(m.roofSlab, SlabType.BOTTOM));
        b.set(0, 4, 0, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
        b.set(1, 4, 0, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
        b.set(2, 4, 0, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
        b.set(1, 3, 0, Arch.lantern(m.lantern, true));
        b.marker("bounty_board", 1, 2, 0, new CompoundTag());
    }

    /** Shipwright's yard (13x11): an open boat shed on heavy posts, a hull on the slip, tools and timber stacks. */
    static void shipwright(Builder b, Mats m) {
        int w = b.w, d = b.d;
        site(b, m, 0, 0, w - 1, d - 1, 14);
        b.fill(0, 0, 0, w - 1, 0, d - 1, ArchBlocks.DOCK_PLANKS.get().defaultBlockState());
        BlockState post = Arch.axis(ArchBlocks.OAK_BEAM.get().defaultBlockState(), Direction.Axis.Y);
        for (int x = 0; x < w; x += 4) {
            b.fill(x, 1, 0, x, H + 1, 0, post);
            b.fill(x, 1, d - 1, x, H + 1, d - 1, post);
        }
        b.fill(0, 1, d - 1, w - 1, H + 1, d - 1, ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState());
        for (int x = 0; x < w; x += 4) b.fill(x, 1, d - 1, x, H + 1, d - 1, post);
        b.fill(0, 1, 0, 0, H + 1, d - 1, ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState());
        b.fill(w - 1, 1, 0, w - 1, H + 1, d - 1, ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState());
        b.fill(0, H + 2, 0, w - 1, H + 2, 0, Arch.axis(ArchBlocks.OAK_BEAM.get().defaultBlockState(), Direction.Axis.X));
        b.fill(0, H + 2, d - 1, w - 1, H + 2, d - 1, Arch.axis(ArchBlocks.OAK_BEAM.get().defaultBlockState(), Direction.Axis.X));
        b.clear(1, 1, 0, w - 2, H + 1, 0);
        Mats rm = Mats.of(m.style, b.seed);
        if (rm.roofKind == RoofKind.FLAT || rm.roofKind == RoofKind.HIP || rm.roofKind == RoofKind.FLARED) {
            rm.roofKind = RoofKind.GABLE;
            rm.roof = ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState();
            rm.roofStairs = ArchBlocks.WEATHERED_PLANK_STAIRS.get().defaultBlockState();
            rm.roofSlab = ArchBlocks.WEATHERED_PLANK_SLAB.get().defaultBlockState();
        }
        Arch.gable(b, rm, 0, 0, w - 1, d - 1, H + 3, false, 1, ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState());
        // a hull frame on the slip: keel and ribs
        BlockState rib = Arch.axis(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Direction.Axis.Y);
        b.fill(w / 2, 1, 1, w / 2, 1, d - 3, Arch.axis(Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), Direction.Axis.Z));
        for (int z = 2; z < d - 3; z += 2) {
            int half = z < 3 || z > d - 5 ? 1 : 2;
            b.fill(w / 2 - half, 2, z, w / 2 - half, 3, z, rib);
            b.fill(w / 2 + half, 2, z, w / 2 + half, 3, z, rib);
            b.set(w / 2 - half + 1, 1, z, Arch.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), SlabType.TOP));
            b.set(w / 2 + half - 1, 1, z, Arch.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), SlabType.TOP));
        }
        b.fill(2, 1, d / 2, 2, 3, d / 2, Blocks.SCAFFOLDING.defaultBlockState());
        b.fill(w - 3, 1, d / 2, w - 3, 3, d / 2, Blocks.SCAFFOLDING.defaultBlockState());
        // workbenches and timber at the back
        b.set(1, 1, d - 2, Blocks.ANVIL.defaultBlockState());
        b.set(2, 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.set(3, 1, d - 2, Blocks.STONECUTTER.defaultBlockState());
        b.fill(w - 5, 1, d - 2, w - 3, 1, d - 2, Arch.axis(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Direction.Axis.X));
        b.fill(w - 5, 2, d - 2, w - 4, 2, d - 2, Arch.axis(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Direction.Axis.X));
        b.set(1, 1, 1, Arch.decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        b.chest(w - 2, 1, d - 2, Direction.WEST, LOOT_WAREHOUSE);
        b.hanging(w / 2, H + 1, d / 2, m.lantern.getBlock());
        b.hanging(w / 2, H + 1, 2, m.lantern.getBlock());
        b.set(1, 1, 0, ArchBlocks.BOLLARD.get().defaultBlockState());
        b.set(w - 2, 1, 0, ArchBlocks.BOLLARD.get().defaultBlockState());
        for (int x = w / 2 - 1; x <= w / 2 + 1; x++) { // the slipway's end: dock-plank steps down toward the water
            b.set(x, 0, -1, Arch.stair(ArchBlocks.DOCK_PLANK_STAIRS.get().defaultBlockState(), Direction.SOUTH, Half.BOTTOM));
            b.set(x, -1, -2, Arch.slab(ArchBlocks.DOCK_PLANK_SLAB.get().defaultBlockState(), SlabType.TOP));
        }
        b.foundation(w / 2 - 1, -1, w / 2 + 1, -1, ArchBlocks.WEATHERED_BEAM.get(), 8);
        b.marker("npc", w / 2 - 3, 1, 2, npc("shipwright"));
        b.marker("ship_spawn", w / 2, 0, -8, new CompoundTag());
    }

    /** Hut (5x5): a one-room dwelling under a pyramid roof (thatch or the style's), bed, barrel and hearth. */
    static void hut(Builder b, Mats m) {
        site(b, m, 0, 0, 4, 4, 9);
        shell(b, m, 0, 0, 4, 4, 1);
        Mats rm = Mats.of(m.style, b.seed);
        if (rm.roofKind != RoofKind.FLAT) {
            rm.roofKind = m.roofKind == RoofKind.FLARED ? RoofKind.FLARED : RoofKind.HIP;
            if (m.style == BuildStyle.VILLAGE || m.style == BuildStyle.GIANT || m.style == BuildStyle.JUNGLE) {
                rm.roof = ArchBlocks.THATCH.get().defaultBlockState();
                rm.roofStairs = ArchBlocks.THATCH_STAIRS.get().defaultBlockState();
                rm.roofSlab = ArchBlocks.THATCH_SLAB.get().defaultBlockState();
            }
            rm.eaveBrackets = false;
        }
        roof(b, rm, 0, 0, 4, 4, H + 1, true);
        b.bigDoor(2, 1, 0, m.door.getBlock(), Direction.NORTH);
        b.set(2, 0, -1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
        Arch.window(b, m, 0, 2, 2, Direction.WEST, 1, false);
        Arch.window(b, m, 4, 2, 2, Direction.EAST, 1, false);
        b.bed(1, 1, 2, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(3, 1, 3, LOOT_HOUSE);
        b.set(3, 1, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        b.hanging(2, 3, 2, m.lantern.getBlock());
        b.marker("npc", 2, 1, 2, npc("resident"));
    }

    // ---------------------------------------------------------------- island secrets

    /** A faint X of coarse soil over a chest buried three blocks down. */
    private static void buriedTreasure(Builder b) {
        b.fill(0, -4, 0, 4, -1, 4, Blocks.DIRT);
        for (int i = 0; i < 5; i++) {
            b.set(i, 0, i, Blocks.COARSE_DIRT);
            b.set(4 - i, 0, i, Blocks.COARSE_DIRT);
        }
        b.set(2, 0, 2, Blocks.ROOTED_DIRT);
        b.chest(2, -3, 2, Direction.NORTH, LOOT_TREASURE);
        b.marker("treasure", 2, -3, 2);
        if (b.rand(7, 3) == 0) b.set(0, 1, 4, Blocks.SKELETON_SKULL);
        b.set(4, 1, 0, Arch.axis(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Direction.Axis.X)); // a weathered marker stake
    }

    /** A mossy boulder hiding a trapdoor and ladder down to a smuggler's cellar. */
    private static void hiddenCache(Builder b) {
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++)
                for (int y = 0; y < 4; y++) {
                    double d = Math.pow((x - 3) / 3.2, 2) + Math.pow((z - 3) / 3.2, 2) + Math.pow(y / 3.4, 2);
                    if (d < 1) b.set(x, y, z, (x * 7 + z * 3 + y) % 4 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                }
        b.clear(3, 0, 0, 3, 1, 1);
        b.set(3, 0, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
        b.clear(3, -6, 1, 3, -1, 1);
        b.ladderUp(3, -6, 2, 6, Direction.NORTH);
        b.fill(0, -8, 1, 6, -8, 6, ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState());
        b.walls(0, -7, 1, 6, -4, 6, ArchBlocks.FIELDSTONE.get());
        b.clear(1, -7, 2, 5, -5, 5);
        b.fill(0, -4, 1, 6, -4, 6, ArchBlocks.FIELDSTONE.get().defaultBlockState());
        b.clear(3, -4, 1, 3, -4, 2);
        b.fill(1, -5, 3, 5, -5, 3, Arch.axis(ArchBlocks.WEATHERED_BEAM.get().defaultBlockState(), Direction.Axis.X));
        b.hanging(3, -6, 4, Blocks.LANTERN);
        b.chest(1, -7, 5, Direction.EAST, LOOT_CACHE);
        b.barrel(5, -7, 5, LOOT_WAREHOUSE);
        b.barrel(5, -7, 4, LOOT_WAREHOUSE);
        b.set(5, -7, 2, ArchBlocks.CARGO_CRATE.get().defaultBlockState());
        b.set(1, -7, 3, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        b.set(1, -7, 2, Blocks.CARTOGRAPHY_TABLE);
        b.marker("secret", 3, -7, 4);
    }

    /** A small forgotten shrine: stepped stone base, four posts under a little roof, candles and an offering chest. */
    private static void lostShrine(Builder b, Mats m) {
        b.foundation(0, 0, 6, 6, Blocks.MOSSY_STONE_BRICKS, 6);
        b.fill(0, 0, 0, 6, 0, 6, Blocks.MOSSY_STONE_BRICKS);
        b.clear(0, 1, 0, 6, 8, 6);
        b.fill(1, 1, 1, 5, 1, 5, Blocks.STONE_BRICKS);
        b.set(3, 1, 0, Arch.stair(Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState(), Direction.SOUTH, Half.BOTTOM));
        for (int[] c : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}}) b.fill(c[0], 2, c[1], c[0], 4, c[1], Arch.axis(ArchBlocks.WEATHERED_BEAM.get().defaultBlockState(), Direction.Axis.Y));
        Mats rm = Mats.of(BuildStyle.WANO, b.seed);
        rm.roof = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        rm.roofStairs = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState();
        rm.roofSlab = Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState();
        rm.eaveBrackets = false;
        Arch.hip(b, rm, 1, 1, 5, 5, 5, 1);
        b.set(3, 2, 4, Blocks.CHISELED_STONE_BRICKS);
        b.set(3, 3, 4, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3));
        b.set(2, 2, 2, Blocks.CANDLE);
        b.set(4, 2, 2, Blocks.CANDLE);
        b.chest(3, 2, 3, Direction.NORTH, LOOT_SHRINE);
        b.hanging(3, 4, 3, ArchBlocks.WHITE_PAPER_LANTERN.get());
        b.set(0, 1, 3, Blocks.FERN);
        b.set(6, 1, 2, Blocks.FERN);
        b.set(6, 1, 5, Blocks.MOSS_CARPET);
        b.marker("secret", 3, 2, 3);
    }

    /** A sunken merchant ship on the coastal shelf, listing to one side, with a chest in the hold. */
    private static void shipwreck(Builder b) {
        int w = b.w, d = b.d;
        int cx = w / 2;
        for (int z = 1; z < d - 1; z++) {
            double t = (z - d / 2.0) / (d / 2.0);
            int half = (int) Math.round((w / 2.0 - 1) * Math.sqrt(Math.max(0, 1 - t * t * t * t)));
            boolean broken = z > d * 0.62 && z < d * 0.75;
            for (int x = cx - half; x <= cx + half; x++) {
                if (broken && b.rand(z * 17 + x, 3) > 0) continue;
                b.set(x, 0, z, Blocks.DARK_OAK_PLANKS);
                if (x == cx - half || x == cx + half) {
                    int h = x == cx - half ? 3 : 1;
                    for (int y = 1; y <= h; y++) if (!(broken && y > 1)) b.set(x, y, z, z % 3 == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_PLANKS);
                }
            }
        }
        b.fill(cx, 1, d / 3, cx, 3, d / 3, Blocks.STRIPPED_SPRUCE_LOG);
        for (int i = 0; i < 7; i++) b.set(Math.min(w - 1, cx + i), 1, d / 3 + 2 + i / 3, Blocks.STRIPPED_SPRUCE_LOG);
        b.set(cx + 1, 1, d / 2, Blocks.SEAGRASS);
        b.set(cx - 2, 1, d / 4, Blocks.SEAGRASS);
        b.set(cx - 1, 1, d / 2 + 1, Arch.decor(ArchBlocks.DECK_CANNON, Direction.WEST).setValue(com.grandline.block.DecorBlock.WATERLOGGED, true));
        b.set(cx + 1, 1, d - 5, ArchBlocks.CARGO_CRATE.get().defaultBlockState());
        b.chest(cx, 1, d - 4, Direction.NORTH, LOOT_WRECK);
        b.barrel(cx - 1, 1, d - 4, LOOT_WAREHOUSE);
        b.marker("treasure", cx, 1, d - 4);
    }

    private Buildings() {}
}
