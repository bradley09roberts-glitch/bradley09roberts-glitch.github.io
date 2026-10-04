package com.squidgame.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The giant Red Light, Green Light doll (9 blocks tall). All of her state is synced so that every client sees the
 * same pose: {@link State} (which animation loop), the eyes, and the turn duration (animation playback is stretched
 * to the difficulty's turn time). The server game drives her; she has no AI.
 */
public class DollEntity extends Mob implements GeoEntity {
    public enum State {
        DORMANT, FACING_TREE, TURNING_TO_PLAYERS, FACING_PLAYERS, TURNING_TO_TREE;

        public static State byOrdinal(int i) {
            State[] v = values();
            return i >= 0 && i < v.length ? v[i] : DORMANT;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_EYES =
            SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TURN_TICKS =
            SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SCANNING =
            SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    public DollEntity(EntityType<? extends DollEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(true);
        setNoAi(true);
        setInvulnerable(true);
        noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, State.DORMANT.ordinal());
        builder.define(DATA_EYES, false);
        builder.define(DATA_TURN_TICKS, 20);
        builder.define(DATA_SCANNING, false);
    }

    public State state() {
        return State.byOrdinal(entityData.get(DATA_STATE));
    }

    public boolean eyesOn() {
        return entityData.get(DATA_EYES);
    }

    public int turnTicks() {
        return entityData.get(DATA_TURN_TICKS);
    }

    public boolean scanning() {
        return entityData.get(DATA_SCANNING);
    }

    public void setEyes(boolean on) {
        entityData.set(DATA_EYES, on);
    }

    public void setScanning(boolean on) {
        entityData.set(DATA_SCANNING, on);
    }

    public void setTurnTicks(int ticks) {
        entityData.set(DATA_TURN_TICKS, Math.max(4, ticks));
    }

    /** Sets the pose loop and (when a transition applies) triggers the matching one-shot on all clients. */
    public void setState(State s) {
        State old = state();
        if (old == s) {
            return;
        }
        entityData.set(DATA_STATE, s.ordinal());
        // the turns are state driven (mainState plays them once and holds the pose): triggering them as well would
        // restart the animation when the trigger ends
        switch (s) {
            case FACING_TREE -> {
                if (old == State.DORMANT) {
                    triggerAnim("main", "wake");
                }
            }
            default -> {
            }
        }
    }

    public void lockOn() {
        triggerAnim("main", "lock_on");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DollState", state().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // the game re-applies the doll's state when a round starts
    }

    // ---------------------------------------------------------------- animation

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<DollEntity> main = new AnimationController<>(this, "main", 4, this::mainState);
        main.setAnimationSpeedHandler(d -> {
            // turn animations are authored at 1.0 s; stretch them to the synced turn time
            State s = d.state();
            if (s == State.TURNING_TO_PLAYERS || s == State.TURNING_TO_TREE) {
                return 20.0 / d.turnTicks();
            }
            return 1.0;
        });
        main.triggerableAnim("wake", RawAnimation.begin().thenPlay(Anims.D_WAKE));
        main.triggerableAnim("lock_on", RawAnimation.begin().thenPlay(Anims.D_LOCK_ON));
        controllers.add(main);
    }

    private PlayState mainState(AnimationState<DollEntity> state) {
        return switch (state()) {
            case DORMANT -> state.setAndContinue(RawAnimation.begin().thenLoop(Anims.D_DORMANT));
            case FACING_TREE -> state.setAndContinue(RawAnimation.begin().thenLoop(Anims.D_IDLE_TREE));
            case TURNING_TO_PLAYERS -> state.setAndContinue(RawAnimation.begin().thenPlayAndHold(Anims.D_TURN_TO_PLAYERS));
            case FACING_PLAYERS -> state.setAndContinue(RawAnimation.begin().thenLoop(
                    scanning() ? Anims.D_SCAN_PLAYERS : Anims.D_IDLE_PLAYERS));
            case TURNING_TO_TREE -> state.setAndContinue(RawAnimation.begin().thenPlayAndHold(Anims.D_TURN_TO_TREE));
        };
    }
}
