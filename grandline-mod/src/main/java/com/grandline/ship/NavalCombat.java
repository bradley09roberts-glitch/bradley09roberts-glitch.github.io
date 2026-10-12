package com.grandline.ship;

import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Ship-to-ship combat: grapple and tow lines, ramming, boarding parties, gun crews (aim leading, fire at will / hold
 * fire), special shot and capturing ships whose crew has been beaten. Driven from each ship's own server tick
 * ({@link #shipTick}, {@link #adjustMove}) so moving a ship always carries the people standing on its deck.
 */
public final class NavalCombat {
    public static final String CANNONBALL = "cannonball", CHAIN_SHOT = "chain_shot", GRAPE_SHOT = "grape_shot";
    public static final List<String> AMMO = List.of(CANNONBALL, CHAIN_SHOT, GRAPE_SHOT);
    /** Below this closing speed (blocks/tick) a bow merely bumps; above it, it rams. */
    static final double RAM_MIN = 0.12;
    static final double SHOT_SPEED = 2.2, SHOT_GRAVITY = 0.02;

    /** Per-ship naval state. Crew, prize origin and squadron are saved with the ship (gun-crew ammo lives in its synced flags). */
    static final class State {
        final Set<UUID> crew = new LinkedHashSet<>();
        String prizeFaction = "";
        @Nullable UUID leader;
        int slot;
        // transient
        int targetId = -2;
        int leaderEntity = -1;
        boolean crewGone;
        long boardCooldown, ramCooldown, lastNoAmmo, fireTimer;
        int ramTicks;
        Vec3 push = Vec3.ZERO;
        final Map<Integer, Long> seatCd = new HashMap<>();
        final Map<Integer, Long> aggressors = new HashMap<>();
        final List<Leap> leaps = new ArrayList<>();
        final Map<Integer, Integer> boarders = new HashMap<>(); // boarder entity id -> ship id it boarded
        // statistics (self-tests, HUD)
        int shotsFired, shipHits;
        float lastRamDealt, lastRamTaken;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            ListTag l = new ListTag();
            for (UUID u : crew) l.add(StringTag.valueOf(u.toString()));
            t.put("Crew", l);
            t.putString("PrizeOf", prizeFaction);
            if (leader != null) t.putUUID("Leader", leader);
            t.putInt("Slot", slot);
            return t;
        }

        void load(CompoundTag t) {
            crew.clear();
            for (Tag e : t.getList("Crew", Tag.TAG_STRING)) {
                try {
                    crew.add(UUID.fromString(e.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            prizeFaction = t.getString("PrizeOf");
            leader = t.hasUUID("Leader") ? t.getUUID("Leader") : null;
            slot = t.getInt("Slot");
        }
    }

    /**
     * Someone in mid-leap from one deck to another (target position kept in the target ship's frame): a boarder
     * (who then fights on that deck) or one of a player's crew following their captain across.
     */
    record Leap(int npc, int targetShip, Vec3 from, Vec3 toLocal, int[] tick, boolean boarder) {}

    // ================================================================== per-tick hooks

    static void shipTick(ShipEntity ship, ServerLevel sl) {
        State n = ship.naval;
        long now = sl.getGameTime();
        if (ship.linkedId() >= 0) {
            ShipEntity p = linked(ship);
            double max = (ship.cls.length + (p == null ? 0 : p.cls.length)) / 2.0 + 30;
            if (p == null || !p.isAlive() || p.isSinking() || ship.isSinking() || p.linkedId() != ship.getId() || p.distanceToSqr(ship) > max * max) unlink(ship);
        }
        if (!n.leaps.isEmpty()) tickLeaps(ship, sl);
        if (!n.boarders.isEmpty()) tickBoarders(ship, sl);
        int phase = Math.floorMod(ship.getId(), 10);
        if (now % 10 == phase && !ship.flag(ShipEntity.F_NPC)) crewStations(ship, sl);
        if (!ship.flag(ShipEntity.F_NPC) && ship.flag(ShipEntity.F_FIRE_AT_WILL)) gunnery(ship, sl, now);
        if (ship.flag(ShipEntity.F_NPC) && now % 20 == phase) captureCheck(ship, sl);
        if (now % 200 == 0) n.aggressors.values().removeIf(t -> now - t > 1200);
    }

    /** Speed multiplier from torn rigging and lines (a grappled pair drifts together; a tow is heavy). */
    static float speedFactor(ShipEntity ship) {
        float f = 0.35f + 0.65f * ship.rigging();
        if (ship.linkedId() >= 0) {
            ShipEntity p = linked(ship);
            if (p != null) {
                if (ship.towing()) f *= Math.max(0.5f, 0.85f - p.cls.ordinal() * 0.05f);
                else if (!p.towing()) f *= 0.15f;
            }
        }
        return f;
    }

    /** Applies knock-back, line pulls and bow collisions/rams to the position a ship is about to move to. */
    static Vec3 adjustMove(ShipEntity ship, ServerLevel sl, Vec3 next) {
        State n = ship.naval;
        if (n.push.lengthSqr() > 1e-6) {
            next = next.add(n.push.x, 0, n.push.z);
            n.push = n.push.scale(0.85);
            if (n.push.lengthSqr() < 2.5e-5) n.push = Vec3.ZERO;
        }
        ShipEntity p = ship.linkedId() >= 0 ? linked(ship) : null;
        if (p != null && !ship.towing()) {
            if (p.towing()) next = towPull(ship, p, next);
            else next = alongsidePull(ship, p, next);
        }
        return ram(ship, sl, next, p);
    }

    // ================================================================== lines (grapple / tow)

    @Nullable
    public static ShipEntity linked(ShipEntity s) {
        int id = s.linkedId();
        if (id < 0) return null;
        return s.level().getEntity(id) instanceof ShipEntity o ? o : null;
    }

    /** Hook {@code a} to {@code b}; with {@code tow}, {@code a} tows {@code b} instead of hauling alongside. */
    public static void link(ShipEntity a, ShipEntity b, boolean tow) {
        unlink(a);
        unlink(b);
        a.setLinkRaw(b.getId() << 1 | (tow ? 1 : 0));
        b.setLinkRaw(a.getId() << 1);
    }

    public static void unlink(ShipEntity a) {
        ShipEntity p = linked(a);
        a.setLinkRaw(-1);
        if (p != null && p.linkedId() == a.getId()) p.setLinkRaw(-1);
    }

    /** Grappled side by side (and close enough to step or leap across). */
    public static boolean alongside(ShipEntity a, ShipEntity b) {
        if (a.linkedId() != b.getId() || a.towing() || b.towing()) return false;
        Vec3 rel = a.toLocal(b.position());
        double gap = Math.abs(rel.x) - beam(a) - beam(b);
        return gap < 2.5 && Math.abs(rel.z) < (a.cls.length + b.cls.length) * 0.35 + 2;
    }

    static double beam(ShipEntity s) {
        return s.cls.halfBeamAt(0.5f);
    }

    private static Vec3 localDir(ShipEntity s, double lx, double lz) {
        double yaw = Math.toRadians(s.getYRot()), c = Math.cos(yaw), sn = Math.sin(yaw);
        return new Vec3(lx * c - lz * sn, 0, lx * sn + lz * c);
    }

    private static Vec3 alongsidePull(ShipEntity ship, ShipEntity p, Vec3 next) {
        Vec3 rel = ship.toLocal(p.position());
        int side = rel.x >= 0 ? 1 : -1;
        double want = beam(ship) + beam(p) + 0.5;
        // each ship closes half of the error in its own tick
        Vec3 err = localDir(ship, rel.x - side * want, rel.z * 0.6);
        Vec3 move = err.scale(0.5);
        double len = move.length();
        if (len > 0.14) move = move.scale(0.14 / len);
        // swing parallel (or anti-parallel) to the other hull
        float d0 = Mth.wrapDegrees(p.getYRot() - ship.getYRot()), d1 = Mth.wrapDegrees(p.getYRot() + 180 - ship.getYRot());
        float d = Math.abs(d0) <= Math.abs(d1) ? d0 : d1;
        ship.setYRot(ship.getYRot() + Mth.clamp(d * 0.5f, -0.8f, 0.8f));
        return next.add(move);
    }

    private static Vec3 towPull(ShipEntity ship, ShipEntity tug, Vec3 next) {
        Vec3 behind = tug.position().subtract(tug.heading().scale(tug.cls.length / 2.0 + ship.cls.length / 2.0 + 4));
        Vec3 err = new Vec3(behind.x - next.x, 0, behind.z - next.z);
        double len = err.length();
        if (len > 1.0) {
            double max = Math.max(0.06, tug.speedNow() * 1.4 + 0.05);
            next = next.add(err.scale(Math.min(max, (len - 1.0) * 0.3) / len));
        }
        Vec3 to = tug.position().subtract(ship.position());
        if (to.horizontalDistanceSqr() > 4) {
            float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            ship.setYRot(ship.getYRot() + Mth.clamp(Mth.wrapDegrees(want - ship.getYRot()) * 0.2f, -2f, 2f));
        }
        return next;
    }

    // ================================================================== ramming

    static boolean inside(ShipEntity s, Vec3 p) {
        Vec3 l = s.toLocal(p);
        float half = s.cls.length / 2f;
        if (Math.abs(l.z) > half + 0.3 || l.y > s.cls.freeboard + 3 || l.y < -s.cls.draft - 2) return false;
        float t = (float) ((l.z + half) / s.cls.length);
        return Math.abs(l.x) <= s.cls.halfBeamAt(Mth.clamp(t, 0, 1)) + 0.3;
    }

    static float mass(ShipEntity s) {
        return s.cls.hull * (s.hasUpgrade("reinforced_hull") ? 1.3f : 1f);
    }

    private static Vec3 ram(ShipEntity ship, ServerLevel sl, Vec3 next, @Nullable ShipEntity partner) {
        if (ship.ferryTarget != null) return next; // ferry / Sea Train services keep their timetable (no rams, no bumps)
        Vec3 move = next.subtract(ship.position());
        if (move.horizontalDistanceSqr() < 1e-6) return next;
        double half = ship.cls.length / 2.0;
        Vec3 bowNow = ship.toWorld(0, 0.2, half), bowNext = bowNow.add(move.x, 0, move.z);
        for (ShipEntity o : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(half + 22, 6, half + 22),
                o -> o != ship && o != partner && !o.isSinking() && o.ferryTarget == null)) {
            if (!inside(o, bowNext) || inside(o, bowNow)) continue; // only a bow entering a hull counts (overlaps may slide apart)
            Vec3 vel = new Vec3(move.x, 0, move.z);
            Vec3 ovel = o.heading().scale(o.speedNow()).add(o.naval.push);
            double closing = vel.subtract(ovel).dot(ship.heading());
            if (closing > RAM_MIN && sl.getGameTime() >= ship.naval.ramCooldown) {
                impact(ship, o, (float) closing, bowNext, sl);
                return ship.position();
            }
            // a gentle bump: lose way and glance off sideways, away from the other hull, instead of sticking to it
            Vec3 rel = ship.toLocal(o.position());
            ship.setSpeed(ship.speedNow() * 0.5f);
            ship.naval.push = ship.naval.push.add(localDir(ship, rel.x >= 0 ? -1 : 1, 0).scale(0.06));
            return ship.position().add(ship.naval.push.x, 0, ship.naval.push.z);
        }
        return next;
    }

    /** A bow strikes another hull at {@code v} blocks/tick: damage both by speed and class, throw the target aside. */
    static void impact(ShipEntity ship, ShipEntity o, float v, Vec3 at, ServerLevel sl) {
        float mr = mass(ship), mt = mass(o);
        boolean bow = ship.hasUpgrade("ram_bow");
        float dealt = v * 170f * (2f * mr / (mr + mt)) * (bow ? 1.5f : 1f);
        float taken = v * 170f * (mt / (mr + mt)) * 0.5f * (bow ? 0.5f : 1f) * (ship.hasUpgrade("seastone_keel") ? 0.7f : 1f);
        o.hurtHull(dealt, null);
        ship.hurtHull(taken, null);
        Vec3 h = ship.heading();
        o.naval.push = o.naval.push.add(h.scale(v * 0.9 * mr / (mr + mt)));
        Vec3 rel = o.toLocal(ship.position());
        o.setYRot(o.getYRot() + (float) (Math.signum(rel.z == 0 ? 1 : rel.z) * Math.signum(rel.x == 0 ? 1 : rel.x) * Math.min(20, v * 14)));
        ship.setSpeed(ship.speedNow() * 0.2f);
        ship.naval.push = h.scale(-v * 0.35 * mt / (mr + mt));
        ship.naval.ramCooldown = sl.getGameTime() + 20;
        ship.naval.lastRamDealt = dealt;
        ship.naval.lastRamTaken = taken;
        noteAggression(o, ship);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.2f, 0.5f);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 3, 0.8, 0.5, 0.8, 0);
        sl.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 60, 1.5, 0.5, 1.5, 0.3);
        double lz = o.toLocal(at).z / (o.cls.length / 2.0);
        String section = lz > 0.4 ? "bow" : lz < -0.4 ? "stern" : "amidships";
        Component msg = Component.translatable("grandline.naval.rammed", o.shipName(), Component.translatable("grandline.naval.section." + section), (int) dealt, (int) taken).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : sl.players()) if (ship.carries(p) || p.getVehicle() == ship || o.carries(p) || p.getVehicle() == o) p.displayClientMessage(msg, true);
    }

    // ================================================================== shot

    static Item ammoItem(String kind) {
        return switch (kind) {
            case CHAIN_SHOT -> NavalItems.CHAIN_SHOT.get();
            case GRAPE_SHOT -> NavalItems.GRAPE_SHOT.get();
            default -> ModItems.CANNONBALL.get();
        };
    }

    @Nullable
    static String ammoKind(ItemStack s) {
        if (s.isEmpty()) return null;
        for (String k : AMMO) if (s.is(ammoItem(k))) return k;
        return null;
    }

    /** One round for a player's own cannon: the shot in hand, else cannonball, chain, grape. Creative is free. */
    @Nullable
    static String takeAmmo(Player p, ItemStack held) {
        String k = ammoKind(held);
        if (p.isCreative()) return k == null ? CANNONBALL : k;
        if (k != null) {
            held.shrink(1);
            return k;
        }
        for (String kind : AMMO) if (take(p, kind)) return kind;
        return null;
    }

    private static boolean take(Player p, String kind) {
        Item it = ammoItem(kind);
        for (ItemStack s : p.getInventory().items) {
            if (s.is(it)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Fire one shot of a kind from a muzzle (the ship's own hull is never hit). */
    static void launch(ShipEntity ship, @Nullable LivingEntity shooter, Vec3 muzzle, Vec3 dir, float damage, String ammo) {
        var info = Hits.Info.of(Element.IMPACT, 1.5f);
        ship.naval.shotsFired++;
        String tag = "gl_ship:" + ship.getId();
        switch (ammo) {
            case GRAPE_SHOT -> {
                for (int i = 0; i < 7; i++) {
                    Vec3 d = dir.normalize().add(ship.getRandom().nextGaussian() * 0.05, ship.getRandom().nextGaussian() * 0.03, ship.getRandom().nextGaussian() * 0.05);
                    AbilityProjectile p = AbilityProjectile.shoot(ship.level(), shooter, "pellet", 0.25f, muzzle, d, SHOT_SPEED * 0.95, damage * 0.6f, Hits.Info.physical(0.6f));
                    p.gravity = SHOT_GRAVITY;
                    p.life = 60;
                    p.ignoreShip = ship;
                    p.addTag(tag);
                    p.addTag("gl_grape");
                }
            }
            case CHAIN_SHOT -> {
                AbilityProjectile p = AbilityProjectile.shoot(ship.level(), shooter, "cannonball", 0.6f, muzzle, dir, SHOT_SPEED, damage * 0.6f, info);
                p.gravity = SHOT_GRAVITY;
                p.explosion = 1.2f;
                p.life = 120;
                p.ignoreShip = ship;
                p.addTag(tag);
                p.addTag("gl_chain");
            }
            default -> {
                AbilityProjectile p = AbilityProjectile.shoot(ship.level(), shooter, "cannonball", 0.5f, muzzle, dir, SHOT_SPEED, damage, info);
                p.gravity = SHOT_GRAVITY;
                p.explosion = 2.5f;
                p.life = 120;
                p.ignoreShip = ship;
                p.addTag(tag);
            }
        }
    }

    /** A projectile struck a ship: credit the firing ship, and let chain/grape shot do their own kind of damage. */
    static float shotHit(ShipEntity ship, AbilityProjectile ap, float hull) {
        for (String t : ap.getTags()) {
            if (!t.startsWith("gl_ship:")) continue;
            try {
                if (ship.level().getEntity(Integer.parseInt(t.substring(8))) instanceof ShipEntity from && from != ship) {
                    from.naval.shipHits++;
                    noteAggression(ship, from);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (ap.getTags().contains("gl_chain")) {
            float rigHp = 30 + 25 * Math.max(1, ship.cls.masts);
            ship.setRigging(ship.rigging() - hull / rigHp);
            return hull * 0.35f;
        }
        if (ap.getTags().contains("gl_grape")) return hull * 0.12f;
        return hull;
    }

    /**
     * Direction (length = shot speed) that lands a shot fired from {@code from} on a target at {@code target} moving
     * with {@code vel} blocks/tick, for the projectile's discrete motion (move, then gravity).
     */
    static Vec3 solveAim(Vec3 from, Vec3 target, Vec3 vel, double speed, double g) {
        Vec3 aimAt = target;
        double vh = speed, vy = 0;
        for (int it = 0; it < 4; it++) {
            Vec3 d = aimAt.subtract(from);
            double horiz = Math.max(0.5, d.horizontalDistance());
            for (int k = 0; k < 3; k++) {
                double ticks = Math.max(1, horiz / vh);
                vy = (d.y + g * ticks * (ticks - 1) / 2) / ticks;
                vy = Mth.clamp(vy, -speed * 0.7, speed * 0.7);
                vh = Math.sqrt(Math.max(0.01, speed * speed - vy * vy));
            }
            double ticks = horiz / vh;
            aimAt = target.add(vel.scale(ticks));
        }
        Vec3 d = aimAt.subtract(from);
        double horiz = Math.max(0.5, d.horizontalDistance());
        return new Vec3(d.x / horiz * vh, vy, d.z / horiz * vh);
    }

    /** Does the cannon at {@code seat} bear on a point (on its side, within 60° of the beam)? */
    static boolean bears(ShipEntity ship, int seat, Vec3 at) {
        int side = ship.seatLocal(seat).x >= 0 ? 1 : -1;
        Vec3 sideDir = localDir(ship, side, 0);
        Vec3 to = at.subtract(ship.muzzle(seat));
        to = new Vec3(to.x, 0, to.z);
        return to.lengthSqr() > 1 && to.normalize().dot(sideDir) > 0.5;
    }

    /** Where a gunner aims on another ship for a kind of shot. */
    static Vec3 aimPoint(ShipEntity t, String ammo) {
        return switch (ammo) {
            case GRAPE_SHOT -> t.toWorld(0, t.cls.freeboard + 1.0, 0);
            case CHAIN_SHOT -> t.toWorld(0, t.cls.freeboard + 0.6, 0);
            default -> t.toWorld(0, t.cls.freeboard * 0.5, 0);
        };
    }

    static Vec3 velocity(Entity e) {
        return new Vec3(e.getX() - e.xo, 0, e.getZ() - e.zo);
    }

    /**
     * A gunner at {@code seat} fires on {@code target} with a led, ballistic aim; skill narrows the spread.
     * @return true if the gun fired
     */
    static boolean gunnerFire(ShipEntity ship, int seat, LivingEntity gunner, Entity target, String ammo, float damage, float skill) {
        Vec3 at = target instanceof ShipEntity ts ? aimPoint(ts, ammo) : target.position().add(0, target.getBbHeight() * 0.5, 0);
        if (!bears(ship, seat, at)) return false;
        Vec3 muzzle = ship.muzzle(seat);
        Vec3 dir = solveAim(muzzle, at, velocity(target).subtract(velocity(ship)), SHOT_SPEED, SHOT_GRAVITY);
        double spread = Mth.clamp(0.028 - skill * 0.0035, 0.004, 0.03);
        var r = ship.getRandom();
        dir = dir.normalize().add(r.nextGaussian() * spread, r.nextGaussian() * spread * 0.6, r.nextGaussian() * spread);
        ship.fireShot(seat, dir, gunner, damage, ammo, true);
        return true;
    }

    // ================================================================== hostility

    public static void noteAggression(ShipEntity victim, ShipEntity by) {
        if (LivingSea.allied(victim, by)) return; // a stray shot between NPC ships of one side (a convoy and her escorts) starts no feud
        victim.naval.aggressors.put(by.getId(), victim.level().getGameTime());
        LivingSea.onAggression(victim, by); // a player's ship firing on, ramming or hooking an NPC ship
    }

    static boolean attackedBy(ShipEntity victim, ShipEntity by) {
        Long t = victim.naval.aggressors.get(by.getId());
        return t != null && victim.level().getGameTime() - t < 1200;
    }

    /** Should the gun crew of a player's ship {@code a} fire on {@code b}? */
    static boolean hostile(ShipEntity a, ShipEntity b) {
        if (b.isSinking() || b.flag(ShipEntity.F_PRIZE)) return false;
        if (attackedBy(a, b)) return true;
        if (b.flag(ShipEntity.F_NPC)) {
            if (b.naval.crewGone) return false;
            if (b.naval.targetId == a.getId()) return true;
            Player cap = a.owner == null ? null : a.level().getPlayerByUUID(a.owner);
            // merchants and bounty hunters are fired on only when they started it (above); raiders spare their allies
            if (b.npcFaction.equals("pirate")) return cap == null || !b.life.raidAlly(cap);
            if (!b.npcFaction.equals("marine") || cap == null) return false;
            CharacterData d = Characters.get(cap);
            return d.faction == Faction.PIRATE || d.faction == Faction.REVOLUTIONARY || d.bounty > 0;
        }
        return false;
    }

    /** May this player hook that ship? (NPC ships always; another player's ship only where PvP is allowed.) */
    static boolean mayGrapple(ServerPlayer sp, ShipEntity target) {
        if (target.owner == null || target.flag(ShipEntity.F_NPC)) return true;
        Player other = sp.level().getPlayerByUUID(target.owner);
        return other == null ? target.flag(ShipEntity.F_PRIZE) : com.grandline.combat.Pvp.allowed(sp, other);
    }

    /** The ship a player is aboard (riding a station or standing on deck). */
    @Nullable
    public static ShipEntity shipOf(Entity e) {
        if (e.getVehicle() instanceof ShipEntity s) return s;
        for (ShipEntity s : e.level().getEntitiesOfClass(ShipEntity.class, e.getBoundingBox().inflate(40), s -> s.carries(e))) return s;
        return null;
    }

    // ================================================================== gun crews (player ships)

    private static boolean isGunner(ShipEntity ship, Entity e) {
        int seat = ship.seatIndex(e);
        return seat >= 1 && seat <= ship.cls.cannons;
    }

    /**
     * The captain's crew: they follow the captain across to a ship grappled alongside (and back), take free gun
     * stations on "fire at will" and stand down on "hold fire".
     */
    static void crewStations(ShipEntity ship, ServerLevel sl) {
        if (ship.owner == null) return;
        ServerPlayer cap = sl.getServer().getPlayerList().getPlayer(ship.owner);
        if (cap != null && cap.level() == sl && ship.linkedId() >= 0) crossOver(ship, sl, cap);
        if (ship.cls.cannons == 0) return;
        boolean aboard = cap != null && cap.level() == sl && (cap.getVehicle() == ship || ship.carries(cap) || cap.distanceToSqr(ship) < 24 * 24);
        for (Entity e : ship.getPassengers()) {
            if (e instanceof GLNpc npc && isGunner(ship, npc) && CrewManager.isCrew(npc) && (!aboard || !ship.flag(ShipEntity.F_FIRE_AT_WILL))) {
                npc.stopRiding(); // stand down (one per pass)
                return;
            }
        }
        if (!aboard || !ship.flag(ShipEntity.F_FIRE_AT_WILL)) return;
        for (HumanoidNpc npc : sl.getEntitiesOfClass(HumanoidNpc.class, ship.getBoundingBox().inflate(ship.cls.length / 2.0 + 2, 4, ship.cls.length / 2.0 + 2),
                m -> !m.isPassenger() && ship.carries(m) && CrewManager.isCrewOf(m, cap) && !CrewManager.isWaiting(m) && m.getTarget() == null
                        && !m.flag(GLNpc.FLAG_SURRENDER) && !m.hasEffect(ModEffects.KNOCKED_OUT))) {
            int seat = freeGun(ship);
            if (seat > 0) ship.seatEntity(npc, seat);
            return;
        }
    }

    /** One crew member per pass leaps to whichever of the two grappled decks the captain stands on. */
    private static void crossOver(ShipEntity ship, ServerLevel sl, ServerPlayer cap) {
        ShipEntity other = linked(ship);
        if (other == null || !alongside(ship, other)) return;
        ShipEntity capDeck = cap.getVehicle() == other || other.carries(cap) ? other : cap.getVehicle() == ship || ship.carries(cap) ? ship : null;
        if (capDeck == null) return;
        ShipEntity from = capDeck == other ? ship : other;
        for (HumanoidNpc m : sl.getEntitiesOfClass(HumanoidNpc.class, from.getBoundingBox().inflate(from.cls.length / 2.0 + 2, 4, from.cls.length / 2.0 + 2),
                m -> !m.isPassenger() && from.carries(m) && CrewManager.isCrewOf(m, cap) && !CrewManager.isWaiting(m)
                        && !m.flag(GLNpc.FLAG_SURRENDER) && !m.hasEffect(ModEffects.KNOCKED_OUT))) {
            for (Leap l : ship.naval.leaps) if (l.npc() == m.getId()) return; // already in the air
            var r = ship.getRandom();
            double hb = Math.max(0.3, capDeck.cls.halfBeamAt(0.5f) - 1.2);
            Vec3 toLocal = new Vec3((r.nextDouble() * 2 - 1) * hb, capDeck.cls.freeboard + 0.05, (r.nextDouble() * 0.4 - 0.25) * capDeck.cls.length);
            ship.naval.leaps.add(new Leap(m.getId(), capDeck.getId(), m.position(), toLocal, new int[]{0}, false));
            m.getNavigation().stop();
            return;
        }
    }

    /** A free cannon station, alternating sides. */
    static int freeGun(ShipEntity ship) {
        int best = -1, bestCount = Integer.MAX_VALUE;
        int port = 0, star = 0;
        for (int s = 1; s <= ship.cls.cannons; s++) if (ship.getFirstPassengerAtSeat(s) != null) {
            if (ship.seatLocal(s).x >= 0) star++;
            else port++;
        }
        for (int s = 1; s <= ship.cls.cannons; s++) {
            if (ship.getFirstPassengerAtSeat(s) != null) continue;
            int count = ship.seatLocal(s).x >= 0 ? star : port;
            if (count < bestCount) {
                best = s;
                bestCount = count;
            }
        }
        return best;
    }

    /** Fire at will: every manned gun that bears fires on the nearest hostile ship, leading its motion. */
    static void gunnery(ShipEntity ship, ServerLevel sl, long now) {
        State n = ship.naval;
        if (now % 20 == Math.floorMod(ship.getId(), 20) || n.targetId == -2) {
            n.targetId = -1;
            double bd = 100 * 100;
            for (ShipEntity o : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(100, 20, 100), o -> o != ship && hostile(ship, o))) {
                double d = o.distanceToSqr(ship);
                if (d < bd) {
                    bd = d;
                    n.targetId = o.getId();
                }
            }
        }
        if (n.targetId < 0 || !(sl.getEntity(n.targetId) instanceof ShipEntity target) || !target.isAlive()) return;
        ServerPlayer cap = ship.owner == null ? null : sl.getServer().getPlayerList().getPlayer(ship.owner);
        if (ship.owner != null && (cap == null || cap.distanceToSqr(ship) > 64 * 64)) return; // nobody to give the order or hand out shot
        for (int seat = 1; seat <= ship.cls.cannons; seat++) {
            if (!(ship.getFirstPassengerAtSeat(seat) instanceof GLNpc gunner) || !gunner.isAlive() || gunner.flag(GLNpc.FLAG_SURRENDER)) continue;
            if (n.seatCd.getOrDefault(seat, 0L) > now) continue;
            String ammo = ammo(ship);
            if (cap != null && !cap.isCreative()) {
                if (!take(cap, ammo)) {
                    ammo = CANNONBALL;
                    if (!take(cap, ammo)) {
                        if (now - n.lastNoAmmo > 200) {
                            n.lastNoAmmo = now;
                            cap.displayClientMessage(Component.translatable("grandline.naval.out_of_shot").withStyle(ChatFormatting.RED), true);
                        }
                        return;
                    }
                }
            }
            float skill = gunner.tier() + (cap != null && CrewBonusesView.sniper(cap) ? 3 : 0);
            if (gunnerFire(ship, seat, gunner, target, ammo, (10f + gunner.tier() * 2.5f) * ship.crewCannon, skill)) {
                n.seatCd.put(seat, now + 70 - Math.min(30, gunner.tier() * 3) + ship.getRandom().nextInt(20));
            } else if (cap != null && !cap.isCreative()) {
                cap.getInventory().add(new ItemStack(ammoItem(ammo))); // gun did not bear: the round goes back
                n.seatCd.put(seat, now + 10);
            } else n.seatCd.put(seat, now + 10);
        }
    }

    /** Captain's orders: fire at will / hold fire. */
    public static void setFireAtWill(ShipEntity ship, @Nullable ServerPlayer by, boolean on) {
        ship.setFlag(ShipEntity.F_FIRE_AT_WILL, on);
        ship.naval.targetId = -2;
        if (by != null) by.displayClientMessage(Component.translatable(on ? "grandline.naval.fire_at_will" : "grandline.naval.hold_fire").withStyle(on ? ChatFormatting.RED : ChatFormatting.AQUA), true);
    }

    public static void setAmmo(ShipEntity ship, @Nullable ServerPlayer by, String kind) {
        int i = AMMO.indexOf(kind);
        if (i < 0) return;
        ship.setFlag(F_AMMO_LO, (i & 1) != 0);
        ship.setFlag(F_AMMO_HI, (i & 2) != 0);
        if (by != null) by.displayClientMessage(Component.translatable("grandline.naval.ammo", Component.translatable("item.grandline." + kind)), true);
    }

    /** Ammo the gun crew loads (kept in two bits of the ship's synced flags, so the HUD shows it and saves keep it). */
    static final byte F_AMMO_LO = 0x40, F_AMMO_HI = (byte) 0x80;

    public static String ammo(ShipEntity ship) {
        int i = (ship.flag(F_AMMO_LO) ? 1 : 0) | (ship.flag(F_AMMO_HI) ? 2 : 0);
        return AMMO.get(Math.min(i, AMMO.size() - 1));
    }

    /** Orders from the helm (key) or the /ship command: the player must command the ship they are aboard. */
    public static boolean order(ServerPlayer sp, String op, String arg) {
        ShipEntity ship = shipOf(sp);
        if (ship == null || !ship.mayCommand(sp)) {
            sp.displayClientMessage(Component.translatable("grandline.naval.no_command"), true);
            return false;
        }
        switch (op) {
            case "fire" -> setFireAtWill(ship, sp, true);
            case "hold" -> setFireAtWill(ship, sp, false);
            case "toggle_fire" -> setFireAtWill(ship, sp, !ship.flag(ShipEntity.F_FIRE_AT_WILL));
            case "ammo" -> setAmmo(ship, sp, arg.isEmpty() ? AMMO.get((AMMO.indexOf(ammo(ship)) + 1) % AMMO.size()) : arg);
            case "cut" -> unlink(ship);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================== boarding parties

    /** Send a wave of boarders leaping from {@code ship} onto {@code target}'s deck. Returns how many jumped. */
    public static int launchBoarders(ShipEntity ship, ShipEntity target, int max) {
        if (!(ship.level() instanceof ServerLevel sl)) return 0;
        List<GLNpc> idle = new ArrayList<>();
        for (UUID u : ship.naval.crew) {
            if (sl.getEntity(u) instanceof GLNpc m && m.isAlive() && !m.isPassenger() && ship.carries(m) && !m.flag(GLNpc.FLAG_SURRENDER)
                    && !m.hasEffect(ModEffects.KNOCKED_OUT) && !ship.naval.boarders.containsKey(m.getId())) idle.add(m);
        }
        int n = Math.min(max, idle.size() - (idle.size() > 1 ? 1 : 0)); // someone stays to mind the ship
        var r = ship.getRandom();
        for (int i = 0; i < n; i++) {
            GLNpc m = idle.get(i);
            double hb = Math.max(0.3, target.cls.halfBeamAt(0.5f) - 1.2);
            double lz = (r.nextDouble() * 0.4 - 0.25) * target.cls.length;
            Vec3 toLocal = new Vec3((r.nextDouble() * 2 - 1) * hb, target.cls.freeboard + 0.05, lz);
            ship.naval.leaps.add(new Leap(m.getId(), target.getId(), m.position(), toLocal, new int[]{0}, true));
            m.getNavigation().stop();
        }
        if (n > 0) {
            Component msg = Component.translatable("grandline.naval.boarders").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : sl.players()) if (target.carries(p) || p.getVehicle() == target) p.displayClientMessage(msg, true);
            sl.playSound(null, target.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
        }
        return n;
    }

    private static void tickLeaps(ShipEntity ship, ServerLevel sl) {
        Iterator<Leap> it = ship.naval.leaps.iterator();
        while (it.hasNext()) {
            Leap l = it.next();
            Entity e = sl.getEntity(l.npc());
            Entity t = sl.getEntity(l.targetShip());
            if (!(e instanceof GLNpc m) || !m.isAlive() || !(t instanceof ShipEntity ts) || ts.isSinking()) {
                it.remove();
                continue;
            }
            int k = ++l.tick()[0];
            double f = Math.min(1, k / 14.0);
            Vec3 to = ts.toWorld(l.toLocal().x, l.toLocal().y, l.toLocal().z);
            Vec3 pos = l.from().lerp(to, f).add(0, Math.sin(f * Math.PI) * 3.5, 0);
            m.setPos(pos.x, pos.y, pos.z);
            m.setDeltaMovement(Vec3.ZERO);
            m.fallDistance = 0;
            m.getNavigation().stop();
            if (k == 1) sl.sendParticles(ParticleTypes.CLOUD, l.from().x, l.from().y + 0.2, l.from().z, 4, 0.2, 0.1, 0.2, 0.02);
            if (f >= 1 && !l.boarder()) {
                it.remove();
                m.getNavigation().stop();
                continue;
            }
            if (f >= 1) {
                it.remove();
                ship.naval.boarders.put(m.getId(), ts.getId());
                m.addTag("gl_boarder");
                LivingEntity foe = boardTarget(m, ts, ship.naval.crew);
                if (foe != null) m.setTarget(foe);
            }
        }
    }

    /** Someone on the boarded deck to fight: the nearest player, or crew/hands of that ship hostile to the boarder. */
    @Nullable
    static LivingEntity boardTarget(GLNpc m, ShipEntity ts, Set<UUID> ownCrew) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, ts.getBoundingBox().inflate(ts.cls.length / 2.0 + 2, 6, ts.cls.length / 2.0 + 2),
                e -> e != m && e.isAlive() && (ts.carries(e) || e.getVehicle() == ts))) {
            boolean foe;
            if (e instanceof Player p) foe = !p.isCreative() && !p.isSpectator();
            else if (e instanceof GLNpc o) foe = !ownCrew.contains(o.getUUID()) && !o.flag(GLNpc.FLAG_SURRENDER) && (CrewManager.isCrew(o) || m.isHostileTo(o));
            else foe = false;
            if (!foe) continue;
            double d = e.distanceToSqr(m);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Boarders walk the enemy deck straight at their foe (path-finding cannot see a deck made of entities). */
    private static void tickBoarders(ShipEntity ship, ServerLevel sl) {
        Iterator<Map.Entry<Integer, Integer>> it = ship.naval.boarders.entrySet().iterator();
        long now = sl.getGameTime();
        while (it.hasNext()) {
            var en = it.next();
            if (!(sl.getEntity(en.getKey()) instanceof GLNpc m) || !m.isAlive() || m.flag(GLNpc.FLAG_SURRENDER) || !(sl.getEntity(en.getValue()) instanceof ShipEntity ts)) {
                it.remove();
                continue;
            }
            if (!ts.carries(m)) {
                if (m.isInWater() || m.getY() < ts.getY() - 1) it.remove(); // fell overboard
                continue;
            }
            LivingEntity foe = m.getTarget();
            if ((foe == null || !foe.isAlive() || !(ts.carries(foe) || foe.getVehicle() == ts)) && now % 10 == 0) {
                foe = boardTarget(m, ts, ship.naval.crew);
                m.setTarget(foe);
            }
            if (foe != null && m.distanceToSqr(foe) > 2.3 * 2.3) {
                m.getNavigation().stop();
                m.getMoveControl().setWantedPosition(foe.getX(), m.getY(), foe.getZ(), 1.15);
            }
        }
    }

    // ================================================================== capture

    /** True when every member of an NPC ship's crew is dead, gone, beaten senseless or has surrendered. */
    public static boolean crewDefeated(ShipEntity ship) {
        if (!(ship.level() instanceof ServerLevel sl)) return false;
        int alive = 0;
        double r2 = Math.pow(ship.cls.length + 60, 2);
        if (!ship.naval.crew.isEmpty()) {
            for (UUID u : ship.naval.crew)
                if (sl.getEntity(u) instanceof GLNpc m && m.isAlive() && !m.flag(GLNpc.FLAG_SURRENDER) && !m.hasEffect(ModEffects.KNOCKED_OUT)
                        && !m.flag(GLNpc.FLAG_ARRESTED) && m.distanceToSqr(ship) < r2) alive++;
        } else {
            for (GLNpc m : sl.getEntitiesOfClass(GLNpc.class, ship.getBoundingBox().inflate(ship.cls.length / 2.0, 6, ship.cls.length / 2.0), m -> ship.carries(m) || m.getVehicle() == ship))
                if (m.isAlive() && !m.flag(GLNpc.FLAG_SURRENDER) && !m.hasEffect(ModEffects.KNOCKED_OUT)) alive++;
        }
        return alive == 0;
    }

    private static void captureCheck(ShipEntity ship, ServerLevel sl) {
        boolean was = ship.naval.crewGone;
        ship.naval.crewGone = crewDefeated(ship);
        if (!ship.naval.crewGone) {
            ship.life.beatenAt = 0;
            return;
        }
        if (!was) {
            ship.setSail(0);
            Component msg = Component.translatable("grandline.naval.crew_beaten", ship.shipName()).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : sl.players()) if (p.distanceToSqr(ship) < 80 * 80) p.displayClientMessage(msg, true);
            LivingSea.onCrewBeaten(ship); // rewards; an unclaimed hulk founders later and leaves salvage
        }
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.distanceToSqr(ship) > Math.pow(ship.cls.length + 8, 2)) continue;
            if (ship.carries(p) || p.getVehicle() == ship) {
                capture(ship, p);
                return;
            }
        }
    }

    /**
     * Take an NPC ship whose crew is beaten: it becomes the player's prize (registered to them, flagged as a prize)
     * until a shipwright buys it or refits it into the fleet. Returns false while any of its crew still fights.
     */
    public static boolean capture(ShipEntity ship, Player p) {
        if (!ship.flag(ShipEntity.F_NPC) || ship.isSinking() || !crewDefeated(ship)) return false;
        String from = ship.npcFaction;
        ship.naval.prizeFaction = from;
        ship.naval.crewGone = false;
        ship.naval.targetId = -1;
        ship.naval.leader = null;
        ship.setFlag(ShipEntity.F_NPC, false);
        ship.setFlag(ShipEntity.F_PRIZE, true);
        ship.setFlag(ShipEntity.F_FIRE_AT_WILL, false);
        ship.npcFaction = "";
        ship.removeTag(LivingSea.TRANSIENT); // a prize is kept across restarts
        ship.life.route = "";
        ship.life.raidIsland = "";
        ship.owner = p.getUUID();
        ship.ownerName = Characters.get(p).name;
        ship.setShipName(Component.translatable("grandline.naval.prize_name", ship.shipName()).getString());
        ship.setSail(0);
        ship.setAnchored(true);
        for (Entity e : new ArrayList<>(ship.getPassengers())) if (!(e instanceof Player)) e.stopRiding();
        if (ship.getServer() != null) {
            ShipRegistry reg = ShipRegistry.get(ship.getServer());
            ShipRegistry.Record r = reg.get(ship.shipId) != null ? reg.get(ship.shipId) : reg.register(ship);
            r.prize = true;
            reg.update(ship);
        }
        if (p instanceof ServerPlayer sp) {
            CharacterData d = Characters.get(sp);
            sp.sendSystemMessage(Component.translatable("grandline.naval.captured", ship.shipName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (from.equals("marine") && d.faction != Faction.MARINE)
                com.grandline.npc.Bounties.raise(sp, d, 3_000_000L * (ship.cls.ordinal() + 1), Component.translatable("grandline.naval.captured_marine"));
            else if (from.equals("pirate") && Faction.isLaw(d.faction)) d.addReputation(Faction.MARINE.id(), 20 + 10 * ship.cls.ordinal());
            CharacterManager.markDirty(sp);
            com.grandline.quest.Quests.progress(sp, "capture", from.isEmpty() ? "any" : from, 1);
            com.grandline.quest.story.SeaJobs.onShipCaptured(sp, ship); // sea jobs: a named ship taken (Marine prize bonus), a convoy lost
            com.grandline.journey.Journey.onPrize(sp);
            com.grandline.journey.DenDenMushi.onPrizeTaken(sp, from); // a Marine prize may hold her captain's black snail
        }
        ship.level().playSound(null, ship.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    /** What a shipwright pays for a prize (fraction of the class price, by condition). */
    public static long prizeValue(ShipEntity ship) {
        float cond = Mth.clamp(ship.hull() / Math.max(1f, ship.maxHull()), 0.3f, 1f);
        return (long) (ship.cls.price * 0.3f * cond * (ship.naval.prizeFaction.equals("marine") ? 1.25f : 1f));
    }

    public static long refitFee(ShipEntity ship) {
        return ship.cls.price / 10;
    }

    public static String prizeFaction(ShipEntity ship) {
        return ship.naval.prizeFaction;
    }

    public static Set<UUID> npcCrew(ShipEntity ship) {
        return ship.naval.crew;
    }

    public static int shipHits(ShipEntity ship) {
        return ship.naval.shipHits;
    }

    public static int shotsFired(ShipEntity ship) {
        return ship.naval.shotsFired;
    }

    public static float lastRamDealt(ShipEntity ship) {
        return ship.naval.lastRamDealt;
    }

    public static float lastRamTaken(ShipEntity ship) {
        return ship.naval.lastRamTaken;
    }

    public static int boardersAboard(ShipEntity attacker, ShipEntity target) {
        int n = 0;
        for (var en : attacker.naval.boarders.entrySet())
            if (en.getValue() == target.getId() && attacker.level().getEntity(en.getKey()) instanceof GLNpc m && target.carries(m)) n++;
        return n;
    }

    /** Read-only view of crew roles that matter to gunnery (the sniper steadies every gun). */
    static final class CrewBonusesView {
        static boolean sniper(ServerPlayer cap) {
            return com.grandline.npc.crew.CrewBonuses.activeRoles(cap).contains("sniper");
        }
    }

    private NavalCombat() {}
}
