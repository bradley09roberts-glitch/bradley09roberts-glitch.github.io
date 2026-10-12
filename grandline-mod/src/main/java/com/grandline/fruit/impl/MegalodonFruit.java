package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import com.grandline.ship.ShipEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Same Same no Mi, Model: Megalodon — ORIGINAL Ancient Zoan. The user becomes the prehistoric giant shark: jaws that
 * tear and drink blood, a fin that "swims" through solid ground, a blood frenzy that hunts the wounded, a ramming
 * charge that stoves in ship hulls, and at awakening a maw of the abyss. The cruel joke of the fruit: like every fruit
 * user, the megalodon cannot swim — it hunts on land and from the decks.
 * Passive: in form, every bite of damage feeds it (10% lifesteal) and badly wounded enemies nearby are sensed (glow).
 */
public final class MegalodonFruit {
    static final String FRENZY = "same_frenzy";

    public static void init(DevilFruit f) {
        Hits.Info bite = FruitKit.info(Element.SLASH, 0.6f);
        PowerKit.zoanTransform(f);

        // Jaws: a massive bite that tears flesh (bleeding) and heals the shark by a quarter of the damage.
        f.add(f.ability("jaws").cooldown(26).stamina(11).anim("pounce").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = c.target != null && c.target.isAlive() && c.target.distanceTo(u) < 4 + u.getScale() ? c.target
                    : Hits.cone(u, 4 + u.getScale(), 70).stream().filter(e -> Hits.hostileTo(u, e)).findFirst().orElse(null);
            if (t == null) {
                FruitKit.fx(u, "same_bite", c.eye().add(c.look().scale(2)), 1, 0, 0);
                return true;
            }
            float before = t.getHealth();
            Hits.hit(u, t, FruitKit.dmg(c, 10), bite.withPoise(40));
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            u.heal(Math.max(0, before - t.getHealth()) * 0.25f);
            FruitKit.fx(u, "same_bite", t.position().add(0, t.getBbHeight() * 0.6, 0), 1.2f, 1, 0);
            PowerKit.sound(c, t.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
            return true;
        }));

        // Ground fin: the shark dives into the ground and swims through it — only a fin cuts the earth — then bursts up
        // with a bite at the end of the run.
        f.add(f.ability("ground_fin").unlock(5).cooldown(90).stamina(18).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            long now = c.level.getGameTime();
            Vec3 start = u.position();
            LivingEntity t = PowerKit.target(c, 16);
            Vec3 dir = t != null ? t.position().subtract(start).multiply(1, 0, 1).normalize() : PowerKit.flatAim(c);
            double dist = t != null ? Math.max(1, t.position().distanceTo(start) - 1.2) : 12;
            CombatState.of(u).iframesUntil = now + 12;
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false));
            Vec3 end = FruitKit.blink(u, dir, dist);
            FruitKit.fx(u, "same_fin", start, (float) end.x, (float) end.y, (float) end.z);
            CombatState.of(u).schedule(now + 6, () -> {
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3))
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 9), bite.withStun(12).withPoise(45))) FruitKit.impulse(e, new Vec3(0, 0.8, 0), false);
                FruitKit.fx(u, "same_breach", u.position(), 2, 0, 0);
                PowerKit.sound(c, u.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.5f);
            });
            return true;
        }));

        // Blood frenzy: the scent of blood drives the shark wild — faster, stronger, and every wounded enemy around is
        // laid bare. Hits on targets below half health deal 30% more while it lasts.
        f.add(f.ability("blood_frenzy").unlock(15).cooldown(500).stamina(20).anim("roar").exec((u, c) -> {
            int dur = 200 + c.mastery * 2;
            CombatState.of(u).buffs.put(FRENZY, dur);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 0));
            for (LivingEntity e : PowerKit.foes(u, u.position(), 32)) {
                if (e.getHealth() < e.getMaxHealth() * 0.5f) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0));
            }
            // the frenzied thrash catches whoever is right in front
            for (LivingEntity e : Hits.cone(u, 3 + u.getScale(), 100)) if (Hits.hostileTo(u, e)) Hits.hit(u, e, FruitKit.dmg(c, 4), bite.withPoise(20));
            FruitKit.fx(u, "same_frenzy", u.position().add(0, 1, 0), 2, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1f, 1.4f);
            return true;
        }));

        // Hull breaker: a ramming charge — creatures in the way are mauled and thrown, and any enemy ship it hits takes
        // a hull-splitting blow.
        f.add(f.ability("hull_breaker").unlock(25).cooldown(220).stamina(32).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 end = FruitKit.blink(u, dir, 12);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 2, 1.6))
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 11), FruitKit.info(Element.BLUNT, 2.5f).withStun(15).withPoise(70)))
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            Set<ShipEntity> rammed = new HashSet<>();
            for (int d = 0; d <= (int) start.distanceTo(end) + 3; d += 3)
                for (ShipEntity s : OriginalKit.ships(u, start.add(dir.scale(d)), 3)) if (!OriginalKit.friendly(u, s) && rammed.add(s)) OriginalKit.wreck(u, s, 80 + c.mastery);
            FruitKit.fx(u, "same_fin", start, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.WOOD_BREAK, 2f, 0.5f);
            return true;
        }));

        // Awakening — Abyss Jaws: a gigantic phantom maw bursts out of the ground (or the sea) and snaps shut on
        // everything above it, dragging it down.
        f.add(f.ability("abyss_jaws").unlock(55).cooldown(1000).stamina(85).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26);
            FruitKit.fx(u, "same_maw", at, 5, 0, 0);
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 5)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 5));
            CombatState.of(u).schedule(c.level.getGameTime() + 12, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 5.5)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 20), FruitKit.info(Element.SLASH, 0f).withStun(30).withPoise(100).asUnblockable());
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                    FruitKit.impulse(e, new Vec3(0, e.isInWater() ? -0.8 : -0.3, 0), false);
                }
                for (ShipEntity s : OriginalKit.ships(u, at, 5)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 120);
                FruitKit.fx(u, "same_maw", at, 5, 1, 0);
                PowerKit.sound(c, at, SoundEvents.EVOKER_FANGS_ATTACK, 2f, 0.3f);
            });
            return true;
        }));

        // Passive: an ancient predator. In form: 10% lifesteal and the frenzy bonus against the wounded; it smells
        // blood — badly wounded enemies nearby glow.
        f.outgoingHook = (u, v, amount, info) -> {
            float a = CombatState.of(u).hasBuff(FRENZY) && v.getHealth() < v.getMaxHealth() * 0.5f ? amount * 1.3f : amount;
            if (!Zoan.form(u).isEmpty()) u.heal(a * 0.1f);
            return a;
        };
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 40 != 0) return;
            for (LivingEntity e : PowerKit.foes(u, u.position(), 24))
                if (e.getHealth() < e.getMaxHealth() * 0.3f) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
        };
    }

    private MegalodonFruit() {}
}
