package com.squidgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.squidgame.entity.DollEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The giant doll: glowing red eyes follow the synced eye state. */
public class DollRenderer extends GeoEntityRenderer<DollEntity> {
    public DollRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DollModel());
        this.shadowRadius = 1.5f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, DollEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        boolean on = animatable.eyesOn();
        model.getBone("eyes_on").ifPresent(b -> b.setHidden(!on));
        model.getBone("eyes_off").ifPresent(b -> b.setHidden(on));
    }

    @Override
    public boolean shouldShowName(DollEntity entity) {
        return false;
    }

    @Override
    public boolean shouldRender(DollEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true; // huge model: never frustum-cull on the entity's small hitbox
    }
}
