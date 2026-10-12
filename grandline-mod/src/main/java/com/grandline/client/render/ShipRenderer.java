package com.grandline.client.render;

import com.grandline.GrandLine;
import com.grandline.ship.ShipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Renders ships from {@link ShipMesh} plus dynamic sails (billowing with sail level) and a flag. */
public class ShipRenderer extends EntityRenderer<ShipEntity> {
    public static final ResourceLocation TEX = GrandLine.id("textures/entity/ship/ship.png");

    public ShipRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ShipEntity e) {
        return TEX;
    }

    /** Ships drawn this frame (see ShipRenderFallback). */
    public static final java.util.Set<Integer> DRAWN = new java.util.HashSet<>();

    @Override
    public void render(ShipEntity ship, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        DRAWN.add(ship.getId());
        ShipMesh mesh = ShipMesh.of(ship.cls);
        float t = ship.tickCount + partial;
        float ryaw = Mth.rotLerp(partial, ship.yRotO, ship.getYRot());
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-ryaw));
        float roll = ship.isSinking() ? Math.min(25, ship.tickCount % 1000 * 0f + 18f) : Mth.sin(t * 0.06f) * 1.6f;
        float pitch = Mth.sin(t * 0.045f + 1.3f) * 0.9f;
        ps.translate(0, Mth.sin(t * 0.08f) * 0.06f, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        PoseStack.Pose pose = ps.last();
        Matrix4f mat = pose.pose();
        int hull = 0xFF000000 | ship.hullColor(), sail = 0xFF000000 | ship.sailColor();
        for (ShipMesh.Quad q : mesh.quads) emit(vc, pose, mat, q.v(), q.tile(), q.tint() == ShipMesh.TINT_HULL ? hull : q.tint() == ShipMesh.TINT_SAIL ? sail : 0xFFFFFFFF, q.nx(), q.ny(), q.nz(), light);
        // sails: height follows sail level; billow outward along +z with the wind of the ship's speed
        var c = ship.cls;
        float level = ship.sail() / 3f;
        for (int m = 0; m < c.masts; m++) {
            float mz = ShipMesh.mastZ(c, m), h = ShipMesh.mastHeight(c, m);
            float span = c.beam * 0.75f + 0.8f;
            float yTop = c.freeboard + h - 1.2f;
            float yBot = yTop - Math.max(0.6f, (h - 3.6f) * level);
            float bulge = 0.3f + level * 1.2f + Mth.sin(t * 0.15f + m) * 0.08f;
            int cols = Math.max(2, (int) Math.ceil(span * 2));
            for (int i = 0; i < cols; i++) {
                float xa = -span + i * (2 * span / cols), xb = xa + 2 * span / cols;
                float ba = bulge * (1 - (float) Math.pow((xa) / span, 2)), bb = bulge * (1 - (float) Math.pow((xb) / span, 2));
                emit(vc, pose, mat, new float[]{xa, yBot, mz + ba * 0.6f, xb, yBot, mz + bb * 0.6f, xb, yTop, mz + bb * 0.2f, xa, yTop, mz + ba * 0.2f},
                        ShipMesh.T_SAIL, sail, 0, 0, 1, light);
            }
            if (m == c.masts - 1 || c.masts == 1) {
                // flag at the top of the last mast, fluttering
                float fy = c.freeboard + h, fz = mz;
                float wave = Mth.sin(t * 0.3f) * 0.2f;
                boolean marine = ship.shipName().startsWith("Marine");
                emit(vc, pose, mat, new float[]{0, fy - 1.2f, fz, 0, fy - 1.2f, fz - 2 + wave, 0, fy, fz - 2 - wave, 0, fy, fz},
                        marine ? ShipMesh.T_MARINE_FLAG : ShipMesh.T_FLAG, 0xFFFFFFFF, 1, 0, 0, light);
            }
        }
        ps.popPose();
        super.render(ship, yaw, partial, ps, buf, light);
    }

    private static final int TILES = 4; // atlas is 4x4 tiles of 16px (64x64)

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float[] v, int tile, int argb, float nx, float ny, float nz, int light) {
        float u0 = (tile % TILES) / (float) TILES, v0 = (tile / TILES) / (float) TILES, s = 1f / TILES;
        float[][] uv = {{u0, v0 + s}, {u0 + s, v0 + s}, {u0 + s, v0}, {u0, v0}};
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        for (int i = 0; i < 4; i++) {
            vc.addVertex(m, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(r, g, b, a).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public boolean shouldRender(ShipEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return f.isVisible(e.getBoundingBoxForCulling());
    }
}
