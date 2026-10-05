package com.squidgame.game.tug;

import com.squidgame.core.tug.BeatClock;
import com.squidgame.core.tug.TugRules;
import com.squidgame.core.tug.TugSim;
import com.squidgame.tournament.Contestant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One heat: the two teams in slot order (index 0 = nearest the gap), who is who in the simulation, the rhythm and the clock. */
final class TugHeat {
    final int index;
    final List<List<Contestant>> teams = List.of(new ArrayList<>(), new ArrayList<>());
    final double[] handicap = {1.0, 1.0};
    private final Map<Integer, Integer> teamOf = new HashMap<>();
    private final Map<Integer, Integer> slotOf = new HashMap<>();
    private final Map<Integer, Integer> simIndex = new HashMap<>();

    @Nullable
    TugSim sim;
    @Nullable
    BeatClock beat;
    long goTick;
    /** Tick at which the current time limit (the heat, or the sudden death after it) runs out, and how long that period is. */
    long limitTick;
    long limitSpan = 1;
    boolean suddenDeath;
    int winner = -1;
    boolean timedOut;
    boolean coinFlip;

    TugHeat(int index) {
        this.index = index;
    }

    void add(int team, Contestant c) {
        List<Contestant> list = teams.get(team);
        teamOf.put(c.number, team);
        slotOf.put(c.number, list.size());
        list.add(c);
    }

    /** The team (A or B) of a contestant in this heat, or -1. */
    int team(Contestant c) {
        return teamOf.getOrDefault(c.number, -1);
    }

    boolean contains(Contestant c) {
        return teamOf.containsKey(c.number);
    }

    /** Position in the line of the team: 0 = nearest the gap. */
    int slot(Contestant c) {
        return slotOf.getOrDefault(c.number, 0);
    }

    int size() {
        return teams.get(0).size() + teams.get(1).size();
    }

    boolean complete() {
        return !teams.get(TugRules.TEAM_A).isEmpty() && !teams.get(TugRules.TEAM_B).isEmpty();
    }

    /** Creates the simulation; the member index of a contestant is its order in {@code specs}. */
    void startSim(TugRules.Params params, BeatClock clock, List<TugSim.Spec> specs) {
        this.beat = clock;
        this.goTick = clock.epoch;
        simIndex.clear();
        for (int i = 0; i < specs.size(); i++) {
            simIndex.put(specs.get(i).id(), i);
        }
        this.sim = new TugSim(params, clock, specs, handicap[TugRules.TEAM_A], handicap[TugRules.TEAM_B], clock.epoch);
    }

    /** Index of the contestant in the simulation, or -1 before the simulation exists. */
    int indexOf(Contestant c) {
        return sim == null ? -1 : simIndex.getOrDefault(c.number, -1);
    }

    @Nullable
    TugSim.Member member(Contestant c) {
        int i = indexOf(c);
        return i < 0 || sim == null ? null : sim.member(i);
    }

    /** Every member of both teams (team A first). */
    List<Contestant> everyone() {
        List<Contestant> all = new ArrayList<>(size());
        all.addAll(teams.get(TugRules.TEAM_A));
        all.addAll(teams.get(TugRules.TEAM_B));
        return all;
    }
}
