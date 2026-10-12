package com.grandline.mixin;

import com.grandline.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observation Haki: living things within range are outlined for the local player only. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void grandline$observation(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || entity == mc.player || !(entity instanceof LivingEntity)) return;
        var v = ClientState.visuals(mc.player.getId());
        if (!v.getBoolean("obs")) return;
        int range = Math.max(12, v.getInt("obsRange"));
        if (entity.distanceToSqr(mc.player) <= range * range) cir.setReturnValue(true);
    }
}
