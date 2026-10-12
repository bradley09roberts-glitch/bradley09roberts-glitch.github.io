package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.net.GLNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Bari Bari no Mi — barriers, the strongest defence. Walls and stairs of (temporary) barrier glass, a crashing shield,
 * a ball that absorbs everything until it shatters, a bulldozing wall and a sealed barrier prison.
 */
public final class BariFruit {
    static final String WALL = "bari_wall";
    static final String BALL = "bari_ball";
    static final BlockState GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    /** Remaining absorption of an active barrier ball. */
    static final Map<LivingEntity, Float> BALL_HP = Collections.synchronizedMap(new WeakHashMap<>());

    public static void init(DevilFruit f) {
        Hits.Info bash = FruitKit.info(Element.BLUNT, 3.2f);

        // Barrier wall: a wall of barrier glass in front; while it stands, frontal attacks are stopped.
        f.add(f.ability("barrier_wall").cooldown(60).stamina(16).anim("cast_forward").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 center = u.position().add(dir.scale(2));
            for (int w = -2; w <= 2; w++)
                for (int y = 0; y < 4; y++) {
                    BlockPos p = BlockPos.containing(center.x - dir.z * w, u.getY() + y, center.z + dir.x * w);
                    PowerKit.placeFree(c.level, p, GLASS);
                }
            CombatState.of(u).buffs.put(WALL, 100);
            FruitKit.fx(u, "barrier", center.add(0, 1.5, 0), (float) dir.x, (float) dir.z, 2.5f);
            PowerKit.sound(c, center, SoundEvents.GLASS_PLACE, 1f, 1.4f);
            return true;
        }));

        // Barrier crash: charge behind a barrier, ramming everyone in the way.
        f.add(f.ability("barrier_crash").unlock(5).cooldown(50).stamina(16).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, PowerKit.flatAim(c), 7);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.3))
                Hits.hit(u, e, FruitKit.dmg(c, 9), bash.withPoise(70));
            FruitKit.fx(u, "barrier_crash", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
            return true;
        }));

        // Barrier stairs: a staircase of barriers climbing in the look direction.
        f.add(f.ability("barrier_stairs").unlock(10).cooldown(100).stamina(14).anim("cast_up").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            BlockPos base = u.blockPosition();
            int steps = 8 + c.mastery / 20;
            for (int i = 1; i <= steps; i++) {
                BlockPos p = BlockPos.containing(base.getX() + 0.5 + dir.x * i, base.getY() + i - 1, base.getZ() + 0.5 + dir.z * i);
                PowerKit.placeFree(c.level, p, GLASS);
            }
            FruitKit.fx(u, "barrier", u.position().add(dir.scale(3)).add(0, 2, 0), (float) dir.x, (float) dir.z, 1.5f);
            return true;
        }));

        // Barrier ball: a sphere around the user that absorbs all damage until it breaks.
        f.add(f.ability("barrier_ball").unlock(15).cooldown(400).stamina(30).anim("harden").exec((u, c) -> {
            int dur = 160 + c.mastery;
            CombatState.of(u).buffs.put(BALL, dur);
            BALL_HP.put(u, 40f + c.mastery * 0.4f + (c.awakened ? 30 : 0));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 1, false, false));
            FruitKit.fx(u, "barrier_ball", u.position().add(0, 1, 0), 1.6f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
            return true;
        }));

        // Barrier bulldozer: a moving wall that shoves everything (projectiles included) out of the way.
        f.add(f.ability("barrier_bulldozer").unlock(30).cooldown(160).stamina(34).anim("double_palm").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            Set<Integer> struck = new HashSet<>();
            for (int d = 1; d <= 16; d++) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 2, () -> {
                    Vec3 p = start.add(dir.scale(step)).add(0, 1, 0);
                    for (Projectile pr : PowerKit.hostileProjectiles(u, p, 2.5)) pr.discard();
                    for (LivingEntity e : PowerKit.foes(u, p, 2.5)) {
                        if (struck.add(e.getId()) && PowerKit.hit(u, e, FruitKit.dmg(c, 9), FruitKit.info(Element.BLUNT, 0.5f).withPoise(60)))
                            FruitKit.impulse(e, dir.scale(1.6).add(0, 0.35, 0), false);
                    }
                    if (step % 2 == 0) FruitKit.fx(u, "barrier", p.add(0, 0.5, 0), (float) dir.x, (float) dir.z, 2.5f);
                });
            }
            return true;
        }));

        // Awakening — Barrier Prison: seal enemies inside barrier boxes, then crush them.
        f.add(f.ability("barrier_prison").unlock(50).cooldown(900).stamina(70).awakened().anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 14);
            var caught = PowerKit.foes(u, at.add(0, 1, 0), 3.5);
            if (caught.isEmpty()) return false;
            for (LivingEntity e : caught) {
                PowerKit.encase(c.level, e, GLASS);
                Hits.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.BLUNT, 0f).withStun(60).withPoise(80));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6));
                FruitKit.fx(u, "barrier_ball", e.position().add(0, 1, 0), 1.4f, 0, 0);
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 40, () -> {
                for (LivingEntity e : caught) {
                    if (!e.isAlive()) continue;
                    Hits.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.BLUNT, 0f).withPoise(60).asUnblockable());
                    FruitKit.fx(u, "barrier_shatter", e.position().add(0, 1, 0), 1.4f, 0, 0);
                }
            });
            return true;
        }));

        // Passive: barriers stop damage. The wall stops frontal hits; the ball absorbs everything until it breaks.
        f.incomingHook = (u, a, amount, info) -> {
            CombatState s = CombatState.of(u);
            if (s.hasBuff(BALL)) {
                float left = BALL_HP.getOrDefault(u, 0f) - amount * (info.unblockable() ? 2 : 1);
                if (left > 0) {
                    BALL_HP.put(u, left);
                    GLNetwork.fx(u, "barrier_ball", u.getX(), u.getY() + 1, u.getZ(), 1.6f, 0, 0);
                    return 0f;
                }
                // the ball shatters; the overflow gets through
                s.buffs.remove(BALL);
                BALL_HP.remove(u);
                u.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                GLNetwork.fx(u, "barrier_shatter", u.getX(), u.getY() + 1, u.getZ(), 1.6f, 0, 0);
                return -left / (info.unblockable() ? 2 : 1);
            }
            if (s.hasBuff(WALL) && !info.unblockable() && a != null && Hits.facing(u, a)) {
                GLNetwork.fx(u, "block", u.getX(), u.getY() + 1.2, u.getZ(), 0, 0, 0);
                return 0f;
            }
            return amount;
        };
    }

    private BariFruit() {}
}
