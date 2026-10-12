package com.grandline.command;

import com.grandline.GrandLine;
import com.grandline.combat.Loadouts;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.FruitManager;
import com.grandline.fruit.FruitWorldData;
import com.grandline.fruit.TerrainChanges;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.Archetypes;
import com.grandline.player.*;
import com.grandline.quest.Quests;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /grandline admin and testing commands (permission level 2). */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class GLCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("grandline").requires(s -> s.hasPermission(2))
                .then(Commands.literal("fruit")
                        .then(Commands.literal("give").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DevilFruits.available().stream().map(f -> f.id), b))
                                .executes(GLCommands::fruitGive)))
                        .then(Commands.literal("set").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DevilFruits.available().stream().map(f -> f.id), b))
                                .executes(GLCommands::fruitSet)))
                        .then(Commands.literal("remove").executes(GLCommands::fruitRemove))
                        .then(Commands.literal("awaken").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            Characters.get(sp).fruitAwakened = true;
                            Loadouts.refresh(sp, Characters.get(sp));
                            return ok(c, "Awakened");
                        }))
                        .then(Commands.literal("ledger").executes(GLCommands::fruitLedger)))
                .then(Commands.literal("char")
                        .then(Commands.literal("info").executes(GLCommands::charInfo))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData cd = Characters.get(sp);
                            if (cd.fruit != null) FruitWorldData.get(sp.server).release(sp.server, cd.fruit, sp.getUUID());
                            cd.archiveAndReset();
                            CharacterManager.applyAttributes(sp);
                            CharacterManager.markVisualsDirty(sp);
                            CharacterManager.openCreationScreen(sp);
                            return ok(c, "Character archived; create a new one");
                        }))
                        .then(Commands.literal("level").then(Commands.argument("n", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData cd = Characters.get(sp);
                            int n = IntegerArgumentType.getInteger(c, "n");
                            cd.statPoints += Math.max(0, n - cd.level) * 3;
                            cd.level = n;
                            CharacterManager.applyAttributes(sp);
                            CharacterManager.markDirty(sp);
                            return ok(c, "Level " + n);
                        })))
                        .then(Commands.literal("mastery").then(Commands.argument("key", StringArgumentType.string()).then(Commands.argument("level", IntegerArgumentType.integer(0, 100)).executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData cd = Characters.get(sp);
                            int target = IntegerArgumentType.getInteger(c, "level");
                            int xp = 0;
                            for (int i = 0; i < target; i++) xp += 60 + i * 30;
                            cd.masteryXp.put(StringArgumentType.getString(c, "key"), xp);
                            Loadouts.refresh(sp, cd);
                            return ok(c, "Mastery set");
                        }))))
                        .then(Commands.literal("style").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(FightingStyles.all().stream().map(s -> s.id), b))
                                .executes(c -> {
                                    ServerPlayer sp = c.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(c, "id");
                                    FightingStyle st = FightingStyles.get(id);
                                    if (st == null) return fail(c, "Unknown style");
                                    CharacterData cd = Characters.get(sp);
                                    cd.knownStyles.add(id);
                                    cd.activeStyle = id;
                                    Loadouts.refresh(sp, cd);
                                    return ok(c, "Learned " + id);
                                })))
                        .then(Commands.literal("haki").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData cd = Characters.get(sp);
                            cd.observationUnlocked = cd.armamentUnlocked = true;
                            cd.futureSight = cd.emission = cd.internalDestruction = true;
                            if (cd.conquerorTrait) cd.conquerorAwakened = cd.conquerorInfusion = true;
                            Loadouts.refresh(sp, cd);
                            return ok(c, "Haki unlocked");
                        }))
                        .then(Commands.literal("conqueror").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData cd = Characters.get(sp);
                            cd.conquerorTrait = cd.conquerorAwakened = true;
                            Loadouts.refresh(sp, cd);
                            return ok(c, "Conqueror's Haki awakened");
                        }))
                        .then(Commands.literal("beli").then(Commands.argument("n", IntegerArgumentType.integer(0)).executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            Characters.get(sp).beli = IntegerArgumentType.getInteger(c, "n");
                            CharacterManager.markDirty(sp);
                            return ok(c, "Beli set");
                        }))))
                .then(Commands.literal("island")
                        .then(Commands.literal("list").executes(GLCommands::islandList))
                        .then(Commands.literal("here").executes(GLCommands::islandHere))
                        .then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    WorldPlan p = WorldPlan.of(c.getSource().getServer());
                                    return SharedSuggestionProvider.suggest(p == null ? java.util.List.of() : p.islands.stream().map(i -> i.id).toList(), b);
                                }).executes(GLCommands::islandTp))))
                .then(Commands.literal("npc").then(Commands.argument("role", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Archetypes.roles(), b))
                        .executes(c -> spawnNpc(c, 0))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 10)).executes(c -> spawnNpc(c, IntegerArgumentType.getInteger(c, "tier"))))))
                .then(Commands.literal("quest").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    return Quests.start(sp, StringArgumentType.getString(c, "id")) ? ok(c, "Quest started") : fail(c, "Cannot start quest");
                })))
                .then(Commands.literal("terrain").then(Commands.literal("revert").executes(c -> ok(c, "Reverted " + TerrainChanges.revertAll(c.getSource().getServer()) + " blocks"))))
                .then(TestCommands.build()));
    }

    static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static int fruitGive(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "id");
        ItemStack s = FruitManager.create(sp.server, id);
        if (s.isEmpty()) return fail(c, "That fruit already exists in this world (" + FruitWorldData.get(sp.server).state(id) + ")");
        sp.getInventory().add(s);
        return ok(c, "Gave " + id);
    }

    private static int fruitSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "id");
        DevilFruit f = DevilFruits.get(id);
        if (f == null) return fail(c, "Unknown fruit");
        CharacterData cd = Characters.get(sp);
        if (cd.fruit != null) FruitWorldData.get(sp.server).release(sp.server, cd.fruit, sp.getUUID());
        cd.fruit = null;
        FruitWorldData.get(sp.server).claimSpecific(id);
        FruitManager.eat(sp, f);
        return ok(c, "Now a user of " + id);
    }

    private static int fruitRemove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        CharacterData cd = Characters.get(sp);
        if (cd.fruit != null) FruitWorldData.get(sp.server).release(sp.server, cd.fruit, sp.getUUID());
        cd.fruit = null;
        cd.zoanForm = "";
        Loadouts.refresh(sp, cd);
        CharacterManager.markVisualsDirty(sp);
        return ok(c, "Fruit removed");
    }

    private static int fruitLedger(CommandContext<CommandSourceStack> c) {
        FruitWorldData data = FruitWorldData.get(c.getSource().getServer());
        StringBuilder sb = new StringBuilder();
        data.snapshot().forEach((k, v) -> {
            DevilFruit f = DevilFruits.get(k);
            sb.append(k).append('=').append(v).append(f != null && DevilFruits.implemented(f) ? "" : "(planned)").append(' ');
        });
        return ok(c, sb.toString());
    }

    private static int charInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        CharacterData cd = Characters.get(sp);
        return ok(c, String.format("%s | %s %s from %s Blue | Lv %d | bounty %,d | beli %,d | fruit %s | style %s | obs %s arm %s coq %s/%s | islands %d | gen %d",
                cd.name, cd.race.id(), cd.faction.id(), cd.origin.id(), cd.level, cd.bounty, cd.beli, cd.fruit, cd.activeStyle,
                cd.observationUnlocked, cd.armamentUnlocked, cd.conquerorTrait, cd.conquerorAwakened, cd.discoveredIslands.size(), cd.generation));
    }

    private static int islandList(CommandContext<CommandSourceStack> c) {
        WorldPlan p = WorldPlan.of(c.getSource().getServer());
        if (p == null) return fail(c, "Not an ocean world");
        long canon = p.islands.stream().filter(i -> i.canon).count();
        return ok(c, p.islands.size() + " islands (" + canon + " canon/starter, " + (p.islands.size() - canon) + " original)");
    }

    private static int islandHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        WorldPlan p = WorldPlan.of(sp.server);
        if (p == null) return fail(c, "Not an ocean world");
        Island i = p.nearest(sp.getBlockX(), sp.getBlockZ(), 100000);
        if (i == null) return fail(c, "No island");
        return ok(c, i.id + " (" + i.zone.id() + ", tier " + i.tier + ", " + i.settlementType + ", r=" + i.radius + ") at " + i.x + "," + i.z + " dist " + (int) i.distance(sp.getBlockX(), sp.getBlockZ()));
    }

    private static int islandTp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        WorldPlan p = WorldPlan.of(sp.server);
        if (p == null) return fail(c, "Not an ocean world");
        Island i = p.byId(StringArgumentType.getString(c, "id"));
        if (i == null) return fail(c, "Unknown island");
        ServerLevel ow = sp.server.overworld();
        int x = i.x, z = i.z;
        var s = i.settlement();
        if (s.hasCenter) {
            x = s.centerX + 4;
            z = s.centerZ + 4;
        }
        ow.getChunk(x >> 4, z >> 4);
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        sp.teleportTo(ow, x + 0.5, y + 1, z + 0.5, sp.getYRot(), sp.getXRot());
        return ok(c, "Teleported to " + i.id);
    }

    private static int spawnNpc(CommandContext<CommandSourceStack> c, int tier) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(sp.serverLevel());
        if (npc == null) return 0;
        BlockPos p = sp.blockPosition().relative(sp.getDirection(), 4);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        npc.configure(StringArgumentType.getString(c, "role"), tier, "", p, "", sp.getRandom());
        sp.serverLevel().addFreshEntity(npc);
        return ok(c, "Spawned " + npc.role() + " tier " + npc.tier());
    }

    private GLCommands() {}
}
