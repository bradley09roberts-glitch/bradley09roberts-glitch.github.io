package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.world.gen.ArchipelagoBiomeSource;
import com.grandline.world.gen.ArchipelagoChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, GrandLine.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, GrandLine.MODID);

    static {
        CHUNK_GENERATORS.register("archipelago", () -> ArchipelagoChunkGenerator.CODEC);
        BIOME_SOURCES.register("archipelago", () -> ArchipelagoBiomeSource.CODEC);
    }

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
    }

    private ModWorldgen() {}
}
