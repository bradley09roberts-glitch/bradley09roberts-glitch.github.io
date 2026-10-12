package com.grandline.npc.notable;

import com.grandline.GrandLine;
import com.grandline.net.GLNetwork;
import com.grandline.npc.Archetype;
import com.grandline.npc.GLNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters.Disposition;
import com.grandline.npc.notable.NotableCharacters.Notable;
import com.grandline.npc.notable.NotableState.Status;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Knockdown;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestEvents;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Glue between the story cast and the rest of the game: who notables fight, yielding instead of dying, permanent
 * deaths and arrests, rejecting stale copies, dialogue (greetings, quests, recruitment) and quest credit for
 * characters who are already gone.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class NotableHooks {
    /** entity UUID → game time of its last yield (a second lethal blow while yielded or soon after is final). */
    private static final Map<UUID, Long> YIELDED = new HashMap<>();
    private static final long YIELD_RECOVER = 600, YIELD_COOLDOWN = 3600;
    /** Islands with local story rumours (rumor.grandline.story.<island>). */
    private static final Set<String> RUMOR_ISLANDS = Set.of("shiokaze_isle", "fuyugane_isle", "kirinoha_isle", "hinata_isle", "shells_town", "orange_town",
            "syrup_village", "baratie", "cocoyasi", "loguetown", "spider_miles", "lvneel", "kano_country", "ilisia_kingdom", "karate_island", "sorbet_kingdom",
            "twin_capes", "whisky_peak", "little_garden", "drum_island", "alabasta", "jaya", "long_ring_long_land", "water_7", "enies_lobby", "thriller_bark",
            "sabaody_archipelago", "amazon_lily", "rusukaina", "impel_down", "marineford", "kuraigana_island", "new_world_gate", "punk_hazard", "dressrosa",
            "green_bit", "zou", "whole_cake_island", "wano_country", "onigashima", "egghead", "baltigo", "hachinosu", "elbaph", "lodestar_island", "laugh_tale");

    // ================================================================== behaviour hooks (called from GLNpc)

    /**
     * Story override of {@link GLNpc#isHostileTo}. Crew rules first; then background NPCs leave the cast alone, and
     * the cast only picks fights with players according to its disposition. {@code null} = default rules.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        if (npc.level().isClientSide) return null;
        Boolean crew = CrewManager.hostility(npc, e);
        if (crew != null) return crew;
        boolean otherNotable = e instanceof GLNpc o && !o.notableId().isEmpty();
        if (npc.notableId().isEmpty()) return otherNotable ? Boolean.FALSE : null;
        Notable n = NotableCharacters.get(npc.notableId());
        if (n == null) return null;
        if (!(e instanceof Player)) return e instanceof GLNpc ? Boolean.FALSE : null;
        int att = npc.attitudeTo(e);
        return switch (n.disposition()) {
            case FRIENDLY -> false;
            case RIVAL -> att < -90;
            case LAWFUL -> att < -40;
            case VILLAIN -> att < 30;
        };
    }

    @Nullable
    public static Boolean killsDowned(GLNpc npc) {
        if (npc.notableId().isEmpty()) return null;
        if (CrewManager.isCrew(npc)) return false;
        Notable n = NotableCharacters.get(npc.notableId());
        return n == null ? null : n.killsDowned();
    }

    // ================================================================== world events

    /** Only the current body of a living notable may exist; stale copies (and the dead) are refused. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof GLNpc npc) || npc.notableId().isEmpty()) return;
        MinecraftServer server = e.getLevel().getServer();
        if (server == null) return;
        NotableState.Entry en = NotableState.get(server).peek(npc.notableId());
        boolean current = en != null && (en.status == Status.ALIVE || en.status == Status.RECRUITED) && npc.getUUID().equals(en.entity);
        if (!current) {
            e.setCanceled(true);
            GrandLine.LOGGER.debug("Rejected stale copy of notable {}", npc.notableId());
        }
    }

    /** Yielding: friendly characters, rivals and crew are beaten, not killed — the first time. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        Entity attacker = e.getSource().getEntity();
        if (CrewManager.friendlyFire(victim, attacker)) {
            e.setCanceled(true);
            return;
        }
        if (!(victim instanceof GLNpc npc) || npc.notableId().isEmpty()) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Notable n = NotableCharacters.get(npc.notableId());
        if (n == null || !(n.yields() || CrewManager.isCrew(npc))) return;
        if (e.getAmount() < npc.getHealth() + npc.getAbsorptionAmount()) return;
        long now = npc.level().getGameTime();
        Long last = YIELDED.get(npc.getUUID());
        if (last != null && (npc.flag(GLNpc.FLAG_SURRENDER) || now - last < YIELD_COOLDOWN)) return; // striking the yielded is final
        e.setCanceled(true);
        yieldNow(npc, n, attacker, now);
    }

    private static void yieldNow(GLNpc npc, Notable n, @Nullable Entity attacker, long now) {
        npc.setHealth(Math.max(1f, npc.getMaxHealth() * 0.2f));
        npc.setFlag(GLNpc.FLAG_SURRENDER, true);
        npc.setTarget(null);
        npc.getNavigation().stop();
        for (GLNpc other : npc.level().getEntitiesOfClass(GLNpc.class, npc.getBoundingBox().inflate(24), o -> o.getTarget() == npc)) other.setTarget(null);
        YIELDED.put(npc.getUUID(), now);
        GLNetwork.anim(npc, "surrender");
        Component msg = Component.translatable("grandline.notable.yield", npc.getDisplayName()).withStyle(ChatFormatting.YELLOW);
        for (Player p : npc.level().players()) if (p.distanceToSqr(npc) < 32 * 32) p.sendSystemMessage(msg);
        ServerPlayer credited = attacker instanceof ServerPlayer sp ? sp : attacker instanceof GLNpc g ? CrewManager.captainOf(g) : null;
        if (credited != null) Quests.progress(credited, "defeat", n.id(), 1);
    }

    /** Real deaths: the notable is gone forever; its fruit returns to the world. Crew kills count for the captain. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || !(dead.level() instanceof ServerLevel level)) return;
        if (dead instanceof ServerPlayer sp) {
            CrewManager.onPlayerDeath(sp);
            return;
        }
        if (!(dead instanceof GLNpc npc)) return;
        if (e.getSource().getEntity() instanceof GLNpc killer && CrewManager.isCrew(killer)) {
            ServerPlayer cap = CrewManager.captainOf(killer);
            if (cap != null && cap.distanceToSqr(npc) < 64 * 64) QuestEvents.onKill(cap, npc);
        }
        if (npc.notableId().isEmpty()) return;
        Notable n = NotableCharacters.get(npc.notableId());
        NotableState st = NotableState.get(level.getServer());
        NotableState.Entry en = st.peek(npc.notableId());
        if (n == null || en == null || !npc.getUUID().equals(en.entity)) return;
        Entity by = e.getSource().getEntity();
        String fate = by != null ? by.getName().getString() : e.getSource().getMsgId();
        st.set(n.id(), Status.DEFEATED, null, fate, level.getGameTime());
        YIELDED.remove(npc.getUUID());
        CrewManager.onMemberGone(level.getServer(), n.id(), false);
        NotableSpawner.releaseFruit(level.getServer(), n);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("grandline.notable.fallen",
                NotableCharacters.name(n), NotableCharacters.title(n)).withStyle(ChatFormatting.DARK_RED), false);
        GrandLine.LOGGER.info("Notable {} fell ({})", n.id(), fate);
    }

    /** Cuffed with seastone and taken away: arrested notables never return either. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof GLNpc npc) || npc.notableId().isEmpty()) return;
        if (npc.getRemovalReason() != Entity.RemovalReason.DISCARDED || !npc.flag(GLNpc.FLAG_ARRESTED)) return;
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Notable n = NotableCharacters.get(npc.notableId());
        NotableState st = NotableState.get(level.getServer());
        NotableState.Entry en = st.peek(npc.notableId());
        if (n == null || en == null || !npc.getUUID().equals(en.entity)) return;
        Player by = level.getNearestPlayer(npc, 8);
        st.set(n.id(), Status.ARRESTED, null, by != null ? by.getName().getString() : "", level.getGameTime());
        CrewManager.onMemberGone(level.getServer(), n.id(), true);
        NotableSpawner.releaseFruit(level.getServer(), n);
        if (by instanceof ServerPlayer sp) Quests.progress(sp, "defeat", n.id(), 1);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("grandline.notable.arrested",
                NotableCharacters.name(n), NotableCharacters.title(n)).withStyle(ChatFormatting.GOLD), false);
    }

    /** Recovery after yielding, and friendly/rival notables sparing players they knocked down. */
    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 20 != 3) return;
        if (!YIELDED.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = YIELDED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> y = it.next();
                long age = now - y.getValue();
                if (age >= YIELD_RECOVER) {
                    for (ServerLevel l : server.getAllLevels()) {
                        if (l.getEntity(y.getKey()) instanceof GLNpc npc && npc.flag(GLNpc.FLAG_SURRENDER)) {
                            npc.setFlag(GLNpc.FLAG_SURRENDER, false);
                            npc.setHealth(Math.max(npc.getHealth(), npc.getMaxHealth() * 0.6f));
                        }
                    }
                }
                if (age > YIELD_COOLDOWN) it.remove();
            }
        }
        if (now % 40 != 3) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            CharacterData d = Characters.get(sp);
            if (!d.isKnockedDown() || d.knockedBy == null) continue;
            if (!(sp.serverLevel().getEntity(d.knockedBy) instanceof GLNpc npc) || npc.notableId().isEmpty()) continue;
            Notable n = NotableCharacters.get(npc.notableId());
            if (n == null || !(n.disposition() == Disposition.FRIENDLY || n.disposition() == Disposition.RIVAL) || CrewManager.isCrew(npc)) continue;
            npc.setTarget(null);
            Knockdown.standUp(sp, d, 0.25f);
            sp.sendSystemMessage(Component.translatable("grandline.notable.spared", npc.getDisplayName()).withStyle(ChatFormatting.YELLOW));
            CharacterManager.markDirty(sp);
        }
    }

    // ================================================================== quests

    /** Objectives that need a notable who is already gone are credited when the player visits where they lived. */
    static void creditFallen(ServerPlayer sp, Notable n) {
        CharacterData d = Characters.get(sp);
        if (d.questProgress.isEmpty()) return;
        boolean any = false;
        for (Map.Entry<String, CompoundTag> q : d.questProgress.entrySet()) {
            QuestDef def = QuestRegistry.get(q.getKey());
            if (def == null) continue;
            for (int i = 0; i < def.objectives().size(); i++) {
                QuestDef.Objective o = def.objectives().get(i);
                if (o.target().equals(n.id()) && (o.type().equals("defeat") || o.type().equals("talk") || o.type().equals("arrest")) && q.getValue().getInt("o" + i) < o.count()) any = true;
            }
        }
        if (!any) return;
        NotableState.Entry en = NotableState.get(sp.server).entry(n.id());
        sp.sendSystemMessage(Component.translatable(en.status == Status.ARRESTED ? "grandline.notable.credit_arrested" : "grandline.notable.credit_fallen",
                NotableCharacters.name(n), en.fate).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Quests.progress(sp, "defeat", n.id(), 1);
        Quests.progress(sp, "talk", n.id(), 1);
        Quests.progress(sp, "arrest", n.id(), 1);
    }

    // ================================================================== dialogue (called from Dialogues)

    /**
     * Adds the story layer to a conversation: a notable's name, title and own greeting, its quests (including its
     * crew member's personal quest), recruitment and crew orders. Ordinary islanders offer the quests of notables
     * from their island who are gone for good.
     */
    public static void decorate(ServerPlayer sp, GLNpc npc, CharacterData d, CompoundTag t, ListTag opts, boolean starter) {
        if (npc.notableId().isEmpty()) {
            Archetype.Hostility h = npc.archetype().hostility();
            if (h != Archetype.Hostility.PASSIVE && h != Archetype.Hostility.DEFENSIVE) return;
            NotableState st = NotableState.get(sp.server);
            for (Notable n : NotableCharacters.onIsland(npc.islandId())) if (st.isGone(n.id())) addQuests(d, opts, n, npc.islandId(), starter, false, true);
            return;
        }
        Notable n = NotableCharacters.get(npc.notableId());
        if (n == null) return;
        t.putString("name", NotableCharacters.name(n).getString() + " — " + NotableCharacters.title(n).getString());
        boolean mine = CrewManager.isCrewOf(npc, sp);
        boolean taken = !mine && CrewManager.isCrew(npc);
        String g = t.getString("greeting");
        boolean hostileGreeting = g.equals("dialogue.grandline.hostile") || g.equals("dialogue.grandline.law_hostile");
        boolean warm = mine || n.disposition() == Disposition.FRIENDLY || n.disposition() == Disposition.RIVAL;
        if (npc.flag(GLNpc.FLAG_SURRENDER)) return;
        if (hostileGreeting && !warm) return;
        t.putString("greeting", mine && n.recruitable() ? n.crewGreetingKey() : taken ? "dialogue.grandline.crew.taken" : n.greetingKey());
        Component generated = com.grandline.quest.story.IslandStories.greeting(n, d); // generated people: their line depends on the story so far
        if (generated != null) t.putString("greetingText", Component.Serializer.toJson(generated, sp.registryAccess()));
        addQuests(d, opts, n, npc.islandId(), starter, mine, false);
        if (mine) {
            opts.add(StringTag.valueOf(CrewManager.isWaiting(npc) ? "crew_follow" : "crew_wait"));
            opts.add(StringTag.valueOf("crew_dismiss"));
        } else if (n.recruitable() && !taken && d.created && QuestRegistry.isDone(d, n.recruitQuest())) {
            opts.add(StringTag.valueOf("recruit"));
        }
    }

    /** @param fallback offered by islanders on behalf of a notable who is gone: their recruitment/crew quests die with them */
    private static void addQuests(CharacterData d, ListTag opts, Notable n, String islandId, boolean starter, boolean mine, boolean fallback) {
        for (QuestDef q : QuestRegistry.offeredBy(n.giverRole(), islandId, starter)) {
            if (q.arc().equals("crew") && !mine) continue;
            if (fallback && q.arc().equals("recruit")) continue;
            if (!QuestRegistry.canOffer(d, q)) continue;
            StringTag opt = StringTag.valueOf("quest:" + q.id());
            if (!opts.contains(opt)) opts.add(opt);
        }
    }

    /** Story dialogue options. Returns true when handled. */
    public static boolean choose(ServerPlayer sp, GLNpc npc, String option) {
        switch (option) {
            case "recruit" -> CrewManager.recruit(sp, npc);
            case "crew_wait" -> CrewManager.setWaiting(sp, npc, true);
            case "crew_follow" -> CrewManager.setWaiting(sp, npc, false);
            case "crew_dismiss" -> CrewManager.dismiss(sp, npc);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** After the usual gossip, islanders sometimes add something about their own island's story. */
    public static void localRumor(ServerPlayer sp, GLNpc npc) {
        if (sp.getRandom().nextInt(2) != 0) return;
        Component generated = RUMOR_ISLANDS.contains(npc.islandId()) ? null : com.grandline.quest.story.IslandStories.rumor(npc.islandId());
        if (generated != null) sp.sendSystemMessage(generated.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)); // original islands' own stories
        if (!RUMOR_ISLANDS.contains(npc.islandId())) return;
        sp.sendSystemMessage(Component.translatable("rumor.grandline.story." + npc.islandId()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
    }

    /** Islands with a local story rumour (for tests). */
    public static Set<String> rumorIslands() {
        return RUMOR_ISLANDS;
    }

    private NotableHooks() {}
}
