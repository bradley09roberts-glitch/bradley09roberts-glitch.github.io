package com.grandline.client.anim;

import com.grandline.client.ClientState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Plays humanoid animations on players and NPCs by posing vanilla model parts after vanilla's own setupAnim,
 * so armour, held items and skin overlays follow automatically. One-shot animations blend in and out; looping
 * "state" animations (guard, knocked down, surrender) persist until replaced.
 */
public final class Animator {
    private record Playing(AnimDef def, float start, float speed) {}

    private record State(String name, float start) {}

    private static final Map<Integer, Playing> ACTIVE = new HashMap<>();
    private static final Map<Integer, State> STATE = new HashMap<>();
    /** > 0 while the first-person hand is being rendered: animations must not pose that arm. */
    public static int firstPersonHand;

    /** Looping states that expire on their own unless replayed (seconds); others last until replaced. */
    private static float stateTimeout(String name) {
        return switch (name) {
            case "iai_stance", "stance" -> 8f;
            case "surrender" -> 20f;
            case "meditate", "sit_ground" -> 120f;
            default -> Float.MAX_VALUE;
        };
    }

    public static void play(int entityId, String name, float speed) {
        switch (name) {
            case "guard", "knocked_down", "surrender", "iai_stance", "stance", "meditate", "sit_ground" -> {
                State cur = STATE.get(entityId);
                if (cur == null || !cur.name().equals(name)) STATE.put(entityId, new State(name, ClientState.clientTicks));
                return;
            }
            case "guard_end", "get_up", "stance_exit" -> {
                STATE.remove(entityId);
                if (name.equals("guard_end") || name.equals("stance_exit")) return;
            }
            default -> {}
        }
        AnimDef def = AnimLibrary.get(name);
        if (def == null) return;
        ACTIVE.put(entityId, new Playing(def, ClientState.clientTicks, speed <= 0 ? 1 : speed));
    }

    public static boolean isPlaying(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    /** Current one-shot animation for an entity at the given partial-tick time, with its time in seconds. */
    public static AnimDef current(int id, float partial, float[] timeOut) {
        Playing p = ACTIVE.get(id);
        if (p == null) {
            State st = STATE.get(id);
            if (st == null) return null;
            float age = (ClientState.clientTicks + partial - st.start()) / 20f;
            if (age > stateTimeout(st.name())) {
                STATE.remove(id);
                return null;
            }
            AnimDef def = AnimLibrary.get(st.name());
            if (def == null) return null;
            float t = age % Math.max(0.05f, def.length());
            timeOut[0] = t;
            timeOut[1] = Mth.clamp(age / 0.15f, 0, 1); // blend into the state
            return def;
        }
        float t = (ClientState.clientTicks + partial - p.start) / 20f * p.speed;
        if (t > p.def.length()) {
            if (p.def.loop()) t = t % p.def.length();
            else {
                ACTIVE.remove(id);
                return current(id, partial, timeOut);
            }
        }
        timeOut[0] = t;
        float fadeIn = Math.min(1f, t / 0.06f);
        float fadeOut = p.def.loop() ? 1f : Math.min(1f, (p.def.length() - t) / 0.1f);
        timeOut[1] = Mth.clamp(Math.min(fadeIn, fadeOut), 0, 1);
        return p.def;
    }

    public static void apply(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        // race proportions (Long-Arm / Long-Leg); scales copy to armour layers via ModelPart#copyFrom
        var race = com.grandline.client.render.RaceVisuals.of(entity).race();
        float arm = com.grandline.client.render.RaceVisuals.armScale(race), leg = com.grandline.client.render.RaceVisuals.legScale(race);
        model.rightArm.yScale = model.leftArm.yScale = arm;
        model.rightLeg.yScale = model.leftLeg.yScale = leg;
        if (firstPersonHand > 0) return; // the first-person arm keeps its vanilla pose; the item gets fp_right instead
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        float[] tw = new float[2];
        AnimDef def = current(entity.getId(), partial, tw);
        if (def == null) return;
        float t = tw[0], w = tw[1];
        pose(def, "head", model.head, t, w);
        pose(def, "body", model.body, t, w);
        pose(def, "right_arm", model.rightArm, t, w);
        pose(def, "left_arm", model.leftArm, t, w);
        pose(def, "right_leg", model.rightLeg, t, w);
        pose(def, "left_leg", model.leftLeg, t, w);
        model.hat.copyFrom(model.head);
    }

    private static void pose(AnimDef def, String bone, ModelPart part, float t, float w) {
        AnimDef.Track tr = def.bones().get(bone);
        if (tr == null) return;
        float[] r = tr.sampleRot(t);
        if (r != null) {
            part.xRot = Mth.lerp(w, part.xRot, -r[0] * Mth.DEG_TO_RAD);
            part.yRot = Mth.lerp(w, part.yRot, r[1] * Mth.DEG_TO_RAD);
            part.zRot = Mth.lerp(w, part.zRot, r[2] * Mth.DEG_TO_RAD);
        }
        float[] p = tr.samplePos(t);
        if (p != null) {
            part.x += -p[0] * w;
            part.y += -p[1] * w;
            part.z += p[2] * w;
        }
    }

    /** Root transform (whole-body flips/leans) for RenderLivingEvent. Returns {tx,ty,tz,rx,ry,rz} or null. */
    public static float[] root(Entity e, float partial) {
        float[] tw = new float[2];
        AnimDef def = current(e.getId(), partial, tw);
        if (def == null) return null;
        AnimDef.Track tr = def.bones().get("root");
        if (tr == null) return null;
        float[] r = tr.sampleRot(tw[0]);
        float[] p = tr.samplePos(tw[0]);
        float w = tw[1];
        // lunges and hops scale with body size (giants cover more ground, Tontatta less)
        float k = w / 16f * (e instanceof LivingEntity le ? le.getScale() : 1f);
        return new float[]{p == null ? 0 : p[0] * k, p == null ? 0 : p[1] * k, p == null ? 0 : p[2] * k,
                r == null ? 0 : r[0] * w, r == null ? 0 : r[1] * w, r == null ? 0 : r[2] * w};
    }

    /** First-person arm offsets from the "fp_right" bone: {tx,ty,tz,rx,ry,rz}. */
    public static float[] firstPerson(Entity e, float partial) {
        float[] tw = new float[2];
        AnimDef def = current(e.getId(), partial, tw);
        if (def == null) return null;
        AnimDef.Track tr = def.bones().get("fp_right");
        if (tr == null) return null;
        float[] r = tr.sampleRot(tw[0]);
        float[] p = tr.samplePos(tw[0]);
        float w = tw[1];
        return new float[]{p == null ? 0 : p[0] * w / 16f, p == null ? 0 : p[1] * w / 16f, p == null ? 0 : p[2] * w / 16f,
                r == null ? 0 : r[0] * w, r == null ? 0 : r[1] * w, r == null ? 0 : r[2] * w};
    }

    public static void clear(int id) {
        ACTIVE.remove(id);
        STATE.remove(id);
    }

    private Animator() {}
}
