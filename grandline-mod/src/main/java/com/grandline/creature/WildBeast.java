package com.grandline.creature;

import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Locale;

/**
 * Canon-world island wildlife with simple AI (GeckoLib-animated): Kung-Fu Dugongs on desert coasts (neutral; beaten
 * ones bow to their master), Lapahns on snow islands (hostile), Banana-gators in tropical swamps (hostile) and giant
 * Sea Cats along warm shores (neutral). Spawned by {@link WildlifeSpawner}; despawn when no one is near.
 */
public class WildBeast extends PathfinderMob implements GeoEntity {
    public enum Kind {
        KUNGFU_DUGONG(0.9f, 1.1f, 26, 5, 0.32, 0.6f, false, true),
        LAPAHN(1.3f, 2.4f, 60, 9, 0.33, 1.2f, true, false),
        BANANA_GATOR(1.4f, 1.0f, 70, 11, 0.24, 1.0f, true, true),
        SEA_CAT(1.6f, 1.9f, 80, 10, 0.27, 1.6f, false, true);

        public final float width, height, health, damage, knockback;
        public final double speed;
        public final boolean hostile, swimmer;

        Kind(float width, float height, float health, float damage, double speed, float knockback, boolean hostile, boolean swimmer) {
            this.width = width;
            this.height = height;
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.knockback = knockback;
            this.hostile = hostile;
            this.swimmer = swimmer;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final Kind kind;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** A beaten Kung-Fu Dugong never fights its master again. */
    private boolean bowed;

    public WildBeast(EntityType<? extends PathfinderMob> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.xpReward = 5 + (int) kind.health / 8;
        if (!level.isClientSide) addGoals(); // registerGoals() runs in the Mob constructor, before kind is set
    }

    public static AttributeSupplier.Builder createAttributes(Kind k) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, k.health)
                .add(Attributes.ATTACK_DAMAGE, k.damage)
                .add(Attributes.MOVEMENT_SPEED, k.speed)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ARMOR, k == Kind.BANANA_GATOR ? 6 : 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, k == Kind.KUNGFU_DUGONG ? 0.1 : 0.6)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, k.swimmer ? 1.0 : 0.0);
    }

    private void addGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(5, kind.swimmer ? new RandomStrollGoal(this, 0.8) : new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        if (kind.hostile) targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> !bowed));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        swing(InteractionHand.MAIN_HAND);
        if (target instanceof LivingEntity le) {
            Hits.Info info = Hits.Info.of(kind == Kind.KUNGFU_DUGONG ? Element.BLUNT : kind == Kind.LAPAHN ? Element.SLASH : Element.PIERCE, kind.knockback);
            return Hits.hit(this, le, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), info) == Hits.Result.HIT;
        }
        return super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        // canon: a beaten Kung-Fu Dugong bows and becomes the victor's disciple
        if (r && kind == Kind.KUNGFU_DUGONG && !bowed && isAlive() && getHealth() < getMaxHealth() * 0.3f && src.getEntity() instanceof ServerPlayer sp) {
            bowed = true;
            setTarget(null);
            setLastHurtByMob(null);
            sp.displayClientMessage(Component.translatable("grandline.wildlife.dugong_bows").withStyle(ChatFormatting.GOLD), true);
        }
        return r;
    }

    @Override
    public void setTarget(LivingEntity t) {
        super.setTarget(bowed ? null : t);
    }

    public boolean bowed() {
        return bowed;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !bowed;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putBoolean("Bowed", bowed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        bowed = t.getBoolean("Bowed");
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        String p = "animation." + kind.id() + ".";
        RawAnimation idle = RawAnimation.begin().thenLoop(p + "idle"), walk = RawAnimation.begin().thenLoop(p + "walk"),
                attack = RawAnimation.begin().thenLoop(p + "attack"), swim = RawAnimation.begin().thenLoop(p + "swim");
        c.add(new AnimationController<>(this, "main", 3, st -> {
            if (swinging) return st.setAndContinue(attack);
            if (isInWater() && kind.swimmer) return st.setAndContinue(swim);
            if (st.isMoving() || st.getLimbSwingAmount() > 0.1f) return st.setAndContinue(walk);
            return st.setAndContinue(idle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
