package com.grandline.activity;

import com.grandline.npc.GLNpc;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extra shop offers of the sea activities (added to the role offers by {@code Shops}): shady dealers sell treasure
 * maps, cooks sell catches and dishes, and every general store stocks a fishing rod.
 */
public final class ActivityShops {
    public record Extra(String item, int count, long price) {}

    public static final long MAP_PRICE = 3500;

    /** Whether this trader deals under the counter: barkeeps anywhere, merchants on pirate or outlaw islands. */
    public static boolean shady(String role, @Nullable Island isl) {
        if (role.equals("bartender")) return true;
        if (!role.equals("merchant") && !role.equals("shopkeeper")) return false;
        if (isl == null) return false;
        return isl.faction.equals("pirate") || isl.faction.equals("revolutionary") || isl.settlementType == SettlementType.FORTRESS
                || isl.settlementType == SettlementType.RUINS;
    }

    public static List<Extra> extras(String role, @Nullable Island isl) {
        List<Extra> l = new ArrayList<>();
        if (shady(role, isl)) l.add(new Extra("grandline:treasure_map", 1, MAP_PRICE));
        switch (role) {
            case "cook", "head_chef", "waiter" -> {
                l.add(new Extra("grandline:grilled_fish", 4, 260));
                l.add(new Extra("grandline:fisherman_stew", 1, 220));
                l.add(new Extra("grandline:saltfire_curry", 1, 900));
                l.add(new Extra("grandline:harbor_bream", 4, 120));
                l.add(new Extra("minecraft:fishing_rod", 1, 250));
            }
            case "innkeeper", "bartender" -> l.add(new Extra("grandline:fisherman_stew", 1, 240));
            case "merchant", "shopkeeper" -> l.add(new Extra("minecraft:fishing_rod", 1, 250));
            default -> {}
        }
        return l;
    }

    public static List<Extra> extras(GLNpc npc) {
        MinecraftServer server = npc.getServer();
        WorldPlan plan = server == null ? null : WorldPlan.of(server);
        return extras(npc.role(), plan == null ? null : plan.byId(npc.islandId()));
    }

    private ActivityShops() {}
}
