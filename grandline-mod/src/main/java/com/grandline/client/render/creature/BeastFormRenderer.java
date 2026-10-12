package com.grandline.client.render.creature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Draws a Zoan beast model in place of a player's or NPC's humanoid body. */
public class BeastFormRenderer extends GeoReplacedEntityRenderer<LivingEntity, BeastAnimatable> {
    public BeastFormRenderer(EntityRendererProvider.Context ctx, BeastAnimatable animatable, boolean glow) {
        super(ctx, new BeastModel(animatable.beast.id()), animatable);
        this.shadowRadius = animatable.beast.width() * 0.6f;
        if (glow) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public boolean shouldShowName(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e instanceof Player) return Minecraft.renderNames() && e != mc.getCameraEntity() && !e.isInvisibleTo(mc.player);
        return super.shouldShowName(e);
    }
}
