package com.squidgame.client;

import com.squidgame.client.hud.SquidHud;
import com.squidgame.client.net.ClientNetworking;
import com.squidgame.client.render.ContestantRenderer;
import com.squidgame.client.render.DollRenderer;
import com.squidgame.client.render.GuardRenderer;
import com.squidgame.client.render.TracksuitLayer;
import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.registry.ModBlocks;
import com.squidgame.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** Client-only entry point: renderers, HUD, network receivers, tracksuit layer, screens. */
@Environment(EnvType.CLIENT)
public class SquidGameClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CONTESTANT, ContestantRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUARD, GuardRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOLL, DollRenderer::new);
        EntityRendererRegistry.register(ModEntities.MARBLE, ThrownItemRenderer::new);

        for (var b : ModBlocks.translucent()) {
            BlockRenderLayerMap.INSTANCE.putBlock(b, RenderType.translucent());
        }

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer pr) {
                helper.register(new TracksuitLayer(pr, context.getModelSet()));
            }
        });

        HudRenderCallback.EVENT.register(SquidHud::render);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(com.squidgame.client.audio.Ambience::tick);
        ClientNetworking.init();
        ScreenRegistry.discoverGameClients();
    }
}
