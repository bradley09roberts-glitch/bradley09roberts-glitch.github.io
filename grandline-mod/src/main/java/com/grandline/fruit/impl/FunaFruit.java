package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.ship.ShipEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Funa Funa no Mi — ORIGINAL ship Paramecia. The user's body holds the spirit of a ship: cannons sprout from their
 * arms, a gangplank bridge unrolls across water, the ship they stand on comes alive and sails itself wherever they
 * look (even with no wind in the Calm Belt), timber knits back into a damaged hull, the user rams like a prow, and at
 * awakening a phantom fleet sails through the enemy line. Passive: standing on a ship, its hull slowly mends itself.
 */
public final class FunaFruit {
    static final BlockState PLANK = Blocks.OAK_PLANKS.defaultBlockState();

    /** Ships brought to life by Living Ship -> game time the spell ends (expired by {@link #tickLivingShips}). */
    static final java.util.Map<ShipEntity, Long> LIVING = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Server tick (every second): a living ship goes back to normal when its time is up or it is gone — even if the
     * user died or left, so no ship is ever left sailing itself forever.
     */
    public static void tickLivingShips(net.minecraft.server.MinecraftServer server) {
        if (LIVING.isEmpty()) return;
        long now = server.overworld().getGameTime();
        synchronized (LIVING) {
            LIVING.entrySet().removeIf(en -> {
                ShipEntity s = en.getKey();
                if (s.isAlive() && !s.isSinking() && en.getValue() > now) return false;
                s.autopilot = false;
                return true;
            });
        }
    }

    /** Helper used by the living-ship ability: keep a ship sailing under the user's will. */
    static void steer(ShipEntity ship, LivingEntity u) {
        ship.setAnchored(false);
        ship.setSail(3);
        ship.autopilot = true;
        Vec3 look = u.getLookAngle();
        float want = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float diff = Mth.wrapDegrees(want - ship.getYRot());
        ship.setRudder(Mth.clamp(diff / 30f, -1, 1));
    }

    public static void init(DevilFruit f) {
        Hits.Info shot = FruitKit.info(Element.IMPACT, 1.2f);

        // Broadside: cannons sprout from the user's arms and fire a three-gun salvo.
        f.add(f.ability("broadside").cooldown(30).stamina(12).anim("double_palm").exec((u, c) -> {
            for (int i = -1; i <= 1; i++) {
                AbilityProjectile p = FruitKit.shoot(c, "cannonball", 0.5f, 2.2, FruitKit.dmg(c, 6), shot.withPoise(30));
                p.setDeltaMovement(PowerKit.yaw(p.getDeltaMovement(), i * 8).add(0, 0.05, 0));
                p.gravity = 0.02;
                p.explosion = 1.5f;
                p.life = 60;
            }
            // the muzzle blast itself scorches anyone standing right in front
            for (LivingEntity e : Hits.cone(u, 2.5 + u.getScale(), 70)) if (Hits.hostileTo(u, e)) PowerKit.hit(u, e, FruitKit.dmg(c, 3), shot.withPoise(15));
            FruitKit.fx(u, "funa_salvo", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.4f);
            return true;
        }));

        // Gangplank: a bridge of planks unrolls from the user's feet across water or a gap; it slams aside anyone on
        // its path. (Temporary — the planks fall away again.)
        f.add(f.ability("gangplank").unlock(5).cooldown(120).stamina(14).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            int y = (int) Math.floor(u.getY() - 0.2) - 1;
            int len = 14 + c.mastery / 10;
            Vec3 start = u.position();
            Set<Integer> struck = new HashSet<>();
            for (int d = 1; d <= len; d++) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 2, () -> {
                    Vec3 p = start.add(dir.scale(step));
                    for (int w = 0; w <= 1; w++) {
                        BlockPos bp = new BlockPos((int) Math.floor(p.x - dir.z * w * 0.9), y, (int) Math.floor(p.z + dir.x * w * 0.9));
                        BlockState cur = c.level.getBlockState(bp);
                        if (cur.canBeReplaced()) com.grandline.fruit.TerrainChanges.place(c.level, bp, PLANK);
                    }
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 1.6))
                        if (struck.add(e.getId()) && PowerKit.hit(u, e, FruitKit.dmg(c, 4), shot.withPoise(20))) PowerKit.push(e, p, 0.7, 0.2);
                    if (step % 3 == 0) FruitKit.fx(u, "funa_plank", p.add(0, 0.2, 0), (float) dir.x, (float) dir.z, 0);
                });
            }
            for (LivingEntity e : Hits.cone(u, 3 + u.getScale(), 60)) if (Hits.hostileTo(u, e) && struck.add(e.getId())) PowerKit.hit(u, e, FruitKit.dmg(c, 4), shot.withPoise(20));
            PowerKit.sound(c, start, SoundEvents.WOOD_PLACE, 1.5f, 0.7f);
            return true;
        }));

        // Living ship: the ship under the user comes alive — anchor up, all sails set, it sails itself and turns
        // wherever the user looks, wind or no wind. Without a ship, the user's own body catches the wind like a sail.
        f.add(f.ability("living_ship").unlock(12).cooldown(600).stamina(20).anim("cast_up").exec((u, c) -> {
            ShipEntity ship = OriginalKit.ownShip(u, 24);
            int dur = 600 + c.mastery * 6;
            CombatState.of(u).buffs.put("tailwind", dur);
            if (ship == null) {
                u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, true));
                u.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, dur, 0, false, true));
                FruitKit.fx(u, "funa_sail", u.position().add(0, 1, 0), 1.5f, 0, 0);
                // the snap of an unfurling sail knocks back whoever stands too close
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3.5)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.WIND, 1.5f).withPoise(20))) PowerKit.push(e, u.position(), 0.8, 0.2);
                }
                return true;
            }
            LIVING.put(ship, c.level.getGameTime() + dur);
            OriginalKit.pulses(c, 0, 10, dur / 10, n -> {
                if (!LIVING.containsKey(ship)) return;
                if (!ship.carries(u) && u.getVehicle() != ship && u.distanceTo(ship) > 32) {
                    LIVING.put(ship, 0L); // the user left the ship behind: the spell ends on the next check
                    return;
                }
                steer(ship, u);
                if (n % 4 == 0) FruitKit.fx(u, "funa_sail", ship.position().add(0, ship.cls.freeboard + 4, 0), ship.cls.beam, 1, 0);
            });
            if (u instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.funa.living_ship", ship.shipName()).withStyle(ChatFormatting.AQUA), true);
            PowerKit.sound(c, ship.position(), SoundEvents.WOOD_PLACE, 2f, 0.5f);
            return true;
        }));

        // Hull mend: timber knits itself back into the hull of the user's ship (a quarter of its hull over a few
        // seconds) and the crew on deck catch their breath. Without a ship, the user patches up their own body.
        f.add(f.ability("hull_mend").unlock(20).cooldown(900).stamina(26).anim("focus").exec((u, c) -> {
            ShipEntity ship = OriginalKit.ownShip(u, 16);
            if (ship == null) {
                OriginalKit.heal(u, 6 + c.mastery / 10f, "funa_mend");
                u.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 1));
                return true;
            }
            float total = ship.maxHull() * 0.25f + 30 + c.mastery;
            OriginalKit.pulses(c, 0, 20, 5, n -> {
                if (!ship.isAlive() || ship.isSinking()) return;
                ship.repair(total / 5);
                FruitKit.fx(u, "funa_mend", ship.position().add(0, ship.cls.freeboard + 0.5, 0), ship.cls.length / 2f, ship.cls.beam, 1);
            });
            for (LivingEntity a : OriginalKit.allies(u, ship.position(), ship.cls.length / 2.0 + 2, true))
                if (ship.carries(a) || a == u) OriginalKit.heal(a, 4, "funa_mend");
            PowerKit.sound(c, ship.position(), SoundEvents.WOOD_PLACE, 2f, 1.2f);
            return true;
        }));

        // Prow ram: the user becomes a ship's prow and rams forward — creatures are thrown aside, enemy ships in the
        // path take a crushing blow to the hull.
        f.add(f.ability("prow_ram").unlock(30).cooldown(200).stamina(30).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 end = FruitKit.blink(u, dir, 12);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 2, 1.6))
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 12), shot.withStun(15).withPoise(70))) PowerKit.push(e, e.position().subtract(dir), 1.2, 0.4);
            Set<ShipEntity> rammed = new HashSet<>();
            for (int d = 0; d <= (int) start.distanceTo(end) + 3; d += 3)
                for (ShipEntity s : OriginalKit.ships(u, start.add(dir.scale(d)), 3)) if (!OriginalKit.friendly(u, s) && rammed.add(s)) OriginalKit.wreck(u, s, 60 + c.mastery);
            FruitKit.fx(u, "funa_ram", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.WOOD_BREAK, 2f, 0.6f);
            return true;
        }));

        // Awakening — Ghost Fleet: three phantom ships sail along a line, each firing a full broadside at every enemy
        // (and enemy ship) near their course.
        f.add(f.ability("ghost_fleet").unlock(55).cooldown(1200).stamina(90).awakened().anim("cast_forward").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            double len = 32;
            for (int ship = 0; ship < 3; ship++) {
                int k = ship;
                OriginalKit.pulses(c, ship * 12, 6, 6, n -> {
                    Vec3 p = start.add(dir.scale(4 + n * len / 6)).add(PowerKit.yaw(dir, 90).scale((k - 1) * 4));
                    FruitKit.fx(u, "funa_ghost_ship", p, (float) dir.x, (float) dir.z, 1);
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 8)) {
                        Hits.hit(u, e, FruitKit.dmg(c, 4), shot.withPoise(25));
                        FruitKit.fx(u, "explosion", e.position().add(0, 1, 0), 1, 0, 0);
                    }
                    for (ShipEntity s : OriginalKit.ships(u, p, 8)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 20 + c.mastery / 5f);
                    if (n % 2 == 0) PowerKit.sound(c, p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.8f);
                });
            }
            return true;
        }));

        // Passive: the ship's spirit — standing on a ship, its hull slowly mends itself.
        f.tickHook = u -> {
            if (u.tickCount % 40 != 0) return;
            ShipEntity ship = OriginalKit.ownShip(u, 2);
            if (ship != null && ship.carries(u) && ship.hull() < ship.maxHull()) ship.repair(1 + ship.cls.ordinal() * 0.5f);
        };
    }

    private FunaFruit() {}
}
