package com.squidgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.squidgame.SquidGameMod;
import com.squidgame.client.state.ClientState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/**
 * Dresses tournament players in the green tracksuit: an inflated copy of the player model textured with
 * {@code player_tracksuit(.slim).png} (transparent on the head so their own face shows) plus the number bibs on the
 * chest and back. Which players wear it is driven by the server's {@code NumbersPayload}.
 */
public class TracksuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WIDE = SquidGameMod.id("textures/entity/player_tracksuit.png");
    private static final ResourceLocation SLIM = SquidGameMod.id("textures/entity/player_tracksuit_slim.png");

    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    public TracksuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.wide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.35f), false), 64, 64).bakeRoot(), false);
        this.slim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.35f), true), 64, 64).bakeRoot(), true);
        for (PlayerModel<AbstractClientPlayer> m : java.util.List.of(wide, slim)) {
            m.hat.visible = false;
            m.jacket.visible = false;
            m.leftSleeve.visible = false;
            m.rightSleeve.visible = false;
            m.leftPants.visible = false;
            m.rightPants.visible = false;
        }
    }

    public static void register(PlayerRenderer renderer, EntityModelSet models) {
        // used by the Fabric registration callback in SquidGameClient
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Map<UUID, Integer> numbers = ClientState.numbers;
        Integer number = numbers.get(player.getUUID());
        if (number == null || player.isInvisible()) {
            return;
        }
        boolean isSlim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        PlayerModel<AbstractClientPlayer> m = isSlim ? slim : wide;
        getParentModel().copyPropertiesTo(m);
        m.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        m.head.visible = false; // the player's own head/face stays visible
        m.body.visible = true;
        renderColoredCutoutModel(m, isSlim ? SLIM : WIDE, poseStack, buffers, light, player, -1);

        // bibs on the torso (body part space: x right, y down, z forward = +Z)
        poseStack.pushPose();
        m.body.translateAndRotate(poseStack);
        poseStack.translate(0f, 6f / 16f, 0f);
        float zFront = -(2f + 0.35f + 0.03f) / 16f;
        poseStack.pushPose();
        poseStack.translate(0f, -2.5f / 16f, zFront);
        poseStack.scale(1f, -1f, 1f);
        BibRenderer.draw(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, number, true, 9f, 5f);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0f, -2.5f / 16f, -zFront);
        poseStack.scale(1f, -1f, 1f);
        BibRenderer.draw(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, number, false, 9f, 5f);
        poseStack.popPose();
        poseStack.popPose();
        m.head.visible = true;
    }
}
