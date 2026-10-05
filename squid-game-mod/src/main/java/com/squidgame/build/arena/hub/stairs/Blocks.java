package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.StateString;

/**
 * Classification of block-state strings for the walk and light models. Info word layout:
 * bits 0-2 kind, bit 3 opaque (blocks light; stairs and slabs are treated as opaque = pessimistic), bits 4-7 emission,
 * bits 8-15 extra (slab type, or stair facing | half << 2 | shape << 3).
 */
final class Blocks {
    private Blocks() {
    }

    static final int NONE = 0, FULL = 1, THIN = 2, SLAB = 3, STAIR = 4;

    static int kind(int info) {
        return info & 7;
    }

    static boolean opaque(int info) {
        return (info & 8) != 0;
    }

    static int emit(int info) {
        return (info >> 4) & 15;
    }

    static int extra(int info) {
        return (info >> 8) & 255;
    }

    static int classify(String state) {
        if (state == null) {
            return NONE;
        }
        String id = StateString.blockId(state);
        int bar = id.indexOf(':');
        String ns = bar < 0 ? "minecraft" : id.substring(0, bar);
        String name = bar < 0 ? id : id.substring(bar + 1);
        int emit = emission(ns, name, state);
        switch (name) {
            case "air", "cave_air", "void_air", "structure_void" -> {
                return NONE;
            }
            case "light" -> {
                return NONE | (emit << 4);
            }
            case "torch", "wall_torch" -> {
                return NONE | (emit << 4);
            }
            default -> {
            }
        }
        if (name.endsWith("_stairs")) {
            String f = StateString.property(state, "facing");
            String h = StateString.property(state, "half");
            String sh = StateString.property(state, "shape");
            int facing = switch (f == null ? "north" : f) {
                case "east" -> 1;
                case "south" -> 2;
                case "west" -> 3;
                default -> 0;
            };
            int half = "top".equals(h) ? 1 : 0;
            int shape = switch (sh == null ? "straight" : sh) {
                case "inner_left" -> 1;
                case "inner_right" -> 2;
                case "outer_left" -> 3;
                case "outer_right" -> 4;
                default -> 0;
            };
            return STAIR | 8 | (emit << 4) | ((facing | half << 2 | shape << 3) << 8);
        }
        if (name.endsWith("_slab")) {
            String t = StateString.property(state, "type");
            int type = "top".equals(t) ? 1 : "double".equals(t) ? 2 : 0;
            if (type == 2) {
                return FULL | 8 | (emit << 4);
            }
            return SLAB | 8 | (emit << 4) | (type << 8);
        }
        if (name.endsWith("_pane") || name.equals("iron_bars") || name.endsWith("fence") || name.endsWith("_wall")
                || name.equals("chain") || name.equals("end_rod") || name.equals("lantern") || name.equals("soul_lantern")
                || name.equals("candle") || name.endsWith("_candle")) {
            return THIN | (emit << 4);
        }
        if (name.endsWith("glass")) {
            return FULL | (emit << 4);          // collides like a block but lets light through
        }
        if (name.equals("invisible_wall") || name.equals("barrier")) {
            return FULL | (emit << 4);
        }
        return FULL | 8 | (emit << 4);
    }

    private static int emission(String ns, String name, String state) {
        if (ns.equals("squidgame")) {
            return name.startsWith("panel_light") ? 15 : 0;
        }
        switch (name) {
            case "sea_lantern", "glowstone", "shroomlight", "lantern", "jack_o_lantern", "ochre_froglight",
                 "verdant_froglight", "pearlescent_froglight" -> {
                return 15;
            }
            case "end_rod", "torch", "wall_torch" -> {
                return 14;
            }
            case "soul_lantern" -> {
                return 10;
            }
            case "redstone_lamp" -> {
                return "true".equals(StateString.property(state, "lit")) ? 15 : 0;
            }
            case "light" -> {
                String l = StateString.property(state, "level");
                try {
                    return l == null ? 15 : Integer.parseInt(l);
                } catch (NumberFormatException e) {
                    return 15;
                }
            }
            default -> {
                return 0;
            }
        }
    }

    // ------------------------------------------------------------------ collision of a quadrant of a block

    /** Is quadrant (qx, qz) of the block solid in its lower (layer 0) or upper (layer 1) half? */
    static boolean solidHalf(int info, int qx, int qz, int layer) {
        switch (kind(info)) {
            case NONE:
                return false;
            case FULL:
            case THIN:
                return true;
            case SLAB: {
                int type = extra(info);
                return type == 0 ? layer == 0 : type == 1 ? layer == 1 : true;
            }
            case STAIR: {
                int e = extra(info);
                int facing = e & 3, half = (e >> 2) & 1, shape = e >> 3;
                boolean base = half == 0 ? layer == 0 : layer == 1;
                if (base) {
                    return true;
                }
                boolean stepLayer = half == 0 ? layer == 1 : layer == 0;
                if (!stepLayer) {
                    return false;
                }
                // rotate the quadrant back into the north-facing frame (counter-clockwise by 'facing' quarter turns)
                int x = qx, z = qz;
                for (int i = 0; i < facing; i++) {
                    int nx = z, nz = 1 - x;
                    x = nx;
                    z = nz;
                }
                return switch (shape) {
                    case 1 -> z == 0 || x == 0;        // inner_left: front half + back-left
                    case 2 -> z == 0 || x == 1;        // inner_right: front half + back-right
                    case 3 -> z == 0 && x == 0;        // outer_left: front-left only
                    case 4 -> z == 0 && x == 1;        // outer_right: front-right only
                    default -> z == 0;                 // straight
                };
            }
            default:
                return false;
        }
    }
}
