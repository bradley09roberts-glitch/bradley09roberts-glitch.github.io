package com.longnight.client;

import com.longnight.LongNight;
import com.longnight.registry.ModEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = LongNight.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(CitizenRenderer.BASE, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        e.registerLayerDefinition(CitizenRenderer.CLOTHES, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.08f), false), 64, 64));
        e.registerLayerDefinition(CitizenRenderer.HAIR, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.16f), false), 64, 64));
        e.registerLayerDefinition(CitizenRenderer.ARMOR, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.24f), false), 64, 64));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
    }
}
