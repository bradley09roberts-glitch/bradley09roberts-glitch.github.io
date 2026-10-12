package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sasori Sasori no Mi, Model: Jaekelopterus — ORIGINAL Ancient Zoan (wave 4). The user becomes the giant sea scorpion
 * of the ancient oceans, the largest arthropod that ever lived — a legend of the deep around Fish-Man Island: great
 * pincers that crush armour, a venomous tail spike, a sideways scuttle, an exoskeleton that shrugs off blows, a volley
 * of venom spines and, at awakening, phantom pincers that burst out of the ground in a line.
 * Passive: in form the carapace turns cutting and piercing blows (-20%) and the scorpion's strikes may envenom.
 * Weakness: standard Zoan; a heavy blow cracks the shell (blunt and impact +20%).
 */
public final class SeaScorpionFruit {
    static final String SHELL = "sasori_shell";
    private static boolean chipping;
    /** Which side the next scuttle goes to (alternates). */
    static final java.util.Map<LivingEntity, Boolean> SIDE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    static void envenom(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amp));
    }

    public static void init(DevilFruit f) {
        Hits.Info claw = FruitKit.info(Element.BLUNT, 0.4f);
        Hits.Info sting = FruitKit.info(Element.PIERCE, 0.4f);
        PowerKit.zoanTransform(f);

        // Pincer crush: the great pincers close on the target and squeeze three times, cracking any guard.
        f.add(f.ability("pincer_crush").cooldown(40).stamina(12).anim("grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 4 + u.getScale());
            if (t == null) {
                FruitKit.fx(u, "sasori_pincer", c.eye().add(c.look().scale(2)), 1, 0, 0);
                return true;
            }
            OriginalKit.pulses(c, 0, 10, 3, n -> {
                if (!t.isAlive() || t.distanceTo(u) > 6 + u.getScale()) return;
                CombatState.of(t).blocking = false;
                t.setDeltaMovement(Vec3.ZERO);
                Hits.hit(u, t, FruitKit.dmg(c, n == 2 ? 7 : 3), claw.withStun(12).withPoise(35).asUnblockable());
                FruitKit.fx(u, "sasori_pincer", t.position().add(0, t.getBbHeight() * 0.6, 0), t.getBbWidth() + 0.3f, n == 2 ? 1 : 0, 0);
            });
            PowerKit.sound(c, t.position(), SoundEvents.ANVIL_LAND, 0.6f, 1.6f);
            return true;
        }));

        // Tail sting: the tail spike whips over the shoulder along a line — venom and a moment of paralysis.
        f.add(f.ability("tail_sting").unlock(5).cooldown(60).stamina(14).anim("whip_crack").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 from = c.eye().add(0, 0.3, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 6 + u.getScale());
            for (LivingEntity e : Hits.line(u, from, dir, len, 0.7)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 8), sting.withStun(25).withPoise(35))) {
                    envenom(e, 100, 1);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5));
                }
            }
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "sasori_sting", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, from, SoundEvents.TRIDENT_HIT, 1f, 0.8f);
            return true;
        }));

        // Scuttle: a sudden sideways scuttle (alternating left and right) out of harm's way, clipping anyone where it
        // stops.
        f.add(f.ability("scuttle").unlock(12).cooldown(50).stamina(10).anim("dash").exec((u, c) -> {
            Vec3 look = PowerKit.flatAim(c);
            boolean right = SIDE.getOrDefault(u, true);
            SIDE.put(u, !right);
            Vec3 side = right ? new Vec3(-look.z, 0, look.x) : new Vec3(look.z, 0, -look.x);
            Vec3 start = u.position();
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            FruitKit.blink(u, side, 6);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 4), claw.withPoise(20));
            // the scuttling legs rake whoever it slipped away from
            for (LivingEntity e : PowerKit.foes(u, start.add(0, 0.5, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 3), sting.withPoise(15));
            FruitKit.fx(u, "sasori_scuttle", start, (float) u.getX(), (float) u.getY(), (float) u.getZ());
            PowerKit.sound(c, u.position(), SoundEvents.SPIDER_STEP, 1.5f, 0.8f);
            return true;
        }));

        // Carapace: the exoskeleton hardens for eight seconds — resistance, and blades and fists chip on the shell
        // (melee attackers take damage back).
        f.add(f.ability("carapace").unlock(20).cooldown(320).stamina(18).anim("harden").exec((u, c) -> {
            int dur = 160 + c.mastery;
            CombatState.of(u).buffs.put(SHELL, dur);
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 1));
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), claw.withPoise(15));
            FruitKit.fx(u, "sasori_shell", u.position().add(0, 1, 0), u.getBbWidth() + 0.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.5f, 0.6f);
            return true;
        }));

        // Spine volley: the tail flicks a fan of venom spines.
        f.add(f.ability("spine_volley").unlock(30).cooldown(100).stamina(20).anim("throw").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -2; i <= 2; i++) {
                Vec3 d = PowerKit.yaw(dir, i * 8);
                AbilityProjectile p = AbilityProjectile.shoot(u, "sasori_spine", 0.3f, c.eye().add(d.scale(0.6)).add(0, 0.1, 0), d, 2.5, FruitKit.dmg(c, 5), sting.withPoise(15));
                p.life = 28;
                p.onHit = (pr, hit, pos) -> {
                    if (hit != null) envenom(hit, 100, 1);
                };
            }
            PowerKit.sound(c, u.position(), SoundEvents.ARROW_SHOOT, 1.2f, 0.7f);
            return true;
        }));

        // Awakening — Paleozoic Reign: phantom pincers burst out of the ground one after another along a line in front;
        // every enemy in their path is seized, crushed and envenomed.
        f.add(f.ability("paleozoic_reign").unlock(55).cooldown(1000).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            java.util.Set<LivingEntity> caught = new java.util.HashSet<>();
            OriginalKit.pulses(c, 0, 3, 7, n -> {
                Vec3 p = start.add(dir.scale(2 + n * 2));
                for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 2.6)) {
                    if (!caught.add(e)) continue;
                    Hits.hit(u, e, FruitKit.dmg(c, 18), claw.withStun(40).withPoise(100).asUnblockable());
                    envenom(e, 140, 2);
                    FruitKit.impulse(e, new Vec3(0, 0.6, 0), false);
                }
                FruitKit.fx(u, "sasori_reign", p, 2.6f, 0, 0);
                if (n % 2 == 0) PowerKit.sound(c, p, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
            });
            return true;
        }));

        // Passive: in form the carapace turns cuts and thrusts (-20%), with Carapace up melee attackers chip on it;
        // heavy blows crack it (+20%). In form, a third of the scorpion's own blows envenom.
        f.incomingHook = (u, a, amount, info) -> {
            boolean form = !Zoan.form(u).isEmpty();
            if (CombatState.of(u).hasBuff(SHELL) && a != null && !chipping && a.distanceTo(u) < 3.5 && Hits.hostileTo(u, a)) {
                chipping = true;
                try {
                    Hits.hit(u, a, 2, FruitKit.info(Element.PIERCE, 0f).withPoise(10));
                } finally {
                    chipping = false;
                }
            }
            if (info.element() == Element.BLUNT || info.element() == Element.IMPACT) return amount * 1.2f;
            if (form && (info.element() == Element.SLASH || info.element() == Element.PIERCE || info.element() == Element.PHYSICAL)) return amount * 0.8f;
            return amount;
        };
        f.outgoingHook = (u, v, amount, info) -> {
            if (!Zoan.form(u).isEmpty() && u.getRandom().nextInt(3) == 0) envenom(v, 60, 0);
            return amount;
        };
    }

    private SeaScorpionFruit() {}
}
