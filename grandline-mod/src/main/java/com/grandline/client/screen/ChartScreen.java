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

    /** Close-up terrain around the player (loaded chunks, one pixel per block), shown when zoomed in. */
    private static final net.minecraft.resources.ResourceLocation LOCAL_ID = com.grandline.GrandLine.id("chart_local");
    private net.minecraft.client.renderer.texture.DynamicTexture local;
    private int localSize, localX, localZ;
    private boolean framed;

    @Override
    protected void init() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (local == null && mc.level != null && mc.player != null) {
            localSize = Math.min(512, (mc.options.getEffectiveRenderDistance() * 2 + 1) * 16);
            localX = mc.player.getBlockX();
            localZ = mc.player.getBlockZ();
            local = new net.minecraft.client.renderer.texture.DynamicTexture(localSize, localSize, true);
            com.grandline.client.TerrainSampler.sample(mc.level, localX, localZ, localSize, local.getPixels());
            local.upload();
            mc.getTextureManager().register(LOCAL_ID, local);
        }
        if (!framed) { // open on the route: zoomed so the player and the target both fit, like a GPS
            framed = true;
            double minX = panX, maxX = panX, minZ = panZ, maxZ = panZ;
            if (com.grandline.client.Guidance.here()) {
                minX = Math.min(minX, com.grandline.client.Guidance.tx);
                maxX = Math.max(maxX, com.grandline.client.Guidance.tx);
                minZ = Math.min(minZ, com.grandline.client.Guidance.tz);
                maxZ = Math.max(maxZ, com.grandline.client.Guidance.tz);
                panX = (minX + maxX) / 2;
                panZ = (minZ + maxZ) / 2;
            }
            double span = Math.max(300, Math.max(maxX - minX, maxZ - minZ) * 1.4);
            zoom = Math.max(1, Math.min(MAX_ZOOM, 70000.0 / span));
        }
    }

    @Override
    public void removed() {
        if (local != null) net.minecraft.client.Minecraft.getInstance().getTextureManager().release(LOCAL_ID);
        super.removed();
    }

    private static final double MAX_ZOOM = 4000;

    private double scale() {
        return Math.min(width, height) / 70000.0 * zoom;
    }

    private int sx(double x) {
        return (int) (width / 2 + (x - panX) * scale());
    }

    private int sz(double z) {
        return (int) (height / 2 + (z - panZ) * scale());
    }

    /** Painted parchment (Higgsfield art); the seas, Red Line and islands are inked over it. */
    private static final net.minecraft.resources.ResourceLocation PARCHMENT = com.grandline.GrandLine.id("textures/gui/chart_parchment.png");
    private static final int INK = 0xFF2B1D0E, INK_FADED = 0xFF7A6A55;

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // the parchment covers the screen (cropped, never stretched out of shape)
        float k = Math.max(width / 1024f, height / 576f);
        int pw = (int) (1024 * k), ph = (int) (576 * k);
        g.blit(PARCHMENT, (width - pw) / 2, (height - ph) / 2, pw, ph, 0, 0, 1024, 576, 1024, 576);
        // Calm Belts and Grand Line bands as ink washes
        g.fill(0, sz(-WorldLayout.CALM_BELT_OUTER), width, sz(-WorldLayout.GRAND_LINE_HALF), 0x405A7A5A);
        g.fill(0, sz(WorldLayout.GRAND_LINE_HALF), width, sz(WorldLayout.CALM_BELT_OUTER), 0x405A7A5A);
        g.fill(0, sz(-WorldLayout.GRAND_LINE_HALF), width, sz(WorldLayout.GRAND_LINE_HALF), 0x302A5F8F);
        for (int rl : new int[]{0, WorldLayout.RED_LINE_B_X, WorldLayout.RED_LINE_C_X}) {
            int a = sx(rl - WorldLayout.RED_LINE_HALF_WIDTH), b = sx(rl + WorldLayout.RED_LINE_HALF_WIDTH);
            g.fill(a, 0, Math.max(a + 2, b), height, 0xC08E2A1E);
        }
        boolean close = scale() > 0.35;
        if (close) { // island land under the terrain close-up, so unloaded parts still read as land
            ListTag land = data.getList("islands", Tag.TAG_COMPOUND);
            for (int i = 0; i < land.size(); i++) {
                CompoundTag e = land.getCompound(i);
                int x = sx(e.getInt("x")), z = sz(e.getInt("z"));
                int r = Math.max(2, (int) (e.getInt("r") * scale()));
                if (x + r < 0 || x - r > width || z + r < 0 || z - r > height) continue;
                g.fill(Math.max(0, x - r), Math.max(0, z - r), Math.min(width, x + r), Math.min(height, z + r), 0xC08FA25A);
            }
        }
        if (close && local != null) { // the real terrain around the player
            int a = sx(localX - localSize / 2.0), b = sz(localZ - localSize / 2.0), c = sx(localX + localSize / 2.0), d = sz(localZ + localSize / 2.0);
            g.blit(LOCAL_ID, a, b, c - a, d - b, 0, 0, localSize, localSize, localSize, localSize);
        }
        ListTag l = data.getList("islands", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            int x = sx(e.getInt("x")), z = sz(e.getInt("z"));
            int r = Math.max(2, (int) (e.getInt("r") * scale()));
            boolean known = e.getBoolean("known");
            if (close) {
                if (zoom < 400) g.renderOutline(x - r, z - r, 2 * r, 2 * r, known ? 0x902B1D0E : 0x607A6A55);
            } else {
                g.fill(x - r - 1, z - r - 1, x + r + 1, z + r + 1, known ? INK : 0x807A6A55);
                g.fill(x - r, z - r, x + r, z + r, known ? 0xFF8FA25A : 0x60B8A880);
            }
            if (zoom > 1.8 || known) {
                Component name = e.getString("name").isEmpty() ? Component.translatable("island.grandline." + e.getString("id")) : Component.literal(e.getString("name"));
                g.drawString(font, name, x + r + 2, z - 4, known ? INK : INK_FADED, false);
            }
        }
        // treasure map targets the player knows: a red ink X (faded once the treasure has been found)
        ListTag tr = data.getList("treasures", Tag.TAG_COMPOUND);
        for (int i = 0; i < tr.size(); i++) {
            CompoundTag e = tr.getCompound(i);
            int x = sx(e.getInt("x")), z = sz(e.getInt("z"));
            int col = e.getBoolean("solved") ? 0xA07A6A55 : 0xFFC0201C;
            for (int q = -2; q <= 2; q++) {
                g.fill(x + q, z + q, x + q + 1, z + q + 1, col);
                g.fill(x + q, z - q, x + q + 1, z - q + 1, col);
            }
            if (zoom > 6) {
                Component isl = e.getString("name").isEmpty() ? Component.translatable("island.grandline." + e.getString("island")) : Component.literal(e.getString("name"));
                g.drawString(font, Component.translatable("screen.grandline.chart_treasure", isl), x + 4, z + 3, col, false);
            }
        }
        // claimed islands: a little flag on its pole, with the crew's name (yours in gold)
        ListTag bases = data.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < bases.size(); i++) {
            CompoundTag e = bases.getCompound(i);
            int x = sx(e.getInt("x")), z = sz(e.getInt("z"));
            int col = e.getBoolean("mine") ? 0xFFC8901C : 0xFF1E1A16;
            g.fill(x, z - 7, x + 1, z + 1, INK);
            g.fill(x + 1, z - 7, x + 6, z - 3, col);
            Component crew = e.getString("crew").isEmpty() ? Component.translatable("grandline.base.default_crew", e.getString("founder")) : Component.literal(e.getString("crew"));
            if (zoom > 1.2 || e.getBoolean("mine")) g.drawString(font, Component.translatable("screen.grandline.chart_base", crew), x + 7, z - 8, col, false);
        }
        // the player's sea jobs: a named pirate ship's last-seen point (and where she is once sighted), a convoy's port and
        // her merchantman, an escort's destination: a ring of ink in the job's colour with its label
        ListTag jobs = data.getList("seajobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag e = jobs.getCompound(i);
            String kind = e.getString("kind");
            int x = sx(e.getInt("x")), z = sz(e.getInt("z"));
            int col = kind.equals("sink") || kind.equals("sighted") ? 0xFF8E1A14 : kind.startsWith("convoy") ? 0xFF1C4E80 : 0xFF2E6B2A;
            int r = kind.equals("sink") ? 4 : 3;
            for (int q = -r; q <= r; q++) { // a ring (a hollow square at this size), filled for a ship seen now
                g.fill(x + q, z - r, x + q + 1, z - r + 1, col);
                g.fill(x + q, z + r, x + q + 1, z + r + 1, col);
                g.fill(x - r, z + q, x - r + 1, z + q + 1, col);
                g.fill(x + r, z + q, x + r + 1, z + q + 1, col);
            }
            if (kind.equals("sighted") || kind.equals("convoy_ship")) g.fill(x - 1, z - 1, x + 2, z + 2, col);
            Component isl = e.getString("island").isEmpty() ? Component.empty()
                    : e.getString("islandName").isEmpty() ? Component.translatable("island.grandline." + e.getString("island")) : Component.literal(e.getString("islandName"));
            g.drawString(font, Component.translatable("screen.grandline.chart_job." + kind, e.getString("name"), isl), x + r + 3, z - 4, col, false);
        }
        // the tracked quest's route (GPS trail) and its target
        if (com.grandline.client.Guidance.here()) {
            int[] rt = com.grandline.client.Guidance.route;
            for (int i = 0; i + 3 < rt.length; i += 2) line(g, sx(rt[i]), sz(rt[i + 1]), sx(rt[i + 2]), sz(rt[i + 3]));
            if (rt.length < 4) line(g, sx(data.getInt("px")), sz(data.getInt("pz")), sx(com.grandline.client.Guidance.tx), sz(com.grandline.client.Guidance.tz));
            int tx = sx(com.grandline.client.Guidance.tx), tz = sz(com.grandline.client.Guidance.tz);
            com.grandline.client.Minimap.marker(g, tx, tz);
            if (com.grandline.client.Guidance.label != null)
                g.drawString(font, com.grandline.client.Guidance.label, tx + 8, tz - 4, 0xFFFFD23C, true);
        }
        int px = sx(data.getInt("px")), pz = sz(data.getInt("pz"));
        for (int i = -3; i <= 3; i++) { // you are here: a red ink cross
            g.fill(px + i, pz + i, px + i + 1, pz + i + 1, 0xFFB01818);
            g.fill(px + i, pz - i, px + i + 1, pz - i + 1, 0xFFB01818);
        }
        Component title = Component.translatable("screen.grandline.chart_title", data.getInt("discovered"), data.getInt("total"));
        g.drawString(font, title, (width - font.width(title)) / 2, 8, INK, false);
        if (com.grandline.client.Guidance.active()) { // what the trail leads to
            Component obj = com.grandline.client.Guidance.objective != null ? com.grandline.client.Guidance.objective : com.grandline.client.Guidance.title;
            Component q = Component.translatable("screen.grandline.chart_guide", com.grandline.client.Guidance.title, obj);
            g.fill((width - font.width(q)) / 2 - 4, 19, (width + font.width(q)) / 2 + 4, 31, 0xA0201810);
            g.drawString(font, q, (width - font.width(q)) / 2, 21, 0xFFFFD23C, false);
        }
        Component help = Component.translatable("screen.grandline.chart_help");
        g.drawString(font, help, (width - font.width(help)) / 2, height - 12, INK_FADED, false);
        super.render(g, mx, my, pt);
    }

    /** A leg of the route: a thick yellow line with a dark edge (marched in screen pixels, clipped to the screen). */
    private void line(GuiGraphics g, int ax, int az, int bx, int bz) {
        if (Math.max(ax, bx) < -4 || Math.min(ax, bx) > width + 4 || Math.max(az, bz) < -4 || Math.min(az, bz) > height + 4) return;
        double len = Math.hypot(bx - ax, bz - az);
        int steps = (int) Math.min(6000, Math.max(1, len));
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                int x = (int) (ax + (bx - ax) * t), y = (int) (az + (bz - az) * t);
                if (x < -2 || x > width + 2 || y < -2 || y > height + 2) continue;
                if (pass == 0) g.fill(x - 2, y - 2, x + 3, y + 3, com.grandline.client.Minimap.TRAIL_EDGE);
                else g.fill(x - 1, y - 1, x + 2, y + 2, com.grandline.client.Minimap.TRAIL);
            }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double sxv, double syv) {
        zoom = Math.max(0.5, Math.min(MAX_ZOOM, zoom * (syv > 0 ? 1.25 : 0.8)));
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
