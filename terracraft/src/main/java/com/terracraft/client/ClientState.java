package com.terracraft.client;

import com.terracraft.network.packet.SyncPlayerStatsPacket;
import com.terracraft.network.packet.SyncProgressionPacket;
import com.terracraft.player.stats.Ability;
import com.terracraft.progression.ProgressionView;
import com.terracraft.progression.WorldVariants;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Client-side mirror of server-authoritative TerraCraft state. Written only by packet handlers. */
public final class ClientState {
    private static final ClientProgression PROGRESSION = new ClientProgression();
    private static SyncPlayerStatsPacket stats = new SyncPlayerStatsPacket(0, 0, 0, 100, 20, 20, 0, 0, 5, 0, "");

    private ClientState() {}

    public static ProgressionView progression() {
        return PROGRESSION;
    }

    public static SyncPlayerStatsPacket stats() {
        return stats;
    }

    /** Wings worn by players (entity id -> flight), and their current animation (see {@link WingAnim}). */
    private static final Map<Integer, com.terracraft.item.accessory.WingsItem.Flight> WINGS = new HashMap<>();
    private static final Map<Integer, WingAnim> WING_ANIM = new HashMap<>();

    public enum WingAnim { FOLDED, FLAP, GLIDE }

    public static com.terracraft.item.accessory.WingsItem.Flight wings(int entityId) {
        return WINGS.get(entityId);
    }

    public static WingAnim wingAnim(int entityId) {
        return WING_ANIM.getOrDefault(entityId, WingAnim.FOLDED);
    }

    public static void setWingAnim(int entityId, WingAnim anim) {
        WING_ANIM.put(entityId, anim);
    }

    static void setWings(com.terracraft.network.packet.PlayerWingsPacket packet) {
        if (packet.style().isEmpty()) {
            WINGS.remove(packet.entityId());
        } else {
            WINGS.put(packet.entityId(), new com.terracraft.item.accessory.WingsItem.Flight(packet.style(), packet.flightTicks(), packet.ascent()));
        }
    }

    public static boolean hasAbility(Ability ability) {
        return (stats.abilityBits() & (1 << ability.ordinal())) != 0;
    }

    static void update(SyncProgressionPacket packet) {
        PROGRESSION.flags.clear();
        PROGRESSION.flags.addAll(packet.flags());
        PROGRESSION.counters.clear();
        PROGRESSION.counters.putAll(packet.counters());
        PROGRESSION.variants = packet.variants();
    }

    static void update(SyncPlayerStatsPacket packet) {
        stats = packet;
    }

    /** Reset when leaving a world so stale data never leaks into the next one. */
    public static void clear() {
        activeEvent = "";
        WINGS.clear();
        WING_ANIM.clear();
        PROGRESSION.flags.clear();
        PROGRESSION.counters.clear();
        PROGRESSION.variants = WorldVariants.DEFAULT;
        stats = new SyncPlayerStatsPacket(0, 0, 0, 100, 20, 20, 0, 0, 5, 0, "");
    }

    private static final class ClientProgression implements ProgressionView {
        private final Set<Identifier> flags = new LinkedHashSet<>();
        private final Map<Identifier, Integer> counters = new HashMap<>();
        private WorldVariants variants = WorldVariants.DEFAULT;

        @Override
        public Set<Identifier> storedFlags() {
            return Collections.unmodifiableSet(flags);
        }

        @Override
        public int counter(Identifier id) {
            return counters.getOrDefault(id, 0);
        }

        @Override
        public WorldVariants variants() {
            return variants;
        }
    }

    private static String activeEvent = "";

    public static String activeEvent() {
        return activeEvent;
    }

    public static void setActiveEvent(String event) {
        activeEvent = event == null ? "" : event;
    }
}
