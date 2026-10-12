package com.grandline.npc.crew;

import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Knockdown;
import com.grandline.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A crew member's day: stay close to the captain, pick up the captain's fight (whoever hurt the captain or whoever
 * the captain is hitting), and run to a knocked-down captain to pull them back up.
 */
public class CrewFollowGoal extends Goal {
    private static final int REVIVE_TICKS = 50;
    private final HumanoidNpc npc;
    private ServerPlayer captain;
    private int repath, reviveTicks;

    public CrewFollowGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.level().isClientSide || npc.notableId().isEmpty() || npc.flag(GLNpc.FLAG_SURRENDER) || npc.hasEffect(ModEffects.KNOCKED_OUT)) return false;
        captain = CrewManager.captainOf(npc);
        if (captain == null || captain.level() != npc.level() || CrewManager.isWaiting(npc)) return false;
        assist();
        if (npc.getTarget() != null) return false;
        return knockedDown() || npc.distanceToSqr(captain) > 6 * 6;
    }

    @Override
    public boolean canContinueToUse() {
        if (captain == null || !captain.isAlive() || captain.level() != npc.level() || npc.getTarget() != null || npc.flag(GLNpc.FLAG_SURRENDER)) return false;
        return knockedDown() || npc.distanceToSqr(captain) > 3 * 3;
    }

    @Override
    public void start() {
        repath = 0;
        reviveTicks = 0;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        captain = null;
        reviveTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (captain == null) return;
        npc.getLookControl().setLookAt(captain, 30, 30);
        double d2 = npc.distanceToSqr(captain);
        if (knockedDown() && d2 < 2.5 * 2.5) {
            npc.getNavigation().stop();
            if (++reviveTicks >= REVIVE_TICKS) revive();
            return;
        }
        reviveTicks = 0;
        if (d2 > 24 * 24 && captain.onGround() && !captain.isPassenger()) {
            BlockPos spot = NotableSpawner.safeSpotNear(captain.serverLevel(), captain.blockPosition());
            if (spot != null) {
                npc.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                npc.getNavigation().stop();
                return;
            }
        }
        if (--repath <= 0) {
            repath = 10;
            npc.getNavigation().moveTo(captain, d2 > 12 * 12 ? 1.2 : 1.0);
        }
    }

    private boolean knockedDown() {
        return captain != null && Characters.get(captain).isKnockedDown();
    }

    /** Take up the captain's fight. */
    private void assist() {
        LivingEntity t = null;
        int now = captain.tickCount;
        if (captain.getLastHurtByMob() != null && now - captain.getLastHurtByMobTimestamp() < 100) t = captain.getLastHurtByMob();
        else if (captain.getLastHurtMob() != null && now - captain.getLastHurtMobTimestamp() < 100) t = captain.getLastHurtMob();
        if (t != null && t != npc && t.isAlive() && !CrewManager.allied(npc, t) && t.distanceToSqr(npc) < 32 * 32) npc.setTarget(t);
    }

    private void revive() {
        CharacterData d = Characters.get(captain);
        Knockdown.standUp(captain, d, 0.35f);
        captain.sendSystemMessage(Component.translatable("grandline.knockdown.revived", npc.getDisplayName()).withStyle(ChatFormatting.GREEN));
        CharacterManager.markDirty(captain);
        reviveTicks = 0;
    }
}
