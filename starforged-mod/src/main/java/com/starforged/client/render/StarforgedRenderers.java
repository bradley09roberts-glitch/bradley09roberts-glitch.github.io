package com.starforged.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starforged.Starforged;
import com.starforged.client.model.ModLayers;
import com.starforged.client.model.StarforgedModels.AstralGolemModel;
import com.starforged.client.model.StarforgedModels.AstralWraithModel;
import com.starforged.client.model.StarforgedModels.EclipseCrystalModel;
import com.starforged.client.model.StarforgedModels.EclipseSovereignModel;
import com.starforged.client.model.StarforgedModels.MimicModel;
import com.starforged.client.model.StarforgedModels.NebulaRayModel;
import com.starforged.client.model.StarforgedModels.StarMiteModel;
import com.starforged.client.model.StarforgedModels.StarlingModel;
import com.starforged.client.model.StarforgedModels.VoidStalkerModel;
import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import com.starforged.client.render.StarforgedRenderStates.CrystalState;
import com.starforged.client.render.StarforgedRenderStates.GolemState;
import com.starforged.client.render.StarforgedRenderStates.MeteorState;
import com.starforged.client.render.StarforgedRenderStates.MimicState;
import com.starforged.client.render.StarforgedRenderStates.PetState;
import com.starforged.client.render.StarforgedRenderStates.SingularityState;
import com.starforged.client.render.StarforgedRenderStates.SovereignState;
import com.starforged.client.render.StarforgedRenderStates.WaveState;
import com.starforged.client.render.StarforgedRenderStates.WraithState;
import com.starforged.entity.animal.NebulaRayEntity;
import com.starforged.entity.animal.StarlingEntity;
import com.starforged.entity.boss.EclipseCrystalEntity;
import com.starforged.entity.boss.EclipseSovereignEntity;
import com.starforged.entity.monster.AstralGolemEntity;
import com.starforged.entity.monster.AstralWraithEntity;
import com.starforged.entity.monster.MimicEntity;
import com.starforged.entity.monster.StarMiteEntity;
import com.starforged.entity.monster.VoidStalkerEntity;
import com.starforged.entity.projectile.EclipseWaveEntity;
import com.starforged.entity.projectile.MeteorEntity;
import com.starforged.entity.projectile.SingularityEntity;
import com.starforged.registry.ModBlocks;
import com.starforged.registry.ModEntities;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;

public final class StarforgedRenderers {
    public static final int FULL_BRIGHT = 15728880;
    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();

    private StarforgedRenderers() {
    }

    public static Identifier tex(String path) {
        return Starforged.id("textures/" + path + ".png");
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STAR_MITE.get(), StarMiteRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_STALKER.get(), VoidStalkerRenderer::new);
        event.registerEntityRenderer(ModEntities.ASTRAL_GOLEM.get(), AstralGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.MIMIC.get(), MimicRenderer::new);
        event.registerEntityRenderer(ModEntities.ASTRAL_WRAITH.get(), AstralWraithRenderer::new);
        event.registerEntityRenderer(ModEntities.STARLING.get(), StarlingRenderer::new);
        event.registerEntityRenderer(ModEntities.NEBULA_RAY.get(), NebulaRayRenderer::new);
        event.registerEntityRenderer(ModEntities.ECLIPSE_SOVEREIGN.get(), EclipseSovereignRenderer::new);
        event.registerEntityRenderer(ModEntities.ECLIPSE_CRYSTAL.get(), EclipseCrystalRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.SINGULARITY.get(), SingularityRenderer::new);
        event.registerEntityRenderer(ModEntities.ECLIPSE_WAVE.get(), EclipseWaveRenderer::new);
        event.registerEntityRenderer(ModEntities.STARBOLT.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.1F, true));
        event.registerEntityRenderer(ModEntities.THROWN_SINGULARITY.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.9F, true));
    }

    private static double distanceToPlayer(Entity entity) {
        var player = Minecraft.getInstance().player;
        return player == null ? 0.0 : player.distanceTo(entity);
    }

    // --- Shared layers ----------------------------------------------------------------------------------------

    /** Draws a full-bright emissive copy of the model with the "_glow" texture. */
    public static class GlowLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
        private final RenderType type;
        private final Predicate<S> visible;

        public GlowLayer(RenderLayerParent<S, M> parent, Identifier texture, Predicate<S> visible) {
            super(parent);
            this.type = RenderTypes.eyes(texture);
            this.visible = visible;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
            if (!state.isInvisible && this.visible.test(state)) {
                collector.order(1).submitModel(this.getParentModel(), state, poseStack, this.type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null,
                    state.outlineColor, null);
            }
        }
    }

    private static <T extends LivingEntity, S extends CreatureState> void extractCreature(T entity, S state, float partial) {
        state.attackTime = entity.getAttackAnim(partial);
        state.cameraDistance = distanceToPlayer(entity);
    }

    // --- Creatures --------------------------------------------------------------------------------------------

    public static class StarMiteRenderer extends MobRenderer<StarMiteEntity, CreatureState, StarMiteModel> {
        private static final Identifier TEXTURE = tex("entity/star_mite");

        public StarMiteRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new StarMiteModel(ctx.bakeLayer(ModLayers.STAR_MITE)), 0.4F);
            this.addLayer(new GlowLayer<>(this, tex("entity/star_mite_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(CreatureState state) {
            return TEXTURE;
        }

        @Override
        public CreatureState createRenderState() {
            return new CreatureState();
        }

        @Override
        public void extractRenderState(StarMiteEntity entity, CreatureState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
        }
    }

    public static class VoidStalkerRenderer extends MobRenderer<VoidStalkerEntity, CreatureState, VoidStalkerModel> {
        private static final Identifier TEXTURE = tex("entity/void_stalker");

        public VoidStalkerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new VoidStalkerModel(ctx.bakeLayer(ModLayers.VOID_STALKER)), 0.5F);
            this.addLayer(new GlowLayer<>(this, tex("entity/void_stalker_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(CreatureState state) {
            return TEXTURE;
        }

        @Override
        public CreatureState createRenderState() {
            return new CreatureState();
        }

        @Override
        public void extractRenderState(VoidStalkerEntity entity, CreatureState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
        }

        /** Stalkers fade into the dark at a distance - only their eyes stay visible. */
        @Override
        protected int getModelTint(CreatureState state) {
            float alpha = (float) Mth.clamp((22.0 - state.cameraDistance) / 12.0, 0.08, 1.0);
            return ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F);
        }

        @Override
        protected void scale(CreatureState state, PoseStack poseStack) {
            poseStack.scale(0.9F, 0.9F, 0.9F);
        }
    }

    public static class AstralGolemRenderer extends MobRenderer<AstralGolemEntity, GolemState, AstralGolemModel> {
        private static final Identifier TEXTURE = tex("entity/astral_golem");

        public AstralGolemRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new AstralGolemModel(ctx.bakeLayer(ModLayers.ASTRAL_GOLEM)), 1.1F);
            this.addLayer(new GlowLayer<>(this, tex("entity/astral_golem_glow"), s -> !s.dormant));
        }

        @Override
        public Identifier getTextureLocation(GolemState state) {
            return TEXTURE;
        }

        @Override
        public GolemState createRenderState() {
            return new GolemState();
        }

        @Override
        public void extractRenderState(AstralGolemEntity entity, GolemState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
            state.dormant = entity.isDormant();
            state.action = entity.getAction();
            state.actionTicks = entity.clientActionTicks + partial;
            state.attackTicksRemaining = entity.getAttackAnimationTick() > 0 ? entity.getAttackAnimationTick() - partial : 0.0F;
        }

        @Override
        protected void scale(GolemState state, PoseStack poseStack) {
            poseStack.scale(1.1F, 1.1F, 1.1F);
        }
    }

    public static class MimicRenderer extends MobRenderer<MimicEntity, MimicState, MimicModel> {
        private static final Identifier TEXTURE = tex("entity/mimic");

        public MimicRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MimicModel(ctx.bakeLayer(ModLayers.MIMIC)), 0.5F);
        }

        @Override
        public Identifier getTextureLocation(MimicState state) {
            return TEXTURE;
        }

        @Override
        public MimicState createRenderState() {
            return new MimicState();
        }

        @Override
        public void extractRenderState(MimicEntity entity, MimicState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
            state.disguised = entity.isDisguised();
            state.chomp = entity.chompTicks > 0 ? entity.chompTicks - partial : 0.0F;
        }

        @Override
        protected float getShadowRadius(MimicState state) {
            return state.disguised ? 0.0F : super.getShadowRadius(state);
        }
    }

    public static class AstralWraithRenderer extends MobRenderer<AstralWraithEntity, WraithState, AstralWraithModel> {
        private static final Identifier TEXTURE = tex("entity/astral_wraith");

        public AstralWraithRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new AstralWraithModel(ctx.bakeLayer(ModLayers.ASTRAL_WRAITH)), 0.0F);
            this.addLayer(new GlowLayer<>(this, tex("entity/astral_wraith_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(WraithState state) {
            return TEXTURE;
        }

        @Override
        public WraithState createRenderState() {
            return new WraithState();
        }

        @Override
        public void extractRenderState(AstralWraithEntity entity, WraithState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
            state.casting = entity.isCasting();
        }

        @Override
        protected int getModelTint(WraithState state) {
            return ARGB.colorFromFloat(0.8F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static class StarlingRenderer extends MobRenderer<StarlingEntity, PetState, StarlingModel> {
        private static final Identifier TEXTURE = tex("entity/starling");

        public StarlingRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new StarlingModel(ctx.bakeLayer(ModLayers.STARLING)), 0.25F);
            this.addLayer(new GlowLayer<>(this, tex("entity/starling_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(PetState state) {
            return TEXTURE;
        }

        @Override
        public PetState createRenderState() {
            return new PetState();
        }

        @Override
        public void extractRenderState(StarlingEntity entity, PetState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
            state.sitting = entity.isInSittingPose();
            state.tame = entity.isTame();
        }

        @Override
        protected int getBlockLightLevel(StarlingEntity entity, net.minecraft.core.BlockPos pos) {
            return 15;
        }
    }

    public static class NebulaRayRenderer extends MobRenderer<NebulaRayEntity, PetState, NebulaRayModel> {
        private static final Identifier TEXTURE = tex("entity/nebula_ray");

        public NebulaRayRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new NebulaRayModel(ctx.bakeLayer(ModLayers.NEBULA_RAY)), 1.2F);
            this.addLayer(new GlowLayer<>(this, tex("entity/nebula_ray_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(PetState state) {
            return TEXTURE;
        }

        @Override
        public PetState createRenderState() {
            return new PetState();
        }

        @Override
        public void extractRenderState(NebulaRayEntity entity, PetState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
            state.tame = entity.isTame();
            state.ridden = entity.isVehicle();
        }
    }

    // --- The Eclipse Sovereign ---------------------------------------------------------------------------------

    public static class EclipseSovereignRenderer extends MobRenderer<EclipseSovereignEntity, SovereignState, EclipseSovereignModel> {
        private static final Identifier TEXTURE = tex("entity/eclipse_sovereign");
        private static final Identifier BEAM = tex("entity/eclipse_beam");

        public EclipseSovereignRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new EclipseSovereignModel(ctx.bakeLayer(ModLayers.ECLIPSE_SOVEREIGN)), 2.0F);
            this.addLayer(new GlowLayer<>(this, tex("entity/eclipse_sovereign_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(SovereignState state) {
            return TEXTURE;
        }

        @Override
        public SovereignState createRenderState() {
            return new SovereignState();
        }

        @Override
        protected AABB getBoundingBoxForCulling(EclipseSovereignEntity entity) {
            return entity.getBoundingBox().inflate(6.0);
        }

        @Override
        public boolean shouldRender(EclipseSovereignEntity entity, net.minecraft.client.renderer.culling.Frustum culler, double x, double y, double z) {
            return super.shouldRender(entity, culler, x, y, z) || entity.attack() == EclipseSovereignEntity.ATTACK_BEAM;
        }

        @Override
        public void extractRenderState(EclipseSovereignEntity entity, SovereignState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extractCreature(entity, state, partial);
            state.phase = entity.phase();
            state.attack = entity.attack();
            state.attackTicks = entity.clientAttackTicks + partial;
            state.phaseTicks = entity.clientPhaseTicks + partial;
            state.entrance = entity.entranceTicks();
            state.dying = entity.deathTime > 0;
            Vec3 origin = entity.getPosition(partial);
            if (entity.attack() == EclipseSovereignEntity.ATTACK_BEAM && entity.clientAttackTicks >= EclipseSovereignEntity.BEAM_CHARGE) {
                Vec3 end = entity.beamEnd();
                if (end.lengthSqr() > 0.01) {
                    state.beamStart = entity.corePosition().subtract(entity.position());
                    state.beamEnd = end.subtract(origin);
                } else {
                    state.beamStart = null;
                }
            } else {
                state.beamStart = null;
            }
        }

        @Override
        protected void scale(SovereignState state, PoseStack poseStack) {
            poseStack.scale(1.45F, 1.45F, 1.45F);
            if (state.dying) {
                float shake = 0.04F;
                poseStack.translate((Math.random() - 0.5) * shake, 0, (Math.random() - 0.5) * shake);
            }
        }

        @Override
        protected float getFlipDegrees() {
            return 0.0F;
        }

        @Override
        public void submit(SovereignState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            if (state.beamStart != null && state.beamEnd != null) {
                float pulse = 1.0F + Mth.sin(state.ageInTicks * 1.3F) * 0.15F;
                Beams.beam(poseStack, collector, BEAM, state.beamStart, state.beamEnd, 1.2F * pulse, 0xFFB45CFF, state.ageInTicks);
                Beams.beam(poseStack, collector, BEAM, state.beamStart, state.beamEnd, 0.45F * pulse, 0xFFFFF3E0, state.ageInTicks * 1.7F);
            }
        }
    }

    public static class EclipseCrystalRenderer extends EntityRenderer<EclipseCrystalEntity, CrystalState> {
        private static final Identifier TEXTURE = tex("entity/eclipse_crystal");
        private static final Identifier BEAM = tex("entity/eclipse_beam");
        private final EclipseCrystalModel model;

        public EclipseCrystalRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.model = new EclipseCrystalModel(ctx.bakeLayer(ModLayers.ECLIPSE_CRYSTAL));
            this.shadowRadius = 0.5F;
        }

        @Override
        public CrystalState createRenderState() {
            return new CrystalState();
        }

        @Override
        public void extractRenderState(EclipseCrystalEntity entity, CrystalState state, float partial) {
            super.extractRenderState(entity, state, partial);
            EclipseSovereignEntity boss = entity.boss();
            state.bossCore = boss == null ? null : boss.getPosition(partial).add(0, 3.4, 0).subtract(entity.getPosition(partial)).subtract(0, 1.1, 0);
            state.hits = entity.hitsLeft();
        }

        @Override
        public void submit(CrystalState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 1.6F, 0.0F);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -0.5F, 0.0F);
            this.model.setupAnim(state);
            collector.submitModel(this.model, state, poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                state.outlineColor, null);
            poseStack.popPose();
            if (state.bossCore != null) {
                poseStack.pushPose();
                poseStack.translate(0.0F, 1.1F, 0.0F);
                Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.bossCore, 0.18F, 0xFFC890FF, state.ageInTicks);
                poseStack.popPose();
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    // --- Projectiles & effects -----------------------------------------------------------------------------------

    public static class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorState> {
        private final BlockModelResolver blocks;

        public MeteorRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.blocks = ctx.getBlockModelResolver();
            this.shadowRadius = 0.0F;
        }

        @Override
        public MeteorState createRenderState() {
            return new MeteorState();
        }

        @Override
        protected boolean affectedByCulling(MeteorEntity entity) {
            return false;
        }

        @Override
        public void extractRenderState(MeteorEntity entity, MeteorState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.size = entity.size();
            state.hollow = entity.kind() == MeteorEntity.Kind.HOLLOW;
            this.blocks.update(state.rock, ModBlocks.METEORITE_ROCK.get().defaultBlockState(), BLOCK_CONTEXT);
        }

        @Override
        public void submit(MeteorState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float s = state.size * 1.4F;
            poseStack.pushPose();
            poseStack.translate(0.0F, s * 0.5F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 9.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * 13.0F));
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            state.rock.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
            // Burning corona.
            Beams.billboard(poseStack, collector, camera, tex("entity/meteor_glow"), 0.0F, s * 0.5F, 0.0F, s * 1.6F,
                state.hollow ? 0xFF9FF3FF : 0xFFFFB050, state.ageInTicks * 4.0F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class SingularityRenderer extends EntityRenderer<SingularityEntity, SingularityState> {
        private static final Identifier CORE = tex("entity/singularity_core");
        private static final Identifier DISK = tex("entity/singularity_disk");

        public SingularityRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public SingularityState createRenderState() {
            return new SingularityState();
        }

        @Override
        public void extractRenderState(SingularityEntity entity, SingularityState state, float partial) {
            super.extractRenderState(entity, state, partial);
            float age = entity.age() + partial;
            float grow = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
            float shrink = Mth.clamp((entity.lifetime() - age) / 8.0F, 0.0F, 1.0F);
            state.scale = (1.4F + entity.power() * 0.6F) * grow * shrink;
            state.spin = age * 6.0F;
        }

        @Override
        public void submit(SingularityState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float s = state.scale;
            if (s > 0.01F) {
                // Tilted accretion disk.
                poseStack.pushPose();
                poseStack.translate(0.0F, 0.6F, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(70.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin));
                Beams.quad(poseStack, collector, Beams.glow(DISK), s * 3.2F, 0xFFFFFFFF);
                poseStack.popPose();
                // Event horizon (always faces the camera).
                Beams.billboard(poseStack, collector, camera, CORE, 0.0F, 0.6F, 0.0F, s * 2.0F, 0xFFFFFFFF, -state.spin * 0.3F);
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class EclipseWaveRenderer extends EntityRenderer<EclipseWaveEntity, WaveState> {
        private static final Identifier TEXTURE = tex("entity/eclipse_wave");

        public EclipseWaveRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public WaveState createRenderState() {
            return new WaveState();
        }

        @Override
        public void extractRenderState(EclipseWaveEntity entity, WaveState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.yaw = entity.getYRot(partial);
            state.pitch = entity.getXRot(partial);
            state.fade = Mth.clamp((EclipseWaveEntity.LIFETIME - entity.life() - partial) / 8.0F, 0.0F, 1.0F);
        }

        @Override
        public void submit(WaveState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.3F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch - 90.0F));
            int alpha = (int) (255 * state.fade);
            Beams.quad(poseStack, collector, Beams.glow(TEXTURE), 3.4F, (alpha << 24) | 0xFFFFFF);
            poseStack.popPose();
            super.submit(state, poseStack, collector, camera);
        }
    }

    // --- Geometry helpers ---------------------------------------------------------------------------------------

    public static final class Beams {
        private Beams() {
        }

        private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v) {
            buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }

        /**
         * Render type for glowing effect sprites: unlit and unshaded, so a billboard is equally bright from every angle (the
         * entity emissive types shade each face by its normal, which dims camera-facing quads).
         */
        public static RenderType glow(Identifier texture) {
            return RenderTypes.eyes(texture);
        }

        /** A flat double-sided square of side {@code size} in the XY plane of the current pose. */
        public static void quad(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, float size, int color) {
            float h = size / 2.0F;
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                vertex(buffer, pose, -h, -h, 0, color, 0, 1);
                vertex(buffer, pose, h, -h, 0, color, 1, 1);
                vertex(buffer, pose, h, h, 0, color, 1, 0);
                vertex(buffer, pose, -h, h, 0, color, 0, 0);
                vertex(buffer, pose, -h, h, 0, color, 0, 0);
                vertex(buffer, pose, h, h, 0, color, 1, 0);
                vertex(buffer, pose, h, -h, 0, color, 1, 1);
                vertex(buffer, pose, -h, -h, 0, color, 0, 1);
            });
        }

        /** A camera-facing glowing sprite. */
        public static void billboard(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Identifier texture,
                                     float x, float y, float z, float size, int color, float roll) {
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(camera.orientation);
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            quad(poseStack, collector, glow(texture), size, color);
            poseStack.popPose();
        }

        /** A glowing beam from {@code from} to {@code to} (relative to the current pose), drawn as two crossed planes. */
        public static void beam(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, Vec3 from, Vec3 to, float width, int color, float age) {
            Vec3 delta = to.subtract(from);
            float length = (float) delta.length();
            if (length < 0.01F) {
                return;
            }
            Vec3 dir = delta.scale(1.0 / length);
            poseStack.pushPose();
            poseStack.translate(from.x, from.y, from.z);
            float yaw = (float) Math.atan2(dir.x, dir.z);
            float pitch = (float) Math.acos(Mth.clamp(dir.y, -1.0, 1.0));
            poseStack.mulPose(Axis.YP.rotation(yaw));
            poseStack.mulPose(Axis.XP.rotation(pitch));
            float h = width / 2.0F;
            float scroll = -age * 0.08F;
            float vEnd = scroll + length * 0.25F;
            RenderType type = glow(texture);
            for (int plane = 0; plane < 2; plane++) {
                final boolean xPlane = plane == 0;
                collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                    float ax = xPlane ? h : 0;
                    float az = xPlane ? 0 : h;
                    vertex(buffer, pose, -ax, 0, -az, color, 0, scroll);
                    vertex(buffer, pose, ax, 0, az, color, 1, scroll);
                    vertex(buffer, pose, ax, length, az, color, 1, vEnd);
                    vertex(buffer, pose, -ax, length, -az, color, 0, vEnd);
                    vertex(buffer, pose, -ax, length, -az, color, 0, vEnd);
                    vertex(buffer, pose, ax, length, az, color, 1, vEnd);
                    vertex(buffer, pose, ax, 0, az, color, 1, scroll);
                    vertex(buffer, pose, -ax, 0, -az, color, 0, scroll);
                });
            }
            poseStack.popPose();
        }
    }
}
