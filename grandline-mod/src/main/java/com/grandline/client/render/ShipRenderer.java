package com.grandline.client.render;

import com.grandline.GrandLine;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.ShipLook;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders ships from {@link ShipMesh} plus the per-ship parts: carved figurehead and emblem flag/sail
 * ({@link ShipDecor}), sails billowing with sail level (torn by chain shot), and grapple/tow lines to a linked ship.
 */
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
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        lines(ship, partial, ryaw, ps, vc, light);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-ryaw));
        float roll = ship.isSinking() ? 18f : Mth.sin(t * 0.06f) * 1.6f;
        float pitch = Mth.sin(t * 0.045f + 1.3f) * 0.9f;
        ps.translate(0, Mth.sin(t * 0.08f) * 0.06f, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        PoseStack.Pose pose = ps.last();
        Matrix4f mat = pose.pose();
        int hull = 0xFF000000 | ship.hullColor(), sail = 0xFF000000 | ship.sailColor();
        for (ShipMesh.Quad q : mesh.quads) emit(vc, pose, mat, q.v(), q.tile(), q.tint() == ShipMesh.TINT_HULL ? hull : q.tint() == ShipMesh.TINT_SAIL ? sail : 0xFFFFFFFF, q.nx(), q.ny(), q.nz(), light);
        ShipLook look = ship.look();
        for (ShipDecor.Quad q : ShipDecor.figurehead(look.figurehead(), ship.cls)) emit(vc, pose, mat, q.v(), q.tile(), q.argb(), q.nx(), q.ny(), q.nz(), light);
        sails(ship, look, t, vc, pose, mat, sail, light);
        ps.popPose();
        super.render(ship, yaw, partial, ps, buf, light);
    }

    // ------------------------------------------------------------------ sails, emblem and flag

    private static void sails(ShipEntity ship, ShipLook look, float t, VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int sail, int light) {
        var c = ship.cls;
        float level = ship.sail() / 3f;
        float torn = Mth.clamp((0.75f - ship.rigging()) / 0.75f, 0, 1);
        int emblemMast = c.masts / 2, foreMast = c.masts - 1; // the main sail, and the foresail seen from ahead
        String[] bmp = ShipDecor.emblem(look.emblem());
        List<int[]> runs = ShipDecor.runs(bmp);
        for (int m = 0; m < c.masts; m++) {
            float mz = ShipMesh.mastZ(c, m), h = ShipMesh.mastHeight(c, m);
            float span = c.beam * 0.75f + 0.8f;
            float yTop = c.freeboard + h - 1.2f;
            float yBot = yTop - Math.max(0.6f, (h - 3.6f) * level);
            float bulge = 0.3f + level * 1.2f + Mth.sin(t * 0.15f + m) * 0.08f;
            int cols = Math.max(2, (int) Math.ceil(span * 2));
            for (int i = 0; i < cols; i++) {
                if (torn > 0 && Math.floorMod(i * 37 + m * 17 + 5, 100) < torn * 38) continue; // chain shot tore this strip away
                float xa = -span + i * (2 * span / cols), xb = xa + 2 * span / cols;
                float ba = bulge * (1 - (float) Math.pow((xa) / span, 2)), bb = bulge * (1 - (float) Math.pow((xb) / span, 2));
                emit(vc, pose, mat, new float[]{xa, yBot, mz + ba * 0.6f, xb, yBot, mz + bb * 0.6f, xb, yTop, mz + bb * 0.2f, xa, yTop, mz + ba * 0.2f},
                        ShipMesh.T_SAIL, sail, 0, 0, 1, light);
            }
            if ((m == emblemMast || m == foreMast) && yTop - yBot > 1.6f) {
                float eh = Math.min((yTop - yBot) * 0.72f, span * 1.3f), ew = eh * bmp[0].length() / bmp.length;
                float y0 = (yTop + yBot) / 2 - eh / 2;
                int col = 0xFF000000 | look.emblemColor();
                for (int[] r : runs) {
                    float xa = -ew / 2 + ew * r[1] / bmp[0].length(), xb = -ew / 2 + ew * r[2] / bmp[0].length();
                    float yb = y0 + eh * (bmp.length - r[0]) / bmp.length, ya = yb - eh / bmp.length;
                    for (int side = -1; side <= 1; side += 2) {
                        float off = side * 0.07f;
                        emit(vc, pose, mat, new float[]{xa, ya, sailZ(mz, bulge, span, xa, ya, yBot, yTop) + off, xb, ya, sailZ(mz, bulge, span, xb, ya, yBot, yTop) + off,
                                xb, yb, sailZ(mz, bulge, span, xb, yb, yBot, yTop) + off, xa, yb, sailZ(mz, bulge, span, xa, yb, yBot, yTop) + off}, ShipDecor.T_PAINT, col, 0, 0, side, light);
                    }
                }
            }
            if (m == c.masts - 1 || c.masts == 1) flag(look, c.freeboard + h, mz, t, runs, bmp, vc, pose, mat, light);
        }
    }

    private static float sailZ(float mz, float bulge, float span, float x, float y, float yBot, float yTop) {
        float k = 0.6f + (0.2f - 0.6f) * (y - yBot) / Math.max(0.01f, yTop - yBot);
        return mz + bulge * (1 - (x / span) * (x / span)) * k;
    }

    /** Flag at a masthead: field in the flag colour, emblem on both faces, fluttering. */
    private static void flag(ShipLook look, float fy, float fz, float t, List<int[]> runs, String[] bmp, VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light) {
        float wave = Mth.sin(t * 0.3f) * 0.2f;
        float len = 2.0f, hgt = 1.2f;
        emit(vc, pose, mat, flagPts(0, 0, 1, 1, 0, fy, fz, len, hgt, wave), ShipDecor.T_PAINT, 0xFF000000 | look.flagColor(), 1, 0, 0, light);
        float eh = 1.0f, ew = eh * bmp[0].length() / bmp.length;
        float u0 = 0.5f - ew / len / 2, du = ew / len, v0 = (hgt - eh) / 2 / hgt, dv = eh / hgt;
        int col = 0xFF000000 | look.emblemColor();
        for (int[] r : runs) {
            float ua = u0 + du * r[1] / bmp[0].length(), ub = u0 + du * r[2] / bmp[0].length();
            float vb = v0 + dv * (bmp.length - r[0]) / bmp.length, va = vb - dv / bmp.length;
            for (int side = -1; side <= 1; side += 2)
                emit(vc, pose, mat, flagPts(ua, va, ub, vb, side * 0.03f, fy, fz, len, hgt, wave), ShipDecor.T_PAINT, col, side, 0, 0, light);
        }
    }

    /** Corners of the sub-rectangle (u0,v0)-(u1,v1) of the waving flag (u from the mast outward, v upward). */
    private static float[] flagPts(float u0, float v0, float u1, float v1, float x, float fy, float fz, float len, float hgt, float wave) {
        float[] out = new float[12];
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            float u = uv[i][0], v = uv[i][1];
            out[i * 3] = x;
            out[i * 3 + 1] = fy - hgt + hgt * v;
            out[i * 3 + 2] = fz - len * u + wave * u * (1 - 2 * v);
        }
        return out;
    }

    // ------------------------------------------------------------------ grapple / tow lines

    private static Vec3 rot(double lx, double ly, double lz, float yawDeg) {
        double yaw = Math.toRadians(yawDeg), c = Math.cos(yaw), s = Math.sin(yaw);
        return new Vec3(lx * c - lz * s, ly, lx * s + lz * c);
    }

    /** Lines are drawn by one ship of each pair, in world-aligned space relative to this ship's rendered position. */
    private static void lines(ShipEntity ship, float partial, float ryaw, PoseStack ps, VertexConsumer vc, int light) {
        int id = ship.linkedId();
        if (id < 0 || !(ship.level().getEntity(id) instanceof ShipEntity o) || o.linkedId() != ship.getId()) return;
        if (o.towing()) return;                                  // the tug draws the tow line
        if (!ship.towing() && ship.getId() > o.getId()) return; // grapples: drawn once per pair
        Vec3 me = new Vec3(Mth.lerp(partial, ship.xOld, ship.getX()), Mth.lerp(partial, ship.yOld, ship.getY()), Mth.lerp(partial, ship.zOld, ship.getZ()));
        Vec3 other = new Vec3(Mth.lerp(partial, o.xOld, o.getX()), Mth.lerp(partial, o.yOld, o.getY()), Mth.lerp(partial, o.zOld, o.getZ())).subtract(me);
        float oyaw = Mth.rotLerp(partial, o.yRotO, o.getYRot());
        PoseStack.Pose pose = ps.last();
        Matrix4f mat = pose.pose();
        if (ship.towing()) {
            Vec3 a = rot(0, ship.cls.freeboard + 0.6, -ship.cls.length / 2.0, ryaw);
            Vec3 b = other.add(rot(0, o.cls.freeboard + 0.5, o.cls.length / 2.0, oyaw));
            rope(vc, pose, mat, a, b, 0.09f, 0.6f, light);
            return;
        }
        Vec3 rel = ship.toLocal(o.position());
        int side = rel.x >= 0 ? 1 : -1;
        Vec3 relO = o.toLocal(ship.position());
        int sideO = relO.x >= 0 ? 1 : -1;
        float hb = ship.cls.halfBeamAt(0.5f), hbO = o.cls.halfBeamAt(0.5f);
        double gap = Math.abs(rel.x) - hb - hbO;
        for (int i = -1; i <= 1; i++) {
            double lz = i * ship.cls.length * 0.22;
            Vec3 a = rot(side * hb, ship.cls.freeboard + 0.8, lz, ryaw);
            Vec3 aw = ship.toWorld(side * hb, ship.cls.freeboard + 0.8, lz);
            double lzO = Mth.clamp(o.toLocal(aw).z, -o.cls.length * 0.4, o.cls.length * 0.4);
            Vec3 b = other.add(rot(sideO * hbO, o.cls.freeboard + 0.8, lzO, oyaw));
            rope(vc, pose, mat, a, b, 0.09f, 0.25f, light);
            if (i == 0 && gap < 3.5) plank(vc, pose, mat, rot(side * (hb - 0.6), ship.cls.freeboard + 0.15, 0, ryaw), other.add(rot(sideO * (hbO - 0.6), o.cls.freeboard + 0.15, lzO, oyaw)), light);
        }
    }

    private static void rope(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, Vec3 a, Vec3 b, float w, float sagPerTen, int light) {
        int n = 10;
        double sag = sagPerTen * a.distanceTo(b) / 10.0;
        Vec3 d = b.subtract(a);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize().scale(w);
        Vec3 up = new Vec3(0, w, 0);
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            Vec3 p = a.add(d.scale(f)).subtract(0, Math.sin(f * Math.PI) * sag, 0);
            quad(vc, pose, m, prev.subtract(up), p.subtract(up), p.add(up), prev.add(up), ShipMesh.T_ROPE, 0xFFFFFFFF, light);
            quad(vc, pose, m, prev.subtract(side), p.subtract(side), p.add(side), prev.add(side), ShipMesh.T_ROPE, 0xFFFFFFFF, light);
            prev = p;
        }
    }

    /** A boarding plank between the rails (deck planks, a little thickness). */
    private static void plank(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, Vec3 a, Vec3 b, int light) {
        Vec3 d = b.subtract(a);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        if (side.lengthSqr() < 1e-6) return;
        side = side.normalize().scale(0.45);
        Vec3 th = new Vec3(0, -0.12, 0);
        quad(vc, pose, m, a.subtract(side), b.subtract(side), b.add(side), a.add(side), ShipMesh.T_DECK, 0xFFFFFFFF, light);
        quad(vc, pose, m, a.add(side).add(th), b.add(side).add(th), b.subtract(side).add(th), a.subtract(side).add(th), ShipMesh.T_DECK, 0xFFB0B0B0, light);
        quad(vc, pose, m, a.add(side), b.add(side), b.add(side).add(th), a.add(side).add(th), ShipMesh.T_RAIL, 0xFFFFFFFF, light);
        quad(vc, pose, m, a.subtract(side).add(th), b.subtract(side).add(th), b.subtract(side), a.subtract(side), ShipMesh.T_RAIL, 0xFFFFFFFF, light);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int tile, int argb, int light) {
        emit(vc, pose, m, new float[]{(float) p0.x, (float) p0.y, (float) p0.z, (float) p1.x, (float) p1.y, (float) p1.z, (float) p2.x, (float) p2.y, (float) p2.z,
                (float) p3.x, (float) p3.y, (float) p3.z}, tile, argb, 0, 1, 0, light);
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
