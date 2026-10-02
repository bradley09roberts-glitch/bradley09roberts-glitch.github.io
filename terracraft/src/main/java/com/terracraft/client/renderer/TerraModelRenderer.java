package com.terracraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.terracraft.TerraCraft;
import com.terracraft.client.model.TerraRenderState;
import com.terracraft.entity.SpriteEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders TerraCraft creatures as 3D models: one model per family (slime, humanoid, eye, bat), a texture per
 * entity ({@code textures/entity/model/<id>.png}, alternate forms {@code <id>_<variant>.png}), a scale that
 * fits the model to the hitbox, and a Terraria-style health bar while damaged.
 */
public class TerraModelRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, TerraRenderState, EntityModel<TerraRenderState>> {
    private static final Identifier WHITE = TerraCraft.id("textures/entity/white.png");
    private final float modelScale;
    private final boolean armsForward;
    private final Map<String, Identifier> textures = new HashMap<>();

    public TerraModelRenderer(EntityRendererProvider.Context context, EntityModel<TerraRenderState> model, float modelScale, float shadow,
                              boolean armsForward) {
        super(context, model, shadow);
        this.modelScale = modelScale;
        this.armsForward = armsForward;
    }

    @Override
    public TerraRenderState createRenderState() {
        return new TerraRenderState();
    }

    @Override
    public void extractRenderState(T entity, TerraRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, itemModelResolver);
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String variant = entity instanceof SpriteEntity sprite ? sprite.spriteVariant() : "";
        String key = variant.isEmpty() ? id.getPath() : id.getPath() + "_" + variant;
        state.variant = key;
        textures.computeIfAbsent(key, k -> Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/entity/model/" + k + ".png"));
        state.airborne = !entity.onGround() && !entity.isNoGravity();
        state.verticalSpeed = (float) entity.getDeltaMovement().y;
        state.spin = entity instanceof SpriteEntity sprite ? sprite.spriteSpin(partialTicks) : 0.0F;
        state.armsForward = armsForward;
        state.aggressive = entity instanceof Mob mob && mob.isAggressive();
        state.healthFraction = entity.getMaxHealth() > 0 ? Mth.clamp(entity.getHealth() / entity.getMaxHealth(), 0.0F, 1.0F) : 1.0F;
        state.healthBar = !(entity instanceof SpriteEntity sprite) || sprite.showsHealthBar();
    }

    @Override
    public Identifier getTextureLocation(TerraRenderState state) {
        return textures.getOrDefault(state.variant, WHITE);
    }

    /** Only named creatures (town NPCs) show a name tag, like Terraria's hover names. */
    @Override
    protected boolean shouldShowName(T entity, double distanceToCameraSq) {
        return entity.hasCustomName() && super.shouldShowName(entity, distanceToCameraSq);
    }

    @Override
    protected void scale(TerraRenderState state, PoseStack poseStack) {
        poseStack.scale(modelScale, modelScale, modelScale);
    }

    @Override
    public void submit(TerraRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state.healthBar && state.healthFraction < 1.0F && state.deathTime <= 0.0F && state.distanceToCameraSq < 32 * 32) {
            submitHealthBar(state, poseStack, collector, camera);
        }
    }

    private static void submitHealthBar(TerraRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        double dx = camera.pos.x - state.x;
        double dz = camera.pos.z - state.z;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(dx, dz)));
        poseStack.translate(0.0F, -0.22F, 0.0F);
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
}
