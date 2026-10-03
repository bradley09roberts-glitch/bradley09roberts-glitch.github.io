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
        "eye_of_cthulhu", new Entry(MobContent.EYE_OF_CTHULHU, BossSummoning.Arrival.OFFSCREEN),
        "eater_of_worlds", new Entry(MobContent.EATER_OF_WORLDS, BossSummoning.Arrival.BURROW),
        "brain_of_cthulhu", new Entry(MobContent.BRAIN_OF_CTHULHU, BossSummoning.Arrival.NEARBY),
        "skeletron", new Entry(MobContent.SKELETRON, BossSummoning.Arrival.NEARBY),
        "queen_bee", new Entry(MobContent.QUEEN_BEE, BossSummoning.Arrival.OFFSCREEN),
        "wall_of_flesh", new Entry(MobContent.WALL_OF_FLESH, BossSummoning.Arrival.OFFSCREEN),
        "the_twins", new Entry(MobContent.RETINAZER, BossSummoning.Arrival.OFFSCREEN),
        "destroyer", new Entry(MobContent.DESTROYER, BossSummoning.Arrival.BURROW),
        "skeletron_prime", new Entry(MobContent.SKELETRON_PRIME, BossSummoning.Arrival.OFFSCREEN));

    private record Entry(Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type, BossSummoning.Arrival arrival) {}

    private BossCommands() {}

    public static void register() {
        // every third Shadow Orb / Crimson Heart awakens the evil boss
        com.terracraft.world.evil.OrbSmashing.bossSummoner = (level, player, crimson) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                if (crimson) {
                    BossSummoning.summon(level, serverPlayer, MobContent.BRAIN_OF_CTHULHU.get(), BossSummoning.Arrival.NEARBY);
                } else {
                    BossSummoning.summon(level, serverPlayer, MobContent.EATER_OF_WORLDS.get(), BossSummoning.Arrival.BURROW);
                }
            }
        };
        TerrariaCommand.addExtension(root -> {
            LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
            BOSSES.forEach((name, entry) -> spawn.then(Commands.literal(name).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                var boss = BossSummoning.summon(player.level(), player, entry.type().get(), entry.arrival());
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
                            if (entity instanceof TerrariaBoss || entity instanceof EaterOfWorlds || entity instanceof Destroyer
                                || entity instanceof SkeletronPrime.Arm || entity instanceof BrainOfCthulhu.BrainCreeper) {
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
