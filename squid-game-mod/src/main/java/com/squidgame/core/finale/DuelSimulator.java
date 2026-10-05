package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a whole duel between two {@link NpcBrain}s without any world: the {@link Duel} rule engine, the same
 * {@link Motion} the live NPC bodies use and no entities. The live game uses it to settle duels nobody watches
 * (with a visible ceremony), and the unit tests use it to check that NPC fights are fair and varied.
 */
public final class DuelSimulator {
    /** An event of the simulated duel with the tick it happened in (the game replays them in a ceremony). */
    public record Timed(int tick, CombatEvent event) {
    }

    /**
     * Final result of a simulated duel. {@code track} holds the pose of both fighters after every tick, six floats per
     * tick ({@code x0 z0 yaw0 x1 z1 yaw1}), starting with the poses before the first tick: the game replays a duel that
     * nobody fights live from it.
     */
    public record Result(Duel.Outcome outcome, Duel.Summary[] summary, Role[] roles, List<Timed> log, float[] track) {
        public int ticks() {
            return outcome.tick();
        }

        public Role winnerRole() {
            return roles[outcome.winner()];
        }

        /** Number of recorded poses (ticks + 1). */
        public int poses() {
            return track.length / 6;
        }

        public double x(int slot, int tick) {
            return track[6 * clampTick(tick) + 3 * slot];
        }

        public double z(int slot, int tick) {
            return track[6 * clampTick(tick) + 3 * slot + 1];
        }

        public double yaw(int slot, int tick) {
            return track[6 * clampTick(tick) + 3 * slot + 2];
        }

        private int clampTick(int tick) {
            return Math.max(0, Math.min(poses() - 1, tick));
        }
    }

    private final Duel duel;
    private final NpcBrain[] brain = new NpcBrain[2];
    private final Motion[] motion = {new Motion(), new Motion()};
    private final boolean[] attackDown = new boolean[2];
    private final double[] x = new double[2], z = new double[2], yaw = new double[2];

    public DuelSimulator(Difficulty difficulty, CourtGeometry court, Personality p0, Personality p1, Role roleOfSlot0, long seed) {
        FinaleRules.Params params = FinaleRules.params(difficulty);
        Rng rng = new Rng(seed);
        this.duel = new Duel(params, court, roleOfSlot0, rng.fork(1));
        brain[0] = new NpcBrain(0, p0, difficulty, params, court, rng.fork(2));
        brain[1] = new NpcBrain(1, p1, difficulty, params, court, rng.fork(3));
        for (int s = 0; s < 2; s++) {
            FighterView v = duel.view(s);
            x[s] = v.x();
            z[s] = v.z();
            yaw[s] = v.yaw();
        }
    }

    public Duel duel() {
        return duel;
    }

    public NpcBrain brain(int slot) {
        return brain[slot];
    }

    /** Position of a simulated fighter. */
    public double[] position(int slot) {
        return new double[]{x[slot], z[slot]};
    }

    /** Advances one tick; returns the events of the tick. */
    public List<CombatEvent> step() {
        NpcBrain.Intent[] in = new NpcBrain.Intent[2];
        for (int s = 0; s < 2; s++) {
            in[s] = brain[s].think(duel);
            apply(s, in[s]);
        }
        List<CombatEvent> events = duel.step();
        for (CombatEvent e : events) {
            if (e.type() == CombatEvent.Type.DODGE) {
                motion[e.actor()].dash(e.ix(), e.iz(), (int) e.amount());
            } else if (e.hasImpulse()) {
                motion[e.impulseSlot()].impulse(e.ix(), e.iz());
            }
        }
        for (int s = 0; s < 2; s++) {
            move(s, in[s]);
        }
        separate();
        for (int s = 0; s < 2; s++) {
            duel.setPose(s, x[s], z[s], yaw[s]);
        }
        return events;
    }

    /** Plays the duel to its end (a timeout at the latest) and returns the outcome with the log of every event. */
    public Result run() {
        List<Timed> log = new ArrayList<>();
        int limit = duel.params().duelTicks() + 10;
        float[] track = new float[6 * (limit + 2)];
        record(track, 0);
        int recorded = 1;
        for (int i = 0; i < limit && !duel.finished(); i++) {
            for (CombatEvent e : step()) {
                log.add(new Timed(duel.tick(), e));
            }
            record(track, recorded++);
        }
        if (!duel.finished()) {
            duel.timeUp();
        }
        return new Result(duel.outcome(), new Duel.Summary[]{duel.summary(0), duel.summary(1)},
                new Role[]{duel.role(0), duel.role(1)}, log, java.util.Arrays.copyOf(track, 6 * recorded));
    }

    private void record(float[] track, int index) {
        for (int s = 0; s < 2; s++) {
            track[6 * index + 3 * s] = (float) x[s];
            track[6 * index + 3 * s + 1] = (float) z[s];
            track[6 * index + 3 * s + 2] = (float) yaw[s];
        }
    }

    /** The buttons: guard first (it cancels a hold), then the attack edges, then shove and dodge. */
    private void apply(int s, NpcBrain.Intent in) {
        duel.guard(s, in.guard());
        if (in.attackHeld() && !attackDown[s]) {
            duel.attackDown(s);
        } else if (!in.attackHeld() && attackDown[s]) {
            duel.attackUp(s);
        }
        attackDown[s] = in.attackHeld() && !in.guard();
        if (in.shove()) {
            duel.shove(s);
        }
        if (in.dodge()) {
            duel.dodge(s, in.dodgeX(), in.dodgeZ());
        }
        boolean sprint = in.speed() > 1.05 && duel.canSprint(s);
        duel.setSprinting(s, sprint);
        yaw[s] = in.yaw();
    }

    private void move(int s, NpcBrain.Intent in) {
        double speed = in.speed();
        if (speed > 1.05 && !duel.canSprint(s)) {
            speed = 1.0;
        }
        double mag = FinaleRules.WALK * speed * duel.speedFactor(s);
        double wx = in.dirX() * mag, wz = in.dirZ() * mag;
        double[] dash = motion[s].dashVelocity();
        if (dash != null && duel.court().edgeDistance(x[s] + dash[0], z[s] + dash[1]) < FinaleRules.DASH_EDGE) {
            motion[s].cancelDash();     // a dodge never carries a fighter over the line
        }
        double[] d = motion[s].step(wx, wz);
        x[s] += d[0];
        z[s] += d[1];
    }

    /** Bodies do not overlap. */
    private void separate() {
        double dx = x[1] - x[0], dz = z[1] - z[0];
        double dist = Math.hypot(dx, dz);
        double min = FinaleRules.BODY_RADIUS * 2;
        if (dist < min) {
            double nx = dist < 1e-6 ? 1 : dx / dist, nz = dist < 1e-6 ? 0 : dz / dist;
            double push = (min - dist) / 2;
            x[0] -= nx * push;
            z[0] -= nz * push;
            x[1] += nx * push;
            z[1] += nz * push;
        }
    }
}
