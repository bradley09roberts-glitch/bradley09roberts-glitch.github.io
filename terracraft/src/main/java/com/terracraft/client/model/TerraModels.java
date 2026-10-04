package com.terracraft.client.model;

import com.terracraft.TerraCraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Model layers of TerraCraft's 3D creatures. Textures live in {@code textures/entity/model/<entity>[_variant].png}. */
public final class TerraModels {
    public static final ModelLayerLocation SLIME = layer("slime");
    public static final ModelLayerLocation MOTHER_SLIME = layer("mother_slime");
    public static final ModelLayerLocation KING_SLIME = layer("king_slime");
    public static final ModelLayerLocation QUEEN_SLIME = layer("queen_slime");
    public static final ModelLayerLocation HEAVENLY_SLIME = layer("heavenly_slime");
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
        "face_monster", "blood_crawler", "man_eater", "snatcher", "meteor_head", "pixie", "unicorn", "gastropod", "chaos_elemental",
        "corruptor", "slimer", "herpling", "floaty_gross", "wraith", "possessed_armor", "werewolf", "mimic",
        "prime_cannon", "prime_saw", "prime_vice", "prime_laser", "parrot", "flying_dutchman", "mister_stabby", "snowman_gangsta", "snow_balla",
        "angry_trapper", "derpling", "plantera", "plantera_hook", "plantera_tentacle",
        "lihzahrd", "flying_snake", "golem", "golem_head", "golem_fist",
        "truffle_worm", "duke_fishron", "sharkron", "dungeon_spirit", "splinterling", "hellhound", "poltergeist", "mourning_wood", "pumpking", "flocko", "everscream", "ice_queen", "prismatic_lacewing", "empress_of_light", "martian_probe", "martian_drone", "scutlix", "martian_saucer",
        "solar_pillar", "vortex_pillar", "nebula_pillar", "stardust_pillar", "sroller", "corite", "alien_hornet", "nebula_floater", "brain_suckler",
        "star_cell", "flow_invader", "moon_lord", "moon_lord_hand", "moon_lord_head",
        "slime_minion", "hornet_minion", "imp_minion", "optic_minion", "pygmy_minion", "tempest_minion", "ufo_minion", "deadly_sphere_minion",
        "terraprisma_minion", "stardust_cell_minion", "stardust_dragon_minion", "rainbow_crystal", "lunar_portal", "reaper", "mothron", "deadly_sphere",
        "antlion", "vulture", "antlion_charger", "antlion_swarmer", "desert_spirit", "ice_bat", "snow_flinx", "wolf", "ice_golem", "ice_elemental",
        "ice_tortoise", "harpy");

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
        event.registerLayerDefinition(QUEEN_SLIME, SlimeBodyModel::createQueenSlime);
        event.registerLayerDefinition(HEAVENLY_SLIME, SlimeBodyModel::createHeavenlySlime);
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
