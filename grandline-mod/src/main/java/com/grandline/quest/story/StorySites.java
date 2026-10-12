package com.grandline.quest.story;

import com.grandline.GrandLine;
import com.grandline.creature.SeaKing;
import com.grandline.creature.SeaKingSpawner;
import com.grandline.creature.WildBeast;
import com.grandline.creature.WildlifeSpawner;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.StoryModel.Site;
import com.grandline.quest.story.StoryModel.SiteKind;
import com.grandline.quest.story.StoryModel.SiteRef;
import com.grandline.registry.ModEntities;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The places of generated stories and jobs. While a player has a quest with places, every second: reaching a place
 * completes its "reach" objective; near a place where foes belong, the foes are there (humanoids of a role, island
 * beasts, or a Sea King offshore) — as many as the objective still needs, a few at a time, topped up after a while.
 * They are ordinary, non-persistent creatures tagged with the place, so nothing lingers once the player leaves.
 * Also credits "slay" objectives (island beasts and Sea Kings) to the player or crew captain who brings one down.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class StorySites {
    public static final int REACH_RADIUS = 10, FIELD_RADIUS = 64, SEA_FIELD_RADIUS = 160, AT_ONCE = 4;
    private static final long REFILL = 200;
    /** (player, site) → game time foes may next be fielded there. */
    private static final Map<String, Long> NEXT = new HashMap<>();

    public static String tag(String site) {
        return "gl_site:" + site;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        if (s.overworld().getGameTime() % 20 != 11) return;
        IslandStories.ensure(s);
        if (!IslandStories.hasSites()) return;
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || sp.level() != s.overworld()) continue;
            tickPlayer(sp);
        }
    }

    /** One pass for a player: reach checks and foes at nearby places (also called by the self-test). */
    public static void tickPlayer(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (d.questProgress.isEmpty()) return;
        ServerLevel l = sp.serverLevel();
        for (Map.Entry<String, CompoundTag> en : Map.copyOf(d.questProgress).entrySet()) {
            List<SiteRef> refs = IslandStories.sitesOf(en.getKey());
            if (refs.isEmpty()) continue;
            QuestDef q = QuestRegistry.get(en.getKey());
            if (q == null) continue;
            for (SiteRef ref : refs) {
                Site site = IslandStories.site(ref.site());
                if (site == null) continue;
                int left = ref.count();
                if (ref.objective() >= 0) {
                    QuestDef.Objective o = q.objectives().get(ref.objective());
                    left = o.count() - en.getValue().getInt("o" + ref.objective());
                    if (left <= 0) continue;
                }
                double dx = sp.getX() - site.x(), dz = sp.getZ() - site.z();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (site.kind() == SiteKind.REACH) {
                    if (dist <= REACH_RADIUS) {
                        sp.sendSystemMessage(Component.translatable("grandline.story.reached", site.label()).withStyle(ChatFormatting.AQUA));
                        Quests.progress(sp, "reach", site.key(), 1);
                    }
                    continue;
                }
                int radius = site.kind() == SiteKind.SEA_KING ? SEA_FIELD_RADIUS : FIELD_RADIUS;
                if (dist <= radius) field(l, sp, site, Math.min(left, AT_ONCE));
            }
        }
    }

    /** Make sure up to {@code want} foes of a place are there (topped up at most every 10 s per player). */
    public static int field(ServerLevel l, @Nullable ServerPlayer sp, Site site, int want) {
        String key = (sp == null ? "-" : sp.getStringUUID()) + "|" + site.key();
        long now = l.getGameTime();
        int have = present(l, site);
        if (have >= want || NEXT.getOrDefault(key, 0L) > now) return have;
        if (!l.hasChunkAt(new BlockPos(site.x(), WorldPlan.SEA_LEVEL, site.z()))) return have;
        int made = 0;
        for (int i = have; i < want; i++) if (spawn(l, site, i) != null) made++;
        NEXT.put(key, now + REFILL);
        return have + made;
    }

    /** Foes of this place alive near it. */
    public static int present(ServerLevel l, Site site) {
        int r = site.kind() == SiteKind.SEA_KING ? SEA_FIELD_RADIUS : 48;
        AABB box = new AABB(site.x() - r, l.getMinBuildHeight(), site.z() - r, site.x() + r, l.getMaxBuildHeight(), site.z() + r);
        String tag = tag(site.key());
        return l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.getTags().contains(tag)).size();
    }

    /** All foes of this place alive near it (self-test). */
    public static List<LivingEntity> foes(ServerLevel l, Site site) {
        int r = site.kind() == SiteKind.SEA_KING ? SEA_FIELD_RADIUS : 48;
        AABB box = new AABB(site.x() - r, l.getMinBuildHeight(), site.z() - r, site.x() + r, l.getMaxBuildHeight(), site.z() + r);
        String tag = tag(site.key());
        return l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.getTags().contains(tag));
    }

    /** Field one foe of a place now (the {@code index}-th: spread around it). Null if there is no room. */
    @Nullable
    public static Entity spawn(ServerLevel l, Site site, int index) {
        double a = index * 2.4;
        int x = site.x() + (int) Math.round(Math.cos(a) * (2 + index)), z = site.z() + (int) Math.round(Math.sin(a) * (2 + index));
        Entity made = switch (site.kind()) {
            case ROLE -> {
                BlockPos at = NotableSpawner.findStand(l, x, z, 16);
                if (at == null) yield null;
                HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
                if (n == null) yield null;
                n.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, l.random.nextFloat() * 360, 0);
                n.configure(site.what(), site.tier(), site.island(), at, "", l.random);
                n.addTag(tag(site.key()));
                l.addFreshEntity(n);
                yield n;
            }
            case BEAST -> {
                WildBeast.Kind kind = beastKind(site.what());
                BlockPos at = NotableSpawner.findStand(l, x, z, 16);
                if (kind == null || at == null) yield null;
                WildBeast b = WildlifeSpawner.spawn(l, kind, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, l.random.nextFloat() * 360);
                if (b == null) yield null;
                b.addTag(tag(site.key()));
                if (site.name() != null && index == 0) {
                    b.setCustomName(site.name());
                    b.setCustomNameVisible(true);
                }
                yield b;
            }
            case SEA_KING -> {
                SeaKing k = SeaKingSpawner.spawn(l, site.x() + 0.5, site.z() + 0.5, l.random.nextFloat() * 360, l.random.nextInt(SeaKing.VARIANTS.length), 1,
                        SeaKingSpawner.pickLength(1, l.random), WorldPlan.SEA_LEVEL - 6);
                if (k == null) yield null;
                k.addTag(tag(site.key()));
                if (site.name() != null) {
                    k.setCustomName(site.name());
                    k.setCustomNameVisible(true);
                }
                yield k;
            }
            case REACH -> null;
        };
        return made;
    }

    @Nullable
    public static WildBeast.Kind beastKind(String id) {
        for (WildBeast.Kind k : WildBeast.Kind.values()) if (k.id().equals(id)) return k;
        return null;
    }

    /** Whether a "slay" target names a creature that can be slain. */
    public static boolean slayable(String target) {
        return target.equals("sea_king") || beastKind(target) != null;
    }

    // ================================================================== slaying beasts

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide) return;
        String kind = dead instanceof WildBeast wb ? wb.kind.id() : dead instanceof SeaKing ? "sea_king" : null;
        if (kind == null) return;
        Entity by = e.getSource().getEntity();
        ServerPlayer sp = by instanceof ServerPlayer p ? p : by instanceof GLNpc g ? CrewManager.captainOf(g) : null;
        if (sp == null || sp.distanceToSqr(dead) > 160 * 160) return;
        Quests.progress(sp, "slay", kind, 1);
    }

    private StorySites() {}
}
