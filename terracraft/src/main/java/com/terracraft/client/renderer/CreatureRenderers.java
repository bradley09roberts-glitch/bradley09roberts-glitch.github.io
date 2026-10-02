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
        for (var npc : NpcContent.all()) {
            register(event, npc.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, false));
        }
    }

    private static void slime(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new SlimeBodyModel(ctx.bakeLayer(TerraModels.SLIME)), slime(width), width * 0.45F, false));
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
