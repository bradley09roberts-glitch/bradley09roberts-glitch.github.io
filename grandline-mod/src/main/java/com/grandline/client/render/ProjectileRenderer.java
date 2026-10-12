package com.grandline.client.render;

import com.grandline.GrandLine;
import com.grandline.entity.AbilityProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Camera-facing glowing billboard per projectile kind (textures/entity/projectile/<kind>.png). */
public class ProjectileRenderer extends EntityRenderer<AbilityProjectile> {
    private static final Set<String> KINDS = Set.of("fire_fist", "fire_bullet", "flame_emperor", "ice_spear", "lightning_bolt", "water_shot", "wind_blade", "tempest",
            "air_blade", "air_blade_big", "crescent", "haki_wave", "cannonball", "bullet", "pellet", "flying_fist", "fire_bird", "knife", "arrow", "spear", "weather_ball", "impact_wave");
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();

    public ProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(AbilityProjectile e) {
        String k = KINDS.contains(e.kind()) ? e.kind() : "energy";
        return TEX.computeIfAbsent(k, kk -> GrandLine.id("textures/entity/projectile/" + kk + ".png"));
    }

    @Override
    public void render(AbilityProjectile e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        float s = Math.max(0.2f, e.size()) * 1.6f;
        ps.pushPose();
        ps.translate(0, e.getBbHeight() / 2, 0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partial) * 20f));
        ps.scale(s, s, s);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(e)));
        Matrix4f m = ps.last().pose();
        int full = 0xF000F0;
        vert(vc, m, ps, -0.5f, -0.5f, 0, 1, full);
        vert(vc, m, ps, 0.5f, -0.5f, 1, 1, full);
        vert(vc, m, ps, 0.5f, 0.5f, 1, 0, full);
        vert(vc, m, ps, -0.5f, 0.5f, 0, 0, full);
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, PoseStack ps, float x, float y, float u, float v, int light) {
        vc.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ps.last(), 0, 1, 0);
    }
}
