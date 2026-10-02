package com.terracraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.terracraft.TerraCraft;
import com.terracraft.entity.SpriteEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Terraria enemies the way Terraria does: a flat, animated sprite. The sprite always faces the camera
 * (rotating around the vertical axis), mirrors to show which way the enemy moves, can tilt along its flight
 * path, flashes red when hurt and shows a Terraria-style health bar while damaged.
 */
public class TerrariaMobRenderer<T extends LivingEntity> extends EntityRenderer<T, TerrariaMobRenderer.State> {
    private static final Identifier WHITE = TerraCraft.id("textures/entity/white.png");

    public static class State extends EntityRenderState {
        MobSprites.Sprite sprite;
        float animTicks;
        float yaw;
        Vec3 look = Vec3.ZERO;
        float spin;
        boolean hurt;
        float healthFraction = 1.0F;
        float deathProgress;
        boolean healthBar = true;
    }

    public TerrariaMobRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        SpriteEntity sprite = entity instanceof SpriteEntity s ? s : null;
        String variant = sprite != null ? sprite.spriteVariant() : "";
        state.sprite = MobSprites.INSTANCE.get(variant.isEmpty() ? id : id.withSuffix("_" + variant));
        state.spin = sprite != null ? sprite.spriteSpin(partialTicks) : 0.0F;
        state.healthBar = sprite == null || sprite.showsHealthBar();
        state.animTicks = state.sprite.animateWhileMoving()
            ? entity.walkAnimation.position(partialTicks) * 4.0F
            : entity.tickCount + partialTicks;
        state.yaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        state.look = entity.getViewVector(partialTicks);
        state.hurt = entity.hurtTime > 0 || entity.deathTime > 0;
        state.healthFraction = entity.getMaxHealth() > 0 ? Mth.clamp(entity.getHealth() / entity.getMaxHealth(), 0.0F, 1.0F) : 1.0F;
        state.deathProgress = entity.deathTime > 0 ? (entity.deathTime + partialTicks) / (entity instanceof SpriteEntity ? 4.0F : 20.0F) : 0.0F;
        state.shadowRadius = entity.getBbWidth() * 0.45F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        MobSprites.Sprite sprite = state.sprite;
        if (sprite == null || state.isInvisible) {
            return;
        }
        // Size: cover the hitbox while keeping the frame's aspect ratio.
        float height = state.boundingBoxHeight * 1.15F;
        float width = height * sprite.aspect();
        if (width < state.boundingBoxWidth * 1.15F) {
            width = state.boundingBoxWidth * 1.15F;
            height = width / sprite.aspect();
        }
        float shrink = 1.0F - Math.min(state.deathProgress, 1.0F) * 0.3F;
        width *= shrink;
        height *= shrink;

        double dx = camera.pos.x - state.x;
        double dz = camera.pos.z - state.z;
        float facing = (float) Mth.atan2(dx, dz);
        // Screen-right vector of the billboard and the enemy's forward vector decide mirroring/tilt.
        double rightX = Mth.cos(facing);
        double rightZ = -Mth.sin(facing);
        float yawRad = state.yaw * Mth.DEG_TO_RAD;
        double forwardRight = -Mth.sin(yawRad) * rightX + Mth.cos(yawRad) * rightZ;
        boolean facesRight = forwardRight > 0;
        boolean mirror = sprite.facesLeft() == facesRight;

        int frame = (int) (state.animTicks / sprite.frameTime()) % sprite.frames();
        float v0 = frame / (float) sprite.frames();
        float v1 = (frame + 1) / (float) sprite.frames();
        float u0 = mirror ? 1.0F : 0.0F;
        float u1 = mirror ? 0.0F : 1.0F;
        int light = sprite.fullbright() ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(state.hurt));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotation(facing));
        float w = width / 2;
        if (sprite.rotate() || state.spin != 0.0F) {
            // Tilt toward where the enemy looks (flight path); mirroring keeps it from flying upside down.
            float tilt = 0.0F;
            if (sprite.rotate()) {
                double screenX = state.look.x * rightX + state.look.z * rightZ;
                tilt = (float) Mth.atan2(state.look.y, Math.abs(screenX));
                tilt = screenX >= 0 ? tilt : -tilt;
            }
            poseStack.translate(0.0F, height / 2, 0.0F);
            poseStack.mulPose(Axis.ZP.rotation(tilt + state.spin * Mth.DEG_TO_RAD));
            poseStack.translate(0.0F, -height / 2, 0.0F);
        }
        float h = height;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.texture()), (pose, buffer) -> {
            vertex(buffer, pose, light, overlay, -w, 0, u0, v1, -1);
            vertex(buffer, pose, light, overlay, w, 0, u1, v1, -1);
            vertex(buffer, pose, light, overlay, w, h, u1, v0, -1);
            vertex(buffer, pose, light, overlay, -w, h, u0, v0, -1);
        });
        poseStack.popPose();

        if (state.healthBar && state.healthFraction < 1.0F && state.deathProgress <= 0.0F && state.distanceToCameraSq < 32 * 32) {
            submitHealthBar(state, poseStack, collector, facing);
        }
        super.submit(state, poseStack, collector, camera);
    }

    private static void submitHealthBar(State state, PoseStack poseStack, SubmitNodeCollector collector, float facing) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotation(facing));
        poseStack.translate(0.0F, -0.22F, 0.02F);
        float half = Math.max(0.35F, state.boundingBoxWidth * 0.6F);
        float fill = -half + 2 * half * state.healthFraction;
        int color = state.healthFraction > 0.6F ? 0xFF30E030 : state.healthFraction > 0.3F ? 0xFFE8D020 : 0xFFE03020;
        int light = LightCoordsUtil.FULL_BRIGHT;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHITE), (pose, buffer) -> {
            quad(buffer, pose, light, -half - 0.03F, -0.03F, half + 0.03F, 0.11F, 0.001F, 0xFF101010);
            quad(buffer, pose, light, -half, 0.0F, fill, 0.08F, 0.002F, color);
        });
        poseStack.popPose();
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, float x0, float y0, float x1, float y1, float z, int color) {
        buffer.addVertex(pose, x0, y0, z).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        buffer.addVertex(pose, x1, y0, z).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        buffer.addVertex(pose, x1, y1, z).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        buffer.addVertex(pose, x0, y1, z).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay, float x, float y, float u, float v, int color) {
        buffer.addVertex(pose, x, y, 0.0F).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
