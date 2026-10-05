package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

import java.util.List;

/**
 * Floor markings that are entities rather than blocks: the queue slot numbers painted flat on the start platform
 * (dark digits on the white slot tiles, readable from behind the queue looking at the gate).
 */
final class Floor {
    private Floor() {
    }

    static void build(BuildContext c) {
        List<Spots.Cell> cells = Spots.queueCells();
        for (int n = 0; n < cells.size(); n++) {
            Spots.Cell k = cells.get(n);
            flatText(c, k.x() + 0.5, Geo.STAND + 0.02, k.z() + 0.5, Integer.toString(n), "#17171F", 1.5f, 180f);
        }
    }

    /** A text display lying on the floor (rotated -90 degrees about X); {@code yaw} turns its reading direction. */
    static void flatText(BuildContext c, double x, double y, double z, String text, String color, float scale, float yaw) {
        String json = "{\"text\":\"" + text + "\",\"color\":\"" + color + "\"}";
        String quoted = "'" + json.replace("\\", "\\\\").replace("'", "\\'") + "'";
        String snbt = "{text:" + quoted + ",billboard:\"fixed\",alignment:\"center\",line_width:200,shadow:false,"
                + "see_through:false,background:0,brightness:{block:15,sky:15},"
                + "transformation:{left_rotation:[-0.70710677f,0f,0f,0.70710677f],right_rotation:[0f,0f,0f,1f],"
                + "translation:[0f,0f,0f],scale:[" + scale + "f," + scale + "f," + scale + "f]}}";
        c.entity("minecraft:text_display", x, y, z, yaw, snbt);
    }
}
