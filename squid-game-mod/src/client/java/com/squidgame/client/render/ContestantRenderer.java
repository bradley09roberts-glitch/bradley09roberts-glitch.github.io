package com.squidgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.squidgame.core.Appearance;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.registry.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Renders contestants. Appearance variety needs no extra textures: skin and hair bones are tinted per entity,
 * hairstyles / faces / glasses are bone visibility toggles, and the number bibs are drawn by {@link NumberPatchLayer}.
 */
public class ContestantRenderer extends GeoEntityRenderer<ContestantEntity> {
    private static final String[] FACE_BONES = {"face_0", "face_1", "face_2", "face_3", "face_4", "face_5"};
    private static final String[] SKIN_BONES = {"head_skin", "neck_skin", "left_hand_skin", "right_hand_skin"};

    public ContestantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ContestantModel());
        this.shadowRadius = 0.4f;
        withScale(0.9375f);
        addRenderLayer(new NumberPatchLayer(this));
        addRenderLayer(new BlockAndItemGeoLayer<>(this, (bone, e) -> {
            if (!bone.getName().equals("item_right")) {
                return null;
            }
            return switch (e.heldItem()) {
                case 1 -> new ItemStack(Items.STICK);
                case 2 -> new ItemStack(ModItems.MARBLE);
                case 3 -> new ItemStack(Items.IRON_NUGGET);
                default -> null;
            };
        }, (bone, e) -> null));
    }

    @Override
    public void preRender(PoseStack poseStack, ContestantEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        Appearance a = animatable.appearance();
        for (int i = 1; i < Appearance.HAIR_BONES.length; i++) {
            final boolean visible = a.hairStyle() == i;
            model.getBone(Appearance.HAIR_BONES[i]).ifPresent(b -> b.setHidden(!visible));
        }
        for (int i = 0; i < FACE_BONES.length; i++) {
            final boolean visible = a.face() == i;
            model.getBone(FACE_BONES[i]).ifPresent(b -> b.setHidden(!visible));
        }
        model.getBone("glasses").ifPresent(b -> b.setHidden(!a.glasses()));
        // build variation: slightly taller / wider bodies
        poseStack.scale(a.widthScale(), a.heightScale(), a.widthScale());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ContestantEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        String n = bone.getName();
        Appearance a = animatable.appearance();
        int tint = colour;
        if (n.endsWith("_skin")) {
            tint = Appearance.SKIN_TONES[a.skinTone()];
        } else if (n.startsWith("hair_")) {
            tint = Appearance.HAIR_COLORS[a.hairColor()];
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, tint);
    }

    @Override
    public boolean shouldShowName(ContestantEntity entity) {
        return false;
    }
}
