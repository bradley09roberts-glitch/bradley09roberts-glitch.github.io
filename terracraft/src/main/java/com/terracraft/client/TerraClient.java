package com.terracraft.client;

import com.terracraft.TerraCraft;
import com.terracraft.client.gui.AccessoryScreen;
import com.terracraft.client.hud.TerrariaHud;
import com.terracraft.client.renderer.MobSprites;
import com.terracraft.client.renderer.TerrariaMobRenderer;
import com.terracraft.client.renderer.TerrariaProjectileRenderer;
import com.terracraft.registry.content.MobContent;
import com.terracraft.registry.ModEntities;
import com.terracraft.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only wiring. Only ever loaded when running on the physical client. */
public final class TerraClient {
    private TerraClient() {}

    public static void init(BusGroup modBus) {
        FMLClientSetupEvent.getBus(modBus).addListener(TerraClient::onClientSetup);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientState.clear());
        RegisterKeyMappingsEvent.BUS.addListener(KeyBindings::register);
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerEntityRenderer(ModEntities.PROJECTILE.get(), TerrariaProjectileRenderer::new);
            com.terracraft.client.renderer.CreatureRenderers.register(event);
        });
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(com.terracraft.client.model.TerraModels::registerLayers);
        RegisterClientReloadListenersEvent.BUS.addListener(event -> event.registerReloadListener(MobSprites.INSTANCE));
        AddGuiOverlayLayersEvent.BUS.addListener(TerrariaHud::register);
        ItemTooltipEvent.BUS.addListener(ItemTooltips::onTooltip);
        ClientEvents.register();
        com.terracraft.menu.AccessoryMenu.clientSlotCount = () -> ClientState.stats().accessorySlots();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.ACCESSORIES.get(), AccessoryScreen::new));
        TerraCraft.LOGGER.info("TerraCraft client setup");
    }
}
