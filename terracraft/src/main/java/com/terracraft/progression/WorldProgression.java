package com.terracraft.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent, server-authoritative world progression: boss kills, hardmode, event completions,
 * NPC rescues, world counters and the world's Terraria variants (evil type, ore choices).
 * <p>
 * Stored once per world in the server-global data storage ({@code data/terracraft/world_progression.dat}).
 * All mutation goes through {@link ProgressionManager}, which fires change listeners and syncs clients.
 */
public final class WorldProgression extends SavedData implements ProgressionView {
    private static final Codec<WorldProgression> CODEC = RecordCodecBuilder.create(i -> i.group(
        Identifier.CODEC.listOf().optionalFieldOf("flags", List.of()).forGetter(p -> List.copyOf(p.flags)),
        Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(p -> Map.copyOf(p.counters)),
        WorldVariants.CODEC.optionalFieldOf("variants").forGetter(p -> Optional.ofNullable(p.variants))
    ).apply(i, WorldProgression::new));

    public static final SavedDataType<WorldProgression> TYPE = new SavedDataType<>(
        TerraCraft.id("world_progression"), WorldProgression::new, CODEC, null
    );

    private final Set<Identifier> flags = new LinkedHashSet<>();
    private final Map<Identifier, Integer> counters = new HashMap<>();
    private WorldVariants variants;

    public WorldProgression() {
    }

    private WorldProgression(List<Identifier> flags, Map<Identifier, Integer> counters, Optional<WorldVariants> variants) {
        this.flags.addAll(flags);
        this.counters.putAll(counters);
        this.variants = variants.orElse(null);
    }

    /** Fetches (creating if needed) the progression data of the running server. */
    public static WorldProgression get(MinecraftServer server) {
        WorldProgression data = server.getDataStorage().computeIfAbsent(TYPE);
        if (data.variants == null) {
            data.variants = WorldVariants.fromSeed(server.overworld().getSeed());
            data.setDirty();
            TerraCraft.LOGGER.info("Initialised Terraria world variants: evil={}, ores={}", data.variants.evil(), data.variants.secondaryOre());
        }
        com.terracraft.world.gen.WorldgenVariants.publish(data.variants);
        return data;
    }

    @Override
    public Set<Identifier> storedFlags() {
        return Collections.unmodifiableSet(flags);
    }

    @Override
    public int counter(Identifier id) {
        return counters.getOrDefault(id, 0);
    }

    public Map<Identifier, Integer> counters() {
        return Collections.unmodifiableMap(counters);
    }

    @Override
    public WorldVariants variants() {
        return variants == null ? WorldVariants.DEFAULT : variants;
    }

    // ------------------------------------------------------------------ package-private mutation (via ProgressionManager)

    boolean setStored(Identifier id, boolean value) {
        boolean changed = value ? flags.add(id) : flags.remove(id);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    int addCounter(Identifier id, int delta) {
        int value = Math.max(0, counter(id) + delta);
        counters.put(id, value);
        setDirty();
        return value;
    }

    void setCounter(Identifier id, int value) {
        counters.put(id, Math.max(0, value));
        setDirty();
    }

    void setVariants(WorldVariants variants) {
        this.variants = variants;
        com.terracraft.world.gen.WorldgenVariants.publish(variants);
        setDirty();
    }

    void clearAll() {
        flags.clear();
        counters.clear();
        setDirty();
    }
}
