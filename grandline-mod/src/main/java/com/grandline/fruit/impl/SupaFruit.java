package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Supa Supa no Mi — a body of blades. Any part of the user becomes steel edges; the body itself is as hard as steel,
 * so cuts and stabs barely scratch it.
 */
public final class SupaFruit {
    static final String SPIDER = "supa_spider";

    static void bleed(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, 0));
    }

    public static void init(DevilFruit f) {
        Hits.Info blade = FruitKit.info(Element.SLASH, 0.8f);

        // Sparkling Daisy: arms become great blades in a cleaving sweep.
        f.add(f.ability("sparkling_daisy").cooldown(22).stamina(10).anim("wide_slash").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 4.5, 110)) if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), blade.withPoise(30))) bleed(e, 40);
            FruitKit.fx(u, "blade_sweep", c.eye().add(c.look().scale(1.6)), 4.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            return true;
        }));

        // Atomic Spar: a cross-cut so fast it flies off as a crescent of steel.
        f.add(f.ability("atomic_spar").unlock(5).cooldown(40).stamina(15).anim("slash_right").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "steel_crescent", 1.0f, 2.4, FruitKit.dmg(c, 8), blade.withPoise(28));
            p.pierce = true;
            p.life = 26;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) bleed(hit, 40);
            };
            return true;
        }));

        // Spider: the whole body hardens into polished steel — almost nothing gets through, and attackers cut
        // themselves on it. The user can barely move meanwhile.
        f.add(f.ability("spider").unlock(15).cooldown(360).stamina(22).anim("iron_body").exec((u, c) -> {
            int dur = 70 + c.mastery / 2;
            CombatState.of(u).buffs.put(SPIDER, dur);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 4, false, false));
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 4), blade.withPoise(20));
            FruitKit.fx(u, "steel_body", u.position().add(0, 1, 0), 1.2f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.ANVIL_PLACE, 0.7f, 1.4f);
            return true;
        }));

        // Spiral Hollow: spinning blades sprout from the body as the user drills straight through the enemy line.
        f.add(f.ability("spiral_hollow").unlock(25).cooldown(120).stamina(26).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 end = FruitKit.blink(u, dir, 9);
            Set<Integer> struck = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 3L, () -> {
                    for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), dir, start.distanceTo(end) + 1, 1.4)) {
                        if (!Hits.hostileTo(u, e)) continue;
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, struck.add(e.getId()) ? 6 : 3), blade.withPoise(25))) bleed(e, 30);
                    }
                });
            }
            FruitKit.fx(u, "blade_trail", start.add(0, 1, 0), (float) end.x, (float) end.y + 0.5f, (float) end.z);
            return true;
        }));

        // Awakening — Thousand Blade Storm: blades erupt from every inch of the body in all directions.
        f.add(f.ability("blade_storm").unlock(50).cooldown(900).stamina(75).awakened().anim("spin_slash").exec((u, c) -> {
            for (int i = 0; i < 6; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 4L, () -> {
                    if (!u.isAlive()) return;
                    Vec3 center = u.position().add(0, 1, 0);
                    Hits.Info info = k == 5 ? FruitKit.info(Element.SLASH, 2.5f).withPoise(80).asUnblockable() : blade.withPoise(20);
                    for (LivingEntity e : PowerKit.foes(u, center, 6.5)) if (PowerKit.hit(u, e, FruitKit.dmg(c, k == 5 ? 10 : 4), info)) bleed(e, 60);
                    FruitKit.fx(u, "blade_storm", center, 6.5f, 0, 0);
                });
            }
            PowerKit.sound(c, u.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
            return true;
        }));

        // Passive: a steel body — cuts and stabs do half damage, blunt blows 20% less; blades sprout from every blow
        // (+20% unarmed damage). Hardened in Spider, it shrugs off nearly everything and cuts melee attackers.
        f.incomingHook = (u, a, amount, info) -> {
            if (CombatState.of(u).hasBuff(SPIDER)) {
                if (a != null && a.distanceTo(u) < 4) a.hurt(u.damageSources().thorns(u), Math.min(6, amount * 0.4f));
                return amount * 0.25f;
            }
            return switch (info.element()) {
                case SLASH, PIERCE -> amount * 0.5f;
                case BLUNT, PHYSICAL -> amount * 0.8f;
                default -> amount;
            };
        };
        f.outgoingHook = (u, v, amount, info) -> info.element() == Element.PHYSICAL ? amount * 1.2f : amount;
    }

    private SupaFruit() {}
}
