package com.grandline.command;

import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.landmark.Landmarks;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Buildings;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Locale;

/**
 * Development tools for the building overhaul (op only, under /grandline test):
 * <ul>
 * <li>{@code view <target> <n>}: puts the player (spectator) at a fixed camera position for a real generated
 * structure, so the automated client can take comparable screenshots: {@code town:<island>} (overview of the
 * settlement), {@code street:<island>} (eye level in the main square), {@code dock:<island>} (the harbour),
 * {@code lm:<landmark>} (a canon set piece), {@code bld:<island>:<plot index>} (one building of the town); n picks
 * one of four directions.</li>
 * <li>{@code place <type> <style> [rotation]}: builds a settlement building of any style in front of the player.</li>
 * <li>{@code showcase <style>}: every settlement type of a style in a row on a stone pad (a development showcase).</li>
 * </ul>
 * Placement writes into the live world (not world generation), so it never touches other chunks' saved buildings.
 */
public final class StructureCommands {
    private StructureCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> view() {
        return Commands.literal("view").then(Commands.argument("target", StringArgumentType.string())
                .then(Commands.argument("n", IntegerArgumentType.integer(0, 7)).executes(c -> view(c.getSource(),
                        StringArgumentType.getString(c, "target"), IntegerArgumentType.getInteger(c, "n")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> place() {
        return Commands.literal("place").then(Commands.argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BuildingType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                .then(Commands.argument("style", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BuildStyle.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                        .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "style"), 0))
                        .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3)).executes(c -> place(c.getSource(),
                                StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "style"), IntegerArgumentType.getInteger(c, "rotation"))))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> showcase() {
        return Commands.literal("showcase").then(Commands.argument("style", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BuildStyle.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                .executes(c -> showcase(c.getSource(), StringArgumentType.getString(c, "style"))));
    }

    /**
     * {@code kit}: every block of the architectural kit on a stone gallery in front of the player (rows of eight,
     * facing the player): full blocks as a 2-high sample, stairs as a little run, slabs bottom and top, walls and panes
     * connected in a short run, shutters open on a wall, lanterns standing and hanging, props facing the player.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> kit() {
        return Commands.literal("kit").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            ServerLevel l = sp.serverLevel();
            BlockSink s = levelSink(l);
            int x0 = sp.getBlockX() - 16, y = sp.getBlockY(), z0 = sp.getBlockZ() + 5;
            var all = com.grandline.registry.ArchBlocks.ALL;
            int rows = (all.size() + 7) / 8;
            for (int dx = -2; dx < 36; dx++)
                for (int dz = -2; dz < rows * 5 + 2; dz++) {
                    s.set(x0 + dx, y - 1, z0 + dz, Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 0; dy < 6; dy++) s.set(x0 + dx, y + dy, z0 + dz, Blocks.AIR.defaultBlockState());
                }
            for (int i = 0; i < all.size(); i++) {
                int x = x0 + (i % 8) * 4, z = z0 + (i / 8) * 5;
                kitSample(s, all.get(i).get().defaultBlockState(), x, y, z);
            }
            c.getSource().sendSuccess(() -> Component.literal("Kit gallery: " + all.size() + " blocks"), true);
            return 1;
        });
    }

    private static void kitSample(BlockSink s, BlockState st, int x, int y, int z) {
        var b = st.getBlock();
        net.minecraft.core.Direction toPlayer = net.minecraft.core.Direction.NORTH;
        if (b instanceof net.minecraft.world.level.block.StairBlock) {
            for (int k = 0; k < 3; k++) s.set(x + k, y, z, st.setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.SOUTH));
            s.set(x, y + 1, z + 1, st.setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.SOUTH));
        } else if (b instanceof net.minecraft.world.level.block.SlabBlock) {
            s.set(x, y, z, st);
            s.set(x + 1, y, z, st.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            s.set(x + 2, y, z, st.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        } else if (b instanceof net.minecraft.world.level.block.WallBlock || b instanceof net.minecraft.world.level.block.IronBarsBlock) {
            for (int k = 0; k < 3; k++) for (int h = 0; h < 2; h++)
                s.set(x + k, y + h, z, com.grandline.world.build.Arch.connect(st, false, k < 2, false, k > 0));
        } else if (b instanceof net.minecraft.world.level.block.TrapDoorBlock) {
            s.set(x + 1, y, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
            s.set(x + 1, y + 1, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
            s.set(x, y, z, com.grandline.world.build.Arch.shutterOpen(b, toPlayer));
            s.set(x, y + 1, z, com.grandline.world.build.Arch.shutterOpen(b, toPlayer));
            s.set(x + 1, y, z, st);
        } else if (b instanceof net.minecraft.world.level.block.LanternBlock) {
            s.set(x, y, z, st);
            s.set(x + 1, y + 2, z, Blocks.OAK_FENCE.defaultBlockState());
            s.set(x + 1, y + 1, z, st.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        } else if (st.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            s.set(x, y, z, st.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, toPlayer));
            if (st.getBlock() instanceof com.grandline.block.DecorBlock && st.toString().contains("awning")) {
                s.set(x, y, z, Blocks.AIR.defaultBlockState());
                s.set(x - 1, y + 2, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
                s.set(x, y + 2, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
                s.set(x + 1, y + 2, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
                for (int k = -1; k <= 1; k++) s.set(x + k, y + 2, z, st.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, toPlayer));
            }
        } else {
            s.set(x, y, z, st);
            s.set(x, y + 1, z, st);
            s.set(x + 1, y, z, st);
        }
    }

    /** Writes into a live level (flag 2: no neighbour updates, like world generation); loot chests get their tables. */
    public static BlockSink levelSink(ServerLevel l) {
        return new BlockSink() {
            final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

            @Override
            public void set(int x, int y, int z, BlockState s) {
                l.setBlock(m.set(x, y, z), s, 2);
            }

            @Override
            public BlockState get(int x, int y, int z) {
                return l.getBlockState(m.set(x, y, z));
            }

            @Override
            public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
                set(x, y, z, chest);
                if (l.getBlockEntity(m.set(x, y, z)) instanceof RandomizableContainerBlockEntity rc)
                    rc.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(lootTable)), l.getRandom().nextLong());
            }
        };
    }

    private static int place(CommandSourceStack src, String type, String style, int rot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        BuildingType t;
        BuildStyle st;
        try {
            t = BuildingType.valueOf(type.toUpperCase(Locale.ROOT));
            st = BuildStyle.valueOf(style.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Unknown type or style"));
            return 0;
        }
        ServerLevel l = sp.serverLevel();
        BlockPos at = sp.blockPosition().relative(sp.getDirection(), 4);
        build(l, t, st, at.getX(), at.getY(), at.getZ(), Rotation.values()[rot], sp.getRandom().nextLong());
        src.sendSuccess(() -> Component.literal("Placed " + t.name().toLowerCase(Locale.ROOT) + " (" + st.name().toLowerCase(Locale.ROOT) + ")"), true);
        return 1;
    }

    static void build(ServerLevel l, BuildingType t, BuildStyle st, int x, int y, int z, Rotation rot, long seed) {
        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        int sx = swap ? t.d : t.w, sz = swap ? t.w : t.d;
        Plot plot = new Plot(t, x, y, z, sx, sz, rot, seed, "", true);
        Buildings.build(plot, st, new Builder(levelSink(l), x, y, z, t.w, t.d, rot, seed));
    }

    /** Every settlement type of a style in a row east of the player, on a stone pad, doors facing the player's side. */
    private static int showcase(CommandSourceStack src, String style) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        BuildStyle st;
        try {
            st = BuildStyle.valueOf(style.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Unknown style"));
            return 0;
        }
        ServerLevel l = sp.serverLevel();
        int x = sp.getBlockX() + 6, y = sp.getBlockY(), z = sp.getBlockZ() + 8;
        int len = 0;
        for (BuildingType t : BuildingType.values()) if (show(t)) len += t.w + 6;
        BlockSink s = levelSink(l);
        for (int dx = -3; dx < len + 3; dx++)
            for (int dz = -6; dz < 22; dz++) {
                s.set(x + dx, y - 1, z + dz, (dx + dz) % 9 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < 30; dy++) s.set(x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
            }
        for (BuildingType t : BuildingType.values()) {
            if (!show(t)) continue;
            build(l, t, st, x, y, z, Rotation.NONE, x * 31L + z);
            x += t.w + 6;
        }
        src.sendSuccess(() -> Component.literal("Showcase of " + st.name().toLowerCase(Locale.ROOT) + " built"), true);
        return 1;
    }

    private static boolean show(BuildingType t) {
        return t != BuildingType.LANDMARK && t != BuildingType.BURIED_TREASURE && t != BuildingType.HIDDEN_CACHE && t != BuildingType.SHIPWRECK;
    }

    // ------------------------------------------------------------------ camera positions

    private static int view(CommandSourceStack src, String target, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel l = src.getServer().overworld();
        String[] p = target.split(":");
        double cx, cz, dist, height, lookY = Double.NaN;
        if (p[0].equals("lm")) {
            Plot plot = null;
            Island host = null;
            for (ServerLevel lv : src.getServer().getAllLevels()) {
                WorldPlan plan;
                try {
                    plan = WorldPlan.forLevel(lv);
                } catch (Exception e) {
                    continue;
                }
                if (plan == null) continue;
                for (Island isl : plan.islands)
                    for (Plot q : isl.settlement().plots)
                        if (p[1].equals(q.landmark()) && plot == null) {
                            plot = q;
                            host = isl;
                            l = lv;
                        }
                if (plot != null) break;
            }
            if (plot == null) {
                src.sendFailure(Component.literal("No such landmark placed: " + p[1]));
                return 0;
            }
            cx = plot.x() + plot.sizeX() / 2.0;
            cz = plot.z() + plot.sizeZ() / 2.0;
            int size = Math.max(plot.sizeX(), plot.sizeZ());
            boolean deep = l.dimension().location().getPath().equals("deep_sea");
            dist = deep ? size * 0.6 + 4 : size * 0.9 + 10; // under water the view is short: frame from inside the dome
            height = deep ? size * 0.3 + 4 : size * 0.45 + 6;
            lookY = plot.y() + Math.min(18, size * 0.25);
            final double fx = cx, fz = cz;
            place(sp, l, fx, fz, dist, height, n, plot.y(), lookY);
            return 1;
        }
        Island isl = null;
        for (Island i : WorldPlan.of(src.getServer()).islands) if (i.id.equals(p[1])) isl = i;
        if (isl == null) {
            src.sendFailure(Component.literal("Unknown island " + p[1]));
            return 0;
        }
        Settlement st = isl.settlement();
        switch (p[0]) {
            case "town" -> {
                cx = st.centerX;
                cz = st.centerZ;
                dist = 46;
                height = 34;
            }
            case "street" -> {
                cx = st.centerX;
                cz = st.centerZ;
                dist = 14;
                height = 2.6;
            }
            case "dock" -> {
                if (!st.hasDock) {
                    src.sendFailure(Component.literal("No dock"));
                    return 0;
                }
                cx = (st.dockX + st.dockTipX()) / 2.0;
                cz = (st.dockZ + st.dockTipZ()) / 2.0;
                dist = 26;
                height = 14;
                lookY = WorldPlan.SEA_LEVEL + 2;
            }
            case "bld" -> {
                int idx = Integer.parseInt(p[2]);
                if (idx < 0 || idx >= st.plots.size()) {
                    src.sendFailure(Component.literal("Plot index out of range (" + st.plots.size() + ")"));
                    return 0;
                }
                Plot q = st.plots.get(idx);
                cx = q.x() + q.sizeX() / 2.0;
                cz = q.z() + q.sizeZ() / 2.0;
                int size = Math.max(q.sizeX(), q.sizeZ());
                dist = size + 7;
                height = 7;
                lookY = q.y() + 4;
            }
            default -> {
                src.sendFailure(Component.literal("Targets: town:<island> street:<island> dock:<island> lm:<landmark> bld:<island>:<i>"));
                return 0;
            }
        }
        l.getChunk((int) cx >> 4, (int) cz >> 4);
        int ground = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) cx, (int) cz);
        place(sp, l, cx, cz, dist, height, n, ground, Double.isNaN(lookY) ? ground + 2 : lookY);
        return 1;
    }

    /** Spectator camera at distance d and height h (above baseY) on bearing n*90+45 degrees, looking at (cx, lookY, cz). */
    private static void place(ServerPlayer sp, ServerLevel l, double cx, double cz, double d, double h, int n, double baseY, double lookY) {
        double ang = Math.toRadians(n * 90 + 45 + (n >= 4 ? 22.5 : 0));
        double x = cx + Math.sin(ang) * d, z = cz - Math.cos(ang) * d;
        l.getChunk((int) x >> 4, (int) z >> 4);
        // in the deep sea the water itself blocks motion, so stand on the sea floor instead of on the surface far above
        Heightmap.Types ground = l.dimension().location().getPath().equals("deep_sea") ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING;
        double y = Math.max(baseY + h, l.getHeight(ground, (int) x, (int) z) + 1.6);
        double dx = cx - x, dz = cz - z, dy = lookY - (y + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        sp.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        sp.teleportTo(l, x, y, z, yaw, pitch);
    }
}
