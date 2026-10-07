package io.github.bradley09roberts.hardcorefriends.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, CompanionRenderState, CompanionModel> {
	private static final Identifier[] SKINS = new Identifier[FriendId.values().length];
	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "textures/entity/companion/" + FriendId.byOrdinal(i).key() + ".png");
		}
	}

	public CompanionRenderer(EntityRendererProvider.Context context) {
		super(context, new CompanionModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(
			this,
			ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), CompanionModel::new),
			context.getEquipmentRenderer()
		));
	}

	@Override
	public CompanionRenderState createRenderState() {
		return new CompanionRenderState();
	}

	@Override
	public void extractRenderState(CompanionEntity entity, CompanionRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks); // fills HumanoidRenderState + arm poses via getArmPose
		state.skinId = entity.getSkinId();
	}

	@Override
	public Identifier getTextureLocation(CompanionRenderState state) {
		return SKINS[Math.floorMod(state.skinId, SKINS.length)];
	}

	@Override
	protected void scale(CompanionRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same as AvatarRenderer
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(CompanionEntity mob, HumanoidArm arm) {
		ItemStack main = mob.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack off = mob.getItemInHand(InteractionHand.OFF_HAND);
		HumanoidModel.ArmPose mainPose = poseFor(mob, main, InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose offPose = poseFor(mob, off, InteractionHand.OFF_HAND);
		if (mainPose.isTwoHanded()) {
			offPose = off.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}
		return mob.getMainArm() == arm ? mainPose : offPose;
	}

	private static HumanoidModel.ArmPose poseFor(CompanionEntity mob, ItemStack stack, InteractionHand hand) {
		if (stack.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}
		if (!mob.isSwinging() && stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}
		if (mob.getUsedItemHand() == hand && mob.getUseItemRemainingTicks() > 0) {
			switch (stack.getUseAnimation()) {
				case BLOCK: return HumanoidModel.ArmPose.BLOCK;
				case BOW: return HumanoidModel.ArmPose.BOW_AND_ARROW;
				case TRIDENT: return HumanoidModel.ArmPose.THROW_TRIDENT;
				case CROSSBOW: return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
				case SPYGLASS: return HumanoidModel.ArmPose.SPYGLASS;
				case TOOT_HORN: return HumanoidModel.ArmPose.TOOT_HORN;
				case BRUSH: return HumanoidModel.ArmPose.BRUSH;
				case SPEAR: return HumanoidModel.ArmPose.SPEAR;
				default: break;
			}
		}
		return HumanoidMobRenderer.usesSpearPose(stack, hand.asArm(mob.getMainArm()), mob)
			? HumanoidModel.ArmPose.SPEAR
			: HumanoidModel.ArmPose.ITEM;
	}
}
