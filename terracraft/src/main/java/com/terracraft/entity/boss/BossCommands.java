package com.terracraft.entity.boss;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.terracraft.command.TerrariaCommand;
import com.terracraft.registry.content.MobContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.function.Supplier;

/** {@code /terraria boss spawn <boss>}, {@code /terraria boss killall}, {@code /terraria boss list}. */
public final class BossCommands {
    private static final Map<String, Entry> BOSSES = Map.of(
        "king_slime", new Entry(MobContent.KING_SLIME, BossSummoning.Arrival.FALL),
        "eye_of_cthulhu", new Entry(MobContent.EYE_OF_CTHULHU, BossSummoning.Arrival.OFFSCREEN));

    private record Entry(Supplier<? extends EntityType<? extends TerrariaBoss>> type, BossSummoning.Arrival arrival) {}

    private BossCommands() {}

    public static void register() {
        TerrariaCommand.addExtension(root -> {
            LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
            BOSSES.forEach((name, entry) -> spawn.then(Commands.literal(name).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                TerrariaBoss boss = BossSummoning.summon(player.level(), player, entry.type().get(), entry.arrival());
                if (boss == null) {
                    ctx.getSource().sendFailure(Component.literal(name + " is already alive."));
                    return 0;
                }
                return 1;
            })));
            root.then(Commands.literal("boss")
                .then(spawn)
                .then(Commands.literal("killall").executes(ctx -> {
                    int removed = 0;
                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                        for (Entity entity : level.getAllEntities()) {
                            if (entity instanceof TerrariaBoss) {
                                entity.discard();
                                removed++;
                            }
                        }
                    }
                    int count = removed;
                    ctx.getSource().sendSuccess(() -> Component.literal("Removed " + count + " bosses."), true);
                    return count;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Bosses: " + String.join(", ", BOSSES.keySet())), false);
                    return BOSSES.size();
                })));
        });
    }
}
