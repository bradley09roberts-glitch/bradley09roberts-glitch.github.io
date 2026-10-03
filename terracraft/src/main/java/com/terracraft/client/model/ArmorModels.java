package com.terracraft.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terracraft.TerraCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 3D armor models. Each set's cubes (helmet crests, horns, pauldrons, knee cops...) are described in
 * {@code assets/terracraft/models/armor/<set>.json}, written by {@code tools/armor_models.py} together with the
 * texture they are painted on. One model is built per set and slot and cached until resources reload.
 */
public final class ArmorModels {
    private static final Map<String, HumanoidModel<HumanoidRenderState>> CACHE = new HashMap<>();
    private static final Map<String, PartPose> POSES = new LinkedHashMap<>();

    static {
        POSES.put("head", PartPose.ZERO);
        POSES.put("body", PartPose.ZERO);
        POSES.put("right_arm", PartPose.offset(-5.0F, 2.0F, 0.0F));
        POSES.put("left_arm", PartPose.offset(5.0F, 2.0F, 0.0F));
        POSES.put("right_leg", PartPose.offset(-1.9F, 12.0F, 0.0F));
        POSES.put("left_leg", PartPose.offset(1.9F, 12.0F, 0.0F));
    }

    private ArmorModels() {}

    public static void clear() {
        CACHE.clear();
    }

    /** The model for one armor piece, or null when the set has no 3D model (the vanilla one is used). */
    public static HumanoidModel<HumanoidRenderState> get(String set, EquipmentSlot slot) {
        String key = set + "/" + slot.getName();
        if (!CACHE.containsKey(key)) {
            CACHE.put(key, build(set, slot));
        }
        return CACHE.get(key);
    }

    private static HumanoidModel<HumanoidRenderState> build(String set, EquipmentSlot slot) {
        var id = TerraCraft.id("models/armor/" + set + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        JsonObject json;
        try (Reader reader = resource.get().openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            TerraCraft.LOGGER.error("Could not read armor model {}", id, e);
            return null;
        }
        String slotName = switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            default -> "legs";
        };
        Map<String, CubeListBuilder> cubes = new HashMap<>();
        for (String part : POSES.keySet()) {
            cubes.put(part, CubeListBuilder.create());
        }
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        // rotated cubes become children of their part; they are added once the parts exist
        record Rotated(String part, float[] pivot, float[] rotation, CubeListBuilder cube) {}
        java.util.List<Rotated> rotated = new java.util.ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            if (!slotName.equals(cube.get("slot").getAsString())) {
                continue;
            }
            String part = cube.get("part").getAsString();
            float[] origin = floats(cube.getAsJsonArray("origin"));
            float[] size = floats(cube.getAsJsonArray("size"));
            float[] uv = floats(cube.getAsJsonArray("uv"));
            CubeDeformation inflate = new CubeDeformation(cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F);
            if (cube.has("pivot")) {
                float[] pivot = floats(cube.getAsJsonArray("pivot"));
                CubeListBuilder builder = CubeListBuilder.create().texOffs((int) uv[0], (int) uv[1])
                    .addBox(origin[0] - pivot[0], origin[1] - pivot[1], origin[2] - pivot[2], size[0], size[1], size[2], inflate);
                rotated.add(new Rotated(part, pivot, floats(cube.getAsJsonArray("rotation")), builder));
            } else {
                cubes.get(part).texOffs((int) uv[0], (int) uv[1]).addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2], inflate);
            }
        }
        for (var entry : POSES.entrySet()) {
            parts.put(entry.getKey(), root.addOrReplaceChild(entry.getKey(), cubes.get(entry.getKey()), entry.getValue()));
        }
        parts.get("head").addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        int index = 0;
        for (Rotated r : rotated) {
            parts.get(r.part).addOrReplaceChild("extra_" + index++, r.cube,
                PartPose.offsetAndRotation(r.pivot[0], r.pivot[1], r.pivot[2], r.rotation[0], r.rotation[1], r.rotation[2]));
        }
        LayerDefinition layer = LayerDefinition.create(mesh, json.get("texture_width").getAsInt(), json.get("texture_height").getAsInt());
        return new HumanoidModel<>(layer.bakeRoot());
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
