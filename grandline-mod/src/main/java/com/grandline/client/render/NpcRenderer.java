package com.grandline.client.render;

import com.grandline.GrandLine;
import com.grandline.npc.HumanoidNpc;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** NPCs use the player rig (so they share every combat animation) with generated skins per role. */
public class NpcRenderer extends HumanoidMobRenderer<HumanoidNpc, PlayerModel<HumanoidNpc>> {
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new RaceLayer<>(this, ctx));
    }

    @Override
    public ResourceLocation getTextureLocation(HumanoidNpc e) {
        return TEX.computeIfAbsent(e.skin(), s -> GrandLine.id("textures/entity/npc/" + s + ".png"));
    }
}
