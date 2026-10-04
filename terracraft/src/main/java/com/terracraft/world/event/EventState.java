package com.terracraft.world.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The currently running world event, persisted ({@code data/terracraft/world_event.dat}). */
public final class EventState extends SavedData {
    private static final Codec<EventState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("active", "").forGetter(s -> s.active),
        Codec.INT.optionalFieldOf("kills", 0).forGetter(s -> s.kills),
        Codec.BOOL.optionalFieldOf("was_day", true).forGetter(s -> s.wasDay),
        Codec.INT.optionalFieldOf("wave", 0).forGetter(s -> s.wave)
    ).apply(i, EventState::new));

    public static final SavedDataType<EventState> TYPE = new SavedDataType<>(TerraCraft.id("world_event"), EventState::new, CODEC, null);

    String active = "";
    int kills;
    boolean wasDay = true;
    /** Current wave of a wave event (Pumpkin/Frost Moon); kills then hold its points. */
    int wave;

    public EventState() {
    }

    private EventState(String active, int kills, boolean wasDay, int wave) {
        this.active = active;
        this.kills = kills;
        this.wasDay = wasDay;
        this.wave = wave;
    }

    public int wave() {
        return wave;
    }

    public static EventState get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public String active() {
        return active;
    }

    public int kills() {
        return kills;
    }
}
