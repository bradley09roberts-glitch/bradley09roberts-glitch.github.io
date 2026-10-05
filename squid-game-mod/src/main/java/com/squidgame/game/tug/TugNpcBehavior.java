package com.squidgame.game.tug;

import com.squidgame.build.Marker;
import com.squidgame.core.tug.BeatClock;
import com.squidgame.core.tug.TugNpcPolicy;
import com.squidgame.core.tug.TugRules;
import com.squidgame.core.tug.TugSim;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import com.squidgame.tournament.Contestant;
import net.minecraft.world.phys.Vec3;

/**
 * How an NPC plays the Tug of War. It first marches onto its slot (the front of the line first, so nobody has to squeeze past
 * somebody who is already standing), lines up, and then pulls according to {@link TugNpcPolicy}: from what a human in the line
 * could see (the lead of the rope, how fast it moves, who visibly strains, the beat and the clock) and its own stamina and
 * personality. The pose shows what it is doing: the braced stance while pulling and resting, the heavy straining stance when
 * it runs low, a heave stroke whenever it heaves, a slip when its strength gives out, and a jolt when the rope suddenly
 * goes the wrong way.
 */
final class TugNpcBehavior implements NpcBehavior {
    private final TugOfWarGame game;
    private final Contestant contestant;
    private final int departDelay;
    private TugNpcPolicy policy;
    private boolean arrived;
    private int walking;
    private int gestureCooldown;
    private long startedAt = -1;

    TugNpcBehavior(TugOfWarGame game, Contestant contestant, int departDelay) {
        this.game = game;
        this.contestant = contestant;
        this.departDelay = departDelay;
    }

    @Override
    public void start(ContestantEntity npc) {
        arrived = false;
        walking = 0;
        policy = null;
    }

    @Override
    public void onStuck(ContestantEntity npc) {
        // last resort of the stuck recovery: walk again from where we are
        walking = Math.max(walking, 120);
    }

    @Override
    public void tick(ContestantEntity npc) {
        Marker slot = game.slotOf(contestant);
        if (slot == null) {
            return;
        }
        if (startedAt < 0) {
            startedAt = npc.level().getGameTime();
        }
        long now = npc.level().getGameTime();
        switch (game.stage()) {
            case WALK_IN -> walk(npc, slot, now);
            case COUNT_IN -> lineUp(npc, slot, now);
            case MATCH -> pull(npc, slot, now);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ walking onto the platform

    private void walk(ContestantEntity npc, Marker slot, long now) {
        if (arrived) {
            hold(npc, slot);
            return;
        }
        if (now - game.stageSince() < departDelay) {
            npc.stopMoving();
            return;
        }
        Vec3 target = new Vec3(slot.x(), slot.y(), slot.z());
        double dx = npc.getX() - target.x;
        double dz = npc.getZ() - target.z;
        if (dx * dx + dz * dz < 0.8 * 0.8 && Math.abs(npc.getY() - target.y) < 1.5) {
            arrive(npc, slot);
            return;
        }
        walking++;
        if (walking > 20 * 22) {
            // it cannot get there (stuck behind others, lost): put it in its place
            npc.teleportSafely(target);
            arrive(npc, slot);
            return;
        }
        if (!npc.isTryingToMove() || walking % 40 == 0) {
            if (!npc.moveToward(target, 1.2)) {
                npc.moveDirect(target, 1.1);
            }
        }
    }

    private void arrive(ContestantEntity npc, Marker slot) {
        arrived = true;
        npc.stopMoving();
        npc.faceYaw(slot.yaw());
        npc.setActivity(Activity.ATTENTION);
        game.onArrived(contestant);
    }

    /** Stays on the slot: a gentle correction when somebody bumped into it. */
    private void hold(ContestantEntity npc, Marker slot) {
        Vec3 target = new Vec3(slot.x(), slot.y(), slot.z());
        double dx = npc.getX() - target.x;
        double dz = npc.getZ() - target.z;
        if (dx * dx + dz * dz > 0.45 * 0.45) {
            npc.moveDirect(target, 0.35);
        } else {
            npc.stopMoving();
        }
        npc.faceYaw(slot.yaw());
    }

    // ------------------------------------------------------------------ count-in

    private void lineUp(ContestantEntity npc, Marker slot, long now) {
        if (!arrived) {
            npc.teleportSafely(new Vec3(slot.x(), slot.y(), slot.z()));
            arrive(npc, slot);
        }
        hold(npc, slot);
        BeatClock beat = game.beat();
        // the last two beats before the GO: take hold of the rope
        if (beat != null && now >= beat.beatTick(-2) - 4) {
            npc.setActivity(Activity.PULL_IDLE);
        }
    }

    // ------------------------------------------------------------------ pulling

    private void pull(ContestantEntity npc, Marker slot, long now) {
        TugSim.Member m = game.memberOf(contestant);
        BeatClock beat = game.beat();
        if (m == null || beat == null || !m.present()) {
            return;
        }
        if (policy == null) {
            policy = new TugNpcPolicy(contestant.personality, game.skillBonus(contestant), npc.rng().fork(contestant.number * 31L + 7),
                    game.window());
        }
        hold(npc, slot);
        int team = game.teamOf(contestant);
        net.minecraft.world.level.Level level = npc.level();
        long tick = level.getGameTime();
        TugNpcPolicy.View view = new TugNpcPolicy.View(tick, game.leadOf(contestant), m.stamina(), m.exhausted(), game.strained(team),
                game.strained(1 - team), game.timeLeftFraction(tick), beat, game.suddenDeath());
        TugNpcPolicy.Action a = policy.decide(view);
        game.npcStance(contestant, a.effort(), a.brace());
        if (gestureCooldown > 0) {
            gestureCooldown--;
        }
        if (a.pressTick() >= 0) {
            TugSim.HeaveReport r = game.npcHeave(contestant, a.pressTick());
            if ((r.result() == TugSim.HeaveResult.HIT || r.result() == TugSim.HeaveResult.MISTIMED) && gestureCooldown == 0) {
                npc.triggerAction("pull_heave");
                gestureCooldown = 8;
            }
        }
        // what the others see: straining when the strength runs low (or has given out), otherwise the braced stance
        boolean strained = m.stance() == TugSim.Stance.SPENT || (m.stance() == TugSim.Stance.PULL && m.stamina() < 0.3);
        npc.setActivity(strained ? Activity.PULL_STRAIN : Activity.PULL_IDLE);
        switch (policy.takeSwing()) {
            case JOLT -> {
                if (gestureCooldown == 0) {
                    npc.triggerAction("pull_slip");
                    gestureCooldown = 20;
                }
            }
            case RALLY -> {
                if (gestureCooldown == 0) {
                    npc.triggerAction("pull_heave");
                    gestureCooldown = 12;
                }
            }
            default -> {
            }
        }
    }
}
