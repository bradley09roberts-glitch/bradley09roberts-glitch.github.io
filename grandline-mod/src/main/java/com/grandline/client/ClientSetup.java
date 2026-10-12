package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.client.anim.AnimLibrary;
import com.grandline.client.hud.HudOverlay;
import com.grandline.client.render.NpcRenderer;
import com.grandline.client.render.ProjectileRenderer;
import com.grandline.client.render.RaceLayer;
import com.grandline.fruit.DevilFruit;
import com.grandline.item.DevilFruitItem;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client registration: renderers, race layers, keys, HUD, animation reload listener, item tints. */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.ABILITY_PROJECTILE.get(), ProjectileRenderer::new);
        e.registerEntityRenderer(ModEntities.HUMANOID_NPC.get(), NpcRenderer::new);
        com.grandline.client.render.ShipRenderers.register(e);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer pr) pr.addLayer(new RaceLayer<>(pr, e.getContext()));
        }
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        for (var k : Keys.ALL) e.register(k);
    }

    @SubscribeEvent
    public static void hud(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, GrandLine.id("hud"), HudOverlay::render);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(new AnimLibrary());
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item e) {
        e.register((stack, layer) -> {
            DevilFruit f = DevilFruitItem.fruit(stack);
            if (f == null) return 0xFFFFFFFF;
            return 0xFF000000 | (layer == 0 ? f.color : f.color2);
        }, ModItems.DEVIL_FRUIT.get());
    }

    private ClientSetup() {}
}
