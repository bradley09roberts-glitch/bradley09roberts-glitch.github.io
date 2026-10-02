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
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 16;
    private ProgressionFlag.Category category = ProgressionFlag.Category.BOSS;
    private int page;
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
                page = 0;
                rebuildWidgets();
            }).bounds(x, 22, 60, BUTTON_HEIGHT).build());
            x += 64;
        }

        List<ProgressionFlag> flags = new ArrayList<>();
        for (ProgressionFlag flag : ProgressionFlags.all()) {
            if (flag.category() == category) {
                flags.add(flag);
            }
        }
        int columns = Math.max(1, (width - 20) / (BUTTON_WIDTH + 4));
        int rows = Math.max(1, (height - 44 - 62) / (BUTTON_HEIGHT + 2));
        int perPage = columns * rows;
        int pages = Math.max(1, (flags.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = page * perPage; i < Math.min(flags.size(), (page + 1) * perPage); i++) {
            ProgressionFlag flag = flags.get(i);
            int local = i - page * perPage;
            int bx = 10 + (local % columns) * (BUTTON_WIDTH + 4);
            int by = 42 + (local / columns) * (BUTTON_HEIGHT + 2);
            Button button = Button.builder(label(flag), b -> send("toggle_flag", flag.id().toString()))
                .bounds(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            flagButtons.add(new FlagButton(flag, button));
            addRenderableWidget(button);
        }
        if (pages > 1) {
            int px = width - 60;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = (page + pages - 1) % pages; rebuildWidgets(); })
                .bounds(px, 22, 20, BUTTON_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = (page + 1) % pages; rebuildWidgets(); })
                .bounds(px + 24, 22, 20, BUTTON_HEIGHT).build());
        }

        int bottom = height - 22;
        String[][] actions = {
            {"Heal", "heal"}, {"Max", "max_stats"}, {"Reset", "reset_stats"},
            {"+Life", "add_life_crystal"}, {"+Mana", "add_mana_crystal"},
            {"Day", "time_day"}, {"Night", "time_night"}, {"Reset world", "reset_progression"}
        };
        int ax = 10;
        for (String[] action : actions) {
            int w = font.width(action[0]) + 8;
            addRenderableWidget(Button.builder(Component.literal(action[0]), b -> send(action[1], ""))
                .bounds(ax, bottom, w, BUTTON_HEIGHT).build());
            ax += w + 4;
        }
    }

    private static Component label(ProgressionFlag flag) {
        boolean on = ClientState.progression().has(flag);
        String name = flag.id().getPath().replace("boss_", "").replace("_defeated", "").replace("event_", "").replace("npc_", "");
        return Component.literal((on ? "[x] " : "[ ] ") + name)
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
        String summary = String.format("Crystals %d  Fruit %d  Mana crystals %d  Life %d  Mana %d  Def %d  Evil: %s",
            stats.lifeCrystals(), stats.lifeFruit(), stats.manaCrystals(), stats.maxLife(), stats.maxMana(), stats.defense(),
            ClientState.progression().variants().evil().getSerializedName());
        graphics.text(font, summary, 10, height - 36, 0xFFE0E0E0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record FlagButton(ProgressionFlag flag, Button button) {}
}
