package com.squidgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.squidgame.entity.GuardEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Masked guard: the rank decides which mask bone is visible, and whether the rifle is carried. */
public class GuardRenderer extends GeoEntityRenderer<GuardEntity> {
    public GuardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GuardModel());
        this.shadowRadius = 0.45f;
        withScale(0.9375f);
    }

    @Override
    public void preRender(PoseStack poseStack, GuardEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        int rank = animatable.rank();
        model.getBone("mask_circle").ifPresent(b -> b.setHidden(rank != GuardEntity.RANK_CIRCLE));
        model.getBone("mask_triangle").ifPresent(b -> b.setHidden(rank != GuardEntity.RANK_TRIANGLE));
        model.getBone("mask_square").ifPresent(b -> b.setHidden(rank != GuardEntity.RANK_SQUARE));
        model.getBone("rifle").ifPresent(b -> b.setHidden(!animatable.isArmed()));
        model.getBone("collar_black").ifPresent(b -> b.setHidden(rank != GuardEntity.RANK_SQUARE));
    }

    @Override
    public boolean shouldShowName(GuardEntity entity) {
        return false;
    }
}
