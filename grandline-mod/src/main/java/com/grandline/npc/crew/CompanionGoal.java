package com.grandline.npc.crew;

import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Cover;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.net.GLNetwork;
import com.grandline.npc.AttackSlots;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Knockdown;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * An active companion's whole behaviour while its captain is near (registered before the generic goals, so a
 * companion never surrenders, wanders off home or fights with the generic AI). Every blow, shot and technique goes
 * through the shared combat system (CombatManager / Hits), so line of effect, guard, dodge and the friendly-fire rules
 * apply exactly as for everyone else.
 * <p>
 * In order of precedence each tick: kneel while downed; sing (support: heal pulse, Encore); pull a knocked-down
 * captain up (and, as support, a downed crewmate); regroup when ordered; fight the target the targeting goal chose
 * ({@link CompanionTargetGoal}) in the role's way; hold the posted position; otherwise keep formation (fighter beside
 * and ahead of the captain, marksman behind, support close). A companion that is far away and out of sight, or stuck
 * for several seconds, is brought next to the captain (never while it is fighting); it opens wooden doors in its way.
 */
public class CompanionGoal extends Goal {
    private static final double HOLD_SLACK = 2.0;
    /** No technique in the first 8 s of a fight (Pinning Shot: 5 s). */
    static final int OPENING = 160;
    private final HumanoidNpc npc;
    private ServerPlayer cap;
    private CrewManager.Member m;
    private int repath, sightCheck, strafeTicks, strafeDir = 1, reviveTicks, mateTicks, stuckChecks;
    private boolean canStrike, sees, seesCaptain;
    private long lastSeen, nextProgressCheck, closeDoorAt;
    private double bestDist = Double.MAX_VALUE;
    private BlockPos openedDoor;
    private LivingEntity lastTarget;
    private HumanoidNpc mate;
    private int mateScan;
    private Vec3 lastCapPos, heading;

    public CompanionGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.level().isClientSide || npc.notableId().isEmpty() || npc.isPassenger()) return false;
        m = CrewManager.memberOf(npc);
        if (m == null || !m.active) return false;
        cap = CrewManager.captainOf(npc);
        return cap != null && cap.level() == npc.level();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
        sightCheck = 0;
        stuckChecks = 0;
        bestDist = Double.MAX_VALUE;
        nextProgressCheck = npc.level().getGameTime() + 40;
        lastCapPos = heading = null;
        if (npc.getNavigation() instanceof GroundPathNavigation g) g.setCanOpenDoors(true);
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.setAggressive(false);
        CombatState.of(npc).blocking = false;
        if (lastTarget != null) AttackSlots.release(lastTarget, npc);
        lastTarget = null;
        reviveTicks = mateTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (m == null || cap == null) return;
        long now = npc.level().getGameTime();
        if (m.downed) {
            npc.getNavigation().stop();
            npc.setAggressive(false);
            return;
        }
        doors(now);
        CompanionRole role = CompanionRole.of(npc);
        if (role == CompanionRole.SUPPORT) {
            CompanionCare.trySupportHeal(npc, cap, now);
            CompanionCare.tryEncore(npc, cap, now);
        }
        if (reviveCaptain(role)) return;
        reviveTicks = 0;
        if (role == CompanionRole.SUPPORT && reviveCrewmate()) return;
        mateTicks = 0;
        if (now < m.regroupUntil) {
            regroup();
            return;
        }
        LivingEntity t = npc.getTarget();
        if (t != lastTarget) {
            if (lastTarget != null) AttackSlots.release(lastTarget, npc);
            lastTarget = t;
            lastSeen = now;
            sightCheck = 0;
        }
        npc.setAggressive(t != null);
        if (t != null && t.isAlive()) {
            fight(t, role, now);
            return;
        }
        if (m.holding()) hold();
        else follow(role, now);
    }

    // ================================================================== movement

    /**
     * The way the captain is heading: the direction of their last few steps (looking around does not reshuffle the
     * formation), or where they face when they have not moved yet.
     */
    private Vec3 heading() {
        Vec3 p = cap.position();
        if (lastCapPos == null || heading == null) {
            float yaw = cap.getYRot() * Mth.DEG_TO_RAD;
            heading = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            lastCapPos = p;
        }
        Vec3 mv = new Vec3(p.x - lastCapPos.x, 0, p.z - lastCapPos.z);
        if (mv.lengthSqr() > 1.5 * 1.5) {
            heading = mv.normalize();
            lastCapPos = p;
        }
        return heading;
    }

    /** Where this companion stands when nothing is happening: fighter beside and ahead, marksman behind, support close. */
    Vec3 formation(CompanionRole role) {
        Vec3 fwd = heading();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        int side = Companions.slot(cap, npc) == 0 ? 1 : -1;
        Vec3 c = cap.position();
        return switch (role) {
            case FIGHTER -> c.add(fwd.scale(2.0)).add(right.scale(1.6 * side));
            case RANGED -> c.add(fwd.scale(-3.0)).add(right.scale(1.6 * side));
            case SUPPORT -> c.add(fwd.scale(-1.5)).add(right.scale(1.2 * side));
        };
    }

    private void follow(CompanionRole role, long now) {
        double d = npc.distanceTo(cap);
        if (--sightCheck <= 0) {
            sightCheck = 10;
            seesCaptain = Cover.canSee(npc, cap);
        }
        if (lostOrStuck(d, now)) {
            if (Companions.teleportNear(npc, cap)) {
                stuckChecks = 0;
                bestDist = Double.MAX_VALUE;
            }
            return;
        }
        Vec3 spot = cap.isPassenger() ? cap.position() : formation(role);
        double ds = Math.sqrt(npc.distanceToSqr(spot.x, npc.getY(), spot.z));
        if (ds > 2.0 || d > 6) {
            if (--repath <= 0) {
                repath = 10;
                double speed = d > 10 ? 1.25 : 1.0;
                if (!npc.getNavigation().moveTo(spot.x, spot.y, spot.z, speed)) npc.getNavigation().moveTo(cap, speed);
            }
        } else {
            npc.getNavigation().stop();
            Vec3 lookAt = cap.getEyePosition().add(cap.getLookAngle().scale(8));
            npc.getLookControl().setLookAt(lookAt.x, lookAt.y, lookAt.z);
        }
    }

    /**
     * Far (over 20 blocks) and out of the captain's sight, or no progress towards the captain for about six seconds:
     * time to be brought over. Only when the captain stands on solid ground, and never mid-fight.
     */
    private boolean lostOrStuck(double d, long now) {
        if (!CrewManager.onSolidGround(cap) || npc.getTarget() != null) return false;
        if (d > 20 && !seesCaptain) return true;
        if (now >= nextProgressCheck) {
            nextProgressCheck = now + 40;
            if (d > 6 && d > bestDist - 0.5) stuckChecks++;
            else stuckChecks = 0;
            bestDist = d < 4 ? Double.MAX_VALUE : Math.min(bestDist, d);
            if (stuckChecks >= 3 && (d > 10 || !seesCaptain)) return true;
        }
        return false;
    }

    private void regroup() {
        double d = npc.distanceTo(cap);
        if (d < 3) {
            m.regroupUntil = 0;
            npc.getNavigation().stop();
            return;
        }
        if (--sightCheck <= 0) {
            sightCheck = 10;
            seesCaptain = Cover.canSee(npc, cap);
        }
        if (d > 16 && !seesCaptain && CrewManager.onSolidGround(cap) && Companions.teleportNear(npc, cap)) {
            m.regroupUntil = 0;
            return;
        }
        npc.getLookControl().setLookAt(cap, 30, 30);
        if (--repath <= 0) {
            repath = 8;
            npc.getNavigation().moveTo(cap, 1.3);
        }
    }

    private void hold() {
        BlockPos hp = m.holdPos();
        if (hp == null) {
            m.hold = npc.blockPosition().asLong();
            return;
        }
        if (npc.distanceToSqr(Vec3.atBottomCenterOf(hp)) > HOLD_SLACK * HOLD_SLACK) {
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(hp.getX() + 0.5, hp.getY(), hp.getZ() + 0.5, 1.0);
            }
        } else {
            npc.getNavigation().stop();
            if (npc.getRandom().nextInt(60) == 0) npc.getLookControl().setLookAt(cap, 30, 30);
        }
    }

    /** Open a closed wooden door that is the next step of the path; close it again behind. */
    private void doors(long now) {
        if (openedDoor != null && now >= closeDoorAt && npc.blockPosition().distSqr(openedDoor) > 4) {
            BlockState s = npc.level().getBlockState(openedDoor);
            if (s.getBlock() instanceof DoorBlock door && door.isOpen(s)) door.setOpen(npc, npc.level(), s, openedDoor, false);
            openedDoor = null;
        }
        Path p = npc.getNavigation().getPath();
        if (p == null || p.isDone()) return;
        for (int i = p.getNextNodeIndex(); i < Math.min(p.getNodeCount(), p.getNextNodeIndex() + 2); i++) {
            BlockPos pos = p.getNodePos(i);
            if (npc.blockPosition().distSqr(pos) > 5) continue;
            for (BlockPos q : new BlockPos[]{pos, pos.above()}) {
                BlockState s = npc.level().getBlockState(q);
                if (s.getBlock() instanceof DoorBlock door && DoorBlock.isWoodenDoor(s) && !door.isOpen(s)) {
                    door.setOpen(npc, npc.level(), s, q, true);
                    openedDoor = q;
                    closeDoorAt = now + 40;
                    return;
                }
            }
        }
    }

    // ================================================================== reviving

    /** Pull a knocked-down captain up. Fighters finish the foe right on them first; supports drop everything. */
    private boolean reviveCaptain(CompanionRole role) {
        CharacterData d = Characters.get(cap);
        if (!d.isKnockedDown()) return false;
        LivingEntity t = npc.getTarget();
        if (t != null && role != CompanionRole.SUPPORT && npc.distanceToSqr(t) < 4 * 4) return false;
        npc.getLookControl().setLookAt(cap, 30, 30);
        if (npc.distanceToSqr(cap) > 2.5 * 2.5) {
            if (--repath <= 0) {
                repath = 8;
                npc.getNavigation().moveTo(cap, 1.25);
            }
            reviveTicks = 0;
            return true;
        }
        npc.getNavigation().stop();
        if (++reviveTicks >= (role == CompanionRole.SUPPORT ? CompanionCare.SUPPORT_REVIVE_TICKS : CompanionCare.REVIVE_TICKS)) {
            Knockdown.standUp(cap, d, 0.35f);
            cap.sendSystemMessage(Component.translatable("grandline.knockdown.revived", npc.getDisplayName()).withStyle(ChatFormatting.GREEN));
            CharacterManager.markDirty(cap);
            reviveTicks = 0;
        }
        return true;
    }

    /** Support: get a downed crewmate back up (twice as fast as the captain can), unless something is attacking us. */
    private boolean reviveCrewmate() {
        if (mate == null || !mate.isAlive() || !CompanionCare.isDowned(mate)) {
            mate = null;
            if (--mateScan > 0) return false;
            mateScan = 10;
            double bd = 16 * 16;
            for (HumanoidNpc b : Companions.activeBodies(cap)) {
                if (b == npc || !CompanionCare.isDowned(b)) continue;
                double dd = b.distanceToSqr(npc);
                if (dd < bd) {
                    bd = dd;
                    mate = b;
                }
            }
            if (mate == null) return false;
        }
        HumanoidNpc down = mate;
        double bd = down.distanceToSqr(npc);
        if (!npc.level().getEntitiesOfClass(Mob.class, npc.getBoundingBox().inflate(5), x -> x.getTarget() == npc).isEmpty()) return false;
        npc.getLookControl().setLookAt(down, 30, 30);
        if (bd > 2.2 * 2.2) {
            if (--repath <= 0) {
                repath = 8;
                npc.getNavigation().moveTo(down, 1.25);
            }
            mateTicks = 0;
            return true;
        }
        npc.getNavigation().stop();
        if (++mateTicks >= CompanionCare.SUPPORT_REVIVE_TICKS) {
            CrewManager.Member dm = CrewManager.memberOf(down);
            if (dm != null) CompanionCare.standUp(down, dm, CompanionCare.REVIVED_HEALTH, true);
            mateTicks = 0;
        }
        return true;
    }

    // ================================================================== fighting

    private void fight(LivingEntity t, CompanionRole role, long now) {
        if (--sightCheck <= 0) {
            sightCheck = 4;
            canStrike = Cover.reachable(npc, t);
            sees = canStrike || npc.getSensing().hasLineOfSight(t);
            if (sees) lastSeen = now;
        }
        if (now - lastSeen > 100) { // lost it: back to the captain
            npc.setTarget(null);
            return;
        }
        npc.getLookControl().setLookAt(t, 30, 30);
        double dist = npc.distanceTo(t);
        WeaponClass wc = CombatManager.weaponOf(npc.getMainHandItem());
        boolean ranged = wc != null && wc.ranged;
        double reach = 2.4 * Math.max(0.7, npc.getScale()) + (wc == WeaponClass.SPEAR || wc == WeaponClass.NAGINATA ? 1.5 : 0);
        roleTechnique(role, t, dist, now);
        technique(t, dist, ranged, now);
        switch (role) {
            case RANGED -> {
                if (ranged) rangedMove(t, dist);
                else meleeMove(t, dist, reach, now);
            }
            case SUPPORT -> supportMove(t, dist, reach, ranged, now);
            default -> meleeMove(t, dist, reach, now);
        }
        strike(t, dist, reach, ranged, role, now);
    }

    /** Keep 6-12 blocks back, behind the captain when that works; move for a clear line instead of shooting walls. */
    private void rangedMove(LivingEntity t, double dist) {
        if (!canStrike) {
            if (--strafeTicks <= 0) {
                strafeTicks = 40;
                strafeDir = -strafeDir;
            }
            if (--repath <= 0) {
                repath = 10;
                Vec3 to = t.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
                Vec3 side = new Vec3(-to.z, 0, to.x).scale(3 * strafeDir);
                Vec3 p = npc.position().add(side);
                if (!npc.getNavigation().moveTo(p.x, p.y, p.z, 1.1)) npc.getNavigation().moveTo(t, 1.0);
            }
            return;
        }
        if (dist < 6) {
            moveAway(t, 8 - dist);
            return;
        }
        if (dist > 12) {
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(t, 1.0);
            }
            return;
        }
        Vec3 behind = cap.position().add(cap.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(3));
        double bt = behind.distanceTo(t.position());
        if (bt >= 6 && bt <= 13 && npc.position().distanceTo(behind) > 3) {
            if (--repath <= 0) {
                repath = 15;
                npc.getNavigation().moveTo(behind.x, behind.y, behind.z, 1.0);
            }
        } else npc.getNavigation().stop();
    }

    /** Close in on a melee slot (fair: only a couple press one foe at once), otherwise circle a few steps out. */
    private void meleeMove(LivingEntity t, double dist, double reach, long now) {
        if (!AttackSlots.claim(t, npc, now)) {
            if (dist < 3) moveAway(t, 2);
            else if (dist > 5.5) path(t, 1.05);
            else {
                npc.getNavigation().stop();
                strafe();
            }
            return;
        }
        if (dist > reach + 0.3 || !canStrike) path(t, dist > 8 ? 1.2 : 1.05);
        else {
            npc.getNavigation().stop();
            strafe();
        }
    }

    /** Stay at the captain's side; only deal with what comes close (or shoot, with a ranged weapon). */
    private void supportMove(LivingEntity t, double dist, double reach, boolean ranged, long now) {
        double dc = npc.distanceTo(cap);
        if (dc > 5) {
            if (--repath <= 0) {
                repath = 8;
                npc.getNavigation().moveTo(cap, 1.15);
            }
            return;
        }
        if (!ranged && dist < 4.5) meleeMove(t, dist, reach, now);
        else npc.getNavigation().stop();
    }

    private void strike(LivingEntity t, double dist, double reach, boolean ranged, CompanionRole role, long now) {
        CombatState s = CombatState.of(npc);
        if (!canStrike || now < s.nextAttackAllowed || s.blocking) return;
        if (ranged) {
            if (dist <= (role == CompanionRole.SUPPORT ? 14 : 18)) {
                face(t);
                CombatManager.fireRanged(npc, npc.getMainHandItem(), false);
            }
            return;
        }
        if (dist <= reach + 0.3) {
            face(t);
            boolean heavy = npc.getRandom().nextFloat() < 0.15f;
            CombatManager.attack(npc, heavy, heavy ? 6 : 0);
        }
    }

    /**
     * Techniques, sparingly: never in the first {@link #OPENING} ticks of a fight (companions open with ordinary blows,
     * so a short fight stays the captain's), then one every 12-18 s at most, only with a clear line and in range.
     */
    private void technique(LivingEntity t, double dist, boolean ranged, long now) {
        if (now < m.techniqueReadyAt || !canStrike || dist > (ranged ? 16 : 6) || Companions.fightAge(cap, now) < OPENING) return;
        CombatState s = CombatState.of(npc);
        List<Ability> list = new ArrayList<>();
        for (Ability a : npc.techniques()) {
            String id = a.id();
            if (id.endsWith("/toggle") || id.endsWith("/transform") || id.startsWith("haki/conqueror") || a.awakened() || s.onCooldown(id, now)) continue;
            list.add(a);
        }
        if (list.isEmpty()) {
            m.techniqueReadyAt = now + 100;
            return;
        }
        face(t);
        Ability a = list.get(npc.getRandom().nextInt(list.size()));
        m.techniqueReadyAt = CombatManager.useAbility(npc, a, t) ? now + 240 + npc.getRandom().nextInt(120) : now + 40;
    }

    /** Bond rank 3: the role's own technique. Support's Encore lives in CompanionCare. */
    private void roleTechnique(CompanionRole role, LivingEntity t, double dist, long now) {
        if (m.bondRank() < 3 || now < m.roleTechReadyAt) return;
        if (role == CompanionRole.FIGHTER) {
            // Rallying Challenge: foes going for the captain turn on the fighter instead
            List<Mob> onCaptain = npc.level().getEntitiesOfClass(Mob.class, cap.getBoundingBox().inflate(8), x -> x.isAlive() && x.getTarget() == cap);
            if (onCaptain.isEmpty()) return;
            int n = 0;
            for (Mob mob : onCaptain) {
                if (n++ >= 3) break;
                mob.setTarget(npc);
            }
            GLNetwork.anim(npc, "howl");
            npc.level().playSound(null, npc.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 0.6f, 1.4f);
            cap.displayClientMessage(Component.translatable("grandline.crew.challenge", npc.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            m.roleTechReadyAt = now + 400;
        } else if (role == CompanionRole.RANGED && canStrike && dist <= 16 && Companions.fightAge(cap, now) >= OPENING / 2) {
            // Pinning Shot: a heavier shot that staggers and slows (through the shared hit code: cover applies)
            face(t);
            Hits.Result r = Hits.hit(npc, t, Hits.power(npc) * 1.5f, Hits.Info.of(Element.PIERCE, 0.2f).withStun(20));
            if (r == Hits.Result.HIT) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            GLNetwork.anim(npc, "aim");
            npc.level().playSound(null, npc.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 1f, 0.8f);
            m.roleTechReadyAt = now + (r == Hits.Result.COVERED ? 40 : 300);
        }
    }

    private void face(LivingEntity t) {
        Vec3 to = t.getBoundingBox().getCenter().subtract(npc.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG));
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.yBodyRot = yaw;
        npc.setXRot(pitch);
    }

    private void path(LivingEntity t, double speed) {
        if (--repath <= 0) {
            repath = 8;
            npc.getNavigation().moveTo(t, speed);
        }
    }

    private void strafe() {
        if (--strafeTicks <= 0) {
            strafeTicks = 20 + npc.getRandom().nextInt(30);
            strafeDir = npc.getRandom().nextBoolean() ? 1 : -1;
        }
        npc.getMoveControl().strafe(-0.1f, 0.3f * strafeDir);
    }

    private void moveAway(LivingEntity t, double by) {
        Vec3 away = npc.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(Math.max(2, by));
        if (--repath <= 0) {
            repath = 6;
            npc.getNavigation().moveTo(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z, 1.15);
        }
    }
}
