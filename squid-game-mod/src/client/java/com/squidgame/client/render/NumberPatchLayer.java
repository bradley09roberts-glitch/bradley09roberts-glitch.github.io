package com.squidgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.squidgame.entity.ContestantEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/** Draws the contestant's number bibs at the {@code number_chest} / {@code number_back} anchor bones. */
public class NumberPatchLayer extends GeoRenderLayer<ContestantEntity> {
    public static final float BIB_W = 9f, BIB_H = 5f;

    public NumberPatchLayer(GeoRenderer<ContestantEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, ContestantEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        String name = bone.getName();
        boolean chest = name.equals("number_chest");
        if (!chest && !name.equals("number_back")) {
            return;
        }
        poseStack.pushPose();
        // the stack is at the bone's transformed origin; move to the pivot (anchor = bib centre)
        poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
        BibRenderer.draw(poseStack, bufferSource, packedLight, packedOverlay, animatable.contestantNumber(), chest, BIB_W, BIB_H);
        poseStack.popPose();
    }
}
