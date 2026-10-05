package com.squidgame.core;

/** The six games, in canonical tournament order. */
public enum GameKind {
    RED_LIGHT("red_light", 1),
    DALGONA("dalgona", 1),
    TUG_OF_WAR("tug_of_war", 4),
    MARBLES("marbles", 2),
    GLASS_BRIDGE("glass_bridge", 4),
    FINAL("final", 2);

    public final String id;
    /**
     * Minimum number of survivors needed to be able to play this game; the planner skips a game the remaining field is too
     * small for. The glass bridge needs about a dozen contestants: the first ones gamble, the route is revealed by their falls, and
     * with fewer (simulation: 8 contestants cross 0.4 on average) nobody would reach the far side.
     */
    public final int minParticipants;

    GameKind(String id, int minParticipants) {
        this.id = id;
        this.minParticipants = minParticipants;
    }

    public String titleKey() {
        return "squidgame.game." + id + ".title";
    }

    public String instructionKey(int line) {
        return "squidgame.game." + id + ".instruction." + line;
    }

    public static GameKind byId(String id) {
        for (GameKind t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
