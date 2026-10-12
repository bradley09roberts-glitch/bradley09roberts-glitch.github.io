package com.grandline.client.screen;

import com.grandline.client.render.ShipDecor;
import com.grandline.net.GLNetwork;
import com.grandline.ship.ShipLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Shipwright: buy a ship, upgrade/repair/rename the moored ship, recover sunk ships, sell or refit captured prizes,
 * and (second page) customise the moored ship: figurehead carving, flag emblem and colours, sail and hull paint,
 * with a live preview.
 */
public class ShipyardScreen extends Screen {
    private static final int TOP = 36, ROW = 20;
    private final CompoundTag data;
    private EditBox name;
    private boolean customisePage;
    // customisation choices (indices into ShipLook lists/palettes)
    private int fig, emb, embCol, flagCol, sailCol, hullCol;
    private final boolean hasShip;

    public ShipyardScreen(CompoundTag data) {
        this(data, data.getBoolean("customisePage"));
    }

    public ShipyardScreen(CompoundTag data, boolean customisePage) {
        super(Component.translatable("screen.grandline.shipyard"));
        this.data = data;
        this.customisePage = customisePage;
        this.hasShip = data.contains("current");
        CompoundTag cur = data.getCompound("current");
        ShipLook look = ShipLook.decode(cur.getString("look"));
        fig = Math.max(0, ShipLook.FIGUREHEADS.indexOf(look.figurehead()));
        emb = Math.max(0, ShipLook.EMBLEMS.indexOf(look.emblem()));
        embCol = nearest(ShipLook.EMBLEM_COLORS, look.emblemColor());
        flagCol = nearest(ShipLook.FLAGS, look.flagColor());
        sailCol = nearest(ShipLook.SAILS, cur.contains("sail") ? cur.getInt("sail") : ShipLook.SAILS[0]);
        hullCol = nearest(ShipLook.HULLS, cur.contains("hullc") ? cur.getInt("hullc") : ShipLook.HULLS[0]);
    }

    private static int nearest(int[] pal, int rgb) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < pal.length; i++) {
            long dr = ((pal[i] >> 16) & 255) - ((rgb >> 16) & 255), dg = ((pal[i] >> 8) & 255) - ((rgb >> 8) & 255), db = (pal[i] & 255) - (rgb & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private void send(CompoundTag t) {
        t.putInt("npc", data.getInt("npc"));
        PacketDistributor.sendToServer(new GLNetwork.UiAction("ship", t));
    }

    private List<String> emblems() {
        // the Marine gull is only offered to Marines
        return data.getBoolean("marine") ? ShipLook.EMBLEMS : ShipLook.EMBLEMS.subList(0, ShipLook.EMBLEMS.size() - 1);
    }

    @Override
    protected void init() {
        if (customisePage) initCustomise();
        else initMain();
    }

    private void initMain() {
        int x = width / 2 - 200, y = TOP;
        ListTag classes = data.getList("classes", Tag.TAG_COMPOUND);
        for (int i = 0; i < classes.size(); i++) {
            CompoundTag c = classes.getCompound(i);
            String id = c.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.buy_ship", Component.translatable("ship_class.grandline." + id), String.format("%,d", c.getLong("price"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "buy");
                t.putString("cls", id);
                send(t);
            }).bounds(x, y + i * ROW, 190, ROW - 2).build());
        }
        ListTag ups = data.getList("upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < ups.size(); i++) {
            CompoundTag u = ups.getCompound(i);
            String id = u.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.buy_upgrade", Component.translatable("ship_upgrade.grandline." + id), String.format("%,d", u.getLong("price"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "upgrade");
                t.putString("upgrade", id);
                send(t);
            }).bounds(x + 205, y + i * ROW, 195, ROW - 2).build());
        }
        int ry = y + Math.max(classes.size(), ups.size()) * ROW + 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.repair"), b -> {
            CompoundTag t = new CompoundTag();
            t.putString("op", "repair");
            send(t);
        }).bounds(x, ry, 90, 20).build());
        name = new EditBox(font, x + 95, ry, 140, 20, Component.translatable("screen.grandline.ship_name"));
        name.setMaxLength(24);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.rename"), b -> {
            CompoundTag t = new CompoundTag();
            t.putString("op", "rename");
            t.putString("name", name.getValue());
            send(t);
        }).bounds(x + 240, ry, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.customise"), b -> {
            customisePage = true;
            rebuildWidgets();
        }).bounds(x + 315, ry, 85, 20).build());
        int ly = ry + ROW + 4;
        ListTag owned = data.getList("owned", Tag.TAG_COMPOUND);
        for (int i = 0; i < owned.size(); i++) {
            CompoundTag o = owned.getCompound(i);
            if (!o.getBoolean("sunk")) continue;
            String sid = o.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.recover", o.getString("name")), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "recover");
                t.putString("ship", sid);
                send(t);
            }).bounds(x, ly, 250, ROW - 2).build());
            ly += ROW;
        }
        ListTag prizes = data.getList("prizes", Tag.TAG_COMPOUND);
        for (int i = 0; i < prizes.size(); i++) {
            CompoundTag p = prizes.getCompound(i);
            String sid = p.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.sell_prize", p.getString("name"), String.format("%,d", p.getLong("value"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "sell_prize");
                t.putString("ship", sid);
                send(t);
            }).bounds(x, ly, 196, ROW - 2).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.claim_prize", String.format("%,d", p.getLong("refit"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "claim_prize");
                t.putString("ship", sid);
                send(t);
            }).bounds(x + 200, ly, 150, ROW - 2).build());
            ly += ROW;
        }
    }

    private void initCustomise() {
        int x = width / 2 - 200, y = TOP;
        String[][] rows = {{"figurehead"}, {"emblem"}, {"emblem_colour"}, {"flag_colour"}, {"sail_colour"}, {"hull_paint"}};
        for (int i = 0; i < rows.length; i++) {
            final int row = i;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(row, -1)).bounds(x, y + i * ROW, 20, ROW - 2).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(row, 1)).bounds(x + 170, y + i * ROW, 20, ROW - 2).build());
        }
        Button apply = Button.builder(Component.translatable("screen.grandline.apply_look"), b -> {
            CompoundTag t = new CompoundTag();
            t.putString("op", "customise");
            t.putString("figurehead", ShipLook.FIGUREHEADS.get(fig));
            t.putString("emblem", emblems().get(Math.min(emb, emblems().size() - 1)));
            t.putInt("emblemc", ShipLook.EMBLEM_COLORS[embCol]);
            t.putInt("flagc", ShipLook.FLAGS[flagCol]);
            t.putInt("sail", ShipLook.SAILS[sailCol]);
            t.putInt("hullc", ShipLook.HULLS[hullCol]);
            send(t);
        }).bounds(x, y + 6 * ROW + 4, 120, 20).build();
        apply.active = hasShip;
        addRenderableWidget(apply);
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.back"), b -> {
            customisePage = false;
            rebuildWidgets();
        }).bounds(x + 125, y + 6 * ROW + 4, 65, 20).build());
    }

    private void cycle(int row, int dir) {
        switch (row) {
            case 0 -> fig = Math.floorMod(fig + dir, ShipLook.FIGUREHEADS.size());
            case 1 -> emb = Math.floorMod(emb + dir, emblems().size());
            case 2 -> embCol = Math.floorMod(embCol + dir, ShipLook.EMBLEM_COLORS.length);
            case 3 -> flagCol = Math.floorMod(flagCol + dir, ShipLook.FLAGS.length);
            case 4 -> sailCol = Math.floorMod(sailCol + dir, ShipLook.SAILS.length);
            default -> hullCol = Math.floorMod(hullCol + dir, ShipLook.HULLS.length);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, customisePage ? Component.translatable("screen.grandline.customise_title") : title, width / 2, 12, 0xFFFFD34A);
        g.drawCenteredString(font, Component.literal("฿ " + String.format("%,d", data.getLong("beli"))), width / 2, 24, 0xFFE8D27A);
        if (customisePage) renderCustomise(g);
        else renderMain(g);
    }

    private void renderMain(GuiGraphics g) {
        int x = width / 2 - 200;
        int ry = TOP + Math.max(data.getList("classes", Tag.TAG_COMPOUND).size(), data.getList("upgrades", Tag.TAG_COMPOUND).size()) * ROW + 4;
        ListTag owned = data.getList("owned", Tag.TAG_COMPOUND);
        int oy = ry + ROW + 4, sunk = 0;
        for (int i = 0; i < owned.size(); i++) if (owned.getCompound(i).getBoolean("sunk")) sunk++;
        oy += (sunk + data.getList("prizes", Tag.TAG_COMPOUND).size()) * ROW;
        for (int i = 0; i < owned.size(); i++) {
            CompoundTag o = owned.getCompound(i);
            if (o.getBoolean("sunk")) continue;
            g.drawString(font, Component.translatable("screen.grandline.owned_ship", o.getString("name"), Component.translatable("ship_class.grandline." + o.getString("cls")), o.getInt("dist")), x, oy, 0xFFCCCCCC);
            oy += 11;
        }
    }

    private void renderCustomise(GuiGraphics g) {
        int x = width / 2 - 200, y = TOP;
        String emblem = emblems().get(Math.min(emb, emblems().size() - 1));
        Component[] labels = {
                Component.translatable("screen.grandline.figurehead", Component.translatable("figurehead.grandline." + ShipLook.FIGUREHEADS.get(fig))),
                Component.translatable("screen.grandline.emblem", Component.translatable("emblem.grandline." + emblem)),
                Component.translatable("screen.grandline.emblem_colour"),
                Component.translatable("screen.grandline.flag_colour"),
                Component.translatable("screen.grandline.sail_colour"),
                Component.translatable("screen.grandline.hull_paint")};
        int[] swatch = {-1, -1, ShipLook.EMBLEM_COLORS[embCol], ShipLook.FLAGS[flagCol], ShipLook.SAILS[sailCol], ShipLook.HULLS[hullCol]};
        for (int i = 0; i < labels.length; i++) {
            int ly = y + i * ROW;
            g.fill(x + 22, ly, x + 168, ly + ROW - 2, 0x88000000);
            g.drawCenteredString(font, labels[i], x + 95 - (swatch[i] >= 0 ? 8 : 0), ly + (ROW - 2 - 8) / 2, 0xFFFFFFFF);
            if (swatch[i] >= 0) {
                g.fill(x + 146, ly + 3, x + 162, ly + ROW - 5, 0xFF000000);
                g.fill(x + 147, ly + 4, x + 161, ly + ROW - 6, 0xFF000000 | swatch[i]);
            }
        }
        CompoundTag cur = data.getCompound("current");
        int py = y + 6 * ROW + 28;
        if (!hasShip) g.drawString(font, Component.translatable("grandline.ship.bring_ship"), x, py, 0xFFFF8080);
        else {
            ShipLook was = ShipLook.decode(cur.getString("look"));
            long fee = 2000L + (was.figurehead().equals(ShipLook.FIGUREHEADS.get(fig)) ? 0 : 8000L);
            g.drawString(font, Component.translatable("screen.grandline.look_fee", cur.getString("name"), String.format("%,d", fee)), x, py, 0xFFE8D27A);
        }
        // ---- preview: the flag, and the ship side-on with sail emblem, paint and figurehead
        int px = x + 205, fy = y;
        int fw = 96, fh = 58;
        g.fill(px - 2, fy - 2, px + fw + 2, fy + fh + 2, 0xFF3A2A1A);
        g.fill(px, fy, px + fw, fy + fh, 0xFF000000 | ShipLook.FLAGS[flagCol]);
        drawEmblem(g, emblem, px + fw / 2, fy + fh / 2, 48, ShipLook.EMBLEM_COLORS[embCol]);
        int sy = fy + fh + 12, hullTop = sy + 62, hullBot = hullTop + 18;
        int sx0 = px - 4, sx1 = sx0 + 118;
        int sail = 0xFF000000 | ShipLook.SAILS[sailCol];
        g.fill(sx0 + 24, sy, sx0 + 26, hullTop, 0xFF5A3A22); // masts
        g.fill(sx0 + 66, sy + 4, sx0 + 68, hullTop, 0xFF5A3A22);
        g.fill(sx0 + 6, sy + 8, sx0 + 44, hullTop - 12, sail);
        g.fill(sx0 + 48, sy + 12, sx0 + 88, hullTop - 8, sail);
        drawEmblem(g, emblem, sx0 + 68, sy + 12 + (hullTop - 8 - sy - 12) / 2, 30, ShipLook.EMBLEM_COLORS[embCol]);
        g.fill(sx0 + 26, sy, sx0 + 40, sy + 9, 0xFF000000 | ShipLook.FLAGS[flagCol]);
        for (int i = 0; i < hullBot - hullTop; i++) { // hull: a tapered side view
            int inset = i * 8 / (hullBot - hullTop);
            g.fill(sx0 + inset, hullTop + i, sx1 - 14 - inset * 2, hullTop + i + 1, 0xFF000000 | ShipLook.HULLS[hullCol]);
        }
        g.fill(sx0, hullTop, sx1 - 14, hullTop + 2, 0xFF3A2414);
        g.fill(sx0 - 4, hullBot - 2, sx1 + 4, hullBot + 4, 0xFF2A5A9A); // sea
        int fc = 0xFF000000 | figureheadColour(ShipLook.FIGUREHEADS.get(fig));
        g.fill(sx1 - 16, hullTop - 10, sx1 - 6, hullTop + 3, fc);
        g.fill(sx1 - 8, hullTop - 8, sx1 - 1, hullTop - 2, fc);
        g.drawCenteredString(font, Component.translatable("figurehead.grandline." + ShipLook.FIGUREHEADS.get(fig)), sx1 - 10, hullTop - 20, 0xFFFFE8A0);
    }

    private static int figureheadColour(String f) {
        return switch (f) {
            case "lion" -> 0xE8A030;
            case "ram" -> 0xEDE6D6;
            case "swan" -> 0xF8F8F8;
            case "dragon" -> 0x3A8A4A;
            case "skull" -> 0xEEE8D8;
            case "serpent" -> 0x2A6EA0;
            default -> 0xE2B33C;
        };
    }

    /** Emblem bitmap centred at (cx, cy), {@code size} pixels tall. */
    private void drawEmblem(GuiGraphics g, String id, int cx, int cy, int size, int rgb) {
        String[] bmp = ShipDecor.emblem(id);
        int px = Math.max(1, size / bmp.length);
        int w = bmp[0].length() * px, h = bmp.length * px;
        int x0 = cx - w / 2, y0 = cy - h / 2;
        for (int[] r : ShipDecor.runs(bmp)) g.fill(x0 + r[1] * px, y0 + r[0] * px, x0 + r[2] * px, y0 + (r[0] + 1) * px, 0xFF000000 | rgb);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
