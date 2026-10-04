package com.squidgame.tournament;

import com.squidgame.core.GameKind;
import org.jetbrains.annotations.Nullable;

/**
 * Chooses the next game from the canonical order given the number of survivors. Games whose minimum participant
 * count is not met are skipped; with exactly two survivors left the tournament jumps to the final; with one
 * survivor (or none) there is nothing left to play.
 */
public final class Planner {
    private Planner() {
    }

    /**
     * @param lastPlayed the game just finished, or null before the first game
     * @param survivors  contestants still alive
     * @return the next game, or null when the tournament is decided
     */
    @Nullable
    public static GameKind next(@Nullable GameKind lastPlayed, int survivors) {
        if (survivors <= 1) {
            return null;
        }
        if (lastPlayed == GameKind.FINAL) {
            return null;
        }
        if (survivors == 2 && lastPlayed != null) {
            return GameKind.FINAL;
        }
        int start = lastPlayed == null ? 0 : lastPlayed.ordinal() + 1;
        for (int i = start; i < GameKind.values().length; i++) {
            GameKind g = GameKind.values()[i];
            if (g == GameKind.FINAL) {
                return survivors >= 2 ? GameKind.FINAL : null;
            }
            if (survivors >= g.minParticipants) {
                return g;
            }
        }
        return null;
    }
}
