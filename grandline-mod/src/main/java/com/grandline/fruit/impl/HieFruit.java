package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Hie Hie no Mi — ice Logia. Freezes the sea into walkable ice and locks enemies in place. */
public final class HieFruit {
    public static void init(DevilFruit f) {
        Hits.Info ice = FruitKit.info(Element.ICE, 0.4f);
        f.add(f.ability("ice_age").cooldown(80).stamina(22).anim("cast_ground").exec((u, c) -> {
            Vec3 look = c.look().multiply(1, 0, 1).normalize();
            BlockPos base = u.blockPosition();
            int len = 10 + c.mastery / 5;
            for (int d = 1; d <= len; d++)
                for (int w = -d / 2 - 1; w <= d / 2 + 1; w++) {
                    int x = (int) Math.round(base.getX() + look.x * d - look.z * w);
                    int z = (int) Math.round(base.getZ() + look.z * d + look.x * w);
                    for (int dy = -2; dy <= 1; dy++) {
                        BlockPos p = new BlockPos(x, base.getY() + dy, z);
                        var st = c.level.getBlockState(p);
                        if (st.is(Blocks.WATER)) FruitKit.temp(c.level, p, Blocks.ICE.defaultBlockState());
                        else if (st.isAir() && c.level.getBlockState(p.below()).isSolidRender(c.level, p.below()) && dy <= 0) FruitKit.temp(c.level, p, Blocks.SNOW.defaultBlockState());
                    }
                }
            for (LivingEntity e : Hits.cone(u, len, 70)) {
                if (Hits.hit(u, e, FruitKit.dmg(c, 5), ice.withStun(30)) == Hits.Result.HIT)
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
            }
            FruitKit.fx(u, "ice_wave", u.position(), (float) look.x, (float) look.z, len);
            return true;
        }));
        f.add(f.ability("ice_saber").unlock(5).cooldown(30).stamina(12).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "ice_spear", 0.6f, 2.0, FruitKit.dmg(c, 6), ice.withPoise(20));
            p.pierce = true;
            return true;
        }));
        f.add(f.ability("ice_wall").unlock(10).cooldown(100).stamina(18).anim("cast_forward").exec((u, c) -> {
            Vec3 look = c.look().multiply(1, 0, 1).normalize();
            Vec3 center = u.position().add(look.scale(3));
            for (int w = -3; w <= 3; w++)
                for (int y = 0; y < 4; y++) {
                    BlockPos p = BlockPos.containing(center.x - look.z * w, u.getY() + y, center.z + look.x * w);
                    if (c.level.getBlockState(p).canBeReplaced()) FruitKit.temp(c.level, p, Blocks.PACKED_ICE.defaultBlockState());
                }
            return true;
        }));
        f.add(f.ability("partisan").unlock(20).cooldown(120).stamina(30).anim("cast_up").exec((u, c) -> {
            for (int i = 0; i < 7; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    AbilityProjectile p = FruitKit.shoot(c, "ice_spear", 0.6f, 2.2, FruitKit.dmg(c, 5), ice);
                    p.setPos(p.getX() + (n - 3) * 0.6, p.getY() + 1.2, p.getZ());
                    p.pierce = true;
                });
            }
            return true;
        }));
        f.add(f.ability("ice_time").unlock(40).cooldown(200).stamina(35).anim("grab").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 4 ? c.target : Hits.cone(u, 3.5, 60).stream().findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 8), ice.withStun(80).asUnblockable());
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 10));
            t.setTicksFrozen(t.getTicksRequiredToFreeze() + 160);
            FruitKit.fx(u, "ice_encase", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            return true;
        }));
        // Passive: freeze water underfoot while sprinting (walk over the sea) - only on the surface, never while weakened
        f.tickHook = u -> {
            if (u.tickCount % 2 != 0 || !(u.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
            if (!u.isSprinting() && !(u instanceof com.grandline.npc.GLNpc)) return;
            BlockPos below = u.blockPosition().below();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = below.offset(dx, 0, dz);
                    if (sl.getBlockState(p).is(Blocks.WATER) && sl.getBlockState(p.above()).isAir()) TerrainChanges.place(sl, p, Blocks.FROSTED_ICE.defaultBlockState());
                }
        };
    }

    private HieFruit() {}
}
