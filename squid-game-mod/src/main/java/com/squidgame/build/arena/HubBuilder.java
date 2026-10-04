package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.PlaceholderBuilder;
import com.squidgame.build.arena.hub.HubPart;

import java.util.ArrayList;
import java.util.List;

/**
 * The hub is one connected structure assembled from parts (see docs/ARENA_MARKERS.md for the shared coordinate
 * contract). Parts are discovered by class name; missing parts are skipped, and if none exists at all the
 * placeholder hub is used so the rest of the mod keeps working.
 */
public final class HubBuilder implements ArenaBuilder {
    /** In build order. Later parts may carve into earlier ones. */
    private static final String[] PARTS = {"DormitoryPart", "CorridorPart", "StairwayPart", "ControlRoomPart"};

    private final List<HubPart> parts = new ArrayList<>();

    public HubBuilder() {
        for (String name : PARTS) {
            try {
                parts.add((HubPart) Class.forName("com.squidgame.build.arena.hub." + name).getDeclaredConstructor().newInstance());
            } catch (ClassNotFoundException e) {
                // part not implemented yet
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot create hub part " + name, e);
            }
        }
    }

    @Override
    public ArenaId id() {
        return ArenaId.HUB;
    }

    @Override
    public void build(BuildContext c) {
        if (parts.isEmpty()) {
            new PlaceholderBuilder(ArenaId.HUB).build(c);
            return;
        }
        for (HubPart p : parts) {
            p.build(c);
        }
    }

    @Override
    public List<String> requiredMarkers() {
        if (parts.isEmpty()) {
            return List.of("dorm.player_spawn", "dorm.npc_spawn", "hub.spectator", "hub.podium");
        }
        List<String> out = new ArrayList<>(List.of("dorm.player_spawn", "dorm.npc_spawn", "dorm.exit_door", "hub.spectator",
                "hub.podium", "dorm.registration_terminal", "prize.pig", "prize.counter"));
        parts.forEach(p -> out.addAll(p.markers()));
        return out;
    }

    @Override
    public List<String> requiredRegions() {
        if (parts.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(List.of("prize.fill", "gate.red_light", "gate.dalgona", "gate.tug_of_war",
                "gate.marbles", "gate.glass_bridge", "gate.final"));
        parts.forEach(p -> out.addAll(p.regions()));
        return out;
    }

    @Override
    public int version() {
        int v = 1;
        for (HubPart p : parts) {
            v = v * 31 + p.version();
        }
        return v;
    }
}
