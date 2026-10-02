package com.starforged.tempest.client;

import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Render states for the Tempestforged entities. */
public final class TempestRenderStates {
    private TempestRenderStates() {
    }

    public static class DiveState extends CreatureState {
        public boolean diving;
    }

    public static class ComboState extends CreatureState {
        public int combo;
    }

    public static class BeastState extends CreatureState {
        public int stamp;
        public boolean charged;
        public boolean rushing;
    }

    public static class SpriteState extends CreatureState {
        public boolean sitting;
    }

    public static class RocState extends CreatureState {
        public boolean flying;
        public boolean ridden;
    }

    public static class VeyrState extends CreatureState {
        public int phase;
        public int attack;
        public float attackTicks;
        public float assemble;
        public int shield;
        public boolean dying;
    }

    public static class ConductorState extends EntityRenderState {
        public float raised;
    }

    public static class EyeState extends EntityRenderState {
    }

    public static class CycloneState extends EntityRenderState {
        public float size;
        public float spin;
    }

    public static class SpearState extends EntityRenderState {
        public float yaw;
        public float pitch;
        public int mode;
    }

    public static class HookState extends EntityRenderState {
        public @Nullable Vec3 ownerHand;
        public boolean anchored;
    }

    public static class BeamState extends EntityRenderState {
        public Vec3 end = Vec3.ZERO;
        public float width;
        public float fade;
    }
}
