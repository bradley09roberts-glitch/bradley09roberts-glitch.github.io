package io.github.bradley09roberts.hardcorefriends.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Wide player model (same part tree as PlayerModel) typed to our own render state. */
public class CompanionModel extends HumanoidModel<CompanionRenderState> {
	public final ModelPart leftSleeve;
	public final ModelPart rightSleeve;
	public final ModelPart leftPants;
	public final ModelPart rightPants;
	public final ModelPart jacket;

	public CompanionModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent); // same render type PlayerModel uses
		this.leftSleeve = this.leftArm.getChild("left_sleeve");
		this.rightSleeve = this.rightArm.getChild("right_sleeve");
		this.leftPants = this.leftLeg.getChild("left_pants");
		this.rightPants = this.rightLeg.getChild("right_pants");
		this.jacket = this.body.getChild("jacket");
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
}
