package com.grandline.client.screen;

import com.grandline.client.ClientState;
import com.grandline.net.GLNetwork;
import com.grandline.player.Appearance;
import com.grandline.player.Blue;
import com.grandline.player.Faction;
import com.grandline.player.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

/**
 * Character creation: name, race (with traits), height & build, colours, scars/tattoos/outfit, home Blue and path.
 * The server validates and clamps everything on submit.
 */
public class CharacterCreationScreen extends Screen {
    private static final int[] PALETTE = {0x3A7BD5, 0x2E9E6A, 0xC9483B, 0xE8B04A, 0x8A5AC9, 0xF4F4F4, 0x2B1B10, 0x8A8A9A, 0xE07A9A, 0x1A1A1A, 0x6ACBE8, 0xD9B56A};
    private final List<Race> races = Race.playableRaces();
    private final List<Faction> paths = Arrays.stream(Faction.values()).filter(f -> f.startingPath).toList();
    private int raceIdx = 0, blueIdx = 0, pathIdx = 0;
    private final Appearance app = new Appearance();
    private EditBox nameBox;
    private final int generation;

    public CharacterCreationScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.create"));
        this.generation = data.getInt("generation");
        app.scale = Race.HUMAN.baseScale;
    }

    private Race race() {
        return races.get(raceIdx);
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int left = cx - 200, right = cx + 40;
        int y = 40;
        nameBox = new EditBox(font, left, y, 150, 18, Component.translatable("screen.grandline.name"));
        nameBox.setMaxLength(24);
        if (minecraft != null && minecraft.player != null) nameBox.setValue(minecraft.player.getGameProfile().getName());
        addRenderableWidget(nameBox);
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleRace(-1)).bounds(left, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleRace(1)).bounds(left + 130, y, 20, 20).build());
        y += 26;
        addRenderableWidget(new AbstractSliderButton(left, y, 150, 20, Component.empty(), 0.5) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.translatable("screen.grandline.height", String.format("%.2f", app.scale)));
            }
            @Override protected void applyValue() {
                app.scale = race().minScale + (float) value * (race().maxScale - race().minScale);
                updatePreview();
            }
        });
        y += 24;
        addRenderableWidget(new AbstractSliderButton(left, y, 150, 20, Component.empty(), 0.5) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.translatable("screen.grandline.build", Math.round(app.build * 100)));
            }
            @Override protected void applyValue() {
                app.build = (float) value;
            }
        });
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.feature_color"), b -> { app.featureColor = next(app.featureColor); updatePreview(); }).bounds(left, y, 73, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.second_color"), b -> { app.secondaryColor = next(app.secondaryColor); updatePreview(); }).bounds(left + 77, y, 73, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.hair_color"), b -> { app.hairColor = next(app.hairColor); updatePreview(); }).bounds(left, y, 73, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.eye_color"), b -> { app.eyeColor = next(app.eyeColor); updatePreview(); }).bounds(left + 77, y, 73, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.scar"), b -> app.scar = (app.scar + 1) % (Appearance.SCARS + 1)).bounds(left, y, 48, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.tattoo"), b -> app.tattoo = (app.tattoo + 1) % (Appearance.TATTOOS + 1)).bounds(left + 51, y, 48, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.outfit"), b -> app.outfit = (app.outfit + 1) % (Appearance.OUTFITS + 1)).bounds(left + 102, y, 48, 20).build());

        int ry = 40;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.origin"), b -> blueIdx = (blueIdx + 1) % Blue.values().length).bounds(right + 40, ry + 140, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.path"), b -> pathIdx = (pathIdx + 1) % paths.size()).bounds(right + 40, ry + 164, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.set_sail").withStyle(net.minecraft.ChatFormatting.GOLD), b -> submit()).bounds(cx - 60, height - 34, 120, 20).build());
        updatePreview();
    }

    private int next(int c) {
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == c) return PALETTE[(i + 1) % PALETTE.length];
        return PALETTE[0];
    }

    private void cycleRace(int d) {
        raceIdx = Math.floorMod(raceIdx + d, races.size());
        app.scale = race().baseScale;
        updatePreview();
    }

    /** Preview the race parts on the local player model (client-only visuals key). */
    private void updatePreview() {
        if (minecraft == null || minecraft.player == null) return;
        CompoundTag v = ClientState.visuals(minecraft.player.getId()).copy();
        v.putString("previewRace", race().id());
        v.put("appearance", app.save());
        ClientState.VISUALS.put(minecraft.player.getId(), v);
    }

    private void submit() {
        CompoundTag t = new CompoundTag();
        t.putString("name", nameBox.getValue());
        t.putString("race", race().id());
        t.putString("faction", paths.get(pathIdx).id());
        t.putString("origin", Blue.values()[blueIdx].id());
        t.put("appearance", app.save());
        PacketDistributor.sendToServer(new GLNetwork.CreateCharacter(t));
        if (minecraft != null && minecraft.player != null) {
            CompoundTag v = ClientState.visuals(minecraft.player.getId()).copy();
            v.remove("previewRace");
            ClientState.VISUALS.put(minecraft.player.getId(), v);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2;
        g.drawCenteredString(font, title, cx, 12, 0xFFFFD34A);
        if (generation > 0) g.drawCenteredString(font, Component.translatable("screen.grandline.new_life", generation), cx, 24, 0xFFFF7070);
        int left = cx - 200;
        g.drawCenteredString(font, Component.translatable(race().translationKey()), left + 75, 72, 0xFFFFFFFF);
        // race traits
        int ty = 240;
        for (String line : font.getSplitter().splitLines(Component.translatable(race().translationKey() + ".desc").getString(), 180, net.minecraft.network.chat.Style.EMPTY).stream().map(net.minecraft.network.chat.FormattedText::getString).toList()) {
            g.drawString(font, line, left, ty, 0xFFCCCCCC);
            ty += 10;
        }
        int right = cx + 40;
        g.drawString(font, Component.translatable("screen.grandline.origin_value", Component.translatable(Blue.values()[blueIdx].translationKey())), right + 40, 225, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("screen.grandline.path_value", Component.translatable(paths.get(pathIdx).translationKey())), right + 40, 237, 0xFFFFFFFF);
        g.fill(left + 155, 162, left + 165, 172, 0xFF000000 | app.featureColor);
        g.fill(left + 155, 186, left + 165, 196, 0xFF000000 | app.hairColor);
        g.drawString(font, "S" + app.scar + " T" + app.tattoo + " O" + app.outfit, left + 155, 214, 0xFFAAAAAA);
        if (minecraft != null && minecraft.player != null) {
            int size = (int) (60 / Math.max(0.6f, app.scale));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, right + 40, 40, right + 160, 175, size, 0.0625f, mx, my, minecraft.player);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
