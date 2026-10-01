package com.starforged.moon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starforged.client.model.ModLayers;
import com.starforged.client.render.StarforgedRenderStates.CreatureState;
import com.starforged.client.render.StarforgedRenderers;
import com.starforged.client.render.StarforgedRenderers.Beams;
import com.starforged.client.render.StarforgedRenderers.GlowLayer;
import com.starforged.moon.MoonEntities;
import com.starforged.moon.boss.LunarAnchorEntity;
import com.starforged.moon.boss.MatriarchEchoEntity;
import com.starforged.moon.boss.PaleMatriarchEntity;
import com.starforged.moon.client.MoonModels.LunarAnchorModel;
import com.starforged.moon.client.MoonModels.LunarMothModel;
import com.starforged.moon.client.MoonModels.MoonkitModel;
import com.starforged.moon.client.MoonModels.MoonleaperModel;
import com.starforged.moon.client.MoonModels.PaleMatriarchModel;
import com.starforged.moon.client.MoonModels.RegolithSkimmerModel;
import com.starforged.moon.client.MoonModels.SeleniteSentinelModel;
import com.starforged.moon.client.MoonModels.UmbralLurkerModel;
import com.starforged.moon.client.MoonRenderStates.AnchorState;
import com.starforged.moon.client.MoonRenderStates.BoltState;
import com.starforged.moon.client.MoonRenderStates.FallingMoonState;
import com.starforged.moon.client.MoonRenderStates.HookState;
import com.starforged.moon.client.MoonRenderStates.KitState;
import com.starforged.moon.client.MoonRenderStates.LeaperState;
import com.starforged.moon.client.MoonRenderStates.LurkerState;
import com.starforged.moon.client.MoonRenderStates.MatriarchState;
import com.starforged.moon.client.MoonRenderStates.MoonletState;
import com.starforged.moon.client.MoonRenderStates.SentinelState;
import com.starforged.moon.client.MoonRenderStates.SkimmerState;
import com.starforged.moon.client.MoonRenderStates.SpinState;
import com.starforged.moon.entity.CrescentGlaiveEntity;
import com.starforged.moon.entity.FallingMoonEntity;
import com.starforged.moon.entity.LunarMothEntity;
import com.starforged.moon.entity.MoonkitEntity;
import com.starforged.moon.entity.MoonleaperEntity;
import com.starforged.moon.entity.MoonletEntity;
import com.starforged.moon.entity.MoonshotBoltEntity;
import com.starforged.moon.entity.RegolithSkimmerEntity;
import com.starforged.moon.entity.SeleniteSentinelEntity;
import com.starforged.moon.entity.TetherHookEntity;
import com.starforged.moon.entity.TideWaveEntity;
import com.starforged.moon.entity.UmbralLurkerEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Renderers for the Moonforged entities. */
public final class MoonRenderers {
    private static final Identifier BEAM = StarforgedRenderers.tex("entity/solar_beam");
    private static final Identifier MOONLET = StarforgedRenderers.tex("entity/moonlet");
    private static final Identifier CRESCENT = StarforgedRenderers.tex("entity/crescent");
    private static final Identifier FALLING_MOON = StarforgedRenderers.tex("entity/falling_moon");
    private static final Identifier TIDE = StarforgedRenderers.tex("entity/tide_wave");
    private static final Identifier GLOW = StarforgedRenderers.tex("entity/solar_flare");

    private MoonRenderers() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MoonEntities.REGOLITH_SKIMMER.get(), RegolithSkimmerRenderer::new);
        event.registerEntityRenderer(MoonEntities.LUNAR_MOTH.get(), LunarMothRenderer::new);
        event.registerEntityRenderer(MoonEntities.SELENITE_SENTINEL.get(), SeleniteSentinelRenderer::new);
        event.registerEntityRenderer(MoonEntities.UMBRAL_LURKER.get(), UmbralLurkerRenderer::new);
        event.registerEntityRenderer(MoonEntities.MOONKIT.get(), MoonkitRenderer::new);
        event.registerEntityRenderer(MoonEntities.MOONLEAPER.get(), MoonleaperRenderer::new);
        event.registerEntityRenderer(MoonEntities.PALE_MATRIARCH.get(), ctx -> new MatriarchRenderer<>(ctx, 2.2F));
        event.registerEntityRenderer(MoonEntities.MATRIARCH_ECHO.get(), ctx -> new MatriarchRenderer<>(ctx, 0.0F));
        event.registerEntityRenderer(MoonEntities.LUNAR_ANCHOR.get(), LunarAnchorRenderer::new);
        event.registerEntityRenderer(MoonEntities.FALLING_MOON.get(), FallingMoonRenderer::new);
        event.registerEntityRenderer(MoonEntities.MOONLET.get(), MoonletRenderer::new);
        event.registerEntityRenderer(MoonEntities.CRESCENT_GLAIVE.get(), CrescentGlaiveRenderer::new);
        event.registerEntityRenderer(MoonEntities.MOONSHOT_BOLT.get(), MoonshotBoltRenderer::new);
        event.registerEntityRenderer(MoonEntities.TETHER_HOOK.get(), TetherHookRenderer::new);
        event.registerEntityRenderer(MoonEntities.TIDE_WAVE.get(), TideWaveRenderer::new);
    }

    private static void extract(LivingEntity entity, CreatureState state, float partial) {
        state.attackTime = entity.getAttackAnim(partial);
    }

    // --- Creatures --------------------------------------------------------------------------------------------

    public static class RegolithSkimmerRenderer extends MobRenderer<RegolithSkimmerEntity, SkimmerState, RegolithSkimmerModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/regolith_skimmer");

        public RegolithSkimmerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new RegolithSkimmerModel(ctx.bakeLayer(ModLayers.REGOLITH_SKIMMER)), 0.7F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/regolith_skimmer_glow"), s -> s.emerge > 0.05F));
        }

        @Override
        public Identifier getTextureLocation(SkimmerState state) {
            return TEXTURE;
        }

        @Override
        public SkimmerState createRenderState() {
            return new SkimmerState();
        }

        @Override
        public void extractRenderState(RegolithSkimmerEntity entity, SkimmerState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.emerge = entity.clientEmerge;
            this.shadowRadius = 0.7F * entity.clientEmerge;
        }

        @Override
        protected void scale(SkimmerState state, PoseStack poseStack) {
            poseStack.scale(1.3F, 1.3F, 1.3F);
            poseStack.translate(0.0F, (1.0F - state.emerge) * 0.45F, 0.0F);
        }
    }

    public static class LunarMothRenderer extends MobRenderer<LunarMothEntity, CreatureState, LunarMothModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/lunar_moth");

        public LunarMothRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new LunarMothModel(ctx.bakeLayer(ModLayers.LUNAR_MOTH)), 0.4F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/lunar_moth_glow"), s -> true));
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
        public void extractRenderState(LunarMothEntity entity, CreatureState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
        }

        @Override
        protected void scale(CreatureState state, PoseStack poseStack) {
            poseStack.scale(1.2F, 1.2F, 1.2F);
        }
    }

    public static class SeleniteSentinelRenderer extends MobRenderer<SeleniteSentinelEntity, SentinelState, SeleniteSentinelModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/selenite_sentinel");

        public SeleniteSentinelRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new SeleniteSentinelModel(ctx.bakeLayer(ModLayers.SELENITE_SENTINEL)), 0.8F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/selenite_sentinel_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(SentinelState state) {
            return TEXTURE;
        }

        @Override
        public SentinelState createRenderState() {
            return new SentinelState();
        }

        @Override
        public void extractRenderState(SeleniteSentinelEntity entity, SentinelState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.charge = entity.charge();
        }

        @Override
        protected void scale(SentinelState state, PoseStack poseStack) {
            poseStack.scale(1.05F, 1.05F, 1.05F);
        }
    }

    public static class UmbralLurkerRenderer extends MobRenderer<UmbralLurkerEntity, LurkerState, UmbralLurkerModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/umbral_lurker");

        public UmbralLurkerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new UmbralLurkerModel(ctx.bakeLayer(ModLayers.UMBRAL_LURKER)), 0.4F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/umbral_lurker_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(LurkerState state) {
            return TEXTURE;
        }

        @Override
        public LurkerState createRenderState() {
            return new LurkerState();
        }

        @Override
        public void extractRenderState(UmbralLurkerEntity entity, LurkerState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.watched = entity.isWatched();
            if (!state.watched) {
                state.frozenWalk = state.walkAnimationPos;
            }
        }
    }

    public static class MoonkitRenderer extends MobRenderer<MoonkitEntity, KitState, MoonkitModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/moonkit");

        public MoonkitRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MoonkitModel(ctx.bakeLayer(ModLayers.MOONKIT)), 0.35F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/moonkit_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(KitState state) {
            return TEXTURE;
        }

        @Override
        public KitState createRenderState() {
            return new KitState();
        }

        @Override
        public void extractRenderState(MoonkitEntity entity, KitState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.sitting = entity.isInSittingPose();
        }
    }

    public static class MoonleaperRenderer extends MobRenderer<MoonleaperEntity, LeaperState, MoonleaperModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/moonleaper");

        public MoonleaperRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MoonleaperModel(ctx.bakeLayer(ModLayers.MOONLEAPER)), 0.8F);
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/moonleaper_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(LeaperState state) {
            return TEXTURE;
        }

        @Override
        public LeaperState createRenderState() {
            return new LeaperState();
        }

        @Override
        public void extractRenderState(MoonleaperEntity entity, LeaperState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.charge = entity.leapCharge();
            state.airborne = !entity.onGround();
            state.lean = entity.bodyLean();
        }

        @Override
        protected void scale(LeaperState state, PoseStack poseStack) {
            poseStack.scale(1.25F, 1.25F, 1.25F);
        }
    }

    // --- The Pale Matriarch (and her echoes, which cast no shadow) ---------------------------------------------

    public static class MatriarchRenderer<T extends Mob> extends MobRenderer<T, MatriarchState, PaleMatriarchModel> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/pale_matriarch");

        public MatriarchRenderer(EntityRendererProvider.Context ctx, float shadow) {
            super(ctx, new PaleMatriarchModel(ctx.bakeLayer(ModLayers.PALE_MATRIARCH)), shadow);
            this.shadowStrength = 1.0F;
            this.addLayer(new GlowLayer<>(this, StarforgedRenderers.tex("entity/pale_matriarch_glow"), s -> true));
        }

        @Override
        public Identifier getTextureLocation(MatriarchState state) {
            return TEXTURE;
        }

        @Override
        public MatriarchState createRenderState() {
            return new MatriarchState();
        }

        @Override
        protected AABB getBoundingBoxForCulling(T entity) {
            return entity.getBoundingBox().inflate(6.0);
        }

        @Override
        public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum culler, double x, double y, double z) {
            return super.shouldRender(entity, culler, x, y, z)
                || (entity instanceof PaleMatriarchEntity boss && boss.attack() == PaleMatriarchEntity.ATTACK_LANCE);
        }

        @Override
        public void extractRenderState(T entity, MatriarchState state, float partial) {
            super.extractRenderState(entity, state, partial);
            extract(entity, state, partial);
            state.beamStart = null;
            state.echo = entity instanceof MatriarchEchoEntity;
            if (entity instanceof PaleMatriarchEntity boss) {
                state.phase = boss.phase();
                state.attack = boss.attack();
                state.attackTicks = boss.clientAttackTicks + partial;
                state.emerge = Math.min(1.0F, (boss.emergeTicks() + partial) / PaleMatriarchEntity.EMERGE_TICKS);
                if (state.phase != PaleMatriarchEntity.PHASE_EMERGE) {
                    state.emerge = 1.0F;
                }
                state.dying = boss.deathTime > 0;
                if (boss.attack() == PaleMatriarchEntity.ATTACK_LANCE && boss.clientAttackTicks >= PaleMatriarchEntity.LANCE_CHARGE) {
                    Vec3 end = boss.beamEnd();
                    if (end.lengthSqr() > 0.01) {
                        state.beamStart = boss.corePosition().subtract(boss.position());
                        state.beamEnd = end.subtract(boss.getPosition(partial));
                    }
                }
            } else {
                state.phase = PaleMatriarchEntity.PHASE_NEW_MOON;
                state.attack = PaleMatriarchEntity.ATTACK_NONE;
                state.emerge = 1.0F;
                state.dying = false;
            }
        }

        @Override
        protected void scale(MatriarchState state, PoseStack poseStack) {
            float s = 1.6F * (0.3F + 0.7F * state.emerge);
            poseStack.scale(s, s, s);
            if (state.dying) {
                poseStack.translate((Math.random() - 0.5) * 0.04, 0, (Math.random() - 0.5) * 0.04);
            }
        }

        @Override
        protected float getFlipDegrees() {
            return 0.0F;
        }

        @Override
        protected int getBlockLightLevel(T entity, BlockPos pos) {
            return 12;
        }

        @Override
        public void submit(MatriarchState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(state, poseStack, collector, camera);
            if (state.beamStart != null && state.beamEnd != null) {
                float pulse = 1.0F + Mth.sin(state.ageInTicks * 1.1F) * 0.15F;
                Beams.beam(poseStack, collector, BEAM, state.beamStart, state.beamEnd, 1.4F * pulse, 0xFF6AA8FF, state.ageInTicks);
                Beams.beam(poseStack, collector, BEAM, state.beamStart, state.beamEnd, 0.5F * pulse, 0xFFF0F8FF, state.ageInTicks * 1.7F);
            }
            if (!state.echo) {
                Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 3.0F, 0.0F, 2.6F, 0x889CC8FF, state.ageInTicks * 1.5F);
            }
        }
    }

    public static class LunarAnchorRenderer extends EntityRenderer<LunarAnchorEntity, AnchorState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("entity/lunar_anchor");
        private final LunarAnchorModel model;

        public LunarAnchorRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.model = new LunarAnchorModel(ctx.bakeLayer(ModLayers.LUNAR_ANCHOR));
            this.shadowRadius = 0.7F;
        }

        @Override
        public AnchorState createRenderState() {
            return new AnchorState();
        }

        @Override
        public void extractRenderState(LunarAnchorEntity entity, AnchorState state, float partial) {
            super.extractRenderState(entity, state, partial);
            PaleMatriarchEntity boss = entity.boss();
            state.bossCore = boss == null ? null : boss.corePosition().subtract(boss.position()).add(boss.getPosition(partial))
                .subtract(entity.getPosition(partial)).subtract(0, 3.4, 0);
            state.hits = entity.hitsLeft();
        }

        @Override
        public void submit(AnchorState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.scale(-1.4F, -1.4F, 1.4F);
            poseStack.translate(0.0F, -1.5F, 0.0F);
            this.model.setupAnim(state);
            collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), StarforgedRenderers.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            poseStack.popPose();
            if (state.bossCore != null) {
                poseStack.pushPose();
                poseStack.translate(0.0F, 3.4F, 0.0F);
                Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.bossCore, 0.3F, 0xFF9CC8FF, state.ageInTicks);
                poseStack.popPose();
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    // --- Projectiles & effects ----------------------------------------------------------------------------------

    public static class FallingMoonRenderer extends EntityRenderer<FallingMoonEntity, FallingMoonState> {
        public FallingMoonRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected boolean affectedByCulling(FallingMoonEntity entity) {
            return false;
        }

        @Override
        public FallingMoonState createRenderState() {
            return new FallingMoonState();
        }

        @Override
        public void extractRenderState(FallingMoonEntity entity, FallingMoonState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.progress = (entity.fallTicks() + partial) / FallingMoonEntity.DURATION;
        }

        @Override
        public void submit(FallingMoonState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float size = FallingMoonEntity.SIZE;
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 0.0F, 0.0F, size * 1.8F, 0xAA9CC8FF, state.ageInTicks * 0.5F);
            Beams.billboard(poseStack, collector, camera, FALLING_MOON, 0.0F, 0.0F, 0.0F, size, 0xFFFFFFFF, state.ageInTicks * 0.8F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class MoonletRenderer extends EntityRenderer<MoonletEntity, MoonletState> {
        public MoonletRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public MoonletState createRenderState() {
            return new MoonletState();
        }

        @Override
        public void extractRenderState(MoonletEntity entity, MoonletState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.size = entity.size();
            state.kind = entity.kind().ordinal();
        }

        @Override
        public void submit(MoonletState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float s = state.size;
            if (state.kind == MoonletEntity.Kind.CRESCENT.ordinal() || state.kind == MoonletEntity.Kind.SHARD.ordinal()) {
                Beams.billboard(poseStack, collector, camera, CRESCENT, 0.0F, s * 0.2F, 0.0F, s * 1.6F, 0xFFFFFFFF, state.ageInTicks * 25.0F);
            } else {
                Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, s * 0.3F, 0.0F, s * 2.4F, 0x889CC8FF, state.ageInTicks * 4.0F);
                Beams.billboard(poseStack, collector, camera, MOONLET, 0.0F, s * 0.3F, 0.0F, s * 1.2F, 0xFFFFFFFF, state.ageInTicks * 2.0F);
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class CrescentGlaiveRenderer extends EntityRenderer<CrescentGlaiveEntity, SpinState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("item/crescent_glaive");

        public CrescentGlaiveRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public SpinState createRenderState() {
            return new SpinState();
        }

        @Override
        public void extractRenderState(CrescentGlaiveEntity entity, SpinState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.spin = (entity.life() + partial) * 45.0F;
        }

        @Override
        public void submit(SpinState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.2F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin));
            Beams.quad(poseStack, collector, Beams.glow(TEXTURE), 2.0F, 0xFFFFFFFF);
            poseStack.popPose();
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 0.2F, 0.0F, 1.8F, 0x669CC8FF, state.spin * 0.2F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class MoonshotBoltRenderer extends EntityRenderer<MoonshotBoltEntity, BoltState> {
        public MoonshotBoltRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public BoltState createRenderState() {
            return new BoltState();
        }

        @Override
        public void extractRenderState(MoonshotBoltEntity entity, BoltState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.beacon = entity.isBeacon();
            state.stuck = entity.isStuck();
        }

        @Override
        public void submit(BoltState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float pulse = state.stuck ? 1.0F + Mth.sin(state.ageInTicks * 0.6F) * 0.3F : 1.0F;
            Beams.billboard(poseStack, collector, camera, GLOW, 0.0F, 0.0F, 0.0F, (state.beacon ? 1.6F : 0.9F) * pulse,
                state.beacon ? 0xFFB08CFF : 0xFFBFE8FF, state.ageInTicks * 10.0F);
            Beams.billboard(poseStack, collector, camera, MOONLET, 0.0F, 0.0F, 0.0F, 0.35F, 0xFFFFFFFF, 0.0F);
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class TetherHookRenderer extends EntityRenderer<TetherHookEntity, HookState> {
        private static final Identifier TEXTURE = StarforgedRenderers.tex("item/tether_hook");

        public TetherHookRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected boolean affectedByCulling(TetherHookEntity entity) {
            return false;
        }

        @Override
        public HookState createRenderState() {
            return new HookState();
        }

        @Override
        public void extractRenderState(TetherHookEntity entity, HookState state, float partial) {
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
                Beams.beam(poseStack, collector, BEAM, Vec3.ZERO, state.ownerHand, 0.08F, 0xFFC8D4EA, state.ageInTicks);
            }
            super.submit(state, poseStack, collector, camera);
        }
    }

    public static class TideWaveRenderer extends EntityRenderer<TideWaveEntity, SpinState> {
        public TideWaveRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected boolean affectedByCulling(TideWaveEntity entity) {
            return false;
        }

        @Override
        public SpinState createRenderState() {
            return new SpinState();
        }

        @Override
        public void extractRenderState(TideWaveEntity entity, SpinState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.spin = (entity.life() + partial) / TideWaveEntity.LIFE;
            state.yaw = entity.getYRot();
        }

        @Override
        public void submit(SpinState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            float fade = state.spin > 0.75F ? (1.0F - state.spin) / 0.25F : 1.0F;
            int alpha = (int) (200 * Mth.clamp(fade, 0.0F, 1.0F));
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            for (int i = -3; i <= 3; i++) {
                poseStack.pushPose();
                poseStack.translate(i * 1.0F, 1.0F + Mth.sin(state.ageInTicks * 0.5F + i) * 0.15F, 0.0F);
                Beams.quad(poseStack, collector, Beams.glow(TIDE), 2.2F, (alpha << 24) | 0xFFFFFF);
                poseStack.popPose();
            }
            poseStack.popPose();
            super.submit(state, poseStack, collector, camera);
        }
    }
}
