package com.terracraft.entity.mob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of Terraria enemy definitions, keyed by entity type id. Filled by {@code MobContent}. */
public final class TerrariaMobs {
    private static final Map<Identifier, MobDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final MobDefinition FALLBACK = MobDefinition.builder().build();

    private TerrariaMobs() {}

    public static void define(Identifier id, MobDefinition definition) {
        DEFINITIONS.put(id, definition);
    }

    public static MobDefinition definition(EntityType<?> type) {
        return DEFINITIONS.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(type), FALLBACK);
    }

    public static Map<Identifier, MobDefinition> all() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }
}
