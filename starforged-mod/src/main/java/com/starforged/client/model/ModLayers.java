package com.starforged.client.model;

import com.starforged.Starforged;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public final class ModLayers {
    public static final ModelLayerLocation STAR_MITE = layer("star_mite");
    public static final ModelLayerLocation VOID_STALKER = layer("void_stalker");
    public static final ModelLayerLocation ASTRAL_GOLEM = layer("astral_golem");
    public static final ModelLayerLocation MIMIC = layer("mimic");
    public static final ModelLayerLocation ASTRAL_WRAITH = layer("astral_wraith");
    public static final ModelLayerLocation STARLING = layer("starling");
    public static final ModelLayerLocation NEBULA_RAY = layer("nebula_ray");
    public static final ModelLayerLocation ECLIPSE_SOVEREIGN = layer("eclipse_sovereign");
    public static final ModelLayerLocation ECLIPSE_CRYSTAL = layer("eclipse_crystal");

    private ModLayers() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Starforged.id(name), "main");
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(STAR_MITE, ModelGeometry::starMite);
        event.registerLayerDefinition(VOID_STALKER, ModelGeometry::voidStalker);
        event.registerLayerDefinition(ASTRAL_GOLEM, ModelGeometry::astralGolem);
        event.registerLayerDefinition(MIMIC, ModelGeometry::mimic);
        event.registerLayerDefinition(ASTRAL_WRAITH, ModelGeometry::astralWraith);
        event.registerLayerDefinition(STARLING, ModelGeometry::starling);
        event.registerLayerDefinition(NEBULA_RAY, ModelGeometry::nebulaRay);
        event.registerLayerDefinition(ECLIPSE_SOVEREIGN, ModelGeometry::eclipseSovereign);
        event.registerLayerDefinition(ECLIPSE_CRYSTAL, ModelGeometry::eclipseCrystal);
    }

    /** Looks up a nested part by slash-separated path, e.g. "body/chest/head". */
    public static ModelPart path(ModelPart root, String path) {
        ModelPart part = root;
        for (String name : path.split("/")) {
            part = part.getChild(name);
        }
        return part;
    }
}
