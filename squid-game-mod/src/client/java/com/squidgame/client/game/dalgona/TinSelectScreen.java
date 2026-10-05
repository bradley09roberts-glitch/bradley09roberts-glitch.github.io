package com.squidgame.client.game.dalgona;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.core.dalgona.DalgonaRules;
import com.squidgame.net.ClientActionPayload;
import com.squidgame.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The four closed tins on the desk. The choice is blind (the server decides which shape the contestant gets); clicking
 * a tin, or pressing 1 - 4, lifts its lid and tells the server, which then opens the carving screen. When the
 * selection time runs out the server opens a tin for the contestant.
 */
final class TinSelectScreen extends Screen implements ScreenRegistry.ClosableByServer {
    private static final int TINS = DalgonaRules.TIN_COUNT;
    private static final int CREAM = 0xFFF4E4C1;
    private static final int OPEN_MS = 520;

    private final long openedAt = System.currentTimeMillis();
    private final long endsAtMs;
    private final long totalMs;
    private final long seed;
    private final float[] lift = new float[TINS];
    private int chosen = -1;
    private long chosenAt;
    private boolean sent;
    private long lastFrame = System.nanoTime();
    private int lastTickSecond = -1;

    // layout
    private float radius;
    private float cy;
    private float gap;

    TinSelectScreen(CompoundTag data) {
        super(Component.translatable("squidgame.game.dalgona.tins.title"));
        long now = System.currentTimeMillis();
        this.endsAtMs = now + Math.max(0, data.getInt("left")) * 50L;
        this.totalMs = Math.max(1, data.getInt("total")) * 50L;
        this.seed = data.getLong("seed");
    }

    @Override
    public String screenId() {
        return "dalgona_tins";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        layout();
    }

    @Override
    protected void rebuildWidgets() {
        layout();
    }

    private void layout() {
        radius = Math.max(24f, Math.min((width - 60) / 9.6f, height * 0.17f));
        gap = radius * 0.5f;
        cy = height * 0.52f;
    }

    private float tinX(int i) {
        float total = TINS * 2 * radius + (TINS - 1) * gap;
        return (width - total) / 2f + radius + i * (2 * radius + gap);
    }

    private int tinAt(double mx, double my) {
        for (int i = 0; i < TINS; i++) {
            if (Math.hypot(mx - tinX(i), my - cy) <= radius * 1.04f) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int i = tinAt(mx, my);
            if (i >= 0) {
                choose(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int i = key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4 ? key - GLFW.GLFW_KEY_1
                : key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_4 ? key - GLFW.GLFW_KEY_KP_1 : -1;
        if (i >= 0) {
            choose(i);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void choose(int i) {
        if (chosen >= 0) {
            return;
        }
        chosen = i;
        chosenAt = System.currentTimeMillis();
        play(ModSounds.UI_SELECT, 1.25f, 0.8f);
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (chosen >= 0 && !sent && now - chosenAt >= OPEN_MS) {
            sent = true;
            CompoundTag t = new CompoundTag();
            t.putInt("tin", chosen);
            try {
                ClientPlayNetworking.send(new ClientActionPayload("dalgona.pick", t));
            } catch (IllegalStateException ignored) {
                // disconnected
            }
            play(ModSounds.UI_CONFIRM, 1.1f, 0.7f);
        }
        // a soft tick for the last five seconds
        int left = (int) Math.ceil((endsAtMs - now) / 1000.0);
        if (chosen < 0 && left <= 5 && left > 0 && left != lastTickSecond) {
            lastTickSecond = left;
            play(ModSounds.UI_SELECT, 0.9f, 0.4f);
        }
    }

    private void play(SoundEvent ev, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        // drawn in render()
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        long now = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        CookiePainter.table(g, width, height);

        float intro = CookiePainter.easeOut(Math.min(1f, (now - openedAt) / 500f));
        int hover = chosen < 0 ? tinAt(mx, my) : -1;
        for (int i = 0; i < TINS; i++) {
            float target = i == hover ? 1f : 0f;
            lift[i] += (target - lift[i]) * Math.min(1f, dt * 14f);
        }
        for (int i = 0; i < TINS; i++) {
            float appear = Math.max(0f, Math.min(1f, intro * 1.6f - i * 0.15f));
            float open = chosen == i ? CookiePainter.easeInOut(Math.min(1f, (now - chosenAt) / (float) OPEN_MS)) : 0f;
            float dim = chosen >= 0 && chosen != i ? 0.4f : 1f;
            drawTin(g, i, tinX(i), cy + (1f - appear) * 24f, lift[i], open, appear * dim, now);
        }
        Gfx.end(g);

        // heading
        g.pose().pushPose();
        g.pose().translate(width / 2f, height * 0.11f, 0);
        g.pose().scale(2.2f, 2.2f, 1f);
        g.drawCenteredString(font, Component.translatable("squidgame.game.dalgona.tins.title"), 0, 0, 0xFFFFD27A);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("squidgame.game.dalgona.tins.subtitle"), width / 2, (int) (height * 0.11f) + 24, 0xFFE9D7B0);

        // countdown
        double leftMs = Math.max(0, endsAtMs - now);
        float frac = (float) Math.max(0, Math.min(1, leftMs / totalMs));
        int bw = Math.min(width - 60, 260);
        int bx = (width - bw) / 2;
        int by = (int) (cy + radius + 28);
        int seconds = (int) Math.ceil(leftMs / 1000.0);
        int col = frac < 0.25f ? 0xFFFF5040 : 0xFFF4E4C1;
        g.drawCenteredString(font, Integer.toString(seconds), width / 2, by - 12, col);
        g.fill(bx - 1, by, bx + bw + 1, by + 5, 0xFF000000);
        g.fill(bx, by + 1, bx + bw, by + 4, 0xFF241810);
        g.fill(bx, by + 1, bx + (int) (bw * frac), by + 4, frac < 0.25f ? 0xFFE03A2A : 0xFFE8B04A);

        Component line = chosen >= 0 ? Component.translatable("squidgame.game.dalgona.tins.opening", chosen + 1)
                : Component.translatable("squidgame.game.dalgona.tins.hint");
        g.drawCenteredString(font, line, width / 2, by + 12, chosen >= 0 ? 0xFFFFD27A : CREAM);
        if (chosen < 0) {
            g.drawCenteredString(font, Component.translatable("squidgame.game.dalgona.tins.auto"), width / 2, by + 24, 0xFFB9A98A);
        }
        if (hover >= 0) {
            g.drawCenteredString(font, Component.translatable("squidgame.game.dalgona.tins.tin", hover + 1), (int) tinX(hover), (int) (cy - radius * 1.25f) - 12, 0xFFFFE9B0);
        }
    }

    /** One tin seen from above: shadow, rim, closed lid (or the lid sliding away over the dark honeycomb). */
    private void drawTin(GuiGraphics g, int index, float cx, float cy, float lift, float open, float alpha, long now) {
        if (alpha <= 0.01f) {
            return;
        }
        float r = radius * (1f + 0.07f * lift);
        float bob = (float) Math.sin(now / 420.0 + index * 1.7) * 0.8f * (1f - lift);
        float y = cy - lift * radius * 0.08f + bob;
        // shadow on the table
        Gfx.disc(g, cx + r * 0.05f + lift * 3f, y + r * 0.10f + lift * 4f, r * 1.07f, Gfx.argb((int) (112 * alpha), 0x000000), 40);
        // hover glow
        if (lift > 0.02f) {
            Gfx.glow(g, cx, y, r * 1.5f, 0xFFC040, 0.55f * lift * alpha);
        }
        // body and rim
        Gfx.disc(g, cx, y, r, Gfx.fade(0xFF3C3D44, alpha), 40);
        Gfx.ring(g, cx, y, r * 0.90f, r, Gfx.fade(0xFFA7A9B3, alpha), 40);
        Gfx.ring(g, cx, y, r * 0.935f, r * 0.96f, Gfx.fade(0xFFD9DBE3, alpha), 40);
        Gfx.ring(g, cx, y, r * 0.88f, r * 0.90f, Gfx.fade(0xFF50525B, alpha), 40);
        // inside the tin: the honeycomb, dark until the lid moves
        float inner = r * 0.875f;
        Gfx.disc(g, cx, y, inner, Gfx.fade(Gfx.lerpColor(0xFF1B120A, 0xFFB57A2E, open), alpha), 40);
        if (open > 0.02f) {
            Gfx.ring(g, cx, y, inner * 0.55f, inner * 0.62f, Gfx.argb((int) (90 * open * alpha), 0xFFE2A0), 32);
            Gfx.disc(g, cx - inner * 0.3f, y - inner * 0.32f, inner * 0.18f, Gfx.argb((int) (80 * open * alpha), 0xFFF0C0), 16);
        }
        // the lid slides up and to the right, tilts and fades
        float lidAlpha = alpha * (1f - 0.85f * open);
        float lx = cx + open * r * 1.25f;
        float ly = y - open * r * 1.15f - (float) Math.sin(open * Math.PI) * r * 0.25f;
        float lr = inner * (1f + 0.08f * open);
        if (lidAlpha > 0.02f) {
            Gfx.disc(g, lx + r * 0.03f, ly + r * 0.04f, lr, Gfx.argb((int) (90 * lidAlpha), 0x000000), 40);
            Gfx.disc(g, lx, ly, lr, Gfx.fade(tint(index, 0xFF8E9099), lidAlpha), 40);
            Gfx.ring(g, lx, ly, lr * 0.82f, lr * 0.85f, Gfx.fade(0xFFB4B7C0, lidAlpha), 36);
            Gfx.ring(g, lx, ly, lr * 0.86f, lr * 0.88f, Gfx.fade(0xFF666871, lidAlpha), 36);
            Gfx.ring(g, lx, ly, lr * 0.95f, lr, Gfx.fade(0xFFC6C9D1, lidAlpha), 36);
            // brushed highlight
            Gfx.quad(g, lx - lr * 0.62f, ly - lr * 0.5f, lx - lr * 0.2f, ly - lr * 0.8f, lx - lr * 0.05f, ly - lr * 0.62f, lx - lr * 0.5f, ly - lr * 0.3f,
                    Gfx.argb((int) (60 * lidAlpha), 0xFFFFFF));
            // a ring pull
            Gfx.ring(g, lx + lr * 0.55f, ly - lr * 0.45f, lr * 0.16f, lr * 0.22f, Gfx.fade(0xFFD9DBE3, lidAlpha), 16);
            // number plate
            Gfx.disc(g, lx, ly, lr * 0.46f, Gfx.fade(0xFF2E2F35, lidAlpha), 28);
            Gfx.ring(g, lx, ly, lr * 0.42f, lr * 0.46f, Gfx.fade(0xFFE8B04A, lidAlpha), 28);
        }
        Gfx.end(g);
        if (lidAlpha > 0.05f) {
            g.pose().pushPose();
            g.pose().translate(lx, ly, 0);
            float s = Math.max(1f, lr / 11f);
            g.pose().scale(s, s, 1f);
            g.drawCenteredString(font, Integer.toString(index + 1), 0, -4, Gfx.argb((int) (255 * lidAlpha), 0xFFD27A));
            g.pose().popPose();
        }
    }

    /** Tiny per-tin colour variation (seeded) so the four lids are not clones. */
    private int tint(int index, int base) {
        int h = (int) ((seed * 31 + index * 0x9E3779B1L) >>> 8) & 0x0F;
        int d = h - 8;
        int r = Math.max(0, Math.min(255, ((base >> 16) & 0xFF) + d));
        int gg = Math.max(0, Math.min(255, ((base >> 8) & 0xFF) + d));
        int b = Math.max(0, Math.min(255, (base & 0xFF) + d));
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }
}
