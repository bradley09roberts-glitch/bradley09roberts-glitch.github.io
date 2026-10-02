package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Cave Bat: small furry body, pointed ears and fast-flapping leathery wings. */
public class BatModel3D extends EntityModel<TerraRenderState> {
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public BatModel3D(ModelPart root) {
        super(root);
        this.rightWing = root.getChild("right_wing");
        this.leftWing = root.getChild("left_wing");
    }

    public static LayerDefinition createBat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-2.0F, 14.0F, -1.5F, 4.0F, 5.0F, 3.0F)
            .texOffs(16, 0).addBox(-2.0F, 10.5F, -2.0F, 4.0F, 4.0F, 4.0F)
            .texOffs(32, 0).addBox(-2.0F, 8.5F, -0.5F, 1.0F, 2.0F, 1.0F)
            .texOffs(32, 0).addBox(1.0F, 8.5F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 16).addBox(-9.0F, 0.0F, 0.0F, 9.0F, 7.0F, 0.0F),
            PartPose.offset(-2.0F, 14.0F, 0.5F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 24).mirror().addBox(0.0F, 0.0F, 0.0F, 9.0F, 7.0F, 0.0F),
            PartPose.offset(2.0F, 14.0F, 0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        float flap = Mth.sin(state.ageInTicks * 1.4F) * 0.9F;
        rightWing.zRot = flap;
        leftWing.zRot = -flap;
    }
}
