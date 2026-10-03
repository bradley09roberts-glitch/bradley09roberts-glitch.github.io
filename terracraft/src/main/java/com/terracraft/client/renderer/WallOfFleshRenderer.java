package com.terracraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.terracraft.TerraCraft;
import com.terracraft.client.model.FleshMouthModel;
import com.terracraft.client.model.TerraModels;
import com.terracraft.client.model.TerraRenderState;
import com.terracraft.entity.boss.WallOfFlesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Wall of Flesh: the mouth model at the entity, plus the wall itself, a vertical sheet of flesh
 * {@code 2 x HALF_WIDTH} blocks wide and {@code 2 x HALF_HEIGHT} tall across the wall's path, built from
 * tiled quads (visible from both sides).
 */
public class WallOfFleshRenderer extends TerraModelRenderer<WallOfFlesh> {
    private static final Identifier WALL = TerraCraft.id("textures/entity/model/wall_of_flesh_wall.png");
    private static final float TILE = 4.0F;

    public WallOfFleshRenderer(EntityRendererProvider.Context context) {
        super(context, new FleshMouthModel(context.bakeLayer(TerraModels.FLESH_MOUTH)), 5.0F / (18.0F / 16.0F), 0.0F, false);
    }

    @Override
    protected AABB getBoundingBoxForCulling(WallOfFlesh entity) {
        return entity.getBoundingBox().inflate(WallOfFlesh.HALF_WIDTH, WallOfFlesh.HALF_HEIGHT, WallOfFlesh.HALF_WIDTH);
    }

    @Override
    public void submit(TerraRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        Direction facing = Direction.fromYRot(state.bodyRot);
        float sx = -facing.getStepZ();
        float sz = facing.getStepX();
        float nx = facing.getStepX();
        float nz = facing.getStepZ();
        float half = (float) WallOfFlesh.HALF_WIDTH;
        float halfHeight = (float) WallOfFlesh.HALF_HEIGHT;
        float centerY = state.boundingBoxHeight / 2.0F;
        // sit just behind the mouth so the mouth pokes out of the wall
        float back = -0.6F;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WALL), (pose, buffer) -> {
            for (float a = -half; a < half; a += TILE) {
                for (float b = -halfHeight; b < halfHeight; b += TILE) {
                    float a1 = Math.min(a + TILE, half);
                    float b1 = Math.min(b + TILE, halfHeight);
                    vertex(buffer, pose, sx * a + nx * back, centerY + b, sz * a + nz * back, 0, 1, light, nx, nz);
                    vertex(buffer, pose, sx * a1 + nx * back, centerY + b, sz * a1 + nz * back, 1, 1, light, nx, nz);
                    vertex(buffer, pose, sx * a1 + nx * back, centerY + b1, sz * a1 + nz * back, 1, 0, light, nx, nz);
                    vertex(buffer, pose, sx * a + nx * back, centerY + b1, sz * a + nz * back, 0, 0, light, nx, nz);
                }
            }
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, float nx, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, nx, 0, nz);
    }
}
