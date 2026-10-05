package com.squidgame.game.bridge;

/** What the game tracks about one contestant while the bridge is being crossed (humans and NPCs alike). */
final class Crosser {
    final int number;
    /** The last panel this contestant touched; the stall rule and the HUD refer to it. */
    PanelField.Cell cell;
    /** The panel the body is above right now (occupancy), or null. */
    PanelField.Cell over;
    /** Highest row (0-based) reached so far, -1 = none. */
    int rowReached = -1;
    /** Game tick of the last progress (being called, reaching a new row). The stall limit counts from here. */
    long progressTick;
    long calledTick;
    /** The panel under this contestant is cracking: they are going down with it. */
    boolean condemned;
    long condemnedDeadline;
    boolean finished;
    int finishRank;
    boolean out;
    /** Ticks in a row the body has been off every panel after having been on the bridge. */
    int offPanelTicks;
    /** The stall timer is currently held because somebody stands in the next row. */
    boolean held;

    Crosser(int number) {
        this.number = number;
    }
}
