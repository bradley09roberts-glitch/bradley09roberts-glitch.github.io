package com.squidgame.client.game.tug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.squidgame.SquidGameMod;
import com.squidgame.entity.RopeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the tug-of-war rope. The rope is a thin textured tube from the rear-most hand of team A to the rear-most hand of team B:
 * straight at hand height along the platforms and sagging in the gap, with
 * <ul>
 *   <li>a sag that shrinks as the strain grows (a slack rope hangs a metre or more, a taut one is nearly straight);</li>
 *   <li>a vibration that grows with the strain, and a bounce after every team pulse;</li>
 *   <li>the texture sliding along the rope with the offset, so the rope visibly moves towards the winning side;</li>
 *   <li>the red flag hanging from the rope at the current offset (it flashes when the heat is decided);</li>
 *   <li>the losing end falling away into the pit when a team loses.</li>
 * </ul>
 * The offset and the strain are smoothed on the client so that the 20 Hz updates of the server do not show as steps.
 */
public class RopeRenderer extends EntityRenderer<RopeEntity> {
    private static final ResourceLocation ROPE = SquidGameMod.id("textures/entity/rope.png");
    private static final ResourceLocation FLAG = SquidGameMod.id("textures/entity/rope_flag.png");
    private static final float RADIUS = 0.045f;
    private static final float SAG_SLACK = 1.2f;
    private static final float SAG_TAUT = 0.03f;
    private static final float TEXTURE_BLOCKS = 0.6f;

    private static final class Smooth {
        float offset;
        float strain;
        long at;
        boolean initialised;
    }

    private final Map<Integer, Smooth> smooth = new HashMap<>();

    public RopeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RopeEntity entity) {
        return ROPE;
    }

    @Override
    public void render(RopeEntity rope, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Smooth sm = smooth.computeIfAbsent(rope.getId(), k -> new Smooth());
        long now = System.nanoTime();
        if (!sm.initialised) {
            sm.offset = rope.offset();
            sm.strain = rope.strain();
            sm.initialised = true;
        } else {
            float dt = Math.min(0.25f, (now - sm.at) / 1.0e9f);
            sm.offset += (rope.offset() - sm.offset) * (1f - (float) Math.exp(-dt * 10f));
            sm.strain += (rope.strain() - sm.strain) * (1f - (float) Math.exp(-dt * 5f));
        }
        sm.at = now;
        if (smooth.size() > 8) {
            smooth.keySet().removeIf(id -> rope.level().getEntity(id) == null);
        }

        float time = rope.level().getGameTime() + partialTick;
        float endA = Math.max(1f, rope.endA());
        float endB = Math.max(1f, rope.endB());
        float gap = Math.max(1f, rope.gapHalf());
        float strain = Mth.clamp(sm.strain, 0f, 1f);
        float offset = Mth.clamp(sm.offset, -1f, 1f);
        int fall = rope.fall();
        float fallAge = fall == 0 ? 0f : Math.max(0f, time - rope.fallTick()) / 20f;
        float pulseAge = time - rope.pulseTick();
        float pulse = Math.abs(rope.pulse());

        // sag of the slack rope: it settles when the heat ends
        float slack = fall == 0 ? 1f - strain : Math.min(1f, 0.35f + fallAge);
        float sag = SAG_TAUT + (SAG_SLACK - SAG_TAUT) * (float) Math.pow(slack, 2.2);
        float vibration = fall == 0 ? 0.05f * (float) Math.pow(strain, 1.4) : 0f;

        int count = Mth.clamp((int) Math.ceil((endA + endB) / 0.5f), 16, 240);
        float[] px = new float[count + 1];
        float[] py = new float[count + 1];
        float[] pz = new float[count + 1];
        for (int i = 0; i <= count; i++) {
            float x = -endA + (endA + endB) * i / count;
            float y = 0f;
            float z = 0f;
            float u = (x + gap) / (2f * gap);
            boolean inGap = u > 0f && u < 1f;
            float env = inGap ? (float) Math.sin(Math.PI * u) : 0f;
            if (inGap) {
                y -= sag * 4f * u * (1f - u);
            }
            // the vibration travels a little way onto the platforms
            float edge = Math.max(0f, 1f - Math.abs(x) / (gap + 6f));
            y += vibration * (env * 0.8f + edge * 0.2f) * (float) Math.sin(time * 0.95f + x * 0.8f);
            z += vibration * 0.6f * (env * 0.8f + edge * 0.2f) * (float) Math.cos(time * 1.35f + x * 0.6f);
            // the bounce after a team pulse
            if (pulseAge >= 0f && pulseAge < 40f) {
                float amp = 0.30f * pulse * (float) Math.exp(-pulseAge / 9f);
                y += amp * env * (float) Math.cos(pulseAge * 0.8f);
            }
            // the falling end: the losing half of the rope swings down after its team
            if (fall != 0) {
                boolean loserSide = fall == 1 ? x < 0 : x > 0;
                if (loserSide) {
                    float k = Mth.clamp(Math.abs(x) / gap, 0f, 1f);
                    k = k * k * (3f - 2f * k);
                    y -= Math.min(60f, 4.9f * fallAge * fallAge) * (0.15f + 0.85f * k);
                }
            }
            px[i] = x;
            py[i] = y;
            pz[i] = z;
        }
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), 11), Math.max(LightTexture.sky(packedLight), 4));
        drawTube(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE)), px, py, pz, light, offset * gap, endA);
        drawFlag(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(FLAG)), offset * gap, px, py, pz, time, strain, fall, fallAge, packedLight);
        super.render(rope, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** A four sided tube along the polyline; the texture runs along the rope and slides with the offset. */
    private static void drawTube(PoseStack poseStack, VertexConsumer vc, float[] px, float[] py, float[] pz, int light, float slide, float endA) {
        PoseStack.Pose pose = poseStack.last();
        int n = px.length;
        float[] n1x = new float[n], n1y = new float[n], n1z = new float[n];
        float[] n2x = new float[n], n2y = new float[n], n2z = new float[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float tx = px[b] - px[a], ty = py[b] - py[a], tz = pz[b] - pz[a];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            // n1 = normalize(t x up) (horizontal across the rope), n2 = n1 x t (about up)
            float cx = -tz, cy = 0f, cz = tx;
            float cl = (float) Math.sqrt(cx * cx + cz * cz);
            if (cl < 1e-4f) {
                cx = 1f;
                cz = 0f;
                cl = 1f;
            }
            cx /= cl;
            cz /= cl;
            n1x[i] = cx;
            n1y[i] = cy;
            n1z[i] = cz;
            n2x[i] = cy * tz - cz * ty;
            n2y[i] = cz * tx - cx * tz;
            n2z[i] = cx * ty - cy * tx;
        }
        final float[][] corner = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        for (int i = 0; i + 1 < n; i++) {
            float v0 = (px[i] + endA - slide) / TEXTURE_BLOCKS;
            float v1 = (px[i + 1] + endA - slide) / TEXTURE_BLOCKS;
            for (int c = 0; c < 4; c++) {
                int d = (c + 1) & 3;
                float[] ca = corner[c], cd = corner[d];
                float nx = (ca[0] + cd[0]) * 0.5f, ny = (ca[1] + cd[1]) * 0.5f;
                // face normal at the first ring (good enough for the shading of a thin rope)
                float fnx = n1x[i] * nx + n2x[i] * ny, fny = n1y[i] * nx + n2y[i] * ny, fnz = n1z[i] * nx + n2z[i] * ny;
                float fl = (float) Math.sqrt(fnx * fnx + fny * fny + fnz * fnz);
                fnx /= fl;
                fny /= fl;
                fnz /= fl;
                vertex(vc, pose, px[i], py[i], pz[i], n1x[i], n1y[i], n1z[i], n2x[i], n2y[i], n2z[i], ca, 0f, v0, light, fnx, fny, fnz);
                vertex(vc, pose, px[i], py[i], pz[i], n1x[i], n1y[i], n1z[i], n2x[i], n2y[i], n2z[i], cd, 1f, v0, light, fnx, fny, fnz);
                vertex(vc, pose, px[i + 1], py[i + 1], pz[i + 1], n1x[i + 1], n1y[i + 1], n1z[i + 1], n2x[i + 1], n2y[i + 1], n2z[i + 1], cd, 1f, v1, light, fnx, fny, fnz);
                vertex(vc, pose, px[i + 1], py[i + 1], pz[i + 1], n1x[i + 1], n1y[i + 1], n1z[i + 1], n2x[i + 1], n2y[i + 1], n2z[i + 1], ca, 0f, v1, light, fnx, fny, fnz);
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float n1x, float n1y, float n1z, float n2x,
                               float n2y, float n2z, float[] corner, float u, float v, int light, float nx, float ny, float nz) {
        float ox = (n1x * corner[0] + n2x * corner[1]) * RADIUS;
        float oy = (n1y * corner[0] + n2y * corner[1]) * RADIUS;
        float oz = (n1z * corner[0] + n2z * corner[1]) * RADIUS;
        vc.addVertex(pose, x + ox, y + oy, z + oz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** The flag: two crossed double-sided ribbons hanging from the rope at the offset. */
    private static void drawFlag(PoseStack poseStack, VertexConsumer vc, float flagX, float[] px, float[] py, float[] pz, float time,
                                 float strain, int fall, float fallAge, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        // the height and sideways position of the rope under the flag
        int n = px.length;
        int i = 0;
        while (i + 2 < n && px[i + 1] < flagX) {
            i++;
        }
        float span = px[Math.min(n - 1, i + 1)] - px[i];
        float f = span <= 1e-4f ? 0f : Mth.clamp((flagX - px[i]) / span, 0f, 1f);
        int j = Math.min(n - 1, i + 1);
        float fy = Mth.lerp(f, py[i], py[j]);
        float fz = Mth.lerp(f, pz[i], pz[j]);
        boolean flash = fall != 0 && fallAge < 3.5f && ((int) (fallAge * 8f)) % 2 == 0;
        int light = flash ? LightTexture.FULL_BRIGHT : LightTexture.pack(Math.max(LightTexture.block(packedLight), 13), Math.max(LightTexture.sky(packedLight), 6));
        float flutter = 0.05f + 0.10f * strain;
        float w = 0.45f;
        float hgt = 0.8f;
        float top = fy - RADIUS;
        float bot = top - hgt;
        float sway1 = flutter * (float) Math.sin(time * 0.5f);
        float sway2 = flutter * (float) Math.sin(time * 0.5f + 1.7f);
        // ribbon across the rope (visible from the platforms)
        quad(vc, pose, flagX, top, fz - w, flagX, top, fz + w, flagX + sway2, bot, fz + w, flagX + sway1, bot, fz - w, light);
        // ribbon along the rope (visible from the sides)
        quad(vc, pose, flagX - w, top, fz, flagX + w, top, fz, flagX + w + sway1, bot, fz + sway2, flagX - w + sway2, bot, fz + sway1, light);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2,
                             float y2, float z2, float x3, float y3, float z3, int light) {
        float[][] p = {{x0, y0, z0, 0, 0}, {x1, y1, z1, 1, 0}, {x2, y2, z2, 1, 1}, {x3, y3, z3, 0, 1}};
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 4; k++) {
                float[] v = p[side == 0 ? k : 3 - k];
                vc.addVertex(pose, v[0], v[1], v[2]).setColor(255, 255, 255, 255).setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(pose, 0f, 1f, 0f);
            }
        }
    }
}
