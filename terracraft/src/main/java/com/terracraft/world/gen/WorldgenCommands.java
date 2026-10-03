package com.terracraft.world.gen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.terracraft.command.TerrariaCommand;
import com.terracraft.progression.WorldProgression;
import com.terracraft.progression.WorldVariants;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;
import java.util.TreeMap;

/** {@code /terraria worldgen scan [radius]}: counts ores, Life Crystals and chests in the surrounding chunks. */
public final class WorldgenCommands {
    private WorldgenCommands() {}

    public static void register() {
        TerrariaCommand.addExtension(root -> root.then(Commands.literal("worldgen")
            .then(Commands.literal("evil").executes(ctx -> {
                long seed = ctx.getSource().getLevel().getSeed();
                var zones = com.terracraft.world.evil.EvilZones.zones(seed,
                    com.terracraft.world.evil.EvilBiomeFeature.landTest(ctx.getSource().getServer().overworld()));
                String evil = WorldProgression.get(ctx.getSource().getServer()).variants().evil().getSerializedName();
                StringBuilder text = new StringBuilder("World evil: " + evil + ". Zones:");
                for (var zone : zones) {
                    text.append(String.format(" [%d, %d r=%d]", (int) zone.x(), (int) zone.z(), (int) zone.radius()));
                }
                ctx.getSource().sendSuccess(() -> Component.literal(text.toString()).withStyle(ChatFormatting.DARK_PURPLE), false);
                return zones.size();
            }))
            .then(Commands.literal("scan")
                .executes(ctx -> scan(ctx.getSource().getPlayerOrException(), 2, ctx.getSource()))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 6))
                    .executes(ctx -> scan(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "radius"), ctx.getSource()))))));
    }

    private static int scan(ServerPlayer player, int radius, net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = player.level();
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, BlockPos> firstSeen = new TreeMap<>();
        ChunkPos center = player.chunkPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                            Block block = chunk.getBlockState(pos.set(x, y, z)).getBlock();
                            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
                            if (name.endsWith("_ore") && !name.contains("coal") && !name.contains("redstone") && !name.contains("lapis")
                                && !name.contains("emerald") && !name.contains("diamond") || name.equals("life_crystal_block") || block == Blocks.CHEST
                                || name.equals("shadow_orb") || name.equals("crimson_heart") || name.endsWith("_altar") || name.equals("ebonstone")
                                || name.equals("crimstone") || name.endsWith("corrupt_grass") || name.equals("crimson_grass")
                                || name.equals("jungle_grass") || name.equals("mud") || name.equals("hive") || name.equals("larva")
                                || name.equals("jungle_spores_plant") || name.endsWith("_brick") || name.equals("hellforge") || name.equals("locked_shadow_chest")
                                || name.equals("ash") || name.equals("hellstone")) {
                                counts.merge(name.replace("deepslate_", ""), 1, Integer::sum);
                                if (name.equals("shadow_orb") || name.equals("crimson_heart") || name.endsWith("_altar") || name.equals("larva")
                                    || name.equals("hellforge") || name.equals("locked_shadow_chest")) {
                                    firstSeen.putIfAbsent(name, new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        WorldVariants variants = WorldProgression.get(level.getServer()).variants();
        StringBuilder choice = new StringBuilder();
        for (WorldVariants.OrePair pair : WorldVariants.OrePair.values()) {
            choice.append(variants.chosenOre(pair)).append(' ');
        }
        int chunks = (2 * radius + 1) * (2 * radius + 1);
        source.sendSuccess(() -> Component.literal("World ores: " + choice.toString().trim()).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(chunks + " chunks: " + counts), false);
        if (!firstSeen.isEmpty()) {
            StringBuilder where = new StringBuilder("Found:");
            firstSeen.forEach((name, at) -> where.append(' ').append(name).append(" @ ").append(at.toShortString()).append(';'));
            source.sendSuccess(() -> Component.literal(where.toString()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        return counts.size();
    }
}
