package com.squidgame.build;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A sparse, Minecraft-free virtual world: absolute block coordinates mapped to block-state
 * strings, plus markers, regions and entity specs. Arena builders write into a buffer; the
 * placer later copies it into the real world in time slices, and the preview tool renders it
 * to images, so structures can be developed and inspected without launching the game.
 *
 * <p>Storage is one {@code short[4096]} palette-index array per 16x16x16 section; palette index
 * 0 means "never touched" (the placer skips it), explicit air is stored as a real palette entry.
 */
public final class BlockBuffer {
    public static final String AIR = "minecraft:air";

    private final Long2ObjectOpenHashMap<short[]> sections = new Long2ObjectOpenHashMap<>();
    private final ArrayList<String> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<String> paletteIndex = new Object2IntOpenHashMap<>();
    private final List<EntitySpec> entities = new ArrayList<>();
    private final Map<String, List<Marker>> markers = new LinkedHashMap<>();
    private final Map<String, List<Region>> regions = new LinkedHashMap<>();
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    private long setCount;

    public BlockBuffer() {
        palette.add(null); // index 0 = untouched
        paletteIndex.defaultReturnValue(-1);
    }

    // ---------------------------------------------------------------- palette

    public int paletteId(String state) {
        int idx = paletteIndex.getInt(state);
        if (idx < 0) {
            idx = palette.size();
            if (idx > Short.MAX_VALUE) {
                throw new IllegalStateException("palette overflow");
            }
            palette.add(state);
            paletteIndex.put(state, idx);
        }
        return idx;
    }

    public String paletteState(int index) {
        return palette.get(index);
    }

    public int paletteSize() {
        return palette.size();
    }

    // ---------------------------------------------------------------- blocks

    private static long key(int sx, int sy, int sz) {
        return ((long) (sx + 0x200000) << 28) | ((long) (sy + 16) << 22) | (long) (sz + 0x200000);
    }

    private static int keySx(long k) {
        return (int) (k >> 28) - 0x200000;
    }

    private static int keySy(long k) {
        return (int) ((k >> 22) & 0x3F) - 16;
    }

    private static int keySz(long k) {
        return (int) (k & 0x3FFFFF) - 0x200000;
    }

    private static int index(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    public void setRaw(int x, int y, int z, int paletteId) {
        long k = key(x >> 4, y >> 4, z >> 4);
        short[] sec = sections.get(k);
        if (sec == null) {
            sec = new short[4096];
            sections.put(k, sec);
        }
        int i = index(x, y, z);
        if (sec[i] == 0) {
            setCount++;
        }
        sec[i] = (short) paletteId;
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    public void set(int x, int y, int z, String state) {
        setRaw(x, y, z, paletteId(state));
    }

    /** The state at a position, or null if never written. */
    public String get(int x, int y, int z) {
        short[] sec = sections.get(key(x >> 4, y >> 4, z >> 4));
        if (sec == null) {
            return null;
        }
        int id = sec[index(x, y, z)];
        return id == 0 ? null : palette.get(id);
    }

    public int getRaw(int x, int y, int z) {
        short[] sec = sections.get(key(x >> 4, y >> 4, z >> 4));
        return sec == null ? 0 : sec[index(x, y, z)];
    }

    /** True if something other than air has been written at the position. */
    public boolean isSolidSet(int x, int y, int z) {
        String s = get(x, y, z);
        return s != null && !s.equals(AIR);
    }

    /** Number of positions written (including explicit air). */
    public long writtenCount() {
        return setCount;
    }

    /** Number of non-air blocks. */
    public long solidCount() {
        long n = 0;
        int air = paletteIndex.getInt(AIR);
        for (short[] sec : sections.values()) {
            for (short v : sec) {
                if (v != 0 && v != air) {
                    n++;
                }
            }
        }
        return n;
    }

    public boolean isEmpty() {
        return setCount == 0;
    }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    /** Visits every written position (x, y, z, state string). Order is by section, not sorted. */
    public void forEach(BlockConsumer consumer) {
        for (Long2ObjectOpenHashMap.Entry<short[]> e : sections.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            int bx = keySx(k) << 4, by = keySy(k) << 4, bz = keySz(k) << 4;
            short[] sec = e.getValue();
            for (int i = 0; i < 4096; i++) {
                int v = sec[i];
                if (v != 0) {
                    consumer.accept(bx + (i & 15), by + (i >> 8), bz + ((i >> 4) & 15), palette.get(v));
                }
            }
        }
    }

    @FunctionalInterface
    public interface BlockConsumer {
        void accept(int x, int y, int z, String state);
    }

    /** Section keys sorted by (x, z, y) so the placer finishes whole chunks column by column. */
    public long[] sortedSectionKeys() {
        long[] keys = sections.keySet().toLongArray();
        List<Long> list = new ArrayList<>(keys.length);
        for (long k : keys) {
            list.add(k);
        }
        list.sort((a, b) -> {
            int c = Integer.compare(keySx(a), keySx(b));
            if (c != 0) return c;
            c = Integer.compare(keySz(a), keySz(b));
            if (c != 0) return c;
            return Integer.compare(keySy(a), keySy(b));
        });
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    public int sectionX(long key) { return keySx(key); }
    public int sectionY(long key) { return keySy(key); }
    public int sectionZ(long key) { return keySz(key); }
    public short[] section(long key) { return sections.get(key); }

    // ---------------------------------------------------------------- entities / markers / regions

    public void addEntity(EntitySpec spec) {
        entities.add(spec);
    }

    public List<EntitySpec> entities() {
        return Collections.unmodifiableList(entities);
    }

    public void addMarker(Marker m) {
        markers.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(m);
    }

    public void addRegion(Region r) {
        regions.computeIfAbsent(r.name(), k -> new ArrayList<>()).add(r);
    }

    public Map<String, List<Marker>> markers() {
        return Collections.unmodifiableMap(markers);
    }

    public Map<String, List<Region>> regions() {
        return Collections.unmodifiableMap(regions);
    }

    public List<Marker> markers(String name) {
        return markers.getOrDefault(name, List.of());
    }

    public Marker marker(String name) {
        List<Marker> l = markers.get(name);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    public List<Region> regions(String name) {
        return regions.getOrDefault(name, List.of());
    }

    public Region region(String name) {
        List<Region> l = regions.get(name);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    public void forEachMarker(Consumer<Marker> c) {
        markers.values().forEach(l -> l.forEach(c));
    }

    // ---------------------------------------------------------------- binary dump (for tools/preview.py)

    /**
     * Writes a compact binary dump: magic, palette, sections (sx, sy, sz, 4096 shorts), markers and
     * regions as UTF text lines. Read by {@code tools/preview.py}.
     */
    public void writeBinary(OutputStream out) throws IOException {
        DataOutputStream d = new DataOutputStream(new java.io.BufferedOutputStream(out, 1 << 16));
        d.writeInt(0x53514246); // "SQBF"
        d.writeInt(1);
        d.writeInt(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            d.writeUTF(palette.get(i) == null ? "" : palette.get(i));
        }
        d.writeInt(sections.size());
        for (long k : sortedSectionKeys()) {
            d.writeInt(keySx(k));
            d.writeInt(keySy(k));
            d.writeInt(keySz(k));
            short[] sec = sections.get(k);
            for (short v : sec) {
                d.writeShort(v);
            }
        }
        List<String> lines = new ArrayList<>();
        markers.values().forEach(l -> l.forEach(m -> lines.add(String.format(java.util.Locale.ROOT,
                "M\t%s\t%.3f\t%.3f\t%.3f\t%.1f\t%s", m.name(), m.x(), m.y(), m.z(), m.yaw(), m.data()))));
        regions.values().forEach(l -> l.forEach(r -> lines.add(String.format(java.util.Locale.ROOT,
                "R\t%s\t%d\t%d\t%d\t%d\t%d\t%d", r.name(), r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()))));
        entities.forEach(e -> lines.add(String.format(java.util.Locale.ROOT,
                "E\t%s\t%.3f\t%.3f\t%.3f", e.type(), e.x(), e.y(), e.z())));
        d.writeInt(lines.size());
        for (String s : lines) {
            d.writeUTF(s);
        }
        d.flush();
    }

    /** Reads back a dump written by {@link #writeBinary} (blocks only; used by tests). */
    public static BlockBuffer readBinary(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 1 << 16));
        if (d.readInt() != 0x53514246) {
            throw new IOException("bad magic");
        }
        d.readInt();
        BlockBuffer b = new BlockBuffer();
        int pn = d.readInt();
        String[] pal = new String[pn];
        for (int i = 0; i < pn; i++) {
            pal[i] = d.readUTF();
        }
        int sn = d.readInt();
        for (int s = 0; s < sn; s++) {
            int sx = d.readInt(), sy = d.readInt(), sz = d.readInt();
            for (int i = 0; i < 4096; i++) {
                short v = d.readShort();
                if (v != 0) {
                    b.set((sx << 4) + (i & 15), (sy << 4) + (i >> 8), (sz << 4) + ((i >> 4) & 15), pal[v]);
                }
            }
        }
        return b;
    }
}
