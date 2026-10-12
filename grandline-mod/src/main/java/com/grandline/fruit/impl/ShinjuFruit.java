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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shinju Shinju no Mi — ORIGINAL pearl Paramecia (wave 4). Like an oyster of Fish-Man Island's coral beds, the user
 * secretes nacre: pearls fired like shot, layer on layer of pearl armour (each layer breaks one blow), irritants coated
 * shut in pearl, a lustre that bursts the armour into blinding light, and at awakening a black pearl that swallows the
 * strongest foe.
 * Passive: the shell keeps growing — one nacre layer regrows every ten seconds (up to two on its own).
 */
public final class ShinjuFruit {
    static final Map<LivingEntity, Integer> LAYERS = Collections.synchronizedMap(new WeakHashMap<>());
    static final int MAX_LAYERS = 6;
    static final BlockState NACRE = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    static final BlockState BLACK_PEARL = Blocks.BLACKSTONE.defaultBlockState();

    public static int layers(LivingEntity e) {
        return LAYERS.getOrDefault(e, 0);
    }

    static void addLayers(LivingEntity e, int n, int cap) {
        LAYERS.put(e, Math.max(layers(e), Math.min(cap, layers(e) + n)));
    }

    public static void init(DevilFruit f) {
        Hits.Info pearl = FruitKit.info(Element.BLUNT, 0.6f);

        // Pearl shot: a hard, gleaming pearl fired like a musket ball.
        f.add(f.ability("pearl_shot").cooldown(14).stamina(7).anim("finger_pistol").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "shinju_pearl", 0.35f, 2.6, FruitKit.dmg(c, 5), pearl.withPoise(20));
            p.life = 30;
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.6f);
            return true;
        }));

        // Nacre armour: three layers of pearl grow over the body (up to six); each one breaks a single blow, halving it.
        f.add(f.ability("nacre_armor").unlock(5).cooldown(240).stamina(16).anim("harden").exec((u, c) -> {
            addLayers(u, 3, MAX_LAYERS);
            // the hardening crust grinds anyone pressed against the user
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), pearl.withPoise(15));
            FruitKit.fx(u, "shinju_layer", u.position().add(0, 1, 0), u.getBbWidth() + 0.4f, layers(u), 0);
            PowerKit.sound(c, u.position(), SoundEvents.CALCITE_PLACE, 1.5f, 1.2f);
            return true;
        }));

        // Pearl prison: like grit in an oyster, the target is coated layer on layer until it is shut in pearl.
        f.add(f.ability("pearl_prison").unlock(12).cooldown(240).stamina(24).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 7), pearl.withStun(50).withPoise(70).asUnblockable());
            OriginalKit.encaseFor(c, t, NACRE, 80);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 5));
            FruitKit.fx(u, "shinju_prison", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.CALCITE_PLACE, 1.5f, 0.6f);
            return true;
        }));

        // Pearl barrage: a fan of five pearls.
        f.add(f.ability("pearl_barrage").unlock(20).cooldown(80).stamina(18).anim("rapid_fire").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -2; i <= 2; i++) {
                Vec3 d = PowerKit.yaw(dir, i * 9);
                AbilityProjectile p = AbilityProjectile.shoot(u, "shinju_pearl", 0.35f, c.eye().add(d.scale(0.6)).add(0, -0.15, 0), d, 2.4, FruitKit.dmg(c, 4), pearl.withPoise(15));
                p.life = 30;
            }
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.2f);
            return true;
        }));

        // Lustre: every layer of nacre bursts into iridescent light at once — enemies around are dazzled and struck,
        // harder for every layer spent.
        f.add(f.ability("lustre").unlock(30).cooldown(200).stamina(24).anim("double_palm").exec((u, c) -> {
            int n = layers(u);
            LAYERS.remove(u);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 6)) {
                Hits.hit(u, e, FruitKit.dmg(c, 4 + n * 3), FruitKit.info(Element.LIGHT, 0.8f).withStun(10).withPoise(30 + n * 8));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                PowerKit.loseTrack(u, e);
            }
            FruitKit.fx(u, "shinju_lustre", u.position().add(0, 1, 0), 6, n, 0);
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 2f, 1.2f);
            return true;
        }));

        // Awakening — Black Pearl: the strongest foe nearby is swallowed by a giant black pearl for four seconds and
        // crushed in it; when it cracks, the user wears its nacre (six layers).
        f.add(f.ability("black_pearl").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            for (LivingEntity e : PowerKit.foes(u, u.position(), 16)) if (t == null || e.getMaxHealth() > t.getMaxHealth()) t = e;
            if (t == null) return false;
            LivingEntity v = t;
            OriginalKit.encaseFor(c, v, BLACK_PEARL, 80);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 6));
            v.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
            OriginalKit.pulses(c, 0, 20, 4, n -> {
                if (!v.isAlive()) return;
                Hits.hit(u, v, FruitKit.dmg(c, n == 3 ? 14 : 5), pearl.withStun(25).withPoise(60).asUnblockable());
                FruitKit.fx(u, "shinju_black", v.position(), v.getBbWidth(), v.getBbHeight(), n == 3 ? 1 : 0);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 62, () -> addLayers(u, MAX_LAYERS, MAX_LAYERS));
            PowerKit.sound(c, v.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1.5f);
            return true;
        }));

        // Passive: each nacre layer breaks one blow (halving it); the shell regrows a layer every ten seconds (max 2).
        f.incomingHook = (u, a, amount, info) -> {
            int n = layers(u);
            if (n <= 0 || amount <= 0) return amount;
            LAYERS.put(u, n - 1);
            com.grandline.net.GLNetwork.fx(u, "shinju_crack", u.getX(), u.getY() + 1, u.getZ(), u.getBbWidth(), n - 1, 0);
            return amount * 0.5f;
        };
        f.tickHook = u -> {
            if (u.tickCount % 200 == 0 && layers(u) < 2) addLayers(u, 1, 2);
        };
    }

    private ShinjuFruit() {}
}
