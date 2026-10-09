package io.github.bradley09roberts.hardcorefriends.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The player model (same part tree as PlayerModel) typed to our own render state, wide-armed or slim-armed: a skin
 * painted for slim arms is drawn on the slim model, or its arms would show a stripe of the wrong pixels.
 */
public class CompanionModel extends HumanoidModel<CompanionRenderState> {
	public final ModelPart leftSleeve;
	public final ModelPart rightSleeve;
	public final ModelPart leftPants;
	public final ModelPart rightPants;
	public final ModelPart jacket;
	private final boolean slim;

	public CompanionModel(ModelPart root) {
		this(root, false);
	}

	public CompanionModel(ModelPart root, boolean slim) {
		super(root, RenderTypes::entityTranslucent); // same render type PlayerModel uses
		this.leftSleeve = this.leftArm.getChild("left_sleeve");
		this.rightSleeve = this.rightArm.getChild("right_sleeve");
		this.leftPants = this.leftLeg.getChild("left_pants");
		this.rightPants = this.rightLeg.getChild("right_pants");
		this.jacket = this.body.getChild("jacket");
		this.slim = slim;
	}

	@Override
	public void setupAnim(CompanionRenderState state) {
		this.hat.visible = true;
		this.jacket.visible = true;
		this.leftSleeve.visible = true;
		this.rightSleeve.visible = true;
		this.leftPants.visible = true;
		this.rightPants.visible = true;
		super.setupAnim(state);
	}

	/** As PlayerModel: a slim arm is half a pixel narrower, so the held item moves in by that much. */
	@Override
	public void translateToHand(CompanionRenderState state, HumanoidArm arm, PoseStack poseStack) {
		if (!slim) {
			super.translateToHand(state, arm, poseStack);
			return;
		}
		this.root().translateAndRotate(poseStack);
		ModelPart part = this.getArm(arm);
		float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
		part.x += offset;
		part.translateAndRotate(poseStack);
		part.x -= offset;
	}
}
