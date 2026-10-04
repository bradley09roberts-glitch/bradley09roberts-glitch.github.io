package com.squidgame.build;

import java.util.List;

/**
 * Minimal stand-in structure (a lit floor slab with a waiting area and the common markers) used for
 * arenas whose real builder is not present, so the pipeline, tournament flow and tests always work.
 */
public final class PlaceholderBuilder implements ArenaBuilder {
    private final ArenaId id;

    public PlaceholderBuilder(ArenaId id) {
        this.id = id;
    }

    @Override
    public ArenaId id() {
        return id;
    }

    @Override
    public List<String> requiredMarkers() {
        return id == ArenaId.RED_LIGHT ? List.of("waiting.spawn", "waiting.player_entry", "gate.door", "arena.spectator", "arena.exit", "redlight.start_spawn", "redlight.doll")
                : id == ArenaId.HUB ? List.of("dorm.player_spawn", "dorm.npc_spawn", "hub.spectator", "hub.podium") : CommonMarkers.REQUIRED;
    }

    /** Placeholders are always older than any real builder (version >= 1), so a real builder replaces them. */
    @Override
    public int version() {
        return 0;
    }

    @Override
    public List<String> requiredRegions() {
        return id == ArenaId.HUB ? List.of() : CommonMarkers.REQUIRED_REGIONS;
    }

    private void buildHub(BuildContext c) {
        c.fill(-40, -2, -40, 40, -1, 40, "minecraft:stone_bricks");
        c.fill(-40, 0, -40, 40, 0, 40, "minecraft:smooth_stone");
        c.shell(-40, 0, -40, 40, 14, 40, "minecraft:white_concrete");
        c.clear(-39, 1, -39, 39, 13, 39);
        c.checker(-39, 0, -39, 39, 39, "minecraft:white_concrete", "minecraft:light_gray_concrete");
        for (int x = -36; x <= 36; x += 12) {
            for (int z = -36; z <= 36; z += 12) {
                c.set(x, 13, z, "minecraft:sea_lantern");
            }
        }
        c.set(0, 1, -30, "squidgame:registration_terminal[facing=south]");
        c.marker("dorm.registration_terminal", 0.5, 1, -30 + 0.5, 0f);
        c.marker("dorm.player_spawn", 0.5, 1, -26.5, 0f);
        int slot = 0;
        for (int z = -20; z <= 20; z += 2) {
            for (int x = -30; x <= 30; x += 2) {
                if (slot < 160) {
                    c.marker("dorm.npc_spawn", x + 0.5, 1, z + 0.5, 0f, "slot=" + slot++);
                }
            }
        }
        c.marker("hub.spectator", 0.5, 8, -30.5, 0f);
        c.marker("hub.podium", 0.5, 1, 30.5, 180f);
        c.text(0.5, 6, -38.4, "SQUID GAME (placeholder hub)", "white", 3f, 0f, true);
    }

    private void buildRedLightField(BuildContext c) {
        // simple flat playground used until the real builder exists: field z[-8..180], x[-56..56]
        c.fill(-60, -2, -12, 60, -1, 184, "minecraft:stone_bricks");
        c.fill(-56, 0, -8, 56, 0, 180, "squidgame:playground_ground");
        c.fill(-56, 1, -8, 56, 30, 180, "minecraft:air");
        c.walls(-57, 1, -9, 57, 30, 181, "minecraft:light_blue_concrete");
        c.fill(-56, 0, 140, 56, 0, 141, "minecraft:white_concrete");
        c.fill(-56, 0, 12, 56, 0, 12, "minecraft:white_concrete");
        c.fill(-50, 0, 141, 50, 0, 175, "minecraft:smooth_sandstone");
        // waiting room (shared prefab) behind the start
        c.at(0, 0, -8, 0, () -> com.squidgame.build.arena.prefab.WaitingRoomPrefab.build(c,
                com.squidgame.build.arena.prefab.WaitingRoomPrefab.Spec.of("RED LIGHT, GREEN LIGHT")));
        int slot = 0;
        for (double z = 1.5; z < 11.5; z += 1.4) {
            for (double x = -49.5; x <= 49.5; x += 1.4) {
                if (slot < 200) {
                    c.marker("redlight.start_spawn", Math.floor(x) + 0.5, 1.0, Math.floor(z) + 0.5, 0f, "slot=" + slot++);
                }
            }
        }
        c.region("redlight.start_zone", -52, 0, 0, 52, 4, 11);
        c.region("redlight.start_line", -56, 0, 12, 56, 1, 12);
        c.region("redlight.finish_line", -56, 0, 140, 56, 3, 140);
        c.region("redlight.safe_zone", -50, 0, 141, 50, 3, 175);
        c.marker("redlight.doll", 0.5, 1.0, 147.5, 0f);
        c.marker("redlight.tree", 0.5, 1.0, 158.5, 0f);
        c.marker("arena.spectator", 0.5, 40, 100.5, 180f);
        c.marker("arena.exit", 0.5, 1.0, 160.5, 180f);
        c.region("arena.bounds", -56, 0, -6, 56, 60, 178);
        c.region("redlight.spawn_dummy", 0, 0, 0, 0, 0, 0);
        for (int x = -50; x <= 50; x += 12) {
            c.marker("guard.post", x + 0.5, 1.0, 170.5, 180f, "rank=triangle");
        }
        c.marker("guard.post", -30.5, 1.0, 100.5, 90f, "rank=triangle");
        c.marker("guard.post", 30.5, 1.0, 100.5, -90f, "rank=triangle");
        for (int z = 20; z < 140; z += 20) {
            c.set(-56, 1, z, "minecraft:sea_lantern");
            c.set(56, 1, z, "minecraft:sea_lantern");
        }
    }

    @Override
    public void build(BuildContext c) {
        if (id == ArenaId.HUB) {
            buildHub(c);
            return;
        }
        if (id == ArenaId.RED_LIGHT) {
            buildRedLightField(c);
            return;
        }
        String floor = "minecraft:smooth_stone";
        String wall = "minecraft:white_concrete";
        c.fill(-40, -2, -40, 40, -1, 40, "minecraft:stone_bricks");
        c.fill(-40, 0, -40, 40, 0, 40, floor);
        c.shell(-40, 0, -40, 40, 12, 40, wall);
        c.clear(-39, 1, -39, 39, 11, 39);
        c.checker(-39, 0, -39, 39, 39, "minecraft:white_concrete", "minecraft:light_gray_concrete");
        for (int x = -36; x <= 36; x += 12) {
            for (int z = -36; z <= 36; z += 12) {
                c.set(x, 11, z, "minecraft:sea_lantern");
            }
        }
        int slot = 0;
        for (int z = -30; z <= -10; z += 2) {
            for (int x = -30; x <= 30; x += 2) {
                c.marker("waiting.spawn", x + 0.5, 1, z + 0.5, 0f, "slot=" + slot++);
            }
        }
        c.marker("waiting.player_entry", 0.5, 1, -34.5, 0f);
        c.marker("arena.spectator", 0.5, 4, 36.5, 180f);
        c.marker("arena.exit", 0.5, 1, 30.5, 180f);
        c.marker("gate.door", 0.5, 1, -5.5, 0f, "w=5,h=4");
        c.region("arena.bounds", -39, 0, -39, 39, 20, 39);
        c.region("waiting.bounds", -39, 0, -39, 39, 11, -6);
        c.text(0.5, 6, 39.4, id.name() + " (placeholder)", "white", 3f, 180f, true);
    }
}
