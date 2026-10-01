package com.starforged.sun.client;

import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Render states for the Sunforged entities. */
public final class SunRenderStates {
    private SunRenderStates() {
    }

    public static class ImpState extends CreatureState {
        public boolean charging;
    }

    public static class HoundState extends CreatureState {
        public boolean sitting;
        public boolean tame;
    }

    public static class KnightState extends CreatureState {
        public boolean guarding;
        public float cleaveTicks;
    }

    public static class PhoenixState extends CreatureState {
        public boolean ridden;
        public float speed;
    }

    public static class WardenState extends CreatureState {
        public int phase;
        public int attack;
        public float attackTicks;
        public float rise;
        public boolean dying;
        public @Nullable Vec3 beamStart;
        public @Nullable Vec3 beamEnd;
        public float nova;
    }

    public static class PylonState extends EntityRenderState {
        public @Nullable Vec3 bossHalo;
        public int hits;
    }

    public static class FlareState extends EntityRenderState {
        public float size;
        public int kind;
    }

    public static class ChakramState extends EntityRenderState {
        public float spin;
    }

    public static class OrbState extends EntityRenderState {
        public @Nullable Vec3 beamTarget;
    }
}
