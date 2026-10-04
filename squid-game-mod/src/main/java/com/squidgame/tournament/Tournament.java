package com.squidgame.tournament;

import com.squidgame.core.Difficulty;
import com.squidgame.core.GameKind;
import com.squidgame.core.Phase;
import com.squidgame.game.GameContext;
import com.squidgame.game.MiniGame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Mutable state of the running tournament. Owned and driven by {@link TournamentManager}. */
public final class Tournament {
    public long seed;
    public Difficulty difficulty = Difficulty.NORMAL;
    public Phase phase = Phase.REGISTRATION;
    public int phaseTicks;
    public int phaseLength;
    public Roster roster = new Roster();
    /** 1-based number of the game being prepared / played (0 = none yet). */
    public int gameNumber;
    @Nullable
    public GameKind gameType;
    @Nullable
    public MiniGame game;
    @Nullable
    public GameContext ctx;
    public int gameTicks;
    public final List<GameKind> played = new ArrayList<>();
    public int eliminationCounter;
    /** Simulation with no humans (spectate or test). */
    public boolean npcOnly;
    public String endMessage = "";
    /** Winner contestant numbers (set in FINAL_WINNER). */
    public final List<Integer> winners = new ArrayList<>();
    /** Contestant numbers eliminated in the game that just concluded, for the results screen. */
    public final List<Integer> lastEliminated = new ArrayList<>();
    public CompoundTag savedGameState = new CompoundTag();
    /** Remaining ticks of the countdown beeps already announced. */
    public int lastCountdownSecond = -1;
    /** Players who asked to start a fresh registration right after the end (auto restart). */
    public boolean restartRequested;
    /** Tick when the tournament started (for stats). */
    public long startedAtTick;

    public int remainingPhaseTicks() {
        return Math.max(0, phaseLength - phaseTicks);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("seed", seed);
        t.putString("difficulty", difficulty.id);
        t.putInt("phase", phase.ordinal());
        t.putInt("phaseTicks", phaseTicks);
        t.putInt("phaseLength", phaseLength);
        t.put("roster", roster.save());
        t.putInt("gameNumber", gameNumber);
        if (gameType != null) {
            t.putString("gameType", gameType.id);
        }
        ListTag pl = new ListTag();
        for (GameKind g : played) {
            pl.add(StringTag.valueOf(g.id));
        }
        t.put("played", pl);
        t.putInt("elimCounter", eliminationCounter);
        t.putBoolean("npcOnly", npcOnly);
        t.putLong("startedAt", startedAtTick);
        if (game != null) {
            CompoundTag gs = new CompoundTag();
            game.saveState(gs);
            t.put("gameState", gs);
        } else {
            t.put("gameState", savedGameState);
        }
        return t;
    }

    public static Tournament load(CompoundTag t) {
        Tournament tr = new Tournament();
        tr.seed = t.getLong("seed");
        tr.difficulty = Difficulty.byId(t.getString("difficulty"), Difficulty.NORMAL);
        tr.phase = Phase.byOrdinal(t.getInt("phase"));
        tr.phaseTicks = t.getInt("phaseTicks");
        tr.phaseLength = t.getInt("phaseLength");
        tr.roster = Roster.load(t, "roster");
        tr.gameNumber = t.getInt("gameNumber");
        tr.gameType = t.contains("gameType") ? GameKind.byId(t.getString("gameType")) : null;
        ListTag pl = t.getList("played", Tag.TAG_STRING);
        for (int i = 0; i < pl.size(); i++) {
            GameKind g = GameKind.byId(pl.getString(i));
            if (g != null) {
                tr.played.add(g);
            }
        }
        tr.eliminationCounter = t.getInt("elimCounter");
        tr.npcOnly = t.getBoolean("npcOnly");
        tr.startedAtTick = t.getLong("startedAt");
        tr.savedGameState = t.getCompound("gameState");
        return tr;
    }
}
