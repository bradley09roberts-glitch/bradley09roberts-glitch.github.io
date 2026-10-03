package com.terracraft.client.model;

import com.terracraft.client.ClientState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * A pair of wings on the player's back: two thin 14x16 panels hinged at the shoulder blades, painted per style
 * with cut-out feather or membrane shapes. Folded back on the ground, flapping while rising, spread to glide.
 */
public class WingsModel extends EntityModel<AvatarRenderState> {
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public WingsModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        rightWing = root.getChild("right_wing");
        leftWing = root.getChild("left_wing");
    }

    public static LayerDefinition createWings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 0).addBox(-14.0F, -5.0F, 0.0F, 14.0F, 16.0F, 1.0F),
            PartPose.offset(-1.0F, 2.0F, 2.2F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.0F, -5.0F, 0.0F, 14.0F, 16.0F, 1.0F),
            PartPose.offset(1.0F, 2.0F, 2.2F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        float fold;
        float lift;
        switch (ClientState.wingAnim(state.id)) {
            case FLAP -> {
                fold = 0.35F;
                lift = Mth.sin(state.ageInTicks * 1.3F) * 0.65F + 0.15F;
            }
            case GLIDE -> {
                fold = 0.2F;
                lift = 0.1F + Mth.sin(state.ageInTicks * 0.25F) * 0.05F;
            }
            default -> {
                fold = 1.15F;
                lift = -0.25F;
            }
        }
        if (state.isCrouching) {
            fold += 0.2F;
        }
        rightWing.yRot = fold;
        rightWing.zRot = lift;
        leftWing.yRot = -fold;
        leftWing.zRot = -lift;
    }
}
