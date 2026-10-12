package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.BlockPos;
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
 * Kai Kai no Mi — ORIGINAL shell Special Paramecia (wave 4). The user's body grows the shells of the White Sea — Dials
 * — and works like every one of them: Breath Dials of stored wind, Flame Dials, Flash Dials, a Jet Dial, a Milky Dial
 * that lays roads of cloud, and the Impact Dial that drinks the force of blows and gives it back. At awakening, the
 * forbidden Reject Dial — ten times the impact, and a recoil that hurts the user too.
 * Passive: a shell body — a tenth of every melee blow taken is soaked into the Impact store (released by Impact or
 * Reject). In the White Sea the shells recharge quickly.
 */
public final class KaiFruit {
    static final String ABSORB = "kai_absorb";
    static final BlockState CLOUD = Blocks.WHITE_WOOL.defaultBlockState();
    /** Impact stored in the user's shells (damage absorbed). */
    static final Map<LivingEntity, Float> STORED = Collections.synchronizedMap(new WeakHashMap<>());
    static final float MAX_STORE = 40;

    static float stored(LivingEntity u) {
        return STORED.getOrDefault(u, 0f);
    }

    static void store(LivingEntity u, float amount) {
        STORED.put(u, Math.min(MAX_STORE, stored(u) + amount));
    }

    static float drain(LivingEntity u) {
        Float v = STORED.remove(u);
        return v == null ? 0 : v;
    }

    public static void init(DevilFruit f) {
        Hits.Info impact = FruitKit.info(Element.IMPACT, 1.4f);

        // Breath dial: a gust of stored wind bursts from the palm — enemies in front are blown away.
        f.add(f.ability("breath_dial").cooldown(30).stamina(10).anim("palm_strike").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 7, 70)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.WIND, 0.5f).withPoise(20)))
                    PowerKit.push(e, u.position(), 1.2, 0.35);
            }
            FruitKit.fx(u, "kai_breath", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.PHANTOM_FLAP, 1.5f, 0.6f);
            return true;
        }));

        // Flame dial: the stored heat of the sun, fired as a burst of flame.
        f.add(f.ability("flame_dial").unlock(5).cooldown(40).stamina(12).anim("palm_strike").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "kai_flame", 0.55f, 1.9, FruitKit.dmg(c, 7), FruitKit.info(Element.FIRE, 0.5f).withPoise(20));
            p.life = 30;
            PowerKit.sound(c, u.position(), SoundEvents.BLAZE_SHOOT, 1f, 1.2f);
            return true;
        }));

        // Flash dial: a palm full of stored light — a blinding flash; enemies in front reel and lose track.
        f.add(f.ability("flash_dial").unlock(10).cooldown(100).stamina(14).anim("double_palm").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 8, 90)) {
                if (!Hits.hostileTo(u, e)) continue;
                Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.LIGHT, 0.2f).withStun(12).withPoise(25));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0));
                PowerKit.loseTrack(u, e);
            }
            FruitKit.fx(u, "kai_flash", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.5f, 1.5f);
            return true;
        }));

        // Impact dial: the palm drinks in every blow for two and a half seconds (most of the damage is soaked into the
        // shell), then the stored impact bursts out at whatever stands in front.
        f.add(f.ability("impact_dial").unlock(15).cooldown(160).stamina(16).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put(ABSORB, 50);
            FruitKit.fx(u, "kai_shell", u.position().add(0, 1, 0), u.getBbWidth() + 0.5f, 0, 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 50, () -> {
                float s = drain(u);
                Vec3 front = u.position().add(0, 1, 0).add(u.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
                for (LivingEntity e : PowerKit.foes(u, front, 3)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 6) + s * 1.5f, impact.withStun(15).withPoise(60).asUnblockable()))
                        PowerKit.push(e, u.position(), 1.0 + s / 30.0, 0.3);
                }
                FruitKit.fx(u, "kai_impact", front, 2 + s / 10f, 0, 0);
                PowerKit.sound(c, front, SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
            });
            PowerKit.sound(c, u.position(), SoundEvents.SHIELD_BLOCK, 1f, 1.4f);
            return true;
        }));

        // Jet dial: the rarest of the shells — the user shoots forward like a Waver at full jet, ramming through anyone
        // in the way.
        f.add(f.ability("jet_dial").unlock(20).cooldown(120).stamina(20).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = c.look().y > 0.4 ? c.look() : PowerKit.flatAim(c);
            Vec3 end = FruitKit.blink(u, dir, 13);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.2))
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 8), impact.withPoise(40))) PowerKit.push(e, start, 0.8, 0.4);
            FruitKit.fx(u, "kai_jet", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.FIRECHARGE_USE, 1.2f, 1.6f);
            return true;
        }));

        // Milky dial: a road of cloud unrolls from the user's feet along the look — across the sea, over a gap, up to a
        // ledge (temporary). Enemies on its path are swept aside.
        f.add(f.ability("milky_dial").unlock(30).cooldown(260).stamina(22).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            double rise = Math.max(-0.5, Math.min(0.6, c.look().y));
            BlockPos base = u.blockPosition().below();
            for (int i = 1; i <= 16; i++) {
                double y = base.getY() + Math.round(i * rise);
                Vec3 p = new Vec3(base.getX() + 0.5 + dir.x * i, y, base.getZ() + 0.5 + dir.z * i);
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                for (int w = -1; w <= 1; w++) {
                    BlockPos b = BlockPos.containing(p.add(side.scale(w)));
                    OriginalKit.tempFor(c, b, CLOUD, 400);
                }
            }
            for (LivingEntity e : Hits.line(u, u.position().add(0, 0.5, 0), dir, 16, 1.5))
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.WIND, 0.4f).withPoise(15)))
                    PowerKit.push(e, e.position().subtract(new Vec3(-dir.z, 0, dir.x)), 0.6, 0.2);
            FruitKit.fx(u, "kai_milky", u.position(), (float) dir.x * 16, (float) rise * 16, (float) dir.z * 16);
            PowerKit.sound(c, u.position(), SoundEvents.WOOL_PLACE, 1.5f, 0.6f);
            return true;
        }));

        // Awakening — Reject Dial: ten times the stored impact, driven point-blank into one target past any guard. The
        // recoil wracks the user's own body (a sixth of their current health).
        f.add(f.ability("reject_dial").unlock(55).cooldown(900).stamina(70).awakened().anim("heavy_punch").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.isAlive() && c.target.distanceTo(u) < 4.5 ? c.target
                    : Hits.cone(u, 4, 70).stream().filter(e -> Hits.hostileTo(u, e)).findFirst().orElse(null);
            if (t == null) return false;
            float s = drain(u);
            Hits.hit(u, t, FruitKit.dmg(c, 26) + s * 3f, impact.withStun(40).withPoise(120).asUnblockable());
            PowerKit.push(t, u.position(), 2.2, 0.5);
            u.hurt(u.damageSources().magic(), Math.max(1, u.getHealth() / 6f));
            FruitKit.fx(u, "kai_reject", t.position().add(0, 1, 0), 3, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
            return true;
        }));

        // Passive: the shell body. While the Impact Dial drinks, 75% of every blow is soaked into the store; otherwise a
        // tenth of each melee blow is. In the White Sea the shells recharge (Breath/Flame/Flash cooldowns run faster).
        f.incomingHook = (u, a, amount, info) -> {
            if (CombatState.of(u).hasBuff(ABSORB)) {
                store(u, amount * 0.75f);
                return amount * 0.25f;
            }
            if (a != null && a.distanceTo(u) < 4) store(u, amount * 0.1f);
            return amount;
        };
        f.tickHook = u -> {
            if (u.tickCount % 20 != 0 || u.level().dimension() != com.grandline.world.SkyWorld.LEVEL) return;
            CombatState s = CombatState.of(u);
            long now = u.level().getGameTime();
            for (String id : new String[]{"fruit/kai/breath_dial", "fruit/kai/flame_dial", "fruit/kai/flash_dial"})
                if (s.onCooldown(id, now)) s.cooldowns.put(id, s.cooldowns.get(id) - 10);
        };
    }

    private KaiFruit() {}
}
