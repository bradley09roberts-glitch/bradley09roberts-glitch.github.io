package com.terracraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.terracraft.TerraCraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loads every JSON file of a datapack directory ({@code data/<ns>/<directory>/**.json}) and hands the parsed
 * map to a consumer. All TerraCraft datapack content (mining rules, recipes, spawn tables, shops...) uses it.
 */
public class JsonDataLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    private final FileToIdConverter lister;
    private final String directory;
    private final Consumer<Map<Identifier, JsonElement>> consumer;

    public JsonDataLoader(String directory, Consumer<Map<Identifier, JsonElement>> consumer) {
        this.directory = directory;
        this.lister = FileToIdConverter.json(directory);
        this.consumer = consumer;
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> result = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier id = lister.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                result.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                TerraCraft.LOGGER.error("Failed to read {} data file {}", directory, entry.getKey(), e);
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        consumer.accept(data);
    }
}
