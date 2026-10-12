package com.grandline.client.render;

import com.grandline.GrandLine;
import com.grandline.player.Race;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import static com.grandline.player.Race.Parts.*;

/**
 * Race body parts (fins, ears, tails, wings, horns, third eye, coral, stone plates, leaf hair...) and Zoan hybrid
 * features, attached to the animated humanoid parts and tinted with the character's chosen colours.
 */
public class RaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation TEX = GrandLine.id("textures/entity/race_parts.png");
    private final ModelPart parts;

    public RaceLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context ctx) {
        super(parent);
        this.parts = build().bakeRoot();
    }

    static LayerDefinition build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // head-attached
        root.addOrReplaceChild("head_fin", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, -12f, -4f, 1, 4, 9), PartPose.ZERO);
        root.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(20, 0).addBox(1.5f, -11f, -1f, 3, 4, 1), PartPose.ZERO);
        root.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(20, 0).mirror().addBox(-4.5f, -11f, -1f, 3, 4, 1), PartPose.ZERO);
        root.addOrReplaceChild("third_eye", CubeListBuilder.create().texOffs(28, 0).addBox(-1f, -7.5f, -4.4f, 2, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(34, 0).addBox(2f, -11f, -2f, 1, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(34, 0).addBox(-3f, -11f, -2f, 1, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("leaf_hair", CubeListBuilder.create().texOffs(0, 14).addBox(-5f, -10f, -5f, 10, 3, 10), PartPose.ZERO);
        root.addOrReplaceChild("blowhole", CubeListBuilder.create().texOffs(28, 4).addBox(-1f, -8.6f, 0f, 2, 1, 2), PartPose.ZERO);
        root.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(40, 0).addBox(-1.5f, -4f, -7f, 3, 3, 3), PartPose.ZERO);
        // body-attached
        root.addOrReplaceChild("back_fin", CubeListBuilder.create().texOffs(0, 28).addBox(-0.5f, 0f, 2f, 1, 8, 4), PartPose.ZERO);
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 28).addBox(-1f, 10f, 2f, 2, 2, 9), PartPose.ZERO);
        root.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(0, 40).addBox(1f, 0f, 2f, 9, 10, 1), PartPose.ZERO);
        root.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 40).mirror().addBox(-10f, 0f, 2f, 9, 10, 1), PartPose.ZERO);
        root.addOrReplaceChild("coral", CubeListBuilder.create().texOffs(34, 28).addBox(-4f, -1f, 2f, 2, 3, 2).addBox(2f, -1f, 2f, 2, 4, 2).addBox(-1f, 3f, 2f, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("plates", CubeListBuilder.create().texOffs(34, 36).addBox(-4.5f, -0.5f, -2.5f, 9, 3, 5), PartPose.ZERO);
        root.addOrReplaceChild("tail_fin", CubeListBuilder.create().texOffs(0, 52).addBox(-4f, 11f, -1f, 8, 2, 6), PartPose.ZERO);
        root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(40, 14).addBox(-1.5f, -6f, -1.5f, 3, 6, 3), PartPose.ZERO);
        // fruit wave 3 (canon) Zoan hybrid extras; texture offsets reuse the regions of similar parts above
        root.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(12, 28).addBox(-1f, -3f, -6f, 2, 8, 2), PartPose.ZERO);
        root.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(34, 0).addBox(1.5f, -1.5f, -7f, 1, 1, 4), PartPose.ZERO);
        root.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(34, 0).mirror().addBox(-2.5f, -1.5f, -7f, 1, 1, 4), PartPose.ZERO);
        root.addOrReplaceChild("big_ear_l", CubeListBuilder.create().texOffs(20, 0).addBox(4f, -8f, -1f, 1, 6, 5), PartPose.ZERO);
        root.addOrReplaceChild("big_ear_r", CubeListBuilder.create().texOffs(20, 0).mirror().addBox(-5f, -8f, -1f, 1, 6, 5), PartPose.ZERO);
        root.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, -10f, 1f, 1, 3, 7), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
        if (e.isInvisible()) return;
        RaceVisuals.Info info = RaceVisuals.of(e);
        Race race = info.race();
        boolean zoan = !info.zoanForm().isEmpty();
        boolean wolf = zoan && info.fruit().equals("inu_wolf");
        boolean leopard = zoan && info.fruit().equals("neko_leopard");
        boolean bison = zoan && info.fruit().equals("ushi_bison");
        boolean phoenix = zoan && info.fruit().equals("tori_phoenix");
        boolean beast = wolf || leopard;   // ears, snout and tail
        int fur = leopard ? 0xFFE0B04A : 0xFF8A8A9A;
        // fruit wave 3 (canon) Zoans
        String zf = zoan ? info.fruit() : "";
        boolean elephant = zf.equals("zou_elephant"), allo = zf.equals("ryu_allosaurus"), ptera = zf.equals("ryu_pteranodon");
        boolean spino = zf.equals("ryu_spinosaurus"), seiryu = zf.equals("uo_seiryu"), orochi = zf.equals("hebi_orochi");
        boolean canon3 = elephant || allo || ptera || spino || seiryu || orochi;
        if (race.parts == 0 && !beast && !bison && !phoenix && !canon3) return;
        M m = getParentModel();
        int col = 0xFF000000 | info.appearance().featureColor;
        int col2 = 0xFF000000 | info.appearance().secondaryColor;
        int hair = 0xFF000000 | info.appearance().hairColor;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        int ov = LivingEntityRenderer.getOverlayCoords(e, 0);

        // ----- head features -----
        ps.pushPose();
        m.head.translateAndRotate(ps);
        if (race.has(FINS)) draw("head_fin", ps, vc, light, ov, col);
        if (race.has(EARS) || beast) {
            draw("ear_l", ps, vc, light, ov, beast ? fur : hair);
            draw("ear_r", ps, vc, light, ov, beast ? fur : hair);
        }
        if (beast) draw("snout", ps, vc, light, ov, fur);
        if (bison) {
            draw("horn_l", ps, vc, light, ov, 0xFFE8E0C8);
            draw("horn_r", ps, vc, light, ov, 0xFFE8E0C8);
            draw("snout", ps, vc, light, ov, 0xFF7A4A2A);
        }
        if (race.has(THIRD_EYE)) draw("third_eye", ps, vc, 0xF000F0, ov, 0xFF000000 | info.appearance().eyeColor);
        if (race.has(HORNS)) {
            draw("horn_l", ps, vc, light, ov, col2);
            draw("horn_r", ps, vc, light, ov, col2);
        }
        if (race.has(LEAF_HAIR)) draw("leaf_hair", ps, vc, light, ov, col);
        if (race.has(BLOWHOLE)) draw("blowhole", ps, vc, light, ov, col2);
        if (canon3) canonWave3Head(ps, vc, light, ov, elephant, allo, ptera, spino, seiryu, orochi);
        ps.popPose();

        // ----- body features -----
        ps.pushPose();
        m.body.translateAndRotate(ps);
        if (race.has(FINS)) draw("back_fin", ps, vc, light, ov, col);
        if (race.has(TAIL) || beast) {
            float sway = Mth.sin(age * 0.15f) * 0.25f + limbSwingAmount * Mth.sin(limbSwing * 0.6f) * 0.4f;
            ModelPart t = parts.getChild("tail");
            t.yRot = sway;
            t.xRot = -0.35f;
            draw("tail", ps, vc, light, ov, beast ? fur : hair);
        }
        if (race.has(SMALL_WINGS) || race.has(FEATHER_WINGS) || race.has(BLACK_WINGS) || phoenix) {
            float flap = Mth.sin(age * (e.onGround() ? 0.08f : 0.5f)) * (e.onGround() ? 0.08f : 0.45f);
            float size = phoenix ? 1.6f : race.has(SMALL_WINGS) ? 0.6f : 1.2f;
            int wc = phoenix ? 0xFF3AB0FF : race.has(BLACK_WINGS) ? 0xFF1A1A1A : race.has(FEATHER_WINGS) ? col : 0xFFF4F4F4;
            ModelPart l = parts.getChild("wing_l"), r = parts.getChild("wing_r");
            l.yRot = -0.5f - flap;
            r.yRot = 0.5f + flap;
            l.xScale = l.yScale = r.xScale = r.yScale = size;
            draw("wing_l", ps, vc, light, ov, wc);
            draw("wing_r", ps, vc, light, ov, wc);
        }
        if (race.has(CORAL)) draw("coral", ps, vc, light, ov, col);
        if (race.has(STONE_SKIN)) draw("plates", ps, vc, light, ov, 0xFF6A6A70);
        if (race.has(LONG_NECK)) draw("neck", ps, vc, light, ov, col2);
        if (race.has(TAIL_FIN) && e.isInWater()) draw("tail_fin", ps, vc, light, ov, col);
        if (canon3) {
            boolean raceTail = race.has(TAIL) || beast, raceWings = race.has(SMALL_WINGS) || race.has(FEATHER_WINGS) || race.has(BLACK_WINGS);
            canonWave3Body(e, ps, vc, light, ov, age, limbSwing, limbSwingAmount, raceTail, raceWings, allo, ptera, spino, seiryu, orochi);
        }
        ps.popPose();
    }

    // ---------------- fruit wave 3 (canon): Zoan hybrid features ----------------

    private void canonWave3Head(PoseStack ps, VertexConsumer vc, int light, int ov, boolean elephant, boolean allo, boolean ptera, boolean spino, boolean seiryu, boolean orochi) {
        int ivory = 0xFFE8E0C8;
        if (elephant) {
            draw("big_ear_l", ps, vc, light, ov, 0xFF9A9AA8);
            draw("big_ear_r", ps, vc, light, ov, 0xFF9A9AA8);
            draw("trunk", ps, vc, light, ov, 0xFF8A8A98);
            draw("tusk_l", ps, vc, light, ov, ivory);
            draw("tusk_r", ps, vc, light, ov, ivory);
        }
        if (allo || spino) draw("snout", ps, vc, light, ov, allo ? 0xFF6A8A4A : 0xFF4A6A6A);
        if (allo) {
            draw("horn_l", ps, vc, light, ov, 0xFF3A5A2A);
            draw("horn_r", ps, vc, light, ov, 0xFF3A5A2A);
        }
        if (ptera) {
            draw("crest", ps, vc, light, ov, 0xFFC09A6A);
            draw("snout", ps, vc, light, ov, 0xFFC09A6A);
        }
        if (seiryu) {
            draw("horn_l", ps, vc, light, ov, ivory);
            draw("horn_r", ps, vc, light, ov, ivory);
            draw("crest", ps, vc, light, ov, 0xFFC0E0FF);
        }
        if (orochi) draw("snout", ps, vc, light, ov, 0xFF5A2A6A);
    }

    private void canonWave3Body(T e, PoseStack ps, VertexConsumer vc, int light, int ov, float age, float limbSwing, float limbSwingAmount,
                                boolean raceTail, boolean raceWings, boolean allo, boolean ptera, boolean spino, boolean seiryu, boolean orochi) {
        if ((allo || spino || seiryu || orochi) && !raceTail) {
            float sway = Mth.sin(age * 0.12f) * 0.3f + limbSwingAmount * Mth.sin(limbSwing * 0.6f) * 0.4f;
            ModelPart t = parts.getChild("tail");
            t.yRot = sway;
            t.xRot = orochi ? -0.1f : -0.35f;
            int tc = allo ? 0xFF6A8A4A : spino ? 0xFF4A6A6A : seiryu ? 0xFF2A5AD0 : 0xFF5A2A6A;
            draw("tail", ps, vc, light, ov, tc);
        }
        if (spino || seiryu) draw("back_fin", ps, vc, light, ov, spino ? 0xFFC04A3A : 0xFFC0E0FF); // sail / dorsal spines
        if (ptera && !raceWings) {
            float flap = Mth.sin(age * (e.onGround() ? 0.08f : 0.5f)) * (e.onGround() ? 0.08f : 0.45f);
            ModelPart l = parts.getChild("wing_l"), r = parts.getChild("wing_r");
            l.yRot = -0.5f - flap;
            r.yRot = 0.5f + flap;
            l.xScale = l.yScale = r.xScale = r.yScale = 1.5f;
            draw("wing_l", ps, vc, light, ov, 0xFF8A6A4A);
            draw("wing_r", ps, vc, light, ov, 0xFF8A6A4A);
        }
    }

    private void draw(String name, PoseStack ps, VertexConsumer vc, int light, int ov, int argb) {
        parts.getChild(name).render(ps, vc, light, ov, argb);
    }
}
