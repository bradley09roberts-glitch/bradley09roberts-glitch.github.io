package com.squidgame.game;

import com.squidgame.core.GameKind;
import com.squidgame.tournament.Contestant;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Placeholder used while a real game is missing: contestants wait a short time, then roughly a third of them are
 * eliminated at random. Keeps the tournament pipeline testable end to end.
 */
public final class StubGame implements MiniGame {
    private final GameKind type;
    private int ticks;

    public StubGame(GameKind type) {
        this.type = type;
    }

    @Override
    public GameKind type() {
        return type;
    }

    @Override
    public List<Component> instructions(GameContext ctx) {
        return List.of(Component.literal("(placeholder game - not implemented yet)"));
    }

    @Override
    public Component objective(GameContext ctx, Contestant viewer) {
        return Component.literal("Wait...");
    }

    @Override
    public int timeLimitTicks(GameContext ctx) {
        return 20 * 20;
    }

    @Override
    public void prepare(GameContext ctx) {
    }

    @Override
    public void placeContestants(GameContext ctx) {
    }

    @Override
    public void begin(GameContext ctx) {
        ticks = 0;
    }

    @Override
    public void tick(GameContext ctx) {
        ticks++;
    }

    @Override
    public boolean isFinished(GameContext ctx) {
        return ticks >= 200;
    }

    @Override
    public void onTimeout(GameContext ctx) {
    }

    @Override
    public GameResult conclude(GameContext ctx) {
        List<Contestant> out = new ArrayList<>();
        List<Contestant> alive = ctx.alive();
        int keep = Math.max(type.minParticipants > 1 ? 2 : 1, (int) Math.ceil(alive.size() * 0.66));
        List<Contestant> shuffled = new ArrayList<>(alive);
        ctx.rng().shuffle(shuffled);
        for (int i = keep; i < shuffled.size(); i++) {
            ctx.eliminate(shuffled.get(i), EliminationCause.TIMEOUT, i * 3);
            out.add(shuffled.get(i));
        }
        return new GameResult(new ArrayList<>(alive.subList(0, 0)), out, Component.literal("Round over"), Component.empty());
    }

    @Override
    public void cleanup(GameContext ctx) {
    }
}
