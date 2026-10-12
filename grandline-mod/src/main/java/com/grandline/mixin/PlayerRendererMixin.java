package com.grandline.mixin;

import com.grandline.client.anim.Animator;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks first-person arm rendering so body animations do not leak onto the empty first-person arm. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void grandline$handStart(CallbackInfo ci) {
        Animator.firstPersonHand++;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void grandline$handEnd(CallbackInfo ci) {
        Animator.firstPersonHand = Math.max(0, Animator.firstPersonHand - 1);
    }
}
