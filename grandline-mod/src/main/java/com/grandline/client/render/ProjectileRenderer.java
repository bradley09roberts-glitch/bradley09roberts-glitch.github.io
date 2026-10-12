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
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();

    public ProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(AbilityProjectile e) {
        // each kind has its own sprite (tools/assetgen/fx*.py); an unknown kind falls back to the generic energy orb
        return TEX.computeIfAbsent(e.kind(), k -> {
            ResourceLocation rl = GrandLine.id("textures/entity/projectile/" + k + ".png");
            return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(rl).isPresent() ? rl : GrandLine.id("textures/entity/projectile/energy.png");
        });
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
