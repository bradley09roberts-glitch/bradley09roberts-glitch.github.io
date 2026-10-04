package com.squidgame.client.render;

import com.squidgame.SquidGameMod;
import com.squidgame.entity.DollEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DollModel extends GeoModel<DollEntity> {
    private static final ResourceLocation MODEL = SquidGameMod.id("geo/entity/doll.geo.json");
    private static final ResourceLocation TEXTURE = SquidGameMod.id("textures/entity/doll.png");
    private static final ResourceLocation ANIMATION = SquidGameMod.id("animations/entity/doll.animation.json");

    @Override
    public ResourceLocation getModelResource(DollEntity e) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DollEntity e) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DollEntity e) {
        return ANIMATION;
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
