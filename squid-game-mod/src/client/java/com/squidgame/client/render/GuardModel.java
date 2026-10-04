package com.squidgame.client.render;

import com.squidgame.SquidGameMod;
import com.squidgame.entity.GuardEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class GuardModel extends GeoModel<GuardEntity> {
    private static final ResourceLocation MODEL = SquidGameMod.id("geo/entity/guard.geo.json");
    private static final ResourceLocation TEXTURE = SquidGameMod.id("textures/entity/guard.png");
    private static final ResourceLocation ANIMATION = SquidGameMod.id("animations/entity/guard.animation.json");

    @Override
    public ResourceLocation getModelResource(GuardEntity e) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GuardEntity e) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(GuardEntity e) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(GuardEntity e, long instanceId, AnimationState<GuardEntity> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null) {
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -40f, 40f) * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -70f, 70f) * Mth.DEG_TO_RAD);
        }
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
