package com.grandline.client.screen;

import com.grandline.world.WorldLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Sea chart of the ocean world: Red Line, Grand Line, Calm Belts, Blues, and known islands. Drag to pan, scroll to zoom. */
public class ChartScreen extends Screen {
    private final CompoundTag data;
    private double zoom = 1, panX, panZ;

    public ChartScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.chart"));
        this.data = data;
        panX = data.getInt("px");
        panZ = data.getInt("pz");
    }

    private double scale() {
        return Math.min(width, height) / 70000.0 * zoom;
    }

    private int sx(double x) {
        return (int) (width / 2 + (x - panX) * scale());
    }

    private int sz(double z) {
        return (int) (height / 2 + (z - panZ) * scale());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF0B2A4A);
        // Calm Belts and Grand Line bands
        g.fill(0, sz(-WorldLayout.CALM_BELT_OUTER), width, sz(-WorldLayout.GRAND_LINE_HALF), 0xFF1E5A6A);
        g.fill(0, sz(WorldLayout.GRAND_LINE_HALF), width, sz(WorldLayout.CALM_BELT_OUTER), 0xFF1E5A6A);
        g.fill(0, sz(-WorldLayout.GRAND_LINE_HALF), width, sz(WorldLayout.GRAND_LINE_HALF), 0xFF123E6E);
        for (int rl : new int[]{0, WorldLayout.RED_LINE_B_X, WorldLayout.RED_LINE_C_X}) {
            int a = sx(rl - WorldLayout.RED_LINE_HALF_WIDTH), b = sx(rl + WorldLayout.RED_LINE_HALF_WIDTH);
            g.fill(a, 0, Math.max(a + 2, b), height, 0xFFB23A2A);
        }
        ListTag l = data.getList("islands", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            int x = sx(e.getInt("x")), z = sz(e.getInt("z"));
            int r = Math.max(2, (int) (e.getInt("r") * scale()));
            boolean known = e.getBoolean("known");
            g.fill(x - r, z - r, x + r, z + r, known ? 0xFF6FBF5A : 0xFF4A6A4A);
            if (zoom > 1.8 || known) {
                Component name = e.getString("name").isEmpty() ? Component.translatable("island.grandline." + e.getString("id")) : Component.literal(e.getString("name"));
                g.drawString(font, name, x + r + 2, z - 4, known ? 0xFFFFFFFF : 0xFF9AA0A0, false);
            }
        }
        int px = sx(data.getInt("px")), pz = sz(data.getInt("pz"));
        g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFFF3030);
        g.drawCenteredString(font, Component.translatable("screen.grandline.chart_title", data.getInt("discovered"), data.getInt("total")), width / 2, 6, 0xFFFFD34A);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double sxv, double syv) {
        zoom = Math.max(0.5, Math.min(40, zoom * (syv > 0 ? 1.25 : 0.8)));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        panX -= dx / scale();
        panZ -= dy / scale();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
