package com.grandline.ship;

import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Race;
import com.grandline.quest.Quests;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shipwright services: buy ships, buy upgrades, repair, recover sunk ships, rename/recolour. */
public final class Shipyard {
    public static final Map<String, Long> UPGRADES = new LinkedHashMap<>();

    static {
        UPGRADES.put("reinforced_hull", 15000L);
        UPGRADES.put("fine_sails", 12000L);
        UPGRADES.put("paddle_wheel", 30000L);
        UPGRADES.put("seastone_keel", 80000L);
        UPGRADES.put("coup_de_burst", 60000L);
        UPGRADES.put("ram_bow", 40000L);
        UPGRADES.put(SpecialRoutes.COATING, 25000L);
    }

    /** Ship coating (for the dive to the deep sea) is only done by the coaters of Sabaody and Fish-Man Island. */
    static boolean coatingYard(ServerPlayer sp) {
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        Island isl = plan == null ? null : plan.nearest(sp.getBlockX(), sp.getBlockZ(), 400);
        return isl != null && (isl.id.equals("sabaody_archipelago") || isl.id.equals("fishman_island"));
    }

    public static void open(ServerPlayer sp, GLNpc npc) {
        open(sp, npc, false);
    }

    static void open(ServerPlayer sp, GLNpc npc, boolean customisePage) {
        CharacterData d = Characters.get(sp);
        CompoundTag t = new CompoundTag();
        t.putInt("npc", npc.getId());
        t.putBoolean("customisePage", customisePage);
        t.putLong("beli", d.beli);
        ListTag classes = new ListTag();
        for (ShipClass c : ShipClass.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", c.id());
            e.putLong("price", c.price);
            e.putInt("cannons", c.cannons);
            e.putInt("hull", c.hull);
            classes.add(e);
        }
        t.put("classes", classes);
        ListTag ups = new ListTag();
        boolean coaters = coatingYard(sp);
        UPGRADES.forEach((k, v) -> {
            if (k.equals(SpecialRoutes.COATING) && !coaters) return;
            CompoundTag e = new CompoundTag();
            e.putString("id", k);
            e.putLong("price", v);
            ups.add(e);
        });
        t.put("upgrades", ups);
        ListTag owned = new ListTag();
        for (ShipRegistry.Record r : ShipRegistry.get(sp.server).ownedBy(sp.getUUID())) {
            CompoundTag e = new CompoundTag();
            e.putString("id", r.shipId.toString());
            e.putString("name", r.name);
            e.putString("cls", r.cls);
            e.putBoolean("sunk", r.sunk);
            e.putInt("dist", (int) Math.sqrt(sp.distanceToSqr(r.x, r.y, r.z)));
            owned.add(e);
        }
        t.put("owned", owned);
        // the ship moored here: its current look for the customisation page, and any prizes to sell or refit
        ShipEntity here = nearestOwned(sp);
        if (here != null && !here.flag(ShipEntity.F_PRIZE)) {
            CompoundTag cur = new CompoundTag();
            cur.putString("name", here.shipName());
            cur.putString("cls", here.cls.id());
            cur.putString("look", here.look().encode());
            cur.putInt("sail", here.sailColor());
            cur.putInt("hullc", here.hullColor());
            t.put("current", cur);
        }
        ListTag prizes = new ListTag();
        for (ShipEntity s : sp.serverLevel().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(80), s -> s.flag(ShipEntity.F_PRIZE) && sp.getUUID().equals(s.owner))) {
            CompoundTag e = new CompoundTag();
            e.putString("id", s.shipId.toString());
            e.putString("name", s.shipName());
            e.putString("cls", s.cls.id());
            e.putLong("value", NavalCombat.prizeValue(s));
            e.putLong("refit", NavalCombat.refitFee(s));
            prizes.add(e);
        }
        t.put("prizes", prizes);
        t.putBoolean("marine", d.faction == com.grandline.player.Faction.MARINE);
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("shipyard", t));
    }

    public static void action(ServerPlayer sp, CompoundTag t) {
        Entity e = sp.level().getEntity(t.getInt("npc"));
        if (!(e instanceof GLNpc npc) || npc.distanceTo(sp) > 10 || !npc.role().equals("shipwright")) return;
        CharacterData d = Characters.get(sp);
        switch (t.getString("op")) {
            case "buy" -> {
                ShipClass c = ShipClass.byId(t.getString("cls"));
                if (c == ShipClass.ROWBOAT && d.race.isLarge()) {
                    sp.sendSystemMessage(Component.translatable("grandline.ship.too_big").withStyle(ChatFormatting.RED));
                    return;
                }
                if (d.beli < c.price && !sp.isCreative()) {
                    sp.sendSystemMessage(Component.translatable("grandline.shop.poor").withStyle(ChatFormatting.RED));
                    return;
                }
                ShipEntity ship = spawnAtDock(sp, npc, c);
                if (ship == null) {
                    sp.sendSystemMessage(Component.translatable("grandline.ship.no_berth"));
                    return;
                }
                if (!sp.isCreative()) d.beli -= c.price;
                Quests.progress(sp, "ship", "any", 1);
                sp.sendSystemMessage(Component.translatable("grandline.ship.bought", Component.translatable("ship_class.grandline." + c.id())).withStyle(ChatFormatting.GOLD));
            }
            case "upgrade" -> {
                String up = t.getString("upgrade");
                Long price = UPGRADES.get(up);
                ShipEntity ship = nearestOwned(sp);
                if (price == null || ship == null) {
                    sp.sendSystemMessage(Component.translatable("grandline.ship.bring_ship"));
                    return;
                }
                if (up.equals(SpecialRoutes.COATING) && !coatingYard(sp)) return;
                if (ship.hasUpgrade(up) || (d.beli < price && !sp.isCreative())) return;
                if (!sp.isCreative()) d.beli -= price;
                ship.addUpgrade(up);
                if (up.equals("reinforced_hull")) ship.repair(ship.maxHull());
                ShipRegistry.get(sp.server).update(ship);
                sp.sendSystemMessage(Component.translatable("grandline.ship.upgraded", Component.translatable("ship_upgrade.grandline." + up)).withStyle(ChatFormatting.GOLD));
            }
            case "repair" -> {
                ShipEntity ship = nearestOwned(sp);
                if (ship == null) return;
                long cost = (long) ((ship.maxHull() - ship.hull()) * 20);
                if (d.beli < cost && !sp.isCreative()) return;
                if (!sp.isCreative()) d.beli -= cost;
                ship.repair(ship.maxHull());
                ship.setRigging(1f);
            }
            case "customise" -> {
                ShipEntity ship = nearestOwned(sp);
                if (ship == null || ship.flag(ShipEntity.F_PRIZE)) {
                    sp.sendSystemMessage(Component.translatable("grandline.ship.bring_ship"));
                    return;
                }
                ShipLook look = new ShipLook(t.getString("figurehead"), t.getString("emblem"), t.getInt("emblemc"), t.getInt("flagc")).valid();
                if (look.emblem().equals("marine") && d.faction != com.grandline.player.Faction.MARINE) look = new ShipLook(look.figurehead(), "skull", look.emblemColor(), look.flagColor());
                long fee = customiseFee(ship, look);
                if (d.beli < fee && !sp.isCreative()) {
                    sp.sendSystemMessage(Component.translatable("grandline.shop.poor").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!sp.isCreative()) d.beli -= fee;
                customise(ship, look, t.getInt("sail") & 0xFFFFFF, t.getInt("hullc") & 0xFFFFFF);
                sp.sendSystemMessage(Component.translatable("grandline.naval.customised", ship.shipName()).withStyle(ChatFormatting.GOLD));
            }
            case "sell_prize", "claim_prize" -> {
                ShipEntity prize = prizeById(sp, t.getString("ship"));
                if (prize == null) return;
                if (t.getString("op").equals("sell_prize")) sellPrize(sp, prize);
                else claimPrize(sp, prize);
            }
            case "recover" -> {
                ShipRegistry reg = ShipRegistry.get(sp.server);
                ShipRegistry.Record r = reg.get(java.util.UUID.fromString(t.getString("ship")));
                if (r == null || !sp.getUUID().equals(r.owner)) return;
                if (!r.sunk && sp.serverLevel().getEntity(r.shipId) != null) return;
                ShipClass c = ShipClass.byId(r.cls);
                long fee = c.price / 5;
                if (d.beli < fee && !sp.isCreative()) return;
                ShipEntity ship = spawnAtDock(sp, npc, c, r);
                if (ship == null) return;
                if (!sp.isCreative()) d.beli -= fee;
                sp.sendSystemMessage(Component.translatable("grandline.ship.recovered").withStyle(ChatFormatting.GOLD));
            }
            case "rename" -> {
                ShipEntity ship = nearestOwned(sp);
                if (ship == null) return;
                String n = t.getString("name").replaceAll("[^\\p{L}\\p{N} .'\\-]", "");
                ship.setShipName(n.length() > 24 ? n.substring(0, 24) : n);
                if (t.contains("sail")) ship.setColors(t.getInt("sail") & 0xFFFFFF, t.getInt("hullc") & 0xFFFFFF);
                ShipRegistry.get(sp.server).update(ship);
            }
            default -> {}
        }
        CharacterManager.markDirty(sp);
        open(sp, npc, t.getString("op").equals("customise"));
    }

    /** 2,000 Beli for paint and flags, plus 8,000 to carve a new figurehead. */
    public static long customiseFee(ShipEntity ship, ShipLook look) {
        return 2000L + (look.figurehead().equals(ship.look().figurehead()) ? 0 : 8000L);
    }

    /** Apply a look and paint to a ship and record it (synced to clients by the entity, saved in the registry). */
    public static void customise(ShipEntity ship, ShipLook look, int sail, int hull) {
        ship.setLook(look);
        ship.setColors(sail, hull);
        if (ship.getServer() != null) ShipRegistry.get(ship.getServer()).update(ship);
    }

    @Nullable
    private static ShipEntity prizeById(ServerPlayer sp, String id) {
        for (ShipEntity s : sp.serverLevel().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(80), s -> s.flag(ShipEntity.F_PRIZE) && sp.getUUID().equals(s.owner)))
            if (s.shipId.toString().equals(id)) return s;
        return null;
    }

    /** The shipwright buys a prize: Beli for the captain, the ship is broken up. */
    public static boolean sellPrize(ServerPlayer sp, ShipEntity prize) {
        if (!prize.flag(ShipEntity.F_PRIZE) || !sp.getUUID().equals(prize.owner)) return false;
        long value = NavalCombat.prizeValue(prize);
        CharacterData d = Characters.get(sp);
        d.beli += value;
        CharacterManager.markDirty(sp);
        NavalCombat.unlink(prize);
        prize.ejectPassengers();
        ShipRegistry.get(sp.server).remove(prize.shipId);
        prize.discard();
        sp.sendSystemMessage(Component.translatable("grandline.naval.prize_sold", prize.shipName(), String.format("%,d", value)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Refit a prize into the captain's fleet (fee: a tenth of its class price). */
    public static boolean claimPrize(ServerPlayer sp, ShipEntity prize) {
        if (!prize.flag(ShipEntity.F_PRIZE) || !sp.getUUID().equals(prize.owner)) return false;
        long fee = NavalCombat.refitFee(prize);
        CharacterData d = Characters.get(sp);
        if (d.beli < fee && !sp.isCreative()) {
            sp.sendSystemMessage(Component.translatable("grandline.shop.poor").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!sp.isCreative()) d.beli -= fee;
        CharacterManager.markDirty(sp);
        prize.setFlag(ShipEntity.F_PRIZE, false);
        prize.setShipName(d.name + "'s " + Component.translatable("ship_class.grandline." + prize.cls.id()).getString());
        prize.setLook(ShipLook.DEFAULT);
        prize.setColors(0xF4EEDC, 0x8A5A32);
        prize.repair(prize.maxHull());
        prize.setRigging(1f);
        ShipRegistry reg = ShipRegistry.get(sp.server);
        if (reg.get(prize.shipId) == null) reg.register(prize);
        reg.update(prize);
        sp.sendSystemMessage(Component.translatable("grandline.naval.prize_claimed", prize.shipName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    @Nullable
    static ShipEntity nearestOwned(ServerPlayer sp) {
        ShipEntity best = null;
        double bd = 80 * 80;
        for (ShipEntity s : sp.serverLevel().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(80))) {
            if (!sp.getUUID().equals(s.owner) && !sp.isCreative()) continue;
            if (s.flag(ShipEntity.F_PRIZE) || s.flag(ShipEntity.F_NPC)) continue; // prizes are sold or refitted first
            double d = s.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    @Nullable
    public static ShipEntity spawnAtDock(ServerPlayer sp, Entity near, ShipClass c) {
        return spawnAtDock(sp, near, c, null);
    }

    @Nullable
    static ShipEntity spawnAtDock(ServerPlayer sp, Entity near, ShipClass c, @Nullable ShipRegistry.Record restore) {
        ServerLevel l = sp.serverLevel();
        Vec3 pos = null;
        float yaw = 0;
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        if (plan != null) {
            Island isl = plan.nearest(near.getBlockX(), near.getBlockZ(), 600);
            if (isl != null) {
                Settlement s = isl.settlement();
                if (s.hasDock) {
                    double ux = s.dockDirX / 1000.0, uz = s.dockDirZ / 1000.0;
                    // berth alongside the dock tip, bow pointing out to sea
                    pos = new Vec3(s.dockTipX() + ux * (c.length / 2.0 + 3) - uz * (c.beam / 2.0 + 3.5), WorldPlan.SEA_LEVEL + 0.9, s.dockTipZ() + uz * (c.length / 2.0 + 3) + ux * (c.beam / 2.0 + 3.5));
                    yaw = (float) Math.toDegrees(Math.atan2(-ux, uz));
                }
            }
        }
        if (pos == null) pos = findWater(l, near.blockPosition());
        if (pos == null) return null;
        ShipEntity ship = ModEntities.SHIPS.get(c).get().create(l);
        if (ship == null) return null;
        ship.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        ship.owner = sp.getUUID();
        ship.ownerName = Characters.get(sp).name;
        ship.setHull(c.hull);
        ShipRegistry reg = ShipRegistry.get(sp.server);
        if (restore != null) {
            ship.shipId = restore.shipId;
            ship.interiorIndex = restore.interior;
            ship.setShipName(restore.name);
            ship.setColors(restore.sailColor, restore.hullColor);
            ship.setLook(ShipLook.decode(restore.look));
            for (String u : restore.upgrades.split(",")) if (!u.isEmpty()) ship.addUpgrade(u);
            ship.setHull(ship.maxHull());
            l.addFreshEntity(ship);
            reg.update(ship);
        } else {
            ship.setShipName(Characters.get(sp).name + "'s " + Component.translatable("ship_class.grandline." + c.id()).getString());
            l.addFreshEntity(ship);
            reg.register(ship);
        }
        return ship;
    }

    @Nullable
    public static Vec3 findWaterPublic(ServerLevel l, BlockPos near) {
        return findWater(l, near);
    }

    @Nullable
    static Vec3 findWater(ServerLevel l, BlockPos near) {
        for (int r = 8; r < 96; r += 4)
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8;
                int x = near.getX() + (int) (Math.cos(ang) * r), z = near.getZ() + (int) (Math.sin(ang) * r);
                boolean ok = true;
                for (int dx = -6; dx <= 6 && ok; dx += 3)
                    for (int dz = -6; dz <= 6 && ok; dz += 3)
                        if (!com.grandline.core.Chunks.ready(l, x + dx, z + dz) // never wait for a chunk that is still generating
                                || !l.getBlockState(new BlockPos(x + dx, WorldPlan.SEA_LEVEL, z + dz)).is(net.minecraft.world.level.block.Blocks.WATER)
                                || !l.getBlockState(new BlockPos(x + dx, WorldPlan.SEA_LEVEL + 1, z + dz)).isAir()) ok = false;
                if (ok) return new Vec3(x + 0.5, WorldPlan.SEA_LEVEL + 0.9, z + 0.5);
            }
        return null;
    }

    private Shipyard() {}
}
