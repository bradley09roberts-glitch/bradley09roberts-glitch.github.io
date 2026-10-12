package com.grandline.client.render.creature;

import com.grandline.creature.SeaKing;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** GeckoLib renderer for Sea Kings (glowing eyes via each variant's _glowmask texture). */
public class SeaKingRenderer extends GeoEntityRenderer<SeaKing> {
    public SeaKingRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SeaKingModel());
        this.shadowRadius = 0;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(SeaKing sk) {
        return 0; // the model rolls belly-up instead (see SeaKingModel)
    }
}
