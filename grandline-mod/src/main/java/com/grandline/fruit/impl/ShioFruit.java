package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.FruitRules;
import com.grandline.npc.GLNpc;
import com.grandline.player.Characters;
import com.grandline.player.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Shio Shio no Mi — ORIGINAL salt Paramecia. The user produces and controls salt: crystal shards and spikes, salt walls
 * and salt flats (which crystallise straight out of sea water, giving a fruit user ground to stand on at sea), and a
 * touch that dries the body out. Salt is the bane of the sea's people: Fish-Men, Merfolk and other water-dwellers
 * (and water-element fruit users) take 50% more damage from every salt attack.
 */
public final class ShioFruit {
    static final BlockState SALT = Blocks.CALCITE.defaultBlockState();
    static final BlockState CRYSTAL = Blocks.QUARTZ_PILLAR.defaultBlockState();

    /** Creatures of the sea that salt dries out. */
    public static boolean aquatic(LivingEntity e) {
        Race race = null;
        if (e instanceof Player p) race = Characters.get(p).race;
        else if (e instanceof GLNpc n) race = Race.byId(n.raceId());
        if (race != null && (race.breathesWater() || race == Race.WHALEFOLK)) return true;
        var fruit = FruitRules.fruitOf(e);
        if (fruit != null && fruit.element == Element.WATER) return true;
        return e.getType().is(EntityTypeTags.AQUATIC) || e.canBreatheUnderwater() && !(e instanceof Player);
    }

    static void dehydrate(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, amp));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, Math.max(0, amp - 1)));
        if (aquatic(e)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp + 1));
    }

    public static void init(DevilFruit f) {
        Hits.Info shard = FruitKit.info(Element.PIERCE, 0.3f);

        // Salt shards: a spray of salt crystals that dries out whoever it cuts.
        f.add(f.ability("salt_shard").cooldown(22).stamina(10).anim("throw").exec((u, c) -> {
            for (int i = -1; i <= 1; i++) {
                AbilityProjectile p = FruitKit.shoot(c, "salt_shard", 0.3f, 2.3, FruitKit.dmg(c, 3), shard.withPoise(12));
                p.setDeltaMovement(PowerKit.yaw(p.getDeltaMovement(), i * 7));
                p.life = 30;
                p.onHit = (pr, hit, pos) -> {
                    if (hit != null) dehydrate(hit, 60, 0);
                };
            }
            return true;
        }));

        // Crystal spikes: a line of salt crystals bursts from the ground.
        f.add(f.ability("crystal_spikes").unlock(5).cooldown(50).stamina(18).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            Set<Integer> struck = new HashSet<>();
            for (int d = 2; d <= 12; d++) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 2, () -> {
                    Vec3 p = start.add(dir.scale(step));
                    BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(start.y), (int) Math.floor(p.z));
                    if (g != null) {
                        int h = 1 + (step % 3 == 0 ? 1 : 0);
                        for (int y = 1; y <= h; y++) PowerKit.placeFree(c.level, g.above(y), CRYSTAL);
                    }
                    FruitKit.fx(u, "salt", p.add(0, 0.5, 0), 0.8f, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 1.6)) {
                        if (struck.add(e.getId()) && PowerKit.hit(u, e, FruitKit.dmg(c, 7), shard.withStun(10).withPoise(35)))
                            FruitKit.impulse(e, new Vec3(0, 0.7, 0), false);
                    }
                });
            }
            return true;
        }));

        // Desiccate: crust the target in salt, pulling the water out of their body over a few seconds.
        f.add(f.ability("desiccate").unlock(10).cooldown(120).stamina(22).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            dehydrate(t, 120, 1);
            for (int i = 0; i < 5; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    if (!t.isAlive()) return;
                    Hits.hit(u, t, FruitKit.dmg(c, 2), FruitKit.info(Element.PHYSICAL, 0f).withPoise(10));
                    FruitKit.fx(u, "salt", t.position().add(0, 1, 0), t.getBbWidth() + 0.4f, 0, 0);
                });
            }
            return true;
        }));

        // Salt wall: a thick wall of salt. It even crystallises out of sea water.
        f.add(f.ability("salt_wall").unlock(15).cooldown(100).stamina(18).anim("cast_forward").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 center = u.position().add(dir.scale(3));
            for (int w = -3; w <= 3; w++) {
                double x = center.x - dir.z * w, z = center.z + dir.x * w;
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(x), (int) Math.floor(u.getY()), (int) Math.floor(z));
                int baseY = g == null ? (int) Math.floor(u.getY()) - 1 : g.getY();
                if (g != null && c.level.getBlockState(g).is(Blocks.WATER)) com.grandline.fruit.TerrainChanges.place(c.level, g, SALT);
                for (int y = 1; y <= 3; y++) PowerKit.placeFree(c.level, new BlockPos((int) Math.floor(x), baseY + y, (int) Math.floor(z)), SALT);
            }
            FruitKit.fx(u, "salt_wall", center.add(0, 1.5, 0), (float) dir.x, (float) dir.z, 3.5f);
            PowerKit.sound(c, center, SoundEvents.CALCITE_PLACE, 1f, 0.8f);
            return true;
        }));

        // Salt flats: the ground (and the sea surface) around the user turns to salt; enemies on it dry out.
        f.add(f.ability("salt_flats").unlock(25).cooldown(300).stamina(34).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 7 + c.mastery / 20.0;
            PowerKit.paintDisc(c.level, center, r, SALT, SALT);
            FruitKit.fx(u, "salt_flats", center, (float) r, 0, 0);
            for (int i = 0; i < 5; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + 5 + i * 15L, () -> {
                    for (LivingEntity e : PowerKit.foes(u, center, r + 0.5)) {
                        if (Math.abs(e.getY() - center.y) > 3) continue;
                        Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.PHYSICAL, 0f).withPoise(8));
                        dehydrate(e, 40, 0);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                    }
                    FruitKit.fx(u, "salt_flats", center, (float) r, 0, 0);
                });
            }
            return true;
        }));

        // Awakening — Pillar of Salt: enemies are sealed inside salt and dried to the core.
        f.add(f.ability("pillar_of_salt").unlock(50).cooldown(900).stamina(75).awakened().anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return false;
            for (LivingEntity e : PowerKit.foes(u, t.position().add(0, 1, 0), 2.5)) {
                PowerKit.encase(c.level, e, SALT);
                Hits.hit(u, e, FruitKit.dmg(c, 14), FruitKit.info(Element.PIERCE, 0f).withStun(80).withPoise(100).asUnblockable());
                dehydrate(e, 160, 2);
                FruitKit.fx(u, "salt_statue", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
            }
            return true;
        }));

        // Passive: salt is poison to the sea's children (Fish-Men, Merfolk, water-dwellers, water fruits).
        f.outgoingHook = (u, v, amount, info) -> aquatic(v) ? amount * 1.5f : amount;
    }

    private ShioFruit() {}
}
