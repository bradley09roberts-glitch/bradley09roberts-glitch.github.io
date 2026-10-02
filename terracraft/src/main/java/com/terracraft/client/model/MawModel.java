package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Flying maw (Eater of Souls, Crimera): a gaping head with four mandibles and a short wriggling body. */
public class MawModel extends EntityModel<TerraRenderState> {
    private final ModelPart maw;
    private final ModelPart[] mandibles = new ModelPart[4];
    private final ModelPart body;

    public MawModel(ModelPart root) {
        super(root);
        this.maw = root.getChild("maw");
        this.body = maw.getChild("body");
        for (int i = 0; i < 4; i++) {
            mandibles[i] = maw.getChild("mandible" + i);
        }
    }

    public static LayerDefinition createMaw() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition maw = mesh.getRoot().addOrReplaceChild("maw", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 19.0F, 0.0F));
        PartDefinition body = maw.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, 0.0F, 4.0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 16).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 5.0F), PartPose.offset(0.0F, 0.0F, 6.0F));
        float[][] corners = {{-3.0F, -3.0F}, {3.0F, -3.0F}, {-3.0F, 3.0F}, {3.0F, 3.0F}};
        for (int i = 0; i < 4; i++) {
            maw.addOrReplaceChild("mandible" + i, CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F),
                PartPose.offset(corners[i][0], corners[i][1], -4.0F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        maw.xRot = state.xRot * Mth.DEG_TO_RAD;
        float open = 0.25F + Mth.sin(state.ageInTicks * 0.6F) * 0.25F;
        for (int i = 0; i < 4; i++) {
            mandibles[i].yRot = (i % 2 == 0 ? 1 : -1) * open;
            mandibles[i].xRot = (i < 2 ? -1 : 1) * open;
        }
        body.yRot = Mth.sin(state.ageInTicks * 0.4F) * 0.3F;
    }
}
