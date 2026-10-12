package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import com.grandline.net.GLNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hana Hana no Mi — sprout copies of body parts on any surface, including enemies. Arms that grapple and break holds
 * from a distance, eyes that watch every angle, wings of arms, and a giant hand rising from the ground.
 */
public final class HanaFruit {
    static final String EYES = "hana_eyes";

    public static void init(DevilFruit f) {
        Hits.Info hold = FruitKit.info(Element.BLUNT, 0.2f);

        // Clutch: arms sprout on the target's body and bend it backwards.
        f.add(f.ability("clutch").cooldown(40).stamina(14).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16 + c.mastery / 10.0);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 8), hold.withStun(25).withPoise(50).asUnblockable());
            FruitKit.fx(u, "sprout_arms", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
            return true;
        }));

        // Seis Fleur — Grab: arms pin a target (and anyone beside it) in place.
        f.add(f.ability("sprout_grapple").unlock(5).cooldown(90).stamina(18).anim("focus").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16 + c.mastery / 10.0);
            if (t == null) return false;
            for (LivingEntity e : PowerKit.foes(u, t.position().add(0, 1, 0), 2.2)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.BLUNT, 0f).withStun(60).withPoise(40)))
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5));
                FruitKit.fx(u, "sprout_arms", e.position(), e.getBbWidth(), e.getBbHeight(), 1);
            }
            return true;
        }));

        // Ojos Fleur: eyes sprout everywhere — enemies are revealed and attacks are seen coming.
        f.add(f.ability("ojos_fleur").unlock(10).cooldown(900).stamina(15).anim("focus").exec((u, c) -> {
            int dur = 20 * (25 + c.mastery / 10);
            CombatState.of(u).buffs.put(EYES, dur);
            for (LivingEntity e : PowerKit.foes(u, u.position(), 40)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
                FruitKit.fx(u, "flower_eyes", e.position().add(0, e.getBbHeight() + 0.3, 0), 0, 0, 0);
            }
            FruitKit.fx(u, "petals", u.position().add(0, 1, 0), 2, 0, 0);
            return true;
        }));

        // Cien Fleur — Wing: arms sprout from the back and flap the user into the air.
        f.add(f.ability("cien_fleur_wing").unlock(20).cooldown(80).stamina(16).anim("cast_up").exec((u, c) -> {
            Vec3 dir = c.look().multiply(1, 0, 1).normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 0.8, 1.0, dir.z * 0.8), false);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
            FruitKit.fx(u, "petals", u.position().add(0, 1.4, 0), 1.5f, 0, 0);
            return true;
        }));

        // Gigantesco Mano: a giant hand bursts from the ground and slams down.
        f.add(f.ability("gigantesco_mano").unlock(30).cooldown(220).stamina(40).anim("ground_slam").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            FruitKit.fx(u, "giant_hand", at, 4, 0, 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 10, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 4))
                    Hits.hit(u, e, FruitKit.dmg(c, 14), FruitKit.info(Element.BLUNT, 2f).withStun(30).withPoise(80));
                TerrainChanges.crater(c.level, BlockPos.containing(at), 2, u);
                FruitKit.fx(u, "petals", at.add(0, 1, 0), 4, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.1f);
            });
            return true;
        }));

        // Awakening — Mil Fleur: a thousand arms seize every enemy in sight at once.
        f.add(f.ability("mil_fleur").unlock(55).cooldown(1000).stamina(85).awakened().anim("focus").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position(), 16)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 12), hold.withStun(40).withPoise(100).asUnblockable()))
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
                FruitKit.fx(u, "sprout_arms", e.position(), e.getBbWidth(), e.getBbHeight(), 2);
            }
            FruitKit.fx(u, "petals", u.position().add(0, 1, 0), 16, 0, 0);
            return true;
        }));

        // Passive (with Ojos Fleur): sprouted eyes see attacks coming and the user slips some of them.
        f.incomingHook = (u, a, amount, info) -> {
            if (!CombatState.of(u).hasBuff(EYES) || info.unblockable() || u.getRandom().nextFloat() >= 0.3f) return amount;
            GLNetwork.fx(u, "observation_dodge", u.getX(), u.getY() + 1, u.getZ(), 0, 0, 0);
            GLNetwork.anim(u, "dodge_side");
            return 0f;
        };
    }

    private HanaFruit() {}
}
