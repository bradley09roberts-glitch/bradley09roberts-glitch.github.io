package com.terracraft.mining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.terracraft.TerraCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven pickaxe power requirements.
 * <pre>
 * data/&lt;ns&gt;/terracraft/mining_power/&lt;name&gt;.json
 * { "pickaxe_power": 55, "blocks": ["minecraft:obsidian", "#terracraft:demonite_ores"] }
 * </pre>
 * The server resolves tags into a flat block -> power table and sends it to clients, so client-side break
 * prediction always agrees with the server.
 */
public final class MiningPower {
    private record Entry(int power, List<Block> blocks, List<TagKey<Block>> tags) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    /** Resolved table (server: rebuilt lazily after reload/tag changes; client: received from server). */
    private static final Map<Block, Integer> RESOLVED = new IdentityHashMap<>();
    private static volatile boolean resolved;

    private MiningPower() {}

    /** Required pickaxe power for a block (0 = any pickaxe). */
    public static int required(BlockState state) {
        if (!resolved) {
            resolve();
        }
        return RESOLVED.getOrDefault(state.getBlock(), 0);
    }

    // ------------------------------------------------------------------ loading (server)

    public static synchronized void load(Map<Identifier, JsonElement> files) {
        ENTRIES.clear();
        for (Map.Entry<Identifier, JsonElement> file : files.entrySet()) {
            try {
                JsonObject obj = file.getValue().getAsJsonObject();
                int power = obj.get("pickaxe_power").getAsInt();
                List<Block> blocks = new ArrayList<>();
                List<TagKey<Block>> tags = new ArrayList<>();
                JsonArray array = obj.getAsJsonArray("blocks");
                for (JsonElement element : array) {
                    String text = element.getAsString();
                    if (text.startsWith("#")) {
                        tags.add(TagKey.create(Registries.BLOCK, Identifier.parse(text.substring(1))));
                    } else {
                        Identifier id = Identifier.parse(text);
                        BuiltInRegistries.BLOCK.getOptional(id).ifPresentOrElse(blocks::add,
                            () -> TerraCraft.LOGGER.warn("Unknown block {} in mining power file {}", id, file.getKey()));
                    }
                }
                ENTRIES.add(new Entry(power, blocks, tags));
            } catch (RuntimeException e) {
                TerraCraft.LOGGER.error("Invalid mining power file {}", file.getKey(), e);
            }
        }
        invalidate();
        TerraCraft.LOGGER.info("Loaded {} mining power rules", ENTRIES.size());
    }

    /** Called when data or tags reload. */
    public static void invalidate() {
        resolved = false;
    }

    private static synchronized void resolve() {
        if (ENTRIES.isEmpty() && !RESOLVED.isEmpty()) {
            // Client side: table came from the server.
            resolved = true;
            return;
        }
        RESOLVED.clear();
        for (Entry entry : ENTRIES) {
            for (Block block : entry.blocks) {
                RESOLVED.merge(block, entry.power, Math::max);
            }
            for (TagKey<Block> tag : entry.tags) {
                for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    RESOLVED.merge(holder.value(), entry.power, Math::max);
                }
            }
        }
        resolved = true;
    }

    // ------------------------------------------------------------------ sync

    public static Map<Identifier, Integer> snapshot() {
        if (!resolved) {
            resolve();
        }
        Map<Identifier, Integer> out = new HashMap<>();
        RESOLVED.forEach((block, power) -> out.put(BuiltInRegistries.BLOCK.getKey(block), power));
        return out;
    }

    /** Client: replace the table with the server's. */
    public static synchronized void applySnapshot(Map<Identifier, Integer> table) {
        ENTRIES.clear();
        RESOLVED.clear();
        table.forEach((id, power) -> BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> RESOLVED.put(block, power)));
        resolved = true;
    }
}
