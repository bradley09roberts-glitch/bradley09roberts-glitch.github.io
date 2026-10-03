package com.terracraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terracraft.TerraCraft;
import com.terracraft.client.ClientState;
import com.terracraft.client.model.WingsModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws the wings a player wears (see {@link ClientState#wings}) attached to their back. */
public class TerraWingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final WingsModel model;

    public TerraWingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, WingsModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        var wings = ClientState.wings(state.id);
        if (wings == null || state.isInvisible) {
            return;
        }
        Identifier texture = TerraCraft.id("textures/entity/wings/" + wings.style() + ".png");
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.scale(1.3F, 1.3F, 1.3F);   // Terraria's wings are about as tall as the player
        collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY,
            state.outlineColor, null);
        poseStack.popPose();
    }
}
