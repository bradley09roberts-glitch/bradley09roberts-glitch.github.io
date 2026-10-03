package com.terracraft.client.renderer;

import com.terracraft.client.model.BatModel3D;
import com.terracraft.client.model.EyeModel;
import com.terracraft.client.model.SlimeBodyModel;
import com.terracraft.client.model.TerraHumanoidModel;
import com.terracraft.client.model.TerraModels;
import com.terracraft.config.TerraConfig;
import com.terracraft.registry.content.MobContent;
import com.terracraft.registry.content.NpcContent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.function.Function;

/**
 * Chooses how each TerraCraft creature is drawn: a 3D model (default) or, with the client option
 * {@code flatSprites}, the flat Terraria-style sprite. The choice is made whenever renderers are (re)built.
 */
public final class CreatureRenderers {
    private CreatureRenderers() {}

    /** Model scale = hitbox width / model width in blocks (slime model 14 px, eye model 10 px). */
    private static float slime(float width) {
        return width / (14.0F / 16.0F);
    }

    private static float eye(float width) {
        return width / (10.0F / 16.0F);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        slime(event, MobContent.GREEN_SLIME.get(), 0.9F);
        slime(event, MobContent.BLUE_SLIME.get(), 0.9F);
        slime(event, MobContent.RED_SLIME.get(), 0.95F);
        slime(event, MobContent.PURPLE_SLIME.get(), 1.1F);
        slime(event, MobContent.YELLOW_SLIME.get(), 1.0F);
        slime(event, MobContent.BLACK_SLIME.get(), 1.0F);
        slime(event, MobContent.BABY_SLIME.get(), 0.55F);
        register(event, MobContent.MOTHER_SLIME.get(), ctx -> new TerraModelRenderer<>(ctx,
            new SlimeBodyModel(ctx.bakeLayer(TerraModels.MOTHER_SLIME)), slime(1.3F), 0.6F, false));
        register(event, MobContent.KING_SLIME.get(), ctx -> new TerraModelRenderer<>(ctx,
            new SlimeBodyModel(ctx.bakeLayer(TerraModels.KING_SLIME)), slime(3.0F), 1.4F, false));
        register(event, MobContent.ZOMBIE.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, true));
        register(event, MobContent.SKELETON.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.SKELETON)), 0.9375F, 0.5F, true));
        eye(event, MobContent.DEMON_EYE.get(), 0.7F);
        eye(event, MobContent.SERVANT_OF_CTHULHU.get(), 0.55F);
        eye(event, MobContent.EYE_OF_CTHULHU.get(), 2.4F);
        register(event, MobContent.CAVE_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        register(event, MobContent.BLOOD_ZOMBIE.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, true));
        eye(event, MobContent.DRIPPLER.get(), 0.9F);
        worm(event, MobContent.DEVOURER.get(), 0.8F);
        worm(event, MobContent.EATER_OF_WORLDS.get(), 1.4F);
        eye(event, MobContent.BRAIN_CREEPER.get(), 0.7F);
        register(event, MobContent.BRAIN_OF_CTHULHU.get(), ctx -> new TerraModelRenderer<>(ctx,
            new com.terracraft.client.model.BrainModel(ctx.bakeLayer(TerraModels.BRAIN)), 2.2F / (15.0F / 16.0F), 1.0F, false));
        worm(event, MobContent.GIANT_WORM.get(), 0.6F);
        maw(event, MobContent.EATER_OF_SOULS.get(), 0.7F);
        maw(event, MobContent.CRIMERA.get(), 0.7F);
        register(event, MobContent.FACE_MONSTER.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 1.0F, 0.5F, true));
        register(event, MobContent.BLOOD_CRAWLER.get(), ctx -> new TerraModelRenderer<>(ctx,
            new net.minecraft.client.model.monster.spider.SpiderModel(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SPIDER)), 0.8F, 0.5F, false));
        // Dungeon
        register(event, MobContent.ANGRY_BONES.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.SKELETON)), 0.9375F, 0.5F, true));
        register(event, MobContent.DARK_CASTER.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, false));
        slime(event, MobContent.DUNGEON_SLIME.get(), 1.0F);
        skull(event, MobContent.CURSED_SKULL.get(), 0.7F);
        skull(event, MobContent.DUNGEON_GUARDIAN.get(), 1.6F);
        skull(event, MobContent.SKELETRON.get(), 2.2F);
        register(event, MobContent.SKELETRON_HAND.get(), ctx -> new TerraModelRenderer<>(ctx,
            new com.terracraft.client.model.BoneHandModel(ctx.bakeLayer(TerraModels.BONE_HAND)), 1.2F / (10.0F / 16.0F), 0.4F, false));
        // Jungle
        slime(event, MobContent.JUNGLE_SLIME.get(), 1.0F);
        register(event, MobContent.JUNGLE_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        maw(event, MobContent.MAN_EATER.get(), 0.8F);
        maw(event, MobContent.SNATCHER.get(), 0.7F);
        bee(event, MobContent.HORNET.get(), 0.7F);
        bee(event, MobContent.BEE.get(), 0.35F);
        bee(event, MobContent.QUEEN_BEE.get(), 2.0F);
        // Underworld
        register(event, MobContent.IMP.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F * 1.5F / 1.8F, 0.4F, false));
        register(event, MobContent.DEMON.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 1.6F, 0.6F, false));
        register(event, MobContent.VOODOO_DEMON.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 1.6F, 0.6F, false));
        slime(event, MobContent.LAVA_SLIME.get(), 1.0F);
        register(event, MobContent.HELLBAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        worm(event, MobContent.BONE_SERPENT.get(), 0.8F);
        register(event, MobContent.WALL_OF_FLESH.get(), WallOfFleshRenderer::new);
        eye(event, MobContent.WALL_OF_FLESH_EYE.get(), 3.2F);
        maw(event, MobContent.THE_HUNGRY.get(), 1.0F);
        for (var npc : NpcContent.all()) {
            register(event, npc.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, false));
        }
    }

    private static void slime(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new SlimeBodyModel(ctx.bakeLayer(TerraModels.SLIME)), slime(width), width * 0.45F, false));
    }

    /** Worm segment model is 10 px wide. */
    public static void worm(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.WormModel(ctx.bakeLayer(TerraModels.WORM)),
            width / (10.0F / 16.0F), width * 0.4F, false));
    }

    private static void maw(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.MawModel(ctx.bakeLayer(TerraModels.MAW)),
            width / (8.0F / 16.0F), width * 0.4F, false));
    }

    /** Bee model: scaled so 10 px fill the hitbox width (the body is longer than wide). */
    private static void bee(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.BeeModel3D(ctx.bakeLayer(TerraModels.BEE)),
            width / (10.0F / 16.0F), width * 0.4F, false));
    }

    /** Skull model is 10 px wide. */
    private static void skull(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.SkullModel(ctx.bakeLayer(TerraModels.SKULL)),
            width / (10.0F / 16.0F), width * 0.4F, false));
    }

    private static void eye(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new EyeModel(ctx.bakeLayer(TerraModels.EYE)), eye(width), width * 0.4F, false));
    }

    private static <T extends LivingEntity> void register(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type,
                                                          Function<EntityRendererProvider.Context, EntityRenderer<T, ?>> model) {
        event.registerEntityRenderer(type, ctx -> flat() ? new TerrariaMobRenderer<>(ctx) : model.apply(ctx));
    }

    private static boolean flat() {
        try {
            return TerraConfig.CLIENT.flatSprites.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }
}
