package com.starforged.moon.entity;

import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lunar Moths are drawn to light - not to bask in it, but to eat it. A moth flutters to the nearest torch, lantern or
 * campfire and drinks the flame (the torch drops as an item; campfires go out). Anyone holding a light is a target.
 */
public class LunarMothEntity extends Monster {
    public LunarMothEntity(EntityType<? extends LunarMothEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 16.0)
            .add(Attributes.FLYING_SPEED, 0.6)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(3, new EatLightGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
            (target, level) -> holdsLight(target)));
    }

    private static boolean holdsLight(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem block && block.getBlock().defaultBlockState().is(ModTags.MOTH_LIGHTS)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            this.level().addParticle(ModParticles.MOON_DUST.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.8, this.getY() + 0.3,
                this.getZ() + (this.random.nextDouble() - 0.5) * 0.8, 0.0, -0.02, 0.0);
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MoonSounds.MOTH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.MOTH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.MOTH_DEATH.get();
    }

    /** Find a light source, flutter over and drink it. */
    private class EatLightGoal extends Goal {
        private @Nullable BlockPos light;
        private int search;
        private int eating;

        EatLightGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (LunarMothEntity.this.getTarget() != null || --this.search > 0) {
                return false;
            }
            this.search = 40;
            this.light = this.findLight();
            return this.light != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.light != null && LunarMothEntity.this.getTarget() == null
                && LunarMothEntity.this.level().getBlockState(this.light).is(ModTags.MOTH_LIGHTS);
        }

        @Override
        public void stop() {
            this.light = null;
            this.eating = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private @Nullable BlockPos findLight() {
            LunarMothEntity moth = LunarMothEntity.this;
            BlockPos origin = moth.blockPosition();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -6, -12), origin.offset(12, 6, 12))) {
                BlockState state = moth.level().getBlockState(pos);
                if (state.is(ModTags.MOTH_LIGHTS) && !(state.getBlock() instanceof CampfireBlock && !state.getValue(CampfireBlock.LIT))) {
                    double d = pos.distSqr(origin);
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos.immutable();
                    }
                }
            }
            return best;
        }

        @Override
        public void tick() {
            LunarMothEntity moth = LunarMothEntity.this;
            if (this.light == null) {
                return;
            }
            Vec3 above = Vec3.atCenterOf(this.light).add(0, 0.8, 0);
            moth.getMoveControl().setWantedPosition(above.x, above.y, above.z, 1.0);
            if (moth.position().distanceToSqr(above) < 2.5) {
                this.eating++;
                if (moth.level() instanceof ServerLevel server) {
                    server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), above.x, above.y - 0.6, above.z, 2, 0.1, 0.1, 0.1, 0.02);
                    if (this.eating >= 30) {
                        BlockState state = server.getBlockState(this.light);
                        if (server.getGameRules().get(GameRules.MOB_GRIEFING)) {
                            if (state.getBlock() instanceof CampfireBlock) {
                                server.setBlockAndUpdate(this.light, state.setValue(CampfireBlock.LIT, false));
                            } else {
                                server.destroyBlock(this.light, true, moth);
                            }
                        }
                        server.playSound(null, this.light, MoonSounds.MOTH_EAT.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                        moth.heal(4.0F);
                        this.light = null;
                    }
                }
            }
        }
    }
}
