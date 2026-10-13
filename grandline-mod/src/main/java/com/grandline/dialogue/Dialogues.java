package com.grandline.dialogue;

import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestEvents;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Reactive NPC dialogue: greeting depends on role, attitude, the player's bounty, race, faction and fruit.
 * Options open shops, quests, training, rumours, recruitment and services.
 */
public final class Dialogues {
    public static void open(ServerPlayer sp, GLNpc npc) {
        CharacterData d = Characters.get(sp);
        QuestEvents.onTalk(sp, npc);
        com.grandline.quest.arc.Scenes.onTalk(sp, npc); // a decision this person puts to the player, or their step's own scene
        int att = npc.attitudeTo(sp);
        String role = npc.role();
        CompoundTag t = new CompoundTag();
        t.putInt("npc", npc.getId());
        t.putString("name", npc.getDisplayName().getString());
        t.putString("role", role);
        t.putString("greeting", greetingKey(npc, d, att));
        t.putInt("attitude", att);
        ListTag opts = new ListTag();
        opts.add(StringTag.valueOf("rumor"));
        if (npc.archetype().merchant() || role.equals("shopkeeper") || role.equals("merchant") || role.equals("blacksmith")) opts.add(StringTag.valueOf("trade"));
        if (role.equals("shipwright")) opts.add(StringTag.valueOf("shipyard"));
        if (role.equals("innkeeper")) opts.add(StringTag.valueOf("rest"));
        if (role.equals("master")) opts.add(StringTag.valueOf("train"));
        if (role.equals("elder") || role.equals("master")) opts.add(StringTag.valueOf("spar")); // a practice bout (combat lessons)
        if (Faction.isLaw(npc.faction()) && d.bounty == 0) opts.add(StringTag.valueOf("bounty_turn_in"));
        boolean starter = isStarter(sp, npc);
        for (QuestDef q : QuestRegistry.offeredBy(role, npc.islandId(), starter)) {
            // faction variants: any variant of a prerequisite counts, and each faction sees only its own version
            if (QuestRegistry.canOffer(d, q)) opts.add(StringTag.valueOf("quest:" + q.id()));
        }
        if (role.equals("elder") && d.completedQuests.contains("first_voyage") && !d.flags.contains("path_chosen")) opts.add(StringTag.valueOf("path"));
        com.grandline.npc.notable.NotableHooks.decorate(sp, npc, d, t, opts, starter); // story cast: greetings, quests, recruitment
        com.grandline.navigation.Ferries.addOptions(sp, npc, opts); // ferries and the Sea Train
        com.grandline.ship.LivingSea.addOptions(sp, npc, opts); // join a harbour raid
        com.grandline.ship.HiredGunners.addOptions(sp, npc, opts); // shipwrights and dockworkers sign on gunners
        com.grandline.career.CareerPerks.addOptions(sp, npc, opts); // careers: promotion talk, supply, banner, safe house, missions, the letter
        opts.add(StringTag.valueOf("leave"));
        t.put("options", opts);
        labelGenerated(sp, t, opts);
        CompoundTag scene = com.grandline.quest.arc.Scenes.drain(sp); // story scenes play first, inside this conversation
        if (scene != null) t.put("scene", scene);
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("dialogue", t));
    }

    /**
     * Generated quests (island stories, job-board jobs) have no fixed language key: their titles, with the generated
     * names and places, travel with the dialogue as serialized components ("labels": option → component JSON).
     */
    public static void labelGenerated(ServerPlayer sp, CompoundTag t, ListTag opts) {
        CompoundTag labels = new CompoundTag();
        for (int i = 0; i < opts.size(); i++) {
            String opt = opts.getString(i);
            if (!opt.startsWith("quest:")) continue;
            QuestDef q = QuestRegistry.get(opt.substring(6));
            if (q == null || !QuestRegistry.isGenerated(q.id())) continue;
            labels.putString(opt, Component.Serializer.toJson(Component.translatable("dialogue.grandline.option.quest", QuestRegistry.title(q)), sp.registryAccess()));
        }
        if (!labels.isEmpty()) t.put("labels", labels);
    }

    private static boolean isStarter(ServerPlayer sp, GLNpc npc) {
        WorldPlan plan = WorldPlan.of(sp.server);
        if (plan == null) return true;
        Island i = plan.byId(npc.islandId());
        return i != null && i.starterFor != null;
    }

    static String greetingKey(GLNpc npc, CharacterData d, int att) {
        String role = npc.role();
        if (npc.flag(GLNpc.FLAG_SURRENDER)) return "dialogue.grandline.surrender";
        if (att < -60) return Faction.isLaw(npc.faction()) ? "dialogue.grandline.law_hostile" : "dialogue.grandline.hostile";
        if (d.bounty > 50_000_000 && npc.faction() == Faction.CIVILIAN) return "dialogue.grandline.famous_pirate";
        if (d.fruit != null && npc.getRandom().nextInt(4) == 0) return "dialogue.grandline.fruit_user";
        if (d.race.isLarge() && npc.getRandom().nextInt(3) == 0) return "dialogue.grandline.big_guest";
        if (d.race == com.grandline.player.Race.FISHMAN && npc.faction() == Faction.CIVILIAN && npc.getRandom().nextInt(3) == 0) return "dialogue.grandline.fishman_wary";
        return "dialogue.grandline.greet." + role;
    }

    /** Handle a chosen dialogue option. */
    public static void choose(ServerPlayer sp, int npcId, String option) {
        if (option.startsWith("scene:")) { // a decision in a story scene ("scene:<decision>:<choice>"); no one need be near
            String[] p = option.split(":");
            if (p.length == 3) com.grandline.quest.arc.Scenes.choose(sp, p[1], p[2]);
            return;
        }
        if (npcId == com.grandline.quest.story.JobBoard.BOARD_ID) { // a notice board, not a person
            com.grandline.quest.story.JobBoard.choose(sp, option);
            return;
        }
        Entity e = sp.level().getEntity(npcId);
        if (!(e instanceof GLNpc npc) || npc.distanceTo(sp) > 8) return;
        CharacterData d = Characters.get(sp);
        if (option.startsWith("quest:")) {
            Quests.start(sp, option.substring(6));
            return;
        }
        if (com.grandline.npc.notable.NotableHooks.choose(sp, npc, option)) return; // recruit / crew orders
        if (com.grandline.navigation.Ferries.choose(sp, npc, option)) return; // passage booked
        if (com.grandline.ship.LivingSea.choose(sp, npc, option)) return; // joined a raid
        if (com.grandline.ship.HiredGunners.choose(sp, npc, option)) return; // hired gunners menu
        if (com.grandline.career.CareerPerks.choose(sp, npc, option)) return; // careers
        switch (option) {
            case "rumor" -> {
                sp.sendSystemMessage(Component.translatable("rumor.grandline." + (1 + sp.getRandom().nextInt(24))).withStyle(ChatFormatting.ITALIC));
                com.grandline.npc.notable.NotableHooks.localRumor(sp, npc);
            }
            case "trade" -> Shops.open(sp, npc);
            case "spar" -> { // a practice bout with a partner who never hurts the player (com.grandline.combat.Sparring)
                if (!com.grandline.combat.Sparring.start(sp, npc))
                    sp.sendSystemMessage(Component.translatable("dialogue.grandline.spar.unavailable", npc.getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
            case "shipyard" -> com.grandline.ship.Shipyard.open(sp, npc);
            case "rest" -> {
                if (d.beli >= 50) {
                    d.beli -= 50;
                    sp.setHealth(sp.getMaxHealth());
                    d.stamina = d.maxStamina();
                    sp.sendSystemMessage(Component.translatable("grandline.inn.rested"));
                }
            }
            case "train" -> {
                // masters teach a style appropriate to their island for a fee
                FightingStyle st = FightingStyles.get(npc.styleId());
                long fee = 2000L * Math.max(1, npc.tier());
                if (st != null && !d.knownStyles.contains(st.id) && d.beli >= fee && (st.race == null || st.race == d.race)) {
                    d.beli -= fee;
                    d.knownStyles.add(st.id);
                    d.activeStyle = st.id;
                    com.grandline.combat.Loadouts.refresh(sp, d);
                    sp.sendSystemMessage(Component.translatable("grandline.style.learned", Component.translatable(st.translationKey())).withStyle(ChatFormatting.GOLD));
                } else sp.sendSystemMessage(Component.translatable("grandline.train.cannot", fee));
            }
            case "path" -> {
                String q = switch (d.faction) {
                    case PIRATE -> "path_pirate";
                    case MARINE -> "path_marine";
                    case REVOLUTIONARY -> "path_revolutionary";
                    case BOUNTY_HUNTER -> "path_bounty_hunter";
                    default -> null;
                };
                d.flags.add("path_chosen");
                if (q != null) Quests.start(sp, q);
                Quests.progress(sp, "talk", "path_contact", 1);
            }
            default -> {}
        }
        CharacterManager.markDirty(sp);
    }

    private Dialogues() {}
}
