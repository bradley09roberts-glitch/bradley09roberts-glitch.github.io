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
 * Quest log (U): active quests on the left; on the right the selected quest's description, objectives with progress,
 * where to go, rewards and a Track button that puts its route on the minimap and the sea chart.
 */
public class QuestLogScreen extends Screen {
    private static final int GOLD = 0xFFE8C060, TEXT = 0xFFE6E0D2, DIM = 0xFFA09A8C, DONE = 0xFF7CC860, PANEL = 0xE0181410, EDGE = 0xFF6E5A3A;
    private final List<CompoundTag> quests = new ArrayList<>();
    private final String tracked;
    private final int completed;
    private int selected, scroll;
    private int px, py, pw, ph, listW;
    private Button trackButton;

    public QuestLogScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.quests"));
        ListTag l = data.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) quests.add(l.getCompound(i));
        tracked = data.getString("tracked");
        completed = data.getInt("completed");
        for (int i = 0; i < quests.size(); i++) if (quests.get(i).getString("id").equals(tracked)) selected = i;
    }

    @Override
    protected void init() {
        pw = Math.min(460, width - 16);
        ph = Math.min(270, height - 16);
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
        // the list
        int rowH = 22, y = py + 22, rows = (ph - 44) / rowH;
        for (int i = scroll; i < Math.min(quests.size(), scroll + rows); i++) {
            CompoundTag q = quests.get(i);
            boolean sel = i == selected, hover = mx >= px + 4 && mx < px + listW - 4 && my >= y && my < y + rowH - 2;
            if (sel || hover) g.fill(px + 4, y, px + listW - 4, y + rowH - 2, sel ? 0x806E5A3A : 0x40FFFFFF);
            boolean isTracked = q.getString("id").equals(tracked);
            if (isTracked) g.drawString(font, "◆", px + 7, y + 6, 0xFFFFD23C, false);
            List<FormattedCharSequence> t = font.split(Guidance.parse(q, "title"), listW - 22);
            for (int k = 0; k < Math.min(2, t.size()); k++) g.drawString(font, t.get(k), px + 17, y + 2 + k * 9 + (t.size() == 1 ? 4 : 0), sel ? GOLD : TEXT, false);
            y += rowH;
        }
        if (quests.size() > rows) g.drawString(font, (scroll + 1) + "-" + Math.min(quests.size(), scroll + rows) + "/" + quests.size(), px + 8, py + ph - 26, DIM, false);
        g.drawString(font, Component.translatable("screen.grandline.quests.completed", completed), px + 8, py + ph - 14, DIM, false);
        // the selected quest
        CompoundTag q = quests.get(selected);
        int x = px + listW + 10, w = pw - listW - 18;
        y = py + 22;
        for (FormattedCharSequence l : font.split(Guidance.parse(q, "title").copy().withStyle(ChatFormatting.BOLD), w)) {
            g.drawString(font, l, x, y, GOLD, false);
            y += 10;
        }
        if (q.contains("where")) {
            Component where = q.contains("dist") ? Component.translatable("screen.grandline.quests.where_dist", Guidance.parse(q, "where"), q.getInt("dist"))
                    : Component.translatable("screen.grandline.quests.where", Guidance.parse(q, "where"));
            for (FormattedCharSequence l : font.split(where, w)) {
                g.drawString(font, l, x, y, 0xFFFFD23C, false);
                y += 10;
            }
        }
        y += 4;
        if (q.contains("desc")) {
            for (FormattedCharSequence l : font.split(Guidance.parse(q, "desc"), w)) {
                g.drawString(font, l, x, y, DIM, false);
                y += 10;
            }
            y += 4;
        }
        g.drawString(font, Component.translatable("screen.grandline.quests.objectives"), x, y, TEXT, false);
        y += 11;
        ListTag objs = q.getList("objectives", Tag.TAG_COMPOUND);
        for (int i = 0; i < objs.size(); i++) {
            CompoundTag o = objs.getCompound(i);
            int have = o.getInt("have"), count = o.getInt("count");
            boolean done = have >= count;
            Component line = Component.literal(done ? "✔ " : "• ").append(Guidance.parse(o, "text"))
                    .append(count > 1 || done ? " (" + have + "/" + count + ")" : "");
            for (FormattedCharSequence l : font.split(line, w - 4)) {
                g.drawString(font, l, x + 4, y, done ? DONE : TEXT, false);
                y += 10;
            }
        }
        y += 4;
        Component rewards = Component.translatable("screen.grandline.quests.rewards", q.getInt("xp"), q.getLong("beli"));
        ListTag items = q.getList("items", Tag.TAG_STRING);
        for (int i = 0; i < items.size(); i++) rewards = rewards.copy().append(", ").append(Guidance.parse(wrap(items.getString(i)), "v"));
        for (FormattedCharSequence l : font.split(rewards, w)) {
            if (y > py + ph - 34) break;
            g.drawString(font, l, x, y, 0xFFB8D8FF, false);
            y += 10;
        }
        for (var widget : renderables) widget.render(g, mx, my, pt);
    }

    private static CompoundTag wrap(String json) {
        CompoundTag t = new CompoundTag();
        t.putString("v", json);
        return t;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int rowH = 22, rows = (ph - 44) / rowH;
        if (mx >= px + 4 && mx < px + listW - 4 && my >= py + 22 && my < py + 22 + rows * rowH) {
            int i = scroll + (int) ((my - py - 22) / rowH);
            if (i < quests.size()) {
                selected = i;
                updateButton();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int rows = (ph - 44) / 22;
        scroll = Math.max(0, Math.min(Math.max(0, quests.size() - rows), scroll - (int) Math.signum(sy)));
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
