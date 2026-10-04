package com.terracraft.world.spawn;

import com.mojang.serialization.Codec;
import com.terracraft.TerraCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Areas (x/z boxes from a given height up) where no Terraria enemy spawns on its own - set with {@code /terraria spawns safezone}.
 * The showcase world marks its floating halls, so Wyverns and event creatures leave its visitors alone.
 */
public final class SafeZones extends SavedData {
    private static final Codec<SafeZones> CODEC = Codec.INT.listOf().xmap(SafeZones::new, z -> z.boxes)
        .fieldOf("boxes").codec();
    public static final SavedDataType<SafeZones> TYPE = new SavedDataType<>(TerraCraft.id("safe_zones"), SafeZones::new, CODEC, null);

    /** Flat list of x0, z0, x1, z1, minY per box. */
    private final List<Integer> boxes;

    public SafeZones() {
        this.boxes = new ArrayList<>();
    }

    private SafeZones(List<Integer> boxes) {
        this.boxes = new ArrayList<>(boxes);
    }

    public static SafeZones get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean isSafe(MinecraftServer server, int x, int y, int z) {
        List<Integer> b = get(server).boxes;
        for (int i = 0; i + 4 < b.size(); i += 5) {
            if (x >= b.get(i) && z >= b.get(i + 1) && x <= b.get(i + 2) && z <= b.get(i + 3) && y >= b.get(i + 4)) {
                return true;
            }
        }
        return false;
    }

    public void add(int x0, int z0, int x1, int z1, int minY) {
        boxes.addAll(List.of(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), minY));
        setDirty();
    }

    public void clear() {
        boxes.clear();
        setDirty();
    }

    public int count() {
        return boxes.size() / 5;
    }
}
