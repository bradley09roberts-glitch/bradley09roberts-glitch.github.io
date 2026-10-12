package com.grandline.base;

import com.grandline.base.CrewBases.Claim;
import com.grandline.core.Chunks;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEntities;
import com.grandline.ship.LivingSea;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * Hired hands who live at a base (the quarters upgrade has room for four): islanders of the existing humanoid roles
 * (guard, sailor, sword master) who keep to the flag, never turn on the crew and fight the base's raiders. The claim
 * keeps who was hired; the people themselves are put on the ground while crew are near and taken up again when
 * nobody is (tagged transient, so a copy saved with a chunk is never brought back). A hand who dies is gone for good.
 */
public final class BaseHands {
    /** Hireable roles and their wages (Beli, paid once). */
    public static final Map<String, Long> ROLES = new LinkedHashMap<>();

    static {
        ROLES.put("guard", 6_000L);
        ROLES.put("sailor", 3_000L);
        ROLES.put("master", 25_000L);
    }

    public static final int PER_QUARTERS = 4;

    public static int capacity(Claim c) {
        return c.has(BaseUpgrades.QUARTERS) ? PER_QUARTERS : 0;
    }

    public static boolean hire(ServerPlayer sp, Claim c, String role) {
        Long wage = ROLES.get(role);
        if (wage == null || !CrewBases.isCrew(sp, c)) return false;
        if (c.hands.size() >= capacity(c)) {
            CrewBases.tell(sp, Component.translatable(capacity(c) == 0 ? "grandline.base.hire_no_quarters" : "grandline.base.hire_full", capacity(c)).withStyle(ChatFormatting.RED));
            return false;
        }
        CharacterData d = Characters.get(sp);
        if (d.beli < wage && !sp.isCreative()) {
            CrewBases.tell(sp, Component.translatable("grandline.base.upgrade_poor", String.format("%,d", wage)).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!sp.isCreative()) d.beli -= wage;
        CharacterManager.markDirty(sp);
        c.hands.add(role);
        c.dirty();
        CrewBases.tell(sp, Component.translatable("grandline.base.hired", Component.translatable("role.grandline." + role), c.hands.size(), capacity(c)).withStyle(ChatFormatting.GOLD));
        sync(sp.serverLevel(), c, sp.level().getGameTime());
        return true;
    }

    /** Hands of this base on the ground now. */
    public static List<HumanoidNpc> present(ServerLevel l, Claim c) {
        List<HumanoidNpc> out = new ArrayList<>();
        for (UUID u : c.spawnedHands.keySet()) if (l.getEntity(u) instanceof HumanoidNpc h && h.isAlive()) out.add(h);
        return out;
    }

    /** Put the hired hands on the ground at the flag (a few per round, loaded chunks only). */
    public static int sync(ServerLevel l, Claim c, long now) {
        c.spawnedHands.keySet().removeIf(u -> !(l.getEntity(u) instanceof HumanoidNpc h) || !h.isAlive());
        if (c.hands.isEmpty() || !Chunks.ready(l, c.flag.getX(), c.flag.getZ())) return 0;
        List<String> missing = new ArrayList<>(c.hands);
        for (String role : c.spawnedHands.values()) missing.remove(role);
        WorldPlan plan = WorldPlan.forLevel(l);
        Island isl = plan == null ? null : plan.byId(c.island);
        int tier = isl == null ? 2 : Math.max(2, isl.tier);
        int made = 0;
        for (String role : missing) {
            if (made >= 2) break;
            double ang = (c.spawnedHands.size() + made) * 2.1 + l.random.nextDouble() * 0.4;
            BlockPos near = c.flag.offset((int) Math.round(Math.cos(ang) * 4), 0, (int) Math.round(Math.sin(ang) * 4));
            BlockPos spot = NotableSpawner.safeSpotNear(l, near);
            if (spot == null) spot = NotableSpawner.safeSpotNear(l, c.flag);
            if (spot == null) continue;
            HumanoidNpc h = ModEntities.HUMANOID_NPC.get().create(l);
            if (h == null) continue;
            h.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, l.random.nextFloat() * 360, 0);
            h.configure(role, tier, c.island, c.flag, "", l.random);
            h.setAllegiance(Faction.CIVILIAN, c.flag); // the base's people: peaceful to visitors, never to raiders
            h.setPersistenceRequired(); // taken up by the base, not by the vanilla despawn rules
            h.addTag(LivingSea.TRANSIENT);
            h.addTag(CrewBases.HAND);
            h.addTag(CrewBases.OF + c.island);
            l.addFreshEntity(h);
            c.spawnedHands.put(h.getUUID(), role);
            made++;
        }
        return made;
    }

    /** Take the hands up again (nobody near, the claim changed hands or ended). */
    public static void despawn(ServerLevel l, Claim c) {
        for (UUID u : c.spawnedHands.keySet()) {
            Entity e = l.getEntity(u);
            if (e != null) e.discard();
        }
        c.spawnedHands.clear();
    }

    /** A hand died: the base has one hand fewer. */
    static void died(MinecraftServer server, Claim c, GLNpc npc) {
        String role = c.spawnedHands.remove(npc.getUUID());
        if (role == null) role = npc.role();
        if (c.hands.remove(role)) {
            c.dirty();
            CrewBases.tellCrew(server, c, Component.translatable("grandline.base.hand_fell", npc.getDisplayName(), CrewBases.islandName(server, c)).withStyle(ChatFormatting.DARK_RED));
        }
    }

    private BaseHands() {}
}
