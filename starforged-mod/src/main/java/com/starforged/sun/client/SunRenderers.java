package com.starforged.sun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starforged.client.model.ModLayers;
import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import com.starforged.client.render.StarforgedRenderers;
import com.starforged.client.render.StarforgedRenderers.Beams;
import com.starforged.client.render.StarforgedRenderers.GlowLayer;
import com.starforged.sun.SunEntities;
import com.starforged.sun.boss.SolarPylonEntity;
import com.starforged.sun.boss.SunWardenEntity;
import com.starforged.sun.client.SunModels.AshenKnightModel;
import com.starforged.sun.client.SunModels.CinderImpModel;
import com.starforged.sun.client.SunModels.EmberHoundModel;
import com.starforged.sun.client.SunModels.MagmaCrawlerModel;
import com.starforged.sun.client.SunModels.SolarPhoenixModel;
import com.starforged.sun.client.SunModels.SolarPylonModel;
import com.starforged.sun.client.SunModels.SunWardenModel;
import com.starforged.sun.client.SunRenderStates.ChakramState;
import com.starforged.sun.client.SunRenderStates.FlareState;
import com.starforged.sun.client.SunRenderStates.HoundState;
import com.starforged.sun.client.SunRenderStates.ImpState;
import com.starforged.sun.client.SunRenderStates.KnightState;
import com.starforged.sun.client.SunRenderStates.OrbState;
import com.starforged.sun.client.SunRenderStates.PhoenixState;
import com.starforged.sun.client.SunRenderStates.PylonState;
import com.starforged.sun.client.SunRenderStates.WardenState;
import com.starforged.sun.entity.AshenKnightEntity;
import com.starforged.sun.entity.CinderChakramEntity;
import com.starforged.sun.entity.CinderImpEntity;
import com.starforged.sun.entity.EmberHoundEntity;
import com.starforged.sun.entity.HeliosOrbEntity;
import com.starforged.sun.entity.MagmaCrawlerEntity;
import com.starforged.sun.entity.SolarFlareEntity;
import com.starforged.sun.entity.SolarPhoenixEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Renderers for the Sunforged entities. */
public final class SunRenderers {
    private static final Identifier BEAM = StarforgedRenderers.tex("entity/solar_beam");
    private static final Identifier FLARE = StarforgedRenderers.tex("entity/solar_flare");

    private SunRenderers() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SunEntities.CINDER_IMP.get(), CinderImpRenderer::new);
        event.registerEntityRenderer(SunEntities.MAGMA_CRAWLER.get(), MagmaCrawlerRenderer::new);
        event.registerEntityRenderer(SunEntities.EMBER_HOUND.get(), EmberHoundRenderer::new);
        event.registerEntityRenderer(SunEntities.ASHEN_KNIGHT.get(), AshenKnightRenderer::new);
        event.registerEntityRenderer(SunEntities.SOLAR_PHOENIX.get(), SolarPhoenixRenderer::new);
        event.registerEntityRenderer(SunEntities.SUN_WARDEN.get(), SunWardenRenderer::new);
        event.registerEntityRenderer(SunEntities.SOLAR_PYLON.get(), SolarPylonRenderer::new);
        event.registerEntityRenderer(SunEntities.SOLAR_FLARE.get(), SolarFlareRenderer::new);
        event.registerEntityRenderer(SunEntities.CINDER_CHAKRAM.get(), CinderChakramRenderer::new);
        event.registerEntityRenderer(SunEntities.SUNBURST_FLASK.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
        event.registerEntityRenderer(SunEntities.HELIOS_ORB.get(), HeliosOrbRenderer::new);
    }

    private static void extract(net.minecraft.world.entity.LivingEntity entity, CreatureState state, float partial) {
        state.attackTime = entity.getAttackAnim(partial);
    }

    // --- Creatures --------------------------------------------------------------------------------------------

    public static class CinderImpRenderer extends MobRenderer<CinderImpEntity, ImpState, CinderImpModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/cinder_imp");

        public CinderImpRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CinderImpModel(ctx.bakeLayer(ModLayers.CINDER_IMP)), 0.3F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/cinder_imp_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(ImpState state) {
            return TEXTURE;
        }

        @Override
        public ImpState createRenderState() {
            return new ImpState();
        }

        @Override
        public void extractRenderState(CinderImpEntity entity, ImpState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.charging = entity.isCharging();
        }

        @Override
        protected int getBlockLightLevel(CinderImpEntity entity, BlockPos pos) {
            return 15;
        }
    }

    public static class MagmaCrawlerRenderer extends MobRenderer<MagmaCrawlerEntity, CreatureState, MagmaCrawlerModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/magma_crawler");

        public MagmaCrawlerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MagmaCrawlerModel(ctx.bakeLayer(ModLayers.MAGMA_CRAWLER)), 0.7F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/magma_crawler_glow"), s -> true));
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
        public void extractRenderState(MagmaCrawlerEntity entity, CreatureState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
        }

        @Override
        protected void scale(CreatureState state, PoseStack poseStack) {
            poseStack.scale(1.25F, 1.25F, 1.25F);
        }
    }

    public static class EmberHoundRenderer extends MobRenderer<EmberHoundEntity, HoundState, EmberHoundModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/ember_hound");

        public EmberHoundRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new EmberHoundModel(ctx.bakeLayer(ModLayers.EMBER_HOUND)), 0.5F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/ember_hound_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(HoundState state) {
            return TEXTURE;
        }

        @Override
        public HoundState createRenderState() {
            return new HoundState();
        }

        @Override
        public void extractRenderState(EmberHoundEntity entity, HoundState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.sitting = entity.isInSittingPose();
            state.tame = entity.isTame();
        }
    }

    public static class AshenKnightRenderer extends MobRenderer<AshenKnightEntity, KnightState, AshenKnightModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/ashen_knight");

        public AshenKnightRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new AshenKnightModel(ctx.bakeLayer(ModLayers.ASHEN_KNIGHT)), 0.6F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/ashen_knight_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(KnightState state) {
            return TEXTURE;
        }

        @Override
        public KnightState createRenderState() {
            return new KnightState();
        }

        @Override
        public void extractRenderState(AshenKnightEntity entity, KnightState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.guarding = entity.isGuarding();
            state.cleaveTicks = entity.cleaveTicks() > 0 ? entity.clientCleaveTicks + partial : 0.0F;
        }

        @Override
        protected void scale(KnightState state, PoseStack poseStack) {
            poseStack.scale(1.12F, 1.12F, 1.12F);
        }
    }

    public static class SolarPhoenixRenderer extends MobRenderer<SolarPhoenixEntity, PhoenixState, SolarPhoenixModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/solar_phoenix");

        public SolarPhoenixRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new SolarPhoenixModel(ctx.bakeLayer(ModLayers.SOLAR_PHOENIX)), 1.0F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/solar_phoenix_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(PhoenixState state) {
            return TEXTURE;
        }

        @Override
        public PhoenixState createRenderState() {
            return new PhoenixState();
        }

        @Override
        public void extractRenderState(SolarPhoenixEntity entity, PhoenixState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.ridden = entity.isVehicle();
            state.speed = (float) entity.getDeltaMovement().length();
        }

        @Override
        protected void scale(PhoenixState state, PoseStack poseStack) {
            poseStack.scale(1.5F, 1.5F, 1.5F);
        }

        @Override
        protected int getBlockLightLevel(SolarPhoenixEntity entity, BlockPos pos) {
            return 15;
        }
    }

    // --- The Sun Warden ----------------------------------------------------------------------------------------

    public static class SunWardenRenderer extends MobRenderer<SunWardenEntity, WardenState, SunWardenModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/sun_warden");

        public SunWardenRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new SunWardenModel(ctx.bakeLayer(ModLayers.SUN_WARDEN)), 2.2F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/sun_warden_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(WardenState state) {
            return TEXTURE;
        }

        @Override
        public WardenState createRenderState() {
            return new WardenState();
        }

        @Override
        protected AABB getBoundingBoxForCulling(SunWardenEntity entity) {
            return entity.getBoundingBox().inflate(6.0);
        }

        @Override
        public boolean shouldRender(SunWardenEntity entity, net.minecraft.client.renderer.culling.Frustum culler, double x, double y, double z) {
            return super.shouldRender(entity, culler, x, y, z) || entity.attack() == SunWardenEntity.ATTACK_BEAM;
        }

        @Override
        public void extractRenderState(SunWardenEntity entity, WardenState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.phase = entity.phase();
            state.attack = entity.attack();
            state.attackTicks = entity.clientAttackTicks + partial;
            state.rise = entity.riseTicks();
            state.dying = entity.deathTime > 0;
            state.nova = entity.novaTicks();
            if (entity.attack() == SunWardenEntity.ATTACK_BEAM && entity.clientAttackTicks >= SunWardenEntity.BEAM_CHARGE) {
                Vec3 end = entity.beamEnd();
                if (end.lengthSqr() > 0.01) {
                    Vec3 origin = entity.getPosition(partial);
                    state.beamStart = entity.haloPosition().subtract(entity.position());
                    state.beamEnd = end.subtract(origin);
                } else {
                    state.beamStart = null;
                }
            } else {
                state.beamStart = null;
            }
        }

        @Override
        protected void scale(WardenState state, PoseStack poseStack) {
            poseStack.scale(1.85F, 1.85F, 1.85F);
            if (state.dying) {
                poseStack.translate((Math.random() - 0.5) * 0.04, 0, (Math.random() - 0.5) * 0.04);
            }
        }

        @Override
        protected float getFlipDegrees() {
            return 0.0F;
        }

        @Override
        public void submit(WardenState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            if (state.beamStart != null && state.beamEnd != null) {
                float pulse = 1.0F + Mth.sin(state.ageInTicks * 1.3F) * 0.15F;
                Beams.beam(poseStack, collector, BEAM, state.beamStart, state.beamEnd, 1.3F * pulse, 0xFFFFB030, state.ageInTicks);
                Beams.beam(poseStack, collector, BEAM, state.beamStart, state.beamEnd, 0.5F * pulse, 0xFFFFFFE0, state.ageInTicks * 1.7F);
            }
            if (state.phase == SunWardenEntity.PHASE_SUPERNOVA) {
                // The gathering supernova: a swelling sun over the Warden's halo.
                float charge = 1.0F - state.nova / (float) SunWardenEntity.NOVA_TICKS;
                Beams.billboard(poseStack, collector, camera, FLARE, 0.0F, 5.7F, 0.0F, 3.0F + charge * 9.0F,
                    0xFFFFE080, state.ageInTicks * 2.0F);
            }
        }
    }

    public static class SolarPylonRenderer extends EntityRenderer<SolarPylonEntity, PylonState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/solar_pylon");
        private final SolarPylonModel model;

        public SolarPylonRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.model = new SolarPylonModel(ctx.bakeLayer(ModLayers.SOLAR_PYLON));
            this.shadowRadius = 0.7F;
        }

        @Override
        public PylonState createRenderState() {
            return new PylonState();
        }

        @Override
        public void extractRenderState(SolarPylonEntity entity, PylonState state, float partial) {
            super.extractRenderState(entity, state, partial);
            SunWardenEntity boss = entity.boss();
            state.bossHalo = boss == null ? null : boss.haloPosition().subtract(boss.position()).add(boss.getPosition(partial))
                .subtract(entity.getPosition(partial)).subtract(0, 3.6, 0);
            state.hits = entity.hitsLeft();
        }

        @Override
        public void submit(PylonState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.scale(-1.5F, -1.5F, 1.5F);
            poseStack.translate(0.0F, -1.5F, 0.0F);
            this.model.setupAnim(state);
            collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), StarforgedRenderers.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            if (state.bossHalo != null) {
                poseStack.pushPose();
                poseStack.translate(0.0F, 3.6F, 0.0F);
                Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.bossHalo, 0.25F, 0xFFFFC040, state.ageInTicks);
                poseStack.popPose();
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    // --- Projectiles & effects ----------------------------------------------------------------------------------

    public static class SolarFlareRenderer extends EntityRenderer<SolarFlareEntity, FlareState> {
        public SolarFlareRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.0F;
        }

        @Override
        public FlareState createRenderState() {
            return new FlareState();
        }

        @Override
        public void extractRenderState(SolarFlareEntity entity, FlareState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.size = entity.kind().size;
            state.kind = entity.kind().ordinal();
        }

        @Override
        public void submit(FlareState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float s = state.size * (1.0F + Mth.sin(state.ageInTicks * 0.8F) * 0.1F);
            int color = state.kind == SolarFlareEntity.Kind.PHOENIX.ordinal() ? 0xFFFF7A30 : 0xFFFFC060;
            Beams.billboard(poseStack, collector, camera, FLARE, 0.0F, state.size * 0.3F, 0.0F, s * 2.2F, color, state.ageInTicks * 6.0F);
            Beams.billboard(poseStack, collector, camera, FLARE, 0.0F, state.size * 0.3F, 0.0F, s * 1.1F, 0xFFFFFFF0, -state.ageInTicks * 9.0F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class CinderChakramRenderer extends EntityRenderer<CinderChakramEntity, ChakramState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("item/cinder_chakram");

        public CinderChakramRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ChakramState createRenderState() {
            return new ChakramState();
        }

        @Override
        public void extractRenderState(CinderChakramEntity entity, ChakramState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.spin = (entity.life() + partial) * 50.0F;
        }

        @Override
        public void submit(ChakramState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.15F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin));
            Beams.quad(poseStack, collector, RenderTypes.entityTranslucentEmissive(TEXTURE), 1.1F, 0xFFFFFFFF);
            poseStack.popPose();
            Beams.billboard(poseStack, collector, camera, FLARE, 0.0F, 0.15F, 0.0F, 1.4F, 0x88FF8A30, state.spin * 0.2F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class HeliosOrbRenderer extends EntityRenderer<HeliosOrbEntity, OrbState> {
        private static final Identifier ORB = StarforgedRenderers.tex("entity/helios_orb");

        public HeliosOrbRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public OrbState createRenderState() {
            return new OrbState();
        }

        @Override
        protected boolean affectedByCulling(HeliosOrbEntity entity) {
            return false;
        }

        @Override
        public void extractRenderState(HeliosOrbEntity entity, OrbState state, float partial) {
            super.extractRenderState(entity, state, partial);
            Entity target = entity.beamTarget();
            state.beamTarget = target == null ? null
                : target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0).subtract(entity.getPosition(partial)).subtract(0, 0.4, 0);
        }

        @Override
        public void submit(OrbState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.3F) * 0.08F;
            Beams.billboard(poseStack, collector, camera, FLARE, 0.0F, 0.4F, 0.0F, 2.4F * pulse, 0xFFFFB030, state.ageInTicks * 3.0F);
            Beams.billboard(poseStack, collector, camera, ORB, 0.0F, 0.4F, 0.0F, 1.0F * pulse, 0xFFFFFFFF, -state.ageInTicks * 2.0F);
            if (state.beamTarget != null) {
                poseStack.pushPose();
                poseStack.translate(0.0F, 0.4F, 0.0F);
                Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.beamTarget, 0.35F, 0xFFFFD060, state.ageInTicks);
                poseStack.popPose();
            }
            super.submit(state, poseStack, collector, camera);
        }
    }
}
