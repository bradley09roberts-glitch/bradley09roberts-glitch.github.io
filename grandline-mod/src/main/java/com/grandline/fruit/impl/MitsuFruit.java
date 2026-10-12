package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Mitsu Mitsu no Mi — ORIGINAL honey Logia. The user becomes golden honey: sticky globs, pools of honey that glue
 * feet to the ground (real honey blocks, temporary), a sweet scent that lures enemies to the user, royal jelly that
 * heals and feeds the crew, and amber that seals a foe inside.
 * Passive: anything that strikes or passes through the honey body comes away stuck (slowed).
 * Weakness: fire caramelises honey — fire always connects and deals 30% more.
 */
public final class MitsuFruit {
    static final BlockState HONEY = Blocks.HONEY_BLOCK.defaultBlockState();
    static final BlockState AMBER = Blocks.HONEYCOMB_BLOCK.defaultBlockState();

    static void stick(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp));
        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, amp));
    }

    public static void init(DevilFruit f) {
        Hits.Info glob = FruitKit.info(Element.BLUNT, 0.4f);

        // Honey shot: a heavy glob of honey that sticks the target in place.
        f.add(f.ability("honey_shot").cooldown(18).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "mitsu_glob", 0.6f, 1.9, FruitKit.dmg(c, 5), glob.withPoise(20));
            p.gravity = 0.015;
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) stick(hit, 60, 2);
            };
            return true;
        }));

        // Honey trap: the ground at a point floods with honey (honey blocks) — feet stick, movement crawls.
        f.add(f.ability("honey_trap").unlock(5).cooldown(160).stamina(20).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            double r = 3.5 + c.mastery / 40.0;
            PowerKit.paintDisc(c.level, at, r, HONEY, null);
            OriginalKit.pulses(c, 0, 10, 12, n -> {
                if (n % 3 == 0) FruitKit.fx(u, "mitsu_pool", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), r + 0.5)) {
                    if (Math.abs(e.getY() - at.y) > 2) continue;
                    stick(e, 20, 3);
                    if (n % 4 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), glob.withPoise(10));
                }
            });
            PowerKit.sound(c, at, SoundEvents.HONEY_BLOCK_PLACE, 1.5f, 0.7f);
            return true;
        }));

        // Sweet lure: an irresistible scent — enemies are drawn to the user and fixate on them.
        f.add(f.ability("sweet_lure").unlock(12).cooldown(240).stamina(18).anim("focus").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 14)) {
                PowerKit.pull(e, u.position(), 1.0, 0.15);
                if (e instanceof Mob m) m.setTarget(u);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                Hits.hit(u, e, FruitKit.dmg(c, 2), glob.withPoise(10));
            }
            FruitKit.fx(u, "mitsu_scent", u.position().add(0, 1, 0), 14, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.HONEY_DRINK, 1.5f, 0.6f);
            return true;
        }));

        // Royal jelly: heals the user and nearby allies, coats them in a protective honey glaze and feeds them.
        f.add(f.ability("royal_jelly").unlock(20).cooldown(500).stamina(24).anim("focus").exec((u, c) -> {
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 7, true)) {
                OriginalKit.heal(a, 8 + c.mastery / 10f, "mitsu_heal");
                a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
                a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 0));
                a.removeEffect(MobEffects.POISON);
                if (a instanceof ServerPlayer sp) sp.getFoodData().eat(6, 0.8f);
            }
            PowerKit.sound(c, u.position(), SoundEvents.HONEY_DRINK, 1f, 1.2f);
            return true;
        }));

        // Amber coffin: the target is sealed in hardening honey that sets like amber.
        f.add(f.ability("amber_coffin").unlock(30).cooldown(260).stamina(30).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 8), glob.withStun(60).withPoise(80).asUnblockable());
            OriginalKit.encaseFor(c, t, AMBER, 100);
            stick(t, 100, 4);
            FruitKit.fx(u, "mitsu_amber", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.HONEYCOMB_WAX_ON, 1.5f, 0.6f);
            return true;
        }));

        // Awakening — Honey Flood: a golden flood buries the area in honey; everyone inside is glued and smothered,
        // then the honey sets hard around them.
        f.add(f.ability("honey_flood").unlock(55).cooldown(1000).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            double r = 9;
            PowerKit.paintDisc(c.level, at, r, HONEY, HONEY);
            OriginalKit.pulses(c, 0, 5, 16, n -> {
                FruitKit.fx(u, "mitsu_pool", at, (float) r, 1, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    stick(e, 20, 4);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1));
                    if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), glob.withPoise(10));
                }
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 82, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 12), glob.withStun(40).withPoise(80).asUnblockable());
                    FruitKit.fx(u, "mitsu_amber", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
                }
                PowerKit.sound(c, at, SoundEvents.HONEYCOMB_WAX_ON, 2f, 0.4f);
            });
            return true;
        }));

        // Passive: a sticky honey body (Logia). Attackers come away stuck; fire caramelises it.
        f.intangibleHook = (u, a, info) -> {
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return false;
            if (a != null && a.distanceTo(u) < 3.5) stick(a, 40, 1);
            return true;
        };
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return amount * 1.3f;
            if (a != null && a.distanceTo(u) < 3.5) stick(a, 40, 1);
            return amount;
        };
    }

    private MitsuFruit() {}
}
