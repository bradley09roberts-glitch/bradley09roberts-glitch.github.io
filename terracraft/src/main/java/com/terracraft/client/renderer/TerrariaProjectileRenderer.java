package com.terracraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Renders every {@link TerrariaProjectile} from its kind's flat pixel-art texture: arrows and bullets as
 * two crossed quads along the flight direction, orbs and spinning weapons as camera-facing sprites.
 */
public class TerrariaProjectileRenderer extends EntityRenderer<TerrariaProjectile, TerrariaProjectileRenderer.State> {
    public static class State extends EntityRenderState {
        Identifier texture;
        ProjectileKind.Orientation orientation = ProjectileKind.Orientation.VELOCITY;
        float scale = 0.5F;
        float yRot;
        float xRot;
        float spin;
        boolean fullbright;
    }

    public TerrariaProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TerrariaProjectile entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ProjectileKind kind = entity.kind();
        state.texture = kind.texture();
        state.orientation = kind.orientation();
        state.scale = kind.renderScale();
        state.yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        state.spin = (entity.age() + partialTicks) * 36.0F;
        state.fullbright = kind.fullbright();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.texture == null) {
            return;
        }
        int light = state.fullbright ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.15F, 0.0F);
        float s = state.scale;
        if (state.orientation == ProjectileKind.Orientation.VELOCITY) {
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture), (pose, buffer) -> {
                // Two crossed quads; the texture's +u axis points along the flight direction (+z).
                quad(buffer, pose, light, s, true);
                quad(buffer, pose, light, s, false);
            });
        } else {
            poseStack.mulPose(camera.orientation);
            if (state.orientation == ProjectileKind.Orientation.SPIN) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin));
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture), (pose, buffer) -> {
                vertex(buffer, pose, light, -s / 2, -s / 2, 0, 0, 1);
                vertex(buffer, pose, light, s / 2, -s / 2, 0, 1, 1);
                vertex(buffer, pose, light, s / 2, s / 2, 0, 1, 0);
                vertex(buffer, pose, light, -s / 2, s / 2, 0, 0, 0);
            });
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, float s, boolean vertical) {
        float h = s / 2;
        if (vertical) {
            vertex(buffer, pose, light, 0, -h, -h, 0, 1);
            vertex(buffer, pose, light, 0, -h, h, 1, 1);
            vertex(buffer, pose, light, 0, h, h, 1, 0);
            vertex(buffer, pose, light, 0, h, -h, 0, 0);
        } else {
            vertex(buffer, pose, light, -h, 0, -h, 0, 1);
            vertex(buffer, pose, light, -h, 0, h, 1, 1);
            vertex(buffer, pose, light, h, 0, h, 1, 0);
            vertex(buffer, pose, light, h, 0, -h, 0, 0);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
