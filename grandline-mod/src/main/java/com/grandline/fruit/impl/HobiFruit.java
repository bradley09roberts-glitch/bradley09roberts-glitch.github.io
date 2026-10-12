package com.grandline.fruit.impl;

import com.grandline.GrandLine;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hobi Hobi no Mi — a touch turns people into toys. A toy shrinks, cannot fight and is forgotten: whoever was fighting
 * it loses track of it. Toys are bound by contract to obey the user.
 */
public final class HobiFruit {
    static final ResourceLocation TOY_SIZE = GrandLine.id("hobi_toy");
    /** Toy -> the user who made it. */
    static final Map<LivingEntity, LivingEntity> OWNER = Collections.synchronizedMap(new WeakHashMap<>());
    static final Map<LivingEntity, Long> UNTIL = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean isToy(LivingEntity e) {
        return OWNER.containsKey(e);
    }

    /** Turn {@code e} into a toy for {@code ticks}: shrunk, unable to fight, forgotten by everyone hunting it. */
    public static void toyify(LivingEntity owner, LivingEntity e, int ticks) {
        long until = e.level().getGameTime() + ticks;
        OWNER.put(e, owner);
        UNTIL.merge(e, until, Math::max);
        CanonKit.scale(e, TOY_SIZE, -0.6);
        CanonKit.disable(e, ticks, 2);
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2));
        CanonKit.forget(e, 32);
        if (e instanceof ServerPlayer sp) CanonKit.tell(sp, "grandline.hobi.toy");
        FruitKit.fx(owner, "toy_poof", e.position().add(0, 0.5, 0), 1, 0, 0);
        // everyone keeps forgetting the toy while the curse lasts
        for (int k = 10; k < ticks; k += 10) {
            CombatState.of(e).schedule(e.level().getGameTime() + k, () -> {
                if (!isToy(e)) return;
                for (Mob m : e.level().getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(32), m -> m.getTarget() == e)) m.setTarget(null);
            });
        }
        CanonKit.later(owner, e, ticks, () -> untoy(e, false));
    }

    static void untoy(LivingEntity e, boolean force) {
        Long until = UNTIL.get(e);
        if (!force && until != null && e.level().getGameTime() < until - 1) return; // refreshed since
        if (OWNER.remove(e) == null) return;
        UNTIL.remove(e);
        CanonKit.scale(e, TOY_SIZE, 0);
        if (e.isAlive()) com.grandline.net.GLNetwork.fx(e, "toy_poof", e.getX(), e.getY() + 1, e.getZ(), 2, 0, 0);
    }

    public static void init(DevilFruit f) {
        Hits.Info toyHit = FruitKit.info(Element.BLUNT, 0.4f);

        // Hobby Touch: a single touch and the target is a toy.
        f.add(f.ability("hobby_touch").cooldown(160).stamina(20).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 4.5);
            if (t == null) return false;
            if (!PowerKit.hit(u, t, FruitKit.dmg(c, 4), toyHit.withPoise(30))) return true; // dodged or blocked: the touch missed
            toyify(u, t, t instanceof Player ? 40 + c.mastery / 3 : 50 + c.mastery / 2);
            PowerKit.sound(c, t.position(), SoundEvents.CHICKEN_EGG, 1.4f, 0.6f);
            return true;
        }));

        // Toy Soldiers: tin soldiers march out firing tiny cannons, and every toy bound by contract turns on the enemy.
        f.add(f.ability("toy_soldiers").unlock(8).cooldown(90).stamina(18).anim("cast_forward").exec((u, c) -> {
            LivingEntity foe = PowerKit.target(c, 24);
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = 0; i < 4; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 3L, () -> {
                    if (!u.isAlive()) return;
                    Vec3 d = PowerKit.yaw(dir, (k - 1.5) * 6);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "toy_soldier", 0.35f, u.position().add(0, 0.6, 0).add(d.scale(0.8)), d, 1.6,
                            FruitKit.dmg(c, 3.5f), FruitKit.info(Element.IMPACT, 0.5f).withPoise(12));
                    p.explosion = 1f;
                    p.life = 30;
                });
            }
            if (foe != null) {
                for (LivingEntity toy : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(u.blockPosition()).inflate(24), e -> OWNER.get(e) == u && e != foe)) {
                    if (toy instanceof Mob m) {
                        CanonKit.enable(toy);
                        toy.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        m.setTarget(foe);
                        FruitKit.fx(u, "toy_poof", toy.position().add(0, 0.5, 0), 0.5f, 0, 0);
                    }
                }
            }
            return true;
        }));

        // Toy Box: a box of wind-up toys is hurled and bursts, scattering springs and confetti.
        f.add(f.ability("toy_box").unlock(15).cooldown(80).stamina(20).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "toy_box", 0.9f, 1.4, FruitKit.dmg(c, 6), FruitKit.info(Element.IMPACT, 1.4f).withPoise(30));
            p.gravity = 0.03;
            p.explosion = 2.5f;
            p.life = 50;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 3)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                FruitKit.fx(u, "confetti", pos, 3, 0, 0);
            };
            return true;
        }));

        // Toy Parade: everyone in front who is already worn down becomes a toy; the rest are battered by marching toys.
        f.add(f.ability("toy_parade").unlock(25).cooldown(240).stamina(30).anim("cast_forward").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 8, 120)) {
                if (!Hits.hostileTo(u, e)) continue;
                if (CanonKit.hp(e) < 0.4f) toyify(u, e, e instanceof Player ? 50 : 70 + c.mastery);
                else if (PowerKit.hit(u, e, FruitKit.dmg(c, 6), toyHit.withPoise(30))) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            FruitKit.fx(u, "confetti", u.position().add(c.look().scale(4)).add(0, 1, 0), 4, 0, 0);
            return true;
        }));

        // Awakening — Toy Kingdom: every enemy in a wide area becomes a toy at once, and the world forgets them.
        f.add(f.ability("toy_kingdom").unlock(50).cooldown(1100).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 center = u.position().add(0, 1, 0);
            for (LivingEntity e : PowerKit.foes(u, center, 12)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 5), toyHit.withPoise(30).asUnblockable()))
                    toyify(u, e, e instanceof Player ? 40 : 50 + c.mastery / 2);
            }
            FruitKit.fx(u, "confetti", center, 12, 1, 0);
            PowerKit.sound(c, center, SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f);
            return true;
        }));

        // Passive: worn-down creatures (not players) that the user strikes below a fifth of their health become toys.
        f.outgoingHook = (u, v, amount, info) -> {
            float left = v.getHealth() - amount;
            if (!(v instanceof Player) && !isToy(v) && left > 0 && left <= v.getMaxHealth() * 0.2f) toyify(u, v, 60);
            return amount;
        };
    }

    private HobiFruit() {}
}
