package com.grandline.npc.crew;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableCharacters.Notable;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.npc.notable.NotableState;
import com.grandline.npc.notable.NotableState.Status;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Crews of recruited notables. Membership is saved per <b>character</b> id ({@code grandline_crews}), so a new
 * character after a permadeath starts without a crew: when a captain dies for good the crew disbands and every
 * member goes home. Members follow their captain ({@link CrewFollowGoal}), fight the captain's enemies, never hurt
 * the captain or each other, and travel "aboard" (stowed as data) when the captain sails far away or logs out,
 * stepping off again next to the captain once they are back on solid ground.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CrewManager {
    public static final int MAX_CREW = 18;
    private static final double STOW_DIST = 96, TELEPORT_DIST = 40, LEASH = 32;

    // ================================================================== saved data

    public static final class Member {
        final String id;
        boolean waiting;
        long home;
        @Nullable CompoundTag stowed, snapshot;
        int missing;

        Member(String id) {
            this.id = id;
        }
    }

    public static final class Crew {
        UUID player;
        String captainName = "";
        final LinkedHashMap<String, Member> members = new LinkedHashMap<>();
    }

    public static final class CrewData extends SavedData {
        final Map<UUID, Crew> crews = new LinkedHashMap<>();
        final Map<String, UUID> captainOf = new HashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            crews.forEach((charId, c) -> {
                CompoundTag ct = new CompoundTag();
                ct.putUUID("character", charId);
                ct.putUUID("player", c.player);
                ct.putString("captain", c.captainName);
                ListTag ms = new ListTag();
                for (Member m : c.members.values()) {
                    CompoundTag mt = new CompoundTag();
                    mt.putString("id", m.id);
                    mt.putBoolean("waiting", m.waiting);
                    mt.putLong("home", m.home);
                    if (m.stowed != null) mt.put("stowed", m.stowed);
                    if (m.snapshot != null) mt.put("snapshot", m.snapshot);
                    ms.add(mt);
                }
                ct.put("members", ms);
                list.add(ct);
            });
            tag.put("crews", list);
            return tag;
        }

        static CrewData load(CompoundTag tag, HolderLookup.Provider provider) {
            CrewData d = new CrewData();
            for (Tag t : tag.getList("crews", Tag.TAG_COMPOUND)) {
                CompoundTag ct = (CompoundTag) t;
                Crew c = new Crew();
                c.player = ct.getUUID("player");
                c.captainName = ct.getString("captain");
                UUID charId = ct.getUUID("character");
                for (Tag mt0 : ct.getList("members", Tag.TAG_COMPOUND)) {
                    CompoundTag mt = (CompoundTag) mt0;
                    Member m = new Member(mt.getString("id"));
                    m.waiting = mt.getBoolean("waiting");
                    m.home = mt.getLong("home");
                    if (mt.contains("stowed")) m.stowed = mt.getCompound("stowed");
                    if (mt.contains("snapshot")) m.snapshot = mt.getCompound("snapshot");
                    c.members.put(m.id, m);
                    d.captainOf.put(m.id, charId);
                }
                d.crews.put(charId, c);
            }
            return d;
        }
    }

    private static CrewData cached;
    private static MinecraftServer cachedFor;

    public static CrewData data(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CrewData::new, CrewData::load, null), GrandLine.MODID + "_crews");
        cachedFor = server;
        return cached;
    }

    // ================================================================== queries

    /** Character id of the captain this notable sails with, or null. */
    @Nullable
    public static UUID captainCharacter(Entity e) {
        if (!(e instanceof GLNpc npc) || npc.notableId().isEmpty() || npc.level().isClientSide) return null;
        MinecraftServer server = npc.getServer();
        return server == null ? null : data(server).captainOf.get(npc.notableId());
    }

    public static boolean isCrew(GLNpc npc) {
        return captainCharacter(npc) != null;
    }

    /** The online player whose current character captains this crew member. */
    @Nullable
    public static ServerPlayer captainOf(GLNpc npc) {
        UUID ch = captainCharacter(npc);
        if (ch == null) return null;
        Crew c = data(npc.getServer()).crews.get(ch);
        return c == null ? null : onlineCaptain(npc.getServer(), ch, c);
    }

    @Nullable
    private static ServerPlayer onlineCaptain(MinecraftServer server, UUID charId, Crew c) {
        ServerPlayer p = server.getPlayerList().getPlayer(c.player);
        if (p == null) return null;
        CharacterData d = Characters.get(p);
        return d.created && charId.equals(d.characterId) ? p : null;
    }

    public static boolean isCrewOf(GLNpc npc, Player p) {
        UUID ch = captainCharacter(npc);
        return ch != null && ch.equals(Characters.get(p).characterId);
    }

    public static boolean isWaiting(GLNpc npc) {
        Member m = member(npc);
        return m != null && m.waiting;
    }

    @Nullable
    private static Member member(GLNpc npc) {
        UUID ch = captainCharacter(npc);
        if (ch == null) return null;
        Crew c = data(npc.getServer()).crews.get(ch);
        return c == null ? null : c.members.get(npc.notableId());
    }

    /** Notable ids in the crew of a character (in recruitment order). */
    public static List<String> members(MinecraftServer server, UUID characterId) {
        Crew c = data(server).crews.get(characterId);
        return c == null ? List.of() : List.copyOf(c.members.keySet());
    }

    /** Crew members are allied to their captain and to each other. */
    public static boolean allied(GLNpc npc, Entity other) {
        UUID ch = captainCharacter(npc);
        if (ch == null) return false;
        if (other instanceof Player p) return ch.equals(Characters.get(p).characterId);
        return ch.equals(captainCharacter(other));
    }

    /** Damage between a captain and their crew, or between crewmates, never lands. */
    public static boolean friendlyFire(LivingEntity victim, @Nullable Entity attacker) {
        if (attacker == null || attacker == victim || victim.level().isClientSide) return false;
        UUID a = attacker instanceof Player p ? Characters.get(p).characterId : captainCharacter(attacker);
        UUID v = victim instanceof Player p ? Characters.get(p).characterId : captainCharacter(victim);
        if (a == null || v == null || !a.equals(v)) return false;
        // same character id on both sides: captain↔member or member↔member (a player never "befriends" itself)
        return !(attacker instanceof Player && victim instanceof Player);
    }

    /**
     * Hostility for crews: members fight whatever threatens or is fought by their captain and nothing else;
     * other NPCs treat a crew member the way they would treat its captain. {@code null} = not a crew matter.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        UUID ch = captainCharacter(npc);
        if (ch != null) {
            if (allied(npc, e)) return false;
            ServerPlayer cap = captainOf(npc);
            if (cap == null) return false;
            if (e == cap.getLastHurtByMob() || e == cap.getLastHurtMob()) return true;
            if (e instanceof Mob m && m.getTarget() == cap) return true;
            return e instanceof GLNpc o && o.distanceToSqr(cap) < LEASH * LEASH && o.isHostileTo(cap);
        }
        UUID other = captainCharacter(e);
        if (other != null && e instanceof GLNpc member) {
            ServerPlayer cap = captainOf(member);
            return cap != null && npc.distanceToSqr(cap) < LEASH * LEASH && npc.isHostileTo(cap);
        }
        return null;
    }

    // ================================================================== actions

    public static boolean recruit(ServerPlayer sp, GLNpc npc) {
        Notable n = NotableCharacters.get(npc.notableId());
        CharacterData d = Characters.get(sp);
        if (n == null || !n.recruitable() || !d.created) return false;
        MinecraftServer server = sp.server;
        CrewData cd = data(server);
        UUID existing = cd.captainOf.get(n.id());
        if (existing != null) {
            Crew c = cd.crews.get(existing);
            sp.sendSystemMessage(Component.translatable("grandline.crew.taken", npc.getDisplayName(), c == null ? "?" : c.captainName).withStyle(ChatFormatting.GRAY));
            return false;
        }
        Crew crew = cd.crews.computeIfAbsent(d.characterId, k -> new Crew());
        crew.player = sp.getUUID();
        crew.captainName = d.name;
        if (crew.members.size() >= MAX_CREW) {
            sp.sendSystemMessage(Component.translatable("grandline.crew.full", MAX_CREW).withStyle(ChatFormatting.RED));
            return false;
        }
        Member m = new Member(n.id());
        m.home = npc.home().asLong();
        crew.members.put(n.id(), m);
        cd.captainOf.put(n.id(), d.characterId);
        cd.setDirty();
        NotableState.get(server).set(n.id(), Status.RECRUITED, npc.getUUID(), d.name, npc.level().getGameTime());
        npc.setAllegiance(d.faction, BlockPos.ZERO);
        npc.setTarget(null);
        npc.setFlag(GLNpc.FLAG_SURRENDER, false);
        sp.sendSystemMessage(Component.translatable("grandline.crew.joined", npc.getDisplayName(), Component.translatable(n.crewRoleKey())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sp.sendSystemMessage(Component.translatable(n.joinedKey()).withStyle(ChatFormatting.ITALIC));
        d.flags.add("crew_" + n.id());
        CharacterManager.markDirty(sp);
        com.grandline.journey.Journey.onRecruit(sp);
        return true;
    }

    public static void setWaiting(ServerPlayer sp, GLNpc npc, boolean waiting) {
        if (!isCrewOf(npc, sp)) return;
        Member m = member(npc);
        if (m == null) return;
        m.waiting = waiting;
        npc.setAllegiance(Characters.get(sp).faction, waiting ? npc.blockPosition() : BlockPos.ZERO);
        data(sp.server).setDirty();
        sp.sendSystemMessage(Component.translatable(waiting ? "grandline.crew.waiting" : "grandline.crew.following", npc.getDisplayName()).withStyle(ChatFormatting.AQUA));
    }

    public static void dismiss(ServerPlayer sp, GLNpc npc) {
        if (!isCrewOf(npc, sp)) return;
        CrewData cd = data(sp.server);
        Crew c = cd.crews.get(Characters.get(sp).characterId);
        Member m = c == null ? null : c.members.remove(npc.notableId());
        cd.captainOf.remove(npc.notableId());
        cd.setDirty();
        if (m != null) sendHome(sp.server, m);
        Characters.get(sp).flags.remove("crew_" + npc.notableId());
        CharacterManager.markDirty(sp);
        sp.sendSystemMessage(Component.translatable("grandline.crew.dismissed", npc.getDisplayName()).withStyle(ChatFormatting.GRAY));
    }

    /** A member died or was arrested. */
    public static void onMemberGone(MinecraftServer server, String notableId, boolean arrested) {
        CrewData cd = data(server);
        UUID ch = cd.captainOf.remove(notableId);
        if (ch == null) return;
        Crew c = cd.crews.get(ch);
        if (c != null) {
            c.members.remove(notableId);
            ServerPlayer cap = onlineCaptain(server, ch, c);
            Notable n = NotableCharacters.get(notableId);
            if (cap != null && n != null)
                cap.sendSystemMessage(Component.translatable(arrested ? "grandline.crew.lost_arrested" : "grandline.crew.lost", Component.translatable(n.nameKey())).withStyle(ChatFormatting.DARK_RED));
        }
        cd.setDirty();
    }

    /** Captain's character died for good: the crew disbands and everyone goes home. */
    public static void onPlayerDeath(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (!d.created || !GLConfig.PERMADEATH.get()) return;
        disband(sp.server, d.characterId);
    }

    public static void disband(MinecraftServer server, UUID characterId) {
        CrewData cd = data(server);
        Crew c = cd.crews.remove(characterId);
        if (c == null) return;
        for (Member m : c.members.values()) {
            cd.captainOf.remove(m.id);
            sendHome(server, m);
        }
        cd.setDirty();
        GrandLine.LOGGER.info("Crew of {} disbanded ({} members went home)", c.captainName, c.members.size());
    }

    /** Free a member: walk home if close, otherwise sail home (re-created there by the spawner). */
    private static void sendHome(MinecraftServer server, Member m) {
        NotableState st = NotableState.get(server);
        Notable n = NotableCharacters.get(m.id);
        if (n == null) return;
        HumanoidNpc e = find(server, st.entry(m.id).entity);
        BlockPos home = m.home != 0 ? BlockPos.of(m.home) : NotableSpawner.homeOf(server, n);
        long now = server.overworld().getGameTime();
        if (e != null && home != null && m.stowed == null && e.level() == server.overworld() && e.blockPosition().distSqr(home) < 128 * 128) {
            e.setAllegiance(n.faction(), home);
            e.setTarget(null);
            st.set(m.id, Status.ALIVE, e.getUUID(), "", now);
        } else {
            if (e != null) e.discard();
            st.set(m.id, Status.UNSPAWNED, null, "", now);
        }
    }

    // ================================================================== travelling with the captain

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CrewData cd = data(sp.server);
        Crew c = cd.crews.get(Characters.get(sp).characterId);
        if (c == null) return;
        NotableState st = NotableState.get(sp.server);
        for (Member m : c.members.values()) {
            if (m.waiting || m.stowed != null) continue;
            HumanoidNpc npc = find(sp.server, st.entry(m.id).entity);
            if (npc != null) stow(cd, m, npc);
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 10 != 5) return;
        CrewData cd = data(server);
        if (cd.crews.isEmpty()) return;
        NotableState st = NotableState.get(server);
        for (Map.Entry<UUID, Crew> en : cd.crews.entrySet()) {
            ServerPlayer cap = onlineCaptain(server, en.getKey(), en.getValue());
            if (cap == null) continue;
            for (Member m : en.getValue().members.values()) tickMember(server, cd, st, cap, m, now);
        }
    }

    private static void tickMember(MinecraftServer server, CrewData cd, NotableState st, ServerPlayer cap, Member m, long now) {
        NotableState.Entry ne = st.entry(m.id);
        if (m.stowed != null) {
            if (onSolidGround(cap)) disembark(cd, st, cap, m, m.stowed, false);
            return;
        }
        if (m.waiting) return;
        HumanoidNpc e = find(server, ne.entity);
        if (e == null) {
            // lost in an unloaded chunk: call them back from the last snapshot
            if (++m.missing >= 6 && m.snapshot != null && onSolidGround(cap)) disembark(cd, st, cap, m, m.snapshot, true);
            return;
        }
        m.missing = 0;
        if (e.level() != cap.level() || e.distanceToSqr(cap) > STOW_DIST * STOW_DIST) {
            stow(cd, m, e);
            return;
        }
        if (e.distanceToSqr(cap) > TELEPORT_DIST * TELEPORT_DIST && onSolidGround(cap)) {
            BlockPos spot = NotableSpawner.safeSpotNear(cap.serverLevel(), cap.blockPosition());
            if (spot != null) {
                e.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                e.getNavigation().stop();
            }
        }
        LivingEntity t = e.getTarget();
        if (t != null && (t.distanceToSqr(cap) > LEASH * LEASH || allied(e, t))) e.setTarget(null);
        if (now % 200 == 5) {
            m.snapshot = save(e);
            cd.setDirty();
        }
    }

    private static boolean onSolidGround(ServerPlayer p) {
        return p.onGround() && !p.isPassenger() && !p.isInWater() && !p.isSpectator();
    }

    private static CompoundTag save(HumanoidNpc e) {
        CompoundTag tag = new CompoundTag();
        e.saveWithoutId(tag);
        return tag;
    }

    private static void stow(CrewData cd, Member m, HumanoidNpc e) {
        m.stowed = save(e);
        m.snapshot = m.stowed;
        e.discard();
        cd.setDirty();
    }

    /** Step off next to the captain. {@code recall}: rebuilt from a snapshot with a fresh body (stale copy is refused later). */
    private static void disembark(CrewData cd, NotableState st, ServerPlayer cap, Member m, CompoundTag tag, boolean recall) {
        ServerLevel level = cap.serverLevel();
        BlockPos spot = NotableSpawner.safeSpotNear(level, cap.blockPosition());
        if (spot == null) return;
        HumanoidNpc e = ModEntities.HUMANOID_NPC.get().create(level);
        if (e == null) return;
        e.load(tag);
        if (recall || level.getEntity(e.getUUID()) != null) e.setUUID(UUID.randomUUID());
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, cap.getYRot(), 0);
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
        e.setTarget(null);
        st.set(m.id, Status.RECRUITED, e.getUUID(), cap.getName().getString(), level.getGameTime());
        NotableSpawner.rebindFruit(level.getServer(), m.id, e.getUUID());
        m.stowed = null;
        m.missing = 0;
        cd.setDirty();
        level.addFreshEntity(e);
    }

    @Nullable
    private static HumanoidNpc find(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        for (ServerLevel l : server.getAllLevels()) if (l.getEntity(id) instanceof HumanoidNpc h) return h;
        return null;
    }

    private CrewManager() {}
}
