package com.grandline.mixin;

import com.grandline.ship.ShipEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player on a moving deck is carried by their own client (players are client-authoritative). The server re-plays
 * each move to validate it against its own ship parts, which stand a little ahead of or behind the client's while
 * the ship sails; vanilla then called the move "wrong" and snapped the player back to where the deck had been, off
 * the ship (seen in the two-client test: "Mate moved wrongly!" and a mate overboard). For a player on or right
 * beside a ship's deck the check allows two blocks of difference and ignores "new" collisions with the parts; the
 * parts still collide (so the server still sees the deck under them and does not kick them for floating).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ShipDeckMoveMixin {
    @Shadow
    public ServerPlayer player;

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(doubleValue = 0.0625))
    private double grandline$deckTolerance(double wrong) {
        return ShipEntity.nearDeck(player) ? 4.0 : wrong;
    }

    @Inject(method = "isPlayerCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
    private void grandline$deckParts(LevelReader level, AABB box, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (ShipEntity.nearDeck(player)) cir.setReturnValue(false);
    }
}
