package com.squidgame.game.marbles;

import com.squidgame.SquidGameMod;
import com.squidgame.core.marbles.MarbleLedger;
import com.squidgame.core.marbles.MarblesRules;
import com.squidgame.core.marbles.MatchOutcome;
import com.squidgame.core.marbles.Side;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.GameContext;
import com.squidgame.net.HudPayload;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.DisplayUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The match of one pair at one plot. Both variants share the lifecycle (intro, rounds, end sequence), the marble
 * counts shown on the HUD, in the hotbar and on the table, and the end announcements; the subclasses implement the
 * rounds. Humans and NPCs act through the same {@code submit...} methods of the subclasses, which validate the phase,
 * the role and the bounds, so there is exactly one rule set.
 */
abstract class Match {
    final MarblesGame game;
    final GameContext ctx;
    final Plot plot;
    final Contestant a;
    final Contestant b;
    final MarblesRules.Variant variant;
    final MarblesRules.Params params;
    final Rng rng;
    private final int[] shown = new int[2];
    private MatchOutcome outcome;
    private long endAt = -1;
    private boolean closed;
    private boolean begun;
    private Display.TextDisplay tableText;

    Match(MarblesGame game, Plot plot, Contestant a, Contestant b, MarblesRules.Variant variant, Rng rng) {
        this.game = game;
        this.ctx = game.ctx();
        this.plot = plot;
        this.a = a;
        this.b = b;
        this.variant = variant;
        this.params = game.params();
        this.rng = rng;
    }

    // ------------------------------------------------------------------ to implement

    abstract MarbleLedger ledger();

    /** The contestants are in place: start the intro. */
    abstract void begin(long now);

    abstract void tick(long now);

    /** The clock's soft deadline: more marbles wins; a tie is settled by one more round / throw. */
    abstract void timeCall(long now);

    /** The hard deadline: decide now, whatever the state. */
    abstract void forceResolve(long now);

    abstract void forfeit(Side loser, long now);

    /** A human (re)connected: send them the current UI. */
    abstract void resendUi(Contestant c);

    /** Round information, banners and bars of the HUD for a partner. */
    abstract void hudExtra(Contestant viewer, Side me, List<HudPayload.Widget> out);

    /** The outcome is decided: close or finalise match screens. */
    abstract void onDecided(long now);

    abstract void release();

    abstract String summary();

    // ------------------------------------------------------------------ identity

    final Contestant of(Side s) {
        return s == Side.A ? a : b;
    }

    final boolean involves(Contestant c) {
        return c.number == a.number || c.number == b.number;
    }

    final Side sideOf(Contestant c) {
        return c.number == a.number ? Side.A : Side.B;
    }

    final Contestant opponentOf(Contestant c) {
        return c.number == a.number ? b : a;
    }

    final int shown(Side s) {
        return shown[s.ordinal()];
    }

    final boolean decided() {
        return outcome != null;
    }

    final boolean closed() {
        return closed;
    }

    final MatchOutcome outcome() {
        return outcome;
    }

    final Contestant winner() {
        return outcome == null ? null : of(outcome.winner());
    }

    final Contestant loser() {
        return outcome == null ? null : of(outcome.loser());
    }

    final int ticks(double seconds) {
        return game.ticks(seconds);
    }

    // ------------------------------------------------------------------ shared presentation

    final void setUpIfNeeded() {
        if (!begun) {
            setUp();
        }
    }

    /** Puts the counters, the hotbar stacks and the table text in place (once). */
    final void setUp() {
        if (begun) {
            return;
        }
        begun = true;
        for (Side s : Side.values()) {
            shown[s.ordinal()] = ledger().count(s);
        }
        Vec3 text = plot.tableTextPos();
        tableText = DisplayUtil.spawnText(ctx.level, text, 0f, tableJson(), 1.7f, true, "squidgame_temp");
        if (tableText != null) {
            CompoundTag t = new CompoundTag();
            tableText.saveWithoutId(t);
            t.putString("billboard", "center");
            tableText.load(t);
        }
        for (Side s : Side.values()) {
            game.giveMarbles(of(s), shown[s.ordinal()]);
        }
    }

    /** Brings the HUD counters, the hotbar stacks and the table to the ledger's counts. */
    final void syncShown() {
        for (Side s : Side.values()) {
            shown[s.ordinal()] = ledger().count(s);
            game.giveMarbles(of(s), shown[s.ordinal()]);
        }
        updateTable();
    }

    private String tableJson() {
        return "[{\"text\":\"" + a.displayNumber() + "  \",\"color\":\"aqua\"},{\"text\":\"" + shown[0]
                + "\",\"color\":\"white\",\"bold\":true},{\"text\":\"  :  \",\"color\":\"gray\"},{\"text\":\"" + shown[1]
                + "\",\"color\":\"white\",\"bold\":true},{\"text\":\"  " + b.displayNumber() + "\",\"color\":\"gold\"}]";
    }

    final void updateTable() {
        if (tableText != null && !tableText.isRemoved()) {
            DisplayUtil.setText(tableText, tableJson());
        }
    }

    final void faceEachOther() {
        for (Side s : Side.values()) {
            ContestantEntity e = ctx.npc(of(s));
            if (e != null && of(s).isAiControlled()) {
                e.faceToward(plot.padPos(s.other()));
            }
        }
    }

    final void soundAt(Vec3 pos, SoundEvent sound, float volume, float pitch) {
        ctx.soundAt(pos, sound, SoundSource.PLAYERS, volume, pitch);
    }

    final void toPartners(java.util.function.BiConsumer<Contestant, ServerPlayer> action) {
        for (Contestant c : new Contestant[]{a, b}) {
            if (c.isAlive() && c.isHumanControlled()) {
                ServerPlayer p = c.player(ctx.server());
                if (p != null) {
                    action.accept(c, p);
                }
            }
        }
    }

    final void giveMarblesTo(Contestant c, Side s) {
        game.giveMarbles(c, shown(s));
    }

    final void gesture(Contestant c, String name) {
        ContestantEntity e = ctx.npc(c);
        if (e != null && c.isAiControlled()) {
            e.triggerAction(name);
        }
    }

    final void pose(Contestant c, Activity activity) {
        ContestantEntity e = ctx.npc(c);
        if (e != null && c.isAiControlled()) {
            e.setActivity(activity);
        }
    }

    // ------------------------------------------------------------------ HUD

    final void hudWidgets(Contestant viewer, List<HudPayload.Widget> out) {
        if (viewer == null || !involves(viewer)) {
            return;
        }
        Side me = sideOf(viewer);
        Contestant opp = of(me.other());
        out.add(HudPayload.Widget.counter("mine", "icon_marble", Component.translatable("squidgame.game.marbles.hud.mine"),
                shown(me), -1));
        out.add(HudPayload.Widget.counter("theirs", "icon_marble",
                Component.translatable("squidgame.game.marbles.hud.theirs", opp.displayNumber()), shown(me.other()), -1));
        if (!decided() && shown(me) == 1) {
            out.add(HudPayload.Widget.banner("last", Component.translatable("squidgame.game.marbles.banner.last_marble"), 0xFF4040));
        }
        hudExtra(viewer, me, out);
    }

    // ------------------------------------------------------------------ end of the match

    /** The match is decided: announce it to the partners; the loser is eliminated when the end sequence completes. */
    final void finish(MatchOutcome o, long now) {
        if (outcome != null) {
            return;
        }
        setUpIfNeeded();
        outcome = o;
        endAt = now + ticks(3.5);
        Contestant w = winner(), l = loser();
        String reason = o.reason().name().toLowerCase(java.util.Locale.ROOT);
        toPartners((c, p) -> {
            boolean won = c.number == w.number;
            Component title = Component.translatable(won ? "squidgame.game.marbles.end.win" : "squidgame.game.marbles.end.lose");
            Component sub = Component.translatable("squidgame.game.marbles.end.reason." + reason,
                    shown(sideOf(c)), shown(sideOf(c).other()), opponentOf(c).displayNumber());
            Announcer.title(p, title, sub, 3, 60, 15);
            p.playNotifySound(won ? ModSounds.UI_CONFIRM : ModSounds.DANGER_STING, SoundSource.MASTER, 1.0f, won ? 1.1f : 0.9f);
            if (!won) {
                ctx.danger(p, 0.5f, 50, 3, 0xFFC01010);
            }
        });
        if (w.isAlive()) {
            pose(w, Activity.CELEBRATE_FIST);
        }
        if (l.isAlive()) {
            pose(l, Activity.SOB);
        }
        SquidGameMod.LOGGER.info("Marbles: {}", summary());
        onDecided(now);
    }

    /** Runs the end sequence; returns true once the match is closed (the game then eliminates the loser). */
    final boolean tickEnd(long now) {
        if (outcome != null && !closed && now >= endAt) {
            closed = true;
            game.concludeMatch(this);
        }
        return closed;
    }

    /** Ends the end sequence at once (the game is being concluded). */
    final void closeNow(int aimTicks) {
        if (!closed && outcome != null) {
            closed = true;
            game.concludeMatch(this, aimTicks);
        }
    }

    /** Removes everything this match created (table text, marbles, screens); idempotent. */
    final void cleanup() {
        release();
        if (tableText != null) {
            tableText.discard();
            tableText = null;
        }
    }
}
