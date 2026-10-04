package com.terracraft.world.hardmode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Which Hardmode changes each overworld chunk has received ({@code data/terracraft/hardmode_chunks.dat}): bit 0 the
 * Hallow/evil stripes, bits 1-3 the veins of each blessed Hardmode ore tier.
 */
public final class HardmodeChunks extends SavedData {
    public static final int STRIPES = 1;
    /** Crystal Shards and Gelatin Crystals grown in the underground Hallow (bit 4; bits 1-3 are ore tiers). */
    public static final int CRYSTALS = 1 << 4;

    private static final Codec<HardmodeChunks> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("chunks", Map.of()).forGetter(HardmodeChunks::encoded)
    ).apply(i, HardmodeChunks::new));

    public static final SavedDataType<HardmodeChunks> TYPE = new SavedDataType<>(TerraCraft.id("hardmode_chunks"), HardmodeChunks::new, CODEC, null);

    private final Map<Long, Integer> flags = new HashMap<>();

    public HardmodeChunks() {
    }

    private HardmodeChunks(Map<String, Integer> stored) {
        stored.forEach((k, v) -> flags.put(Long.parseLong(k), v));
    }

    private Map<String, Integer> encoded() {
        Map<String, Integer> out = new HashMap<>();
        flags.forEach((k, v) -> out.put(Long.toString(k), v));
        return out;
    }

    public static HardmodeChunks get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static int tierBit(int tier) {
        return 1 << tier;
    }

    public int flags(long chunk) {
        return flags.getOrDefault(chunk, 0);
    }

    public void setFlags(long chunk, int value) {
        flags.put(chunk, value);
        setDirty();
    }

    /** Forget everything (when Hardmode is switched off again with a command). */
    public void clear() {
        flags.clear();
        setDirty();
    }
}
