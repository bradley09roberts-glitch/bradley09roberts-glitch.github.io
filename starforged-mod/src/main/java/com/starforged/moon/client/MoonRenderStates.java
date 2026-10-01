package com.starforged.moon.client;

import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Render states for the Moonforged entities. */
public final class MoonRenderStates {
    private MoonRenderStates() {
    }

    public static class SkimmerState extends CreatureState {
        public float emerge;
    }

    public static class SentinelState extends CreatureState {
        public int charge;
    }

    public static class LurkerState extends CreatureState {
        public boolean watched;
        public float frozenWalk;
    }

    public static class KitState extends CreatureState {
        public boolean sitting;
    }

    public static class LeaperState extends CreatureState {
        public int charge;
        public boolean airborne;
        public float lean;
    }

    public static class MatriarchState extends CreatureState {
        public int phase;
        public int attack;
        public float attackTicks;
        public float emerge;
        public boolean dying;
        public boolean echo;
        public @Nullable Vec3 beamStart;
        public @Nullable Vec3 beamEnd;
    }

    public static class AnchorState extends EntityRenderState {
        public @Nullable Vec3 bossCore;
        public int hits;
    }

    public static class MoonletState extends EntityRenderState {
        public float size;
        public int kind;
    }

    public static class SpinState extends EntityRenderState {
        public float spin;
        public float yaw;
    }

    public static class HookState extends EntityRenderState {
        public @Nullable Vec3 ownerHand;
        public boolean anchored;
    }

    public static class FallingMoonState extends EntityRenderState {
        public float progress;
    }

    public static class BoltState extends EntityRenderState {
        public boolean beacon;
        public boolean stuck;
    }
}
