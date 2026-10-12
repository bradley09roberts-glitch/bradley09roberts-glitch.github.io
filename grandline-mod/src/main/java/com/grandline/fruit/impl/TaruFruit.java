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
import net.minecraft.world.phys.Vec3;

/**
 * Taru Taru no Mi — ORIGINAL barrel Paramecia (wave 4). Barrels of every kind come out of the user's body — water
 * butts, powder kegs, rum casks — and the user can roll up inside one. A humble sailor's fruit, tough and a little
 * ridiculous: barrels to throw, a barrel to roll in, a keg that explodes, a barrel bunker, a cask of grog for the crew
 * and, at awakening, an avalanche of barrels.
 * Passive: iron-hooped — blunt blows -15%.
 * Weakness: the staves are wood — fire deals 20% more.
 */
public final class TaruFruit {
    static final String BUNKER = "taru_bunker";

    public static void init(DevilFruit f) {
        Hits.Info barrel = FruitKit.info(Element.BLUNT, 1.4f);

        // Barrel toss: a heavy water butt hurled at the target.
        f.add(f.ability("barrel_toss").cooldown(20).stamina(9).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "taru_barrel", 0.8f, 1.5, FruitKit.dmg(c, 6), barrel.withPoise(30));
            p.gravity = 0.02;
            p.life = 40;
            PowerKit.sound(c, u.position(), SoundEvents.WOOD_PLACE, 1.2f, 0.8f);
            return true;
        }));

        // Barrel roll: the user curls up inside a barrel and rolls forward, bowling over everyone in the way.
        f.add(f.ability("barrel_roll").unlock(5).cooldown(80).stamina(14).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 end = FruitKit.blink(u, dir, 10);
            for (LivingEntity e : Hits.line(u, start.add(0, 0.6, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.2)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 6), barrel.withPoise(35))) FruitKit.impulse(e, new Vec3(dir.x * 0.4, 0.6, dir.z * 0.4), false);
            }
            FruitKit.fx(u, "taru_roll", start, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.WOOD_BREAK, 1.2f, 0.6f);
            return true;
        }));

        // Powder keg: a keg of black powder with a lit fuse — it explodes where it lands (no harm to the ground).
        f.add(f.ability("powder_keg").unlock(12).cooldown(120).stamina(18).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "taru_keg", 0.6f, 1.2, FruitKit.dmg(c, 9), FruitKit.info(Element.IMPACT, 1.6f).withPoise(45));
            p.gravity = 0.035;
            p.life = 50;
            p.explosion = 2.8f;
            // a keg tossed at point-blank range goes off in the user's hands — and the user is the one built of barrels
            for (LivingEntity e : Hits.cone(u, 2.5, 80)) if (Hits.hostileTo(u, e)) Hits.hit(u, e, FruitKit.dmg(c, 4), barrel.withPoise(20));
            PowerKit.sound(c, u.position(), SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
            return true;
        }));

        // Barrel bunker: the user shuts themselves into an iron-hooped barrel for four seconds — 70% of all damage is
        // soaked and blows thud harmlessly off, shoving attackers back.
        f.add(f.ability("barrel_bunker").unlock(20).cooldown(260).stamina(16).anim("harden").exec((u, c) -> {
            int dur = 80 + c.mastery / 2;
            CombatState.of(u).buffs.put(BUNKER, dur);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 2, false, false));
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), barrel.withPoise(20))) PowerKit.push(e, u.position(), 0.8, 0.2);
            }
            FruitKit.fx(u, "taru_bunker", u.position(), u.getBbWidth() + 0.4f, u.getBbHeight(), 0);
            PowerKit.sound(c, u.position(), SoundEvents.BARREL_CLOSE, 1.5f, 0.6f);
            return true;
        }));

        // Grog ration: a cask of grog for the whole crew — heals, feeds and puts fight in their bellies.
        f.add(f.ability("grog_ration").unlock(30).cooldown(600).stamina(16).anim("focus").exec((u, c) -> {
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 6, true)) {
                OriginalKit.heal(a, 6 + c.mastery / 15f, "taru_grog");
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0));
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                if (a instanceof ServerPlayer sp) sp.getFoodData().eat(5, 0.6f);
            }
            // the cask is cracked over the head of whoever is closest
            LivingEntity t = PowerKit.target(c, 3.5);
            if (t != null) Hits.hit(u, t, FruitKit.dmg(c, 5), barrel.withStun(10).withPoise(30));
            PowerKit.sound(c, u.position(), SoundEvents.BARREL_OPEN, 1.5f, 0.8f);
            return true;
        }));

        // Awakening — Barrel Avalanche: dozens of barrels tumble out of the sky onto an area for three seconds; every
        // fourth one is a powder keg.
        f.add(f.ability("barrel_avalanche").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 22);
            double r = 7;
            OriginalKit.pulses(c, 0, 5, 12, n -> {
                boolean keg = n % 4 == 3;
                FruitKit.fx(u, "taru_fall", at, (float) r, keg ? 1 : 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    if (keg) {
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.IMPACT, 1.5f).withPoise(40))) PowerKit.push(e, at, 0.6, 0.5);
                    } else if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 4), barrel.withStun(8).withPoise(25));
                }
                if (keg) PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1f);
                else if (n % 2 == 0) PowerKit.sound(c, at, SoundEvents.WOOD_BREAK, 1.5f, 0.6f);
            });
            return true;
        }));

        // Passive: iron hoops (blunt -15%); in the barrel bunker 70% of everything is soaked and attackers are shoved
        // off. The staves are wood: fire +20%.
        f.incomingHook = (u, a, amount, info) -> {
            float m = 1f;
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) m *= 1.2f;
            if (info.element() == Element.BLUNT || info.element() == Element.PHYSICAL) m *= 0.85f;
            if (CombatState.of(u).hasBuff(BUNKER)) {
                m *= 0.3f;
                if (a != null && a.distanceTo(u) < 4) PowerKit.push(a, u.position(), 0.6, 0.15);
            }
            return amount * m;
        };
    }

    private TaruFruit() {}
}
