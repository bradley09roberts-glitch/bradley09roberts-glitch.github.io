package com.starforged.client.render;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/** Render states for every Starforged entity. */
@OnlyIn(Dist.CLIENT)
public final class StarforgedRenderStates {
    private StarforgedRenderStates() {
    }

    public static class CreatureState extends LivingEntityRenderState {
        public float attackTime;
        /** Distance from the camera, used for fading effects. */
        public double cameraDistance;
    }

    public static class GolemState extends CreatureState {
        public boolean dormant;
        public int action;
        public float actionTicks;
        public float attackTicksRemaining;
    }

    public static class MimicState extends CreatureState {
        public boolean disguised;
        public float chomp;
    }

    public static class WraithState extends CreatureState {
        public boolean casting;
    }

    public static class PetState extends CreatureState {
        public boolean sitting;
        public boolean tame;
        public boolean ridden;
    }

    public static class SovereignState extends CreatureState {
        public int phase;
        public int attack;
        public float attackTicks;
        public float phaseTicks;
        public float entrance;
        public @Nullable Vec3 beamStart;
        public @Nullable Vec3 beamEnd;
        public boolean dying;
    }

    public static class CrystalState extends EntityRenderState {
        public @Nullable Vec3 bossCore;
        public int hits;
    }

    public static class MeteorState extends EntityRenderState {
        public final BlockModelRenderState rock = new BlockModelRenderState();
        public float size;
        public boolean hollow;
    }

    public static class SingularityState extends EntityRenderState {
        public float scale;
        public float spin;
    }

    public static class WaveState extends EntityRenderState {
        public float yaw;
        public float pitch;
        public float fade;
    }
}
