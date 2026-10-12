package com.grandline.client.render.creature;

import com.grandline.GrandLine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** GeckoLib model for a Zoan beast form: geo/zoan/&lt;id&gt;, textures/entity/zoan/&lt;id&gt;, animations/zoan/&lt;id&gt;. */
public class BeastModel extends GeoModel<BeastAnimatable> {
    private final ResourceLocation geo, tex, anim;

    public BeastModel(String id) {
        this.geo = geo(id);
        this.tex = GrandLine.id("textures/entity/zoan/" + id + ".png");
        this.anim = GrandLine.id("animations/zoan/" + id + ".anim.json");
    }

    public static ResourceLocation geo(String id) {
        return GrandLine.id("geo/zoan/" + id + ".geo.json");
    }

    @Override
    public ResourceLocation getModelResource(BeastAnimatable a) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureResource(BeastAnimatable a) {
        return tex;
    }

    @Override
    public ResourceLocation getAnimationResource(BeastAnimatable a) {
        return anim;
    }

    /** The head follows where the player is looking (on top of the keyframed head motion). */
    @Override
    public void setCustomAnimations(BeastAnimatable a, long instanceId, AnimationState<BeastAnimatable> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || d == null) return;
        head.setRotX(head.getRotX() + Mth.clamp(d.headPitch(), -50, 50) * Mth.DEG_TO_RAD * 0.7f);
        head.setRotY(head.getRotY() + Mth.clamp(d.netHeadYaw(), -60, 60) * Mth.DEG_TO_RAD * 0.7f);
    }
}
