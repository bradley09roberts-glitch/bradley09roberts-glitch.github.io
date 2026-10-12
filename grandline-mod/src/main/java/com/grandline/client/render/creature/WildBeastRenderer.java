package com.grandline.client.render.creature;

import com.grandline.GrandLine;
import com.grandline.creature.WildBeast;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer for island wildlife: geo/beast, textures/entity/beast and animations/beast by kind. */
public class WildBeastRenderer extends GeoEntityRenderer<WildBeast> {
    public WildBeastRenderer(EntityRendererProvider.Context ctx, WildBeast.Kind kind) {
        super(ctx, new Model(kind));
        this.shadowRadius = kind.width * 0.5f;
    }

    static final class Model extends GeoModel<WildBeast> {
        private final ResourceLocation geo, tex, anim;

        Model(WildBeast.Kind kind) {
            geo = GrandLine.id("geo/beast/" + kind.id() + ".geo.json");
            tex = GrandLine.id("textures/entity/beast/" + kind.id() + ".png");
            anim = GrandLine.id("animations/beast/" + kind.id() + ".anim.json");
        }

        @Override
        public ResourceLocation getModelResource(WildBeast b) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(WildBeast b) {
            return tex;
        }

        @Override
        public ResourceLocation getAnimationResource(WildBeast b) {
            return anim;
        }

        @Override
        public void setCustomAnimations(WildBeast b, long id, software.bernie.geckolib.animation.AnimationState<WildBeast> state) {
            var head = getAnimationProcessor().getBone("head");
            var d = state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY_MODEL_DATA);
            if (head == null || d == null) return;
            head.setRotX(head.getRotX() + net.minecraft.util.Mth.clamp(d.headPitch(), -40, 40) * net.minecraft.util.Mth.DEG_TO_RAD * 0.6f);
            head.setRotY(head.getRotY() + net.minecraft.util.Mth.clamp(d.netHeadYaw(), -50, 50) * net.minecraft.util.Mth.DEG_TO_RAD * 0.6f);
        }
    }
}
