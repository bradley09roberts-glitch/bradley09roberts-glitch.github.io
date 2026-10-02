package com.terracraft.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world town NPC bookkeeping ({@code data/terracraft/town_npcs.dat}): which NPCs live (entity UUID,
 * name, house), when dead ones died, and candidate house positions collected from placed doors/lights/furniture.
 */
public final class NpcWorldData extends SavedData {
    public record Record(Optional<UUID> entity, String name, Optional<BlockPos> house, long diedAt) {
        static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Record::entity),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Record::name),
            BlockPos.CODEC.optionalFieldOf("house").forGetter(Record::house),
            Codec.LONG.optionalFieldOf("died_at", -1L).forGetter(Record::diedAt)
        ).apply(i, Record::new));

        public boolean alive() {
            return entity.isPresent();
        }
    }

    private static final Codec<NpcWorldData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING, Record.CODEC).optionalFieldOf("npcs", Map.of()).forGetter(d -> Map.copyOf(d.npcs)),
        BlockPos.CODEC.listOf().optionalFieldOf("candidates", List.of()).forGetter(d -> List.copyOf(d.candidates))
    ).apply(i, NpcWorldData::new));

    public static final SavedDataType<NpcWorldData> TYPE = new SavedDataType<>(TerraCraft.id("town_npcs"), NpcWorldData::new, CODEC, null);
    public static final int MAX_CANDIDATES = 256;

    private final Map<String, Record> npcs = new HashMap<>();
    private final Set<BlockPos> candidates = new LinkedHashSet<>();

    public NpcWorldData() {
    }

    private NpcWorldData(Map<String, Record> npcs, List<BlockPos> candidates) {
        this.npcs.putAll(npcs);
        this.candidates.addAll(candidates);
    }

    public static NpcWorldData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Record> record(String npc) {
        return Optional.ofNullable(npcs.get(npc));
    }

    public Map<String, Record> records() {
        return npcs;
    }

    public void put(String npc, Record record) {
        npcs.put(npc, record);
        setDirty();
    }

    public void remove(String npc) {
        npcs.remove(npc);
        setDirty();
    }

    public Set<BlockPos> candidates() {
        return candidates;
    }

    public void addCandidate(BlockPos pos) {
        if (candidates.add(pos.immutable())) {
            if (candidates.size() > MAX_CANDIDATES) {
                candidates.remove(candidates.iterator().next());
            }
            setDirty();
        }
    }

    public void removeCandidate(BlockPos pos) {
        if (candidates.remove(pos)) {
            setDirty();
        }
    }
}
