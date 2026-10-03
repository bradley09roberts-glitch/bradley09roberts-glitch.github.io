package com.terracraft.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terracraft.TerraCraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A creature model described in {@code assets/terracraft/models/creature/<name>.json} (written by
 * tools/creature_models.py): a tree of parts with cubes, and animation roles for some of them. Used for creatures
 * with a shape of their own (Imp, Demons, Eater of Souls, Crimera, Face Monster, Blood Crawler, Man Eater, Snatcher,
 * Meteor Head). The JSON is read from the mod jar when layers are registered.
 */
public class JsonCreatureModel extends EntityModel<TerraRenderState> {
    private record Anim(ModelPart part, String role, float amp, float speed, float phase) {}

    private final List<Anim> anims = new ArrayList<>();

    public JsonCreatureModel(ModelPart root, String name) {
        this(root, read(name));
    }

    public static LayerDefinition create(String name) {
        JsonObject json = read(name);
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put(null, mesh.getRoot());
        for (JsonElement element : json.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            String parentName = part.get("parent").isJsonNull() ? null : part.get("parent").getAsString();
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement c : part.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                float[] o = floats(cube.getAsJsonArray("origin"));
                float[] s = floats(cube.getAsJsonArray("size"));
                float[] uv = floats(cube.getAsJsonArray("uv"));
                float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;
                cubes.texOffs((int) uv[0], (int) uv[1]).addBox(o[0], o[1], o[2], s[0], s[1], s[2], new CubeDeformation(inflate));
            }
            float[] pivot = floats(part.getAsJsonArray("pivot"));
            float[] rot = floats(part.getAsJsonArray("rotation"));
            PartDefinition parent = defs.get(parentName);
            defs.put(part.get("name").getAsString(), parent.addOrReplaceChild(part.get("name").getAsString(), cubes,
                PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0], rot[1], rot[2])));
        }
        return LayerDefinition.create(mesh, json.get("texture_width").getAsInt(), json.get("texture_height").getAsInt());
    }

    /** Finds every named part by following the parent chain recorded in the JSON, and its animations. */
    private JsonCreatureModel(ModelPart root, JsonObject json) {
        super(root);
        Map<String, ModelPart> parts = new HashMap<>();
        for (JsonElement element : json.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            String n = part.get("name").getAsString();
            ModelPart parent = part.get("parent").isJsonNull() ? root : parts.get(part.get("parent").getAsString());
            parts.put(n, parent.getChild(n));
        }
        for (JsonElement element : json.getAsJsonArray("anims")) {
            JsonObject a = element.getAsJsonObject();
            ModelPart part = parts.get(a.get("part").getAsString());
            if (part != null) {
                anims.add(new Anim(part, a.get("role").getAsString(), a.get("amp").getAsFloat(), a.get("speed").getAsFloat(),
                    a.get("phase").getAsFloat()));
            }
        }
    }

    @Override
    public void setupAnim(TerraRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float walk = state.walkAnimationPos;
        float walkSpeed = Math.min(1.0F, state.walkAnimationSpeed);
        for (Anim a : anims) {
            float wave = Mth.sin(age * a.speed + a.phase);
            ModelPart p = a.part;
            switch (a.role) {
                case "head" -> {
                    p.yRot += state.yRot * Mth.DEG_TO_RAD;
                    p.xRot += state.xRot * Mth.DEG_TO_RAD;
                }
                case "leg_r" -> p.xRot += Mth.cos(walk * 0.6662F) * 1.4F * walkSpeed * a.amp;
                case "leg_l" -> p.xRot += Mth.cos(walk * 0.6662F + Mth.PI) * 1.4F * walkSpeed * a.amp;
                case "arm_r" -> p.xRot += Mth.cos(walk * 0.6662F + Mth.PI) * walkSpeed * a.amp;
                case "arm_l" -> p.xRot += Mth.cos(walk * 0.6662F) * walkSpeed * a.amp;
                case "wing_r" -> p.zRot += wave * a.amp;
                case "wing_l" -> p.zRot -= wave * a.amp;
                case "tail", "wiggle" -> p.yRot += wave * a.amp;
                case "jaw" -> p.xRot += (wave + 1.0F) * 0.5F * a.amp;
                case "bob" -> p.y += wave * a.amp;
                case "dangle", "cast" -> p.xRot += wave * a.amp;
                case "spider" -> p.yRot += Mth.sin(walk * 1.3F + a.phase) * a.amp * walkSpeed + wave * 0.03F;
                case "flicker" -> {
                    p.xScale = 1.0F + wave * a.amp;
                    p.yScale = 1.0F + wave * a.amp;
                }
                default -> { }
            }
        }
    }

    private static JsonObject read(String name) {
        String path = "/assets/" + TerraCraft.MODID + "/models/creature/" + name + ".json";
        try (InputStream in = JsonCreatureModel.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing creature model " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read creature model " + path, e);
        }
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
