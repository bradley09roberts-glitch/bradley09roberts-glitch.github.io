package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.arena.hub.stairs.Route.Exit;
import com.squidgame.core.util.Rng;

/**
 * The tangle: random chains of platforms, stair flights (up and down, one to three lanes, some solid wedges) and
 * bridges wandering through the free space of the hall. Every element is dry-run first and only built where its volume
 * and head-room are free, so flights cross over and under each other without ever touching. Chains may end in mid-air
 * (stairs to nowhere).
 */
final class Wander {
    private Wander() {
    }

    static int chains, segments;

    private static boolean fits(Ctx k, java.util.function.Consumer<Route> body, Route r) {
        k.probe = true;
        k.conflict = false;
        try {
            body.accept(r.copy());
        } finally {
            k.probe = false;
        }
        boolean ok = !k.conflict;
        k.conflict = false;
        return ok;
    }

    private static int odd(Rng rng, int lo, int hi) {
        int v = rng.rangeInt(lo, hi);
        return v % 2 == 0 ? v + 1 : v;
    }

    private static final int[] RISES = {6, 6, 9, 9, 12, 12, 12, 15, 18, 24};

    static void run(Ctx k, int attempts, int head) {
        int saved = k.head;
        k.head = head;
        Rng rng = k.rng;
        for (int n = 0; n < attempts; n++) {
            Dir d = Dir.values()[rng.nextInt(4)];
            int x = rng.rangeInt(-43, 43), z = rng.rangeInt(-167, -86);
            int H = 3 * rng.rangeInt(0, 20);
            Route r = new Route(k, d, x, z, H, false);
            int lanes = rng.chance(0.74) ? 1 : (rng.chance(0.7) ? 2 : 3);
            int lw = rng.chance(0.82) ? 5 : 3;
            int wp = 0;                                  // width of the flight we arrived on
            int built = 0;
            Look lk = Look.of(rng.nextInt(12), 2);
            int maxSeg = rng.rangeInt(2, 9);
            boolean endsWithFlight = false;
            int lastRise = 0, lastLanes = 1, lastLw = 5;
            for (int s = 0; s < maxSeg; s++) {
                boolean done = false;
                for (int combo = 0; combo < 10 && !done; combo++) {
                    int cl = combo == 0 ? lanes : (rng.chance(0.74) ? 1 : (rng.chance(0.7) ? 2 : 3));
                    int cw = combo == 0 ? lw : (rng.chance(0.82) ? 5 : 3);
                    int wf = Kit.flightWidth(cl, cw);
                    Exit ex = rng.chance(0.4) ? Exit.STRAIGHT : (rng.chance(0.5) ? Exit.LEFT : Exit.RIGHT);
                    boolean bridge = rng.chance(0.2);
                    boolean grand = !bridge && rng.chance(0.12);
                    int len, w;
                    if (bridge) {
                        ex = Exit.STRAIGHT;
                        len = odd(rng, 9, 25);
                        w = Math.max(Math.max(wf, wp), rng.chance(0.5) ? 5 : 7);
                    } else if (ex == Exit.STRAIGHT) {
                        len = odd(rng, 7, grand ? 15 : 11);
                        w = Math.max(Math.max(wf, wp) + 2, odd(rng, 7, grand ? 15 : 11));
                    } else {
                        len = Math.max(wf + 2, odd(rng, 7, grand ? 15 : 11));
                        w = Math.max(wp, odd(rng, 7, grand ? 15 : 11));
                    }
                    if (w % 2 == 0) {
                        w++;
                    }
                    if (len % 2 == 0) {
                        len++;
                    }
                    int rise = (rng.chance(0.55) ? 1 : -1) * RISES[rng.nextInt(RISES.length)];
                    if (r.H + rise > 63 || r.H + rise < 0) {
                        rise = -rise;
                    }
                    if (r.H + rise > 63 || r.H + rise < 0) {
                        continue;
                    }
                    boolean wedge = rise > 0 && r.H <= 3 && rise <= 18 && rng.chance(0.4);
                    Look flightLook = rng.chance(0.5) ? lk : Look.of(rng.nextInt(12), 2);
                    final Look fk = wedge ? flightLook.withDepth(100) : flightLook;
                    int pat = bridge ? Kit.PAT_STRIPE : rng.nextInt(3);
                    final int fl = len, fw = w, fr = rise, fcl = cl, fcw = cw, fp = pat;
                    final Exit fe = ex;
                    final Look flk = lk;
                    if (fits(k, t -> {
                        t.landing(fl, fw, fe, flk, fp);
                        t.flight(fr, fcl, fcw, fk);
                    }, r)) {
                        r.landing(len, w, ex, lk.withDepth(3), pat);
                        r.flight(rise, cl, cw, fk);
                        built++;
                        segments += 2;
                        wp = wf;
                        lanes = cl;
                        lw = cw;
                        done = true;
                        endsWithFlight = true;
                        lastRise = rise;
                        lastLanes = cl;
                        lastLw = cw;
                    }
                }
                if (!done) {
                    // no flight fits: end the chain with just a landing (a platform on its own) if it fits
                    if (s == 0 && !endsWithFlight) {
                        int len = odd(rng, 7, 11), w = odd(rng, 7, 11);
                        final int fl = len, fw = w;
                        final Look flk = lk;
                        if (fits(k, t -> t.landing(fl, fw, Exit.STRAIGHT, flk, Kit.PAT_FRAME), r) && rng.chance(0.5)) {
                            r.landing(len, w, Exit.STRAIGHT, lk.withDepth(3), Kit.PAT_FRAME);
                            built++;
                            segments++;
                        }
                    }
                    break;
                }
                lk = rng.chance(0.4) ? lk : Look.of(rng.nextInt(12), 2);
            }
            if (endsWithFlight) {
                // close the open end of a flight that leads nowhere
                Kit.cap(k, r.d, r.cx + r.d.dx, r.cz + r.d.dz, lastRise > 0 ? r.H : r.H + 1, lastLanes, lastLw);
            }
            if (built > 0) {
                chains++;
            }
        }
        k.head = saved;
    }
}
