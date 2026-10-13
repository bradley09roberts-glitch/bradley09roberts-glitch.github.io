package com.grandline.client.screen;

import com.grandline.client.Keys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One compact page with every control and the key it is on right now (rebinding is in the normal Controls menu,
 * one button away), the combat loop in five lines and how to read a foe's wind-up. Opened with /controls or from
 * the Character screen.
 */
public class ControlsScreen extends Screen {
    private static final int GOLD = 0xFFE8C060, TEXT = 0xFFE6E0D2, DIM = 0xFFA09A8C, KEY = 0xFFFFD23C, PANEL = 0xE0181410, EDGE = 0xFF6E5A3A;
    @Nullable private final Screen parent;
    private int px, py, pw, ph;

    private record Row(Component action, Component key) {}

    public ControlsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.grandline.controls"));
        this.parent = parent;
    }

    private static Component key(KeyMapping k) {
        return k.isUnbound() ? Component.translatable("screen.grandline.controls.unbound") : k.getTranslatedKeyMessage();
    }

    private List<Row> rows() {
        var o = minecraft.options;
        List<Row> r = new ArrayList<>();
        r.add(new Row(Component.translatable("screen.grandline.controls.strike"), Component.translatable("screen.grandline.controls.tap", key(o.keyAttack))));
        r.add(new Row(Component.translatable("screen.grandline.controls.heavy"), Component.translatable("screen.grandline.controls.hold", key(o.keyAttack))));
        r.add(new Row(Component.translatable("screen.grandline.controls.combat_mode"), key(Keys.COMBAT_MODE)));
        r.add(new Row(Component.translatable("screen.grandline.controls.guard"), Component.translatable("screen.grandline.controls.hold", key(o.keyUse))));
        r.add(new Row(Component.translatable("screen.grandline.controls.dodge"), key(Keys.DODGE)));
        r.add(new Row(Component.translatable("screen.grandline.controls.techniques"), Component.literal("1 - 9")));
        r.add(new Row(Component.translatable("screen.grandline.controls.next_set"), Component.translatable("screen.grandline.controls.or_scroll", key(Keys.NEXT_SET))));
        r.add(new Row(Component.translatable("screen.grandline.controls.lock_on"), key(Keys.LOCK_ON)));
        r.add(new Row(Component.translatable("screen.grandline.controls.haki"), Component.literal("").append(key(Keys.OBSERVATION)).append(" / ").append(key(Keys.ARMAMENT)).append(" / ").append(key(Keys.CONQUEROR))));
        r.add(new Row(Component.translatable("screen.grandline.controls.transform"), key(Keys.TRANSFORM)));
        r.add(new Row(Component.translatable("screen.grandline.controls.quest_log"), key(Keys.QUEST_LOG)));
        r.add(new Row(Component.translatable("screen.grandline.controls.chart"), key(Keys.CHART)));
        r.add(new Row(Component.translatable("screen.grandline.controls.minimap"), key(Keys.MINIMAP)));
        r.add(new Row(Component.translatable("screen.grandline.controls.character"), key(Keys.CHARACTER)));
        r.add(new Row(Component.translatable("screen.grandline.controls.ship"), Component.literal("").append(key(Keys.ANCHOR)).append(" / ").append(key(Keys.BURST))));
        for (KeyMapping k : Keys.ALL) {
            // keys added later (crew orders and the like) appear here by themselves
            if (k == Keys.COMBAT_MODE || k == Keys.DODGE || k == Keys.LOCK_ON || k == Keys.NEXT_SET || k == Keys.OBSERVATION || k == Keys.ARMAMENT
                    || k == Keys.CONQUEROR || k == Keys.TRANSFORM || k == Keys.QUEST_LOG || k == Keys.CHART || k == Keys.MINIMAP || k == Keys.CHARACTER
                    || k == Keys.ANCHOR || k == Keys.BURST) continue;
            r.add(new Row(Component.translatable(k.getName()), key(k)));
        }
        return r;
    }

    @Override
    protected void init() {
        pw = Math.min(440, width - 16);
        ph = Math.min(300, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.controls.rebind"), b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(px + pw - 214, py + ph - 26, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(px + pw - 88, py + ph - 26, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, EDGE);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), px + 8, py + 7, GOLD, false);
        // left: every control and its key
        int colW = Math.min(230, pw / 2 + 10), y = py + 22;
        for (Row r : rows()) {
            if (y > py + ph - 36) break;
            g.drawString(font, r.action, px + 10, y, TEXT, false);
            g.drawString(font, r.key, px + colW - 4 - font.width(r.key), y, KEY, false);
            y += 11;
        }
        // right: the loop and the tells
        int x = px + colW + 10, w = pw - colW - 18;
        y = py + 22;
        g.drawString(font, Component.translatable("screen.grandline.controls.loop").withStyle(ChatFormatting.BOLD), x, y, GOLD, false);
        y += 12;
        for (int i = 1; i <= 5; i++) {
            for (FormattedCharSequence l : font.split(Component.translatable("screen.grandline.controls.loop." + i), w)) {
                g.drawString(font, l, x, y, TEXT, false);
                y += 10;
            }
            y += 2;
        }
        y += 4;
        g.drawString(font, Component.translatable("screen.grandline.controls.tells").withStyle(ChatFormatting.BOLD), x, y, GOLD, false);
        y += 12;
        for (String t : new String[]{"heavy", "technique", "boss", "danger"}) {
            for (FormattedCharSequence l : font.split(Component.translatable("screen.grandline.controls.tell." + t), w)) {
                if (y > py + ph - 36) break;
                g.drawString(font, l, x, y, DIM, false);
                y += 10;
            }
            y += 2;
        }
        for (var widget : renderables) widget.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
