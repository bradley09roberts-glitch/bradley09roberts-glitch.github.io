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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
        json(event, MobContent.EATER_OF_SOULS.get(), "eater_of_souls", 1.0F, 0.35F);
        json(event, MobContent.CRIMERA.get(), "crimera", 1.0F, 0.35F);
        json(event, MobContent.FACE_MONSTER.get(), "face_monster", 1.0F, 0.5F);
        json(event, MobContent.BLOOD_CRAWLER.get(), "blood_crawler", 1.0F, 0.5F);
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
        json(event, MobContent.MAN_EATER.get(), "man_eater", 1.0F, 0.4F);
        json(event, MobContent.SNATCHER.get(), "snatcher", 1.0F, 0.35F);
        bee(event, MobContent.HORNET.get(), 0.7F);
        bee(event, MobContent.BEE.get(), 0.35F);
        bee(event, MobContent.QUEEN_BEE.get(), 2.0F);
        // Underworld
        json(event, MobContent.IMP.get(), "imp", 1.0F, 0.4F);
        json(event, MobContent.DEMON.get(), "demon", 1.0F, 0.6F);
        json(event, MobContent.VOODOO_DEMON.get(), "voodoo_demon", 1.0F, 0.6F);
        slime(event, MobContent.LAVA_SLIME.get(), 1.0F);
        register(event, MobContent.HELLBAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        worm(event, MobContent.BONE_SERPENT.get(), 0.8F);
        register(event, MobContent.WALL_OF_FLESH.get(), WallOfFleshRenderer::new);
        eye(event, MobContent.WALL_OF_FLESH_EYE.get(), 3.2F);
        maw(event, MobContent.THE_HUNGRY.get(), 1.0F);
        // Goblin Army (goblins are a bit shorter than people)
        for (var goblin : java.util.List.of(MobContent.GOBLIN_PEON, MobContent.GOBLIN_THIEF, MobContent.GOBLIN_SORCERER, MobContent.GOBLIN_ARCHER)) {
            register(event, goblin.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(TerraModels.GOBLIN)), 0.9375F * 1.5F / 1.8F, 0.4F, false));
        }
        register(event, MobContent.GOBLIN_WARRIOR.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.GOBLIN)), 0.9375F * 1.7F / 1.8F, 0.45F, false));
        json(event, MobContent.METEOR_HEAD.get(), "meteor_head", 1.0F, 0.0F);
        // Hardmode
        json(event, MobContent.PIXIE.get(), "pixie", 1.0F, 0.2F);
        json(event, MobContent.UNICORN.get(), "unicorn", 1.0F, 0.7F);
        json(event, MobContent.GASTROPOD.get(), "gastropod", 1.0F, 0.4F);
        register(event, MobContent.ILLUMINANT_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        slime(event, MobContent.ILLUMINANT_SLIME.get(), 1.0F);
        json(event, MobContent.CHAOS_ELEMENTAL.get(), "chaos_elemental", 0.9375F, 0.5F);
        json(event, MobContent.CORRUPTOR.get(), "corruptor", 1.0F, 0.5F);
        json(event, MobContent.SLIMER.get(), "slimer", 1.0F, 0.5F);
        slime(event, MobContent.CRIMSLIME.get(), 1.0F);
        json(event, MobContent.HERPLING.get(), "herpling", 1.0F, 0.5F);
        json(event, MobContent.FLOATY_GROSS.get(), "floaty_gross", 1.0F, 0.0F);
        json(event, MobContent.WRAITH.get(), "wraith", 0.9375F, 0.0F);
        json(event, MobContent.POSSESSED_ARMOR.get(), "possessed_armor", 0.9375F, 0.5F);
        json(event, MobContent.WEREWOLF.get(), "werewolf", 1.0F, 0.5F);
        worm(event, MobContent.WYVERN.get(), 1.0F);
        register(event, MobContent.ARMORED_SKELETON.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.SKELETON)), 0.9375F, 0.5F, true));
        register(event, MobContent.GIANT_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 1.2F, 0.4F, false));
        json(event, MobContent.MIMIC.get(), "mimic", 1.0F, 0.6F);
        // Mechanical bosses
        eye(event, MobContent.RETINAZER.get(), 2.0F);
        eye(event, MobContent.SPAZMATISM.get(), 2.0F);
        eye(event, MobContent.PROBE.get(), 0.6F);
        worm(event, MobContent.DESTROYER.get(), 1.4F);
        skull(event, MobContent.SKELETRON_PRIME.get(), 2.2F);
        json(event, MobContent.PRIME_CANNON.get(), "prime_cannon", 1.0F, 0.4F);
        json(event, MobContent.PRIME_SAW.get(), "prime_saw", 1.0F, 0.4F);
        json(event, MobContent.PRIME_VICE.get(), "prime_vice", 1.0F, 0.4F);
        json(event, MobContent.PRIME_LASER.get(), "prime_laser", 1.0F, 0.4F);
        for (var npc : NpcContent.all()) {
            var layer = npc.getId().getPath().contains("goblin") ? TerraModels.GOBLIN : TerraModels.HUMANOID;
            register(event, npc.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(layer)), 0.9375F, 0.5F, false));
        }
    }

    /** A creature with its own JSON model (see JsonCreatureModel), drawn at 1 model unit = 1/16 block times {@code scale}. */
    private static void json(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, String name, float scale, float shadow) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx,
            new com.terracraft.client.model.JsonCreatureModel(ctx.bakeLayer(TerraModels.creature(name)), name), scale, shadow, false));
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
