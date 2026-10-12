package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Nendo Nendo no Mi — ORIGINAL clay Special Paramecia. The user's body is living clay: arms moulded into giant fists,
 * clay walls, clay soldiers that march and fight on their own for a while, a body that can simply be remoulded to
 * close wounds, a cast that hardens around a foe, and at awakening a clay colossus.
 * Passive: the soft clay body absorbs blunt blows and shockwaves (-30%). Water softens it (+25% from water); fire
 * bakes it hard (tougher but slower for a moment).
 */
public final class NendoFruit {
    static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    static final BlockState BAKED = Blocks.TERRACOTTA.defaultBlockState();

    /** A clay soldier summon: a walking figure that hunts the nearest enemy and punches it. */
    static final class Soldier {
        Vec3 pos;
        Soldier(Vec3 p) { pos = p; }
    }

    public static void init(DevilFruit f) {
        Hits.Info clay = FruitKit.info(Element.BLUNT, 1.6f);

        // Clay fist: the arm swells into a giant fist of clay and pounds everything along a short line.
        f.add(f.ability("clay_fist").cooldown(22).stamina(10).anim("heavy_punch").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.4, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 5 + u.getScale());
            for (LivingEntity e : Hits.line(u, from, dir, len, 1.3)) if (Hits.hostileTo(u, e)) PowerKit.hit(u, e, FruitKit.dmg(c, 8), clay.withPoise(40));
            FruitKit.fx(u, "nendo_fist", from.add(dir.scale(len * 0.7)), 1.5f, 0, 0);
            PowerKit.sound(c, from, SoundEvents.MUD_PLACE, 1.5f, 0.5f);
            return true;
        }));

        // Clay wall: a thick wall of clay slapped up in front of the user.
        f.add(f.ability("clay_wall").unlock(5).cooldown(90).stamina(14).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 center = u.position().add(dir.scale(2.5));
            for (int w = -2; w <= 2; w++) {
                double x = center.x - dir.z * w, z = center.z + dir.x * w;
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(x), (int) Math.floor(u.getY()), (int) Math.floor(z));
                int baseY = g == null ? (int) Math.floor(u.getY()) - 1 : g.getY();
                for (int y = 1; y <= 3; y++) PowerKit.placeFree(c.level, new BlockPos((int) Math.floor(x), baseY + y, (int) Math.floor(z)), CLAY);
            }
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 3)) if (PowerKit.hit(u, e, FruitKit.dmg(c, 4), clay.withPoise(25))) PowerKit.push(e, u.position(), 0.8, 0.2);
            FruitKit.fx(u, "nendo_wall", center.add(0, 1.5, 0), (float) dir.x, (float) dir.z, 2.5f);
            PowerKit.sound(c, center, SoundEvents.MUD_PLACE, 1.5f, 0.7f);
            return true;
        }));

        // Clay soldiers: three soldiers are moulded from the ground; for fifteen seconds they march on the nearest
        // enemy and pummel it.
        f.add(f.ability("clay_soldiers").unlock(15).cooldown(500).stamina(30).anim("cast_ground").exec((u, c) -> {
            Soldier[] squad = new Soldier[3];
            Vec3 dir = PowerKit.flatAim(c);
            for (int i = 0; i < 3; i++) squad[i] = new Soldier(u.position().add(PowerKit.yaw(dir, (i - 1) * 50).scale(2)));
            float punch = FruitKit.dmg(c, 3.5f);
            OriginalKit.pulses(c, 0, 5, 60 + c.mastery / 2, n -> {
                for (Soldier s : squad) {
                    LivingEntity t = c.target != null && c.target.isAlive() && c.target.position().distanceTo(s.pos) < 16 ? c.target
                            : PowerKit.foes(u, s.pos.add(0, 1, 0), 14).stream().findFirst().orElse(null);
                    if (t != null) {
                        Vec3 to = t.position().subtract(s.pos).multiply(1, 0, 1);
                        double d = to.length();
                        if (d > 1.6) s.pos = s.pos.add(to.scale(Math.min(0.9, d - 1.4) / d));
                        BlockPos g = PowerKit.ground(c.level, (int) Math.floor(s.pos.x), (int) Math.floor(s.pos.y + 1), (int) Math.floor(s.pos.z));
                        if (g != null) s.pos = new Vec3(s.pos.x, g.getY() + 1, s.pos.z);
                        if (d <= 2.2 && n % 3 == 0) {
                            Hits.hit(u, t, punch, clay.withPoise(20));
                            FruitKit.fx(u, "nendo_fist", t.position().add(0, 1, 0), 0.6f, 0, 0);
                        }
                    }
                    FruitKit.fx(u, "nendo_soldier", s.pos, t == null ? 0 : (float) Math.atan2(t.getZ() - s.pos.z, t.getX() - s.pos.x), 1, 0);
                }
            });
            PowerKit.sound(c, u.position(), SoundEvents.MUD_PLACE, 2f, 0.4f);
            return true;
        }));

        // Remould: the user kneads their own body back into shape — wounds close, and ailments are pressed out.
        f.add(f.ability("remould").unlock(20).cooldown(600).stamina(20).anim("focus").exec((u, c) -> {
            OriginalKit.heal(u, u.getMaxHealth() * 0.2f + c.mastery / 10f, "nendo_remould");
            u.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            u.removeEffect(MobEffects.WEAKNESS);
            u.removeEffect(MobEffects.WITHER);
            u.removeEffect(MobEffects.POISON);
            // flung-off lumps of clay batter whoever is close
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), clay.withPoise(15));
            PowerKit.sound(c, u.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
            return true;
        }));

        // Clay cast: wet clay engulfs the target and hardens into a cast around it for a few seconds.
        f.add(f.ability("clay_cast").unlock(30).cooldown(260).stamina(28).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 8), clay.withStun(60).withPoise(70).asUnblockable());
            OriginalKit.encaseFor(c, t, BAKED, 100);
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 2));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
            FruitKit.fx(u, "nendo_cast", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.MUD_PLACE, 1.5f, 0.4f);
            return true;
        }));

        // Awakening — Clay Colossus: a towering clay giant rises at a point and, for ten seconds, slams its fists down
        // on the nearest enemies.
        f.add(f.ability("clay_colossus").unlock(55).cooldown(1100).stamina(85).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            OriginalKit.pulses(c, 0, 20, 10, n -> {
                LivingEntity t = PowerKit.foes(u, at.add(0, 1, 0), 14).stream().findFirst().orElse(c.target != null && c.target.isAlive() ? c.target : null);
                FruitKit.fx(u, "nendo_colossus", at, t == null ? 0 : (float) t.getX(), t == null ? 0 : (float) t.getZ(), 0);
                if (t == null || t.position().distanceTo(at) > 16) return;
                for (LivingEntity e : PowerKit.foes(u, t.position().add(0, 1, 0), 4)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 12), clay.withStun(15).withPoise(70))) FruitKit.impulse(e, new Vec3(0, 0.7, 0), false);
                }
                PowerKit.paintDisc(c.level, t.position(), 2, CLAY, null);
                FruitKit.fx(u, "nendo_fist", t.position().add(0, 0.5, 0), 3, 0, 0);
                PowerKit.sound(c, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            });
            return true;
        }));

        // Passive: clay body — blunt blows and shockwaves are absorbed; water softens it; fire bakes it hard.
        f.incomingHook = (u, a, amount, info) -> switch (info.element()) {
            case BLUNT, IMPACT -> amount * 0.7f;
            case WATER -> amount * 1.25f;
            case FIRE, MAGMA -> {
                u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
                u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                yield amount;
            }
            default -> amount;
        };
    }

    private NendoFruit() {}
}
