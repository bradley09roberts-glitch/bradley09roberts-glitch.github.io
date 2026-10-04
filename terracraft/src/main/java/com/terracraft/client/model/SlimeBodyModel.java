package com.terracraft.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Terraria slime: a translucent jelly dome with a darker core and two eyes, squashing and stretching as it
 * hops. The King Slime layer adds a crown and the silhouette of a ninja trapped inside; the Mother Slime
 * layer adds a baby slime inside.
 */
public class SlimeBodyModel extends EntityModel<TerraRenderState> {
    private final ModelPart body;
    private final ModelPart wingRight;
    private final ModelPart wingLeft;
    private final boolean wingsAlways;

    public SlimeBodyModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
        this.body = root.getChild("body");
        this.wingRight = body.hasChild("wing_right") ? body.getChild("wing_right") : null;
        this.wingLeft = body.hasChild("wing_left") ? body.getChild("wing_left") : null;
        this.wingsAlways = body.hasChild("halo");
    }

    private static PartDefinition base(MeshDefinition mesh) {
        // Pivot at the feet so squashing keeps the slime on the ground.
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        body.addOrReplaceChild("jelly", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-7.0F, -10.0F, -7.0F, 14.0F, 10.0F, 14.0F)
            .texOffs(0, 24).addBox(-5.0F, -13.0F, -5.0F, 10.0F, 3.0F, 10.0F), PartPose.ZERO);
        body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 40).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
        body.addOrReplaceChild("eyes", CubeListBuilder.create()
            .texOffs(32, 40).addBox(-4.5F, -8.0F, -7.6F, 2.0F, 3.0F, 1.0F)
            .texOffs(32, 44).addBox(2.5F, -8.0F, -7.6F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
        return body;
    }

    public static LayerDefinition createSlime() {
        MeshDefinition mesh = new MeshDefinition();
        base(mesh);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createMotherSlime() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = base(mesh);
        body.addOrReplaceChild("baby", CubeListBuilder.create().texOffs(40, 48).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createKingSlime() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = base(mesh);
        body.addOrReplaceChild("crown", CubeListBuilder.create()
            .texOffs(64, 0).addBox(-4.5F, -15.0F, -4.5F, 9.0F, 2.0F, 9.0F)
            .texOffs(64, 16).addBox(-4.5F, -17.0F, -4.5F, 1.0F, 2.0F, 1.0F)
            .texOffs(64, 16).addBox(3.5F, -17.0F, -4.5F, 1.0F, 2.0F, 1.0F)
            .texOffs(64, 16).addBox(-4.5F, -17.0F, 3.5F, 1.0F, 2.0F, 1.0F)
            .texOffs(64, 16).addBox(3.5F, -17.0F, 3.5F, 1.0F, 2.0F, 1.0F)
            .texOffs(70, 16).addBox(-0.5F, -18.0F, -4.8F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
        body.addOrReplaceChild("ninja", CubeListBuilder.create()
            .texOffs(64, 24).addBox(-1.5F, -6.0F, -1.0F, 3.0F, 4.0F, 2.0F)
            .texOffs(80, 24).addBox(-1.5F, -9.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * Queen Slime: a tiara of crystal points and a jewel, crystal spikes rising out of the gel, and a pair of
     * crystalline wings that only show in her second (flying) phase.
     */
    public static LayerDefinition createQueenSlime() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = base(mesh);
        body.addOrReplaceChild("tiara", CubeListBuilder.create()
            .texOffs(64, 0).addBox(-4.5F, -14.5F, -4.5F, 9.0F, 1.5F, 9.0F)
            .texOffs(64, 16).addBox(-0.5F, -18.5F, -4.7F, 1.0F, 4.0F, 1.0F)
            .texOffs(64, 16).addBox(-3.0F, -17.0F, -4.7F, 1.0F, 2.5F, 1.0F)
            .texOffs(64, 16).addBox(2.0F, -17.0F, -4.7F, 1.0F, 2.5F, 1.0F)
            .texOffs(64, 16).addBox(-4.5F, -16.5F, -1.0F, 1.0F, 2.0F, 1.0F)
            .texOffs(64, 16).addBox(3.5F, -16.5F, -1.0F, 1.0F, 2.0F, 1.0F)
            .texOffs(70, 16).addBox(-1.0F, -16.0F, -5.2F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
        body.addOrReplaceChild("crystals", CubeListBuilder.create()
            .texOffs(80, 16).addBox(-1.0F, -9.0F, -1.0F, 2.0F, 5.0F, 2.0F)
            .texOffs(80, 16).addBox(-4.0F, -6.0F, 1.0F, 2.0F, 4.0F, 2.0F)
            .texOffs(80, 16).addBox(2.0F, -7.0F, 0.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
        wings(body, 9.0F, 14.0F);
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** Heavenly Slime: a small slime with a halo and little feathered wings. */
    public static LayerDefinition createHeavenlySlime() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = base(mesh);
        body.addOrReplaceChild("halo", CubeListBuilder.create()
            .texOffs(64, 0).addBox(-4.0F, -16.5F, -4.0F, 8.0F, 1.0F, 8.0F), PartPose.ZERO);
        wings(body, 7.0F, 10.0F);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void wings(PartDefinition body, float height, float length) {
        body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(96, 24).addBox(-length, -height, 0.0F, length, height, 1.0F),
            PartPose.offset(-4.0F, -6.0F, 5.0F));
        body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(96, 40).addBox(0.0F, -height, 0.0F, length, height, 1.0F),
            PartPose.offset(4.0F, -6.0F, 5.0F));
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        if (wingRight != null) {
            boolean show = wingsAlways || state.variant.endsWith("_winged");
            wingRight.visible = show;
            wingLeft.visible = show;
            float flap = Mth.sin(state.ageInTicks * 0.9F) * 0.6F;
            wingRight.yRot = 0.5F + flap;
            wingLeft.yRot = -0.5F - flap;
        }
        // stretch while rising, squash on landing
        float stretch = state.airborne ? Mth.clamp(state.verticalSpeed * 0.8F, -0.25F, 0.3F) : -0.08F * Mth.sin(state.ageInTicks * 0.15F) - 0.04F;
        body.yScale = 1.0F + stretch;
        body.xScale = 1.0F - stretch * 0.5F;
        body.zScale = 1.0F - stretch * 0.5F;
    }
}
