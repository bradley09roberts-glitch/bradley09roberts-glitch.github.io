package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.arena.hub.corridor.Plan.Kind;
import com.squidgame.build.arena.hub.corridor.Plan.Space;

/**
 * Floor / wall / ceiling block patterns of every space. Walls are one block thick and shared between two spaces, so a
 * wall pattern only depends on the position along the wall and the height (corridor patterns win shared walls).
 */
final class Surfaces {
    private Surfaces() {
    }

    // ------------------------------------------------------------------ helpers

    /** Position along the long axis of a corridor. */
    static int u(Space s, int x, int z) {
        return s.kind == Kind.CORRIDOR_X ? x : z;
    }

    /** Signed distance from the corridor axis (-3..3 for the 7-wide corridors). */
    static int v(Space s, int x, int z) {
        return s.kind == Kind.CORRIDOR_X ? z - (s.z0 + s.z1) / 2 : x - (s.x0 + s.x1) / 2;
    }

    // ------------------------------------------------------------------ floors (y = -1)

    static String floor(Space s, int x, int z) {
        switch (s.name) {
            case "checkpoint":
                return checkpointFloor(s, x, z);
            case "j1":
                return junctionFloor(s, x, z);
            case "e2_vestibule":
                return vestibuleFloor(s, x, z);
            case "corner_se":
            case "corner_sw":
            case "corner_ne":
            case "corner_nw":
                return cornerFloor(s, x, z);
            case "e1":
            case "n1":
            case "n2":
            case "e2":
            case "branch":
                return corridorFloor(s, x, z);
            case "canteen":
                return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_PINK;
            case "infirmary":
                return ((x >> 1) + (z >> 1) & 1) == 0 ? Pal.TILE_WHITE : "minecraft:light_gray_concrete";
            case "barracks":
                return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_BLACK;
            case "monitor":
                return Pal.TILE_BLACK;
            case "armory":
                return ((x + z) & 1) == 0 ? "minecraft:gray_concrete" : "minecraft:black_concrete";
            case "office":
                return Pal.TILE_BLACK;
            case "store":
                return "minecraft:light_gray_concrete";
            default:
                return Pal.TILE_WHITE;
        }
    }

    private static String corridorFloor(Space s, int x, int z) {
        String inlay = Inlays.at(x, z);
        if (inlay != null && Plan.at(x, z) == s) {
            return inlay;
        }
        int v = Math.abs(v(s, x, z));
        String arrow = Inlays.arrow(s, x, z);
        if (arrow != null) {
            return arrow;
        }
        return switch (v) {
            case 3 -> Pal.TILE_BLACK;
            case 2 -> Pal.TILE_WHITE;
            default -> Pal.TILE_PINK;
        };
    }

    private static String checkpointFloor(Space s, int x, int z) {
        // black and white checkerboard with a pink lane (x -1..1) leading to the north gate
        if (Math.abs(x) <= 1) {
            return Pal.TILE_PINK;
        }
        if (Math.abs(x) == 2) {
            return Pal.TILE_WHITE;
        }
        return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_BLACK;
    }

    private static String junctionFloor(Space s, int x, int z) {
        // centred on (0,-48): pink disc inside a black ring on white
        double dx = x - 0, dz = z + 48;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r <= 1.6) {
            return Pal.TILE_PINK;
        }
        if (r <= 2.7) {
            return Pal.TILE_BLACK;
        }
        if (r <= 3.7) {
            return Pal.TILE_WHITE;
        }
        return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_BLACK;
    }

    private static String cornerFloor(Space s, int x, int z) {
        double cx = (s.x0 + s.x1) / 2.0, cz = (s.z0 + s.z1) / 2.0;
        double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
        if (r <= 1.2) {
            return Pal.TILE_PINK;
        }
        if (r <= 2.3) {
            return Pal.TILE_BLACK;
        }
        if (r <= 3.3) {
            return Pal.TILE_WHITE;
        }
        return ((x + z) & 1) == 0 ? Pal.TILE_BLACK : Pal.TILE_WHITE;
    }

    private static String vestibuleFloor(Space s, int x, int z) {
        int ax = Math.abs(x);
        if (ax <= 3) {
            // rainbow runner towards the stairway entrance
            return Pal.pastel(x + 3);
        }
        if (ax == 4) {
            return Pal.TILE_BLACK;
        }
        return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_PINK;
    }

    // ------------------------------------------------------------------ ceilings (y = h)

    static String ceiling(Space s, int x, int z) {
        if (s.corridor()) {
            int v = v(s, x, z);
            int u = u(s, x, z);
            if (Math.floorMod(u, 4) == 0) {
                return Pal.BEAM;           // beam
            }
            if (v == 0) {
                return Pal.LIGHT_WHITE;    // light strip
            }
            return Pal.WHITE;
        }
        return Pal.WHITE;
    }

    // ------------------------------------------------------------------ walls

    /**
     * Block of the wall column cell at height y. {@code dir} = direction from the wall into the room that is being
     * painted (0 N, 1 E, 2 S, 3 W); {@code u} = coordinate along the wall (x for N/S facing walls, z for E/W).
     */
    static String wall(Space s, int u, int y, int dir) {
        return switch (s.name) {
            case "n1" -> pastelWall(s, u, y);
            case "n2" -> securityWall(s, u, y);
            case "e2" -> clinicalWall(s, u, y);
            case "branch" -> executiveWall(s, u, y);
            case "e1", "j1", "e2_vestibule", "corner_se", "corner_sw", "corner_ne", "corner_nw" -> guardWall(s, u, y);
            case "checkpoint" -> checkpointWall(s, u, y);
            default -> roomWall(s, u, y);
        };
    }

    private static String strip(String light) {
        return light;
    }

    /** The signature guard wall: light strip, black skirting, pink panels between black ribs, white band, quartz cornice. */
    private static String guardWall(Space s, int u, int y) {
        boolean rib = Math.floorMod(u, 4) == 0;
        return switch (y) {
            case 0 -> Pal.LIGHT_WHITE;
            case 1 -> Pal.BLACK;
            case 2, 3 -> rib ? Pal.RIB : Pal.PINK;
            case 4 -> rib ? Pal.RIB : Pal.WHITE;
            case 5 -> rib ? Pal.RIB : Pal.QUARTZ;
            case 6, 7 -> rib ? Pal.RIB : Pal.WHITE;
            default -> Pal.BLACK;
        };
    }

    private static String checkpointWall(Space s, int u, int y) {
        boolean rib = Math.floorMod(u, 4) == 2;
        return switch (y) {
            case 0 -> Pal.LIGHT_WHITE;
            case 1 -> Pal.BLACK;
            case 2, 3, 4 -> rib ? Pal.BLACK : Pal.PINK;
            case 5 -> Pal.BLACK;
            case 6, 7 -> rib ? Pal.PINK : Pal.WHITE;
            default -> Pal.BLACK;
        };
    }

    private static String pastelWall(Space s, int u, int y) {
        // 3-block modules, one pastel colour each (pink -> peach -> yellow -> mint -> sky -> lilac -> cream)
        int module = Math.floorDiv(-52 - u, 3);
        boolean rib = Math.floorMod(-52 - u, 3) == 2 && false;
        return switch (y) {
            case 0 -> Pal.LIGHT_WHITE;
            case 1 -> Pal.BLACK;
            case 2, 3, 4 -> Pal.pastel(module);
            case 5 -> Pal.WHITE;
            default -> Pal.WHITE;
        };
    }

    private static String securityWall(Space s, int u, int y) {
        boolean rib = Math.floorMod(u, 4) == 0;
        return switch (y) {
            case 0 -> Pal.LIGHT_PINK;
            case 1 -> Pal.BLACK;
            case 2, 3 -> rib ? Pal.PINK : Pal.GRAY;
            case 4 -> rib ? Pal.PINK : Pal.BLACK;
            case 5 -> Pal.BLACK;
            default -> Pal.BLACK;
        };
    }

    private static String clinicalWall(Space s, int u, int y) {
        boolean rib = Math.floorMod(u, 6) == 0;
        return switch (y) {
            case 0 -> Pal.LIGHT_WHITE;
            case 1 -> Pal.GRAY;
            case 2, 3, 4 -> rib ? Pal.BLACK : Pal.WHITE;
            case 5 -> rib ? Pal.BLACK : Pal.PINK;
            default -> Pal.WHITE;
        };
    }

    private static String executiveWall(Space s, int u, int y) {
        return switch (y) {
            case 0 -> Pal.LIGHT_PINK;
            case 1 -> Pal.BLACK;
            case 2, 3, 4 -> Pal.BLACK;
            case 5 -> Pal.PINK;
            default -> Pal.BLACK;
        };
    }

    private static String roomWall(Space s, int u, int y) {
        String light = switch (s.name) {
            case "barracks" -> Pal.LIGHT_WARM;
            case "canteen", "infirmary", "office" -> Pal.LIGHT_WHITE;
            case "monitor" -> Pal.LIGHT_PINK;
            default -> Pal.LIGHT_WHITE;
        };
        return switch (y) {
            case 0 -> light;
            case 1 -> Pal.BLACK;
            case 2, 3 -> switch (s.name) {
                case "monitor", "armory", "store" -> Pal.GRAY;
                default -> Pal.PINK;
            };
            default -> switch (s.name) {
                case "monitor" -> Pal.BLACK;
                case "armory", "store" -> Pal.LGRAY;
                default -> Pal.WHITE;
            };
        };
    }
}
