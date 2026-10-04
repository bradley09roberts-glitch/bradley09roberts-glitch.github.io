package com.squidgame.build;

import com.squidgame.core.GameKind;

/**
 * The structures that make up the tournament complex. Each lives in its own region of the
 * tournament dimension, 1000 blocks apart, so they never share chunks: one arena can be loaded,
 * built and unloaded without touching the others, and the area is clearly isolated.
 */
public enum ArenaId {
    /** Dormitory, guard corridors, stairway maze and control room: one connected structure. */
    HUB("hub", 0, 0, null),
    RED_LIGHT("red_light", 1000, 0, GameKind.RED_LIGHT),
    DALGONA("dalgona", 2000, 0, GameKind.DALGONA),
    TUG_OF_WAR("tug_of_war", 3000, 0, GameKind.TUG_OF_WAR),
    MARBLES("marbles", 4000, 0, GameKind.MARBLES),
    GLASS_BRIDGE("glass_bridge", 5000, 0, GameKind.GLASS_BRIDGE),
    FINAL("final", 6000, 0, GameKind.FINAL);

    /** World Y that local y = 0 maps to. Local y may range from -128 to +255 (world -64..319). */
    public static final int ORIGIN_Y = 64;

    public final String id;
    public final int originX;
    public final int originZ;
    public final GameKind game;

    ArenaId(String id, int originX, int originZ, GameKind game) {
        this.id = id;
        this.originX = originX;
        this.originZ = originZ;
        this.game = game;
    }

    public static ArenaId forGame(GameKind game) {
        for (ArenaId a : values()) {
            if (a.game == game) {
                return a;
            }
        }
        throw new IllegalArgumentException(game.toString());
    }

    public static ArenaId byId(String id) {
        for (ArenaId a : values()) {
            if (a.id.equals(id) || a.name().equalsIgnoreCase(id)) {
                return a;
            }
        }
        return null;
    }
}
