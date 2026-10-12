package com.longnight.client;

import com.longnight.LongNight;
import com.longnight.entity.CitizenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A person is drawn as layered player-shaped models: body (skin and face), clothes tinted with
 * their culture/trade colour, hair tinted with their own hair colour, then any armour. Size is
 * scaled by people (dwarves, smallfolk, elves) and by age.
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {
    public static final ModelLayerLocation BASE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LongNight.MODID, "citizen"), "main");
    public static final ModelLayerLocation CLOTHES = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LongNight.MODID, "citizen"), "clothes");
    public static final ModelLayerLocation HAIR = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LongNight.MODID, "citizen"), "hair");
    public static final ModelLayerLocation ARMOR = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LongNight.MODID, "citizen"), "armor");

    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(BASE), false), 0.5f);
        addLayer(new Tinted(this, new PlayerModel<>(ctx.bakeLayer(CLOTHES), false), Kind.CLOTHES));
        addLayer(new Tinted(this, new PlayerModel<>(ctx.bakeLayer(HAIR), false), Kind.HAIR));
        addLayer(new Tinted(this, new PlayerModel<>(ctx.bakeLayer(ARMOR), false), Kind.ARMOR));
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity e) {
        return CitizenEntity.tex("body", e.bodyTexture());
    }

    @Override
    protected void scale(CitizenEntity e, PoseStack pose, float partialTick) {
        float s = e.bodyScale() * 0.9375f; // player-sized model scale
        pose.scale(s, s, s);
    }

    enum Kind { CLOTHES, HAIR, ARMOR }

    static final class Tinted extends RenderLayer<CitizenEntity, PlayerModel<CitizenEntity>> {
        private final PlayerModel<CitizenEntity> model;
        private final Kind kind;

        Tinted(RenderLayerParent<CitizenEntity, PlayerModel<CitizenEntity>> parent, PlayerModel<CitizenEntity> model, Kind kind) {
            super(parent);
            this.model = model;
            this.kind = kind;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CitizenEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ResourceLocation tex;
            int color;
            switch (kind) {
                case CLOTHES -> {
                    tex = CitizenEntity.tex("clothes", e.clothesTexture());
                    color = 0xFF000000 | e.clothColor();
                }
                case HAIR -> {
                    tex = CitizenEntity.tex("hair", e.hairTexture());
                    color = 0xFF000000 | e.hairColor();
                }
                default -> {
                    if (e.armorTexture().isEmpty()) return;
                    tex = CitizenEntity.tex("armor", e.armorTexture());
                    color = -1;
                }
            }
            coloredCutoutModelCopyLayerRender(getParentModel(), model, tex, pose, buffers, light, e, limbSwing, limbSwingAmount, ageInTicks,
                    netHeadYaw, headPitch, partialTick, color);
        }
    }
}
