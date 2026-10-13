package com.grandline.client.screen;

import com.grandline.net.GLNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Crew roster (V): the active companions (at most two) with Follow/Hold and Rest, the reserve with Join or a swap for
 * one of the active pair, each with role, health and bond rank; Focus and Regroup for everyone. Swaps are greyed out
 * unless the captain is at a safe place (a town, or aboard their ship) and out of combat; the server checks again.
 */
public class CrewScreen extends Screen {
    private static final int GOLD = 0xFFE8C060, TEXT = 0xFFE6E0D2, DIM = 0xFFA09A8C, GOOD = 0xFF7CC860, BAD = 0xFFE05A4A, PANEL = 0xE0181410, EDGE = 0xFF6E5A3A;
    private static final int ROW = 24;
    private final List<CompoundTag> active = new ArrayList<>(), reserve = new ArrayList<>();
    private final boolean safe, combat;
    private final int activeMax, max;
    private int scroll, px, py, pw, ph, reserveTop, reserveRows;

    public CrewScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.crew"));
        ListTag l = data.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) (l.getCompound(i).getBoolean("active") ? active : reserve).add(l.getCompound(i));
        safe = data.getBoolean("safe");
        combat = data.getBoolean("combat");
        activeMax = Math.max(1, data.getInt("activeMax"));
        max = data.getInt("max");
    }

    private boolean canSwap() {
        return safe && !combat;
    }

    private void send(String op, String id, String out) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        t.putString("id", id);
        if (!out.isEmpty()) t.putString("out", out);
        t.putBoolean("screen", true); // the server answers with the updated roster
        PacketDistributor.sendToServer(new GLNetwork.UiAction("crew", t));
    }

    @Override
    protected void init() {
        pw = Math.min(380, width - 16);
        ph = Math.min(300, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int bx = px + pw - 8;
        int y = py + 36;
        for (int i = 0; i < activeMax; i++, y += ROW) {
            if (i >= active.size()) continue;
            CompoundTag m = active.get(i);
            String id = m.getString("id");
            boolean holding = m.getString("order").equals("hold");
            Button rest = Button.builder(Component.translatable("screen.grandline.crew.rest"), b -> send("rest", id, ""))
                    .bounds(bx - 46, y, 46, 20).build();
            rest.active = canSwap();
            addRenderableWidget(rest);
            addRenderableWidget(Button.builder(Component.translatable(holding ? "screen.grandline.crew.follow" : "screen.grandline.crew.hold"),
                    b -> send(holding ? "follow" : "hold", id, "")).bounds(bx - 46 - 52, y, 50, 20).build());
        }
        reserveTop = y + 14;
        int footer = py + ph - 26;
        reserveRows = Math.max(1, (footer - 6 - reserveTop) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, reserve.size() - reserveRows)));
        for (int r = 0; r < reserveRows && scroll + r < reserve.size(); r++) {
            CompoundTag m = reserve.get(scroll + r);
            String id = m.getString("id");
            int ry = reserveTop + r * ROW;
            if (active.size() < activeMax) {
                Button join = Button.builder(Component.translatable("screen.grandline.crew.join"), b -> send("join", id, "")).bounds(bx - 46, ry, 46, 20).build();
                join.active = canSwap();
                addRenderableWidget(join);
            } else {
                // full: swap with one of the active pair (named on the button)
                int x = bx;
                for (int i = active.size() - 1; i >= 0; i--) {
                    CompoundTag a = active.get(i);
                    String outId = a.getString("id");
                    Component label = Component.translatable("screen.grandline.crew.swap", shortName(a));
                    int w = Math.max(54, font.width(label) + 10);
                    x -= w + 2;
                    Button sw = Button.builder(label, b -> send("swap", id, outId)).bounds(x + 2, ry, w, 20).build();
                    sw.active = canSwap();
                    addRenderableWidget(sw);
                }
            }
        }
        int fw = 80;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.crew.focus"), b -> send("focus", "", "")).bounds(px + 8, footer, fw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.crew.regroup"), b -> send("regroup", "", "")).bounds(px + 12 + fw, footer, fw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(px + pw - 8 - fw, footer, fw, 20).build());
    }

    private Component shortName(CompoundTag m) {
        String full = Component.translatable(m.getString("name")).getString();
        int sp = full.indexOf(' ');
        return Component.literal(sp > 0 ? full.substring(0, sp) : full);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, EDGE);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.drawString(font, title, px + 8, py + 7, GOLD, false);
        Component count = Component.translatable("screen.grandline.crew.count", active.size(), activeMax, active.size() + reserve.size(), max);
        g.drawString(font, count, px + pw - 8 - font.width(count), py + 7, DIM, false);
        Component place = Component.translatable(combat ? "screen.grandline.crew.in_combat" : safe ? "screen.grandline.crew.safe" : "screen.grandline.crew.unsafe");
        g.drawString(font, place, px + 8, py + 18, combat ? BAD : safe ? GOOD : DIM, false);

        int y = py + 36;
        g.drawString(font, Component.translatable("screen.grandline.crew.active"), px + 8, y - 9, GOLD, false);
        for (int i = 0; i < activeMax; i++, y += ROW) {
            if (i < active.size()) row(g, active.get(i), px + 8, y, true);
            else g.drawString(font, Component.translatable("screen.grandline.crew.empty_slot").withStyle(ChatFormatting.ITALIC), px + 12, y + 6, DIM, false);
        }
        g.drawString(font, Component.translatable("screen.grandline.crew.reserve", reserve.size()), px + 8, reserveTop - 10, GOLD, false);
        if (reserve.isEmpty() && active.isEmpty()) {
            g.drawString(font, Component.translatable("screen.grandline.crew.none"), px + 12, reserveTop + 6, DIM, false);
        } else if (reserve.isEmpty()) {
            g.drawString(font, Component.translatable("screen.grandline.crew.no_reserve"), px + 12, reserveTop + 6, DIM, false);
        }
        for (int r = 0; r < reserveRows && scroll + r < reserve.size(); r++) row(g, reserve.get(scroll + r), px + 8, reserveTop + r * ROW, false);
        if (reserve.size() > reserveRows)
            g.drawString(font, Component.literal((scroll + 1) + "-" + Math.min(reserve.size(), scroll + reserveRows) + " / " + reserve.size()), px + pw - 60, reserveTop - 10, DIM, false);
        if (!canSwap())
            g.drawString(font, Component.translatable("screen.grandline.crew.swap_hint"), px + 8, py + ph - 38, DIM, false);
        for (var w : renderables) w.render(g, mx, my, pt);
    }

    /** Name, role, health bar, bond rank and status of one member. */
    private void row(GuiGraphics g, CompoundTag m, int x, int y, boolean isActive) {
        boolean downed = m.getBoolean("downed");
        Component name = Component.translatable(m.getString("name"));
        g.drawString(font, name, x + 2, y + 1, downed ? BAD : TEXT, false);
        Component role = Component.translatable("companion_role.grandline." + m.getString("role"));
        String status = downed ? "screen.grandline.crew.downed" : isActive ? (m.getString("order").equals("hold") ? "screen.grandline.crew.holding" : "screen.grandline.crew.following")
                : "screen.grandline.crew.waiting";
        Component line = Component.translatable("screen.grandline.crew.line", role, m.getInt("rank"), Component.translatable(status),
                Component.translatable("screen.grandline.crew.where." + m.getString("where")));
        g.drawString(font, line, x + 2, y + 12, DIM, false);
        // health bar
        float hp = m.getFloat("hp"), maxHp = Math.max(1f, m.getFloat("maxHp"));
        int bw = 52, bx = x + 150, by = y + 3;
        g.fill(bx, by, bx + bw, by + 5, 0xFF2A2420);
        int fillW = Math.round(bw * Math.max(0, Math.min(1, hp / maxHp)));
        g.fill(bx, by, bx + fillW, by + 5, downed ? BAD : hp / maxHp < 0.35f ? 0xFFE0A040 : GOOD);
        g.drawString(font, Component.literal(Math.round(hp) + "/" + Math.round(maxHp)), bx + bw + 4, by - 1, DIM, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int old = scroll;
        scroll = Math.max(0, Math.min(Math.max(0, reserve.size() - reserveRows), scroll - (int) Math.signum(sy)));
        if (scroll != old) rebuildWidgets();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (com.grandline.client.Keys.CREW_ROSTER.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
