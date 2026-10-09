package io.github.bradley09roberts.hardcorefriends.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.people.Skins;

/**
 * Draws a friend (named friend, newcomer or child) like a player: the skin from the skin list ({@link Skins}) on the
 * wide-armed or slim-armed player model to match it, with armour, held items, heads and elytra. A child is drawn at
 * their smaller size by the game itself (the scale attribute), name tag and shadow included.
 *
 * <p>It is a plain mob renderer with the humanoid layers added here, rather than a {@code HumanoidMobRenderer}, so the
 * model can be chosen per friend: the render state says which, and {@link #submit} picks the model before drawing.
 */
public class CompanionRenderer extends MobRenderer<CompanionEntity, CompanionRenderState, CompanionModel> {
	private final CompanionModel wideModel;
	private final CompanionModel slimModel;

	public CompanionRenderer(EntityRendererProvider.Context context) {
		this(context, new CompanionModel(context.bakeLayer(ModelLayers.PLAYER), false),
			new CompanionModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true));
	}

	private CompanionRenderer(EntityRendererProvider.Context context, CompanionModel wide, CompanionModel slim) {
		super(context, wide, 0.5F);
		this.wideModel = wide;
		this.slimModel = slim;
		// The layers HumanoidMobRenderer gives a humanoid mob, then armour for whichever model is in use.
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
		this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(new ArmourLayer(this,
			new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(),
				part -> new CompanionModel(part, false)), context.getEquipmentRenderer()),
			new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_SLIM_ARMOR, context.getModelSet(),
				part -> new CompanionModel(part, true)), context.getEquipmentRenderer())));
	}

	@Override
	public CompanionRenderState createRenderState() {
		return new CompanionRenderState();
	}

	@Override
	public void extractRenderState(CompanionEntity entity, CompanionRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.leftArmPose = armPose(entity, HumanoidArm.LEFT);
		state.rightArmPose = armPose(entity, HumanoidArm.RIGHT);
		state.skinId = entity.getSkinId();
		state.slim = Skins.forDrawing(state.skinId).slim();
	}

	/** Picks the wide or slim model for this friend's skin, then draws as usual (the layers use the same model). */
	@Override
	public void submit(CompanionRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	@Override
	public Identifier getTextureLocation(CompanionRenderState state) {
		return Skins.forDrawing(state.skinId).texture();
	}

	@Override
	protected void scale(CompanionRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same as AvatarRenderer
	}

	private static HumanoidModel.ArmPose armPose(CompanionEntity mob, HumanoidArm arm) {
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

	/** Armour shaped for the model in use: the slim armour set on slim arms, the wide one otherwise. */
	private static final class ArmourLayer extends RenderLayer<CompanionRenderState, CompanionModel> {
		private final HumanoidArmorLayer<CompanionRenderState, CompanionModel, CompanionModel> wide;
		private final HumanoidArmorLayer<CompanionRenderState, CompanionModel, CompanionModel> slim;

		ArmourLayer(RenderLayerParent<CompanionRenderState, CompanionModel> parent,
				HumanoidArmorLayer<CompanionRenderState, CompanionModel, CompanionModel> wide,
				HumanoidArmorLayer<CompanionRenderState, CompanionModel, CompanionModel> slim) {
			super(parent);
			this.wide = wide;
			this.slim = slim;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CompanionRenderState state,
				float yRot, float xRot) {
			(state.slim ? slim : wide).submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
		}
	}
}
