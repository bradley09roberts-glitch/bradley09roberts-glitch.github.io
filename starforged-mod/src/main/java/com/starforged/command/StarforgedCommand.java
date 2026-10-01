package com.starforged.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.starforged.boss.EclipseSummoning;
import com.starforged.entity.projectile.MeteorEntity;
import com.starforged.event.StarfallManager;
import com.starforged.registry.ModBlocks;
import com.starforged.registry.ModItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * /starforged - showcase commands for recording and testing.
 * <pre>
 *   /starforged help
 *   /starforged starfall start [seconds] | stop
 *   /starforged meteor [count]          rain meteors around you
 *   /starforged kit                     every weapon, gadget and armor piece
 *   /starforged boss                    builds an altar in front of you and begins the summoning ritual
 *   /starforged observatory             generates a Fallen Observatory where you stand
 *   /starforged locate                  finds the nearest Fallen Observatory
 * </pre>
 */
public final class StarforgedCommand {
    private StarforgedCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("starforged")
            .executes(StarforgedCommand::help)
            .then(Commands.literal("help").executes(StarforgedCommand::help))
            .then(Commands.literal("starfall")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start")
                    .executes(c -> startStarfall(c, 600))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 36000))
                        .executes(c -> startStarfall(c, IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("stop").executes(StarforgedCommand::stopStarfall)))
            .then(Commands.literal("meteor")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> meteors(c, 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                    .executes(c -> meteors(c, IntegerArgumentType.getInteger(c, "count")))))
            .then(Commands.literal("kit")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(StarforgedCommand::kit))
            .then(Commands.literal("boss")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(StarforgedCommand::boss))
            .then(Commands.literal("observatory")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> run(c, "place structure starforged:fallen_observatory ~ ~ ~")))
            .then(Commands.literal("locate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> run(c, "locate structure #starforged:observatories"))));
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        source.sendSuccess(() -> Component.literal("✦ Starforged ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        String[][] lines = {
            {"/starforged kit", "every legendary weapon, gadget & armor"},
            {"/starforged starfall start [seconds]", "make the stars fall right now"},
            {"/starforged starfall stop", "end the Starfall"},
            {"/starforged meteor [count]", "call meteors down around you"},
            {"/starforged observatory", "build a Fallen Observatory here"},
            {"/starforged locate", "find the nearest Fallen Observatory"},
            {"/starforged boss", "summon the Eclipse Sovereign (cinematic)"},
        };
        for (String[] line : lines) {
            source.sendSuccess(() -> Component.literal(line[0]).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - " + line[1]).withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int startStarfall(CommandContext<CommandSourceStack> c, int seconds) {
        ServerLevel overworld = c.getSource().getServer().overworld();
        StarfallManager.forceStart(overworld, seconds * 20L);
        c.getSource().sendSuccess(() -> Component.translatable("commands.starforged.starfall.start", seconds), true);
        return 1;
    }

    private static int stopStarfall(CommandContext<CommandSourceStack> c) {
        StarfallManager.forceStop(c.getSource().getServer().overworld());
        c.getSource().sendSuccess(() -> Component.translatable("commands.starforged.starfall.stop"), true);
        return 1;
    }

    private static int meteors(CommandContext<CommandSourceStack> c, int count) {
        ServerLevel level = c.getSource().getLevel();
        Vec3 pos = c.getSource().getPosition();
        for (int i = 0; i < count; i++) {
            MeteorEntity meteor = StarfallManager.spawnMeteorNear(level, pos, level.getRandom());
            // Stagger arrival a little for big showers.
            meteor.setPos(meteor.getX(), meteor.getY() + i * 6.0, meteor.getZ());
        }
        c.getSource().sendSuccess(() -> Component.translatable("commands.starforged.meteor", count), true);
        return count;
    }

    private static int kit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        List<Supplier<? extends Item>> gear = List.of(
            ModItems.STARCALLER_STAFF, ModItems.METEOR_HAMMER, ModItems.VOID_SCYTHE, ModItems.CONSTELLATION_BOW, ModItems.ECLIPSE_BLADE,
            ModItems.GRAVITY_GAUNTLET, ModItems.RIFT_PEARL, ModItems.ASTRAL_COMPASS, ModItems.ECLIPSE_SIGIL, ModItems.ASTRAL_EGG,
            ModItems.NEBULA_CLOAK, ModItems.STARMETAL_SWORD, ModItems.STARMETAL_PICKAXE);
        for (Supplier<? extends Item> item : gear) {
            player.getInventory().add(new ItemStack(item.get()));
        }
        player.getInventory().add(new ItemStack(ModItems.SINGULARITY_GRENADE.get(), 16));
        player.getInventory().add(new ItemStack(ModItems.STARDUST.get(), 64));
        player.getInventory().add(new ItemStack(Items.ARROW, 64));
        player.getInventory().add(new ItemStack(ModItems.CELESTIAL_ALTAR.get()));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ECLIPSE_CROWN.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARMETAL_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.STARMETAL_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.COMET_BOOTS.get()));
        c.getSource().sendSuccess(() -> Component.translatable("commands.starforged.kit"), true);
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos altar = BlockPos.containing(player.position().add(look.scale(8.0)));
        level.setBlock(altar, ModBlocks.CELESTIAL_ALTAR.get().defaultBlockState(), Block.UPDATE_ALL);
        EclipseSummoning.force(level, altar);
        c.getSource().sendSuccess(() -> Component.translatable("commands.starforged.boss"), true);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> c, String command) {
        CommandSourceStack source = c.getSource();
        source.getServer().getCommands().performPrefixedCommand(source, command);
        return 1;
    }

    @SuppressWarnings("unused")
    private static boolean isOverworld(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }
}
