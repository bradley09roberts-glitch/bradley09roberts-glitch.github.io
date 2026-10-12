package com.grandline.client.render.creature;

import com.grandline.GrandLine;
import com.grandline.creature.SeaKing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Sea King model. The head plays keyframed animations (jaw, fins); the body segments are top-level bones that are
 * placed every frame along the creature's swimming path (the same path its hitbox parts follow), with a travelling
 * side-to-side ripple while it swims and a belly-up roll when it faints or dies.
 */
public class SeaKingModel extends GeoModel<SeaKing> {
    private static final ResourceLocation[] GEO = new ResourceLocation[SeaKing.VARIANTS.length];
    private static final ResourceLocation[] TEX = new ResourceLocation[SeaKing.VARIANTS.length];
    private static final ResourceLocation ANIM = GrandLine.id("animations/sea_king/sea_king.anim.json");

    static {
        for (int i = 0; i < SeaKing.VARIANTS.length; i++) {
            GEO[i] = GrandLine.id("geo/sea_king/" + SeaKing.VARIANTS[i] + ".geo.json");
            TEX[i] = GrandLine.id("textures/entity/sea_king/" + SeaKing.VARIANTS[i] + ".png");
        }
    }

    private final Vec3[] m = new Vec3[SeaKing.SEGMENTS];

    @Override
    public ResourceLocation getModelResource(SeaKing sk) {
        return GEO[Math.floorMod(sk.variant(), GEO.length)];
    }

    @Override
    public ResourceLocation getTextureResource(SeaKing sk) {
        return TEX[Math.floorMod(sk.variant(), TEX.length)];
    }

    @Override
    public ResourceLocation getAnimationResource(SeaKing sk) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(SeaKing sk, long instanceId, AnimationState<SeaKing> state) {
        float pt = state.getPartialTick();
        float s = Math.max(0.1f, sk.getScale());
        double ex = Mth.lerp(pt, sk.xo, sk.getX()), ey = Mth.lerp(pt, sk.yo, sk.getY()), ez = Mth.lerp(pt, sk.zo, sk.getZ());
        float yaw = Mth.rotLerp(pt, sk.yBodyRotO, sk.yBodyRot);
        double th = Math.toRadians(180 - yaw), c = Math.cos(th), sn = Math.sin(th);
        int n = SeaKing.SEGMENTS;
        for (int k = 0; k < n; k++) {
            Vec3 w = sk.segment(k, pt);
            double ox = w.x - ex, oy = w.y - ey, oz = w.z - ez;
            m[k] = new Vec3((ox * c - oz * sn) / s, oy / s, (ox * sn + oz * c) / s);
        }
        byte st = sk.state();
        boolean dead = sk.isDeadOrDying();
        float roll = 0;
        if (dead) roll = (float) Math.PI * Math.min(1f, (sk.deathTime + pt) / 16f);
        else if (st == SeaKing.FAINT) roll = (float) Math.PI * Math.min(1f, sk.stateAge(pt) / 16f);
        // travelling ripple while swimming (visual only)
        if (!dead && (st == SeaKing.SWIM || st == SeaKing.FLEE || st == SeaKing.SLAM)) {
            float time = sk.tickCount + pt;
            for (int k = 0; k < n; k++) {
                Vec3 a = k == 0 ? new Vec3(0, SeaKing.HEAD_H / 2, 0) : m[k - 1];
                Vec3 b = k == n - 1 ? m[k] : m[k + 1];
                double dx = a.x - b.x, dz = a.z - b.z, len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1e-4) continue;
                double amp = (0.08 + 0.32 * k / (n - 1.0)) * (st == SeaKing.SLAM && k >= n - SeaKing.SLAM_SEGS ? 0.2 : 1);
                double off = Math.sin(time * 0.21 - k * 0.55) * amp;
                m[k] = m[k].add(dz / len * off, 0, -dx / len * off);
            }
        }
        Vec3 headC = new Vec3(0, SeaKing.HEAD_H / 2, 0);
        for (int k = 0; k < n; k++) {
            GeoBone bone = getAnimationProcessor().getBone("seg" + k);
            if (bone == null) continue;
            Vec3 p = m[k];
            bone.setPosX((float) (-p.x * 16));
            bone.setPosY((float) (p.y * 16));
            bone.setPosZ((float) (p.z * 16));
            Vec3 front = k == 0 ? headC : m[k - 1];
            Vec3 back = k == n - 1 ? p : m[k + 1];
            Vec3 d = front.subtract(back);
            if (d.lengthSqr() < 1e-8) d = new Vec3(0, 0, -1);
            d = d.normalize();
            bone.setRotX((float) Math.asin(Mth.clamp(d.y, -1, 1)));
            bone.setRotY((float) Math.atan2(-d.x, -d.z));
            bone.setRotZ(0);
            GeoBone r = getAnimationProcessor().getBone("seg" + k + "_roll");
            if (r != null) r.setRotZ(roll);
        }
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head != null) {
            float pitch = Mth.lerp(pt, sk.xRotO, sk.getXRot());
            head.setRotX((float) Math.toRadians(-pitch));
            head.setRotY(0);
        }
        GeoBone hr = getAnimationProcessor().getBone("head_roll");
        if (hr != null) hr.setRotZ(roll);
    }
}
