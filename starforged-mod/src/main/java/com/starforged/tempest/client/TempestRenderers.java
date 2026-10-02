package com.starforged.tempest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starforged.client.model.ModLayers;
import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import com.starforged.client.render.StarforgedRenderers;
import com.starforged.client.render.StarforgedRenderers.Beams;
import com.starforged.client.render.StarforgedRenderers.GlowLayer;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.boss.StormConductorEntity;
import com.starforged.tempest.boss.StormEyeEntity;
import com.starforged.tempest.boss.VeyrEntity;
import com.starforged.tempest.client.TempestModels.ShardwingModel;
import com.starforged.tempest.client.TempestModels.StaticWispModel;
import com.starforged.tempest.client.TempestModels.StormConductorModel;
import com.starforged.tempest.client.TempestModels.StormRocModel;
import com.starforged.tempest.client.TempestModels.StormboundModel;
import com.starforged.tempest.client.TempestModels.ThunderjawModel;
import com.starforged.tempest.client.TempestModels.VeyrModel;
import com.starforged.tempest.client.TempestModels.ZephyrSpriteModel;
import com.starforged.tempest.client.TempestRenderStates.BeamState;
import com.starforged.tempest.client.TempestRenderStates.BeastState;
import com.starforged.tempest.client.TempestRenderStates.ComboState;
import com.starforged.tempest.client.TempestRenderStates.ConductorState;
import com.starforged.tempest.client.TempestRenderStates.CycloneState;
import com.starforged.tempest.client.TempestRenderStates.DiveState;
import com.starforged.tempest.client.TempestRenderStates.EyeState;
import com.starforged.tempest.client.TempestRenderStates.HookState;
import com.starforged.tempest.client.TempestRenderStates.RocState;
import com.starforged.tempest.client.TempestRenderStates.SpearState;
import com.starforged.tempest.client.TempestRenderStates.SpriteState;
import com.starforged.tempest.client.TempestRenderStates.VeyrState;
import com.starforged.tempest.entity.ArcBeamEntity;
import com.starforged.tempest.entity.CycloneEntity;
import com.starforged.tempest.entity.ShardwingEntity;
import com.starforged.tempest.entity.StaticWispEntity;
import com.starforged.tempest.entity.StormRocEntity;
import com.starforged.tempest.entity.StormShardEntity;
import com.starforged.tempest.entity.StormboundEntity;
import com.starforged.tempest.entity.StormhookEntity;
import com.starforged.tempest.entity.TempestJavelinEntity;
import com.starforged.tempest.entity.ThunderjawAlphaEntity;
import com.starforged.tempest.entity.ThunderjawEntity;
import com.starforged.tempest.entity.ZephyrSpriteEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Renderers for the Tempestforged entities. */
public final class TempestRenderers {
    private static final Identifier BEAM = StarforgedRenderers.tex("entity/solar_beam");
    private static final Identifier GLOW = StarforgedRenderers.tex("entity/solar_flare");
    private static final Identifier CYCLONE = StarforgedRenderers.tex("entity/cyclone");
    private static final Identifier EYE = StarforgedRenderers.tex("entity/storm_eye");
    private static final int STORM_BLUE = 0xFF9CD8FF;

    private TempestRenderers() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TempestEntities.STATIC_WISP.get(), StaticWispRenderer::new);
        event.registerEntityRenderer(TempestEntities.SHARDWING.get(), ShardwingRenderer::new);
        event.registerEntityRenderer(TempestEntities.STORMBOUND.get(), StormboundRenderer::new);
        event.registerEntityRenderer(TempestEntities.THUNDERJAW.get(), ctx -> new ThunderjawRenderer<>(ctx, false));
        event.registerEntityRenderer(TempestEntities.THUNDERJAW_ALPHA.get(), ctx -> new ThunderjawRenderer<>(ctx, true));
        event.registerEntityRenderer(TempestEntities.ZEPHYR_SPRITE.get(), ZephyrSpriteRenderer::new);
        event.registerEntityRenderer(TempestEntities.STORM_ROC.get(), StormRocRenderer::new);
        event.registerEntityRenderer(TempestEntities.VEYR.get(), VeyrRenderer::new);
        event.registerEntityRenderer(TempestEntities.STORM_CONDUCTOR.get(), StormConductorRenderer::new);
        event.registerEntityRenderer(TempestEntities.STORM_EYE.get(), StormEyeRenderer::new);
        event.registerEntityRenderer(TempestEntities.CYCLONE.get(), CycloneRenderer::new);
        event.registerEntityRenderer(TempestEntities.TEMPEST_JAVELIN.get(), JavelinRenderer::new);
        event.registerEntityRenderer(TempestEntities.STORMHOOK.get(), StormhookRenderer::new);
        event.registerEntityRenderer(TempestEntities.STORM_SHARD.get(), StormShardRenderer::new);
        event.registerEntityRenderer(TempestEntities.ARC_BEAM.get(), ArcBeamRenderer::new);
    }

    private static void extract(LivingEntity entity, CreatureState state, float partial) {
        state.attackTime = entity.getAttackAnim(partial);
    }

    // --- Creatures -------------------------------------------------------------------------------------------------

    public static class StaticWispRenderer extends MobRenderer<StaticWispEntity, CreatureState, StaticWispModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/static_wisp");

        public StaticWispRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new StaticWispModel(ctx.bakeLayer(ModLayers.STATIC_WISP)), 0.3F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/static_wisp_glow"), s -> true));
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
        public void extractRenderState(StaticWispEntity entity, CreatureState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
        }

        @Override
        protected int getBlockLightLevel(StaticWispEntity entity, BlockPos pos) {
            return 15;
        }

        @Override
        public void submit(CreatureState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 0.6F, 0.0F, 1.4F, 0x889CD8FF, state.ageInTicks * 3.0F);
        }
    }

    public static class ShardwingRenderer extends MobRenderer<ShardwingEntity, DiveState, ShardwingModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/shardwing");

        public ShardwingRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new ShardwingModel(ctx.bakeLayer(ModLayers.SHARDWING)), 0.6F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/shardwing_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(DiveState state) {
            return TEXTURE;
        }

        @Override
        public DiveState createRenderState() {
            return new DiveState();
        }

        @Override
        public void extractRenderState(ShardwingEntity entity, DiveState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.diving = entity.isDiving();
        }

        @Override
        protected void scale(DiveState state, PoseStack poseStack) {
            poseStack.scale(1.2F, 1.2F, 1.2F);
        }
    }

    public static class StormboundRenderer extends MobRenderer<StormboundEntity, ComboState, StormboundModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/stormbound");

        public StormboundRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new StormboundModel(ctx.bakeLayer(ModLayers.STORMBOUND)), 0.6F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/stormbound_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(ComboState state) {
            return TEXTURE;
        }

        @Override
        public ComboState createRenderState() {
            return new ComboState();
        }

        @Override
        public void extractRenderState(StormboundEntity entity, ComboState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.combo = entity.combo();
        }

        @Override
        protected void scale(ComboState state, PoseStack poseStack) {
            poseStack.scale(1.2F, 1.2F, 1.2F);
        }
    }

    public static class ThunderjawRenderer<T extends ThunderjawEntity> extends MobRenderer<T, BeastState, ThunderjawModel> {
        private final Identifier texture;
        private final float scale;

        public ThunderjawRenderer(EntityRendererProvider.Context ctx, boolean alpha) {
            super(ctx, new ThunderjawModel(ctx.bakeLayer(alpha ? ModLayers.THUNDERJAW_ALPHA : ModLayers.THUNDERJAW)), alpha ? 1.8F : 1.2F);
            String name = alpha ? "thunderjaw_alpha" : "thunderjaw";
            this.texture = StarforgedRenderers.tex("entity/" + name);
            this.scale = alpha ? 2.1F : 1.4F;
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/" + name + "_glow"), s -> alpha || s.charged));
        }

        @Override
        public Identifier getTextureLocation(BeastState state) {
            return this.texture;
        }

        @Override
        public BeastState createRenderState() {
            return new BeastState();
        }

        @Override
        public void extractRenderState(T entity, BeastState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.stamp = entity.stamp();
            state.charged = entity.isCharged();
            state.rushing = entity.isRushing();
        }

        @Override
        protected void scale(BeastState state, PoseStack poseStack) {
            poseStack.scale(this.scale, this.scale, this.scale);
        }

        @Override
        public void submit(BeastState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            if (state.charged) {
                float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.8F) * 0.2F;
                Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, state.boundingBoxHeight * 0.8F, 0.0F, state.boundingBoxWidth * 1.4F * pulse,
                    0x669CD8FF, state.ageInTicks * 5.0F);
            }
        }
    }

    public static class ZephyrSpriteRenderer extends MobRenderer<ZephyrSpriteEntity, SpriteState, ZephyrSpriteModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/zephyr_sprite");

        public ZephyrSpriteRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new ZephyrSpriteModel(ctx.bakeLayer(ModLayers.ZEPHYR_SPRITE)), 0.25F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/zephyr_sprite_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(SpriteState state) {
            return TEXTURE;
        }

        @Override
        public SpriteState createRenderState() {
            return new SpriteState();
        }

        @Override
        public void extractRenderState(ZephyrSpriteEntity entity, SpriteState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.sitting = entity.isInSittingPose();
        }
    }

    public static class StormRocRenderer extends MobRenderer<StormRocEntity, RocState, StormRocModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/storm_roc");

        public StormRocRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new StormRocModel(ctx.bakeLayer(ModLayers.STORM_ROC)), 1.0F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/storm_roc_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(RocState state) {
            return TEXTURE;
        }

        @Override
        public RocState createRenderState() {
            return new RocState();
        }

        @Override
        public void extractRenderState(StormRocEntity entity, RocState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.flying = entity.isFlying();
            state.ridden = entity.isVehicle();
        }

        @Override
        protected void scale(RocState state, PoseStack poseStack) {
            poseStack.scale(1.1F, 1.1F, 1.1F);
        }
    }

    // --- Veyr ------------------------------------------------------------------------------------------------------

    public static class VeyrRenderer extends MobRenderer<VeyrEntity, VeyrState, VeyrModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/veyr");

        public VeyrRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new VeyrModel(ctx.bakeLayer(ModLayers.VEYR)), 1.2F);
            this.shadowStrength = 1.0F;
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/veyr_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(VeyrState state) {
            return TEXTURE;
        }

        @Override
        public VeyrState createRenderState() {
            return new VeyrState();
        }

        @Override
        protected AABB getBoundingBoxForCulling(VeyrEntity entity) {
            return entity.getBoundingBox().inflate(4.0);
        }

        @Override
        public void extractRenderState(VeyrEntity entity, VeyrState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.phase = entity.phase();
            state.attack = entity.attack();
            state.attackTicks = entity.clientAttackTicks + partial;
            state.assemble = state.phase == VeyrEntity.PHASE_ASSEMBLE
                ? Math.min(1.0F, (entity.assembleTicks() + partial) / VeyrEntity.ASSEMBLE_TICKS) : 1.0F;
            state.shield = entity.shield();
            state.dying = entity.deathTime > 0;
        }

        @Override
        protected void scale(VeyrState state, PoseStack poseStack) {
            poseStack.scale(1.35F, 1.35F, 1.35F);
            if (state.dying) {
                poseStack.translate((Math.random() - 0.5) * 0.04, 0, (Math.random() - 0.5) * 0.04);
            }
        }

        @Override
        protected float getFlipDegrees() {
            return 0.0F;
        }

        @Override
        protected int getBlockLightLevel(VeyrEntity entity, BlockPos pos) {
            return 12;
        }

        @Override
        public void submit(VeyrState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            if (state.phase == VeyrEntity.PHASE_CONDUCTION && state.shield > 0) {
                // The storm shield: one layer per hit it can still take.
                for (int i = 0; i < state.shield; i++) {
                    float size = 4.6F + i * 0.5F;
                    Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 2.0F, 0.0F, size, 0x449CD8FF, state.ageInTicks * (2.0F + i));
                }
            }
            if (state.attack == VeyrEntity.ATTACK_LANCE && state.attackTicks < VeyrEntity.LANCE_CHARGE) {
                float c = state.attackTicks / VeyrEntity.LANCE_CHARGE;
                Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 4.8F, 0.0F, 0.5F + c * 1.8F, 0xFFBFE8FF, state.ageInTicks * 12.0F);
            }
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 2.6F, 0.0F, 1.6F * state.assemble, 0x669CD8FF, state.ageInTicks * 1.5F);
        }
    }

    public static class StormConductorRenderer extends EntityRenderer<StormConductorEntity, ConductorState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/storm_conductor");
        private final StormConductorModel model;

        public StormConductorRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.model = new StormConductorModel(ctx.bakeLayer(ModLayers.STORM_CONDUCTOR));
            this.shadowRadius = 0.6F;
        }

        @Override
        public ConductorState createRenderState() {
            return new ConductorState();
        }

        @Override
        public void extractRenderState(StormConductorEntity entity, ConductorState state, float partial) {
            super.extractRenderState(entity, state, partial);
            int raised = entity.raised();
            // Rises over the first few ticks, sinks over the last few.
            int up = StormConductorEntity.RAISE_TICKS - raised;
            state.raised = raised <= 0 ? 0.0F : Math.min(1.0F, Math.min(up + partial, raised - partial) / 8.0F);
        }

        @Override
        public void submit(ConductorState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.scale(-1.25F, -1.25F, 1.25F);
            poseStack.translate(0.0F, -1.5F, 0.0F);
            this.model.setupAnim(state);
            collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), StarforgedRenderers.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            float top = 0.45F + 2.55F * state.raised;
            float pulse = state.raised > 0.0F ? 1.0F + Mth.sin(state.ageInTicks * 0.9F) * 0.25F : 0.6F;
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, top, 0.0F, (state.raised > 0.0F ? 1.6F : 0.7F) * pulse,
                state.raised > 0.0F ? 0xFFBFE8FF : 0x665A7AA0, state.ageInTicks * 6.0F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    /** An Eye of the Storm: a calm ring on the ground with a pale column of air rising out of it. */
    public static class StormEyeRenderer extends EntityRenderer<StormEyeEntity, EyeState> {
        public StormEyeRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected boolean affectedByCulling(StormEyeEntity entity) {
            return false;
        }

        @Override
        public EyeState createRenderState() {
            return new EyeState();
        }

        @Override
        public void submit(EyeState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float size = (float) StormEyeEntity.RADIUS * 2.0F;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.08F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * 2.0F));
            Beams.quad(poseStack, collector, Beams.glow(EYE), size, 0xFFFFFFFF);
            poseStack.popPose();
            Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, new Vec3(0, 24, 0), size * 0.9F, 0x339CD8FF, state.ageInTicks);
            super.submit(state, poseStack, collector, camera);
        }
    }

    // --- Projectiles & effects -------------------------------------------------------------------------------------

    /** A cyclone: a stack of spinning wind rings that widens toward the top. */
    public static class CycloneRenderer extends EntityRenderer<CycloneEntity, CycloneState> {
        public CycloneRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public CycloneState createRenderState() {
            return new CycloneState();
        }

        @Override
        public void extractRenderState(CycloneEntity entity, CycloneState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.size = entity.size();
            state.spin = (entity.life() + partial) * 28.0F;
        }

        @Override
        public void submit(CycloneState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float s = state.size;
            for (int i = 0; i < 7; i++) {
                float h = i / 6.0F;
                poseStack.pushPose();
                poseStack.translate(Mth.sin(state.ageInTicks * 0.3F + i) * 0.08F * s, h * 2.6F * s, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin * (1.0F + h * 0.4F) + i * 40.0F));
                int alpha = (int) (200 - h * 90);
                Beams.quad(poseStack, collector, Beams.glow(CYCLONE), (0.7F + h * 1.6F) * s, (alpha << 24) | 0xDDEEFF);
                poseStack.popPose();
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class JavelinRenderer extends EntityRenderer<TempestJavelinEntity, SpearState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("item/tempest_javelin");

        public JavelinRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public SpearState createRenderState() {
            return new SpearState();
        }

        @Override
        public void extractRenderState(TempestJavelinEntity entity, SpearState state, float partial) {
            super.extractRenderState(entity, state, partial);
            Vec3 motion = entity.getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-4) {
                state.yaw = (float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG);
                state.pitch = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG);
            } else {
                state.yaw = entity.getYRot();
                state.pitch = entity.getXRot();
            }
            state.mode = entity.state();
        }

        @Override
        public void submit(SpearState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            for (int i = 0; i < 2; i++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(i * 90.0F));
                Beams.quad(poseStack, collector, RenderTypes.entityCutout(TEXTURE), 2.2F, 0xFFFFFFFF);
                poseStack.popPose();
            }
            poseStack.popPose();
            if (state.mode == TempestJavelinEntity.STUCK) {
                float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.7F) * 0.3F;
                Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 0.6F, 0.0F, 1.4F * pulse, 0xAA9CD8FF, state.ageInTicks * 8.0F);
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class StormhookRenderer extends EntityRenderer<StormhookEntity, HookState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("item/stormhook");

        public StormhookRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected boolean affectedByCulling(StormhookEntity entity) {
            return false;
        }

        @Override
        public HookState createRenderState() {
            return new HookState();
        }

        @Override
        public void extractRenderState(StormhookEntity entity, HookState state, float partial) {
            super.extractRenderState(entity, state, partial);
            Entity owner = entity.getOwner();
            state.ownerHand = owner == null ? null
                : owner.getPosition(partial).add(0, owner.getBbHeight() * 0.6, 0).subtract(entity.getPosition(partial));
            state.anchored = entity.isAnchored();
        }

        @Override
        public void submit(HookState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            Beams.billboard(poseStack, collector, camera, TEXTURE, 0.0F, 0.0F, 0.0F, 0.6F, 0xFFFFFFFF, 0.0F);
            if (state.ownerHand != null) {
                Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.ownerHand, 0.1F, STORM_BLUE, state.ageInTicks * 3.0F);
                if (state.anchored) {
                    Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.ownerHand, 0.03F, 0xFFFFFFFF, state.ageInTicks * 5.0F);
                }
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class StormShardRenderer extends EntityRenderer<StormShardEntity, EntityRenderState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("item/shardwing_crystal");

        public StormShardRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }

        @Override
        public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 0.1F, 0.0F, 0.9F, 0x889CD8FF, state.ageInTicks * 10.0F);
            Beams.billboard(poseStack, collector, camera, TEXTURE, 0.0F, 0.1F, 0.0F, 0.6F, 0xFFFFFFFF, state.ageInTicks * 30.0F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class ArcBeamRenderer extends EntityRenderer<ArcBeamEntity, BeamState> {
        public ArcBeamRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected boolean affectedByCulling(ArcBeamEntity entity) {
            return false;
        }

        @Override
        public BeamState createRenderState() {
            return new BeamState();
        }

        @Override
        public void extractRenderState(ArcBeamEntity entity, BeamState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.end = entity.end();
            state.width = entity.width();
            state.fade = Mth.clamp(1.0F - (entity.tickCount + partial) / ArcBeamEntity.LIFE, 0.0F, 1.0F);
        }

        @Override
        public void submit(BeamState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            int alpha = (int) (255 * state.fade);
            float w = state.width * (0.6F + state.fade * 0.4F);
            Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.end, w * 2.0F, (alpha << 24) | 0x5AA0FF, state.ageInTicks * 4.0F);
            Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.end, w * 0.7F, (alpha << 24) | 0xF0F8FF, state.ageInTicks * 6.0F);
            super.submit(state, poseStack, collector, camera);
        }
    }
}
