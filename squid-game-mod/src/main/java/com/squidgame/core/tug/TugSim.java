package com.squidgame.core.tug;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic simulation of one tug of war heat. Humans and NPCs drive it through exactly the same calls:
 * {@link #setStance} (how hard to pull, whether to brace) and {@link #heave} (a timed burst), plus {@link #tick} once per
 * server tick. Nothing in here is random, so a heat is reproducible from its inputs.
 *
 * <h2>Model</h2>
 * <pre>
 *   member pull   = strength * effort * staminaFactor            (effort 0..1; bracing disables pulling)
 *   member anchor = strength * (0.55 + 0.45 * staminaFactor)     (only while bracing; exhausted members can still brace)
 *   heave burst   = strength * quality * HEAVE_GAIN * envelope   (quality from how close to the beat the press was)
 *   team force    = (sum pull + bursts * (1 + SYNC_BONUS * share of the team currently in a burst)) * handicap
 *   raw           = (force B - force A) / unit - SPRING * offset
 *   the team being dragged resists with its anchors: only the part of |raw| above ANCHOR_STRENGTH * anchor moves the rope
 *   offset' = offset + velocity,  velocity' = velocity + (net / weight - drag * velocity) / MASS   (drag from the difficulty)
 * </pre>
 * {@code weight} is 1 for teams of 8 and more and grows for smaller teams (see {@link TugRules#ropeWeight}).
 * Offset -1 = team A's edge (B wins when it reaches +1, A wins at -1). Forces are in units of "one average team pulling with
 * full effort" so the same numbers work for 2 v 2 and 32 v 32.
 */
public final class TugSim {
    public enum Outcome {ONGOING, A_WINS, B_WINS}

    public enum HeaveResult {
        /** Inside the window: a burst was added. */
        HIT,
        /** Pressed outside the window (or twice for one beat): the stamina is gone, nothing was gained. */
        MISTIMED,
        /** Pressed again right after the last heave: ignored, free. */
        TOO_SOON,
        /** No stamina left: ignored, free. */
        EXHAUSTED,
        /** The member is not part of the running heat. */
        INACTIVE
    }

    /** Outcome of one {@link #heave} call; {@code error} is the signed distance to the nearest beat in ticks. */
    public record HeaveReport(HeaveResult result, int error, double quality) {
    }

    public enum EventKind {EXHAUSTED, RECOVERED}

    public record Event(EventKind kind, int member) {
    }

    /** What the member is visibly doing this tick. */
    public enum Stance {REST, PULL, BRACE, SPENT}

    /** Static description of one member; {@code id} is the contestant number. */
    public record Spec(int id, int team, double strength, double endurance) {
    }

    public static final class Member {
        public final int id;
        public final int team;
        public final double strength;
        public final double endurance;
        private double stamina = 1.0;
        private boolean exhausted;
        private boolean present = true;
        private double effort;
        private boolean brace;
        private Stance stance = Stance.REST;
        private long lastHeavePress = Long.MIN_VALUE / 4;
        private long lastHeaveBeat = Long.MIN_VALUE;
        private int heavesHit;
        private int heavesMissed;

        Member(Spec s) {
            this.id = s.id();
            this.team = s.team();
            this.strength = s.strength();
            this.endurance = Math.max(0.3, s.endurance());
        }

        public double stamina() {
            return stamina;
        }

        public boolean exhausted() {
            return exhausted;
        }

        public boolean present() {
            return present;
        }

        public Stance stance() {
            return stance;
        }

        public int heavesHit() {
            return heavesHit;
        }

        public int heavesMissed() {
            return heavesMissed;
        }
    }

    private record Pulse(long start, double amplitude) {
    }

    private final TugRules.Params params;
    private final BeatClock beat;
    private final List<Member> members = new ArrayList<>();
    private final double unit;
    private final double weight;
    private final double[] handicap = {1.0, 1.0};
    private final List<List<Pulse>> pulses = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<Event> events = new ArrayList<>();
    private final double[] force = new double[2];
    private final double[] anchor = new double[2];
    private final double[] burst = new double[2];
    private final double[] present = new double[2];
    private double offset;
    private double velocity;
    private double strain;
    private long now;
    private Outcome outcome = Outcome.ONGOING;

    /**
     * @param members  every member of both teams (team {@link TugRules#TEAM_A} or {@link TugRules#TEAM_B})
     * @param handicap force multipliers per team (index = team); the smaller team of an odd heat gets a little extra
     */
    public TugSim(TugRules.Params params, BeatClock beat, List<Spec> members, double handicapA, double handicapB, long startTick) {
        this.params = params;
        this.beat = beat;
        int a = 0, b = 0;
        for (Spec s : members) {
            this.members.add(new Member(s));
            if (s.team() == TugRules.TEAM_A) {
                a++;
            } else {
                b++;
            }
        }
        this.unit = Math.max(1.0, (a + b) / 2.0);
        this.weight = TugRules.ropeWeight(unit);
        this.handicap[0] = handicapA;
        this.handicap[1] = handicapB;
        this.now = startTick;
        recountPresent();
        if (a == 0 || b == 0) {
            outcome = a == 0 ? (b == 0 ? Outcome.ONGOING : Outcome.B_WINS) : Outcome.A_WINS;
        }
    }

    // ------------------------------------------------------------------ inputs

    public int size() {
        return members.size();
    }

    public Member member(int index) {
        return members.get(index);
    }

    /** Sets how a member wants to pull: {@code effort} 0..1; {@code brace} (leaning back) overrides pulling. */
    public void setStance(int index, double effort, boolean brace) {
        Member m = members.get(index);
        m.effort = Math.max(0.0, Math.min(1.0, effort));
        m.brace = brace;
    }

    /**
     * A heave pressed at server tick {@code pressTick} (already corrected for network latency). {@code forgiveness} (ticks)
     * is subtracted from the distance to the beat before it is judged (humans get {@link TugRules#HUMAN_FORGIVENESS}).
     */
    public HeaveReport heave(int index, long pressTick, double forgiveness) {
        Member m = members.get(index);
        if (!m.present || outcome != Outcome.ONGOING) {
            return new HeaveReport(HeaveResult.INACTIVE, 0, 0);
        }
        if (pressTick - m.lastHeavePress < TugRules.MIN_HEAVE_GAP) {
            return new HeaveReport(HeaveResult.TOO_SOON, 0, 0);
        }
        double cost = TugRules.HEAVE_COST * params.costScale() / m.endurance;
        if (m.exhausted || m.stamina < cost * 0.5) {
            return new HeaveReport(HeaveResult.EXHAUSTED, 0, 0);
        }
        m.lastHeavePress = pressTick;
        // a tired member heaves weaker: the burst scales with the stamina it had before paying for it
        double fresh = 0.45 + 0.55 * TugRules.staminaFactor(m.stamina);
        m.stamina = Math.max(0.0, m.stamina - cost);
        long index0 = beat.nearestIndex(pressTick);
        int error = (int) (pressTick - beat.beatTick(index0));
        double quality = TugRules.heaveQuality(Math.max(0.0, Math.abs(error) - forgiveness), params.window());
        boolean repeat = index0 == m.lastHeaveBeat;
        m.lastHeaveBeat = index0;
        if (quality <= 0 || repeat) {
            m.heavesMissed++;
            return new HeaveReport(HeaveResult.MISTIMED, error, 0);
        }
        m.heavesHit++;
        pulses.get(m.team).add(new Pulse(now, m.strength * quality * fresh));
        return new HeaveReport(HeaveResult.HIT, error, quality);
    }

    /** A contestant left the heat (eliminated by the tournament, disconnected for good): their contribution stops. */
    public void remove(int index) {
        Member m = members.get(index);
        if (m.present) {
            m.present = false;
            m.stance = Stance.REST;
            recountPresent();
            checkForfeit();
        }
    }

    private void recountPresent() {
        present[0] = 0;
        present[1] = 0;
        for (Member m : members) {
            if (m.present) {
                present[m.team] += m.strength;
            }
        }
    }

    private void checkForfeit() {
        if (outcome != Outcome.ONGOING) {
            return;
        }
        boolean a = false, b = false;
        for (Member m : members) {
            if (m.present) {
                if (m.team == TugRules.TEAM_A) {
                    a = true;
                } else {
                    b = true;
                }
            }
        }
        if (!a && b) {
            outcome = Outcome.B_WINS;
        } else if (a && !b) {
            outcome = Outcome.A_WINS;
        }
    }

    // ------------------------------------------------------------------ step

    /** Advances the heat by one server tick; {@code serverTick} is the absolute tick this step belongs to. */
    public void tick(long serverTick) {
        now = serverTick;
        if (outcome != Outcome.ONGOING) {
            return;
        }
        double[] pull = new double[2];
        double[] anchorSum = new double[2];
        for (Member m : members) {
            if (!m.present) {
                continue;
            }
            stepMember(m, pull, anchorSum);
        }
        for (int t = 0; t < 2; t++) {
            burst[t] = burstSum(t);
            double share = present[t] <= 0 ? 0 : Math.min(1.0, burst[t] / present[t]);
            force[t] = (pull[t] + TugRules.HEAVE_GAIN * burst[t] * (1.0 + TugRules.SYNC_BONUS * share)) * handicap[t];
            anchor[t] = anchorSum[t] * handicap[t];
        }
        double raw = (force[TugRules.TEAM_B] - force[TugRules.TEAM_A]) / unit - TugRules.SPRING * offset;
        double net;
        if (raw > 0) {
            net = Math.max(0.0, raw - TugRules.ANCHOR_STRENGTH * anchor[TugRules.TEAM_A] / unit);
        } else {
            net = Math.min(0.0, raw + TugRules.ANCHOR_STRENGTH * anchor[TugRules.TEAM_B] / unit);
        }
        velocity += (net / weight - params.drag() * velocity) / TugRules.MASS;
        offset += velocity;
        updateStrain();
        if (offset >= 1.0) {
            offset = 1.0;
            velocity = 0;
            outcome = Outcome.B_WINS;
        } else if (offset <= -1.0) {
            offset = -1.0;
            velocity = 0;
            outcome = Outcome.A_WINS;
        }
    }

    private void stepMember(Member m, double[] pull, double[] anchorSum) {
        double drain = params.costScale() / m.endurance;
        boolean pulling = m.effort > 0 && !m.brace && !m.exhausted;
        if (pulling) {
            m.stamina -= TugRules.PULL_DRAIN * m.effort * drain;
            m.stance = Stance.PULL;
        } else if (m.brace) {
            if (m.exhausted) {
                m.stamina += TugRules.REST_RECOVERY * TugRules.EXHAUSTED_BRACE_RECOVERY;
                m.stance = Stance.SPENT;
            } else {
                m.stamina -= TugRules.PULL_DRAIN * TugRules.BRACE_DRAIN_FRACTION * drain;
                m.stance = Stance.BRACE;
            }
        } else {
            m.stamina += TugRules.REST_RECOVERY * (0.85 + 0.15 * m.endurance);
            m.stance = m.exhausted ? Stance.SPENT : Stance.REST;
        }
        if (m.stamina >= 1.0) {
            m.stamina = 1.0;
        }
        if (m.stamina <= 0.0) {
            m.stamina = 0.0;
            if (!m.exhausted) {
                m.exhausted = true;
                m.stance = Stance.SPENT;
                events.add(new Event(EventKind.EXHAUSTED, m.id));
            }
        } else if (m.exhausted && m.stamina >= TugRules.RECOVER_THRESHOLD) {
            m.exhausted = false;
            events.add(new Event(EventKind.RECOVERED, m.id));
        }
        double factor = TugRules.staminaFactor(m.stamina);
        if (pulling) {
            pull[m.team] += m.strength * m.effort * factor;
        } else if (m.brace) {
            anchorSum[m.team] += m.strength * (0.55 + 0.45 * factor);
        }
    }

    private double burstSum(int team) {
        List<Pulse> list = pulses.get(team);
        double sum = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Pulse p = list.get(i);
            int age = (int) (now - p.start());
            if (age >= TugRules.PULSE_TICKS) {
                list.remove(i);
            } else {
                sum += p.amplitude() * TugRules.pulseEnvelope(age);
            }
        }
        return sum;
    }

    private void updateStrain() {
        double la = (force[TugRules.TEAM_A] + TugRules.ANCHOR_STRENGTH * anchor[TugRules.TEAM_A]) / unit;
        double lb = (force[TugRules.TEAM_B] + TugRules.ANCHOR_STRENGTH * anchor[TugRules.TEAM_B]) / unit;
        double lo = Math.min(la, lb), hi = Math.max(la, lb);
        double target = hi < 0.08 ? 0.0 : Math.max(0.0, Math.min(1.0, 0.18 + 0.85 * lo + 0.15 * hi));
        strain += (target - strain) * 0.15;
    }

    // ------------------------------------------------------------------ results and queries

    public Outcome outcome() {
        return outcome;
    }

    /** Rope offset in [-1, 1]; +1 = team B has pulled the rope over its edge. */
    public double offset() {
        return offset;
    }

    /** Rope velocity in offset units per tick. */
    public double velocity() {
        return velocity;
    }

    /** How taut the rope is, 0 (slack) to 1 (humming); smoothed. */
    public double strain() {
        return strain;
    }

    /** Force of a team in the last tick, in units of one average team pulling flat out. */
    public double force(int team) {
        return force[team] / unit;
    }

    /** Share (0..1) of the team that is inside a heave burst right now: the sync of the team. */
    public double sync(int team) {
        return present[team] <= 0 ? 0 : Math.min(1.0, burst[team] / present[team]);
    }

    public double meanStamina(int team) {
        double sum = 0;
        int n = 0;
        for (Member m : members) {
            if (m.present && m.team == team) {
                sum += m.stamina;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** Fraction of the team's present members that are exhausted or visibly straining (stamina below a quarter). */
    public double strainedFraction(int team) {
        int n = 0, strained = 0;
        for (Member m : members) {
            if (m.present && m.team == team) {
                n++;
                if (m.exhausted || m.stamina < 0.25) {
                    strained++;
                }
            }
        }
        return n == 0 ? 0 : strained / (double) n;
    }

    public int presentCount(int team) {
        int n = 0;
        for (Member m : members) {
            if (m.present && m.team == team) {
                n++;
            }
        }
        return n;
    }

    /** Returns the events since the last call (exhaustion / recovery) and clears the list. */
    public List<Event> drainEvents() {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Event> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    /** Winner by rope position at the time limit; {@link Outcome#ONGOING} means an exact tie (sudden death). */
    public Outcome verdictAtTimeout() {
        if (outcome != Outcome.ONGOING) {
            return outcome;
        }
        if (Math.abs(offset) < TugRules.TIE_EPSILON) {
            return Outcome.ONGOING;
        }
        return offset > 0 ? Outcome.B_WINS : Outcome.A_WINS;
    }

    /** Tie at the timeout: everybody is fresh again for the sudden death, the rope stops. */
    public void startSuddenDeath() {
        for (Member m : members) {
            m.stamina = 1.0;
            m.exhausted = false;
        }
        for (List<Pulse> l : pulses) {
            l.clear();
        }
        velocity = 0;
    }

    public BeatClock beat() {
        return beat;
    }

    public TugRules.Params params() {
        return params;
    }

    public long now() {
        return now;
    }
}
