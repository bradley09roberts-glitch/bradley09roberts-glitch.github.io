package com.grandline.client.screen;

import com.grandline.GrandLine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A treasure map held up to the screen: the island inked on parchment from the server's land mask (deep water,
 * shallows, beach, lowland, highland), a red X on the secret, a compass rose, the hint for the kind of secret and a
 * rough bearing and distance from where the reader stands. Found treasure gets a stamp.
 */
public class TreasureMapScreen extends Screen {
    private static final ResourceLocation PARCHMENT = GrandLine.id("textures/gui/chart_parchment.png");
    private static final int INK = 0xFF2B1D0E, RED = 0xFFB01818;
    private static final int[] FILL = {0x00000000, 0x305A86A8, 0xFFD9C08C, 0xFF93A55E, 0xFF7E6440};

    private final CompoundTag d;
    private final byte[] mask;
    private final int n;

    public TreasureMapScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.treasure_map"));
        this.d = data;
        this.mask = data.getByteArray("mask");
        this.n = Math.max(1, data.getInt("n"));
    }

    private int level(int i, int j) {
        if (i < 0 || j < 0 || i >= n || j >= n || mask.length < n * n) return 0;
        return mask[j * n + i];
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int side = Math.max(96, Math.min(width - 40, height - 92));
        int x0 = (width - side) / 2, y0 = 30;
        // parchment sheet (a square crop of the painted chart parchment)
        g.blit(PARCHMENT, x0 - 14, y0 - 24, side + 28, side + 84, 224, 0, 576, 576, 1024, 576);
        float cs = (float) side / n;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int lv = level(i, j);
                int ax = x0 + (int) (i * cs), ay = y0 + (int) (j * cs), bx = x0 + (int) ((i + 1) * cs), by = y0 + (int) ((j + 1) * cs);
                if (lv > 0) g.fill(ax, ay, bx, by, FILL[Math.min(4, lv)]);
                boolean land = lv >= 2;
                // coastline in ink: land next to water
                if (land && (level(i - 1, j) < 2 || level(i + 1, j) < 2 || level(i, j - 1) < 2 || level(i, j + 1) < 2)) g.fill(ax, ay, bx, by, INK);
                // highland hatching
                if (lv == 4 && (i + j) % 3 == 0) g.fill(ax + (bx - ax) / 3, ay + (by - ay) / 3, ax + (bx - ax) * 2 / 3 + 1, ay + (by - ay) * 2 / 3 + 1, 0xFF4A3A22);
            }
        int span = Math.max(1, d.getInt("span")), wx0 = d.getInt("x0"), wz0 = d.getInt("z0");
        int tx = x0 + (int) ((d.getInt("tx") - wx0) / (float) span * side), tz = y0 + (int) ((d.getInt("tz") - wz0) / (float) span * side);
        // the X
        int r = Math.max(4, side / 40);
        for (int k = -r; k <= r; k++) {
            g.fill(tx + k - 1, tz + k - 1, tx + k + 2, tz + k + 2, RED);
            g.fill(tx + k - 1, tz - k - 1, tx + k + 2, tz - k + 2, RED);
        }
        // compass rose
        int cx = x0 + side - 16, cy = y0 + 18;
        g.fill(cx - 1, cy - 10, cx + 1, cy + 10, INK);
        g.fill(cx - 10, cy - 1, cx + 10, cy + 1, INK);
        g.fill(cx - 2, cy - 10, cx + 2, cy - 6, RED);
        g.drawString(font, "N", cx - font.width("N") / 2, cy - 20, INK, false);
        // the reader, when standing on this map
        int px = d.getInt("px"), pz = d.getInt("pz");
        boolean onMap = d.getBoolean("here") && px >= wx0 && px < wx0 + span && pz >= wz0 && pz < wz0 + span;
        if (onMap) {
            int sx = x0 + (int) ((px - wx0) / (float) span * side), sz = y0 + (int) ((pz - wz0) / (float) span * side);
            g.fill(sx - 2, sz - 2, sx + 3, sz + 3, INK);
            g.fill(sx - 1, sz - 1, sx + 2, sz + 2, 0xFFE8D8B0);
        }
        // title, hint and bearing
        Component isl = d.getString("name").isEmpty() ? Component.translatable("island.grandline." + d.getString("island")) : Component.literal(d.getString("name"));
        Component title = Component.translatable(d.getBoolean("grand") ? "screen.grandline.treasure_map.title_grand" : "screen.grandline.treasure_map.title", isl);
        g.drawString(font, title, (width - font.width(title)) / 2, y0 - 16, INK, false);
        int ty = y0 + side + 6;
        Component hint = Component.translatable("grandline.treasure.kind." + d.getString("kind"));
        g.drawString(font, hint, (width - font.width(hint)) / 2, ty, INK, false);
        Component bearing;
        if (!d.getBoolean("here")) bearing = Component.translatable("screen.grandline.treasure_map.elsewhere");
        else {
            double dx = d.getInt("tx") - px, dz = d.getInt("tz") - pz;
            String dir = compass(dx, dz), dist = rough(Math.sqrt(dx * dx + dz * dz));
            bearing = Component.translatable(onMap ? "screen.grandline.treasure_map.near" : "screen.grandline.treasure_map.bearing", dir, dist);
        }
        g.drawString(font, bearing, (width - font.width(bearing)) / 2, ty + 12, 0xFF5A3A1A, false);
        Component zone = Component.translatable("zone.grandline." + d.getString("zone"));
        g.drawString(font, zone, (width - font.width(zone)) / 2, ty + 24, 0xFF7A6A55, false);
        if (d.getBoolean("solved")) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, y0 + side / 2f, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-18));
            g.pose().scale(3f, 3f, 1);
            Component found = Component.translatable("screen.grandline.treasure_map.found");
            g.drawString(font, found, -font.width(found) / 2, -4, 0xD0B01818, false);
            g.pose().popPose();
        }
    }

    static String compass(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(ang / 45) % 8];
    }

    /** A sailor's estimate: rounded to 10, 50, 100 or 500 blocks. */
    static String rough(double dist) {
        long step = dist < 100 ? 10 : dist < 600 ? 50 : dist < 3000 ? 100 : 500;
        return String.format("%,d", Math.max(step, Math.round(dist / step) * step));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
