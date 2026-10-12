package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.ship.ShipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Kabi Kabi no Mi — ORIGINAL mold Logia. The user is a living colony of mold and spores: spore puffs and clouds, a
 * mycelium carpet that roots enemies and feeds allies, rot that eats into flesh and into the timber of ships, and
 * puffball sentries that keep spraying spores on their own.
 * Weakness: fire burns spores away — fire always connects and deals 50% more. Rain and darkness let the colony
 * thrive (slow regeneration). Immune to poison.
 */
public final class KabiFruit {
    static final BlockState MYCELIUM = Blocks.MYCELIUM.defaultBlockState();
    static final BlockState PUFFBALL = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();

    static void infect(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amp));
        e.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, amp));
    }

    public static void init(DevilFruit f) {
        Hits.Info spore = FruitKit.info(Element.POISON, 0.2f);

        // Spore shot: a puff of spores that bursts on whoever it hits.
        f.add(f.ability("spore_shot").cooldown(18).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "kabi_spore", 0.5f, 1.8, FruitKit.dmg(c, 4), spore.withPoise(12));
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 2.2)) infect(e, 80, 0);
            };
            return true;
        }));

        // Spore cloud: a cloud of choking spores hangs at a point — poison, nausea, weakness, and enemies lose track.
        f.add(f.ability("spore_cloud").unlock(5).cooldown(150).stamina(20).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18).add(0, 1, 0);
            double r = 4.5;
            OriginalKit.pulses(c, 0, 10, 10, n -> {
                FruitKit.fx(u, "kabi_cloud", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at, r)) {
                    infect(e, 40, 0);
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
                    PowerKit.loseTrack(u, e);
                    if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), spore.withPoise(5));
                }
            });
            PowerKit.sound(c, at, SoundEvents.SPORE_BLOSSOM_PLACE, 1.5f, 0.6f);
            return true;
        }));

        // Mycelium carpet: mold spreads across the ground; enemies on it are rooted and sickened, allies on it are fed.
        f.add(f.ability("mycelium").unlock(15).cooldown(300).stamina(26).anim("cast_ground").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 7 + c.mastery / 25.0;
            PowerKit.paintDisc(c.level, at, r, MYCELIUM, null);
            OriginalKit.pulses(c, 0, 10, 20, n -> {
                if (n % 4 == 0) FruitKit.fx(u, "kabi_carpet", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), r)) {
                    if (Math.abs(e.getY() - at.y) > 2.5) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
                    if (n % 3 == 0) {
                        infect(e, 40, 0);
                        Hits.hit(u, e, FruitKit.dmg(c, 1.5f), spore.withPoise(5));
                    }
                }
                if (n % 4 == 0) for (LivingEntity a : OriginalKit.allies(u, at.add(0, 0.5, 0), r, true)) OriginalKit.heal(a, 1f, "");
            });
            PowerKit.sound(c, at, SoundEvents.FUNGUS_BREAK, 1.5f, 0.5f);
            return true;
        }));

        // Rot: mold eats into a target (wither and weakness); a ship hit by rot has its timber eaten for a while.
        f.add(f.ability("rot").unlock(20).cooldown(200).stamina(26).anim("palm_strike").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            LivingEntity t = PowerKit.target(c, 18);
            ShipEntity ship = OriginalKit.enemyShip(u, at, 6);
            if (t == null && ship == null) return false;
            if (t != null) {
                Hits.hit(u, t, FruitKit.dmg(c, 6), spore.withPoise(30));
                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                FruitKit.fx(u, "kabi_rot", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            }
            if (ship != null) {
                float per = 6 + c.mastery / 20f;
                OriginalKit.pulses(c, 0, 20, 10, n -> {
                    if (!ship.isAlive() || ship.isSinking()) return;
                    OriginalKit.wreck(u, ship, per);
                    FruitKit.fx(u, "kabi_rot", ship.position().add(0, 1, 0), ship.cls.beam, 2, 1);
                });
            }
            PowerKit.sound(c, at, SoundEvents.FUNGUS_BREAK, 1.2f, 0.4f);
            return true;
        }));

        // Puffball sentries: three puffballs sprout around the user and keep spraying spores at enemies on their own.
        f.add(f.ability("puffball_sentries").unlock(30).cooldown(400).stamina(34).anim("cast_ground").exec((u, c) -> {
            List<Vec3> spots = new ArrayList<>();
            Vec3 base = u.position();
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3 + u.getYRot() * Math.PI / 180;
                Vec3 p = base.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(base.y), (int) Math.floor(p.z));
                if (g != null) OriginalKit.tempFor(c, g.above(), PUFFBALL, 240);
                spots.add(g == null ? p : Vec3.atBottomCenterOf(g.above()));
            }
            OriginalKit.pulses(c, 5, 20, 12, n -> {
                for (Vec3 s : spots) {
                    LivingEntity t = PowerKit.foes(u, s.add(0, 0.5, 0), 6).stream().findFirst().orElse(null);
                    FruitKit.fx(u, "kabi_puffball", s.add(0, 0.6, 0), t == null ? 0 : 1, 0, 0);
                    if (t == null) continue;
                    Hits.hit(u, t, FruitKit.dmg(c, 3), spore.withPoise(8));
                    infect(t, 60, 0);
                }
            });
            PowerKit.sound(c, base, SoundEvents.PUFFER_FISH_BLOW_UP, 1f, 0.5f);
            return true;
        }));

        // Awakening — Blight: the colony blooms over the whole area — the ground turns to mycelium, every enemy rots,
        // and the timber of enemy ships nearby is eaten through.
        f.add(f.ability("blight").unlock(55).cooldown(1100).stamina(85).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 12;
            PowerKit.paintDisc(c.level, at, r, MYCELIUM, null);
            OriginalKit.pulses(c, 0, 10, 12, n -> {
                FruitKit.fx(u, "kabi_cloud", at.add(0, 1, 0), (float) r, 1, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 3), spore.withPoise(10));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 1));
                    infect(e, 40, 1);
                }
                if (n % 3 == 0) for (ShipEntity s : OriginalKit.ships(u, at, r + 6)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 15 + c.mastery / 10f);
            });
            PowerKit.sound(c, at, SoundEvents.SPORE_BLOSSOM_PLACE, 2f, 0.4f);
            return true;
        }));

        // Passive: mold body (Logia). Fire burns it (+50%, always connects); attackers who touch it are infected;
        // rain and darkness feed the colony; poison never harms it.
        f.intangibleHook = (u, a, info) -> {
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return false;
            if (a != null && a.distanceTo(u) < 3.5) infect(a, 40, 0);
            return true;
        };
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.FIRE || info.element() == Element.MAGMA ? amount * 1.5f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 20 != 0) return;
            u.removeEffect(MobEffects.POISON);
            if (u.tickCount % 60 == 0 && (u.isInWaterOrRain() || u.level().getMaxLocalRawBrightness(u.blockPosition()) < 7) && u.getHealth() < u.getMaxHealth()) u.heal(1f);
        };
    }

    private KabiFruit() {}
}
