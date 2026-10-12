package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.haki.Haki;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Soru Soru no Mi — souls. "Life or death?": whoever fears the user loses years of their lifespan, which the user
 * keeps. Pieces of soul put into objects make Homies: the thundercloud Zeus, the sun Prometheus, the bicorne-sword
 * Napoleon, and the very land around enemies, which comes alive to seize them.
 */
public final class SoruFruit {
    /** Lifespan taken (in health points), slowly feeding the user. */
    static final Map<LivingEntity, Float> LIFESPAN = Collections.synchronizedMap(new WeakHashMap<>());

    /** Take lifespan from one victim. Those with the will to refuse fear (equal or stronger willpower) resist. */
    static boolean takeLifespan(LivingEntity u, AbilityContext c, LivingEntity e, float fraction) {
        boolean fearless = Haki.willpower(e) >= Haki.willpower(u) * 1.2f + 10;
        if (fearless) {
            Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.PHYSICAL, 0.3f).withPoise(10));
            CanonKit.tell(e, "grandline.soru.resisted");
            FruitKit.fx(u, "soul_resist", e.position().add(0, 1, 0), 0, 0, 0);
            return false;
        }
        float years = Math.max(3, Math.min(40, e.getMaxHealth() * fraction)) * (c.awakened ? 1.2f : 1f);
        if (!PowerKit.hit(u, e, years, FruitKit.info(Element.PHYSICAL, 0f).withPoise(30).asUnblockable())) return false;
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        LIFESPAN.merge(u, years, (a, b) -> Math.min(200f, a + b));
        FruitKit.fx(u, "soul_pull", e.position().add(0, 1, 0), (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
        CanonKit.tell(e, "grandline.soru.lifespan");
        return true;
    }

    static void thunder(ServerLevel l, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        l.addFreshEntity(bolt);
    }

    public static void init(DevilFruit f) {
        // Soul Pocus: "Life or death?" — everyone in front who fears the user gives up years of their life.
        f.add(f.ability("soul_pocus").cooldown(140).stamina(22).anim("roar").exec((u, c) -> {
            int n = 0;
            for (LivingEntity e : Hits.cone(u, 9, 120)) if (Hits.hostileTo(u, e) && takeLifespan(u, c, e, 0.15f)) n++;
            FruitKit.fx(u, "soul_pocus", u.position().add(0, 1, 0), 9, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.SOUL_ESCAPE.value(), 1.5f, 0.6f);
            return true;
        }));

        // Zeus: the thundercloud Homie strikes the target area with lightning, three times.
        f.add(f.ability("zeus").unlock(8).cooldown(90).stamina(22).anim("cast_up").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 24);
            Vec3 first = t != null ? t.position() : FruitKit.aimPoint(c, 24);
            FruitKit.fx(u, "zeus_cloud", first.add(0, 7, 0), 3, 0, 0);
            for (int i = 0; i < 3; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + 6 + i * 8L, () -> {
                    Vec3 at = t != null && t.isAlive() ? t.position() : first;
                    thunder(c.level, at);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 2.6))
                        Hits.hit(u, e, FruitKit.dmg(c, 7), FruitKit.info(Element.LIGHTNING, 0.6f).withStun(10).withPoise(35));
                });
            }
            return true;
        }));

        // Prometheus: the sun Homie hurls itself as a blazing fireball.
        f.add(f.ability("prometheus").unlock(15).cooldown(110).stamina(26).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "prometheus_sun", 1.5f, 1.3, FruitKit.dmg(c, 12), FruitKit.info(Element.FIRE, 1.6f).withPoise(50));
            p.explosion = 3f;
            p.life = 50;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 3)) e.igniteForSeconds(5);
            };
            PowerKit.sound(c, u.position(), SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
            return true;
        }));

        // Napoleon: the bicorne Homie becomes a giant sword — "Ikoku": a thrust whose shockwave carves a line.
        f.add(f.ability("napoleon").unlock(25).cooldown(160).stamina(30).anim("heavy_thrust").exec((u, c) -> {
            Vec3 from = c.eye();
            Vec3 dir = FruitKit.aimDir(c);
            Vec3 end = PowerKit.beam(c, from, dir, 14, 1.6, FruitKit.dmg(c, 14), FruitKit.info(Element.SLASH, 2.5f).withPoise(80).asUnblockable());
            FruitKit.fx(u, "ikoku", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, u.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.4f);
            return true;
        }));

        // Homies: the user breathes soul into the land around her enemies — grass, rocks and trees seize and beat them.
        f.add(f.ability("living_land").unlock(35).cooldown(260).stamina(34).anim("cast_ground").exec((u, c) -> {
            int n = 0;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 11)) {
                CanonKit.disable(e, 30, 4);
                for (int k = 0; k < 3; k++) {
                    CombatState.of(u).schedule(c.level.getGameTime() + 4 + k * 8L, () -> {
                        if (!e.isAlive()) return;
                        Hits.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.BLUNT, 0.6f).withPoise(20));
                        FruitKit.fx(u, "homie_land", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
                    });
                }
                if (++n >= 8) break;
            }
            FruitKit.fx(u, "homie_land", u.position(), 2, 0, 1);
            return true;
        }));

        // Awakening — Soul Queen: every soul in the area is asked "life or death?" at once while Zeus and Prometheus rage.
        f.add(f.ability("soul_queen").unlock(55).cooldown(1100).stamina(90).awakened().anim("roar").exec((u, c) -> {
            Vec3 center = u.position();
            FruitKit.fx(u, "soul_pocus", center.add(0, 1, 0), 14, 1, 0);
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 14)) takeLifespan(u, c, e, 0.2f);
            for (int i = 0; i < 4; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + 10 + i * 8L, () -> {
                    var foes = PowerKit.foes(u, center.add(0, 1, 0), 14);
                    if (foes.isEmpty()) return;
                    LivingEntity e = foes.get(u.getRandom().nextInt(foes.size()));
                    thunder(c.level, e.position());
                    for (LivingEntity v : PowerKit.foes(u, e.position().add(0, 1, 0), 3)) // Zeus strikes, Prometheus burns
                        if (PowerKit.hit(u, v, FruitKit.dmg(c, 6), FruitKit.info(Element.LIGHTNING, 0.4f).withPoise(20))) v.igniteForSeconds(2);
                });
            }
            PowerKit.sound(c, center, SoundEvents.SOUL_ESCAPE.value(), 2f, 0.4f);
            return true;
        }));

        // Passive: stolen lifespan feeds the user — 1 health per second while any remains.
        f.tickHook = u -> {
            if (u.tickCount % 20 != 0 || u.getHealth() >= u.getMaxHealth()) return;
            Float left = LIFESPAN.get(u);
            if (left == null || left <= 0) return;
            float h = Math.min(1f, left);
            u.heal(h);
            LIFESPAN.put(u, left - h * 2);
        };
    }

    private SoruFruit() {}
}
