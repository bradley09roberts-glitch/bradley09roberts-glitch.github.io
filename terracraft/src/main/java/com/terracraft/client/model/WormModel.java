package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** One worm segment (head with snapping mandibles, ridged body, tapering tail), pitched along the worm. */
public class WormModel extends EntityModel<TerraRenderState> {
    private final ModelPart segment;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart leftJaw;
    private final ModelPart rightJaw;

    public WormModel(ModelPart root) {
        super(root);
        this.segment = root.getChild("segment");
        this.head = segment.getChild("head");
        this.body = segment.getChild("body");
        this.tail = segment.getChild("tail");
        this.leftJaw = head.getChild("left_jaw");
        this.rightJaw = head.getChild("right_jaw");
    }

    public static LayerDefinition createWorm() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition segment = mesh.getRoot().addOrReplaceChild("segment", CubeListBuilder.create(), PartPose.offset(0.0F, 19.0F, 0.0F));
        PartDefinition head = segment.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -5.0F, -6.0F, 10.0F, 10.0F, 12.0F), PartPose.ZERO);
        head.addOrReplaceChild("left_jaw", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -1.0F, -5.0F, 1.0F, 2.0F, 5.0F), PartPose.offset(3.0F, 1.0F, -6.0F));
        head.addOrReplaceChild("right_jaw", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -1.0F, -5.0F, 1.0F, 2.0F, 5.0F), PartPose.offset(-3.0F, 1.0F, -6.0F));
        segment.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 24).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.ZERO);
        segment.addOrReplaceChild("tail", CubeListBuilder.create()
            .texOffs(0, 44).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 8.0F, 10.0F)
            .texOffs(40, 44).addBox(-2.0F, -2.0F, 5.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        String v = state.variant;
        head.visible = v.endsWith("_head");
        tail.visible = v.endsWith("_tail");
        body.visible = !head.visible && !tail.visible;
        segment.xRot = state.xRot * Mth.DEG_TO_RAD;
        float snap = 0.35F + Mth.sin(state.ageInTicks * 0.5F) * 0.3F;
        leftJaw.yRot = -snap;
        rightJaw.yRot = snap;
    }
}
