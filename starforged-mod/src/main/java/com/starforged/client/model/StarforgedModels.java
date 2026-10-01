package com.starforged.client.model;

import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import com.starforged.client.render.StarforgedRenderStates.CrystalState;
import com.starforged.client.render.StarforgedRenderStates.GolemState;
import com.starforged.client.render.StarforgedRenderStates.MimicState;
import com.starforged.client.render.StarforgedRenderStates.PetState;
import com.starforged.client.render.StarforgedRenderStates.SovereignState;
import com.starforged.client.render.StarforgedRenderStates.WraithState;
import com.starforged.entity.boss.EclipseSovereignEntity;
import com.starforged.entity.monster.AstralGolemEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Animated models for every Starforged creature. Geometry comes from {@link ModelGeometry}; the procedural
 * animations live here.
 */
public final class StarforgedModels {
    private StarforgedModels() {
    }

    private static final float DEG = Mth.DEG_TO_RAD;

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // --- Star Mite ---------------------------------------------------------------------------------------------

    public static class StarMiteModel extends EntityModel<CreatureState> {
        private final ModelPart head;
        private final ModelPart abdomen;
        private final ModelPart[] legs = new ModelPart[6];

        public StarMiteModel(ModelPart root) {
            super(root);
            this.head = root.getChild("head");
            this.abdomen = root.getChild("abdomen");
            int i = 0;
            for (int n = 0; n < 3; n++) {
                this.legs[i++] = root.getChild("leg_right_" + n);
                this.legs[i++] = root.getChild("leg_left_" + n);
            }
        }

        @Override
        public void setupAnim(CreatureState state) {
            super.setupAnim(state);
            float pos = state.walkAnimationPos;
            float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.5F);
            this.head.yRot = state.yRot * DEG * 0.6F;
            this.head.xRot = state.xRot * DEG * 0.4F;
            for (int i = 0; i < 6; i++) {
                float side = i % 2 == 0 ? -1.0F : 1.0F;
                float phase = (i / 2) * 2.1F + (i % 2) * Mth.PI;
                this.legs[i].yRot += Mth.sin(pos * 1.3F + phase) * 0.5F * speed * side;
                this.legs[i].zRot += Math.abs(Mth.cos(pos * 1.3F + phase)) * 0.35F * speed * side;
            }
            this.abdomen.xRot = Mth.sin(state.ageInTicks * 0.15F) * 0.06F;
            this.abdomen.y += Mth.sin(state.ageInTicks * 0.15F) * 0.3F;
        }
    }

    // --- Void Stalker ------------------------------------------------------------------------------------------

    public static class VoidStalkerModel extends EntityModel<CreatureState> {
        private final ModelPart head;
        private final ModelPart body;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        public VoidStalkerModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        @Override
        public void setupAnim(CreatureState state) {
            super.setupAnim(state);
            float pos = state.walkAnimationPos;
            float speed = state.walkAnimationSpeed;
            float age = state.ageInTicks;
            this.head.yRot = state.yRot * DEG;
            this.head.xRot = state.xRot * DEG + 0.15F;
            this.head.zRot = Mth.sin(age * 0.07F) * 0.08F;
            this.body.xRot = 0.18F;
            this.head.z -= 1.0F;
            this.rightLeg.xRot = Mth.cos(pos * 0.6662F) * 1.1F * speed;
            this.leftLeg.xRot = Mth.cos(pos * 0.6662F + Mth.PI) * 1.1F * speed;
            this.rightArm.xRot = Mth.cos(pos * 0.6662F + Mth.PI) * 0.7F * speed + 0.1F + Mth.sin(age * 0.05F) * 0.06F;
            this.leftArm.xRot = Mth.cos(pos * 0.6662F) * 0.7F * speed + 0.1F + Mth.sin(age * 0.05F + 1.0F) * 0.06F;
            this.rightArm.zRot = 0.12F + Mth.sin(age * 0.09F) * 0.05F;
            this.leftArm.zRot = -0.12F - Mth.sin(age * 0.09F) * 0.05F;
            if (state.attackTime > 0.0F) {
                float swing = Mth.sin(state.attackTime * Mth.PI);
                this.rightArm.xRot = -2.2F + swing * 2.0F;
                this.leftArm.xRot = -2.2F + swing * 2.0F;
                this.rightArm.yRot = -0.4F * swing;
                this.leftArm.yRot = 0.4F * swing;
            }
        }
    }

    // --- Astral Golem ------------------------------------------------------------------------------------------

    public static class AstralGolemModel extends EntityModel<GolemState> {
        private final ModelPart head;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        public AstralGolemModel(ModelPart root) {
            super(root);
            this.head = root.getChild("head");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        @Override
        public void setupAnim(GolemState state) {
            super.setupAnim(state);
            if (state.dormant) {
                this.head.xRot = 0.45F;
                this.rightArm.xRot = -0.15F;
                this.leftArm.xRot = -0.15F;
                return;
            }
            float pos = state.walkAnimationPos;
            float speed = state.walkAnimationSpeed;
            this.head.yRot = state.yRot * DEG;
            this.head.xRot = state.xRot * DEG;
            this.rightLeg.xRot = -1.5F * Mth.triangleWave(pos, 13.0F) * speed;
            this.leftLeg.xRot = 1.5F * Mth.triangleWave(pos, 13.0F) * speed;
            this.rightArm.xRot = (-0.2F + 1.5F * Mth.triangleWave(pos, 13.0F)) * speed;
            this.leftArm.xRot = (-0.2F - 1.5F * Mth.triangleWave(pos, 13.0F)) * speed;
            float t = state.actionTicks;
            if (state.action == AstralGolemEntity.ACTION_SLAM) {
                float raise = t < 20 ? ease(t / 16.0F) : 1.0F - ease((t - 20.0F) / 3.0F);
                this.rightArm.xRot = -2.9F * raise + (t >= 22 ? 0.4F : 0.0F);
                this.leftArm.xRot = -2.9F * raise + (t >= 22 ? 0.4F : 0.0F);
                this.head.xRot = t >= 22 && t < 30 ? 0.4F : -0.2F;
            } else if (state.action == AstralGolemEntity.ACTION_BARRAGE) {
                this.rightArm.xRot = -1.4F + Mth.sin(t * 0.8F) * 0.25F;
                this.leftArm.xRot = -1.4F - Mth.sin(t * 0.8F) * 0.25F;
            } else if (state.attackTicksRemaining > 0.0F) {
                this.rightArm.xRot = -2.0F + 1.5F * Mth.triangleWave(state.attackTicksRemaining, 10.0F);
                this.leftArm.xRot = -2.0F + 1.5F * Mth.triangleWave(state.attackTicksRemaining, 10.0F);
            }
        }
    }

    // --- Mimic -------------------------------------------------------------------------------------------------

    public static class MimicModel extends EntityModel<MimicState> {
        private final ModelPart base;
        private final ModelPart lid;
        private final ModelPart tongue;

        public MimicModel(ModelPart root) {
            super(root);
            this.base = root.getChild("base");
            this.lid = root.getChild("lid");
            this.tongue = this.base.getChild("tongue");
        }

        @Override
        public void setupAnim(MimicState state) {
            super.setupAnim(state);
            float age = state.ageInTicks;
            if (state.disguised) {
                // Every so often it "breathes" - a sharp-eyed player may notice the lid twitch.
                float breath = Mth.sin(age * 0.045F);
                this.lid.xRot = breath > 0.97F ? -(breath - 0.97F) * 4.0F : 0.0F;
                this.tongue.visible = false;
                return;
            }
            this.tongue.visible = true;
            float open = 0.35F + 0.3F * Math.abs(Mth.sin(age * 0.35F));
            if (state.chomp > 0.0F) {
                float c = state.chomp / 10.0F;
                open = c > 0.5F ? (1.0F - c) * 2.4F : c * 2.4F;
            }
            this.lid.xRot = -open;
            this.tongue.xRot = Mth.sin(age * 0.5F) * 0.25F;
            this.tongue.yRot = Mth.sin(age * 0.3F) * 0.2F;
            float bounce = Math.abs(Mth.sin(state.walkAnimationPos * 0.7F)) * 4.0F * Math.min(1.0F, state.walkAnimationSpeed * 2.0F);
            this.base.y -= bounce;
            this.lid.y -= bounce;
        }
    }

    // --- Astral Wraith -----------------------------------------------------------------------------------------

    public static class AstralWraithModel extends EntityModel<WraithState> {
        private final ModelPart hood;
        private final ModelPart body;
        private final ModelPart skirt;
        private final ModelPart tatters;
        private final ModelPart rightArm;
        private final ModelPart leftArm;

        public AstralWraithModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.hood = root.getChild("hood");
            this.body = root.getChild("body");
            this.skirt = root.getChild("skirt");
            this.tatters = this.skirt.getChild("tatters");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
        }

        @Override
        public void setupAnim(WraithState state) {
            super.setupAnim(state);
            float age = state.ageInTicks;
            float bob = Mth.sin(age * 0.1F) * 1.2F;
            for (ModelPart p : new ModelPart[]{this.hood, this.body, this.skirt, this.rightArm, this.leftArm}) {
                p.y += bob;
            }
            this.hood.yRot = state.yRot * DEG;
            this.hood.xRot = state.xRot * DEG;
            this.skirt.xRot = 0.1F + Mth.sin(age * 0.15F) * 0.08F;
            this.tatters.xRot = Mth.sin(age * 0.22F + 0.5F) * 0.18F;
            if (state.casting) {
                this.rightArm.xRot = -2.4F + Mth.sin(age * 0.6F) * 0.1F;
                this.leftArm.xRot = -2.4F - Mth.sin(age * 0.6F) * 0.1F;
                this.rightArm.zRot = 0.2F;
                this.leftArm.zRot = -0.2F;
            } else {
                this.rightArm.xRot = -0.5F + Mth.sin(age * 0.08F) * 0.08F;
                this.leftArm.xRot = -0.2F + Mth.sin(age * 0.08F + 2.0F) * 0.1F;
                this.leftArm.zRot = -0.15F;
            }
        }
    }

    // --- Starling ----------------------------------------------------------------------------------------------

    public static class StarlingModel extends EntityModel<PetState> {
        private final ModelPart core;
        private final ModelPart rightWing;
        private final ModelPart leftWing;

        public StarlingModel(ModelPart root) {
            super(root);
            this.core = root.getChild("core");
            this.rightWing = this.core.getChild("right_wing");
            this.leftWing = this.core.getChild("left_wing");
        }

        @Override
        public void setupAnim(PetState state) {
            super.setupAnim(state);
            float age = state.ageInTicks;
            this.core.yRot = state.yRot * DEG;
            this.core.xRot = state.xRot * DEG * 0.5F;
            this.core.zRot = Mth.sin(age * 0.08F) * 0.15F;
            this.core.y += state.sitting ? 3.0F : Mth.sin(age * 0.2F) * 1.2F;
            float flap = state.sitting ? 0.1F : Mth.sin(age * 1.4F) * 0.7F;
            this.rightWing.zRot = flap;
            this.leftWing.zRot = -flap;
        }
    }

    // --- Nebula Ray --------------------------------------------------------------------------------------------

    public static class NebulaRayModel extends EntityModel<PetState> {
        private final ModelPart body;
        private final ModelPart rightWing;
        private final ModelPart rightTip;
        private final ModelPart leftWing;
        private final ModelPart leftTip;
        private final ModelPart tail1;
        private final ModelPart tail2;
        private final ModelPart tail3;

        public NebulaRayModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
            this.rightWing = this.body.getChild("right_wing");
            this.rightTip = this.rightWing.getChild("right_wing_tip");
            this.leftWing = this.body.getChild("left_wing");
            this.leftTip = this.leftWing.getChild("left_wing_tip");
            this.tail1 = this.body.getChild("tail1");
            this.tail2 = this.tail1.getChild("tail2");
            this.tail3 = this.tail2.getChild("tail3");
        }

        @Override
        public void setupAnim(PetState state) {
            super.setupAnim(state);
            float age = state.ageInTicks;
            float speed = state.ridden ? 0.22F : 0.12F;
            float t = age * speed;
            float amp = state.ridden ? 0.55F : 0.42F;
            this.rightWing.zRot = Mth.sin(t) * amp;
            this.rightTip.zRot = Mth.sin(t - 0.7F) * amp * 0.8F;
            this.leftWing.zRot = -Mth.sin(t) * amp;
            this.leftTip.zRot = -Mth.sin(t - 0.7F) * amp * 0.8F;
            this.body.y += Mth.cos(t) * 1.5F;
            this.body.xRot = state.xRot * DEG * 0.6F;
            this.tail1.yRot = Mth.sin(age * 0.08F) * 0.25F;
            this.tail2.yRot = Mth.sin(age * 0.08F - 0.8F) * 0.35F;
            this.tail3.yRot = Mth.sin(age * 0.08F - 1.6F) * 0.45F;
            this.tail1.xRot = Mth.sin(t + 1.0F) * 0.1F;
        }
    }

    // --- Eclipse Sovereign -------------------------------------------------------------------------------------

    public static class EclipseSovereignModel extends EntityModel<SovereignState> {
        private final ModelPart body;
        private final ModelPart chest;
        private final ModelPart head;
        private final ModelPart halo;
        private final ModelPart trail;
        private final ModelPart trailTip;
        private final ModelPart rightHand;
        private final ModelPart leftHand;
        private final ModelPart[] cape = new ModelPart[3];
        private final ModelPart[] rightFingers = new ModelPart[4];
        private final ModelPart[] leftFingers = new ModelPart[4];

        public EclipseSovereignModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
            this.chest = this.body.getChild("chest");
            this.head = this.chest.getChild("head");
            this.halo = this.head.getChild("halo");
            this.trail = ModLayers.path(this.body, "waist/trail");
            this.trailTip = this.trail.getChild("trail_tip");
            this.rightHand = root.getChild("right_hand");
            this.leftHand = root.getChild("left_hand");
            for (int i = 0; i < 3; i++) {
                this.cape[i] = this.chest.getChild("cape_" + i);
            }
            for (int i = 0; i < 4; i++) {
                this.rightFingers[i] = this.rightHand.getChild("right_finger_" + i);
                this.leftFingers[i] = this.leftHand.getChild("left_finger_" + i);
            }
        }

        private void hands(float x, float y, float z, float xRot, float curl) {
            this.rightHand.x = -x;
            this.leftHand.x = x;
            this.rightHand.y = y;
            this.leftHand.y = y;
            this.rightHand.z = z;
            this.leftHand.z = z;
            this.rightHand.xRot = xRot;
            this.leftHand.xRot = xRot;
            for (int i = 0; i < 4; i++) {
                this.rightFingers[i].xRot = curl;
                this.leftFingers[i].xRot = curl;
            }
        }

        @Override
        public void setupAnim(SovereignState state) {
            super.setupAnim(state);
            float age = state.ageInTicks;
            float bob = Mth.sin(age * 0.07F) * 2.0F;
            this.body.y += bob;
            this.head.yRot = state.yRot * DEG;
            this.head.xRot = state.xRot * DEG * 0.6F;
            this.halo.zRot = age * 0.03F;
            this.trail.xRot = Mth.sin(age * 0.09F) * 0.12F;
            this.trailTip.xRot = Mth.sin(age * 0.09F - 0.7F) * 0.18F;
            for (int i = 0; i < 3; i++) {
                this.cape[i].xRot = 0.15F + Mth.sin(age * 0.11F + i) * 0.1F;
            }

            // Idle gauntlet float.
            float handBob = Mth.sin(age * 0.09F + 1.3F) * 2.5F;
            this.hands(22.0F, -14.0F + bob + handBob, -2.0F, Mth.sin(age * 0.05F) * 0.1F, 0.2F);

            float t = state.attackTicks;
            switch (state.attack) {
                case EclipseSovereignEntity.ATTACK_METEORS -> {
                    float up = ease(t / 10.0F);
                    this.hands(20.0F + 6.0F * up, -14.0F - 22.0F * up + bob, -2.0F, -0.6F * up, -0.3F);
                    this.head.xRot -= 0.5F * up;
                }
                case EclipseSovereignEntity.ATTACK_BEAM -> {
                    if (t < EclipseSovereignEntity.BEAM_CHARGE) {
                        float c = ease(t / 20.0F);
                        this.hands(22.0F - 12.0F * c, -14.0F + bob, -2.0F - 10.0F * c, -0.4F * c, 0.8F);
                    } else {
                        float s = ease((t - EclipseSovereignEntity.BEAM_CHARGE) / 6.0F);
                        this.hands(10.0F + 18.0F * s, -14.0F - 8.0F * s + bob, -12.0F + 8.0F * s, -0.4F, -0.4F);
                        this.chest.xRot = -0.15F * s;
                    }
                }
                case EclipseSovereignEntity.ATTACK_SLAM -> {
                    if (t < 22) {
                        float r = ease(t / 14.0F);
                        this.hands(14.0F, -14.0F - 26.0F * r + bob, -4.0F, -0.8F * r, 1.2F);
                    } else {
                        float s = ease((t - 22.0F) / 4.0F);
                        float back = t > 32 ? ease((t - 32.0F) / 10.0F) : 0.0F;
                        this.hands(14.0F, -40.0F + 66.0F * s * (1.0F - back) + 26.0F * back * 0.0F + bob, -4.0F, 0.6F * s, 1.4F);
                        this.chest.xRot = 0.35F * s * (1.0F - back);
                        this.head.xRot += 0.3F * s * (1.0F - back);
                    }
                }
                case EclipseSovereignEntity.ATTACK_SINGULARITY, EclipseSovereignEntity.ATTACK_NOVA -> {
                    float c = ease(t / 12.0F);
                    this.hands(22.0F - 8.0F * c, -14.0F + bob, -2.0F - 12.0F * c, -1.2F * c, 1.0F);
                    this.rightHand.zRot = c * 0.6F + Mth.sin(age * 0.5F) * 0.1F * c;
                    this.leftHand.zRot = -c * 0.6F - Mth.sin(age * 0.5F) * 0.1F * c;
                }
                case EclipseSovereignEntity.ATTACK_SUMMON -> {
                    float d = ease(t / 15.0F);
                    this.hands(22.0F + 8.0F * d, -14.0F + 14.0F * d + bob, -2.0F, 0.5F * d, 0.0F);
                    this.head.xRot += 0.35F * d;
                }
                default -> {
                }
            }
            if (state.phase == EclipseSovereignEntity.PHASE_STUNNED) {
                this.chest.xRot = 0.45F;
                this.head.xRot = 0.6F;
                this.hands(18.0F, 4.0F + bob, -2.0F, 0.6F, 0.0F);
            }
            if (state.phase == EclipseSovereignEntity.PHASE_ENTRANCE) {
                float spread = 1.0F - ease(state.entrance / (float) EclipseSovereignEntity.ENTRANCE_TICKS);
                this.hands(10.0F + 18.0F * spread, -14.0F - 10.0F * spread + bob, -2.0F, -0.5F * spread, -0.4F);
            }
            if (state.dying) {
                this.chest.xRot = -0.4F;
                this.head.xRot = -0.7F + Mth.sin(age * 2.0F) * 0.1F;
                this.hands(28.0F, -30.0F, 0.0F, -1.0F, -0.6F);
            }
        }
    }

    // --- Eclipse Crystal ---------------------------------------------------------------------------------------

    public static class EclipseCrystalModel extends Model<CrystalState> {
        private final ModelPart crystal;
        private final ModelPart outer;
        private final ModelPart inner;

        public EclipseCrystalModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucentEmissive);
            this.crystal = root.getChild("crystal");
            this.outer = this.crystal.getChild("outer");
            this.inner = this.crystal.getChild("inner");
        }

        @Override
        public void setupAnim(CrystalState state) {
            super.setupAnim(state);
            float age = state.ageInTicks;
            this.crystal.yRot = age * 0.06F;
            this.outer.xRot += age * 0.04F;
            this.inner.yRot += -age * 0.1F;
            this.crystal.y += Mth.sin(age * 0.1F) * 1.5F;
        }
    }
}
