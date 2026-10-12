package com.grandline.mixin;

import com.grandline.ship.ShipPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the server, players do not collide with ship parts. A player on a moving deck is carried by their own client
 * (players are client-authoritative); the server only re-plays the move to validate it, against ship parts that
 * stand where the server's ship is, a little ahead of or behind the client's. A rail or the deck edge there turned
 * the move "wrong" and the server snapped the player back to where the deck had been, off the ship (seen in the
 * two-client test as "moved wrongly!" and a mate overboard). NPCs, whose movement the server runs, still collide.
 */
@Mixin(Entity.class)
public abstract class ShipDeckPlayerMixin {
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void grandline$playersPassShipPartsOnServer(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof ShipPart && (Object) this instanceof ServerPlayer) cir.setReturnValue(false);
    }
}
