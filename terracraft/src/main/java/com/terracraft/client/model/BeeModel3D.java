package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Insect body for Hornets, bees and the Queen Bee: head, thorax, striped abdomen with stinger and buzzing wings. */
public class BeeModel3D extends EntityModel<TerraRenderState> {
    private final ModelPart body;
    private final ModelPart abdomen;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public BeeModel3D(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.abdomen = body.getChild("abdomen");
        this.leftWing = body.getChild("left_wing");
        this.rightWing = body.getChild("right_wing");
    }

    public static LayerDefinition createBee() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-2.5F, -3.0F, -9.0F, 5.0F, 5.0F, 4.0F)
            .texOffs(20, 0).addBox(-3.0F, -3.5F, -5.0F, 6.0F, 6.0F, 5.0F), PartPose.offset(0.0F, 18.0F, 0.0F));
        body.addOrReplaceChild("abdomen", CubeListBuilder.create()
            .texOffs(0, 12).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 9.0F)
            .texOffs(34, 12).addBox(-0.5F, 0.0F, 9.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -0.5F, -0.5F, -0.25F, 0.0F, 0.0F));
        body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(32, 20).addBox(0.0F, 0.0F, -2.0F, 8.0F, 0.0F, 5.0F),
            PartPose.offset(1.5F, -3.5F, -3.0F));
        body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(32, 20).mirror().addBox(-8.0F, 0.0F, -2.0F, 8.0F, 0.0F, 5.0F),
            PartPose.offset(-1.5F, -3.5F, -3.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        float flap = Mth.cos(state.ageInTicks * 2.6F) * 0.6F;
        leftWing.zRot = -0.3F + flap;
        rightWing.zRot = 0.3F - flap;
        body.y = 18.0F + Mth.sin(state.ageInTicks * 0.2F) * 0.5F;
        abdomen.xRot = -0.25F + Mth.sin(state.ageInTicks * 0.15F) * 0.08F;
        body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
    }
}
