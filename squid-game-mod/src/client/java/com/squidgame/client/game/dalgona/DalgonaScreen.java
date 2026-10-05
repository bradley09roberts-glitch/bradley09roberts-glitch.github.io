package com.squidgame.client.game.dalgona;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.core.Difficulty;
import com.squidgame.core.dalgona.DalgonaRules;
import com.squidgame.core.dalgona.DalgonaShape;
import com.squidgame.core.dalgona.StrokeValidator;
import com.squidgame.game.dalgona.DalgonaNet;
import com.squidgame.net.ClientActionPayload;
import com.squidgame.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The carving table. The player holds the left mouse button and traces the groove with the needle; right mouse button
 * or L licks the cookie. Everything that decides the outcome happens on the server: this screen sends the needle
 * strokes (batched every tick, with the time they took), predicts the carved parts so the furrow appears under the
 * needle without lag, and shows what the server reports (stress, licks, free / cracked, time). Esc steps away from the
 * table (the clock keeps running); right-clicking the desk in the world brings the screen back.
 */
final class DalgonaScreen extends Screen implements ScreenRegistry.ClosableByServer {
    static DalgonaScreen current;

    private static final ResourceLocation ICON_LICK = ResourceLocation.fromNamespaceAndPath("squidgame", "textures/gui/hud/icon_lick.png");
    private static final int AMBER = 0xFFFFC040;
    private static final int GREEN = 0xFF50D890;
    private static final int RED = 0xFFFF4040;
    private static final int CREAM = 0xFFF4E4C1;
    private static final int PINK = 0xFFE0457B;

    /** A crumb flying off the needle (GUI pixels, seconds). */
    private static final class Chip {
        float x;
        float y;
        float vx;
        float vy;
        float age;
        float life;
        float size;
        int color;
    }

    private final CookieModel model;
    private final List<Chip> chips = new ArrayList<>();
    private final boolean intro;
    private final long openedAt = System.currentTimeMillis();
    private final Random rnd = new Random();

    // layout
    private int size;
    private float ox;
    private float oy;
    private int lickX;
    private int lickY;
    private static final int LICK_BOX = 34;

    // input
    private boolean down;
    private final int[] pend = new int[1024];
    private int pendN;
    private boolean startFlag;
    private boolean endPending;
    private boolean strokeOpen;
    private long lastSendMs;
    private long msgStartNanos;
    private double lastX = -1;
    private double lastY = -1;
    private final long[] moveT = new long[10];
    private final float[] moveX = new float[10];
    private final float[] moveY = new float[10];
    private int moveCount;
    private double ratioShown;
    private int tickCount;

    // feedback
    private long shakeUntil;
    private long shakeDur = 1;
    private float shakeAmp;
    private long flashUntil;
    private int lastScratchTick = -100;
    private int lastHeartbeatTick = -100;
    private int crackStage;
    private long lickAnimStart = -1;
    private long popStart = -1;
    private long breakStart = -1;
    private long closeAt;
    private boolean timeoutShown;
    private long timeoutAt;
    private final List<CookiePainter.Shard> shards = new ArrayList<>();
    private long lastFrameNanos = System.nanoTime();
    private double heatShown;

    DalgonaScreen(CompoundTag data) {
        super(Component.translatable("squidgame.game.dalgona.title"));
        DalgonaShape shape = DalgonaShape.byOrdinal(data.getInt("shape"));
        Difficulty d = Difficulty.values()[Math.max(0, Math.min(Difficulty.values().length - 1, data.getInt("difficulty")))];
        this.model = new CookieModel(shape, DalgonaRules.params(d), data.getLong("seed"));
        this.intro = data.getBoolean("intro");
    }

    @Override
    public String screenId() {
        return "dalgona";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        current = this;
        layout();
        hideCursor(true);
    }

    @Override
    public void removed() {
        if (down || strokeOpen) {
            endStroke();
        }
        hideCursor(false);
        if (current == this) {
            current = null;
        }
    }

    private void hideCursor(boolean hide) {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, hide ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        } catch (RuntimeException | LinkageError ignored) {
            // no window (tests / unusual platforms): the system cursor simply stays visible
        }
    }

    private void layout() {
        size = Math.max(96, Math.min(height - 88, (int) (width * 0.48f))) & ~7;
        ox = (width - size) / 2f;
        oy = 35 + (height - 88 - size) / 2f;
        lickX = (int) (ox + size + 16 + 14 + 10);
        lickY = (int) (oy + size - LICK_BOX);
    }

    @Override
    protected void rebuildWidgets() {
        layout();
    }

    // ------------------------------------------------------------------ state from the server

    static void onState(DalgonaNet.StatePayload p) {
        DalgonaScreen s = current;
        if (s != null) {
            s.apply(p);
        }
    }

    private void apply(DalgonaNet.StatePayload p) {
        long now = System.currentTimeMillis();
        boolean settled = !down && !strokeOpen && now - lastSendMs > 350;
        // The first state after the screen opened (also after a reconnect) only tells where the cookie stands: the
        // stress and the events in it happened before, so they cause no impact mark, shake or sound.
        boolean first = !model.gotState;
        double before = model.apply(p, now, settled);
        double delta = first ? 0 : p.stress() - before;
        int events = first ? 0 : p.events();
        if ((events & DalgonaNet.E_SPIKE) != 0 || delta > 7) {
            hit(delta);
        }
        if ((events & DalgonaNet.E_PENALTY) != 0) {
            flashUntil = now + 500;
            play(ModSounds.UI_DENY, 1f, 0.7f);
        }
        if ((events & DalgonaNet.E_LICK) != 0 && lickAnimStart < 0) {
            startLickAnim(now);
        }
        if (p.cracked() && breakStart < 0) {
            startBreak(now);
        } else if (p.done() && popStart < 0) {
            popStart = now;
            endStroke();
            closeAt = now + 3300;
        } else if (p.timedOut() && !timeoutShown) {
            timeoutShown = true;
            timeoutAt = now;
            endStroke();
            closeAt = now + 2500;
            play(ModSounds.GAME_END_BUZZER, 1f, 1f);
        }
    }

    private void hit(double delta) {
        long now = System.currentTimeMillis();
        shakeAmp = (float) Math.min(9.0, 2.5 + delta * 0.35);
        shakeDur = 380;
        shakeUntil = now + shakeDur;
        float x = lastX >= 0 ? (float) lastX : 512f;
        float y = lastX >= 0 ? (float) lastY : 512f;
        model.addDecal(x, y, delta > 20 ? 3 : delta > 12 ? 2 : delta > 7 ? 1 : 0, now, rnd);
        play(ModSounds.DALGONA_CRACK, 0.85f + rnd.nextFloat() * 0.3f, 0.9f);
    }

    private void startBreak(long now) {
        breakStart = now;
        closeAt = now + 3400;
        endStroke();
        shakeAmp = 11f;
        shakeDur = 600;
        shakeUntil = now + shakeDur;
        // the cookie breaks into the squares of its texture, flying away from where the needle was
        float originX = ox + size / 2f;
        float originY = oy + size / 2f;
        if (lastX >= 0) {
            originX = ox + (float) lastX * size / 1024f;
            originY = oy + (float) lastY * size / 1024f;
        }
        shards.clear();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                float tu = 16 * i + 8, tv = 16 * j + 8;
                if (Math.hypot(tu - 64, tv - 64) > 68) {
                    continue;
                }
                float sx = ox + (16 * i + 8) / 128f * size;
                float sy = oy + (16 * j + 8) / 128f * size;
                float dx = sx - originX, dy = sy - originY;
                float len = Math.max(8f, (float) Math.hypot(dx, dy));
                float speed = size * (0.5f + rnd.nextFloat() * 1.3f);
                shards.add(new CookiePainter.Shard(sx, sy, dx / len * speed + (rnd.nextFloat() - 0.5f) * size * 0.4f,
                        dy / len * speed - size * (0.2f + rnd.nextFloat() * 0.5f), (rnd.nextFloat() - 0.5f) * 700f, 16 * i, 16 * j));
            }
        }
    }

    /** The tongue sheen over the cookie; the slurp itself is played by the server at the desk. */
    private void startLickAnim(long now) {
        lickAnimStart = now;
    }

    private static void send(String id, CompoundTag data) {
        try {
            ClientPlayNetworking.send(new ClientActionPayload(id, data));
        } catch (IllegalStateException ignored) {
            // the connection is already gone (the screen is being removed because of a disconnect)
        }
    }

    // ------------------------------------------------------------------ input

    private boolean inCookie(double mx, double my) {
        return mx >= ox - 4 && mx <= ox + size + 4 && my >= oy - 4 && my <= oy + size + 4;
    }

    private boolean overLick(double mx, double my) {
        return mx >= lickX && mx < lickX + LICK_BOX && my >= lickY && my < lickY + LICK_BOX;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1 || (button == 0 && overLick(mx, my))) {
            requestLick();
            return true;
        }
        if (button == 0 && inCookie(mx, my) && model.canCarve() && model.lickLock <= 0) {
            down = true;
            startFlag = true;
            endPending = false;
            msgStartNanos = System.nanoTime();
            lastX = -1;
            addPoint(mx, my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && down) {
            addPoint(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && down) {
            addPoint(mx, my);
            endStroke();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_L) {
            requestLick();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Canvas coordinates (0..1023) of a screen position. */
    private int toCanvas(double screen, float origin) {
        double u = (screen - origin) / size * DalgonaShape.CANVAS;
        return (int) Math.max(0, Math.min(DalgonaShape.MAX_COORD, Math.round(u)));
    }

    private void addPoint(double mx, double my) {
        int cx = toCanvas(mx, ox);
        int cy = toCanvas(my, oy);
        if (lastX >= 0 && Math.hypot(cx - lastX, cy - lastY) < 1.5) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        int carvedBefore = model.carvedCount;
        double moved = lastX >= 0 ? Math.hypot(cx - lastX, cy - lastY) : 0;
        // local prediction: carve along the segment from the previous point
        if (lastX >= 0) {
            double len = Math.hypot(cx - lastX, cy - lastY);
            int steps = Math.max(1, (int) Math.ceil(len / 5.0));
            for (int i = 1; i <= steps; i++) {
                model.carveLocal(lastX + (cx - lastX) * i / steps, lastY + (cy - lastY) * i / steps, nowMs);
            }
        } else {
            model.carveLocal(cx, cy, nowMs);
        }
        lastX = cx;
        lastY = cy;
        recordMove(cx, cy);
        spawnChips(cx, cy, moved, model.carvedCount > carvedBefore);
        if (pendN >= pend.length) {
            pendN = resample(pend, pendN, pend.length / 2);
        }
        pend[pendN++] = StrokeValidator.pack(cx, cy);
    }

    /** Crumbs jump off the needle while it carves; a needle that is too fast throws more (and hotter) ones. */
    private void spawnChips(int cx, int cy, double moved, boolean carved) {
        if (moved < 1.5 || chips.size() > 80) {
            return;
        }
        double ratio = ratioShown;
        int n = (carved ? 1 : 0) + (ratio > 1.0 ? 2 : 0) + (rnd.nextInt(3) == 0 ? 1 : 0);
        float sx = ox + cx * size / 1024f;
        float sy = oy + cy * size / 1024f;
        float unit = Math.max(1f, size / 150f);
        for (int i = 0; i < n; i++) {
            Chip c = new Chip();
            double a = Math.PI * 2 * rnd.nextDouble();
            float sp = (14f + rnd.nextFloat() * 40f) * unit * (0.7f + (float) Math.min(2.0, ratio));
            c.x = sx;
            c.y = sy;
            c.vx = (float) Math.cos(a) * sp;
            c.vy = (float) Math.sin(a) * sp - 22f * unit;
            c.life = 0.28f + rnd.nextFloat() * 0.35f;
            c.size = (1f + rnd.nextInt(2)) * Math.min(2f, unit);
            c.color = ratio > 1.0 ? 0xFFFFB45A : 0xFFE2B060;
            chips.add(c);
        }
    }

    private void drawChips(GuiGraphics g, float dt) {
        if (chips.isEmpty()) {
            return;
        }
        float gravity = 150f * Math.max(1f, size / 150f);
        for (java.util.Iterator<Chip> it = chips.iterator(); it.hasNext(); ) {
            Chip c = it.next();
            c.age += dt;
            if (c.age >= c.life) {
                it.remove();
                continue;
            }
            c.vy += gravity * dt;
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            Gfx.rect(g, c.x, c.y, c.x + c.size, c.y + c.size, Gfx.fade(c.color, 1f - c.age / c.life));
        }
        Gfx.end(g);
    }

    private void recordMove(float x, float y) {
        long now = System.nanoTime();
        if (moveCount == moveT.length) {
            System.arraycopy(moveT, 1, moveT, 0, moveCount - 1);
            System.arraycopy(moveX, 1, moveX, 0, moveCount - 1);
            System.arraycopy(moveY, 1, moveY, 0, moveCount - 1);
            moveCount--;
        }
        moveT[moveCount] = now;
        moveX[moveCount] = x;
        moveY[moveCount] = y;
        moveCount++;
    }

    /** Needle speed relative to the local safe speed over roughly the last 130 ms (1 = at the limit). */
    private double speedRatio(long nowNanos) {
        if (moveCount < 2 || nowNanos - moveT[moveCount - 1] > 120_000_000L) {
            return 0;
        }
        int first = moveCount - 1;
        while (first > 0 && nowNanos - moveT[first - 1] < 130_000_000L) {
            first--;
        }
        double path = 0;
        for (int i = first + 1; i < moveCount; i++) {
            path += Math.hypot(moveX[i] - moveX[i - 1], moveY[i] - moveY[i - 1]);
        }
        double ticks = Math.max(1.0, (moveT[moveCount - 1] - moveT[first]) / 50_000_000.0);
        double[] arc = new double[1];
        model.shape.nearest(moveX[moveCount - 1], moveY[moveCount - 1], arc);
        double safe = model.params.localSafeSpeed(model.shape.fragility(model.shape.sampleAtArc(arc[0])));
        return path / ticks / safe;
    }

    /** Thins a batch of packed points down to {@code target} (keeping the first and the last). */
    private static int resample(int[] pts, int n, int target) {
        if (n <= target) {
            return n;
        }
        for (int i = 0; i < target; i++) {
            pts[i] = pts[(int) ((long) i * (n - 1) / Math.max(1, target - 1))];
        }
        return target;
    }

    private void endStroke() {
        down = false;
        if (pendN > 0 || strokeOpen) {
            endPending = true;
            flush();
        }
        moveCount = 0;
    }

    /** Sends the points collected since the last message. Called every tick and when the needle lifts. */
    private void flush() {
        if (pendN == 0 && !endPending) {
            return;
        }
        long now = System.nanoTime();
        int ms = (int) Math.max(20, Math.min(1000, (now - msgStartNanos) / 1_000_000L));
        int n = pendN;
        if (n == 0) {
            // the needle lifted without moving: repeat the last point so the message is not empty
            pend[0] = StrokeValidator.pack((int) Math.max(0, lastX), (int) Math.max(0, lastY));
            n = 1;
        }
        n = resample(pend, n, StrokeValidator.MAX_POINTS);
        int[] pts = java.util.Arrays.copyOf(pend, n);
        CompoundTag t = new CompoundTag();
        t.putIntArray("p", pts);
        t.putInt("t", ms);
        t.putByte("f", (byte) ((startFlag ? 1 : 0) | (endPending ? 2 : 0)));
        send("dalgona.stroke", t);
        lastSendMs = System.currentTimeMillis();
        strokeOpen = !endPending;
        startFlag = false;
        endPending = false;
        pendN = 0;
        msgStartNanos = now;
    }

    private void requestLick() {
        if (!model.canCarve()) {
            return;
        }
        if (!model.canLickLocal()) {
            play(ModSounds.UI_DENY, 1f, 0.6f);
            return;
        }
        if (down || strokeOpen) {
            endStroke();
        }
        send("dalgona.lick", new CompoundTag());
        // optimistic prediction, corrected by the server's next state
        model.licks--;
        model.lickLock = model.params.lickLockTicks();
        model.lickCooldown = model.params.lickCooldownTicks();
        model.stressShown = Math.max(0, model.stressShown - model.params.lickRelief());
        startLickAnim(System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        tickCount++;
        model.tick();
        flush();
        long now = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        double ratio = down ? speedRatio(nowNs) : 0;
        ratioShown += (ratio - ratioShown) * 0.45;
        double stress = model.stressShown;
        // sounds: the needle scratching, the cookie creaking at 25 / 50 / 75, the heartbeat when it is about to go
        if (down && ratio > 0.05 && tickCount - lastScratchTick >= 5 && model.canCarve()) {
            lastScratchTick = tickCount;
            play(ModSounds.NEEDLE_SCRATCH, (float) Math.min(1.5, 0.75 + 0.5 * ratio), 0.32f);
        }
        int stage = stress >= 75 ? 3 : stress >= 50 ? 2 : stress >= 25 ? 1 : 0;
        if (stage > crackStage && model.canCarve()) {
            play(ModSounds.DALGONA_CRACK, 0.9f + 0.15f * stage, 0.6f);
        }
        if (stage < crackStage && stress < crackStage * 25 - 6 || stage > crackStage) {
            crackStage = stage;
        }
        if (stress >= 65 && model.canCarve()) {
            int interval = (int) Math.max(10, 24 - (stress - 65) * 0.4);
            if (tickCount - lastHeartbeatTick >= interval) {
                lastHeartbeatTick = tickCount;
                play(ModSounds.DANGER_HEARTBEAT, 1f, 0.6f);
            }
        }
        if (closeAt > 0 && now >= closeAt) {
            onClose();
        }
    }

    private void play(SoundEvent ev, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        // drawn in render(): the table covers the whole screen
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        long nowMs = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrameNanos) / 1e9f);
        lastFrameNanos = nowNs;

        CookiePainter.table(g, width, height);

        float shx = 0, shy = 0;
        if (nowMs < shakeUntil) {
            float a = shakeAmp * (shakeUntil - nowMs) / shakeDur;
            shx = (rnd.nextFloat() - 0.5f) * 2f * a;
            shy = (rnd.nextFloat() - 0.5f) * 2f * a;
        }
        g.pose().pushPose();
        g.pose().translate(shx, shy, 0);

        CookiePainter.View v = new CookiePainter.View();
        float introT = intro ? Math.min(1f, (nowMs - openedAt) / 900f) : 1f;
        float scale = 0.72f + 0.28f * CookiePainter.easeOut(introT);
        v.size = size * scale;
        v.ox = ox + (size - v.size) / 2f;
        v.oy = oy + (size - v.size) / 2f;
        v.k = v.size / DalgonaShape.CANVAS;
        v.now = nowMs;
        v.alpha = Math.min(1f, introT * 2f);
        v.pulse = (nowMs % 100000) / 1000f;
        boolean overCookie = inCookie(mx, my) && model.canCarve();
        v.needleValid = overCookie || down;
        if (v.needleValid) {
            v.needleX = (mx - v.ox) / v.k;
            v.needleY = (my - v.oy) / v.k;
        }
        v.needleDown = down;
        double ratioNow = down ? ratioShown : 0;
        heatShown += (Math.min(1.0, ratioNow / 1.6) - heatShown) * Math.min(1f, dt * 14f);
        v.heat = (float) Math.max(heatShown, model.stressShown >= 80 ? (model.stressShown - 80) / 20.0 : 0.0);
        if (popStart >= 0) {
            v.pop = Math.min(1f, (nowMs - popStart) / 1500f);
        }
        if (lickAnimStart >= 0) {
            float t = (nowMs - lickAnimStart) / 1800f;
            v.lick = t >= 1f ? -1f : t;
            if (t >= 1f) {
                lickAnimStart = -1;
            }
        }
        if (breakStart >= 0) {
            v.shattered = true;
            CookiePainter.paint(g, model, v);
            updateShards(dt);
            float t = (nowMs - breakStart) / 1800f;
            CookiePainter.shards(g, shards, v, Math.max(0f, 1f - Math.max(0f, t - 0.55f) / 0.45f));
        } else {
            CookiePainter.paint(g, model, v);
            drawChips(g, dt);
        }
        g.pose().popPose();

        drawStressVignette(g, nowMs);
        drawPanels(g, mx, my, nowMs);
        drawBanners(g, nowMs);
        if (nowMs < flashUntil) {
            g.fill(0, 0, width, height, Gfx.argb((int) (90 * (flashUntil - nowMs) / 500f), 0xFF2020));
        }
        // the needle is the mouse cursor (the system cursor is hidden), so it never shakes and is drawn last
        CookiePainter.needle(g, model, v, mx, my, model.canCarve() && (overCookie || down));
    }

    private void updateShards(float dt) {
        float gravity = size * 3.2f;
        for (CookiePainter.Shard s : shards) {
            s.vy += gravity * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.rot += s.vrot * dt;
        }
    }

    private void drawStressVignette(GuiGraphics g, long nowMs) {
        double stress = model.stressShown;
        if (stress < 55 || !model.canCarve()) {
            return;
        }
        float strength = (float) Math.pow((stress - 55) / 45.0, 1.3) * 0.55f;
        float beat = 0.72f + 0.28f * (float) Math.sin(nowMs / (stress > 80 ? 110.0 : 170.0));
        int steps = 10;
        int dx = (int) (width * 0.22f);
        int dy = (int) (height * 0.30f);
        for (int i = 0; i < steps; i++) {
            float f = 1f - i / (float) steps;
            int col = Gfx.argb((int) (255 * strength * beat * f * f * 0.8f), 0xC01010);
            int ix = dx * i / steps, iy = dy * i / steps, nx = dx * (i + 1) / steps, ny = dy * (i + 1) / steps;
            g.fill(ix, iy, width - ix, ny, col);
            g.fill(ix, height - ny, width - ix, height - iy, col);
            g.fill(ix, ny, nx, height - ny, col);
            g.fill(width - nx, ny, width - ix, height - ny, col);
        }
    }

    // ------------------------------------------------------------------ panels

    private void drawPanels(GuiGraphics g, int mx, int my, long nowMs) {
        if (!drawResultBanner(g, nowMs)) {
            drawTimer(g, nowMs);
        }
        List<Component> tip = null;
        int gx = (int) ox - 30;
        int gy = (int) oy;
        gauge(g, gx, gy, 14, size, model.stressShown / 100.0, stressColor(model.stressShown), true, -1);
        g.drawCenteredString(font, Integer.toString((int) Math.round(model.stressShown)), gx + 7, gy + size + 4, stressColor(model.stressShown));
        label(g, Component.translatable("squidgame.game.dalgona.screen.stress"), gx + 7, gy + size + 14);
        if (mx >= gx - 2 && mx <= gx + 16 && my >= gy && my <= gy + size) {
            tip = List.of(Component.translatable("squidgame.game.dalgona.screen.tip.stress.1").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("squidgame.game.dalgona.screen.tip.stress.2"),
                    Component.translatable("squidgame.game.dalgona.screen.tip.stress.3"));
        }
        int rx = (int) (ox + size + 16);
        double progress = model.progress();
        gauge(g, rx, gy, 14, size, progress, 0xFFE8B04A, false, model.params.successThreshold());
        g.drawCenteredString(font, (int) Math.round(progress * 100) + "%", rx + 7, gy + size + 4, 0xFFE8B04A);
        label(g, Component.translatable("squidgame.game.dalgona.screen.carved"), rx + 7, gy + size + 14);
        if (mx >= rx - 2 && mx <= rx + 16 && my >= gy && my <= gy + size) {
            tip = List.of(Component.translatable("squidgame.game.dalgona.screen.tip.carved.1").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("squidgame.game.dalgona.screen.tip.carved.2", (int) Math.round(model.params.successThreshold() * 100)));
        }
        drawLickButton(g, mx, my);
        if (overLick(mx, my)) {
            tip = model.licks <= 0 ? List.of(Component.translatable("squidgame.game.dalgona.screen.tip.lick.none").withStyle(net.minecraft.ChatFormatting.RED))
                    : model.lickCooldown > 0 || model.lickLock > 0
                    ? List.of(Component.translatable("squidgame.game.dalgona.screen.tip.lick.1").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("squidgame.game.dalgona.screen.tip.lick.wait", String.format("%.1f", Math.max(model.lickCooldown, model.lickLock) / 20.0)))
                    : List.of(Component.translatable("squidgame.game.dalgona.screen.tip.lick.1").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("squidgame.game.dalgona.screen.tip.lick.2", (int) model.params.lickRelief()),
                    Component.translatable("squidgame.game.dalgona.screen.tip.lick.3"));
        }
        drawSpeed(g, mx, my);
        if (mx >= ox && mx <= ox + size && my >= oy + size + 8 && my <= oy + size + 18) {
            tip = List.of(Component.translatable("squidgame.game.dalgona.screen.tip.speed.1").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("squidgame.game.dalgona.screen.tip.speed.2"),
                    Component.translatable("squidgame.game.dalgona.screen.tip.speed.3"));
        }
        // the bottom lines: how to play, replaced by a pulsing warning while the cookie is in danger
        int hy = height - 25;
        boolean lickable = model.canLickLocal();
        if (model.canCarve() && model.stressShown >= 80) {
            warning(g, Component.translatable("squidgame.game.dalgona.screen.cracking"), RED, hy - 2, nowMs);
            if (lickable) {
                g.drawCenteredString(font, Component.translatable("squidgame.game.dalgona.screen.hint.lick.short"), width / 2, hy + 15, 0xFFFFD27A);
            }
        } else if (down && ratioShown > 1.1 && model.canCarve()) {
            warning(g, Component.translatable("squidgame.game.dalgona.screen.slow_down"), AMBER, hy + 1, nowMs);
        } else {
            boolean hint = model.canCarve() && model.stressShown >= 50 && lickable;
            g.drawCenteredString(font, Component.translatable(hint ? "squidgame.game.dalgona.screen.hint.lick" : "squidgame.game.dalgona.screen.help.1"),
                    width / 2, hy, hint ? 0xFFFFD27A : 0xFFE9D7B0);
            g.drawCenteredString(font, Component.translatable("squidgame.game.dalgona.screen.help.2"), width / 2, hy + 11, 0xFFB9A98A);
        }
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private void label(GuiGraphics g, Component text, int cx, int y) {
        g.drawCenteredString(font, text, cx, y, 0xFFE9D7B0);
    }

    private void warning(GuiGraphics g, Component text, int color, int y, long nowMs) {
        float pulse = 0.78f + 0.22f * (float) Math.sin(nowMs / 90.0);
        g.pose().pushPose();
        g.pose().translate(width / 2f, y, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawCenteredString(font, text, 0, 0, Gfx.argb((int) (255 * pulse), color & 0xFFFFFF));
        g.pose().popPose();
    }

    private static int stressColor(double stress) {
        return stress < 35 ? GREEN : stress < 65 ? Gfx.lerpColor(AMBER, 0xFFFF8A30, (float) ((stress - 35) / 30)) : Gfx.lerpColor(0xFFFF8A30, RED, (float) ((stress - 65) / 35));
    }

    private void gauge(GuiGraphics g, int x, int y, int w, int h, double value, int color, boolean stress, double mark) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF40362A);
        g.fill(x, y, x + w, y + h, 0xFF150E08);
        int fh = (int) Math.round(h * Math.max(0, Math.min(1, value)));
        g.fill(x, y + h - fh, x + w, y + h, color);
        g.fill(x, y + h - fh, x + w / 3, y + h, Gfx.argb(70, 0xFFFFFF));
        // tick marks
        for (int i = 1; i < 4; i++) {
            int ty = y + h - h * i / 4;
            g.fill(x, ty, x + w, ty + 1, 0x66000000);
        }
        if (mark > 0) {
            int ty = y + h - (int) Math.round(h * mark);
            g.fill(x - 4, ty, x + w + 4, ty + 1, 0xFFFFFFFF);
            g.fill(x - 5, ty - 1, x - 2, ty + 2, 0xFFFFFFFF);
            g.fill(x + w + 2, ty - 1, x + w + 5, ty + 2, 0xFFFFFFFF);
        }
        if (stress && value > 0.75) {
            g.renderOutline(x - 2, y - 2, w + 4, h + 4, Gfx.argb((int) (160 + 90 * Math.sin(System.currentTimeMillis() / 120.0)), 0xFF3030));
        }
    }

    private void drawSpeed(GuiGraphics g, int mx, int my) {
        int x = (int) ox;
        int y = (int) (oy + size + 9);
        int w = size;
        g.fill(x - 1, y - 1, x + w + 1, y + 7, 0xFF000000);
        g.fill(x, y, x + w, y + 6, 0xFF150E08);
        // zones: calm, careful, too fast
        g.fill(x, y, x + w / 2, y + 6, 0x40208850);
        g.fill(x + w / 2, y, x + w * 3 / 4, y + 6, 0x40B07010);
        g.fill(x + w * 3 / 4, y, x + w, y + 6, 0x40B02020);
        double r = Math.min(2.0, ratioShown);
        int fw = (int) Math.round(w * r / 2.0);
        int col = ratioShown < 0.8 ? GREEN : ratioShown < 1.0 ? AMBER : RED;
        g.fill(x, y, x + fw, y + 6, col);
        g.fill(x + w / 2 - 1, y - 2, x + w / 2 + 1, y + 8, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("squidgame.game.dalgona.screen.speed"), x, y + 9, 0xFFB9A98A, false);
        Component limit = Component.translatable("squidgame.game.dalgona.screen.limit");
        g.drawString(font, limit, x + w / 2 - font.width(limit) / 2, y + 9, 0xFFB9A98A, false);
    }

    private void drawLickButton(GuiGraphics g, int mx, int my) {
        boolean ready = model.canLickLocal();
        boolean hover = overLick(mx, my);
        int x = lickX, y = lickY;
        g.fill(x - 2, y - 2, x + LICK_BOX + 2, y + LICK_BOX + 2, 0xFF000000);
        g.fill(x - 1, y - 1, x + LICK_BOX + 1, y + LICK_BOX + 1, ready ? (hover ? 0xFFFFD27A : PINK) : 0xFF4A4038);
        g.fill(x, y, x + LICK_BOX, y + LICK_BOX, hover && ready ? 0xFF3A2218 : 0xFF21140C);
        g.setColor(ready ? 1f : 0.45f, ready ? 0.85f : 0.45f, ready ? 0.9f : 0.45f, 1f);
        g.blit(ICON_LICK, x + 5, y + 4, 24, 24, 0f, 0f, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        // cooldown sweep
        int cd = Math.max(model.lickCooldown, 0);
        if (cd > 0 && model.licks > 0) {
            float frac = Math.min(1f, cd / (float) model.params.lickCooldownTicks());
            g.enableScissor(x, y, x + LICK_BOX, y + LICK_BOX);
            Gfx.sector(g, x + LICK_BOX / 2f, y + LICK_BOX / 2f, LICK_BOX * 0.72f, 0, Math.PI * 2 * frac, 0x99000000);
            Gfx.end(g);
            g.disableScissor();
        }
        g.drawString(font, Integer.toString(Math.max(0, model.licks)), x + LICK_BOX - 9, y + LICK_BOX - 10, model.licks > 0 ? 0xFFFFFFFF : RED, true);
        g.drawString(font, "L", x + 3, y + 3, 0xFFB9A98A, false);
    }

    private void drawTimer(GuiGraphics g, long nowMs) {
        double ticks = model.ticksLeftNow(nowMs);
        int seconds = (int) Math.ceil(ticks / 20.0);
        float frac = (float) Math.max(0, Math.min(1, ticks / model.timeTotal));
        int col = frac < 0.15f ? RED : frac < 0.35f ? AMBER : CREAM;
        String t = String.format("%d:%02d", seconds / 60, seconds % 60);
        g.pose().pushPose();
        g.pose().translate(width / 2f, 3, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, t, 0, 0, col);
        g.pose().popPose();
        int bw = Math.min(width - 40, 220);
        int bx = (width - bw) / 2;
        g.fill(bx - 1, 21, bx + bw + 1, 26, 0xFF000000);
        g.fill(bx, 22, bx + bw, 25, 0xFF241810);
        g.fill(bx, 22, bx + (int) (bw * frac), 25, frac < 0.15f ? RED : frac < 0.35f ? AMBER : GREEN);
    }

    /** "You drew: star" over the cookie while it slides in. */
    private void drawBanners(GuiGraphics g, long nowMs) {
        long age = nowMs - openedAt;
        if (!intro || age >= 2000 || breakStart >= 0 || popStart >= 0 || timeoutShown) {
            return;
        }
        float a = age < 250 ? age / 250f : age > 1500 ? Math.max(0f, 1f - (age - 1500) / 500f) : 1f;
        Component title = Component.translatable("squidgame.game.dalgona.screen.drew",
                Component.translatable(model.shape.translationKey()).withStyle(net.minecraft.ChatFormatting.BOLD));
        g.pose().pushPose();
        g.pose().translate(width / 2f, oy + size * 0.5f - 12, 0);
        g.pose().scale(1.8f, 1.8f, 1f);
        int w = font.width(title);
        g.fill(-w / 2 - 6, -4, w / 2 + 6, 13, Gfx.argb((int) (150 * a), 0x000000));
        g.drawString(font, title, -w / 2, 0, Gfx.argb((int) (255 * a), CREAM & 0xFFFFFF), true);
        g.pose().popPose();
    }

    /**
     * How the cookie ended, in big letters where the timer normally is (so the cookie itself stays clear for the
     * pop / shatter animation). Returns false while the cookie is still being carved.
     */
    private boolean drawResultBanner(GuiGraphics g, long nowMs) {
        Component text;
        int color;
        long since;
        if (breakStart >= 0) {
            text = Component.translatable("squidgame.game.dalgona.screen.cracked");
            color = RED;
            since = breakStart;
        } else if (popStart >= 0) {
            text = Component.translatable("squidgame.game.dalgona.screen.free");
            color = 0xFFFFD84A;
            since = popStart;
        } else if (timeoutShown) {
            text = Component.translatable("squidgame.game.dalgona.screen.time_up");
            color = RED;
            since = timeoutAt;
        } else {
            return false;
        }
        float age = nowMs - since;
        float a = Math.min(1f, age / 180f);
        float s = 3f * (1f + 0.3f * (1f - CookiePainter.easeOut(Math.min(1f, age / 380f))));
        int w = font.width(text);
        s = Math.min(s, (width - 16f) / Math.max(1, w));
        g.pose().pushPose();
        g.pose().translate(width / 2f, 3, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, -w / 2, 0, Gfx.argb((int) (255 * a), color & 0xFFFFFF), true);
        g.pose().popPose();
        return true;
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
