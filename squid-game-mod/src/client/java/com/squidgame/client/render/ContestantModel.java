package com.squidgame.client.render;

import com.squidgame.SquidGameMod;
import com.squidgame.entity.ContestantEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Geo model of the numbered contestant (see docs/ASSET_CONTRACT.md for bone names). */
public class ContestantModel extends GeoModel<ContestantEntity> {
    private static final ResourceLocation MODEL = SquidGameMod.id("geo/entity/contestant.geo.json");
    private static final ResourceLocation TEXTURE = SquidGameMod.id("textures/entity/contestant.png");
    private static final ResourceLocation ANIMATION = SquidGameMod.id("animations/entity/contestant.animation.json");

    @Override
    public ResourceLocation getModelResource(ContestantEntity e) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ContestantEntity e) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ContestantEntity e) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(ContestantEntity e, long instanceId, AnimationState<ContestantEntity> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) {
            return;
        }
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }
        // look direction is added on top of whatever the playing animation does to the head
        float yaw = Mth.clamp(data.netHeadYaw(), -70f, 70f);
        float pitch = Mth.clamp(data.headPitch(), -40f, 40f);
        head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD);
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
