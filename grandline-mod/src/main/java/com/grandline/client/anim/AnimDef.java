package com.grandline.client.anim;

import java.util.Map;

/** A keyframed humanoid animation (Bedrock/GeckoLib-compatible JSON). Rotations in degrees, positions in pixels. */
public record AnimDef(String name, float length, boolean loop, Map<String, Track> bones) {
    public record Key(float t, float x, float y, float z) {}

    public record Track(Key[] rotation, Key[] position) {
        public float[] sampleRot(float t) {
            return sample(rotation, t);
        }

        public float[] samplePos(float t) {
            return sample(position, t);
        }

        static float[] sample(Key[] keys, float t) {
            if (keys == null || keys.length == 0) return null;
            if (t <= keys[0].t()) return new float[]{keys[0].x(), keys[0].y(), keys[0].z()};
            for (int i = 0; i < keys.length - 1; i++) {
                Key a = keys[i], b = keys[i + 1];
                if (t <= b.t()) {
                    float k = (t - a.t()) / Math.max(1e-4f, b.t() - a.t());
                    k = k * k * (3 - 2 * k); // smoothstep easing
                    return new float[]{a.x() + (b.x() - a.x()) * k, a.y() + (b.y() - a.y()) * k, a.z() + (b.z() - a.z()) * k};
                }
            }
            Key l = keys[keys.length - 1];
            return new float[]{l.x(), l.y(), l.z()};
        }
    }
}
