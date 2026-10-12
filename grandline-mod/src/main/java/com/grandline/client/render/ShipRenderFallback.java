package com.grandline.client.render;

import com.grandline.GrandLine;
import com.grandline.ship.ShipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Vanilla only draws an entity once the terrain section it stands in has been compiled. A fast ship sailing into
 * fresh sections would blink out of view (most visibly for its own crew); this draws any ship that vanilla skipped
 * this frame.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class ShipRenderFallback {
    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ShipRenderer.DRAWN.clear();
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ShipEntity ship) || ShipRenderer.DRAWN.contains(ship.getId())) continue;
            if (ship.distanceToSqr(cam) > 256 * 256) continue;
            double x = net.minecraft.util.Mth.lerp(partial, ship.xOld, ship.getX()) - cam.x;
            double y = net.minecraft.util.Mth.lerp(partial, ship.yOld, ship.getY()) - cam.y;
            double z = net.minecraft.util.Mth.lerp(partial, ship.zOld, ship.getZ()) - cam.z;
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(ship.getX(), ship.getY() + 2, ship.getZ()));
            mc.getEntityRenderDispatcher().render(ship, x, y, z, ship.getYRot(), partial, ps, buf, light);
            drew = true;
        }
        if (drew) buf.endBatch();
        ShipRenderer.DRAWN.clear();
    }

    private ShipRenderFallback() {}
}
