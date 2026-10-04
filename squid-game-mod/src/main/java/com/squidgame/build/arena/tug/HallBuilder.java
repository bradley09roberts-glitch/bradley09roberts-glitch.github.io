package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

import java.util.List;

/** Orchestrates the build passes of the Tug of War hall (shell, pit, walls, decks, galleries, roof, rooms, markers). */
public final class HallBuilder {
    private HallBuilder() {
    }

    public static final List<String> REQUIRED_MARKERS = List.of(
            "waiting.spawn", "waiting.player_entry", "gate.door", "arena.spectator", "arena.exit", "guard.post",
            "tug.slot_a", "tug.slot_b", "tug.rope_a", "tug.rope_b", "tug.rope_center", "tug.pit_floor",
            "tug.waiting_a", "tug.waiting_b", "tug.spare");

    public static final List<String> REQUIRED_REGIONS = List.of(
            "arena.bounds", "waiting.bounds", "tug.edge_a", "tug.edge_b", "tug.pit");

    public static void build(BuildContext c) {
        HallShell.build(c);
        PitBuilder.build(c);
        WallDetail.build(c);
        DeckBuilder.build(c);
        PlateauBuilder.build(c);
        GalleryBuilder.build(c);
        RingProps.build(c);
        RoofBuilder.build(c);
        RoomBuilder.build(c);
        Markers.build(c);
    }
}
