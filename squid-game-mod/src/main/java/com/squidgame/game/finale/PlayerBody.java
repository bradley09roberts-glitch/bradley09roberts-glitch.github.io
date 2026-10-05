package com.squidgame.game.finale;

import com.squidgame.SquidGameMod;
import com.squidgame.core.finale.CourtGeometry;
import com.squidgame.core.finale.FinaleRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * A player in the fight. The player walks by themselves (the client predicts its own movement); the server only
 * applies knock-back and dash velocities (sent to the client like vanilla knock-back), slows the player down through
 * a transient attribute modifier according to what the fighter is doing, and removes the jump. Transient modifiers
 * are never saved, so a crash cannot leave a player slowed.
 */
final class PlayerBody extends FightBody {
    private static final ResourceLocation SPEED = SquidGameMod.id("finale_speed");
    private static final ResourceLocation JUMP = SquidGameMod.id("finale_no_jump");

    private final ServerPlayer player;
    private int dashLeft;
    private double dashX, dashZ;
    private double factor = 1.0;

    PlayerBody(ServerPlayer player) {
        this.player = player;
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    ServerPlayer player() {
        return player;
    }

    @Override
    LivingEntity entity() {
        return player;
    }

    @Override
    boolean isPlayer() {
        return true;
    }

    @Override
    void impulse(double ix, double iz) {
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(ix, v.y, iz);
        player.hurtMarked = true;
    }

    @Override
    void dash(double vx, double vz, int ticks) {
        dashX = vx;
        dashZ = vz;
        dashLeft = ticks;
    }

    @Override
    void cancelDash() {
        dashLeft = 0;
    }

    @Override
    void move(double wishX, double wishZ, CourtGeometry court) {
        if (dashLeft <= 0) {
            return;
        }
        // a dodge never carries a player over the line: look as far ahead as the step of this tick plus the slide after the
        // last tick (a player is not stopped by the server the way an NPC body is), and stop dashing a little before it
        double len = Math.max(1e-6, Math.hypot(dashX, dashZ));
        double reachX = dashX + dashX / len * FinaleRules.DASH_SLIDE, reachZ = dashZ + dashZ / len * FinaleRules.DASH_SLIDE;
        if (court.edgeDistance(player.getX() + reachX, player.getZ() + reachZ) < FinaleRules.OUT_MARGIN + 0.35) {
            dashLeft = 0;
            return;
        }
        dashLeft--;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(dashX, v.y, dashZ);
        player.hurtMarked = true;
    }

    @Override
    void setSpeedFactor(double f) {
        double clamped = Math.max(0.02, Math.min(1.0, f));
        if (Math.abs(clamped - factor) < 0.01) {
            return;
        }
        factor = clamped;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (clamped >= 0.999) {
                speed.removeModifier(SPEED);
            } else {
                speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED, clamped - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    @Override
    void release() {
        clearModifiers(player);
        factor = 1.0;
        dashLeft = 0;
    }

    /** Removes every modifier the fight may have put on a player (also used as a safety net when the game ends). */
    static void clearModifiers(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED);
        }
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(JUMP);
        }
    }
}
