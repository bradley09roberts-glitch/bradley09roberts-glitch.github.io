package com.squidgame.game.bridge;

import com.squidgame.core.Difficulty;
import com.squidgame.core.bridge.BridgeKnowledge;
import com.squidgame.core.bridge.BridgeLayout;
import com.squidgame.core.bridge.BridgeQueue;
import com.squidgame.core.bridge.BridgeRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Everything an NPC may know about the bridge game, i.e. what a contestant standing in the hall could see: the layout,
 * who stands where, which panels are gone, what has publicly been revealed (the {@link BridgeKnowledge} log), the
 * queue order and the gate. It deliberately holds no reference to the hidden route and offers no way to ask whether a
 * panel is safe: that exists only inside {@link GlassBridgeGame}.
 */
final class BridgePublicView {
    private final PanelField field;
    private final BridgeKnowledge knowledge;
    private final BridgeQueue queue;
    private final BridgeRules.Params params;
    private final Difficulty difficulty;
    private final Map<Integer, Crosser> crossers;
    private final LongSupplier clock;
    private final Vec3 staging;
    private final List<Vec3> finishSpots;
    private final BridgeLayout.Rect startBounds;
    private final BridgeLayout.Rect finishBounds;
    boolean gateOpen;
    long gateOpenedAt;

    BridgePublicView(PanelField field, BridgeKnowledge knowledge, BridgeQueue queue, BridgeRules.Params params, Difficulty difficulty,
                     Map<Integer, Crosser> crossers, LongSupplier clock, Vec3 staging, List<Vec3> finishSpots) {
        this.field = field;
        this.knowledge = knowledge;
        this.queue = queue;
        this.params = params;
        this.difficulty = difficulty;
        this.crossers = crossers;
        this.clock = clock;
        this.staging = staging;
        this.finishSpots = finishSpots;
        this.startBounds = field.layout.start().inset(0.7);
        this.finishBounds = field.layout.finish().inset(0.7);
    }

    // ------------------------------------------------------------------ the hall

    BridgeLayout layout() {
        return field.layout;
    }

    double topY() {
        return field.topY;
    }

    int rows() {
        return field.rows();
    }

    BridgeRules.Params params() {
        return params;
    }

    Difficulty difficulty() {
        return difficulty;
    }

    long now() {
        return clock.getAsLong();
    }

    BridgeLayout.Rect startBounds() {
        return startBounds;
    }

    BridgeLayout.Rect finishBounds() {
        return finishBounds;
    }

    /** Where the contestant who is next in line waits, just behind the gate. */
    Vec3 staging() {
        return staging;
    }

    /** A spot on the far platform for the contestant who finished with this rank (1-based). */
    Vec3 finishSpot(int rank) {
        if (finishSpots.isEmpty()) {
            return new Vec3(field.layout.finish().centerX(), field.topY, field.layout.finish().minZ() + 3);
        }
        return finishSpots.get(Math.floorMod(rank - 1, finishSpots.size()));
    }

    // ------------------------------------------------------------------ what has been seen

    BridgeKnowledge knowledge() {
        return knowledge;
    }

    // ------------------------------------------------------------------ queue and gate

    /** The gate is open for this contestant. */
    boolean isCalled(int number) {
        return queue.state(number) == BridgeQueue.State.CALLED;
    }

    /** This contestant is the next one in line. */
    boolean isOnDeck(int number) {
        return queue.next() == number;
    }

    boolean isOut(int number) {
        Crosser c = crossers.get(number);
        return (c != null && c.out) || queue.state(number) == BridgeQueue.State.OUT;
    }

    boolean hasFinished(int number) {
        return queue.state(number) == BridgeQueue.State.FINISHED;
    }

    int finishRank(int number) {
        Crosser c = crossers.get(number);
        return c == null ? 0 : c.finishRank;
    }

    long calledAt(int number) {
        Crosser c = crossers.get(number);
        return c == null ? 0 : c.calledTick;
    }

    // ------------------------------------------------------------------ panels and people

    /** The panel this contestant last stepped on (the judged one), or null before the first step. */
    @Nullable
    PanelField.Cell cellOf(int number) {
        Crosser c = crossers.get(number);
        return c == null ? null : c.cell;
    }

    /** The panel this contestant has just been heard cracking under them (everybody hears it). */
    boolean myPanelCracked(int number) {
        Crosser c = crossers.get(number);
        return c != null && c.condemned;
    }

    boolean panelIntact(int row, int lane) {
        return field.cell(row, lane).state == PanelField.State.INTACT;
    }

    /** True when {@code lane} of {@code row} still has its glass (it may have been broken by a fall or the stall rule). */
    boolean panelHasGlass(int row, int lane) {
        return field.cell(row, lane).hasGlass();
    }

    /**
     * The glass of this panel has shattered for good (it was the weak one of its row): a hole everybody in the hall can
     * see, as opposed to a panel that is cracking or that the stall rule broke and that re-forms soon.
     */
    boolean panelBroken(int row, int lane) {
        return field.cell(row, lane).state == PanelField.State.BROKEN;
    }

    /** A panel an NPC may hop onto: intact, nobody on it and nobody else about to hop onto it. */
    boolean panelFree(int row, int lane, int me) {
        PanelField.Cell c = field.cell(row, lane);
        return c.state == PanelField.State.INTACT && (c.occupant == 0 || c.occupant == me)
                && (c.reservedBy == 0 || c.reservedBy == me);
    }

    /** Somebody (other than {@code except}) stands above a panel of this row. */
    boolean rowOccupied(int row, int except) {
        return field.rowOccupied(row, except);
    }

    boolean reserve(int number, int row, int lane) {
        PanelField.Cell c = field.cell(row, lane);
        if (c.reservedBy != 0 && c.reservedBy != number) {
            return false;
        }
        c.reservedBy = number;
        return true;
    }

    void release(int number) {
        for (PanelField.Cell c : field.all()) {
            if (c.reservedBy == number) {
                c.reservedBy = 0;
            }
        }
    }

    /** Ticks left before the stall limit breaks the glass under this contestant (full limit when not on a panel). */
    int stallRemaining(int number) {
        Crosser c = crossers.get(number);
        if (c == null) {
            return params.stallLimitTicks();
        }
        return BridgeRules.stallRemaining(params, c.progressTick, now());
    }
}
