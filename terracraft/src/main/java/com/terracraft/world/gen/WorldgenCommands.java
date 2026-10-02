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
            .then(Commands.literal("scan")
                .executes(ctx -> scan(ctx.getSource().getPlayerOrException(), 2, ctx.getSource()))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 6))
                    .executes(ctx -> scan(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "radius"), ctx.getSource()))))));
    }

    private static int scan(ServerPlayer player, int radius, net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = player.level();
        Map<String, Integer> counts = new TreeMap<>();
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
                                && !name.contains("emerald") && !name.contains("diamond") || name.equals("life_crystal_block") || block == Blocks.CHEST) {
                                counts.merge(name.replace("deepslate_", ""), 1, Integer::sum);
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
        return counts.size();
    }
}
