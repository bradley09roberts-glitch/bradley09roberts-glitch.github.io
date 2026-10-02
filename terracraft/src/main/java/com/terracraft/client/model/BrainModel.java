package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Brain of Cthulhu: two pulsing hemispheres on a brain stem with dangling nerve tendrils. */
public class BrainModel extends EntityModel<TerraRenderState> {
    private final ModelPart brain;
    private final ModelPart[] tendrils = new ModelPart[4];

    public BrainModel(ModelPart root) {
        super(root);
        this.brain = root.getChild("brain");
        for (int i = 0; i < 4; i++) {
            tendrils[i] = brain.getChild("tendril" + i);
        }
    }

    public static LayerDefinition createBrain() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition brain = mesh.getRoot().addOrReplaceChild("brain", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-7.5F, -10.0F, -8.0F, 7.0F, 10.0F, 16.0F)
            .texOffs(0, 26).addBox(0.5F, -10.0F, -8.0F, 7.0F, 10.0F, 16.0F)
            .texOffs(46, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
        float[][] spots = {{-4.0F, -3.0F}, {4.0F, -3.0F}, {-3.0F, 4.0F}, {3.0F, 4.0F}};
        for (int i = 0; i < 4; i++) {
            brain.addOrReplaceChild("tendril" + i, CubeListBuilder.create().texOffs(46, 10).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F),
                PartPose.offset(spots[i][0], 0.0F, spots[i][1]));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.25F) * 0.04F;
        brain.xScale = pulse;
        brain.yScale = pulse;
        brain.zScale = pulse;
        for (int i = 0; i < 4; i++) {
            tendrils[i].xRot = Mth.sin(state.ageInTicks * 0.2F + i) * 0.4F;
            tendrils[i].zRot = Mth.cos(state.ageInTicks * 0.17F + i * 1.3F) * 0.4F;
        }
    }
}
