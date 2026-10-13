package com.grandline.client.screen;

import com.grandline.client.Guidance;
import com.grandline.net.GLNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The journal (U). On the left, active quests grouped as the Story, Side stories and Jobs (optional generated work).
 * On the right, the selected quest: its chapter, the next step, where (a precise marker, or the area to search), how
 * dangerous its foes are for you, who is who, why it matters, the objectives with progress, rewards, and the story so
 * far. A Track button puts its route on the minimap and the sea chart (one quest is tracked at a time).
 */
public class QuestLogScreen extends Screen {
    private static final int GOLD = 0xFFE8C060, TEXT = 0xFFE6E0D2, DIM = 0xFFA09A8C, DONE = 0xFF7CC860, PANEL = 0xE0181410, EDGE = 0xFF6E5A3A,
            WHERE = 0xFFFFD23C, INFO = 0xFFB8D8FF, HEAD = 0xFFC8A870;
    private final List<CompoundTag> quests = new ArrayList<>();
    /** List rows: a quest index, or -1 - group for a group heading. */
    private final List<Integer> rows = new ArrayList<>();
    private final String tracked;
    private final int completed;
    private int selected, scroll, detailScroll, detailHeight;
    private int px, py, pw, ph, listW;
    private Button trackButton;

    private record Line(FormattedCharSequence text, int color, int indent) {}

    public QuestLogScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.quests"));
        ListTag l = data.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) quests.add(l.getCompound(i));
        tracked = data.getString("tracked");
        completed = data.getInt("completed");
        String group = null;
        for (int i = 0; i < quests.size(); i++) {
            String g = quests.get(i).getString("group");
            if (g.isEmpty()) g = "side";
            if (!g.equals(group)) {
                rows.add(-1 - groupIndex(g));
                group = g;
            }
            rows.add(i);
            if (quests.get(i).getString("id").equals(tracked)) selected = i;
        }
    }

    private static int groupIndex(String g) {
        return switch (g) {
            case "story" -> 0;
            case "side" -> 1;
            default -> 2;
        };
    }

    private static String groupKey(int idx) {
        return "screen.grandline.quests.group." + (idx == 0 ? "story" : idx == 1 ? "side" : "job");
    }

    @Override
    protected void init() {
        pw = Math.min(480, width - 16);
        ph = Math.min(290, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listW = Math.min(150, pw / 3);
        trackButton = addRenderableWidget(Button.builder(Component.translatable("screen.grandline.quests.track"), b -> track())
                .bounds(px + pw - 110, py + ph - 26, 102, 20).build());
        updateButton();
    }

    private void updateButton() {
        if (quests.isEmpty()) {
            trackButton.visible = false;
            return;
        }
        boolean isTracked = quests.get(selected).getString("id").equals(tracked);
        trackButton.active = !isTracked;
        trackButton.setMessage(Component.translatable(isTracked ? "screen.grandline.quests.tracking" : "screen.grandline.quests.track"));
    }

    private void track() {
        CompoundTag t = new CompoundTag();
        t.putString("id", quests.get(selected).getString("id"));
        PacketDistributor.sendToServer(new GLNetwork.UiAction("quest_track", t));
    }

    private int rowH() {
        return 20;
    }

    private int visibleRows() {
        return (ph - 44) / rowH();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, EDGE);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.drawString(font, Component.translatable("screen.grandline.quests").withStyle(ChatFormatting.BOLD), px + 8, py + 7, GOLD, false);
        g.fill(px + listW, py + 20, px + listW + 1, py + ph - 6, EDGE);
        if (quests.isEmpty()) {
            for (FormattedCharSequence l : font.split(Component.translatable("grandline.quests.none", completed), pw - 24)) {
                g.drawString(font, l, px + 12, py + 34, TEXT, false);
            }
            for (var w : renderables) w.render(g, mx, my, pt);
            return;
        }
        // the list, with group headings
        int rh = rowH(), y = py + 22, vis = visibleRows();
        for (int r = scroll; r < Math.min(rows.size(), scroll + vis); r++) {
            int idx = rows.get(r);
            if (idx < 0) {
                g.drawString(font, Component.translatable(groupKey(-1 - idx)).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE), px + 7, y + 7, HEAD, false);
                y += rh;
                continue;
            }
            CompoundTag q = quests.get(idx);
            boolean sel = idx == selected, hover = mx >= px + 4 && mx < px + listW - 4 && my >= y && my < y + rh - 2;
            if (sel || hover) g.fill(px + 4, y, px + listW - 4, y + rh - 2, sel ? 0x806E5A3A : 0x40FFFFFF);
            if (q.getString("id").equals(tracked)) g.drawString(font, "◆", px + 7, y + 5, 0xFFFFD23C, false);
            List<FormattedCharSequence> t = font.split(Guidance.parse(q, "title"), listW - 22);
            for (int k = 0; k < Math.min(2, t.size()); k++) g.drawString(font, t.get(k), px + 17, y + 1 + k * 9 + (t.size() == 1 ? 4 : 0), sel ? GOLD : TEXT, false);
            y += rh;
        }
        if (rows.size() > vis) g.drawString(font, (scroll + 1) + "-" + Math.min(rows.size(), scroll + vis) + "/" + rows.size(), px + 8, py + ph - 26, DIM, false);
        g.drawString(font, Component.translatable("screen.grandline.quests.completed", completed), px + 8, py + ph - 14, DIM, false);
        // the selected quest, scrollable
        int x = px + listW + 10, w = pw - listW - 18, top = py + 22, bottom = py + ph - 32;
        List<Line> lines = detail(quests.get(selected), w);
        detailHeight = lines.size() * 10;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, detailHeight - (bottom - top))));
        g.enableScissor(x - 2, top, x + w + 2, bottom);
        int ly = top - detailScroll;
        for (Line l : lines) {
            if (ly > top - 10 && ly < bottom) g.drawString(font, l.text(), x + l.indent(), ly, l.color(), false);
            ly += 10;
        }
        g.disableScissor();
        if (detailHeight > bottom - top) {
            int barH = Math.max(12, (bottom - top) * (bottom - top) / detailHeight);
            int barY = top + (bottom - top - barH) * detailScroll / Math.max(1, detailHeight - (bottom - top));
            g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, 0x80E8C060);
            g.drawString(font, Component.translatable("screen.grandline.quests.scroll"), x, py + ph - 22, DIM, false);
        }
        for (var widget : renderables) widget.render(g, mx, my, pt);
    }

    /** The selected quest's page as wrapped lines. */
    private List<Line> detail(CompoundTag q, int w) {
        List<Line> out = new ArrayList<>();
        add(out, Guidance.parse(q, "title").copy().withStyle(ChatFormatting.BOLD), w, GOLD, 0);
        if (q.contains("chapter")) add(out, Guidance.parse(q, "chapter"), w, HEAD, 0);
        else if (q.getString("group").equals("job")) add(out, Component.translatable("screen.grandline.quests.job_label"), w, DIM, 0);
        gap(out);
        if (q.contains("next")) add(out, Component.translatable("screen.grandline.quests.next", Guidance.parse(q, "next")), w, TEXT, 0);
        if (q.contains("where")) {
            Component where = Guidance.parse(q, "where");
            Component line = q.getBoolean("area")
                    ? Component.translatable(q.contains("dist") ? "screen.grandline.quests.area_dist" : "screen.grandline.quests.area", where, q.getInt("dist"))
                    : q.contains("dist") ? Component.translatable("screen.grandline.quests.where_dist", where, q.getInt("dist"))
                    : Component.translatable("screen.grandline.quests.where", where);
            add(out, line, w, WHERE, 0);
        }
        if (q.contains("danger")) add(out, Component.translatable("screen.grandline.quests.danger", Guidance.parse(q, "danger")), w, 0xFFFF9A7A, 0);
        ListTag who = q.getList("who", Tag.TAG_STRING);
        if (!who.isEmpty()) {
            gap(out);
            add(out, Component.translatable("screen.grandline.quests.who"), w, HEAD, 0);
            for (int i = 0; i < who.size(); i++) add(out, parse(who.getString(i)), w - 6, TEXT, 6);
        }
        if (q.contains("why")) {
            gap(out);
            add(out, Component.translatable("screen.grandline.quests.why"), w, HEAD, 0);
            add(out, Guidance.parse(q, "why"), w - 6, TEXT, 6);
        }
        if (q.contains("desc")) {
            gap(out);
            add(out, Guidance.parse(q, "desc"), w, DIM, 0);
        }
        gap(out);
        add(out, Component.translatable("screen.grandline.quests.objectives"), w, HEAD, 0);
        ListTag objs = q.getList("objectives", Tag.TAG_COMPOUND);
        for (int i = 0; i < objs.size(); i++) {
            CompoundTag o = objs.getCompound(i);
            int have = o.getInt("have"), count = o.getInt("count");
            boolean done = have >= count;
            Component line = Component.literal(done ? "✔ " : "• ").append(Guidance.parse(o, "text")).append(count > 1 || done ? " (" + have + "/" + count + ")" : "");
            add(out, line, w - 4, done ? DONE : TEXT, 4);
        }
        gap(out);
        Component rewards = Component.translatable("screen.grandline.quests.rewards", q.getInt("xp"), q.getLong("beli"));
        ListTag items = q.getList("items", Tag.TAG_STRING);
        for (int i = 0; i < items.size(); i++) rewards = rewards.copy().append(", ").append(parse(items.getString(i)));
        add(out, rewards, w, INFO, 0);
        ListTag recap = q.getList("recap", Tag.TAG_STRING);
        if (!recap.isEmpty()) {
            gap(out);
            add(out, Component.translatable("screen.grandline.quests.recap"), w, HEAD, 0);
            for (int i = 0; i < recap.size(); i++) add(out, Component.literal("– ").append(parse(recap.getString(i))), w - 6, DIM, 6);
        }
        return out;
    }

    private void add(List<Line> out, Component c, int w, int color, int indent) {
        if (c == null) return;
        for (FormattedCharSequence l : font.split(c, w)) out.add(new Line(l, color, indent));
    }

    private void gap(List<Line> out) {
        if (!out.isEmpty()) out.add(new Line(FormattedCharSequence.EMPTY, 0, 0));
    }

    private static Component parse(String json) {
        CompoundTag t = new CompoundTag();
        t.putString("v", json);
        Component c = Guidance.parse(t, "v");
        return c == null ? Component.empty() : c;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int rh = rowH(), vis = visibleRows();
        if (mx >= px + 4 && mx < px + listW - 4 && my >= py + 22 && my < py + 22 + vis * rh) {
            int r = scroll + (int) ((my - py - 22) / rh);
            if (r < rows.size() && rows.get(r) >= 0) {
                selected = rows.get(r);
                detailScroll = 0;
                updateButton();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= px + listW) {
            detailScroll = Math.max(0, detailScroll - (int) Math.signum(sy) * 20);
            return true;
        }
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (com.grandline.client.Keys.QUEST_LOG.matches(key, scan)) {
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
