package com.squidgame.tournament;

import com.squidgame.core.Phase;
import com.squidgame.game.MiniGame;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Builds and sends the per-player HUD snapshot a few times per second. */
public final class HudService {
    private final TournamentManager manager;

    public HudService(TournamentManager manager) {
        this.manager = manager;
    }

    public void tick() {
        if (manager.server().getTickCount() % 4 != 0) {
            return;
        }
        for (ServerPlayer p : Announcer.audience(manager.server())) {
            ModNetwork.send(p, build(p));
        }
    }

    public HudPayload build(ServerPlayer p) {
        Tournament t = manager.tournament();
        if (t == null) {
            return new HudPayload(Phase.LOBBY.ordinal(), "", Component.translatable("squidgame.hud.lobby.title"),
                    Component.translatable("squidgame.hud.lobby.objective"), 0, 0, 0, 0, 0, HudPayload.STATUS_NONE, List.of());
        }
        Contestant me = t.roster.ofPlayer(p.getUUID());
        int status = HudPayload.STATUS_NONE;
        int number = 0;
        if (me != null) {
            number = me.number;
            status = switch (me.status()) {
                case ALIVE -> HudPayload.STATUS_ALIVE;
                case ELIMINATED -> HudPayload.STATUS_ELIMINATED;
                case WINNER -> HudPayload.STATUS_WINNER;
            };
        } else if (manager.isSpectator(p)) {
            status = HudPayload.STATUS_SPECTATOR;
        }
        Component title;
        Component objective;
        List<HudPayload.Widget> widgets = new ArrayList<>();
        MiniGame g = t.game;
        String gameId = t.gameType == null ? "" : t.gameType.id;
        switch (t.phase) {
            case REGISTRATION -> {
                title = Component.translatable("squidgame.phase.registration");
                objective = Component.translatable("squidgame.hud.registration.objective", t.roster.size());
            }
            case INSTRUCTIONS -> {
                title = t.gameType == null ? Component.empty() : Component.translatable(t.gameType.titleKey());
                objective = Component.translatable("squidgame.hud.instructions.objective");
            }
            case COUNTDOWN -> {
                title = t.gameType == null ? Component.empty() : Component.translatable(t.gameType.titleKey());
                objective = Component.translatable("squidgame.hud.countdown.objective");
            }
            case GAME -> {
                title = t.gameType == null ? Component.empty() : Component.translatable(t.gameType.titleKey());
                objective = g != null ? g.objective(t.ctx, me) : Component.empty();
                if (g != null && t.ctx != null) {
                    g.hudWidgets(t.ctx, me, widgets);
                }
            }
            case ELIMINATIONS -> {
                title = t.gameType == null ? Component.empty() : Component.translatable(t.gameType.titleKey());
                objective = Component.translatable("squidgame.hud.eliminations.objective");
            }
            case RESULTS -> {
                title = t.gameType == null ? Component.empty() : Component.translatable(t.gameType.titleKey());
                objective = Component.translatable("squidgame.hud.results.objective");
            }
            case TRANSITION -> {
                title = Component.translatable("squidgame.phase.transition");
                objective = Component.translatable("squidgame.hud.transition.objective");
            }
            case FINAL_WINNER -> {
                title = Component.translatable("squidgame.phase.final_winner");
                objective = Component.translatable("squidgame.hud.winner.objective");
            }
            default -> {
                title = Component.translatable(t.phase.translationKey());
                objective = Component.empty();
            }
        }
        int timerTotal = t.phaseLength;
        int timer = t.remainingPhaseTicks();
        return new HudPayload(t.phase.ordinal(), gameId, title, objective, timer, timerTotal,
                t.roster.aliveCount(), t.roster.size(), number, status, widgets);
    }
}
