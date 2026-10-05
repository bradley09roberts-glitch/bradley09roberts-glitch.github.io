package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/**
 * House prefabs. Frame (HF): x in [-hw, hw] (centre 0 = lane axis), z = 0 is the porch row, the facade wall is the
 * plane z = 1 and its front faces -Z (towards the court / alley); the back wall is z = depth-1. y = 1 is the plinth
 * layer (ground blocks are y = 0). Houses are solid inside (windows glow through a glowstone panel, doors are
 * recessed with a solid back) so nothing can be walked into. Neighbouring houses share the party-wall columns x = +-hw.
 */
final class House {
    private House() {
    }

    enum Kind {HANOK, TOWN, FLAT, GABLE}

    static final class Spec {
        Kind kind = Kind.HANOK;
        int hw = 4;
        int depth = 5;
        String wall = Mat.PLASTER;
        String wall2 = Mat.CALCITE;
        String timber = Mat.LOG_S;
        Roofs.Family roof = Roofs.TILE;
        int number = 1;
        String label = null;         // plaque text instead of the number
        long seed = 1;
        boolean backEave = true;     // roof overhangs the back wall (false against the perimeter wall)
        boolean chimney = false;
        boolean tank = false;
        boolean laundry = false;
        boolean plaque = true;
        boolean lanterns = true;
        boolean tall = false;        // HANOK/GABLE: steeper, taller roof by raising the walls one block
        boolean guard = false;       // FLAT: roof terrace hosts an armed guard post

        Spec kind(Kind k) {
            this.kind = k;
            return this;
        }
    }

    static void build(BuildContext c, Spec s) {
        switch (s.kind) {
            case HANOK -> hanok(c, s);
            case TOWN -> town(c, s);
            case FLAT -> flat(c, s);
            case GABLE -> gable(c, s);
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /** solid mass of the house with weathered plaster, from y1..y2 */
    private static void mass(BuildContext c, Spec s, int y1, int y2) {
        int hw = s.hw, d = s.depth;
        String[] pal = {s.wall, s.wall2, Mat.DIORITE};
        double[] w = {62, 30, 8};
        if (s.wall.equals(Mat.CEMENT)) {
            pal = new String[]{Mat.CEMENT, "minecraft:stone", Mat.ANDESITE};
            w = new double[]{70, 18, 12};
        } else if (s.wall.equals(Mat.CREAM)) {
            pal = new String[]{Mat.CREAM, "minecraft:sandstone", Mat.CALCITE};
            w = new double[]{66, 24, 10};
        }
        c.noise(-hw, y1, 1, hw, y2, d - 1, pal, w);
    }

    /** plinth (stone) under the whole footprint and the wooden porch deck in front. */
    private static void plinth(BuildContext c, Spec s) {
        int hw = s.hw, d = s.depth;
        c.noise(-hw, 1, 0, hw, 1, d - 1, new String[]{Mat.SB, Mat.SB_MOSS, Mat.COBBLE, Mat.SB_CRACK}, new double[]{60, 14, 16, 10});
        c.fill(-hw + 1, 1, 0, hw - 1, 1, 0, Mat.SPRUCE);
        // flower pots along the porch edge
        U.Rnd r = new U.Rnd(s.seed ^ 0x51);
        for (int x = -hw + 1; x <= hw - 1; x++) {
            if (Math.abs(x) >= 2 && r.chance(0.6) && x != 0) {
                Props.pot(c, x, 2, 0, r);
            }
        }
    }

    /** Facade door at x=0 on the facade plane (recessed one block when the house is deep enough) with a plaque. */
    private static void door(BuildContext c, Spec s, int y, boolean doubleHeightLintel) {
        int dz = s.depth >= 4 ? 2 : 1;
        String[] trad = {"minecraft:spruce_door", "minecraft:dark_oak_door", "minecraft:crimson_door", "minecraft:mangrove_door",
                "minecraft:jungle_door", "minecraft:spruce_door"};
        String[] bright = {"minecraft:warped_door", "minecraft:acacia_door", "minecraft:cherry_door", "minecraft:crimson_door",
                "minecraft:birch_door", "minecraft:bamboo_door"};
        U.Rnd dr = new U.Rnd(s.seed ^ 0xD00);
        String base = (s.kind == Kind.FLAT ? bright : trad)[dr.i(6)];
        c.set(0, y, dz, Mat.door(base, "south", false, "left", false));
        c.set(0, y + 1, dz, Mat.door(base, "south", true, "left", false));
        if (dz == 2) {
            c.clear(0, y, 1, 0, y + 1, 1);
            // timber door frame
            c.set(-1, y, 1, Mat.log(s.timber, "y"));
            c.set(1, y, 1, Mat.log(s.timber, "y"));
        }
        c.set(0, y + 2, 1, Mat.PLANKS);
        if (s.plaque) {
            String txt = s.label != null ? s.label : String.valueOf(s.number);
            c.text(0.5, y + 2.5, 0.97, txt, "#F6E7B0", txt.length() > 3 ? 0.9f : 1.6f, 180f, false);
        }
    }

    /** A warm window 'w' wide at x, rows y..y+1 on the facade plane z=1, glowing through a panel at z=2. */
    private static void window(BuildContext c, Spec s, int x1, int x2, int y) {
        if (x1 > x2) {
            return;
        }
        int gz = Math.min(2, s.depth - 1);
        for (int x = x1; x <= x2; x++) {
            for (int dy = 0; dy < 2; dy++) {
                c.set(x, y + dy, 1, Mat.PANE_WARM);
                if (gz > 1) {
                    c.set(x, y + dy, gz, Mat.GLOW);
                }
            }
        }
        if (s.depth <= 3) {
            // shallow relief house: glow panel replaces the back row cells behind the window
            for (int x = x1; x <= x2; x++) {
                for (int dy = 0; dy < 2; dy++) {
                    c.set(x, y + dy, 2, Mat.GLOW);
                }
            }
        }
        // dark sill and lintel plus shutters
        c.fill(x1 - 1 >= -s.hw + 1 ? x1 - 1 : x1, y - 1, 1, x2 + 1 <= s.hw - 1 ? x2 + 1 : x2, y - 1, 1, Mat.SB);
        for (int x = x1; x <= x2; x++) {
            c.set(x, y + 2, 1, Mat.log(s.timber, "x"));
        }
    }

    /** timber posts on the facade at x = -hw, -1, 1, hw, y1..y2 */
    private static void posts(BuildContext c, Spec s, int y1, int y2) {
        int hw = s.hw;
        String post = Mat.log(s.timber, "y");
        c.fill(-hw, y1, 1, -hw, y2, 1, post);
        c.fill(hw, y1, 1, hw, y2, 1, post);
        if (hw >= 3) {
            c.fill(-1, y1, 1, -1, y2, 1, post);
            c.fill(1, y1, 1, 1, y2, 1, post);
        }
    }

    private static void porchPosts(BuildContext c, Spec s, int y1, int y2) {
        int hw = s.hw;
        String post = Mat.log(s.timber, "y");
        c.fill(-hw + 1, y1, 0, -hw + 1, y2, 0, post);
        c.fill(hw - 1, y1, 0, hw - 1, y2, 0, post);
    }

    private static void hangLanterns(BuildContext c, Spec s, int yBeam) {
        if (!s.lanterns) {
            return;
        }
        int hw = s.hw;
        int lx = Math.max(1, hw - 2);
        c.set(-lx, yBeam - 1, 0, Mat.lantern(true));
        c.set(lx, yBeam - 1, 0, Mat.lantern(true));
    }

    private static void chimney(BuildContext c, Spec s, int topY) {
        int cx = s.hw - 1, cz = Math.max(1, s.depth - 2);
        c.fill(cx, 2, cz, cx, topY, cz, Mat.BRICK);
        c.set(cx, topY + 1, cz, Mat.slabB(Mat.SB_SL));
        c.set(cx, topY, cz, "minecraft:bricks");
    }

    /** Back of the house (visible from the alley behind): door with a step and lamp, lit windows, drain pipe. */
    private static void backFeatures(BuildContext c, Spec s, int top) {
        if (!s.backEave || s.depth < 4) {
            return;
        }
        int hw = s.hw, d = s.depth, zb = d - 1;
        U.Rnd r = new U.Rnd(s.seed ^ 0xBAC);
        String base = r.chance(0.5) ? "minecraft:dark_oak_door" : "minecraft:spruce_door";
        c.set(0, 2, zb, Mat.door(base, "north", false, "right", false));
        c.set(0, 3, zb, Mat.door(base, "north", true, "right", false));
        c.set(0, 4, zb, Mat.PLANKS);
        c.set(0, 1, d, Mat.slabB(Mat.SB_SL));
        Props.stoneLamp(c, 2, 1, d);
        for (int x : new int[]{-2, 2}) {
            if (r.chance(0.8)) {
                c.set(x, 3, zb, Mat.PANE_WARM);
                c.set(x, 4, zb, Mat.PANE_WARM);
                c.set(x, 3, zb - 1, Mat.GLOW);
                c.set(x, 4, zb - 1, Mat.GLOW);
            }
        }
        if (r.chance(0.6)) {
            c.fill(-hw + 1, 2, d, -hw + 1, top + 1, d, Mat.BARS);
        }
        if (r.chance(0.35)) {
            Props.barrel(c, -2, 1, d);
        } else if (r.chance(0.35)) {
            Props.crate(c, 3, 1, d, 1 + r.i(2));
        }
    }

    /** TV antenna on a roof: pole with two cross bars. */
    static void antenna(BuildContext c, int x, int y, int z) {
        c.fill(x, y, z, x, y + 3, z, Mat.BARS);
        c.fill(x - 1, y + 3, z, x + 1, y + 3, z, Mat.BARS);
        c.fill(x - 1, y + 2, z, x + 1, y + 2, z, Mat.BARS);
        c.set(x, y + 4, z, "minecraft:lightning_rod[facing=up]");
    }

    // ------------------------------------------------------------------ HANOK: single storey, gable roof along x

    private static void hanok(BuildContext c, Spec s) {
        int hw = s.hw, d = s.depth;
        int top = s.tall ? 5 : 4;      // wall top y
        int beam = top + 1;
        plinth(c, s);
        mass(c, s, 2, top);
        c.fill(-hw, 2, 0, -hw, top, 0, s.wall);
        c.fill(hw, 2, 0, hw, top, 0, s.wall);
        posts(c, s, 2, top);
        // windows and door
        int wx = hw >= 4 ? 2 : (hw == 3 ? 2 : 1);
        window(c, s, -(hw - 1), -wx, 3);
        window(c, s, wx, hw - 1, 3);
        door(c, s, 2, false);
        // porch
        porchPosts(c, s, 2, top);
        c.fill(-hw, beam, 0, hw, beam, 0, Mat.log(s.timber, "x"));
        c.fill(-hw, beam, 1, hw, beam, 1, Mat.log(s.timber, "x"));
        c.fill(-hw, beam, d - 1, hw, beam, d - 1, Mat.log(s.timber, "x"));
        hangLanterns(c, s, beam);
        // roof
        int zf = -1, zb = s.backEave ? d : d - 1;
        int y0 = beam + 1;
        Roofs.gableX(c, -hw, hw, zf, zb, y0, s.roof, beam, s.roof.block(), s.wall);
        // rafter boards under the front eave
        c.fill(-hw, beam, -1, hw, beam, -1, Mat.slabT("minecraft:dark_oak_slab"));
        upturn(c, s, y0, zf, zb);
        if (s.chimney) {
            chimney(c, s, y0 + (zb - zf) / 2 + 1);
        }
        if (s.laundry) {
            Props.laundryX(c, -hw + 1, hw - 1, beam - 1, 0, new U.Rnd(s.seed));
        }
        backFeatures(c, s, top);
    }

    /** Lifted eave corners (upturned tips) at the four roof corners. */
    private static void upturn(BuildContext c, Spec s, int y0, int zf, int zb) {
        int hw = s.hw;
        String st = s.roof.stair();
        // front corners
        c.set(-hw, y0, zf, Mat.log(s.timber, "z"));
        c.set(hw, y0, zf, Mat.log(s.timber, "z"));
        c.set(-hw, y0 + 1, zf, Mat.stair(st, "south"));
        c.set(hw, y0 + 1, zf, Mat.stair(st, "south"));
        if (s.backEave) {
            c.set(-hw, y0, zb, Mat.log(s.timber, "z"));
            c.set(hw, y0, zb, Mat.log(s.timber, "z"));
            c.set(-hw, y0 + 1, zb, Mat.stair(st, "north"));
            c.set(hw, y0 + 1, zb, Mat.stair(st, "north"));
        }
    }

    // ------------------------------------------------------------------ TOWN: two storeys, balcony, hip roof

    private static void town(BuildContext c, Spec s) {
        int hw = s.hw, d = s.depth;
        plinth(c, s);
        mass(c, s, 2, 8);
        c.fill(-hw, 2, 0, -hw, 8, 0, s.wall);
        c.fill(hw, 2, 0, hw, 8, 0, s.wall);
        posts(c, s, 2, 8);
        int wx = hw >= 4 ? 2 : 1;
        // ground floor
        window(c, s, -(hw - 1), -wx, 3);
        window(c, s, wx, hw - 1, 3);
        door(c, s, 2, false);
        // storey line
        c.fill(-hw, 5, 1, hw, 5, 1, Mat.log(s.timber, "x"));
        c.fill(-hw, 9, 1, hw, 9, 1, Mat.log(s.timber, "x"));
        // upper floor windows (taller shutters) and a central glazed door to the balcony
        window(c, s, -(hw - 1), -wx, 7);
        window(c, s, wx, hw - 1, 7);
        for (int y = 6; y <= 7; y++) {
            c.set(0, y, 1, Mat.PANE_WARM);
            if (d > 2) {
                c.set(0, y, 2, Mat.GLOW);
            }
        }
        // balcony deck over the porch, railing
        porchPosts(c, s, 2, 4);
        c.fill(-hw, 5, 0, hw, 5, 0, Mat.log(s.timber, "x"));
        c.fill(-hw + 1, 5, -1, hw - 1, 5, -1, Mat.slabB("minecraft:dark_oak_slab"));
        c.fill(-hw + 1, 6, -1, hw - 1, 6, -1, Mat.FENCE);
        c.fill(-hw + 1, 6, 0, -hw + 1, 6, 0, Mat.FENCE);
        c.fill(hw - 1, 6, 0, hw - 1, 6, 0, Mat.FENCE);
        c.fill(-hw, 6, 0, -hw, 8, 0, s.wall);
        c.fill(hw, 6, 0, hw, 8, 0, s.wall);
        hangLanterns(c, s, 5);
        // hip roof from y=10
        int zf = -1, zb = s.backEave ? d : d - 1;
        c.fill(-hw, 9, 0, hw, 9, 0, Mat.log(s.timber, "x"));
        c.fill(-hw, 9, d - 1, hw, 9, d - 1, Mat.log(s.timber, "x"));
        c.fill(-hw, 9, -1, hw, 9, -1, Mat.slabT("minecraft:dark_oak_slab"));
        Roofs.hip(c, -hw, zf, hw, zb, 10, s.roof, 9, s.roof.block());
        if (s.chimney) {
            chimney(c, s, 12);
        }
        if (s.laundry) {
            Props.laundryX(c, -hw + 2, hw - 2, 8, -1, new U.Rnd(s.seed));
        }
        backFeatures(c, s, 8);
    }

    // ------------------------------------------------------------------ FLAT: concrete house, flat roof terrace, tank

    private static void flat(BuildContext c, Spec s) {
        int hw = s.hw, d = s.depth;
        int floors = s.tall ? 2 : 1;
        int top = floors == 2 ? 8 : 4;
        plinth(c, s);
        mass(c, s, 2, top);
        c.fill(-hw, 2, 0, -hw, top, 0, s.wall);
        c.fill(hw, 2, 0, hw, top, 0, s.wall);
        // window band (cement houses: white frames, blue shutters)
        int wx = hw >= 4 ? 2 : 1;
        window(c, s, -(hw - 1), -wx, 3);
        window(c, s, wx, hw - 1, 3);
        door(c, s, 2, false);
        c.fill(-hw, 5, 1, hw, 5, 1, Mat.BRICK);
        if (floors == 2) {
            window(c, s, -(hw - 1), -1, 7);
            window(c, s, 1, hw - 1, 7);
            c.fill(-hw, 9, 1, hw, 9, 1, Mat.BRICK);
        }
        // corrugated canopy over the door
        String cano = "minecraft:light_blue_concrete";
        c.fill(-2, 5 - (floors == 2 ? 0 : 0), 0, 2, 5, 0, floors == 1 ? "minecraft:blue_concrete" : cano);
        // roof slab + parapet
        int roofY = top + 1;
        c.fill(-hw, roofY, 0, hw, roofY, d - 1, Mat.CEMENT);
        c.fill(-hw, roofY + 1, 0, hw, roofY + 1, 0, Mat.slabB("minecraft:stone_brick_slab"));
        c.fill(-hw, roofY + 1, d - 1, hw, roofY + 1, d - 1, Mat.slabB("minecraft:stone_brick_slab"));
        c.fill(-hw, roofY + 1, 0, -hw, roofY + 1, d - 1, Mat.slabB("minecraft:stone_brick_slab"));
        c.fill(hw, roofY + 1, 0, hw, roofY + 1, d - 1, Mat.slabB("minecraft:stone_brick_slab"));
        hangLanterns(c, s, 5);
        if (s.tank) {
            tank(c, -hw + 2, roofY + 1, Math.max(1, d - 3));
        }
        if (s.laundry) {
            c.fill(hw - 2, roofY + 1, 1, hw - 2, roofY + 2, 1, Mat.FENCE);
            c.fill(hw - 2, roofY + 1, d - 2, hw - 2, roofY + 2, d - 2, Mat.FENCE);
            Props.laundryZ(c, 1, d - 2, roofY + 3, hw - 2, new U.Rnd(s.seed));
        }
        if (s.chimney) {
            c.fill(hw - 1, roofY + 1, d - 2, hw - 1, roofY + 3, d - 2, Mat.BRICK);
            c.set(hw - 1, roofY + 4, d - 2, Mat.slabB(Mat.SB_SL));
        }
        if (s.guard) {
            c.marker("guard.post", 0.5, roofY + 1.0, Math.max(1, d / 2) + 0.5, U.yaw(c, 180f), "rank=triangle");
        } else if (new U.Rnd(s.seed ^ 0xA77).chance(0.6)) {
            antenna(c, hw - 1, roofY + 1, Math.max(1, d / 2));
        }
        backFeatures(c, s, top);
    }

    /** Rooftop water tank: blue barrel 2x2 with a dark lid on iron-bar legs. */
    static void tank(BuildContext c, int x, int y, int z) {
        c.set(x, y, z, Mat.BARS);
        c.set(x + 1, y, z, Mat.BARS);
        c.set(x, y, z + 1, Mat.BARS);
        c.set(x + 1, y, z + 1, Mat.BARS);
        c.fill(x, y + 1, z, x + 1, y + 2, z + 1, "minecraft:blue_concrete");
        c.fill(x, y + 3, z, x + 1, y + 3, z + 1, "minecraft:black_concrete");
        c.set(x, y + 2, z, "minecraft:light_blue_concrete");
    }

    // ------------------------------------------------------------------ GABLE: gable end faces the court

    private static void gable(BuildContext c, Spec s) {
        int hw = s.hw, d = s.depth;
        int top = 5;
        plinth(c, s);
        mass(c, s, 2, top);
        c.fill(-hw, 2, 0, -hw, top, 0, s.wall);
        c.fill(hw, 2, 0, hw, top, 0, s.wall);
        posts(c, s, 2, top);
        int wx = hw >= 4 ? 2 : 1;
        window(c, s, -(hw - 1), -wx, 3);
        window(c, s, wx, hw - 1, 3);
        door(c, s, 2, false);
        c.fill(-hw, 6, 1, hw, 6, 1, Mat.log(s.timber, "x"));
        porchPosts(c, s, 2, 5);
        c.fill(-hw, 6, 0, hw, 6, 0, Mat.log(s.timber, "x"));
        hangLanterns(c, s, 6);
        // gable roof along z, eaves overhang the front by one block (z=-1) and the back if allowed
        int zb = s.backEave ? d : d - 1;
        Roofs.gableZ(c, -hw, hw, -1, zb, 7, s.roof, 6, s.roof.block(), s.wall);
        // attic window in the facade gable triangle (the triangle itself is the roof's front fill at z=-1)
        c.set(0, 8, -1, Mat.PANE_WARM);
        c.set(0, 8, 0, Mat.GLOW);
        c.fill(-hw, 7, -1, hw, 7, -1, Mat.log(s.timber, "x"));
        if (s.chimney) {
            chimney(c, s, 10);
        }
        backFeatures(c, s, top);
    }
}
