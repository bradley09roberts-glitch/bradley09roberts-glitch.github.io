package com.grandline.quest;

import com.grandline.GrandLine;
import com.grandline.npc.Archetypes;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.Map;

/**
 * Readable quest text in chat: a quest's description and a checklist of its objectives with progress, shown when the
 * quest starts and by {@code /quests} (every active quest, any time).
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class QuestText {
    private QuestText() {}

    /** The quest's description: the generated one for board and island jobs, else its {@code quest.grandline.<id>.desc} text. */
    public static Component description(QuestDef q) {
        Component generated = QuestRegistry.generatedDescription(q);
        if (generated != null) return generated;
        String key = "quest.grandline." + q.id() + ".desc";
        return Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    /** Description (grey, italic) and one line per objective, with progress from {@code prog} (null = not started). */
    public static void send(ServerPlayer sp, QuestDef q, CompoundTag prog) {
        Component desc = description(q);
        if (desc != null) sp.sendSystemMessage(desc.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            int have = prog == null ? 0 : Math.min(o.count(), prog.getInt("o" + i));
            boolean done = have >= o.count();
            MutableComponent line = Component.literal(done ? "  ✔ " : "  • ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                    .append(objective(sp.getServer(), o).withStyle(done ? ChatFormatting.GRAY : ChatFormatting.WHITE));
            if (o.count() > 1 || done) line.append(Component.literal(" (" + have + "/" + o.count() + ")").withStyle(ChatFormatting.GRAY));
            sp.sendSystemMessage(line);
        }
    }

    /** One objective in words: "Defeat Bandit ×3", "Talk to Old Fumio", "Sail to Shells Town"... */
    public static MutableComponent objective(MinecraftServer server, QuestDef.Objective o) {
        String t = o.target(), type = o.type();
        String key = "grandline.quest.obj." + type;
        return switch (type) {
            case "kill", "arrest" -> Component.translatable(key, who(t), o.count());
            case "defeat", "talk" -> Component.translatable(key, who(t));
            case "discover" -> t.startsWith("any") ? Component.translatable(key + "." + t, o.count()) : Component.translatable(key, island(server, t));
            case "collect" -> Component.translatable(key, item(t), o.count());
            case "fish" -> Component.translatable(key, t.equals("any") ? Component.translatable("grandline.quest.obj.any_fish") : item(t), o.count());
            case "slay" -> Component.translatable(key, Component.literal(pretty(t)), o.count());
            case "escort", "reach", "poneglyph", "ship", "arrested", "sink", "convoy" -> Component.translatable(key);
            case "capture", "sea_battle", "fruit_kill", "treasure" -> Component.translatable(key, o.count());
            default -> Component.literal(pretty(type) + (t.equals("any") ? "" : ": " + pretty(t)));
        };
    }

    private static Component who(String id) {
        if (NotableCharacters.get(id) != null) return Component.translatable(NotableCharacters.get(id).nameKey());
        if (Archetypes.has(id)) return Component.translatable("role.grandline." + id);
        return Component.literal(pretty(id));
    }

    private static Component island(MinecraftServer server, String id) {
        WorldPlan plan = server == null ? null : WorldPlan.of(server);
        Island isl = plan == null ? null : plan.byId(id);
        if (isl != null && isl.name != null && !isl.name.isEmpty()) return Component.literal(isl.name);
        return Component.translatable("island.grandline." + id);
    }

    private static Component item(String id) {
        if (id.startsWith("#")) return Component.literal(pretty(id.substring(id.indexOf(':') + 1)));
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.get(rl).getDescription() : Component.literal(pretty(id));
    }

    private static String pretty(String s) {
        String p = s.substring(s.indexOf(':') + 1).replace('_', ' ');
        return p.isEmpty() ? s : p.substring(0, 1).toUpperCase(Locale.ROOT) + p.substring(1);
    }

    // ------------------------------------------------------------------ /quests

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("quests").executes(c -> list(c.getSource())));
    }

    private static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        CharacterData d = Characters.get(sp);
        if (d.questProgress.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("grandline.quests.none", d.completedQuests.size()).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        sp.sendSystemMessage(Component.translatable("grandline.quests.header", d.questProgress.size(), d.completedQuests.size()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Map.Entry<String, CompoundTag> en : d.questProgress.entrySet()) {
            QuestDef q = QuestRegistry.get(en.getKey());
            if (q == null) continue;
            sp.sendSystemMessage(Component.literal("▸ ").withStyle(ChatFormatting.AQUA).append(QuestRegistry.title(q).copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
            send(sp, q, en.getValue());
        }
        return d.questProgress.size();
    }
}
