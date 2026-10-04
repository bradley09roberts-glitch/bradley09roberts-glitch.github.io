package com.squidgame.tournament;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.squidgame.SquidGameMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/** Helpers for the vanilla display entities used for animated doors, signs and machinery (smoothly interpolated, no custom renderer). */
public final class DisplayUtil {
    private DisplayUtil() {
    }

    /** Spawns a block display. {@code translation}/{@code scale} are in blocks, relative to the entity position. */
    public static Display.BlockDisplay spawnBlock(ServerLevel level, Vec3 pos, float yaw, String blockState,
                                                  Vec3 translation, Vec3 scale, String tag) {
        String snbt = "{id:\"minecraft:block_display\",block_state:{Name:\"" + blockName(blockState) + "\"" + props(blockState) + "},"
                + transformation(translation, scale) + ",brightness:{block:15,sky:15},"
                + "interpolation_duration:0,Tags:[\"squidgame_static\",\"" + tag + "\"]}";
        return spawn(level, snbt, pos, yaw, Display.BlockDisplay.class);
    }

    /** Spawns a text display. */
    public static Display.TextDisplay spawnText(ServerLevel level, Vec3 pos, float yaw, String json, float scale, boolean background, String tag) {
        String quoted = "'" + json.replace("\\", "\\\\").replace("'", "\\'") + "'";
        String snbt = "{id:\"minecraft:text_display\",text:" + quoted + ",billboard:\"fixed\",alignment:\"center\",line_width:500,"
                + "shadow:true,background:" + (background ? "1073741824" : "0") + ",brightness:{block:15,sky:15},"
                + transformation(Vec3.ZERO, new Vec3(scale, scale, scale)) + ",Tags:[\"squidgame_static\",\"" + tag + "\"]}";
        return spawn(level, snbt, pos, yaw, Display.TextDisplay.class);
    }

    /** Replaces the text of a text display. */
    public static void setText(Display.TextDisplay d, String json) {
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        t.putString("text", json);
        d.load(t);
    }

    /** Smoothly animates a display to a new translation/scale over {@code ticks} client ticks. */
    public static void animate(Display d, Vec3 translation, Vec3 scale, int ticks) {
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        try {
            CompoundTag tr = TagParser.parseTag("{" + transformation(translation, scale) + "}").getCompound("transformation");
            t.put("transformation", tr);
        } catch (CommandSyntaxException e) {
            SquidGameMod.LOGGER.error("bad transformation", e);
            return;
        }
        t.putInt("start_interpolation", 0);
        t.putInt("interpolation_duration", ticks);
        d.load(t);
    }

    private static String transformation(Vec3 tr, Vec3 sc) {
        return "transformation:{translation:[" + tr.x + "f," + tr.y + "f," + tr.z + "f],scale:[" + sc.x + "f," + sc.y + "f," + sc.z
                + "f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}";
    }

    private static String blockName(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    private static String props(String state) {
        int i = state.indexOf('[');
        if (i < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",Properties:{");
        String[] parts = state.substring(i + 1, state.length() - 1).split(",");
        for (int k = 0; k < parts.length; k++) {
            String[] kv = parts[k].split("=");
            if (k > 0) {
                sb.append(',');
            }
            sb.append(kv[0]).append(":\"").append(kv[1]).append('"');
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T spawn(ServerLevel level, String snbt, Vec3 pos, float yaw, Class<T> type) {
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            Entity e = EntityType.loadEntityRecursive(tag, level, entity -> {
                entity.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
                return entity;
            });
            if (e == null) {
                return null;
            }
            level.addFreshEntity(e);
            return (T) e;
        } catch (CommandSyntaxException | RuntimeException ex) {
            SquidGameMod.LOGGER.error("display spawn failed: {} ({})", ex.getMessage(), snbt);
            return null;
        }
    }
}
