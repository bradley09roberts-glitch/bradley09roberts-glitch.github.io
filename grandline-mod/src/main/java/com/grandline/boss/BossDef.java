package com.grandline.boss;

import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One boss: which notable it is (see {@link com.grandline.npc.notable.NotableCharacters}), its health thresholds,
 * signature attacks per phase, arena script and rewards. Lines: {@code boss.grandline.<id>.phase2/.phase3}.
 */
public final class BossDef {
    /** What the arena does around the boss: hazards when a phase begins, periodic events while it lasts. */
    public interface Arena {
        default void onPhase(BossFight f, int phase) {}

        /** Every second of the fight (phase 1 included). */
        default void tick(BossFight f, long now) {}

        Arena NONE = new Arena() {};
    }

    public final String id;
    float[] thresholds = {0.70f, 0.35f};
    final List<BossMove> moves = new ArrayList<>();
    BossEvent.BossBarColor color = BossEvent.BossBarColor.PURPLE;
    int enrageTicks = 20 * 60 * 6;
    Arena arena = Arena.NONE;
    /** Existing abilities (buffs, forms, heals) the boss uses when a phase begins, keyed by phase. */
    final Map<Integer, List<String>> phasePowers = new HashMap<>();
    /** Extra one-off actions when a phase begins (Zoan forms, Haki buffs). */
    final Map<Integer, Consumer<BossFight>> phaseActions = new HashMap<>();
    // ---- rewards ----
    String relicItem = "fine_katana";
    float relicBonus = 4f;
    String scrollStyle = "";
    String hakiKind = "armament";
    long bounty = 100_000_000L;

    BossDef(String id) {
        this.id = id;
    }

    public List<BossMove> moves() {
        return Collections.unmodifiableList(moves);
    }

    /** The move set of a phase (1-3). */
    public List<BossMove> movesFor(int phase) {
        List<BossMove> out = new ArrayList<>();
        for (BossMove m : moves) if (m.inPhase(phase)) out.add(m);
        return out;
    }

    /** Health fraction below which the given phase ends (0 for the last phase). */
    public float threshold(int phase) {
        return phase >= 1 && phase <= thresholds.length ? thresholds[phase - 1] : 0f;
    }

    /** The phase that matches a health fraction (a fight that starts on a wounded boss picks up where it was). */
    public int phaseForHealth(float frac) {
        int p = 1;
        for (float t : thresholds) if (frac <= t) p++;
        return p;
    }

    public int phaseCount() {
        return thresholds.length + 1;
    }

    public int enrageTicks() { return enrageTicks; }
    public String relicItem() { return relicItem; }
    public String scrollStyle() { return scrollStyle; }
    public String hakiKind() { return hakiKind; }
    public long bounty() { return bounty; }
    public Arena arena() { return arena; }
    public List<String> phasePowers(int phase) { return phasePowers.getOrDefault(phase, List.of()); }
    public String relicNameKey() { return "boss.grandline.relic." + id; }
    public String phaseLineKey(int phase) { return "boss.grandline." + id + ".phase" + phase; }

    // ------------------------------------------------------------------ builder

    BossDef move(BossMove m) { moves.add(m); return this; }
    BossDef color(BossEvent.BossBarColor c) { color = c; return this; }
    BossDef thresholds(float... t) { thresholds = t; return this; }
    BossDef enrage(int seconds) { enrageTicks = seconds * 20; return this; }
    BossDef arena(Arena a) { arena = a; return this; }
    BossDef relic(String item, float bonus) { relicItem = item; relicBonus = bonus; return this; }
    BossDef scroll(String style) { scrollStyle = style; return this; }
    BossDef haki(String kind) { hakiKind = kind; return this; }
    BossDef bounty(long b) { bounty = b; return this; }
    BossDef power(int phase, String abilityId) { phasePowers.computeIfAbsent(phase, k -> new ArrayList<>()).add(abilityId); return this; }
    BossDef onPhase(int phase, Consumer<BossFight> c) { phaseActions.merge(phase, c, Consumer::andThen); return this; }
}
