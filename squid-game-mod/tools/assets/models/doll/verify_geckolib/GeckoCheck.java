import com.google.gson.JsonObject;
import net.minecraft.class_4587;   // com.mojang.blaze3d.vertex.PoseStack (intermediary name)
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.animation.keyframe.KeyframeStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads the doll through the REAL GeckoLib 4.9.3 loader (geo + animation json) and computes the transformed quad
 * vertices with the REAL RenderUtil / GeoRenderer matrix order, then dumps them as text so verify_geckolib.py can
 * compare them numerically with the python port used by the preview renderer.
 *
 * usage: java GeckoCheck <doll.geo.json> <doll.animation.json> <out.txt>
 */
public class GeckoCheck {
    static AnimationPoint pointAt(List<Keyframe<MathValue>> frames, double tick) {
        // AnimationController.getCurrentKeyFrameLocation + getAnimationPointAtTick (private in GeckoLib)
        double total = 0, start = tick;
        Keyframe<MathValue> cur = null;
        for (Keyframe<MathValue> f : frames) {
            total += f.length();
            if (total > tick) {
                cur = f;
                start = tick - (total - f.length());
                break;
            }
        }
        if (cur == null) {
            cur = frames.get(frames.size() - 1);
            start = tick;
        }
        return new AnimationPoint(cur, start, cur.length(), cur.startValue().get(), cur.endValue().get());
    }

    static double val(KeyframeStack<Keyframe<MathValue>> st, int axis, double tick) {
        List<Keyframe<MathValue>> l = axis == 0 ? st.xKeyframes() : axis == 1 ? st.yKeyframes() : st.zKeyframes();
        return EasingType.lerpWithOverride(pointAt(l, tick), null);
    }

    static void renderRec(class_4587 ps, GeoBone bone, Set<String> hidden, StringBuilder out) {
        ps.method_22903();                                   // pushPose
        RenderUtil.prepMatrixForBone(ps, bone);              // translate, pivot, rotateZYX, scale, -pivot
        if (!hidden.contains(bone.getName())) {
            int ci = 0;
            for (GeoCube cube : bone.getCubes()) {
                ps.method_22903();
                RenderUtil.translateToPivotPoint(ps, cube);
                RenderUtil.rotateMatrixAroundCube(ps, cube);
                RenderUtil.translateAwayFromPivotPoint(ps, cube);
                Matrix4f pose = new Matrix4f(ps.method_23760().method_23761());
                for (GeoQuad q : cube.quads()) {
                    if (q == null) continue;
                    out.append(bone.getName()).append('|').append(ci).append('|').append(q.direction().method_15434());
                    for (GeoVertex v : q.vertices()) {
                        Vector4f p = pose.transform(new Vector4f(v.position().x(), v.position().y(), v.position().z(), 1f));
                        out.append(String.format(Locale.ROOT, "|%.6f,%.6f,%.6f,%.6f,%.6f", p.x(), p.y(), p.z(), v.texU(), v.texV()));
                    }
                    out.append('\n');
                }
                ps.method_22909();                           // popPose
                ci++;
            }
        }
        if (!bone.isHidingChildren() && !hidden.contains(bone.getName())) {
            for (GeoBone ch : bone.getChildBones()) renderRec(ps, ch, hidden, out);
        }
        ps.method_22909();
    }

    static void collect(GeoBone b, Map<String, GeoBone> m) {
        m.put(b.getName(), b);
        for (GeoBone c : b.getChildBones()) collect(c, m);
    }

    public static void main(String[] a) throws Exception {
        var gson = KeyFramesAdapter.GEO_GSON;
        JsonObject geoRoot = gson.fromJson(Files.readString(Path.of(a[0])), JsonObject.class);
        Model model = gson.fromJson(geoRoot, Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        JsonObject animRoot = gson.fromJson(Files.readString(Path.of(a[1])), JsonObject.class);
        BakedAnimations anims = gson.fromJson(animRoot.getAsJsonObject("animations"), BakedAnimations.class);

        Map<String, GeoBone> bones = new LinkedHashMap<>();
        for (GeoBone b : baked.topLevelBones()) collect(b, bones);
        for (GeoBone b : bones.values()) b.saveInitialSnapshot();
        StringBuilder out = new StringBuilder();
        out.append("# bones ").append(bones.size()).append('\n');
        out.append("# animations ").append(anims.animations().size()).append('\n');
        for (var e : anims.animations().entrySet()) {
            Animation an = e.getValue();
            out.append("# anim ").append(e.getKey()).append(" ticks=").append(an.length()).append(" loop=").append(an.loopType().getId()).append('\n');
        }
        List<String> names = new ArrayList<>();
        names.add("");
        names.addAll(anims.animations().keySet());
        for (String name : names) {
            Animation an = name.isEmpty() ? null : anims.getAnimation(name);
            double[] fracs = an == null ? new double[]{0} : new double[]{0, 0.113, 0.37, 0.61, 0.92, 1.0};
            for (double frac : fracs) {
                for (GeoBone b : bones.values()) {            // reset to the bind pose
                    b.updateRotation(b.getInitialSnapshot().getRotX(), b.getInitialSnapshot().getRotY(), b.getInitialSnapshot().getRotZ());
                    b.updatePosition(0, 0, 0);
                    b.updateScale(1, 1, 1);
                    b.setHidden(false);
                }
                double tick = 0;
                if (an != null) {
                    tick = frac >= 1.0 ? an.length() - 1e-9 : frac * an.length();
                    for (BoneAnimation ba : an.boneAnimations()) {
                        GeoBone b = bones.get(ba.boneName());
                        if (b == null) {
                            out.append("# MISSING bone ").append(ba.boneName()).append('\n');
                            continue;
                        }
                        if (!ba.rotationKeyFrames().xKeyframes().isEmpty()) {          // AnimationProcessor: value + initial snapshot
                            b.setRotX((float) val(ba.rotationKeyFrames(), 0, tick) + b.getInitialSnapshot().getRotX());
                            b.setRotY((float) val(ba.rotationKeyFrames(), 1, tick) + b.getInitialSnapshot().getRotY());
                            b.setRotZ((float) val(ba.rotationKeyFrames(), 2, tick) + b.getInitialSnapshot().getRotZ());
                        }
                        if (!ba.positionKeyFrames().xKeyframes().isEmpty()) {
                            b.setPosX((float) val(ba.positionKeyFrames(), 0, tick));
                            b.setPosY((float) val(ba.positionKeyFrames(), 1, tick));
                            b.setPosZ((float) val(ba.positionKeyFrames(), 2, tick));
                        }
                        if (!ba.scaleKeyFrames().xKeyframes().isEmpty()) {
                            b.setScaleX((float) val(ba.scaleKeyFrames(), 0, tick));
                            b.setScaleY((float) val(ba.scaleKeyFrames(), 1, tick));
                            b.setScaleZ((float) val(ba.scaleKeyFrames(), 2, tick));
                        }
                    }
                }
                Set<String> hidden = new HashSet<>(List.of("eyes_on"));       // as DollRenderer does while the eyes are off
                out.append("@pose ").append(name.isEmpty() ? "bind" : name).append(' ').append(String.format(Locale.ROOT, "%.6f", tick)).append('\n');
                class_4587 ps = new class_4587();
                for (GeoBone top : baked.topLevelBones()) renderRec(ps, top, hidden, out);
            }
        }
        Files.writeString(Path.of(a[2]), out.toString());
        System.out.println("GeckoCheck ok: " + bones.size() + " bones, " + anims.animations().size() + " animations");
    }
}
