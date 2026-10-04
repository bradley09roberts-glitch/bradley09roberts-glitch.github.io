package com.squidgame.game;

import com.squidgame.core.GameKind;
import com.squidgame.net.HudPayload;
import com.squidgame.tournament.Contestant;
import com.squidgame.entity.ContestantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * One of the six games. All rules, timers, choices and eliminations run on the server through this interface;
 * the tournament drives the lifecycle:
 * <pre>
 * prepare (INSTRUCTIONS start) -> placeContestants (COUNTDOWN start) -> begin (GAME start)
 *   -> tick* until isFinished() or timeLimit -> [onTimeout] -> conclude (ELIMINATIONS) -> cleanup
 * </pre>
 * Implementations must be restart-safe: {@link #cleanup} always runs (also on reset), and anything that must survive
 * a server restart (e.g. the glass bridge route) goes through {@link #saveState}/{@link #loadState}.
 */
public interface MiniGame {
    GameKind type();

    /** Instruction lines shown (and announced) before the countdown. Use translation keys. */
    List<Component> instructions(GameContext ctx);

    /** Short objective for the HUD, personalised if needed. */
    Component objective(GameContext ctx, Contestant viewer);

    /** Game duration in ticks, already scaled for difficulty (the tournament additionally applies config timeScale). */
    int timeLimitTicks(GameContext ctx);

    /** Instructions phase start: contestants are in the waiting room. Build per-round state, spawn props and guards. */
    void prepare(GameContext ctx);

    /** Countdown start: move every alive contestant to its starting position inside the arena. */
    void placeContestants(GameContext ctx);

    /** Game phase start: assign NPC behaviours, start timers. */
    void begin(GameContext ctx);

    /** Called every server tick while the game phase is active. */
    void tick(GameContext ctx);

    /** True when the game decided it is over (e.g. everyone finished or only one left). */
    boolean isFinished(GameContext ctx);

    /** Time limit reached before {@link #isFinished}: apply the timeout rules (eliminate whoever has not succeeded). */
    void onTimeout(GameContext ctx);

    /** Game over: eliminate every remaining failure (via {@link GameContext#eliminate}) and report the outcome. */
    GameResult conclude(GameContext ctx);

    /** Remove temporary entities, blocks and effects. Idempotent. */
    void cleanup(GameContext ctx);

    // ------------------------------------------------------------------ optional hooks

    default void onContestantEliminated(GameContext ctx, Contestant c, EliminationCause cause) {
    }

    /** A UI / input action from a participant's client. */
    default void onClientAction(GameContext ctx, Contestant c, ServerPlayer player, String id, CompoundTag data) {
    }

    /** A player used a special block (e.g. a dalgona station). Return true if consumed. */
    default boolean onBlockUse(GameContext ctx, Contestant c, ServerPlayer player, BlockPos pos) {
        return false;
    }

    /** A player right-clicked an NPC contestant. Return true if consumed (e.g. marble partner request). */
    default boolean onInteractContestant(GameContext ctx, Contestant c, ServerPlayer player, ContestantEntity target) {
        return false;
    }

    /** A player released a charged marble (target throw). */
    default void onMarbleRelease(GameContext ctx, Contestant c, ServerPlayer player, int chargedTicks) {
    }

    /** A player attacked another entity (left click). Return true to cancel vanilla handling (default: cancel). */
    default boolean onPlayerAttack(GameContext ctx, Contestant attacker, ServerPlayer player, net.minecraft.world.entity.Entity target) {
        return true;
    }

    /** A human contestant's control changed (disconnect: AI took over; reconnect: human resumed). */
    default void onControllerChanged(GameContext ctx, Contestant c) {
    }

    /** Add game-specific HUD widgets for {@code viewer} (the viewer may be a spectator: contestant == null). */
    default void hudWidgets(GameContext ctx, Contestant viewer, List<HudPayload.Widget> out) {
    }

    /** Persist hidden state that must survive a restart (e.g. the generated safe route). */
    default void saveState(CompoundTag tag) {
    }

    default void loadState(CompoundTag tag) {
    }
}
