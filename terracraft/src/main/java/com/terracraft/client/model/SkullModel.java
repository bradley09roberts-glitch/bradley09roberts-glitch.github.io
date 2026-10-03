package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** A floating skull with a chattering jaw: Skeletron's head, the Dungeon Guardian and Cursed Skulls. Spins with {@code state.spin}. */
public class SkullModel extends EntityModel<TerraRenderState> {
    private final ModelPart skull;
    private final ModelPart jaw;

    public SkullModel(ModelPart root) {
        super(root);
        this.skull = root.getChild("skull");
        this.jaw = skull.getChild("jaw");
    }

    public static LayerDefinition createSkull() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition skull = mesh.getRoot().addOrReplaceChild("skull", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 8.0F, 10.0F), PartPose.offset(0.0F, 19.0F, 0.0F));
        skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 18).addBox(-4.0F, 0.0F, -8.0F, 8.0F, 3.0F, 8.0F),
            PartPose.offset(0.0F, 2.0F, 3.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        skull.zRot = state.spin * Mth.DEG_TO_RAD;
        skull.y = 19.0F + Mth.sin(state.ageInTicks * 0.1F) * 0.6F;
        jaw.xRot = 0.1F + Math.abs(Mth.sin(state.ageInTicks * (state.aggressive ? 0.5F : 0.15F))) * 0.35F;
    }
}
