package com.terracraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.OpenDevMenuPacket;
import com.terracraft.player.HealthManager;
import com.terracraft.player.ManaManager;
import com.terracraft.player.PlayerEvents;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.progression.WorldProgression;
import com.terracraft.progression.WorldVariants;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code /terraria} developer command tree (permission level 2).
 * <p>
 * Systems that come later (bosses, events, NPCs) plug their own sub-commands in through
 * {@link #addExtension}, so this class does not grow into a god-object.
 */
public final class TerrariaCommand {
    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> EXTENSIONS = new ArrayList<>();

    private TerrariaCommand() {}

    /** Lets another system attach sub-commands under {@code /terraria}. */
    public static void addExtension(Consumer<LiteralArgumentBuilder<CommandSourceStack>> extension) {
        EXTENSIONS.add(extension);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("terraria")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        root.then(Commands.literal("worldstate").executes(TerrariaCommand::worldState));

        root.then(Commands.literal("progression")
            .then(Commands.literal("list").executes(TerrariaCommand::worldState))
            .then(Commands.literal("reset").executes(ctx -> {
                ProgressionManager.reset(ctx.getSource().getServer());
                ctx.getSource().sendSuccess(() -> Component.literal("World progression reset."), true);
                return 1;
            }))
            .then(Commands.literal("set")
                .then(Commands.argument("flag", IdentifierArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                        ProgressionFlags.all().stream().filter(f -> !f.isDerived()).map(ProgressionFlag::id), builder))
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setFlag(ctx, IdentifierArgument.getId(ctx, "flag"), BoolArgumentType.getBool(ctx, "value")))))));

        root.then(Commands.literal("hardmode")
            .then(Commands.argument("value", BoolArgumentType.bool())
                .executes(ctx -> setFlag(ctx, ProgressionFlags.HARDMODE.id(), BoolArgumentType.getBool(ctx, "value")))));

        root.then(Commands.literal("evil")
            .then(Commands.literal("corruption").executes(ctx -> setEvil(ctx, WorldVariants.WorldEvil.CORRUPTION)))
            .then(Commands.literal("crimson").executes(ctx -> setEvil(ctx, WorldVariants.WorldEvil.CRIMSON))));

        root.then(Commands.literal("stats")
            .executes(ctx -> showStats(ctx, ctx.getSource().getPlayerOrException()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> showStats(ctx, EntityArgument.getPlayer(ctx, "player")))));

        root.then(Commands.literal("set")
            .then(statSetter("lifecrystals", (data, v) -> data.setLifeCrystals(v)))
            .then(statSetter("lifefruit", (data, v) -> data.setLifeFruit(v)))
            .then(statSetter("manacrystals", (data, v) -> data.setManaCrystals(v)))
            .then(statSetter("accessoryslots", (data, v) -> data.setExtraAccessorySlots(v))));

        root.then(Commands.literal("give")
            .then(Commands.literal("mana")
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        ManaManager.restore(TerraPlayerData.get(player), amount);
                        ctx.getSource().sendSuccess(() -> Component.literal("Restored " + amount + " mana."), false);
                        return amount;
                    }))));

        root.then(Commands.literal("heal").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            HealthManager.healToFull(player);
            TerraPlayerData data = TerraPlayerData.get(player);
            ManaManager.restore(data, data.maxMana());
            ctx.getSource().sendSuccess(() -> Component.literal("Healed."), false);
            return 1;
        }));

        root.then(Commands.literal("devmenu").executes(ctx -> {
            TerraNetwork.sendToPlayer(ctx.getSource().getPlayerOrException(), OpenDevMenuPacket.INSTANCE);
            return 1;
        }));

        root.then(Commands.literal("biome").then(Commands.literal("debug").executes(TerrariaCommand::biomeDebug)));

        for (Consumer<LiteralArgumentBuilder<CommandSourceStack>> extension : EXTENSIONS) {
            extension.accept(root);
        }
        dispatcher.register(root);
    }

    @FunctionalInterface
    private interface StatSetter {
        void set(TerraPlayerData data, int value);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> statSetter(String name, StatSetter setter) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int value = IntegerArgumentType.getInteger(ctx, "value");
            setter.set(TerraPlayerData.get(player), value);
            PlayerEvents.refreshAndSync(player);
            ctx.getSource().sendSuccess(() -> Component.literal("Set " + name + " to " + value + "."), false);
            return value;
        }));
    }

    private static int setFlag(CommandContext<CommandSourceStack> ctx, Identifier id, boolean value) {
        ProgressionFlag flag = ProgressionFlags.resolve(id);
        if (flag.isDerived()) {
            ctx.getSource().sendFailure(Component.literal(id + " is derived from other flags and cannot be set directly."));
            return 0;
        }
        boolean changed = ProgressionManager.set(ctx.getSource().getServer(), flag, value);
        ctx.getSource().sendSuccess(() -> Component.literal(id + " = " + value + (changed ? "" : " (unchanged)")), true);
        return 1;
    }

    private static int setEvil(CommandContext<CommandSourceStack> ctx, WorldVariants.WorldEvil evil) {
        MinecraftServer server = ctx.getSource().getServer();
        ProgressionManager.setVariants(server, WorldProgression.get(server).variants().withEvil(evil));
        ctx.getSource().sendSuccess(() -> Component.literal("World evil is now " + evil.getSerializedName() + "."), true);
        return 1;
    }

    private static int worldState(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        WorldProgression data = WorldProgression.get(server);
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("=== TerraCraft world state ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Hardmode: " + data.isHardmode() + "   World evil: " + data.variants().evil().getSerializedName()), false);
        StringBuilder ores = new StringBuilder("Ores: ");
        for (WorldVariants.OrePair pair : WorldVariants.OrePair.values()) {
            ores.append(data.variants().chosenOre(pair)).append(' ');
        }
        source.sendSuccess(() -> Component.literal(ores.toString().trim()), false);
        for (ProgressionFlag.Category category : ProgressionFlag.Category.values()) {
            MutableComponent line = Component.literal(category.name() + ": ").withStyle(ChatFormatting.YELLOW);
            boolean any = false;
            for (ProgressionFlag flag : ProgressionFlags.all()) {
                if (flag.category() == category) {
                    boolean on = data.has(flag);
                    line.append(Component.literal((on ? "*" : "") + flag.id().getPath() + " ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
                    any = true;
                }
            }
            if (any) {
                source.sendSuccess(() -> line, false);
            }
        }
        List<Identifier> custom = data.storedFlags().stream().filter(id -> ProgressionFlags.byId(id).isEmpty()).toList();
        if (!custom.isEmpty()) {
            source.sendSuccess(() -> Component.literal("CUSTOM: " + custom).withStyle(ChatFormatting.AQUA), false);
        }
        if (!data.counters().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Counters: " + data.counters()), false);
        }
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx, Player player) {
        TerraPlayerData data = TerraPlayerData.get(player);
        PlayerStats stats = data.stats();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("=== " + player.getName().getString() + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format("Life %.0f/%d (crystals %d, fruit %d)   Mana %d/%d (crystals %d)",
            player.getHealth(), stats.maxLife, data.lifeCrystals(), data.lifeFruit(), (int) data.mana(), stats.maxMana, data.manaCrystals())), false);
        source.sendSuccess(() -> Component.literal("Defense " + stats.defense() + "   Accessory slots " + data.usableAccessorySlots()
            + "   Set bonus: " + (stats.activeSetBonus.isEmpty() ? "none" : stats.activeSetBonus)), false);
        StringBuilder mods = new StringBuilder();
        for (Map.Entry<Stat, Float> entry : stats.view().entrySet()) {
            if (entry.getValue() != 0.0F) {
                mods.append(entry.getKey().getSerializedName()).append('=').append(String.format("%.2f", entry.getValue())).append(' ');
            }
        }
        source.sendSuccess(() -> Component.literal("Stats: " + (mods.isEmpty() ? "none" : mods.toString().trim())), false);
        source.sendSuccess(() -> Component.literal("Abilities: " + stats.abilities), false);
        return 1;
    }

    private static int biomeDebug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
        TerrariaLayer layer = TerrariaLayer.of(level, pos);
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Biome: " + biome + "   Terraria layer: ").append(layer.displayName()), false);
        source.sendSuccess(() -> Component.literal("Y=" + pos.getY() + "  light=" + level.getMaxLocalRawBrightness(pos)
            + "  day=" + level.isBrightOutside() + "  raining=" + level.isRaining()), false);
        return 1;
    }
}
