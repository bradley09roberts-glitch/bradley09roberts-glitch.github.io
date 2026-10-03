package com.terracraft.client.model;

import com.terracraft.TerraCraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Model layers of TerraCraft's 3D creatures. Textures live in {@code textures/entity/model/<entity>[_variant].png}. */
public final class TerraModels {
    public static final ModelLayerLocation SLIME = layer("slime");
    public static final ModelLayerLocation MOTHER_SLIME = layer("mother_slime");
    public static final ModelLayerLocation KING_SLIME = layer("king_slime");
    public static final ModelLayerLocation HUMANOID = layer("humanoid");
    public static final ModelLayerLocation SKELETON = layer("skeleton");
    public static final ModelLayerLocation GOBLIN = layer("goblin");
    public static final ModelLayerLocation EYE = layer("eye");
    public static final ModelLayerLocation BAT = layer("bat");
    public static final ModelLayerLocation WORM = layer("worm");
    public static final ModelLayerLocation MAW = layer("maw");
    public static final ModelLayerLocation BRAIN = layer("brain");
    public static final ModelLayerLocation SKULL = layer("skull");
    public static final ModelLayerLocation BONE_HAND = layer("bone_hand");
    public static final ModelLayerLocation BEE = layer("bee");
    public static final ModelLayerLocation FLESH_MOUTH = layer("flesh_mouth");
    public static final ModelLayerLocation WINGS = layer("wings");

    /** Creatures with JSON-described models (tools/creature_models.py), one layer each. */
    public static final java.util.List<String> JSON_CREATURES = java.util.List.of("imp", "demon", "voodoo_demon", "eater_of_souls", "crimera",
        "face_monster", "blood_crawler", "man_eater", "snatcher", "meteor_head");

    private TerraModels() {}

    public static ModelLayerLocation creature(String name) {
        return new ModelLayerLocation(TerraCraft.id("creature/" + name), "main");
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(TerraCraft.id(name), "main");
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SLIME, SlimeBodyModel::createSlime);
        event.registerLayerDefinition(MOTHER_SLIME, SlimeBodyModel::createMotherSlime);
        event.registerLayerDefinition(KING_SLIME, SlimeBodyModel::createKingSlime);
        event.registerLayerDefinition(HUMANOID, TerraHumanoidModel::createHumanoid);
        event.registerLayerDefinition(SKELETON, TerraHumanoidModel::createSkeleton);
        event.registerLayerDefinition(GOBLIN, TerraHumanoidModel::createGoblin);
        event.registerLayerDefinition(EYE, EyeModel::createEye);
        event.registerLayerDefinition(BAT, BatModel3D::createBat);
        event.registerLayerDefinition(WORM, WormModel::createWorm);
        event.registerLayerDefinition(MAW, MawModel::createMaw);
        event.registerLayerDefinition(BRAIN, BrainModel::createBrain);
        event.registerLayerDefinition(SKULL, SkullModel::createSkull);
        event.registerLayerDefinition(BONE_HAND, BoneHandModel::createHand);
        event.registerLayerDefinition(BEE, BeeModel3D::createBee);
        event.registerLayerDefinition(FLESH_MOUTH, FleshMouthModel::createMouth);
        event.registerLayerDefinition(WINGS, WingsModel::createWings);
        for (String name : JSON_CREATURES) {
            event.registerLayerDefinition(creature(name), () -> JsonCreatureModel.create(name));
        }
    }
}
