package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The Wall of Flesh's mouth: fleshy lips around a gaping maw with a row of teeth top and bottom. */
public class FleshMouthModel extends EntityModel<TerraRenderState> {
    private final ModelPart upper;
    private final ModelPart lower;

    public FleshMouthModel(ModelPart root) {
        super(root);
        ModelPart mouth = root.getChild("mouth");
        this.upper = mouth.getChild("upper");
        this.lower = mouth.getChild("lower");
    }

    public static LayerDefinition createMouth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition mouth = mesh.getRoot().addOrReplaceChild("mouth", CubeListBuilder.create()
            .texOffs(0, 40).addBox(-8.0F, -4.0F, -2.0F, 16.0F, 8.0F, 6.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        mouth.addOrReplaceChild("upper", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-9.0F, -8.0F, -6.0F, 18.0F, 8.0F, 8.0F)
            .texOffs(0, 54).addBox(-8.0F, 0.0F, -6.0F, 16.0F, 2.0F, 1.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
        mouth.addOrReplaceChild("lower", CubeListBuilder.create()
            .texOffs(0, 18).addBox(-9.0F, 0.0F, -6.0F, 18.0F, 8.0F, 8.0F)
            .texOffs(0, 58).addBox(-8.0F, -2.0F, -6.0F, 16.0F, 2.0F, 1.0F), PartPose.offset(0.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        float chomp = Math.abs(Mth.sin(state.ageInTicks * 0.15F)) * 0.35F;
        upper.xRot = chomp;
        lower.xRot = -chomp;
    }
}
