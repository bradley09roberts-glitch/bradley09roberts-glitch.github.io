package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;
import com.squidgame.build.StateString;

/**
 * Mirror-symmetric drawing: the hall is symmetric about the plane x = 0 (block x maps to -x-1), so most structure is
 * written once on the west half and mirrored (facing / stair shapes are mirrored with the block).
 */
final class Sym {
    private Sym() {
    }

    static int mx(int x) {
        return -x - 1;
    }

    static void fill(BuildContext c, int x1, int y1, int z1, int x2, int y2, int z2, String s) {
        c.fill(x1, y1, z1, x2, y2, z2, s);
        c.fill(mx(x1), y1, z1, mx(x2), y2, z2, StateString.mirrorX(s));
    }

    static void set(BuildContext c, int x, int y, int z, String s) {
        c.set(x, y, z, s);
        c.set(mx(x), y, z, StateString.mirrorX(s));
    }

    static void line(BuildContext c, int x1, int y1, int z1, int x2, int y2, int z2, String s) {
        c.line(x1, y1, z1, x2, y2, z2, s);
        c.line(mx(x1), y1, z1, mx(x2), y2, z2, StateString.mirrorX(s));
    }

    static void clear(BuildContext c, int x1, int y1, int z1, int x2, int y2, int z2) {
        c.clear(x1, y1, z1, x2, y2, z2);
        c.clear(mx(x1), y1, z1, mx(x2), y2, z2);
    }

    /** Pattern fill on the west half; the east half gets the pattern evaluated at the mirrored coordinate. */
    static void pattern(BuildContext c, int x1, int y1, int z1, int x2, int y2, int z2, BuildContext.TriFunction f) {
        c.pattern(x1, y1, z1, x2, y2, z2, f);
        c.pattern(mx(x1), y1, z1, mx(x2), y2, z2, (x, y, z) -> {
            String s = f.apply(mx(x), y, z);
            return s == null ? null : StateString.mirrorX(s);
        });
    }
}
