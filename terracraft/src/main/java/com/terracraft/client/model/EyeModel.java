package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Flying eyeball (Demon Eye, Servant of Cthulhu, Eye of Cthulhu): a rounded ball that pitches toward where it
 * flies, with three veiny tendrils trailing behind. The Eye of Cthulhu's mouth form only swaps the texture.
 */
public class EyeModel extends EntityModel<TerraRenderState> {
    private final ModelPart eye;
    private final ModelPart[] tendrils = new ModelPart[3];

    public EyeModel(ModelPart root) {
        super(root);
        this.eye = root.getChild("eye");
        for (int i = 0; i < 3; i++) {
            tendrils[i] = eye.getChild("tendril" + i);
        }
    }

    public static LayerDefinition createEye() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition eye = mesh.getRoot().addOrReplaceChild("eye", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F)
            .texOffs(0, 16).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F)
            .texOffs(32, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 10.0F, 6.0F)
            .texOffs(0, 28).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 10.0F), PartPose.offset(0.0F, 19.0F, 0.0F));
        float[][] spots = {{-2.0F, -1.5F}, {2.0F, -1.5F}, {0.0F, 2.0F}};
        for (int i = 0; i < 3; i++) {
            eye.addOrReplaceChild("tendril" + i, CubeListBuilder.create().texOffs(40, 20 + i * 10).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F),
                PartPose.offset(spots[i][0], spots[i][1], 4.0F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        eye.xRot = state.xRot * Mth.DEG_TO_RAD;
        eye.zRot = state.spin * Mth.DEG_TO_RAD;
        for (int i = 0; i < 3; i++) {
            tendrils[i].yRot = Mth.sin(state.ageInTicks * 0.6F + i * 2.1F) * 0.35F;
            tendrils[i].xRot = Mth.cos(state.ageInTicks * 0.5F + i) * 0.25F;
        }
    }
}
