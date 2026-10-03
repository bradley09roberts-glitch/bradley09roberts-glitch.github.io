package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Skeletron's hand: two forearm bones, a palm and four clutching fingers plus a thumb. */
public class BoneHandModel extends EntityModel<TerraRenderState> {
    private final ModelPart hand;
    private final ModelPart[] fingers = new ModelPart[5];

    public BoneHandModel(ModelPart root) {
        super(root);
        this.hand = root.getChild("hand");
        for (int i = 0; i < fingers.length; i++) {
            fingers[i] = hand.getChild("finger" + i);
        }
    }

    public static LayerDefinition createHand() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition hand = mesh.getRoot().addOrReplaceChild("hand", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-3.0F, 0.0F, -1.5F, 6.0F, 5.0F, 3.0F)
            .texOffs(8, 10).addBox(-2.5F, -9.0F, -1.0F, 2.0F, 9.0F, 2.0F)
            .texOffs(8, 10).addBox(0.5F, -9.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offset(0.0F, 13.0F, 0.0F));
        float[] xs = {-2.5F, -1.0F, 0.5F, 2.0F};
        for (int i = 0; i < 4; i++) {
            hand.addOrReplaceChild("finger" + i, CubeListBuilder.create().texOffs(0, 10).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
                PartPose.offset(xs[i], 5.0F, 0.0F));
        }
        hand.addOrReplaceChild("finger4", CubeListBuilder.create().texOffs(4, 10).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
            PartPose.offsetAndRotation(-3.0F, 2.0F, -1.0F, 0.0F, 0.0F, 0.6F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        float clutch = 0.25F + Mth.sin(state.ageInTicks * 0.2F) * 0.25F;
        for (int i = 0; i < 4; i++) {
            fingers[i].xRot = -clutch - i * 0.04F;
        }
        hand.zRot = Mth.sin(state.ageInTicks * 0.07F) * 0.15F;
    }
}
