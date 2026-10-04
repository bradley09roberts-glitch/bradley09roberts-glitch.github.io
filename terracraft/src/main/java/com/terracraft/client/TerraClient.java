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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring. Only ever loaded when running on the physical client. */
public final class TerraClient {
    private TerraClient() {}

    public static void init(IEventBus modBus) {
        IEventBus game = NeoForge.EVENT_BUS;
        modBus.addListener(TerraClient::onRegisterScreens);
        game.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientState.clear());
        modBus.addListener(KeyBindings::register);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(ModEntities.PROJECTILE.get(), TerrariaProjectileRenderer::new);
            com.terracraft.client.renderer.CreatureRenderers.register(event);
        });
        modBus.addListener(com.terracraft.client.model.TerraModels::registerLayers);
        modBus.addListener((EntityRenderersEvent.AddLayers event) -> {
            for (var type : event.getSkins()) {
                net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> renderer = event.getPlayerRenderer(type);
                if (renderer != null) {
                    renderer.addLayer(new com.terracraft.client.renderer.TerraWingsLayer(renderer,
                        new com.terracraft.client.model.WingsModel(event.getEntityModels().bakeLayer(com.terracraft.client.model.TerraModels.WINGS))));
                }
            }
        });
        modBus.addListener((AddClientReloadListenersEvent event) -> {
            event.addListener(TerraCraft.id("mob_sprites"), MobSprites.INSTANCE);
            event.addListener(TerraCraft.id("armor_models"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> com.terracraft.client.model.ArmorModels.clear());
        });
        modBus.addListener(TerrariaHud::register);
        com.terracraft.client.hud.HealthBars.register(modBus);
        modBus.addListener(TerraArmorClient::register);
        game.addListener(ItemTooltips::onTooltip);
        // no boots slot (see NoBootsSlot): paint over its empty frame in the inventory background
        game.addListener((ScreenEvent.Render.Background event) -> {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen) {
                int x = screen.getGuiLeft() + 7;
                int y = screen.getGuiTop() + 61;
                event.getGuiGraphics().fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
            }
        });
        ClientEvents.register();
        game.addListener((ViewportEvent.ComputeFogColor event) -> {
            if ("blood_moon".equals(ClientState.activeEvent())) {
                // Blood Moon: the night turns red
                event.setRed(Math.min(1.0F, event.getRed() * 0.6F + 0.25F));
                event.setGreen(event.getGreen() * 0.35F);
                event.setBlue(event.getBlue() * 0.35F);
            }
        });
        com.terracraft.menu.AccessoryMenu.clientSlotCount = () -> ClientState.stats().accessorySlots();
        com.terracraft.progression.ProgressionManager.clientView = ClientState::progression;
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ACCESSORIES.get(), AccessoryScreen::new);
    }
}
