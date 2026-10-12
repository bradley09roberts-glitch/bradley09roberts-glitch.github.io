package com.grandline.ship;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * What an NPC ship of the living sea is doing (see {@link LivingSea}): a merchant's route, a raid on a harbour, who
 * has wronged it and who fought it. The route and raid are saved with the ship; the rest is transient.
 */
public final class SeaLife {
    /** Merchant route: destination island id (empty when none) and where she came from (flavour). */
    public String route = "", origin = "";
    /** Harbour raid: island id, phase (see {@link HarbourRaids}) and when the raiders landed. */
    public String raidIsland = "";
    public int raidPhase;
    public long landedAt, phaseAt;
    /** Part of a Marine-escorted convoy (the merchant at its head). */
    public boolean convoy;

    // ------------------------------------------------------------------ transient
    /** Players who attacked this ship or its crew (crimes are charged once per player). */
    final Set<UUID> wronged = new HashSet<>();
    /** Players who fought this ship (or the raiders it landed): uuid -> game time of the last blow (and who they were). */
    final Map<UUID, Long> fought = new HashMap<>();
    final Map<UUID, net.minecraft.server.level.ServerPlayer> who = new HashMap<>();
    /** Harbour raid: Marines have been sent for; ticks the raid ship has lain stalled on her way in. */
    boolean responded;
    int stall;
    /** Players who joined this ship's raid. */
    final Set<UUID> raidAllies = new HashSet<>();
    /** Merchant: entity id of the ship or player she is running from (-1 none), and until when she lies hove-to. */
    int threatId = -1;
    long heaveTo, portUntil;
    /** Merchant: struck her colours (crew surrendered, cargo over the side). */
    boolean yielded;
    /** Merchant: players told she has hove to for them. */
    final Set<UUID> greeted = new HashSet<>();
    /** Pirates: running from a stronger ship (entity id, -1 none). */
    int fleeFrom = -1;
    /** Crew beaten at this game time (0 = not beaten); a beaten hulk founders after a while. */
    long beatenAt;
    /** Rewards and salvage are handed out once. */
    boolean defeated, salvaged;
    /** Chasing a target dead ahead: the next time she yaws to bring a broadside to bear. */
    long yawAt;
    /** Test hook: arrived at the destination (the ship may have been removed since). */
    public boolean arrived;

    /** Merchant: has struck her colours. */
    public boolean yielded() {
        return yielded;
    }

    /** Merchant: entity id of whoever she is running from or fighting (-1 none); pirates: of the ship they run from. */
    public int threat() {
        return threatId >= 0 ? threatId : fleeFrom;
    }

    public boolean wronged(Player p) {
        return wronged.contains(p.getUUID());
    }

    public boolean raidAlly(Player p) {
        return raidAllies.contains(p.getUUID());
    }

    public Set<UUID> fighters() {
        return Collections.unmodifiableSet(fought.keySet());
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Route", route);
        t.putString("Origin", origin);
        t.putString("Raid", raidIsland);
        t.putInt("RaidPhase", raidPhase);
        t.putLong("Landed", landedAt);
        t.putLong("PhaseAt", phaseAt);
        t.putBoolean("Convoy", convoy);
        ListTag w = new ListTag();
        for (UUID u : wronged) w.add(StringTag.valueOf(u.toString()));
        t.put("Wronged", w);
        return t;
    }

    void load(CompoundTag t) {
        route = t.getString("Route");
        origin = t.getString("Origin");
        raidIsland = t.getString("Raid");
        raidPhase = t.getInt("RaidPhase");
        landedAt = t.getLong("Landed");
        phaseAt = t.getLong("PhaseAt");
        convoy = t.getBoolean("Convoy");
        wronged.clear();
        for (Tag e : t.getList("Wronged", Tag.TAG_STRING)) {
            try {
                wronged.add(UUID.fromString(e.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
