package com.terracraft.npc;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.terracraft.command.TerrariaCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** {@code /terraria npc spawn <npc> | list | killall | housing}. */
public final class NpcCommands {
    private NpcCommands() {}

    static void register() {
        TerrariaCommand.addExtension(root -> {
            LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
            TownNpcs.all().forEach((id, type) -> spawn.then(Commands.literal(id).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerLevel level = player.level();
                NpcWorldData data = NpcWorldData.get(level.getServer());
                if (NpcManager.isPresent(level.getServer(), id)) {
                    ctx.getSource().sendFailure(Component.literal(id + " is already alive (use /terraria npc killall first)."));
                    return 0;
                }
                HousingChecker.Result here = HousingChecker.check(level, player.blockPosition());
                BlockPos house = here.valid() && NpcManager.occupant(level, here.anchor()) == null ? here.anchor() : null;
                TownNpc npc = NpcManager.spawn(level, data, type, house, "", true);
                if (npc != null && house == null) {
                    npc.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                }
                return npc != null ? 1 : 0;
            })));
            root.then(Commands.literal("npc")
                .then(spawn)
                .then(Commands.literal("list").executes(ctx -> {
                    NpcWorldData data = NpcWorldData.get(ctx.getSource().getServer());
                    data.records().forEach((id, record) -> ctx.getSource().sendSuccess(() -> Component.literal(id + ": " + record.name()
                        + (record.alive() ? " (alive" + record.house().map(h -> ", house " + h.toShortString()).orElse(", homeless") + ")" : " (dead)")), false));
                    ctx.getSource().sendSuccess(() -> Component.literal("Known house candidates: " + data.candidates().size()).withStyle(ChatFormatting.GRAY), false);
                    return data.records().size();
                }))
                .then(Commands.literal("killall").executes(ctx -> {
                    int removed = 0;
                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                        for (Entity entity : level.getAllEntities()) {
                            if (entity instanceof TownNpc) {
                                entity.discard();
                                removed++;
                            }
                        }
                    }
                    TownNpcs.all().keySet().forEach(id -> NpcManager.forget(ctx.getSource().getServer(), id));
                    int count = removed;
                    ctx.getSource().sendSuccess(() -> Component.literal("Removed " + count + " town NPCs and reset their records."), true);
                    return count;
                }))
                .then(Commands.literal("housing").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HousingChecker.Result result = HousingChecker.check(player.level(), player.blockPosition());
                    if (result.valid()) {
                        NpcManager.addCandidate(player.level(), player.blockPosition());
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable(result.valid() ? "message.terracraft.housing.valid" : result.translationKey(), result.volume())
                        .withStyle(result.valid() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                    return result.valid() ? 1 : 0;
                })));
        });
    }
}
