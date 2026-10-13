package com.grandline.client.screen;

import com.grandline.net.GLNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * NPC conversation: greeting reacts to who you are; options lead to quests, shops, training and services.
 *
 * <p>A conversation can open with a story scene (data {@code scene}: line keys whose text is {@code "<speaker>|<line>"},
 * the player's name as {@code you}, and optionally a {@code decision} with {@code choices}). The scene is paged one line
 * at a time, with the speaker's name above it: click, Space or Enter for the next line. A decision ends the scene with
 * one button per choice; picking one tells the server, which plays what follows. After the scene the usual options
 * appear (or the screen closes when the scene came on its own).
 */
public class DialogueScreen extends Screen {
    private static final int GOLD = 0xFFE8C060, TEXT = 0xFFEFEFEF, DIM = 0xFFA09A8C, PANEL = 0xD8101018, EDGE = 0xFF6E5A3A;
    private final CompoundTag data;
    private final ListTag sceneLines;
    private final CompoundTag scene;
    /** The scene line being shown. */
    private int line;
    /** The scene is over (paged through, or its decision made): the usual conversation follows. */
    private boolean sceneDone;

    public DialogueScreen(CompoundTag data) {
        super(Component.literal(data.getString("name")));
        this.data = data;
        this.scene = data.getCompound("scene");
        this.sceneLines = scene.getList("lines", Tag.TAG_STRING);
        this.sceneDone = sceneLines.isEmpty();
    }

    private boolean inScene() {
        return !sceneDone;
    }

    /** On the last line of a scene that ends in a decision: the choices are showing. */
    private boolean atDecision() {
        return !sceneDone && scene.contains("decision") && line >= sceneLines.size() - 1;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (inScene()) {
            if (atDecision()) {
                ListTag choices = scene.getList("choices", Tag.TAG_COMPOUND);
                int y = height / 2 + 34;
                for (int i = 0; i < choices.size(); i++) {
                    CompoundTag c = choices.getCompound(i);
                    String id = c.getString("id");
                    Component label = Component.literal((i + 1) + ". ").append(Component.translatable(c.getString("k")));
                    int bh = font.wordWrapHeight(label, 280) > 9 ? 30 : 20;
                    addRenderableWidget(new WrappedButton(width / 2 - 150, y, 300, bh, label, b -> chooseDecision(id)));
                    y += bh + 3;
                }
            }
            return;
        }
        ListTag opts = data.getList("options", Tag.TAG_STRING);
        int y = height / 2 + 10;
        for (int i = 0; i < opts.size(); i++) {
            String opt = opts.getString(i);
            Component label;
            Component generated = data.getCompound("labels").contains(opt) ? parse(data.getCompound("labels").getString(opt)) : null;
            if (generated != null) label = generated; // generated quests: title with names and places, sent by the server
            else if (opt.startsWith("quest:")) label = Component.translatable("dialogue.grandline.option.quest", Component.translatable("quest.grandline." + opt.substring(6)));
            else if (opt.startsWith("ferry|") || opt.startsWith("seatrain|")) {
                String[] p = opt.split("\\|");
                label = Component.translatable("dialogue.grandline.option." + p[0], p.length > 3 ? p[3] : p[1], p.length > 2 ? p[2] : "?");
            } else label = Component.translatable("dialogue.grandline.option." + opt);
            addRenderableWidget(Button.builder(label, b -> choose(opt)).bounds(width / 2 - 110, y, 220, 18).build());
            y += 21;
        }
    }

    private void choose(String opt) {
        if (!opt.equals("leave")) {
            CompoundTag t = new CompoundTag();
            t.putInt("npc", data.getInt("npc"));
            t.putString("option", opt);
            PacketDistributor.sendToServer(new GLNetwork.UiAction("dialogue", t));
        }
        if (!opt.equals("trade")) onClose();
    }

    /** A decision: the server records it and plays what follows (as a new scene). */
    private void chooseDecision(String choiceId) {
        CompoundTag t = new CompoundTag();
        t.putInt("npc", data.getInt("npc"));
        t.putString("option", "scene:" + scene.getString("decision") + ":" + choiceId);
        PacketDistributor.sendToServer(new GLNetwork.UiAction("dialogue", t));
        endScene();
    }

    private void advance() {
        if (atDecision()) return; // only a choice moves on from here
        if (line + 1 < sceneLines.size()) {
            line++;
            init();
            return;
        }
        endScene();
    }

    private void endScene() {
        sceneDone = true;
        if (data.getList("options", Tag.TAG_STRING).isEmpty()) onClose();
        else init();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (inScene() && button == 0) {
            advance();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (inScene() && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            advance();
            return true;
        }
        if (atDecision() && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            ListTag choices = scene.getList("choices", Tag.TAG_COMPOUND);
            int i = key - GLFW.GLFW_KEY_1;
            if (i < choices.size()) {
                chooseDecision(choices.getCompound(i).getString("id"));
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (inScene()) {
            renderScene(g, mx, my, pt);
            return;
        }
        super.render(g, mx, my, pt);
        int x = width / 2 - 160, y = height / 2 - 90;
        g.fill(x - 6, y - 6, x + 326, y + 86, 0xCC101018);
        g.drawString(font, title.copy().withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFFFF);
        if (!data.getString("role").isEmpty())
            g.drawString(font, Component.translatable("role.grandline." + data.getString("role")).withStyle(ChatFormatting.GRAY), x, y + 11, 0xFFAAAAAA);
        int ly = y + 28;
        Component greeting = data.contains("greetingText") ? parse(data.getString("greetingText")) : null;
        if (greeting == null) greeting = Component.translatable(data.getString("greeting"));
        for (FormattedText l : font.getSplitter().splitLines(greeting, 310, Style.EMPTY)) {
            g.drawString(font, l.getString(), x, ly, 0xFFEFEFEF);
            ly += 11;
        }
    }

    private void renderScene(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        int idx = Math.min(line, sceneLines.size() - 1);
        String[] sp = idx < 0 ? new String[]{"narrator", ""} : speakerAndText(sceneLines.getString(idx));
        String speaker = speakerName(sp[0]);
        boolean narrator = speaker.isEmpty();
        int w = 320, x = width / 2 - w / 2;
        List<FormattedCharSequence> text = font.split(narrator ? Component.literal(sp[1]).withStyle(ChatFormatting.ITALIC) : Component.literal(sp[1]), w - 16);
        int h = 30 + text.size() * 11, y = height / 2 - 20 - h;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, EDGE);
        g.fill(x, y, x + w, y + h, PANEL);
        if (!narrator) g.drawString(font, Component.literal(speaker).withStyle(ChatFormatting.BOLD), x + 8, y + 7, GOLD, false);
        int ly = y + (narrator ? 9 : 21);
        for (FormattedCharSequence l : text) {
            g.drawString(font, l, x + 8, ly, narrator ? DIM : TEXT, false);
            ly += 11;
        }
        String counter = (idx + 1) + "/" + sceneLines.size();
        g.drawString(font, counter, x + w - 8 - font.width(counter), y + 7, DIM, false);
        Component hint = Component.translatable(atDecision() ? "screen.grandline.scene.decide" : "screen.grandline.scene.next");
        g.drawCenteredString(font, hint, width / 2, y + h + 6, DIM);
        for (var widget : renderables) widget.render(g, mx, my, pt);
    }

    /** "speaker|text" of a translated scene line (the player's name is argument 1). */
    private String[] speakerAndText(String key) {
        String s = Component.translatable(key, scene.getString("you")).getString();
        int bar = s.indexOf('|');
        return bar < 0 ? new String[]{"narrator", s} : new String[]{s.substring(0, bar).trim(), s.substring(bar + 1).trim()};
    }

    /** Display name of a speaker token: you, narrator (none), a notable, a named minor character or a role. */
    private String speakerName(String tok) {
        if (tok.equals("narrator") || tok.isEmpty()) return "";
        if (tok.equals("you")) return scene.getString("you");
        for (String k : new String[]{"notable.grandline." + tok, "speaker.grandline." + tok, "role.grandline." + tok})
            if (I18n.exists(k)) return I18n.get(k);
        return tok.substring(0, 1).toUpperCase(Locale.ROOT) + tok.substring(1).replace('_', ' ');
    }

    /** A component serialized by the server (generated names and places), or null if it cannot be read. */
    @org.jetbrains.annotations.Nullable
    private static Component parse(String json) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || json.isEmpty()) return null;
        try {
            return Component.Serializer.fromJson(json, mc.level.registryAccess());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A button whose label may wrap onto two lines (decision options are sentences). */
    private class WrappedButton extends Button {
        WrappedButton(int x, int y, int w, int h, Component label, OnPress press) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int bg = isHoveredOrFocused() ? 0xE0503C20 : 0xE0201810;
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? GOLD : EDGE);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);
            List<FormattedCharSequence> ls = font.split(getMessage(), width - 12);
            int ty = getY() + (height - ls.size() * 10) / 2 + 1;
            for (FormattedCharSequence l : ls) {
                g.drawString(font, l, getX() + 6, ty, isHoveredOrFocused() ? 0xFFFFF0C0 : TEXT, false);
                ty += 10;
            }
        }
    }
}
