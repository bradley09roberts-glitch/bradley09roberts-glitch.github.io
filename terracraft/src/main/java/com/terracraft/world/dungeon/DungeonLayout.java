package com.terracraft.world.dungeon;

import com.terracraft.world.evil.EvilZones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The world's one Dungeon, as a pure function of the seed (and the terrain height at its entrance), so every
 * chunk can build its part on its own. Like Terraria, it lies far from spawn on one side of the world:
 * a brick entrance tower on the surface (where the Old Man waits), a ladder shaft down, then several
 * levels of rooms joined by corridors and ladder shafts, holding spikes, chests and locked gold chests.
 * <p>
 * Boxes are interior (air) volumes; {@link DungeonFeature} wraps each in a brick shell.
 */
public final class DungeonLayout {
    /** Rooms sit on a grid of this many blocks. */
    static final int CELL = 15;
    static final int ROOM_HALF = 4;
    static final int ROOM_HEIGHT = 6;
    static final int LEVEL_SPACING = 13;
    static final int LEVELS = 4;
    static final int SHELL = 2;

    public enum Kind { TOWER, ENTRY_SHAFT, ROOM, CORRIDOR, SHAFT }

    public enum Brick { BLUE, GREEN, PINK }

    public enum Decor { CHEST, LOCKED_CHEST, SPIKES, BOOKSHELF, LANTERN, COBWEB, LADDER }

    /** Inclusive interior bounds. */
    public record Box(Kind kind, int x0, int y0, int z0, int x1, int y1, int z1) {
        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }

        public boolean containsShell(int x, int y, int z, int shell) {
            return x >= x0 - shell && x <= x1 + shell && y >= y0 - shell && y <= y1 + shell && z >= z0 - shell && z <= z1 + shell;
        }

        boolean touchesColumn(int minX, int minZ, int maxX, int maxZ, int margin) {
            return x1 + margin >= minX && x0 - margin <= maxX && z1 + margin >= minZ && z0 - margin <= maxZ;
        }
    }

    /** A placed decoration; {@code facing} is used by chests and ladders. */
    public record Placement(BlockPos pos, Decor decor, Direction facing) {}

    private final int centerX;
    private final int centerZ;
    private final int surfaceY;
    private final Direction doorFacing;
    private final Brick brick;
    private final List<Box> boxes = new ArrayList<>();
    private final List<Placement> placements = new ArrayList<>();

    private DungeonLayout(int centerX, int centerZ, int surfaceY, Direction doorFacing, Brick brick) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.surfaceY = surfaceY;
        this.doorFacing = doorFacing;
        this.brick = brick;
    }

    // ------------------------------------------------------------------ location (seed only)

    /** Where the entrance is (x, z), decided from the seed: 650-950 blocks from spawn, on dry land, away from evil zones. */
    public static int[] location(long seed, EvilZones.LandTest land) {
        RandomSource random = RandomSource.create(seed ^ 0xD0_6E0_17L);
        double baseAngle = random.nextDouble() * Math.PI * 2;
        int[] fallback = null;
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = baseAngle + (attempt == 0 ? 0 : (random.nextDouble() - 0.5) * Math.min(Math.PI * 2, 0.4 + attempt * 0.2));
            double distance = 650 + random.nextInt(300) + attempt * 10;
            int x = (int) (Math.cos(angle) * distance);
            int z = (int) (Math.sin(angle) * distance);
            if (fallback == null) {
                fallback = new int[]{x, z};
            }
            boolean dry = land.isLand(x, z);
            for (int i = 0; i < 8 && dry; i++) {
                double a = i * Math.PI / 4;
                dry = land.isLand(x + (int) (Math.cos(a) * 24), z + (int) (Math.sin(a) * 24));
            }
            if (!dry) {
                continue;
            }
            EvilZones.Zone evil = EvilZones.nearest(seed, x, z);
            if (evil != null && Mth.square(evil.x() - x) + Mth.square(evil.z() - z) < Mth.square(evil.radius() * 1.4 + 90)) {
                continue;
            }
            return new int[]{x, z};
        }
        return fallback;
    }

    // ------------------------------------------------------------------ full layout

    /** Builds the dungeon around an entrance at (x, surfaceY, z). */
    public static DungeonLayout build(long seed, int x, int z, int surfaceY, int minY) {
        RandomSource random = RandomSource.create(seed ^ 0xB0_17E5L);
        Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        // face the door roughly back toward spawn
        if (Math.abs(x) > Math.abs(z)) {
            door = x > 0 ? Direction.WEST : Direction.EAST;
        } else {
            door = z > 0 ? Direction.NORTH : Direction.SOUTH;
        }
        Brick brick = Brick.values()[random.nextInt(Brick.values().length)];
        DungeonLayout layout = new DungeonLayout(x, z, surfaceY, door, brick);
        layout.generate(random, minY);
        return layout;
    }

    private void generate(RandomSource random, int minY) {
        // entrance tower: 9x9 hall from the surface up, with a doorway toward spawn
        boxes.add(new Box(Kind.TOWER, centerX - 4, surfaceY, centerZ - 4, centerX + 4, surfaceY + 7, centerZ + 4));
        int topFloor = Math.max(minY + 8 + (LEVELS - 1) * LEVEL_SPACING, surfaceY - 24);
        // ladder shaft from the tower floor down into the first room
        // (against the south wall of the tower and of the first room, so the ladder always has a wall behind it)
        boxes.add(new Box(Kind.ENTRY_SHAFT, centerX - 1, topFloor + 1, centerZ + 2, centerX + 1, surfaceY - 1, centerZ + ROOM_HALF));
        for (int y = topFloor; y < surfaceY + 3; y++) {
            placements.add(new Placement(new BlockPos(centerX, y, centerZ + ROOM_HALF), Decor.LADDER, Direction.NORTH));
        }

        // rooms: a random walk on the grid per level; each level starts below the previous level's last room
        Map<Long, int[]> rooms = new HashMap<>();
        Set<Long> corridorDone = new HashSet<>();
        int gx = 0;
        int gz = 0;
        Direction away = doorFacing.getOpposite();
        for (int level = 0; level < LEVELS; level++) {
            int floor = topFloor - level * LEVEL_SPACING;
            int count = 6 + random.nextInt(3);
            int px = gx;
            int pz = gz;
            addRoom(rooms, level, gx, gz, floor);
            if (level > 0) {
                addShaft(gx, gz, floor, floor + LEVEL_SPACING);
            }
            for (int i = 1; i < count; i++) {
                Direction step;
                // walk mostly away from the entrance, with turns
                int roll = random.nextInt(10);
                if (roll < 4) {
                    step = away;
                } else if (roll < 7) {
                    step = away.getClockWise();
                } else if (roll < 9) {
                    step = away.getCounterClockWise();
                } else {
                    step = away.getOpposite();
                }
                int nx = px + step.getStepX();
                int nz = pz + step.getStepZ();
                addRoom(rooms, level, nx, nz, floor);
                long key = corridorKey(level, px, pz, nx, nz);
                if (corridorDone.add(key)) {
                    addCorridor(px, pz, nx, nz, floor);
                }
                px = nx;
                pz = nz;
            }
            gx = px;
            gz = pz;
        }
        decorate(random, rooms);
    }

    private static long corridorKey(int level, int ax, int az, int bx, int bz) {
        int lx = Math.min(ax, bx);
        int lz = Math.min(az, bz);
        boolean xAxis = ax != bx;
        return ((long) level << 48) ^ ((long) (lx & 0xFFFF) << 32) ^ ((long) (lz & 0xFFFF) << 16) ^ (xAxis ? 1 : 2);
    }

    private int roomX(int gx) {
        return centerX + gx * CELL;
    }

    private int roomZ(int gz) {
        return centerZ + gz * CELL;
    }

    private void addRoom(Map<Long, int[]> rooms, int level, int gx, int gz, int floor) {
        long key = ((long) level << 40) ^ ((long) (gx & 0xFFFFF) << 20) ^ (gz & 0xFFFFF);
        if (rooms.containsKey(key)) {
            return;
        }
        rooms.put(key, new int[]{gx, gz, floor, level});
        int x = roomX(gx);
        int z = roomZ(gz);
        boxes.add(new Box(Kind.ROOM, x - ROOM_HALF, floor, z - ROOM_HALF, x + ROOM_HALF, floor + ROOM_HEIGHT - 1, z + ROOM_HALF));
    }

    private void addCorridor(int ax, int az, int bx, int bz, int floor) {
        int x0 = roomX(Math.min(ax, bx));
        int x1 = roomX(Math.max(ax, bx));
        int z0 = roomZ(Math.min(az, bz));
        int z1 = roomZ(Math.max(az, bz));
        if (ax != bx) {
            boxes.add(new Box(Kind.CORRIDOR, x0 + ROOM_HALF, floor, z0 - 1, x1 - ROOM_HALF, floor + 3, z0 + 1));
        } else {
            boxes.add(new Box(Kind.CORRIDOR, x0 - 1, floor, z0 + ROOM_HALF, x0 + 1, floor + 3, z1 - ROOM_HALF));
        }
    }

    private void addShaft(int gx, int gz, int lowerFloor, int upperFloor) {
        int x = roomX(gx) + ROOM_HALF - 1;
        int z = roomZ(gz) + ROOM_HALF - 1;
        boxes.add(new Box(Kind.SHAFT, x - 1, lowerFloor + 1, z - 1, x + 1, upperFloor - 1, z + 1));
        for (int y = lowerFloor; y < upperFloor + 2; y++) {
            placements.add(new Placement(new BlockPos(x, y, z + 1), Decor.LADDER, Direction.NORTH));
        }
    }

    private void decorate(RandomSource random, Map<Long, int[]> rooms) {
        int index = 0;
        List<int[]> ordered = new ArrayList<>(rooms.values());
        ordered.sort((a, b) -> a[3] != b[3] ? Integer.compare(a[3], b[3]) : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        boolean keyChest = false;
        for (int[] room : ordered) {
            index++;
            int x = roomX(room[0]);
            int z = roomZ(room[1]);
            int floor = room[2];
            boolean first = room[3] == 0 && room[0] == 0 && room[1] == 0;
            if (first) {
                continue;
            }
            // Corridors meet rooms in the middle of each wall and shafts use the (+x, +z) corner, so furniture
            // goes into the other corners.
            int roll = random.nextInt(100);
            BlockPos chest = new BlockPos(x - ROOM_HALF + 1, floor, z - ROOM_HALF + 1);
            if (roll < 30) {
                placements.add(new Placement(chest, Decor.LOCKED_CHEST, Direction.SOUTH));
            } else if (roll < 55 || !keyChest && index > 3) {
                placements.add(new Placement(chest, Decor.CHEST, Direction.SOUTH));
                keyChest = true;
            }
            if (random.nextInt(3) == 0) {
                for (int dz = -ROOM_HALF; dz <= -2; dz++) {
                    for (int dy = 0; dy < 3; dy++) {
                        placements.add(new Placement(new BlockPos(x + ROOM_HALF, floor + dy, z + dz), Decor.BOOKSHELF, Direction.WEST));
                    }
                }
            }
            if (random.nextInt(2) == 0) {
                // spike strip across the room floor, with a gap in the middle
                int sx = x - 1 + random.nextInt(3);
                for (int dz = -ROOM_HALF + 1; dz <= ROOM_HALF - 1; dz++) {
                    if (dz != 0) {
                        placements.add(new Placement(new BlockPos(sx, floor, z + dz), Decor.SPIKES, Direction.UP));
                    }
                }
            }
            placements.add(new Placement(new BlockPos(x, floor + ROOM_HEIGHT - 1, z), Decor.LANTERN, Direction.DOWN));
            for (int corner = 0; corner < 3; corner++) {
                if (random.nextInt(3) == 0) {
                    int cx = x + (corner % 2 == 0 ? -ROOM_HALF : ROOM_HALF);
                    int cz = z + (corner < 2 ? -ROOM_HALF : ROOM_HALF);
                    placements.add(new Placement(new BlockPos(cx, floor + ROOM_HEIGHT - 1, cz), Decor.COBWEB, Direction.UP));
                }
            }
        }
    }

    // ------------------------------------------------------------------ queries

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int surfaceY() {
        return surfaceY;
    }

    public Direction doorFacing() {
        return doorFacing;
    }

    public Brick brick() {
        return brick;
    }

    public List<Box> boxes() {
        return boxes;
    }

    public List<Placement> placements() {
        return placements;
    }

    /** Where the Old Man stands: just outside the tower door. */
    public BlockPos oldManSpot() {
        return new BlockPos(centerX + doorFacing.getStepX() * 7, surfaceY, centerZ + doorFacing.getStepZ() * 7);
    }

    public BlockPos entrance() {
        return new BlockPos(centerX, surfaceY, centerZ);
    }

    /** True inside the dungeon proper (rooms, corridors and shafts below the entrance). */
    public boolean isInside(BlockPos pos) {
        for (Box box : boxes) {
            if ((box.kind() == Kind.ROOM || box.kind() == Kind.CORRIDOR || box.kind() == Kind.SHAFT)
                && box.containsShell(pos.getX(), pos.getY(), pos.getZ(), 1)) {
                return true;
            }
        }
        return false;
    }

    /** Overall horizontal bounds (with shell), for quick chunk rejection. */
    public int[] bounds() {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Box box : boxes) {
            minX = Math.min(minX, box.x0());
            minZ = Math.min(minZ, box.z0());
            maxX = Math.max(maxX, box.x1());
            maxZ = Math.max(maxZ, box.z1());
        }
        return new int[]{minX - 8, minZ - 8, maxX + 8, maxZ + 8};
    }
}
