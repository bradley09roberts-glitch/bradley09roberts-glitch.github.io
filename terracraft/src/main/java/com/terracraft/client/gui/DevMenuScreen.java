package com.terracraft.client.gui;

import com.terracraft.client.ClientState;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.DevActionPacket;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Developer menu: toggle any progression flag, jump between day and night, and edit player upgrades
 * without typing commands. All actions are executed (and permission-checked) on the server.
 */
public class DevMenuScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 18;
    private ProgressionFlag.Category category = ProgressionFlag.Category.BOSS;
    private final List<FlagButton> flagButtons = new ArrayList<>();

    public DevMenuScreen() {
        super(Component.translatable("screen.terracraft.dev_menu"));
    }

    @Override
    protected void init() {
        flagButtons.clear();
        int x = 10;
        for (ProgressionFlag.Category cat : new ProgressionFlag.Category[]{
            ProgressionFlag.Category.BOSS, ProgressionFlag.Category.WORLD, ProgressionFlag.Category.EVENT, ProgressionFlag.Category.NPC}) {
            addRenderableWidget(Button.builder(Component.literal(cat.name()), b -> {
                category = cat;
                rebuildWidgets();
            }).bounds(x, 24, 70, BUTTON_HEIGHT).build());
            x += 74;
        }

        int columns = Math.max(1, (width - 20) / (BUTTON_WIDTH + 4));
        int index = 0;
        for (ProgressionFlag flag : ProgressionFlags.all()) {
            if (flag.category() != category) {
                continue;
            }
            int col = index % columns;
            int row = index / columns;
            int bx = 10 + col * (BUTTON_WIDTH + 4);
            int by = 48 + row * (BUTTON_HEIGHT + 2);
            Button button = Button.builder(label(flag), b -> send("toggle_flag", flag.id().toString()))
                .bounds(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            flagButtons.add(new FlagButton(flag, button));
            addRenderableWidget(button);
            index++;
        }

        int bottom = height - 26;
        String[][] actions = {
            {"Heal", "heal"}, {"Max stats", "max_stats"}, {"Reset stats", "reset_stats"},
            {"+Life Crystal", "add_life_crystal"}, {"+Mana Crystal", "add_mana_crystal"},
            {"Day", "time_day"}, {"Night", "time_night"}, {"Reset world", "reset_progression"}
        };
        int ax = 10;
        for (String[] action : actions) {
            int w = font.width(action[0]) + 12;
            addRenderableWidget(Button.builder(Component.literal(action[0]), b -> send(action[1], ""))
                .bounds(ax, bottom, w, BUTTON_HEIGHT).build());
            ax += w + 4;
        }
    }

    private static Component label(ProgressionFlag flag) {
        boolean on = ClientState.progression().has(flag);
        return Component.literal((on ? "[x] " : "[ ] ") + flag.id().getPath())
            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static void send(String action, String argument) {
        TerraNetwork.sendToServer(new DevActionPacket(action, argument));
    }

    @Override
    public void tick() {
        for (FlagButton entry : flagButtons) {
            entry.button.setMessage(label(entry.flag));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFD700);
        SyncPlayerStatsPacket stats = ClientState.stats();
        String summary = String.format("Life crystals %d  Life fruit %d  Mana crystals %d  Max life %d  Max mana %d  Defense %d  Evil %s",
            stats.lifeCrystals(), stats.lifeFruit(), stats.manaCrystals(), stats.maxLife(), stats.maxMana(), stats.defense(),
            ClientState.progression().variants().evil().getSerializedName());
        graphics.text(font, summary, 10, height - 44, 0xFFE0E0E0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record FlagButton(ProgressionFlag flag, Button button) {}
}
