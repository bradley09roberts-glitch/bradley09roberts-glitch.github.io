package com.grandline.fruit;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import com.grandline.world.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Agreed rule: fruit powers may change terrain in the wild only; changes revert over time; towns are immune.
 * Pending reverts are saved so a restart never leaves ice walls or craters behind permanently.
 */
public final class TerrainChanges extends SavedData {
    private record Change(ResourceKey<Level> dim, BlockPos pos, BlockState original, long revertAt) {}

    private final Deque<Change> pending = new ArrayDeque<>();

    public static TerrainChanges get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerrainChanges::new, TerrainChanges::load, null), GrandLine.MODID + "_terrain");
    }

    public static boolean allowed(ServerLevel level, BlockPos pos) {
        return GLConfig.FRUIT_TERRAIN_CHANGES.get() && TownProtection.canModify(level, pos)
                && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0 && level.getBlockEntity(pos) == null;
    }

    /** Temporarily set a block (only replacing air/water/plants/terrain); reverts after the configured delay. */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state) {
        if (!allowed(level, pos)) return false;
        BlockState cur = level.getBlockState(pos);
        if (cur.equals(state)) return false;
        TerrainChanges d = get(level.getServer());
        long at = level.getGameTime() + GLConfig.FRUIT_TERRAIN_REVERT_SECONDS.get() * 20L + level.random.nextInt(60);
        d.pending.add(new Change(level.dimension(), pos.immutable(), cur, at));
        d.setDirty();
        level.setBlock(pos, state, 3);
        return true;
    }

    public static void breakTemporarily(ServerLevel level, BlockPos pos) {
        place(level, pos, Blocks.AIR.defaultBlockState());
    }

    /** Small spherical crater in natural terrain. */
    public static void crater(ServerLevel level, BlockPos center, int radius, @Nullable Entity cause) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius / 2, radius))) {
            if (p.distSqr(center) > radius * radius) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.liquid()) continue;
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 4) continue;
            place(level, p.immutable(), Blocks.AIR.defaultBlockState());
        }
    }

    public static void tick(MinecraftServer server) {
        TerrainChanges d = get(server);
        if (d.pending.isEmpty()) return;
        long now = server.overworld().getGameTime();
        int budget = 400;
        while (!d.pending.isEmpty() && budget-- > 0) {
            Change c = d.pending.peekFirst();
            if (c.revertAt > now) {
                // queue is approximately time-ordered; rotate to look at a few more entries
                d.pending.addLast(d.pending.pollFirst());
                if (budget < 380) break;
                continue;
            }
            d.pending.pollFirst();
            ServerLevel level = server.getLevel(c.dim);
            if (level != null && level.isLoaded(c.pos)) {
                level.setBlock(c.pos, c.original, 3);
            } else if (level != null) {
                d.pending.addLast(new Change(c.dim, c.pos, c.original, now + 200)); // retry when loaded
            }
            d.setDirty();
        }
    }

    /** Revert everything immediately (server stop / admin command). */
    public static int revertAll(MinecraftServer server) {
        TerrainChanges d = get(server);
        int n = 0;
        while (!d.pending.isEmpty()) {
            Change c = d.pending.pollFirst();
            ServerLevel level = server.getLevel(c.dim);
            if (level != null) {
                level.setBlock(c.pos, c.original, 3);
                n++;
            }
        }
        d.setDirty();
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Change c : pending) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", c.dim.location().toString());
            t.put("pos", NbtUtils.writeBlockPos(c.pos));
            t.put("state", NbtUtils.writeBlockState(c.original));
            t.putLong("at", c.revertAt);
            l.add(t);
        }
        tag.put("pending", l);
        return tag;
    }

    public static TerrainChanges load(CompoundTag tag, HolderLookup.Provider provider) {
        TerrainChanges d = new TerrainChanges();
        ListTag l = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            var pos = NbtUtils.readBlockPos(t, "pos").orElse(null);
            if (pos == null) continue;
            BlockState st = NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK), t.getCompound("state"));
            d.pending.add(new Change(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))), pos, st, t.getLong("at")));
        }
        return d;
    }
}
