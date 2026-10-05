package com.squidgame.client.game.marbles;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.game.marbles.MarblesNet;
import com.squidgame.net.ClientActionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The odd-or-even table: both marble stacks (public), the hand, the controls of the current role - the holder picks
 * how many marbles go into the fist, the guesser picks the wager and calls odd or even - the decision timer, the
 * revealed history, and the reveal itself (the fist shakes, opens, the verdict lands and the stacks change hands).
 * Everything shown comes from the server's per-player state ({@code OddEvenMatch#stateFor}); the screen only sends the
 * player's own decision, which the server validates again.
 */
@Environment(EnvType.CLIENT)
final class MarblesScreen extends Screen implements ScreenRegistry.ClosableByServer {
    static final int PW = 300, PH = 190;
    private static final int PINK = FlatButton.PINK;
    private static final int GOLD = 0xFFFFD84A, GREEN = 0xFF55FF88, RED = 0xFFFF5555, GREY = 0xFFCFCFCF;
    private static final ResourceLocation MARBLE = tex("marble_big");
    private static final ResourceLocation HAND_CLOSED = tex("hand_closed");
    private static final ResourceLocation HAND_OPEN = tex("hand_open");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("squidgame", "textures/gui/marbles/" + name + ".png");
    }

    /** One revealed round, from the viewer's point of view. */
    static final class Hist {
        int round;
        boolean held;
        int hidden;
        boolean odd;
        int wager;
        boolean ok;
        int delta;
    }

    static final class Reveal {
        int hidden;
        boolean guessOdd;
        int wager;
        boolean correct;
        boolean iWon;
        boolean iHeld;
        int delta;
        int revealTicks;
        int verdictIn;
    }

    /** The screen state as the server sent it. */
    static final class State {
        String phase = "intro";
        int round, maxRounds;
        boolean overtime;
        int me, opp;
        String oppName = "";
        int myMarbles, oppMarbles;
        boolean hold;
        int maxHold = 1, maxWager = 1;
        boolean forced;
        boolean myLocked, oppLocked;
        int myHold;
        boolean myGuessOdd;
        int myWager;
        int remaining, total;
        Reveal rev;
        final List<Hist> hist = new ArrayList<>();
        boolean over, won;
        String reason = "";
        final long receivedAt = System.nanoTime();

        /** Dismissing the panel with Esc hides it for the rest of the decision (or of the reveal). */
        String key() {
            return round + "|" + (phase.equals("reveal") || phase.equals("over") ? "r" : "d");
        }

        static State parse(CompoundTag t) {
            State s = new State();
            s.phase = t.getString("phase");
            s.round = t.getInt("round");
            s.maxRounds = t.getInt("maxRounds");
            s.overtime = t.getBoolean("overtime");
            s.me = t.getInt("me");
            s.opp = t.getInt("opp");
            s.oppName = t.getString("oppName");
            s.myMarbles = t.getInt("myMarbles");
            s.oppMarbles = t.getInt("oppMarbles");
            s.hold = t.getString("role").equals("hold");
            s.maxHold = Math.max(1, t.getInt("maxHold"));
            s.maxWager = Math.max(1, t.getInt("maxWager"));
            s.forced = t.getBoolean("forced");
            s.myLocked = t.getBoolean("myLocked");
            s.oppLocked = t.getBoolean("oppLocked");
            s.myHold = t.getInt("myHold");
            s.myGuessOdd = t.getBoolean("myGuessOdd");
            s.myWager = t.getInt("myWager");
            s.remaining = t.getInt("remaining");
            s.total = Math.max(1, t.getInt("total"));
            if (t.contains("rev", Tag.TAG_COMPOUND)) {
                CompoundTag r = t.getCompound("rev");
                Reveal rv = new Reveal();
                rv.hidden = r.getInt("hidden");
                rv.guessOdd = r.getBoolean("guessOdd");
                rv.wager = r.getInt("wager");
                rv.correct = r.getBoolean("correct");
                rv.iWon = r.getBoolean("iWon");
                rv.iHeld = r.getBoolean("iHeld");
                rv.delta = r.getInt("delta");
                rv.revealTicks = r.getInt("revealTicks");
                rv.verdictIn = r.getInt("verdictIn");
                s.rev = rv;
            }
            ListTag hist = t.getList("hist", Tag.TAG_COMPOUND);
            for (int i = 0; i < hist.size(); i++) {
                CompoundTag e = hist.getCompound(i);
                Hist h = new Hist();
                h.round = e.getInt("r");
                h.held = e.getBoolean("held");
                h.hidden = e.getInt("n");
                h.odd = e.getBoolean("odd");
                h.wager = e.getInt("w");
                h.ok = e.getBoolean("ok");
                h.delta = e.getInt("d");
                s.hist.add(h);
            }
            if (t.contains("over", Tag.TAG_COMPOUND)) {
                s.over = true;
                s.won = t.getCompound("over").getBoolean("won");
                s.reason = t.getCompound("over").getString("reason");
            }
            return s;
        }
    }

    private State s;
    private int selCount = 1, selWager = 1;
    /** -1 nothing chosen yet, 0 even, 1 odd. */
    private int selOdd = -1;
    private FlatButton minus, plus, odd, even, lock;

    MarblesScreen(State s) {
        super(Component.translatable("squidgame.game.marbles.ui.title"));
        this.s = s;
        selCount = Mth.clamp(selCount, 1, s.maxHold);
        selWager = Mth.clamp(selWager, 1, s.maxWager);
        syncLocked();
    }

    @Override
    public String screenId() {
        return MarblesNet.SCREEN_ODD_EVEN;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A new state from the server (same screen instance): keep the player's unsent choices within the new round. */
    void apply(State next) {
        boolean newRound = next.round != s.round;
        this.s = next;
        if (newRound) {
            selCount = 1;
            selWager = 1;
            selOdd = -1;
        }
        selCount = Mth.clamp(selCount, 1, next.maxHold);
        selWager = Mth.clamp(selWager, 1, next.maxWager);
        syncLocked();
        rebuildWidgets();
    }

    private void syncLocked() {
        if (s.myLocked) {
            if (s.hold) {
                selCount = Math.max(1, s.myHold);
            } else {
                selWager = Math.max(1, s.myWager);
                selOdd = s.myGuessOdd ? 1 : 0;
            }
        }
        if (s.forced) {
            selWager = 1;
        }
    }

    @Override
    public void onClose() {
        MarblesClient.dismissed = s.key();
        super.onClose();
    }

    // ------------------------------------------------------------------ widgets

    private int px() {
        return (width - PW) / 2;
    }

    private int py() {
        return (height - PH) / 2;
    }

    private boolean decidingNow() {
        return s.phase.equals("decide") && !s.myLocked;
    }

    @Override
    protected void init() {
        minus = plus = odd = even = lock = null;
        if (!decidingNow()) {
            return;
        }
        int px = px(), py = py(), cx = px + PW / 2;
        minus = addRenderableWidget(new FlatButton(cx - 62, py + 82, 18, 14, Component.literal("-"), b -> adjust(-1)));
        plus = addRenderableWidget(new FlatButton(cx + 44, py + 82, 18, 14, Component.literal("+"), b -> adjust(1)));
        lock = addRenderableWidget(new FlatButton(cx - 46, py + 118, 92, 16, Component.translatable("squidgame.game.marbles.ui.lock"), b -> lockIn()));
        if (!s.hold) {
            odd = addRenderableWidget(new FlatButton(cx - 54, py + 98, 50, 16, Component.translatable("squidgame.game.marbles.ui.odd"), b -> choose(1)));
            even = addRenderableWidget(new FlatButton(cx + 4, py + 98, 50, 16, Component.translatable("squidgame.game.marbles.ui.even"), b -> choose(0)));
            if (s.forced) {
                minus.active = false;
                plus.active = false;
            }
        }
        refreshButtons();
    }

    private void refreshButtons() {
        if (lock != null && !s.hold) {
            lock.active = selOdd >= 0;
        }
        if (odd != null) {
            odd.selected = selOdd == 1;
        }
        if (even != null) {
            even.selected = selOdd == 0;
        }
        if (minus != null && !s.forced) {
            int v = s.hold ? selCount : selWager;
            int max = s.hold ? s.maxHold : s.maxWager;
            minus.active = v > 1;
            plus.active = v < max;
        }
    }

    private void adjust(int d) {
        if (s.hold) {
            selCount = Mth.clamp(selCount + d, 1, s.maxHold);
        } else if (!s.forced) {
            selWager = Mth.clamp(selWager + d, 1, s.maxWager);
        }
        refreshButtons();
    }

    private void choose(int parity) {
        selOdd = parity;
        refreshButtons();
    }

    private void lockIn() {
        if (!decidingNow()) {
            return;
        }
        CompoundTag t = new CompoundTag();
        if (s.hold) {
            t.putInt("count", selCount);
            ClientPlayNetworking.send(new ClientActionPayload(MarblesNet.ACTION_HOLD, t));
            s.myLocked = true;
            s.myHold = selCount;
        } else {
            if (selOdd < 0) {
                return;
            }
            t.putInt("wager", selWager);
            t.putBoolean("odd", selOdd == 1);
            ClientPlayNetworking.send(new ClientActionPayload(MarblesNet.ACTION_GUESS, t));
            s.myLocked = true;
            s.myWager = selWager;
            s.myGuessOdd = selOdd == 1;
        }
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (decidingNow()) {
            switch (key) {
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> {
                    adjust(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> {
                    adjust(1);
                    return true;
                }
                case GLFW.GLFW_KEY_O -> {
                    if (!s.hold) {
                        choose(1);
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_E -> {
                    if (!s.hold) {
                        choose(0);
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                    if (s.hold || selOdd >= 0) {
                        lockIn();
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ rendering
    //
    // Layout (panel coordinates): header 6, stacks 22-98 (left: yours, right: the opponent's), centre column 20-134 (role, hand,
    // selector, odd / even, lock in), status line 137-150, history 156-183, time bar 185.

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the world stays visible behind the panel (no blur, see render)
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x38000000);
        int px = px(), py = py(), cx = px + PW / 2;
        g.fill(px, py, px + PW, py + PH, 0xFF101018);
        g.renderOutline(px, py, PW, PH, PINK);
        long elapsed = (System.nanoTime() - s.receivedAt) / 1_000_000L;
        Reveal rv = s.phase.equals("reveal") ? s.rev : null;
        boolean verdict = rv != null && elapsed >= rv.verdictIn * 50L;
        int delta = verdict ? rv.delta : 0;

        // header: title, time left of the decision, round
        g.drawString(font, Component.translatable("squidgame.game.marbles.ui.title"), px + 8, py + 6, GOLD, true);
        Component round = s.overtime ? Component.translatable("squidgame.game.marbles.ui.overtime")
                : Component.translatable("squidgame.game.marbles.ui.round", s.round, s.maxRounds);
        g.drawString(font, round, px + PW - 8 - font.width(round), py + 6, s.overtime ? RED : GREY, true);
        if (s.phase.equals("decide")) {
            long left = Math.max(0, s.remaining - (System.nanoTime() - s.receivedAt) / 50_000_000L);
            float frac = Mth.clamp(left / (float) s.total, 0f, 1f);
            Component secs = Component.literal((left + 19) / 20 + " s");
            g.drawString(font, secs, cx - font.width(secs) / 2, py + 6, frac < 0.2f ? RED : frac < 0.45f ? 0xFFFFC040 : 0xFFFFFFFF, true);
        }
        g.fill(px + 6, py + 17, px + PW - 6, py + 18, 0x66E0457B);

        // the two stacks (public information)
        renderStack(g, px + 46, py + 22, Component.translatable("squidgame.game.marbles.ui.you"), s.myMarbles + delta, delta, GREEN, null);
        renderStack(g, px + PW - 46, py + 22, Component.literal(String.format("No. %03d", s.opp)), s.oppMarbles - delta, -delta, GOLD, s.oppName);

        renderCentre(g, cx, py, elapsed, rv, verdict);
        renderStatus(g, cx, py);
        renderHistory(g, px, py);
        renderFooter(g, px, py);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** A marble pile with the count (and the change a reveal is applying), centred on {@code cx}. */
    private void renderStack(GuiGraphics g, int cx, int y, Component label, int count, int change, int color, String name) {
        g.drawCenteredString(font, label, cx, y, color);
        g.pose().pushPose();
        g.pose().translate(cx, y + 10, 0);
        g.pose().scale(2.4f, 2.4f, 1f);
        g.drawCenteredString(font, Integer.toString(Math.max(0, count)), 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        if (change != 0) {
            g.pose().pushPose();
            g.pose().translate(cx, y + 32, 0);
            g.pose().scale(1.4f, 1.4f, 1f);
            g.drawCenteredString(font, Component.literal((change > 0 ? "+" : "") + change), 0, 0, change > 0 ? GREEN : RED);
            g.pose().popPose();
        }
        int cols = 8, size = 9, step = 7;
        int n = Math.min(Math.max(0, count), 32);
        int baseY = y + 64;
        for (int i = 0; i < n; i++) {
            int row = i / cols, col = i % cols;
            int rowCount = Math.min(cols, n - row * cols);
            int x = cx - rowCount * size / 2 + col * size;
            g.blit(MARBLE, x, baseY - row * step, size, size, 0, 0, 16, 16, 16, 16);
        }
        if (name != null && !name.isEmpty()) {
            g.drawCenteredString(font, font.plainSubstrByWidth(name, 90), cx, y + 76, 0xFF9A9AA4);
        }
    }

    private void drawHand(GuiGraphics g, ResourceLocation hand, int cx, int py, int dx, int dy) {
        g.blit(hand, cx - 20 + dx, py + 38 + dy, 40, 40, 0, 0, 48, 48, 48, 48);
    }

    private void renderCentre(GuiGraphics g, int cx, int py, long elapsed, Reveal rv, boolean verdict) {
        String phase = s.phase;
        if (phase.equals("over")) {
            g.pose().pushPose();
            g.pose().translate(cx, py + 52, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.drawCenteredString(font, Component.translatable(s.won ? "squidgame.game.marbles.ui.over.won" : "squidgame.game.marbles.ui.over.lost"),
                    0, 0, s.won ? GREEN : RED);
            g.pose().popPose();
            return;
        }
        if (phase.equals("intro")) {
            drawHand(g, HAND_CLOSED, cx, py, 0, 0);
            return;
        }
        ResourceLocation hand = HAND_CLOSED;
        int dx = 0, dy = 0;
        if (rv != null) {
            double verdictMs = rv.verdictIn * 50.0;
            if (elapsed < 0.55 * verdictMs) {
                double a = elapsed / 38.0;
                dx = (int) Math.round(Math.sin(a) * 3.0);
                dy = (int) Math.round(Math.cos(a * 1.3) * 2.0);
                g.drawCenteredString(font, Component.translatable("squidgame.game.marbles.ui.reveal.shake"), cx, py + 24, GREY);
            } else {
                hand = HAND_OPEN;
                int n = Math.min(rv.hidden, 12);
                for (int i = 0; i < n; i++) {
                    g.blit(MARBLE, cx - n * 8 / 2 + i * 8, py + 21, 8, 8, 0, 0, 16, 16, 16, 16);
                }
                g.drawCenteredString(font, Component.translatable("squidgame.game.marbles.ui.reveal.open", rv.hidden,
                        Component.translatable(rv.hidden % 2 == 1 ? "squidgame.game.marbles.ui.odd" : "squidgame.game.marbles.ui.even")),
                        cx, py + 30, 0xFFFFFFFF);
            }
        } else {
            g.drawCenteredString(font, Component.translatable(s.hold ? "squidgame.game.marbles.ui.role.hold" : "squidgame.game.marbles.ui.role.guess"),
                    cx, py + 24, s.hold ? GOLD : 0xFF8FD8FF);
        }
        drawHand(g, hand, cx, py, dx, dy);

        if (rv != null) {
            if (verdict) {
                boolean good = rv.iWon;
                String key = rv.iHeld ? (rv.correct ? "squidgame.game.marbles.ui.reveal.they_right" : "squidgame.game.marbles.ui.reveal.they_wrong")
                        : (rv.correct ? "squidgame.game.marbles.ui.reveal.correct" : "squidgame.game.marbles.ui.reveal.wrong");
                float pop = Mth.clamp((elapsed - rv.verdictIn * 50f) / 180f, 0f, 1f);
                float sc = 1.25f + 0.6f * (1f - pop);
                g.pose().pushPose();
                g.pose().translate(cx, py + 86, 0);
                g.pose().scale(sc, sc, 1f);
                g.drawCenteredString(font, Component.translatable(key), 0, 0, good ? GREEN : RED);
                g.pose().popPose();
                g.drawCenteredString(font, Component.translatable(good ? "squidgame.game.marbles.ui.reveal.won" : "squidgame.game.marbles.ui.reveal.lost",
                        Math.abs(rv.delta)), cx, py + 104, good ? GREEN : RED);
            }
            return;
        }
        if (decidingNow()) {
            Component value = s.hold ? Component.translatable("squidgame.game.marbles.ui.hold", selCount)
                    : s.forced ? Component.translatable("squidgame.game.marbles.ui.wager.forced")
                    : Component.translatable("squidgame.game.marbles.ui.wager", selWager);
            g.drawCenteredString(font, value, cx, py + 85, 0xFFFFFFFF);
            if (s.hold) {
                g.drawCenteredString(font, Component.translatable("squidgame.game.marbles.ui.hold.prompt", s.maxHold), cx, py + 103, GREY);
            }
        } else if (s.myLocked) {
            // the choice is made: a small summary of it under the hand
            Component locked = s.hold ? Component.translatable("squidgame.game.marbles.ui.locked.hold", s.myHold)
                    : Component.translatable("squidgame.game.marbles.ui.locked.guess", s.myWager,
                    Component.translatable(s.myGuessOdd ? "squidgame.game.marbles.ui.odd" : "squidgame.game.marbles.ui.even"));
            g.drawCenteredString(font, locked, cx, py + 85, GREEN);
        }
    }

    /** The line between the controls and the history: the opponent's state, the intro text, why the match ended. */
    private void renderStatus(GuiGraphics g, int cx, int py) {
        String phase = s.phase;
        if (phase.equals("over")) {
            wrapped(g, Component.translatable("squidgame.game.marbles.end.reason." + s.reason, s.myMarbles, s.oppMarbles, s.opp), cx, py + 137, GREY);
        } else if (phase.equals("intro")) {
            wrapped(g, Component.translatable("squidgame.game.marbles.ui.intro"), cx, py + 137, GREY);
        } else if (phase.equals("decide") || phase.equals("locked")) {
            g.drawCenteredString(font, Component.translatable(s.oppLocked ? "squidgame.game.marbles.ui.opp.ready" : "squidgame.game.marbles.ui.opp.thinking"),
                    cx, py + 140, s.oppLocked ? GOLD : 0xFF9A9AA4);
        }
    }

    /** Draws a text centred on {@code cx}, wrapped to the panel width; returns the number of lines. */
    private int wrapped(GuiGraphics g, Component text, int cx, int y, int color) {
        List<FormattedCharSequence> lines = font.split(text, PW - 24);
        for (int i = 0; i < lines.size(); i++) {
            g.drawCenteredString(font, lines.get(i), cx, y + i * 9, color);
        }
        return lines.size();
    }

    private void renderHistory(GuiGraphics g, int px, int py) {
        int y = py + 156;
        g.drawString(font, Component.translatable("squidgame.game.marbles.ui.history"), px + 8, y, 0xFF9A9AA4, false);
        Component hint = Component.translatable("squidgame.game.marbles.ui.hide", MarblesClient.panelKeyName());
        g.drawString(font, hint, px + PW - 8 - font.width(hint), y, 0xFF70707A, false);
        y += 10;
        if (s.hist.isEmpty()) {
            g.drawString(font, Component.translatable("squidgame.game.marbles.ui.history.empty"), px + 8, y, 0xFF70707A, false);
            return;
        }
        int from = Math.max(0, s.hist.size() - 2);
        for (int i = from; i < s.hist.size(); i++) {
            Hist h = s.hist.get(i);
            Component parity = Component.translatable(h.hidden % 2 == 1 ? "squidgame.game.marbles.ui.odd" : "squidgame.game.marbles.ui.even");
            Component call = Component.translatable(h.odd ? "squidgame.game.marbles.ui.odd" : "squidgame.game.marbles.ui.even");
            Component line = Component.translatable(h.held ? "squidgame.game.marbles.ui.history.held" : "squidgame.game.marbles.ui.history.guessed",
                    h.round, h.hidden, parity, call, h.wager);
            g.drawString(font, line, px + 8, y, 0xFFD8D8E0, false);
            Component d = Component.literal((h.delta > 0 ? "+" : "") + h.delta);
            g.drawString(font, d, px + PW - 10 - font.width(d), y, h.delta > 0 ? GREEN : RED, false);
            y += 9;
        }
    }

    private void renderFooter(GuiGraphics g, int px, int py) {
        if (s.phase.equals("decide")) {
            long left = Math.max(0, s.remaining - (System.nanoTime() - s.receivedAt) / 50_000_000L);
            float frac = Mth.clamp(left / (float) s.total, 0f, 1f);
            int bx = px + 8, bw = PW - 16, by = py + PH - 5;
            g.fill(bx, by, bx + bw, by + 3, 0x66000000);
            int col = frac < 0.2f ? 0xFFFF4040 : frac < 0.45f ? 0xFFFFC040 : 0xFF50D890;
            g.fill(bx, by, bx + (int) (bw * frac), by + 3, col);
        }
    }
}
