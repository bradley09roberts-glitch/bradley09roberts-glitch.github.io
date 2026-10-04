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
public class TerraModelRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, TerraRenderState, EntityModel<? super TerraRenderState>> {
    private static final Identifier WHITE = TerraCraft.id("textures/entity/white.png");
    private final float modelScale;
    private final boolean armsForward;
    private final Map<String, Identifier> textures = new HashMap<>();
    private static final Map<String, Identifier> tetherTextures = new HashMap<>();
    /** Length of one vine/chain segment and its width, in blocks. */
    private static final float SEGMENT = 0.5F;
    private static final float TETHER_WIDTH = 0.4F;

    public TerraModelRenderer(EntityRendererProvider.Context context, EntityModel<? super TerraRenderState> model, float modelScale, float shadow,
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
        state.tether = null;
        if (entity instanceof com.terracraft.entity.mob.TerrariaMob mob && mob.tetherId() >= 0
            && entity.level().getEntity(mob.tetherId()) instanceof net.minecraft.world.entity.Entity anchor) {
            net.minecraft.world.phys.Vec3 feet = entity.getPosition(partialTicks);
            state.tether = anchor.getPosition(partialTicks).add(0, anchor.getBbHeight() * 0.5, 0).subtract(feet);
            state.tetherStart = entity.getBbHeight() * 0.5F;
            state.tetherTexture = tetherTextures.computeIfAbsent(mob.tetherStyle(), k -> TerraCraft.id("textures/entity/tether/" + k + ".png"));
        }
    }

    /** A chained creature stays visible while any part of its tether is on screen. */
    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(T entity) {
        net.minecraft.world.phys.AABB box = super.getBoundingBoxForCulling(entity);
        if (entity instanceof com.terracraft.entity.mob.TerrariaMob mob && mob.tetherId() >= 0
            && entity.level().getEntity(mob.tetherId()) instanceof net.minecraft.world.entity.Entity anchor) {
            box = box.minmax(anchor.getBoundingBox());
        }
        return box;
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
        if (state.tether != null && state.tetherTexture != null) {
            submitTether(state, poseStack, collector);
        }
        if (state.healthBar && state.healthFraction < 1.0F && state.deathTime <= 0.0F && state.distanceToCameraSq < 32 * 32) {
            submitHealthBar(state, poseStack, collector, camera);
        }
    }

    /**
     * The tether: a row of segments from the creature's middle to its anchor, each drawn as two crossed quads along
     * the line so it looks solid from every side (Plantera's vines, Golem's chains).
     */
    private static void submitTether(TerraRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        org.joml.Vector3f start = new org.joml.Vector3f(0, state.tetherStart, 0);
        org.joml.Vector3f end = new org.joml.Vector3f((float) state.tether.x, (float) state.tether.y, (float) state.tether.z);
        org.joml.Vector3f dir = new org.joml.Vector3f(end).sub(start);
        float length = dir.length();
        if (length < 0.05F) {
            return;
        }
        dir.div(length);
        org.joml.Vector3f side = new org.joml.Vector3f(dir).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(1, 0, 0);
        }
        side.normalize(TETHER_WIDTH / 2);
        org.joml.Vector3f up = new org.joml.Vector3f(dir).cross(side).normalize(TETHER_WIDTH / 2);
        int light = state.lightCoords;
        int segments = Math.max(1, (int) Math.ceil(length / SEGMENT));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.tetherTexture), (pose, buffer) -> {
            for (int i = 0; i < segments; i++) {
                float t0 = i * SEGMENT;
                float t1 = Math.min(length, t0 + SEGMENT);
                float v1 = (t1 - t0) / SEGMENT;
                org.joml.Vector3f a = new org.joml.Vector3f(dir).mul(t0).add(start);
                org.joml.Vector3f b = new org.joml.Vector3f(dir).mul(t1).add(start);
                for (org.joml.Vector3f w : new org.joml.Vector3f[]{side, up}) {
                    tetherVertex(buffer, pose, a.x - w.x, a.y - w.y, a.z - w.z, 0, 0, light);
                    tetherVertex(buffer, pose, a.x + w.x, a.y + w.y, a.z + w.z, 1, 0, light);
                    tetherVertex(buffer, pose, b.x + w.x, b.y + w.y, b.z + w.z, 1, v1, light);
                    tetherVertex(buffer, pose, b.x - w.x, b.y - w.y, b.z - w.z, 0, v1, light);
                }
            }
        });
    }

    private static void tetherVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
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
