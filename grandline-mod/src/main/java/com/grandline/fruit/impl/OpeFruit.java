package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Ope Ope no Mi — creates a ROOM in which the user can relocate and dissect anything. */
public final class OpeFruit {
    public static final String ROOM = "room";

    static double radius(AbilityContext c) {
        return 8 + c.mastery / 6.0 + (c.awakened ? 10 : 0);
    }

    static boolean roomActive(LivingEntity u) {
        return CombatState.of(u).hasBuff(ROOM);
    }

    public static void init(DevilFruit f) {
        f.add(f.ability("room").cooldown(60).stamina(20).anim("cast_up").exec((u, c) -> {
            CombatState.of(u).buffs.put(ROOM, 20 * (10 + c.mastery / 5));
            FruitKit.fx(u, "room", u.position(), (float) radius(c), 20 * (10 + c.mastery / 5f), 0);
            return true;
        }));
        f.add(f.ability("shambles").cooldown(30).stamina(10).anim("finger_twist").exec((u, c) -> {
            if (!roomActive(u)) return false;
            LivingEntity t = c.target;
            if (t != null && t.distanceTo(u) <= radius(c) * 2) {
                Vec3 a = u.position(), b = t.position();
                u.teleportTo(b.x, b.y, b.z);
                t.teleportTo(a.x, a.y, a.z);
            } else {
                Vec3 p = FruitKit.aimPoint(c, radius(c));
                u.teleportTo(p.x, p.y, p.z);
            }
            u.fallDistance = 0;
            FruitKit.fx(u, "shambles", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));
        f.add(f.ability("amputate").unlock(10).cooldown(70).stamina(20).anim("wide_slash").exec((u, c) -> {
            if (!roomActive(u)) return false;
            for (LivingEntity e : Hits.sphere(u, u.position(), radius(c))) {
                if (Hits.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.SLASH, 0.2f).withStun(20)) == Hits.Result.HIT)
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            }
            FruitKit.fx(u, "room_slash", u.position().add(0, 1, 0), (float) radius(c), 0, 0);
            return true;
        }));
        f.add(f.ability("takt").unlock(15).cooldown(90).stamina(18).anim("cast_up").exec((u, c) -> {
            if (!roomActive(u)) return false;
            for (LivingEntity e : Hits.sphere(u, u.position(), radius(c))) {
                if (!Hits.hostileTo(u, e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 2));
                CombatState.of(e).staggeredUntil = c.level.getGameTime() + 40;
            }
            return true;
        }));
        f.add(f.ability("injection_shot").unlock(20).cooldown(60).stamina(16).anim("thrust").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, c.look(), roomActive(u) ? 14 : 7);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1, 1)) {
                Hits.hit(u, e, FruitKit.dmg(c, 9), FruitKit.info(Element.PIERCE, 1f).withPoise(40));
            }
            return true;
        }));
        f.add(f.ability("counter_shock").unlock(30).cooldown(100).stamina(24).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 4 ? c.target : Hits.cone(u, 3.5, 60).stream().findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 12), FruitKit.info(Element.LIGHTNING, 1.5f).withStun(50).asUnblockable());
            FruitKit.fx(u, "discharge", t.position().add(0, 1, 0), 1.5f, 0, 0);
            return true;
        }));
    }

    private OpeFruit() {}
}
