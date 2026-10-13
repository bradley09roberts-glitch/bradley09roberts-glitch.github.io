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
 * member goes home.
 * <p>
 * The roster holds up to {@link #MAX_CREW} members; at most {@link #ACTIVE_LIMIT} of them are <b>active</b> (they
 * follow, fight and travel with the captain, see {@link CompanionGoal}); the rest wait in <b>reserve</b> where they
 * were left (always a safe place) or aboard. Who is active changes only at a safe place and out of combat
 * ({@link CompanionOrders}). Active members travel "aboard" (stowed as data) when the captain sails far away, changes
 * dimension or logs out, and step off again next to the captain once the captain is back on solid ground. A member is
 * never killed in normal play: at zero health it is downed ({@link CompanionCare}).
 * <p>
 * Save format 2 adds, per member, {@code active}, {@code order}, {@code hold}, {@code bond}, {@code capLevel} and
 * {@code downed}, and per crew {@code v}, {@code quests} and {@code former}. Format-1 saves load with defaults: the
 * members who were following become active (up to two), then those who were told to wait, and the rest go to reserve.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CrewManager {
    public static final int MAX_CREW = 18;
    /** Companions following and fighting at once; the rest of the roster is in reserve. */
    public static final int ACTIVE_LIMIT = 2;
    /** Save format of the crew data (1 = before companions had an active slot, orders and a bond). */
    static final int FORMAT = 2;
    private static final double STOW_DIST = 96, TELEPORT_DIST = 40, LEASH = 32;
    /** Bond points needed for ranks 1-5. */
    static final int[] BOND_RANKS = {0, 3, 8, 15, 25};

    /** A companion's standing order: follow the captain (default) or hold a position. */
    public enum Order {
        FOLLOW, HOLD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Order byId(String s) {
            return "hold".equals(s) ? HOLD : FOLLOW;
        }
    }

    // ================================================================== saved data

    public static final class Member {
        final String id;
        boolean active = true;
        Order order = Order.FOLLOW;
        long home, hold;
        @Nullable CompoundTag stowed, snapshot;
        int missing;
        /** Bond points (fights won and story quests finished together). */
        int bond;
        /** The captain's level when last seen (companion numbers follow it, see balance/CompanionTuning). */
        int capLevel;
        boolean downed;
        // ---- transient, never saved ----
        long downedSince, quietSince, healReadyAt, techniqueReadyAt, roleTechReadyAt, focusUntil, regroupUntil, lastRevivePoke;
        int fightHeals, focusId = -1, reviveProgress;
        boolean encoreUsed;

        Member(String id) {
            this.id = id;
        }

        public String id() { return id; }
        public boolean active() { return active; }
        public Order order() { return order; }
        public boolean holding() { return order == Order.HOLD; }
        public int bond() { return bond; }
        public int bondRank() { return rankOf(bond); }
        public boolean downed() { return downed; }
        public boolean stowed() { return stowed != null; }
        public int captainLevel() { return capLevel; }
        public int fightHeals() { return fightHeals; }
        public int missingChecks() { return missing; }
        @Nullable public BlockPos holdPos() { return hold == 0 ? null : BlockPos.of(hold); }

        /** Self-test hook: technique and song timers back to ready (so measured fights start alike). */
        public void resetTimersForTest() {
            techniqueReadyAt = roleTechReadyAt = healReadyAt = 0;
            fightHeals = 0;
            encoreUsed = false;
            focusId = -1;
            regroupUntil = 0;
        }
    }

    public static final class Crew {
        UUID player;
        String captainName = "";
        final LinkedHashMap<String, Member> members = new LinkedHashMap<>();
        /** Bond points of members who were dismissed: a friend who comes back remembers. */
        final Map<String, Integer> former = new HashMap<>();
        /** Completed quests counted for bond so far (-1: not looked yet). */
        int questsSeen = -1;

        public Collection<Member> roster() {
            return Collections.unmodifiableCollection(members.values());
        }

        @Nullable
        public Member get(String id) {
            return members.get(id);
        }

        public int questsSeen() {
            return questsSeen;
        }

        public int activeCount() {
            int n = 0;
            for (Member m : members.values()) if (m.active) n++;
            return n;
        }
    }

    public static final class CrewData extends SavedData {
        final Map<UUID, Crew> crews = new LinkedHashMap<>();
        final Map<String, UUID> captainOf = new HashMap<>();

        @Nullable
        public Crew crew(UUID characterId) {
            return crews.get(characterId);
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            crews.forEach((charId, c) -> {
                CompoundTag ct = new CompoundTag();
                ct.putInt("v", FORMAT);
                ct.putUUID("character", charId);
                ct.putUUID("player", c.player);
                ct.putString("captain", c.captainName);
                ct.putInt("quests", c.questsSeen);
                CompoundTag former = new CompoundTag();
                c.former.forEach(former::putInt);
                ct.put("former", former);
                ListTag ms = new ListTag();
                for (Member m : c.members.values()) {
                    CompoundTag mt = new CompoundTag();
                    mt.putString("id", m.id);
                    mt.putBoolean("waiting", m.holding()); // format 1 readers
                    mt.putLong("home", m.home);
                    mt.putBoolean("active", m.active);
                    mt.putString("order", m.order.id());
                    mt.putLong("hold", m.hold);
                    mt.putInt("bond", m.bond);
                    mt.putInt("capLevel", m.capLevel);
                    mt.putBoolean("downed", m.downed);
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

        public static CrewData load(CompoundTag tag, HolderLookup.Provider provider) {
            CrewData d = new CrewData();
            for (Tag t : tag.getList("crews", Tag.TAG_COMPOUND)) {
                CompoundTag ct = (CompoundTag) t;
                Crew c = new Crew();
                c.player = ct.getUUID("player");
                c.captainName = ct.getString("captain");
                UUID charId = ct.getUUID("character");
                boolean old = ct.getInt("v") < 2;
                for (Tag mt0 : ct.getList("members", Tag.TAG_COMPOUND)) {
                    CompoundTag mt = (CompoundTag) mt0;
                    Member m = new Member(mt.getString("id"));
                    m.order = mt.contains("order") ? Order.byId(mt.getString("order")) : mt.getBoolean("waiting") ? Order.HOLD : Order.FOLLOW;
                    m.home = mt.getLong("home");
                    m.hold = mt.getLong("hold");
                    m.active = !mt.contains("active") || mt.getBoolean("active");
                    m.bond = Math.max(0, mt.getInt("bond"));
                    m.capLevel = mt.getInt("capLevel");
                    m.downed = mt.getBoolean("downed");
                    if (mt.contains("stowed")) m.stowed = mt.getCompound("stowed");
                    if (mt.contains("snapshot")) m.snapshot = mt.getCompound("snapshot");
                    if (m.id.isEmpty()) continue;
                    c.members.put(m.id, m);
                    d.captainOf.put(m.id, charId);
                }
                c.questsSeen = ct.contains("quests") ? ct.getInt("quests") : -1;
                CompoundTag former = ct.getCompound("former");
                for (String k : former.getAllKeys()) c.former.put(k, former.getInt(k));
                if (old) migrate(c);
                else enforceLimit(c);
                d.crews.put(charId, c);
            }
            return d;
        }
    }

    /** Format 1: whoever was following becomes active first (in recruitment order), then those who were waiting. */
    static void migrate(Crew c) {
        int n = 0;
        for (Member m : c.members.values()) m.active = false;
        for (Member m : c.members.values()) if (!m.holding() && n < ACTIVE_LIMIT) {
            m.active = true;
            n++;
        }
        for (Member m : c.members.values()) if (!m.active && n < ACTIVE_LIMIT) {
            m.active = true;
            n++;
        }
    }

    private static void enforceLimit(Crew c) {
        int n = 0;
        for (Member m : c.members.values()) if (m.active && ++n > ACTIVE_LIMIT) m.active = false;
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

    /** Self-test hook: fake players are not in the player list, so they are registered here to count as online. */
    private static final Map<UUID, ServerPlayer> TEST_CAPTAINS = new HashMap<>();

    public static void testCaptain(ServerPlayer p, boolean online) {
        if (online) TEST_CAPTAINS.put(p.getUUID(), p);
        else TEST_CAPTAINS.remove(p.getUUID());
    }

    @Nullable
    static ServerPlayer onlineCaptain(MinecraftServer server, UUID charId, Crew c) {
        ServerPlayer p = server.getPlayerList().getPlayer(c.player);
        if (p == null) p = TEST_CAPTAINS.get(c.player);
        if (p == null) return null;
        CharacterData d = Characters.get(p);
        return d.created && charId.equals(d.characterId) ? p : null;
    }

    public static boolean isCrewOf(GLNpc npc, Player p) {
        UUID ch = captainCharacter(npc);
        return ch != null && ch.equals(Characters.get(p).characterId);
    }

    /** Not following the captain right now: holding a position, or in reserve. */
    public static boolean isWaiting(GLNpc npc) {
        Member m = memberOf(npc);
        return m != null && (!m.active || m.holding());
    }

    @Nullable
    public static Member memberOf(GLNpc npc) {
        UUID ch = captainCharacter(npc);
        if (ch == null) return null;
        Crew c = data(npc.getServer()).crews.get(ch);
        return c == null ? null : c.members.get(npc.notableId());
    }

    @Nullable
    public static Crew crewOf(Player p) {
        if (!(p instanceof ServerPlayer sp)) return null;
        CharacterData d = Characters.get(sp);
        return d.created ? data(sp.server).crews.get(d.characterId) : null;
    }

    @Nullable
    public static Member member(Player p, String id) {
        Crew c = crewOf(p);
        return c == null ? null : c.members.get(id);
    }

    /** Notable ids in the crew of a character (in recruitment order). */
    public static List<String> members(MinecraftServer server, UUID characterId) {
        Crew c = data(server).crews.get(characterId);
        return c == null ? List.of() : List.copyOf(c.members.keySet());
    }

    /** Ids of the active companions (following or holding) of a character. */
    public static List<String> activeIds(MinecraftServer server, UUID characterId) {
        Crew c = data(server).crews.get(characterId);
        if (c == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Member m : c.members.values()) if (m.active) out.add(m.id);
        return out;
    }

    public static int rankOf(int bondPoints) {
        int r = 1;
        for (int i = 0; i < BOND_RANKS.length; i++) if (bondPoints >= BOND_RANKS[i]) r = i + 1;
        return r;
    }

    /** Bond points still needed for the next rank, or -1 at rank 5. */
    public static int toNextRank(int bondPoints) {
        int r = rankOf(bondPoints);
        return r >= BOND_RANKS.length ? -1 : BOND_RANKS[r] - bondPoints;
    }

    public static int bondRank(GLNpc npc) {
        Member m = memberOf(npc);
        return m == null ? 1 : m.bondRank();
    }

    /** The captain's level a companion's numbers come from (last seen; the notable's own sea if never seen). */
    public static int captainLevel(GLNpc npc) {
        Member m = memberOf(npc);
        if (m != null && m.capLevel > 0) return m.capLevel;
        ServerPlayer cap = captainOf(npc);
        if (cap != null) return Math.max(1, Characters.get(cap).level);
        return com.grandline.balance.Tuning.referenceLevel(npc.tier());
    }

    /** The loaded body of a crew member, if any. */
    @Nullable
    public static HumanoidNpc body(MinecraftServer server, String id) {
        return find(server, NotableState.get(server).entry(id).entity);
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
     * Hostility for crews: a companion fights only foes of its captain and party ({@link Companions#isFoe}: whoever
     * attacks them or is hostile to the captain), never neutral or friendly people. Other NPCs treat a crew member the
     * way they would treat its captain, and leave a downed one alone. {@code null} = not a crew matter.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        UUID ch = captainCharacter(npc);
        if (ch != null) return !allied(npc, e) && Companions.isFoe(npc, e);
        UUID other = captainCharacter(e);
        if (other != null && e instanceof GLNpc member) {
            if (CompanionCare.isDowned(member)) return false;
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
        m.active = crew.activeCount() < ACTIVE_LIMIT;
        m.bond = crew.former.getOrDefault(n.id(), 0);
        crew.former.remove(n.id());
        m.capLevel = d.level;
        crew.members.put(n.id(), m);
        cd.captainOf.put(n.id(), d.characterId);
        cd.setDirty();
        NotableState.get(server).set(n.id(), Status.RECRUITED, npc.getUUID(), d.name, npc.level().getGameTime());
        // active: follow (home ZERO = no leash); reserve: wait right here, where the captain found them
        npc.setAllegiance(d.faction, m.active ? BlockPos.ZERO : npc.blockPosition());
        npc.setTarget(null);
        npc.setFlag(GLNpc.FLAG_SURRENDER, false);
        com.grandline.balance.CompanionTuning.apply(npc);
        npc.setHealth(npc.getMaxHealth());
        if (npc instanceof HumanoidNpc h) m.snapshot = save(h);
        sp.sendSystemMessage(Component.translatable("grandline.crew.joined", npc.getDisplayName(), Component.translatable(n.crewRoleKey())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sp.sendSystemMessage(Component.translatable(n.joinedKey()).withStyle(ChatFormatting.ITALIC));
        CompanionRole role = CompanionRole.of(n);
        sp.sendSystemMessage(Component.translatable(m.active ? "grandline.crew.joined_active" : "grandline.crew.joined_reserve", npc.getDisplayName(),
                Component.translatable(role.key()), ACTIVE_LIMIT).withStyle(ChatFormatting.AQUA));
        d.flags.add("crew_" + n.id());
        CharacterManager.markDirty(sp);
        com.grandline.journey.Journey.onRecruit(sp);
        return true;
    }

    /** Kept for older callers: wait here = hold, otherwise follow. */
    public static void setWaiting(ServerPlayer sp, GLNpc npc, boolean waiting) {
        CompanionOrders.setOrder(sp, npc, waiting ? Order.HOLD : Order.FOLLOW, true);
    }

    public static void dismiss(ServerPlayer sp, GLNpc npc) {
        if (!isCrewOf(npc, sp)) return;
        CrewData cd = data(sp.server);
        Crew c = cd.crews.get(Characters.get(sp).characterId);
        Member m = c == null ? null : c.members.remove(npc.notableId());
        cd.captainOf.remove(npc.notableId());
        if (m != null && m.bond > 0) c.former.put(m.id, m.bond);
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
        if (e != null && m.downed) CompanionCare.standUp(e, m, 0.5f, false);
        if (e != null && home != null && m.stowed == null && e.level() == server.overworld() && e.blockPosition().distSqr(home) < 128 * 128) {
            e.setAllegiance(n.faction(), home);
            e.setTarget(null);
            e.retune(); // back to the numbers of their own sea
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
            if (!m.active || m.holding() || m.stowed != null) continue;
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
        for (Map.Entry<UUID, Crew> en : List.copyOf(cd.crews.entrySet())) {
            Crew c = en.getValue();
            ServerPlayer cap = onlineCaptain(server, en.getKey(), c);
            if (cap == null) {
                // captain away: members left standing somewhere (holding, in reserve) still recover
                for (Member m : List.copyOf(c.members.values())) {
                    HumanoidNpc e = m.stowed == null ? find(server, st.entry(m.id).entity) : null;
                    if (e != null) upkeep(cd, null, m, e, now);
                }
                continue;
            }
            Companions.updateFight(cap, en.getKey(), c, now);
            if (now % 100 == 5) CompanionCredit.questBond(cap, c);
            for (Member m : List.copyOf(c.members.values())) tickMember(server, cd, st, cap, m, now);
        }
    }

    private static void tickMember(MinecraftServer server, CrewData cd, NotableState st, ServerPlayer cap, Member m, long now) {
        int lvl = Math.max(1, Characters.get(cap).level);
        if (m.capLevel != lvl) {
            m.capLevel = lvl;
            cd.setDirty();
        }
        NotableState.Entry ne = st.entry(m.id);
        if (!m.active || m.holding()) {
            // reserve and holding members stay where they are (in a safe place, or where the captain posted them)
            HumanoidNpc e = m.stowed == null ? find(server, ne.entity) : null;
            if (e != null) {
                if (m.holding() && m.hold == 0) {
                    m.hold = e.blockPosition().asLong();
                    cd.setDirty();
                }
                // a member put in reserve by an old save while following has no anchor: it waits right where it stands
                if (e.home().equals(BlockPos.ZERO)) e.setAllegiance(e.faction(), m.holding() ? BlockPos.of(m.hold) : e.blockPosition());
                upkeep(cd, cap, m, e, now);
            }
            return;
        }
        if (m.stowed != null) {
            if (onSolidGround(cap)) disembark(cd, st, cap, m, m.stowed, false);
            return;
        }
        HumanoidNpc e = find(server, ne.entity);
        if (e == null) {
            // lost in an unloaded chunk (or left far behind in reserve): call them back from the last snapshot
            if (++m.missing >= 6 && m.snapshot != null && onSolidGround(cap)) disembark(cd, st, cap, m, m.snapshot, true);
            return;
        }
        m.missing = 0;
        if (e.level() != cap.level() || e.distanceToSqr(cap) > STOW_DIST * STOW_DIST) {
            stow(cd, m, e);
            return;
        }
        if (e.distanceToSqr(cap) > TELEPORT_DIST * TELEPORT_DIST && onSolidGround(cap) && e.getTarget() == null) Companions.teleportNear(e, cap);
        upkeep(cd, cap, m, e, now);
    }

    /** Numbers, recovery and housekeeping for a member with a body in the world. */
    private static void upkeep(CrewData cd, @Nullable ServerPlayer cap, Member m, HumanoidNpc e, long now) {
        if (com.grandline.balance.CompanionTuning.stale(e)) com.grandline.balance.CompanionTuning.apply(e);
        if (e.flag(GLNpc.FLAG_SURRENDER)) e.setFlag(GLNpc.FLAG_SURRENDER, false); // companions are downed, they never yield
        CompanionCare.upkeep(e, m, cap, now);
        LivingEntity t = e.getTarget();
        if (t != null && !Companions.isFoe(e, t)) e.setTarget(null);
        if (now % 200 == 5) {
            m.snapshot = save(e);
            cd.setDirty();
        }
    }

    static boolean onSolidGround(ServerPlayer p) {
        return p.onGround() && !p.isPassenger() && !p.isInWater() && !p.isSpectator();
    }

    static CompoundTag save(HumanoidNpc e) {
        CompoundTag tag = new CompoundTag();
        e.saveWithoutId(tag);
        return tag;
    }

    /** Take a member aboard: the body leaves the world and is kept as data. */
    static void stow(CrewData cd, Member m, HumanoidNpc e) {
        m.stowed = save(e);
        m.snapshot = m.stowed;
        e.discard();
        cd.setDirty();
    }

    /** Step off next to the captain. {@code recall}: rebuilt from a snapshot with a fresh body (stale copy is refused later). */
    static boolean disembark(CrewData cd, NotableState st, ServerPlayer cap, Member m, CompoundTag tag, boolean recall) {
        ServerLevel level = cap.serverLevel();
        BlockPos spot = NotableSpawner.safeSpotNear(level, cap.blockPosition());
        // only where entities are live: a body added to a chunk still loading stays invisible for a while, would be
        // counted missing and recalled a second time (a duplicate); the next crew tick tries again
        if (spot == null || !level.isPositionEntityTicking(spot)) return false;
        HumanoidNpc e = ModEntities.HUMANOID_NPC.get().create(level);
        if (e == null) return false;
        e.load(tag);
        if (recall || level.getEntity(e.getUUID()) != null) e.setUUID(UUID.randomUUID());
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, cap.getYRot(), 0);
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
        e.setTarget(null);
        e.setAllegiance(Characters.get(cap).faction, BlockPos.ZERO);
        st.set(m.id, Status.RECRUITED, e.getUUID(), cap.getName().getString(), level.getGameTime());
        NotableSpawner.rebindFruit(level.getServer(), m.id, e.getUUID());
        m.stowed = null;
        m.missing = 0;
        if (m.downed) { // the fight is long over: back on their feet as they step off
            m.downed = false;
            e.removeEffect(com.grandline.registry.ModEffects.KNOCKED_OUT);
            e.setHealth(Math.max(e.getHealth(), e.getMaxHealth() * CompanionCare.GET_UP_HEALTH));
        }
        cd.setDirty();
        level.addFreshEntity(e);
        com.grandline.balance.CompanionTuning.apply(e);
        return true;
    }

    @Nullable
    static HumanoidNpc find(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        for (ServerLevel l : server.getAllLevels()) if (l.getEntity(id) instanceof HumanoidNpc h) return h;
        return null;
    }

    private CrewManager() {}
}
