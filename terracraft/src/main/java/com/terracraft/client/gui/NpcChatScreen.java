package com.terracraft.client.gui;

import com.terracraft.economy.Coins;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.NpcActionPacket;
import com.terracraft.network.packet.OpenNpcChatPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Terraria-style NPC chat window: the NPC's line of dialogue with service buttons (Shop, Heal, Help, Close);
 * the shop view shows the NPC's wares with prices, buys on click and can sell the held item.
 * Everything is validated on the server; refreshed dialogue updates this screen in place.
 */
public class NpcChatScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int CELL = 22;
    private static final int COLUMNS = 10;

    private OpenNpcChatPacket data;
    private boolean shop;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private final List<ItemStack> offerStacks = new ArrayList<>();
    private boolean closeSent;

    public NpcChatScreen(OpenNpcChatPacket packet) {
        super(Component.translatable("npc.terracraft." + packet.npcId()));
        this.data = packet;
        loadOffers();
    }

    public int entityId() {
        return data.entityId();
    }

    /** New dialogue for the same NPC (Help, Heal results...). */
    public void update(OpenNpcChatPacket packet) {
        this.data = packet;
        this.shop = false;
        loadOffers();
        rebuildWidgets();
    }

    private void loadOffers() {
        offerStacks.clear();
        for (OpenNpcChatPacket.Offer offer : data.offers()) {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(offer.item()));
            offerStacks.add(new ItemStack(item, offer.count()));
        }
    }

    private Component npcName() {
        Entity entity = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(data.entityId()) : null;
        return entity != null ? entity.getDisplayName() : title;
    }

    private List<FormattedCharSequence> dialogue() {
        return font.split(Component.translatable(data.dialogueKey(), data.dialogueArg()), PANEL_WIDTH - 16);
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = 24;
        int rows = shop ? Math.max(1, (offerStacks.size() + COLUMNS - 1) / COLUMNS) : 0;
        int body = shop ? rows * CELL + 26 : dialogue().size() * 10 + 6;
        panelHeight = 22 + body + 24;
        int buttonY = panelY + panelHeight - 22;
        List<Button> buttons = new ArrayList<>();
        if (shop) {
            buttons.add(Button.builder(Component.translatable("screen.terracraft.npc.sell"), b -> send("sell", 0)).width(90).build());
            buttons.add(Button.builder(Component.translatable("screen.terracraft.npc.back"), b -> { shop = false; rebuildWidgets(); }).width(60).build());
        } else {
            for (String service : data.services()) {
                if (service.equals("shop")) {
                    buttons.add(Button.builder(Component.translatable("screen.terracraft.npc.shop"), b -> { shop = true; rebuildWidgets(); }).width(60).build());
                } else {
                    buttons.add(Button.builder(Component.translatable("screen.terracraft.npc." + service), b -> send(service, 0)).width(60).build());
                }
            }
        }
        buttons.add(Button.builder(Component.translatable("screen.terracraft.npc.close"), b -> onClose()).width(60).build());
        int total = buttons.stream().mapToInt(Button::getWidth).sum() + 4 * (buttons.size() - 1);
        int x = panelX + (PANEL_WIDTH - total) / 2;
        for (Button button : buttons) {
            button.setPosition(x, buttonY);
            button.setHeight(18);
            addRenderableWidget(button);
            x += button.getWidth() + 4;
        }
    }

    private void send(String action, int index) {
        TerraNetwork.sendToServer(new NpcActionPacket(data.entityId(), action, index));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + panelHeight + 2, 0xFF1A2550);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xE03A4A8C);
        graphics.text(font, npcName(), panelX + 8, panelY + 7, 0xFFFFE070);
        int y = panelY + 22;
        if (shop) {
            ItemStack hovered = ItemStack.EMPTY;
            long hoveredPrice = 0;
            for (int i = 0; i < offerStacks.size(); i++) {
                int cx = panelX + 8 + (i % COLUMNS) * CELL + (PANEL_WIDTH - 16 - COLUMNS * CELL) / 2;
                int cy = y + (i / COLUMNS) * CELL;
                boolean over = mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2;
                graphics.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, over ? 0xA0A0C0FF : 0x60202850);
                graphics.item(offerStacks.get(i), cx + 2, cy + 2);
                if (offerStacks.get(i).getCount() > 1) {
                    graphics.text(font, String.valueOf(offerStacks.get(i).getCount()), cx + 12, cy + 12, 0xFFFFFFFF);
                }
                if (over) {
                    hovered = offerStacks.get(i);
                    hoveredPrice = data.offers().get(i).price();
                }
            }
            int infoY = y + Math.max(1, (offerStacks.size() + COLUMNS - 1) / COLUMNS) * CELL + 4;
            long coins = minecraft != null && minecraft.player != null ? Coins.total(minecraft.player) : 0;
            graphics.text(font, Component.translatable("screen.terracraft.npc.savings").append(Coins.format(coins)), panelX + 8, infoY, 0xFFFFFFFF);
            if (!hovered.isEmpty()) {
                List<Component> tip = new ArrayList<>();
                tip.add(hovered.getHoverName());
                tip.add(Component.translatable("screen.terracraft.npc.price").withStyle(ChatFormatting.GRAY).append(Coins.format(hoveredPrice)));
                graphics.setTooltipForNextFrame(font, tip, java.util.Optional.empty(), mouseX, mouseY);
            }
        } else {
            for (FormattedCharSequence line : dialogue()) {
                graphics.text(font, line, panelX + 8, y, 0xFFFFFFFF);
                y += 10;
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (shop) {
            int y = panelY + 22;
            for (int i = 0; i < offerStacks.size(); i++) {
                int cx = panelX + 8 + (i % COLUMNS) * CELL + (PANEL_WIDTH - 16 - COLUMNS * CELL) / 2;
                int cy = y + (i / COLUMNS) * CELL;
                if (event.x() >= cx && event.x() < cx + CELL - 2 && event.y() >= cy && event.y() < cy + CELL - 2) {
                    int times = event.hasShiftDown() ? 5 : 1;
                    for (int t = 0; t < times; t++) {
                        send("buy", i);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        if (!closeSent) {
            closeSent = true;
            send("close", 0);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
