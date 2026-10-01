package com.starforged.registry;

import com.starforged.Starforged;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class ModTags {
    public static final TagKey<Item> STARMETAL_REPAIR = item("starmetal_repair_materials");
    public static final TagKey<Item> ECLIPSE_REPAIR = item("eclipse_repair_materials");
    public static final TagKey<Item> STARLING_FOOD = item("starling_food");
    public static final TagKey<Item> SUNSTEEL_REPAIR = item("sunsteel_repair_materials");
    public static final TagKey<Item> SOLAR_REPAIR = item("solar_repair_materials");

    public static final TagKey<Item> MOONSILVER_REPAIR = item("moonsilver_repair_materials");
    public static final TagKey<Item> TIDAL_REPAIR = item("tidal_repair_materials");

    public static final TagKey<Item> AETHERIUM_REPAIR = item("aetherium_repair_materials");
    public static final TagKey<Item> TEMPEST_REPAIR = item("tempest_repair_materials");

    public static final TagKey<Block> METEOR_PROOF = block("meteor_proof");
    /** Blocks Aetherium tools cannot harvest (none - top tier). */
    public static final TagKey<Block> INCORRECT_FOR_AETHERIUM = block("incorrect_for_aetherium_tool");
    /** Blocks Sunsteel tools cannot harvest (Moonsilver Ore). */
    public static final TagKey<Block> INCORRECT_FOR_SUNSTEEL = block("incorrect_for_sunsteel_tool");
    /** Blocks Moonsilver tools cannot harvest (none yet - top tier). */
    public static final TagKey<Block> INCORRECT_FOR_MOONSILVER = block("incorrect_for_moonsilver_tool");
    /** Light sources a Lunar Moth will snuff out. */
    public static final TagKey<Block> MOTH_LIGHTS = block("lunar_moth_lights");

    /** Voidborn creatures take bonus damage from starlight weapons. */
    public static final TagKey<EntityType<?>> VOIDBORN = entity("voidborn");
    /** Creatures allied with the Eclipse Sovereign (never targeted by its attacks). */
    public static final TagKey<EntityType<?>> SOVEREIGN_ALLIES = entity("sovereign_allies");

    /** Creatures that fight for the Sun Warden. */
    public static final TagKey<EntityType<?>> WARDEN_ALLIES = entity("warden_allies");
    /** Creatures that fight for the Pale Matriarch. */
    public static final TagKey<EntityType<?>> MATRIARCH_ALLIES = entity("matriarch_allies");
    /** Creatures of the Pale Reach that grow stronger at high tide. */
    public static final TagKey<EntityType<?>> TIDEBOUND = entity("tidebound");
    /** Creatures that fight for Veyr, the Tempest Regent. */
    public static final TagKey<EntityType<?>> REGENT_ALLIES = entity("regent_allies");
    /** Creatures of Stormreach: immune to the storm's lightning. */
    public static final TagKey<EntityType<?>> STORMBORN = entity("stormborn");
    public static final TagKey<Structure> TEMPEST_CITADELS = TagKey.create(Registries.STRUCTURE, Starforged.id("tempest_citadels"));
    public static final TagKey<Structure> TIDAL_ORRERIES = TagKey.create(Registries.STRUCTURE, Starforged.id("tidal_orreries"));
    public static final TagKey<Structure> SUN_TEMPLES = TagKey.create(Registries.STRUCTURE, Starforged.id("sun_temples"));
    public static final TagKey<Structure> OBSERVATORIES = TagKey.create(Registries.STRUCTURE, Starforged.id("observatories"));

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, Starforged.id(name));
    }

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, Starforged.id(name));
    }

    private static TagKey<EntityType<?>> entity(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, Starforged.id(name));
    }

    private ModTags() {
    }
}
