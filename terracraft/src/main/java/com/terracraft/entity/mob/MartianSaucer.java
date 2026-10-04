package com.terracraft.entity.mob;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.registry.content.MartianContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Martian Saucer, Martian Madness's flagship: it hovers over the player sweeping the ground with lasers and
 * dropping rockets, and counts for ten kills. Drops one of its weapons.
 */
public class MartianSaucer extends TerrariaMob {
    private int laserTimer = 40;
    private int rocketTimer = 80;

    public MartianSaucer(EntityType<? extends MartianSaucer> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        Player target = level.getNearestPlayer(this, 100);
        if (target == null || target.isSpectator() || target.isCreative()) {
            return;
        }
        Vec3 goal = target.position().add(Mth.sin(tickCount * 0.03F) * 6, 10, 0);
        Vec3 motion = getDeltaMovement().scale(0.9).add(goal.subtract(position()).normalize().scale(0.04));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 from = position().add(0, 0.2, 0);
        float mult = TerrariaDifficulty.enemyDamageMultiplier(level);
        if (--laserTimer <= 0) {
            for (int i = 0; i < 3; i++) {
                Vec3 aim = target.position().add((random.nextDouble() - 0.5) * 3, 0.5, (random.nextDouble() - 0.5) * 3).subtract(from).normalize();
                TerrariaProjectile.shoot(level, this, ProjectileKinds.MARTIAN_LASER, from, aim, 10.0F, 0.0F, 70.0F * mult, DamageClass.GENERIC, 0, 1.0F);
            }
            playSound(SoundEvents.BEACON_DEACTIVATE, 1.5F, 2.0F);
            laserTimer = 25 + random.nextInt(15);
        }
        if (--rocketTimer <= 0) {
            TerrariaProjectile.shoot(level, this, ProjectileKinds.ENEMY_ROCKET, from, new Vec3(0, -1, 0), 2.0F, 0.2F, 90.0F * mult,
                DamageClass.GENERIC, 0, 3.0F);
            rocketTimer = 50 + random.nextInt(30);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && source.getEntity() instanceof Player) {
            List<Item> loot = List.of(MartianContent.INFLUX_WAVER.get(), MartianContent.LASER_MACHINEGUN.get(), MartianContent.XENOPOPPER.get(),
                com.terracraft.registry.content.SummonContent.XENO_STAFF.get());
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(loot.get(random.nextInt(loot.size())))));
        }
    }
}
