package com.squidgame.tournament;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Persisted tournament state: the running tournament (if any) and the snapshots of players inside the arena. */
public final class TournamentData extends SavedData {
    public static final String KEY = "squidgame_tournament";
    private static final Factory<TournamentData> FACTORY = new Factory<>(TournamentData::new, TournamentData::load, DataFixTypes.LEVEL);

    public CompoundTag tournament;
    public ListTag snapshots = new ListTag();

    public static TournamentData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (tournament != null) {
            tag.put("tournament", tournament);
        }
        tag.put("snapshots", snapshots);
        return tag;
    }

    private static TournamentData load(CompoundTag tag, HolderLookup.Provider provider) {
        TournamentData d = new TournamentData();
        if (tag.contains("tournament", Tag.TAG_COMPOUND)) {
            d.tournament = tag.getCompound("tournament");
        }
        d.snapshots = tag.getList("snapshots", Tag.TAG_COMPOUND);
        return d;
    }
}
