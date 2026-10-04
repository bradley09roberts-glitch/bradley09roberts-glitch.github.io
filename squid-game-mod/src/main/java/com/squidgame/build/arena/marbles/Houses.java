package com.squidgame.build.arena.marbles;

/** Chooses house designs / colours for each plot (deterministic by slot). */
final class Houses {
    private Houses() {
    }

    static House.Spec specFor(Layout.Slot s) {
        U.Rnd r = new U.Rnd(500 + s.ox * 31L + s.oz * 17L + s.rot);
        House.Spec h = new House.Spec();
        h.hw = 4;
        h.depth = s.houseDepth;
        h.seed = r.next();
        boolean relief = s.houseDepth <= 4 && (s.zone.equals("CW1") || s.zone.equals("CE1") || s.zone.equals("N") || s.zone.equals("SB"));
        h.backEave = !relief;
        // kind
        double q = r.d();
        if (s.houseDepth <= 3) {
            h.kind = q < 0.5 ? House.Kind.HANOK : q < 0.8 ? House.Kind.FLAT : House.Kind.GABLE;
        } else {
            h.kind = q < 0.36 ? House.Kind.HANOK : q < 0.58 ? House.Kind.TOWN : q < 0.80 ? House.Kind.FLAT : House.Kind.GABLE;
        }
        // walls
        double w = r.d();
        if (h.kind == House.Kind.FLAT) {
            h.wall = w < 0.7 ? Mat.CEMENT : w < 0.85 ? Mat.PLASTER : Mat.CREAM;
            h.tall = r.chance(0.5);
            h.tank = true;
            h.laundry = r.chance(0.5);
        } else {
            h.wall = w < 0.62 ? Mat.PLASTER : w < 0.82 ? Mat.CREAM : Mat.CEMENT;
        }
        h.wall2 = Mat.CALCITE;
        h.timber = r.chance(0.7) ? Mat.LOG_S : Mat.SPRUCE_LOG;
        double rf = r.d();
        h.roof = rf < 0.55 ? Roofs.TILE : rf < 0.75 ? Roofs.COBDS : rf < 0.85 ? Roofs.BLACK : rf < 0.95 ? Roofs.MUD : Roofs.STONE;
        h.chimney = r.chance(0.25);
        if (h.kind == House.Kind.HANOK) {
            h.tall = r.chance(0.35);
            h.laundry = r.chance(0.2);
        }
        return h;
    }
}
