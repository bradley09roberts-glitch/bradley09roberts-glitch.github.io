package com.terracraft.client.gui;

import com.terracraft.client.ClientState;
import com.terracraft.crafting.CraftingLogic;
import com.terracraft.crafting.CraftingStations;
import com.terracraft.crafting.TerraRecipe;
import com.terracraft.crafting.TerraRecipeManager;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.CraftRecipePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Terraria's crafting menu: a list of recipes available with the stations within reach, the selected
 * recipe's materials and stations, and craft buttons. Recipes are filtered client-side for display only;
 * the server re-validates every craft.
 */
public class TerrariaCraftingScreen extends Screen {
    private enum Filter { CRAFTABLE, NEARBY, ALL }

    private static final int ROW = 20;
    private static final int PANEL = 0xD0182040;
    private static final int PANEL_BORDER = 0xFF4A64B4;
    private Filter filter = Filter.CRAFTABLE;
    private final List<TerraRecipe> visible = new ArrayList<>();
    private Set<Identifier> stations = Set.of();
    private TerraRecipe selected;
    private int scroll;
    private int refreshTimer;
    private int listX;
    private int listY;
    private int listWidth;
    private int listRows;
    private Button filterButton;

    public TerrariaCraftingScreen() {
        super(Component.translatable("screen.terracraft.crafting"));
    }

    @Override
    protected void init() {
        listWidth = Math.min(200, width / 2 - 20);
        listX = width / 2 - listWidth - 10;
        listY = 40;
        listRows = Math.max(3, (height - listY - 40) / ROW);
        int detailX = width / 2 + 10;
        filterButton = addRenderableWidget(Button.builder(filterLabel(), b -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            b.setMessage(filterLabel());
            scroll = 0;
            refresh();
        }).bounds(listX, listY - 24, listWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.terracraft.crafting.craft"), b -> craft(1))
            .bounds(detailX, height - 56, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.terracraft.crafting.craft_10"), b -> craft(10))
            .bounds(detailX + 94, height - 56, 90, 20).build());
        refresh();
    }

    private Component filterLabel() {
        return Component.translatable("screen.terracraft.crafting.filter." + filter.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void refresh() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        var all = TerraRecipeManager.client();
        stations = CraftingStations.nearby(player.level(), player.blockPosition(), TerraRecipeManager.stationsUsed(all));
        visible.clear();
        for (TerraRecipe recipe : all) {
            if (!CraftingLogic.isUnlocked(recipe, ClientState.progression())) {
                continue;
            }
            boolean hasStations = CraftingLogic.hasStations(recipe, stations);
            boolean craftable = hasStations && CraftingLogic.maxCrafts(player, recipe) > 0;
            if (filter == Filter.ALL || (filter == Filter.NEARBY && hasStations) || craftable) {
                visible.add(recipe);
            }
        }
        if (selected != null && !visible.contains(selected)) {
            selected = null;
        }
        if (selected == null && !visible.isEmpty()) {
            selected = visible.get(0);
        }
        scroll = Math.max(0, Math.min(scroll, visible.size() - listRows));
    }

    private void craft(int times) {
        if (selected != null) {
            TerraNetwork.sendToServer(new CraftRecipePacket(selected.id(), times));
            refreshTimer = 2;
        }
    }

    @Override
    public void tick() {
        if (--refreshTimer <= 0) {
            refreshTimer = 10;
            refresh();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (mx >= listX && mx < listX + listWidth && my >= listY && my < listY + listRows * ROW) {
            int index = scroll + (int) ((my - listY) / ROW);
            if (index >= 0 && index < visible.size()) {
                selected = visible.get(index);
                if (doubleClick) {
                    craft(event.hasShiftDown() ? 10 : 1);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), Math.max(0, visible.size() - listRows)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFD700);
        panel(graphics, listX - 4, listY - 4, listWidth + 8, listRows * ROW + 8);
        for (int row = 0; row < listRows && scroll + row < visible.size(); row++) {
            TerraRecipe recipe = visible.get(scroll + row);
            int y = listY + row * ROW;
            boolean craftable = CraftingLogic.hasStations(recipe, stations) && CraftingLogic.maxCrafts(player, recipe) > 0;
            if (recipe == selected) {
                graphics.fill(listX, y, listX + listWidth, y + ROW, 0x6080A0FF);
            } else if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= y && mouseY < y + ROW) {
                graphics.fill(listX, y, listX + listWidth, y + ROW, 0x30FFFFFF);
            }
            graphics.item(recipe.resultStack(), listX + 2, y + 2);
            Component name = recipe.resultStack().getHoverName();
            graphics.text(font, name, listX + 22, y + 6, craftable ? 0xFFFFFFFF : 0xFF808080);
            if (recipe.count() > 1) {
                graphics.text(font, "x" + recipe.count(), listX + listWidth - 22, y + 6, 0xFFC0C0C0);
            }
        }
        if (visible.isEmpty()) {
            graphics.textWithWordWrap(font, Component.translatable("screen.terracraft.crafting.empty"), listX + 4, listY + 4, listWidth - 8, 0xFFA0A0A0);
        }
        drawDetails(graphics, player);
    }

    private void drawDetails(GuiGraphicsExtractor graphics, LocalPlayer player) {
        int x = width / 2 + 10;
        int y = listY;
        int w = Math.min(220, width - x - 10);
        panel(graphics, x - 4, y - 4, w + 8, height - y - 64);
        if (selected == null) {
            return;
        }
        graphics.item(selected.resultStack(), x, y);
        graphics.text(font, selected.resultStack().getHoverName(), x + 20, y + 4, 0xFFFFFFFF);
        y += 24;
        graphics.text(font, Component.translatable("screen.terracraft.crafting.materials").withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFFFF);
        y += 12;
        for (TerraRecipe.Ingredient ingredient : selected.ingredients()) {
            int have = CraftingLogic.count(player, ingredient);
            graphics.item(ingredient.icon(), x, y);
            int color = have >= ingredient.count() ? 0xFF80FF80 : 0xFFFF8080;
            graphics.text(font, ingredient.displayName().copy().append(" " + have + "/" + ingredient.count()), x + 20, y + 4, color);
            y += 18;
        }
        y += 4;
        graphics.text(font, Component.translatable("screen.terracraft.crafting.stations").withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFFFF);
        y += 12;
        if (selected.stations().isEmpty()) {
            graphics.text(font, Component.translatable("screen.terracraft.crafting.by_hand"), x, y, 0xFF80FF80);
        }
        for (Identifier station : selected.stations()) {
            boolean near = stations.contains(station);
            graphics.text(font, CraftingStations.displayName(station), x, y, near ? 0xFF80FF80 : 0xFFFF8080);
            y += 11;
        }
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.outline(x, y, w, h, PANEL_BORDER);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
