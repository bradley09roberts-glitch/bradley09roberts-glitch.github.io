package com.grandline.career;

import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * NPCs under a player's command: a Marine officer's squad (Commander and up: called with {@code /career squad}), a
 * revolutionary commander's cell, and the pirates who join a Supernova's banner (one by one, in conversation). They
 * follow their leader, take up the leader's fights and never turn on them; a squad serves for a while and is sent home
 * when its time is up, when the leader logs out, falls, deserts or is demoted below the rank that commands it. Squad
 * NPCs are transient: they are never restored from disk.
 */
@EventBusSubscriber(modid = com.grandline.GrandLine.MODID)
public final class Squads {
    public static final String TAG = "gl_career_squad", OF = "gl_squad_of:";
    /** How long a called squad serves, and how long a banner man stays. */
    public static final long SQUAD_TICKS = 20 * 60 * 5, BANNER_TICKS = 20 * 60 * 20;

    private static final class Member {
        final UUID npc;
        final ServerLevel level;
        final long until;

        Member(UUID npc, ServerLevel level, long until) {
            this.npc = npc;
            this.level = level;
            this.until = until;
        }
    }

    /** Leader (player UUID) → the NPCs serving them. */
    private static final Map<UUID, List<Member>> SQUADS = new HashMap<>();

    /** How many a called squad of this career and rank holds (0: this rank cannot call one). */
    public static int rankSize(CareerTrack t, int rank) {
        return switch (t) {
            case MARINE -> rank >= 11 ? 6 : rank >= 10 ? 5 : rank >= 8 ? 4 : rank >= 7 ? 3 : rank >= 6 ? 2 : 0;
            case REVOLUTIONARY -> rank >= 3 ? 4 : rank >= 2 ? 3 : 0;
            default -> 0;
        };
    }

    /** How many pirates may sail under this pirate's banner. */
    public static int bannerSize(CharacterData d) {
        if (!d.career.track.equals(CareerTrack.PIRATE.id())) return 0;
        int r = Careers.rank(d);
        return r >= 2 ? 8 : d.career.warlord ? 4 : r >= 1 ? 3 : 0;
    }

    /** The roles of a squad, strongest first. */
    static List<String> roles(CareerTrack t, int rank, int size) {
        List<String> out = new ArrayList<>();
        if (t == CareerTrack.MARINE) {
            if (rank >= 11) out.add("vice_admiral");
            if (rank >= 10) out.add("marine_captain");
            if (rank >= 8) out.add("marine_officer");
            while (out.size() < size) out.add("marine");
        } else while (out.size() < size) out.add("revolutionary");
        return out.subList(0, size);
    }

    public static List<GLNpc> members(ServerPlayer leader) {
        List<GLNpc> out = new ArrayList<>();
        for (Member m : SQUADS.getOrDefault(leader.getUUID(), List.of()))
            if (m.level.getEntity(m.npc) instanceof GLNpc n && n.isAlive()) out.add(n);
        return out;
    }

    public static boolean isMember(GLNpc npc) {
        return npc.getTags().contains(TAG);
    }

    @Nullable
    static UUID leaderOf(Entity e) {
        for (String t : e.getTags()) if (t.startsWith(OF)) return UUID.fromString(t.substring(OF.length()));
        return null;
    }

    /** Call a squad (Marines from Commander, revolutionaries from Commander). Returns how many came, or a negative error. */
    public static int call(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        int size = t == null ? 0 : rankSize(t, Careers.rank(d));
        long now = Careers.now(sp.server);
        if (size == 0) {
            Careers.tell(sp, Component.translatable("grandline.career.squad.rank").withStyle(ChatFormatting.RED));
            return -1;
        }
        if (d.career.squadReady > now) {
            Careers.tell(sp, Component.translatable("grandline.career.cooldown", (d.career.squadReady - now) / 20).withStyle(ChatFormatting.RED));
            return -2;
        }
        dismiss(sp);
        int tier = Math.max(2, Math.min(9, d.level / 8 + 1));
        int n = 0;
        for (String role : roles(t, Careers.rank(d), size)) {
            if (spawn(sp, role, tier, now + SQUAD_TICKS) != null) n++;
        }
        if (n == 0) {
            Careers.tell(sp, Component.translatable("grandline.career.squad.no_room").withStyle(ChatFormatting.RED));
            return -3;
        }
        d.career.squadReady = now + 20 * 60 * 10;
        Careers.tell(sp, Component.translatable("grandline.career.squad.called." + t.id(), n).withStyle(ChatFormatting.AQUA));
        return n;
    }

    /** One NPC at the leader's side (a squad member or a banner man). */
    @Nullable
    static HumanoidNpc spawn(ServerPlayer leader, String role, int tier, long until) {
        ServerLevel l = leader.serverLevel();
        BlockPos at = NotableSpawner.safeSpotNear(l, leader.blockPosition());
        if (at == null) at = leader.blockPosition();
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        if (n == null) return null;
        n.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, leader.getYRot(), 0);
        n.configure(role, tier, "", BlockPos.ZERO, "", l.random);
        if (!l.addFreshEntity(n)) return null;
        enlist(leader, n, until);
        l.sendParticles(ParticleTypes.CLOUD, n.getX(), n.getY() + 1, n.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        return n;
    }

    /** Put an NPC under the leader's command until {@code until}. */
    static void enlist(ServerPlayer leader, GLNpc n, long until) {
        remember(leader);
        n.addTag(TAG);
        for (String t : new ArrayList<>(n.getTags())) if (t.startsWith(OF)) n.removeTag(t);
        n.addTag(OF + leader.getUUID());
        n.setAllegiance(n.faction(), BlockPos.ZERO); // no leash to a home
        n.setTarget(null);
        if (n instanceof HumanoidNpc h) h.goalSelector.addGoal(4, new FollowGoal(h));
        SQUADS.computeIfAbsent(leader.getUUID(), k -> new ArrayList<>()).add(new Member(n.getUUID(), leader.serverLevel(), until));
    }

    /** A pirate NPC joins a Supernova's banner. */
    public static boolean recruit(ServerPlayer sp, GLNpc npc) {
        CharacterData d = Characters.get(sp);
        int room = bannerSize(d);
        if (room == 0 || isMember(npc) || npc.faction() != Faction.PIRATE || !npc.notableId().isEmpty()) return false;
        if (bannerCount(sp) >= room) {
            Careers.tell(sp, Component.translatable("grandline.career.banner.full", room).withStyle(ChatFormatting.RED));
            return false;
        }
        enlist(sp, npc, Careers.now(sp.server) + BANNER_TICKS);
        Careers.tell(sp, Component.translatable("grandline.career.banner.joined", npc.getDisplayName(), bannerCount(sp), room).withStyle(ChatFormatting.GOLD));
        return true;
    }

    public static int bannerCount(ServerPlayer sp) {
        int n = 0;
        for (GLNpc m : members(sp)) if (m.faction() == Faction.PIRATE) n++;
        return n;
    }

    /** Send the leader's people home (they vanish in a puff: back to their posts and ships). */
    public static void dismiss(ServerPlayer sp) {
        dismiss(sp.getUUID(), sp.server);
    }

    static void dismiss(UUID leader, MinecraftServer server) {
        List<Member> l = SQUADS.remove(leader);
        if (l == null) return;
        for (Member m : l) {
            if (m.level.getEntity(m.npc) instanceof GLNpc n) {
                m.level.sendParticles(ParticleTypes.CLOUD, n.getX(), n.getY() + 1, n.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                n.discard();
            }
        }
    }

    /** Every second: time served, leaders gone or out of the career, the dead. */
    static void tick(MinecraftServer server) {
        if (SQUADS.isEmpty()) return;
        long now = Careers.now(server);
        for (Iterator<Map.Entry<UUID, List<Member>>> it = SQUADS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer leader = server.getPlayerList().getPlayer(en.getKey());
            if (leader == null) leader = findLeader(en.getValue(), en.getKey());
            CharacterData d = leader == null ? null : Characters.get(leader);
            boolean leaderOk = leader != null && leader.isAlive() && d.created;
            for (Iterator<Member> mi = en.getValue().iterator(); mi.hasNext(); ) {
                Member m = mi.next();
                Entity e = m.level.getEntity(m.npc);
                boolean keep = leaderOk && e instanceof GLNpc n && n.isAlive() && now < m.until && allowed(d, n);
                if (!keep) {
                    if (e instanceof GLNpc n && n.isAlive()) n.discard();
                    mi.remove();
                }
            }
            if (en.getValue().isEmpty()) it.remove();
        }
    }

    /** Fake players (tests) are outside the player list: the members remember the leader's entity. */
    @Nullable
    private static ServerPlayer findLeader(List<Member> l, UUID leader) {
        for (Member m : l) {
            for (Player p : m.level.players()) if (p.getUUID().equals(leader) && p instanceof ServerPlayer sp) return sp;
            ServerPlayer ref = LEADERS.get(leader);
            if (ref != null && !ref.isRemoved()) return ref;
        }
        return null;
    }

    /** Leaders by UUID (also fake players, which the player list does not hold). */
    private static final Map<UUID, ServerPlayer> LEADERS = new HashMap<>();

    static void remember(ServerPlayer sp) {
        LEADERS.put(sp.getUUID(), sp);
    }

    /** Whether the member may still serve: a Marine squad needs a Marine of rank, a banner a pirate of standing. */
    private static boolean allowed(CharacterData d, GLNpc n) {
        CareerTrack t = CareerTrack.of(d.faction);
        if (n.faction() == Faction.PIRATE) return bannerSize(d) > 0;
        return t != null && rankSize(t, Careers.rank(d)) > 0 && t.faction == n.faction();
    }

    static void clear() {
        SQUADS.clear();
        LEADERS.clear();
    }

    /** Squad members never fight their leader or each other; they fight whoever their leader fights. */
    @Nullable
    static Boolean hostility(GLNpc npc, LivingEntity e) {
        UUID mine = isMember(npc) ? leaderOf(npc) : null;
        UUID theirs = e instanceof GLNpc o && isMember(o) ? leaderOf(o) : null;
        if (mine == null && theirs == null) return null;
        if (mine != null && e.getUUID().equals(mine)) return false;
        if (mine != null && mine.equals(theirs)) return false;
        if (theirs != null && npc.getUUID().equals(theirs)) return false;
        if (mine != null && e instanceof Player p) return false; // other players: only if they attack (HurtByTarget)
        return null;
    }

    /** Squad members from a previous session (saved with their chunk) are not restored. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !e.loadedFromDisk() || !(e.getEntity() instanceof GLNpc n) || !n.getTags().contains(TAG)) return;
        UUID leader = leaderOf(n);
        List<Member> l = leader == null ? null : SQUADS.get(leader);
        if (l == null || l.stream().noneMatch(m -> m.npc.equals(n.getUUID()))) e.setCanceled(true);
    }

    /** Stay with the leader, take up their fight. */
    static final class FollowGoal extends Goal {
        private final HumanoidNpc npc;
        private ServerPlayer leader;
        private int repath;

        FollowGoal(HumanoidNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private ServerPlayer leader() {
            UUID u = leaderOf(npc);
            if (u == null || !(npc.level() instanceof ServerLevel sl)) return null;
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(u);
            if (p == null) p = LEADERS.get(u);
            return p != null && p.level() == npc.level() && p.isAlive() ? p : null;
        }

        @Override
        public boolean canUse() {
            if (!isMember(npc) || npc.flag(GLNpc.FLAG_SURRENDER)) return false;
            leader = leader();
            if (leader == null) return false;
            assist();
            if (npc.getTarget() != null) return false;
            return npc.distanceToSqr(leader) > 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && npc.getTarget() == null && npc.distanceToSqr(leader) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
            leader = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (leader == null) return;
            npc.getLookControl().setLookAt(leader, 30, 30);
            double d2 = npc.distanceToSqr(leader);
            if (d2 > 24 * 24 && leader.onGround() && !leader.isPassenger()) {
                BlockPos spot = NotableSpawner.safeSpotNear(leader.serverLevel(), leader.blockPosition());
                if (spot != null) {
                    npc.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                    npc.getNavigation().stop();
                    return;
                }
            }
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(leader, d2 > 12 * 12 ? 1.2 : 1.0);
            }
        }

        private void assist() {
            LivingEntity t = null;
            int now = leader.tickCount;
            if (leader.getLastHurtByMob() != null && now - leader.getLastHurtByMobTimestamp() < 100) t = leader.getLastHurtByMob();
            else if (leader.getLastHurtMob() != null && now - leader.getLastHurtMobTimestamp() < 100) t = leader.getLastHurtMob();
            if (t != null && t != npc && t.isAlive() && !(t instanceof GLNpc o && isMember(o) && leader.getUUID().equals(leaderOf(o)))
                    && t.distanceToSqr(npc) < 32 * 32) npc.setTarget(t);
        }
    }

    private Squads() {}
}
