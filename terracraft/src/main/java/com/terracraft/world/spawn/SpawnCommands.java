package com.terracraft.world.spawn;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.terracraft.command.TerrariaCommand;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.WorldProgression;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** {@code /terraria spawns info|force [n]} and {@code /terraria killall}. */
public final class SpawnCommands {
    private SpawnCommands() {}

    public static void register() {
        TerrariaCommand.addExtension(root -> {
            root.then(Commands.literal("spawns")
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.level();
                    TerrariaLayer layer = TerrariaLayer.ofHeight(player.getBlockY());
                    SpawnRule.SpawnContext context = new SpawnRule.SpawnContext(level.dimension(), level.getBiome(player.blockPosition()),
                        layer, level.isBrightOutside(), level.canSeeSky(player.blockPosition()), level.getBlockState(player.blockPosition().below()));
                    WorldProgression progression = WorldProgression.get(level.getServer());
                    List<String> matching = new ArrayList<>();
                    for (SpawnRule rule : TerrariaSpawner.rules()) {
                        if (rule.matches(context, progression)) {
                            matching.add(rule.entity().getPath() + "(" + rule.weight() + ")");
                        }
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Enemies nearby " + TerrariaSpawner.nearbyEnemies(player) + "/"
                        + TerrariaSpawner.maxSpawns(level, layer) + "  layer " + layer.name().toLowerCase() + "  "
                        + (context.day() ? "day" : "night") + (context.sky() ? " (open sky)" : "")).withStyle(ChatFormatting.GOLD), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("Rules here: " + (matching.isEmpty() ? "none" : String.join(", ", matching))), false);
                    return matching.size();
                }))
                .then(Commands.literal("force")
                    .executes(ctx -> force(ctx.getSource().getPlayerOrException(), 1, ctx.getSource()))
                    .then(Commands.argument("groups", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> force(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "groups"), ctx.getSource())))));
            root.then(Commands.literal("killall").executes(ctx -> {
                int removed = 0;
                for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                    for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof TerrariaMob) {
                            entity.discard();
                            removed++;
                        }
                    }
                }
                int count = removed;
                ctx.getSource().sendSuccess(() -> Component.literal("Removed " + count + " Terraria enemies."), true);
                return count;
            }));
        });
    }

    private static int force(ServerPlayer player, int groups, net.minecraft.commands.CommandSourceStack source) {
        int done = 0;
        for (int attempt = 0; attempt < groups * 30 && done < groups; attempt++) {
            if (TerrariaSpawner.trySpawn(player.level(), player, player.getRandom())) {
                done++;
            }
        }
        int result = done;
        source.sendSuccess(() -> Component.literal("Spawned " + result + "/" + groups + " groups."), false);
        return done;
    }
}
