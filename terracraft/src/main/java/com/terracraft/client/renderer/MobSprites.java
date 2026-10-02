package com.terracraft.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terracraft.TerraCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

import java.io.InputStream;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprite sheet metadata for enemies, NPCs and bosses. A sprite is {@code textures/entity/mob/<id>.png}: frames
 * stacked vertically (like Terraria's own NPC sheets), with optional {@code <id>.json} next to it:
 * <pre>{"frames": 2, "frame_time": 8, "faces": "left", "rotate": false, "animate": "always", "fullbright": false}</pre>
 * {@code animate} is {@code always} or {@code move} (advance only while walking). {@code rotate} turns the sprite
 * toward the direction of flight (flyers). Resource packs can swap both files, which is how the personal
 * Terraria sprite pack replaces the art.
 */
public final class MobSprites implements ResourceManagerReloadListener {
    public static final MobSprites INSTANCE = new MobSprites();
    private final Map<Identifier, Sprite> cache = new ConcurrentHashMap<>();

    public record Sprite(Identifier texture, int frames, int frameTime, boolean facesLeft, boolean rotate, boolean animateWhileMoving,
                         boolean fullbright, float aspect) {}

    private MobSprites() {}

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        cache.clear();
    }

    public Sprite get(Identifier entityId) {
        return cache.computeIfAbsent(entityId, MobSprites::load);
    }

    private static Sprite load(Identifier entityId) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Identifier texture = Identifier.fromNamespaceAndPath(entityId.getNamespace(), "textures/entity/mob/" + entityId.getPath() + ".png");
        Identifier meta = Identifier.fromNamespaceAndPath(entityId.getNamespace(), "textures/entity/mob/" + entityId.getPath() + ".json");
        int frames = 1;
        int frameTime = 8;
        boolean facesLeft = true;
        boolean rotate = false;
        boolean moving = false;
        boolean fullbright = false;
        Optional<Resource> metaResource = manager.getResource(meta);
        if (metaResource.isPresent()) {
            try (Reader reader = metaResource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                frames = Math.max(1, GsonHelper.getAsInt(json, "frames", 1));
                frameTime = Math.max(1, GsonHelper.getAsInt(json, "frame_time", 8));
                facesLeft = !"right".equals(GsonHelper.getAsString(json, "faces", "left"));
                rotate = GsonHelper.getAsBoolean(json, "rotate", false);
                moving = "move".equals(GsonHelper.getAsString(json, "animate", "always"));
                fullbright = GsonHelper.getAsBoolean(json, "fullbright", false);
            } catch (Exception e) {
                TerraCraft.LOGGER.warn("Bad sprite metadata {}", meta, e);
            }
        }
        float aspect = 1.0F;
        Optional<Resource> image = manager.getResource(texture);
        if (image.isPresent()) {
            try (InputStream in = image.get().open()) {
                byte[] header = in.readNBytes(24);
                int width = readInt(header, 16);
                int height = readInt(header, 20);
                if (width > 0 && height > 0) {
                    aspect = width / (height / (float) frames);
                }
            } catch (Exception e) {
                TerraCraft.LOGGER.warn("Could not read sprite size {}", texture, e);
            }
        }
        return new Sprite(texture, frames, frameTime, facesLeft, rotate, moving, fullbright, aspect);
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16 | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
    }
}
