package com.starforged.client.model;

import com.starforged.Starforged;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.client.event.EntityRenderersEvent;

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
    public static final ModelLayerLocation CINDER_IMP = layer("cinder_imp");
    public static final ModelLayerLocation MAGMA_CRAWLER = layer("magma_crawler");
    public static final ModelLayerLocation EMBER_HOUND = layer("ember_hound");
    public static final ModelLayerLocation ASHEN_KNIGHT = layer("ashen_knight");
    public static final ModelLayerLocation SOLAR_PHOENIX = layer("solar_phoenix");
    public static final ModelLayerLocation SUN_WARDEN = layer("sun_warden");
    public static final ModelLayerLocation SOLAR_PYLON = layer("solar_pylon");

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
        event.registerLayerDefinition(CINDER_IMP, ModelGeometry::cinderImp);
        event.registerLayerDefinition(MAGMA_CRAWLER, ModelGeometry::magmaCrawler);
        event.registerLayerDefinition(EMBER_HOUND, ModelGeometry::emberHound);
        event.registerLayerDefinition(ASHEN_KNIGHT, ModelGeometry::ashenKnight);
        event.registerLayerDefinition(SOLAR_PHOENIX, ModelGeometry::solarPhoenix);
        event.registerLayerDefinition(SUN_WARDEN, ModelGeometry::sunWarden);
        event.registerLayerDefinition(SOLAR_PYLON, ModelGeometry::solarPylon);
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
