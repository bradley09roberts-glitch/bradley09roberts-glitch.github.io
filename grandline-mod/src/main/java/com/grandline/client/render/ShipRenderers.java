package com.grandline.client.render;

import com.grandline.registry.ModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ShipRenderers {
    public static void register(EntityRenderersEvent.RegisterRenderers e) {
        for (var holder : ModEntities.SHIPS.values()) e.registerEntityRenderer(holder.get(), ShipRenderer::new);
    }

    private ShipRenderers() {}
}
