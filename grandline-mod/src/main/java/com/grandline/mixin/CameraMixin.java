package com.grandline.mixin;

import com.grandline.client.ClientState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Over-the-shoulder third-person camera while in Combat Mode (agreed camera behaviour). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Inject(method = "setup", at = @At("TAIL"))
    private void grandline$shoulder(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!detached || thirdPersonReverse || !ClientState.combatMode) return;
        float s = entity instanceof LivingEntity le ? le.getScale() : 1f;
        move(-0.6f * s, 0.15f * s, -0.75f * s);
    }
}
