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

    public static final TagKey<Block> METEOR_PROOF = block("meteor_proof");

    /** Voidborn creatures take bonus damage from starlight weapons. */
    public static final TagKey<EntityType<?>> VOIDBORN = entity("voidborn");
    /** Creatures allied with the Eclipse Sovereign (never targeted by its attacks). */
    public static final TagKey<EntityType<?>> SOVEREIGN_ALLIES = entity("sovereign_allies");

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
