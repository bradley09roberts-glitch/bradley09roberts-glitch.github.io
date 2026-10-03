package com.terracraft.client.hud;

import com.terracraft.TerraCraft;
import com.terracraft.client.ClientState;
import com.terracraft.config.TerraConfig;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Terraria-style status display replacing the vanilla hearts:
 * life hearts (20 life each, turning golden with Life Fruit), "Life: x/y" text, a defense shield,
 * and a vertical column of mana stars at the right edge. There is no food bar (hunger is disabled); the air
 * bubbles take its place.
 */
public final class TerrariaHud {
    private static final Identifier HEART_EMPTY = TerraCraft.id("hud/heart_empty");
    private static final Identifier HEART_FULL = TerraCraft.id("hud/heart_full");
    private static final Identifier HEART_GOLDEN = TerraCraft.id("hud/heart_golden");
    private static final Identifier STAR_EMPTY = TerraCraft.id("hud/star_empty");
    private static final Identifier STAR_FULL = TerraCraft.id("hud/star_full");
    private static final Identifier DEFENSE = TerraCraft.id("hud/defense");
    private static final int ICON = 9;

    private TerrariaHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.PLAYER_HEALTH, TerrariaHud::extract);
        // there is no hunger; the Terraria HUD draws the air bubbles where the food bar was
        event.replaceLayer(VanillaGuiLayers.FOOD_LEVEL, (graphics, delta) -> {});
        event.wrapLayer(VanillaGuiLayers.AIR_LEVEL, layer -> (graphics, delta) -> {
            if (!TerraConfig.CLIENT.terrariaHud.get()) {
                layer.render(graphics, delta);
            }
        });
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        Hud hud = mc.gui.hud;
        if (!TerraConfig.CLIENT.terrariaHud.get()) {
            hud.extractPlayerHealth(graphics);
            return;
        }
        int xLeft = graphics.guiWidth() / 2 - 91;
        int xRight = graphics.guiWidth() / 2 + 91;
        int yBase = graphics.guiHeight() - 39;
        SyncPlayerStatsPacket stats = ClientState.stats();
        int rows = drawHearts(graphics, mc.font, player, stats, xLeft, yBase);
        hud.extractAirBubbles(graphics, player, 0, yBase, xRight);
        drawMana(graphics, mc.font, stats);
        if (TerraConfig.CLIENT.showDefense.get() && stats.defense() > 0) {
            int y = yBase - (rows - 1) * 10;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DEFENSE, xLeft - 13, y, ICON, ICON);
            graphics.centeredText(mc.font, Integer.toString(stats.defense()), xLeft - 9, y + 10, 0xFFFFFFFF);
        }
    }

    /** Draws hearts and returns the number of rows used. */
    private static int drawHearts(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, SyncPlayerStatsPacket stats, int xLeft, int yBase) {
        float maxLife = Math.max(1.0F, player.getMaxHealth());
        float life = Mth.clamp(player.getHealth(), 0.0F, maxLife);
        // Terraria: 20 life per heart up to 20 hearts; beyond 400 life each heart holds more.
        float perHeart = Math.max(20.0F, maxLife / 20.0F);
        int hearts = Math.max(1, Mth.ceil(maxLife / perHeart));
        int golden = Math.min(hearts, stats.lifeFruit());
        int rows = (hearts + 9) / 10;
        boolean lowLife = life <= maxLife * 0.2F;
        for (int i = 0; i < hearts; i++) {
            int x = xLeft + (i % 10) * 8;
            int y = yBase - (i / 10) * 10;
            if (lowLife && player.tickCount % 10 < 5) {
                y += (i % 2 == 0) ? 1 : 0;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_EMPTY, x, y, ICON, ICON);
            float fill = Mth.clamp((life - i * perHeart) / perHeart, 0.0F, 1.0F);
            if (fill > 0.0F) {
                int width = Math.max(1, Math.round(ICON * fill));
                graphics.enableScissor(x, y, x + width, y + ICON);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, i < golden ? HEART_GOLDEN : HEART_FULL, x, y, ICON, ICON);
                graphics.disableScissor();
            }
        }
        if (TerraConfig.CLIENT.showLifeText.get()) {
            String text = "Life: " + Mth.ceil(life) + "/" + Math.round(maxLife);
            graphics.text(font, text, xLeft, yBase - (rows - 1) * 10 - 10, 0xFFFFFFFF, true);
        }
        return rows;
    }

    private static void drawMana(GuiGraphicsExtractor graphics, Font font, SyncPlayerStatsPacket stats) {
        int maxMana = stats.maxMana();
        if (maxMana <= 0) {
            return;
        }
        float perStar = Math.max(20.0F, maxMana / 10.0F);
        int stars = Math.max(1, Mth.ceil(maxMana / perStar));
        int x = graphics.guiWidth() - 14;
        int y0 = graphics.guiHeight() / 2 - stars * 10 / 2;
        if (TerraConfig.CLIENT.showManaText.get()) {
            String text = Integer.toString(stats.mana());
            graphics.text(font, text, x + 5 - font.width(text), y0 - 11, 0xFF9696FF, true);
        }
        for (int i = 0; i < stars; i++) {
            int y = y0 + i * 10;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STAR_EMPTY, x, y, ICON, ICON);
            float fill = Mth.clamp((stats.mana() - i * perStar) / perStar, 0.0F, 1.0F);
            if (fill > 0.0F) {
                int height = Math.max(1, Math.round(ICON * fill));
                graphics.enableScissor(x, y + ICON - height, x + ICON, y + ICON);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STAR_FULL, x, y, ICON, ICON);
                graphics.disableScissor();
            }
        }
    }
}
