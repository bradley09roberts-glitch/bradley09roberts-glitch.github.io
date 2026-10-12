package com.grandline.client.render.creature;

import com.grandline.client.anim.AnimDef;
import com.grandline.client.anim.Animator;
import com.grandline.creature.BeastForms;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Animation state for one Zoan beast model drawn in place of a player or NPC (GeckoLib "replaced entity"; the
 * animation managers are kept per entity id). Plays idle / walk / attack / fly from what the entity is doing.
 */
public final class BeastAnimatable implements GeoReplacedEntity {
    public final BeastForms.Beast beast;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle, walk, attack, fly;

    public BeastAnimatable(BeastForms.Beast beast) {
        this.beast = beast;
        String p = "animation." + beast.id() + ".";
        idle = RawAnimation.begin().thenLoop(p + "idle");
        walk = RawAnimation.begin().thenLoop(p + "walk");
        attack = RawAnimation.begin().thenLoop(p + "attack");
        fly = RawAnimation.begin().thenLoop(p + "fly");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 3, state -> {
            Entity ent = state.getData(DataTickets.ENTITY);
            if (!(ent instanceof LivingEntity le)) return PlayState.STOP;
            var ctl = state.getController();
            boolean air = airborne(le);
            if (attacking(le)) {
                ctl.setAnimationSpeed(1.4);
                return state.setAndContinue(attack);
            }
            if (air && (beast.flyer() || le.fallDistance > 2.5f || le.getDeltaMovement().y > 0.3)) {
                ctl.setAnimationSpeed(1);
                return state.setAndContinue(fly);
            }
            float swing = state.getLimbSwingAmount();
            if (swing > 0.1f) {
                ctl.setAnimationSpeed(Mth.clamp(swing * 2.2f, 0.7f, 2.2f));
                return state.setAndContinue(walk);
            }
            ctl.setAnimationSpeed(1);
            return state.setAndContinue(idle);
        }));
    }

    /** In the air: not on the ground, not swimming or riding, and nothing solid just below the feet. */
    static boolean airborne(LivingEntity le) {
        if (le.onGround() || le.isInWater() || le.isPassenger()) return false;
        var below = net.minecraft.core.BlockPos.containing(le.getX(), le.getY() - 0.35, le.getZ());
        return le.level().getBlockState(below).getCollisionShape(le.level(), below).isEmpty();
    }

    /** A melee swing or a one-shot combat animation (anything but reactions) counts as attacking. */
    static boolean attacking(LivingEntity le) {
        if (le.swinging) return true;
        float[] tw = new float[2];
        AnimDef d = Animator.current(le.getId(), 0, tw);
        if (d == null || d.loop()) return false;
        String n = d.name();
        for (String no : new String[]{"hit", "dodge", "stagger", "guard", "parry", "block", "transform", "get_up", "knock", "surrender", "land", "jump", "iai_stance"})
            if (n.startsWith(no)) return false;
        return true;
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.PLAYER;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
