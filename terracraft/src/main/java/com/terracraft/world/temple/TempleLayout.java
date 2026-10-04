package com.terracraft.world.temple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Lihzahrd Temple, as a pure function of its position and the seed so every chunk can build its own part.
 * A solid block of Lihzahrd Brick {@value #SIZE} blocks square deep under the jungle, cut into a 4 x 4 grid of
 * cells: the middle four cells form the altar chamber (where Golem is fought), the twelve around it are rooms joined
 * by narrow corridors in a maze. One edge room holds the locked Lihzahrd Door; a tunnel and a ladder shaft lead from
 * it up to the jungle above.
 * <p>
 * Boxes are air volumes; everything else inside {@link #bounds()} is brick.
 */
public final class TempleLayout {
    static final int CELL = 16;
    static final int GRID = 4;
    public static final int SIZE = CELL * GRID;
    static final int ROOM_HEIGHT = 6;
    static final int CORRIDOR_HEIGHT = 3;
    static final int CHAMBER_HEIGHT = 11;
    /** Brick below the floor and above the tallest ceiling. */
    static final int SHELL = 3;
    static final int TUNNEL_LENGTH = 7;

    public enum Kind { ROOM, CHAMBER, CORRIDOR, TUNNEL, SHAFT }

    public enum Decor { CHEST, SPIKES, DART_TRAP, LAMP, ALTAR, DOOR, LADDER }

    public record Box(Kind kind, int x0, int y0, int z0, int x1, int y1, int z1) {
        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    /** A decoration; {@code facing} is where a chest, ladder or dart trap faces. */
    public record Placement(BlockPos pos, Decor decor, Direction facing) {}

    private final int x0;
    private final int z0;
    private final int floorY;
    private final List<Box> boxes = new ArrayList<>();
    private final List<Placement> placements = new ArrayList<>();
    private BlockPos door;
    private Direction doorFacing;
    private BlockPos altar;
    private BlockPos shaftTop;

    private TempleLayout(int centerX, int centerZ, int floorY) {
        this.x0 = centerX - SIZE / 2;
        this.z0 = centerZ - SIZE / 2;
        this.floorY = floorY;
    }

    public static TempleLayout build(long seed, int centerX, int centerZ, int floorY) {
        TempleLayout layout = new TempleLayout(centerX, centerZ, floorY);
        layout.generate(RandomSource.create(seed ^ 0x7E3B1E_11L));
        return layout;
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of the brick mass (inclusive). */
    public int[] bounds() {
        return new int[]{x0, floorY - SHELL, z0, x0 + SIZE - 1, floorY + CHAMBER_HEIGHT + SHELL - 1, z0 + SIZE - 1};
    }

    /** Columns touched by anything (brick mass, tunnel, shaft): {minX, minZ, maxX, maxZ}. */
    public int[] footprint() {
        int[] b = bounds();
        int minX = b[0];
        int minZ = b[2];
        int maxX = b[3];
        int maxZ = b[5];
        for (Box box : boxes) {
            minX = Math.min(minX, box.x0 - 1);
            minZ = Math.min(minZ, box.z0 - 1);
            maxX = Math.max(maxX, box.x1 + 1);
            maxZ = Math.max(maxZ, box.z1 + 1);
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    public boolean insideBricks(int x, int y, int z) {
        int[] b = bounds();
        return x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
    }

    public List<Box> boxes() {
        return boxes;
    }

    public List<Placement> placements() {
        return placements;
    }

    public BlockPos door() {
        return door;
    }

    public Direction doorFacing() {
        return doorFacing;
    }

    public BlockPos altar() {
        return altar;
    }

    /** Where the ladder shaft starts (its top is the surface, found when the chunk is built). */
    public BlockPos shaftBottom() {
        return shaftTop;
    }

    public int floorY() {
        return floorY;
    }

    private static boolean isChamber(int cx, int cz) {
        return (cx == 1 || cx == 2) && (cz == 1 || cz == 2);
    }

    private int cellX(int cx) {
        return x0 + cx * CELL;
    }

    private int cellZ(int cz) {
        return z0 + cz * CELL;
    }

    private void generate(RandomSource random) {
        // the altar chamber
        int c0x = cellX(1) + 2;
        int c0z = cellZ(1) + 2;
        int c1x = cellX(3) - 3;
        int c1z = cellZ(3) - 3;
        boxes.add(new Box(Kind.CHAMBER, c0x, floorY, c0z, c1x, floorY + CHAMBER_HEIGHT - 1, c1z));
        altar = new BlockPos((c0x + c1x) / 2, floorY, (c0z + c1z) / 2);
        placements.add(new Placement(altar, Decor.ALTAR, Direction.NORTH));
        for (int[] corner : new int[][]{{c0x + 1, c0z + 1}, {c1x - 1, c0z + 1}, {c0x + 1, c1z - 1}, {c1x - 1, c1z - 1}}) {
            placements.add(new Placement(new BlockPos(corner[0], floorY + 7, corner[1]), Decor.LAMP, Direction.UP));
        }
        // ring rooms
        List<int[]> ring = new ArrayList<>();
        for (int cx = 0; cx < GRID; cx++) {
            for (int cz = 0; cz < GRID; cz++) {
                if (!isChamber(cx, cz)) {
                    ring.add(new int[]{cx, cz});
                    int rx = cellX(cx) + 2;
                    int rz = cellZ(cz) + 2;
                    boxes.add(new Box(Kind.ROOM, rx, floorY, rz, rx + CELL - 5, floorY + ROOM_HEIGHT - 1, rz + CELL - 5));
                }
            }
        }
        // maze over the ring (random depth-first walk), plus one extra loop
        Set<Long> connected = new HashSet<>();
        List<int[]> stack = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        int[] start = ring.get(random.nextInt(ring.size()));
        stack.add(start);
        visited.add(start[0] * GRID + start[1]);
        while (!stack.isEmpty()) {
            int[] cell = stack.get(stack.size() - 1);
            List<int[]> options = new ArrayList<>();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = cell[0] + d.getStepX();
                int nz = cell[1] + d.getStepZ();
                if (nx >= 0 && nz >= 0 && nx < GRID && nz < GRID && !isChamber(nx, nz) && !visited.contains(nx * GRID + nz)) {
                    options.add(new int[]{nx, nz});
                }
            }
            if (options.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }
            int[] next = options.get(random.nextInt(options.size()));
            corridor(cell, next);
            visited.add(next[0] * GRID + next[1]);
            stack.add(next);
        }
        int[] a = ring.get(random.nextInt(ring.size()));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int[] b = {a[0] + d.getStepX(), a[1] + d.getStepZ()};
            if (b[0] >= 0 && b[1] >= 0 && b[0] < GRID && b[1] < GRID && !isChamber(b[0], b[1])) {
                corridor(a, b);
                break;
            }
        }
        // the entrance: a room on the edge of the temple
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int along = 1 + random.nextInt(2);
        int[] entryCell = switch (side) {
            case NORTH -> new int[]{along, 0};
            case SOUTH -> new int[]{along, GRID - 1};
            case WEST -> new int[]{0, along};
            default -> new int[]{GRID - 1, along};
        };
        entrance(entryCell, side);
        // the chamber opens into the ring room on the far side from the entrance
        int[] chamberDoor = switch (side.getOpposite()) {
            case NORTH -> new int[]{1 + random.nextInt(2), 0};
            case SOUTH -> new int[]{1 + random.nextInt(2), GRID - 1};
            case WEST -> new int[]{0, 1 + random.nextInt(2)};
            default -> new int[]{GRID - 1, 1 + random.nextInt(2)};
        };
        int[] inner = {chamberDoor[0] - side.getOpposite().getStepX(), chamberDoor[1] - side.getOpposite().getStepZ()};
        corridor(chamberDoor, inner);
        decorate(random, ring);
    }

    /** A corridor between two neighbouring cells, centred on the shared edge (3 wide, 3 high). */
    private void corridor(int[] a, int[] b) {
        int ax = cellX(a[0]) + CELL / 2;
        int az = cellZ(a[1]) + CELL / 2;
        int bx = cellX(b[0]) + CELL / 2;
        int bz = cellZ(b[1]) + CELL / 2;
        int y1 = floorY + CORRIDOR_HEIGHT - 1;
        if (a[0] != b[0]) {
            boxes.add(new Box(Kind.CORRIDOR, Math.min(ax, bx), floorY, az - 1, Math.max(ax, bx), y1, az + 1));
        } else {
            boxes.add(new Box(Kind.CORRIDOR, ax - 1, floorY, Math.min(az, bz), ax + 1, y1, Math.max(az, bz)));
        }
    }

    /** Corridor from the edge room out through the wall, the door in the wall, then a tunnel and a ladder shaft. */
    private void entrance(int[] cell, Direction side) {
        int mx = cellX(cell[0]) + CELL / 2;
        int mz = cellZ(cell[1]) + CELL / 2;
        int[] b = bounds();
        int wall = switch (side) {
            case NORTH -> b[2];
            case SOUTH -> b[5];
            case WEST -> b[0];
            default -> b[3];
        };
        int y1 = floorY + CORRIDOR_HEIGHT - 1;
        // from the room to just inside the outer wall
        if (side.getAxis() == Direction.Axis.Z) {
            int from = side == Direction.NORTH ? wall + 1 : mz;
            int to = side == Direction.NORTH ? mz : wall - 1;
            boxes.add(new Box(Kind.CORRIDOR, mx - 1, floorY, from, mx + 1, y1, to));
            door = new BlockPos(mx, floorY, wall);
        } else {
            int from = side == Direction.WEST ? wall + 1 : mx;
            int to = side == Direction.WEST ? mx : wall - 1;
            boxes.add(new Box(Kind.CORRIDOR, from, floorY, mz - 1, to, y1, mz + 1));
            door = new BlockPos(wall, floorY, mz);
        }
        doorFacing = side;
        placements.add(new Placement(door, Decor.DOOR, side));
        // tunnel out through the rock, then a shaft up
        BlockPos outside = door.relative(side);
        BlockPos end = door.relative(side, TUNNEL_LENGTH);
        int tx0 = Math.min(outside.getX(), end.getX()) - (side.getAxis() == Direction.Axis.Z ? 1 : 0);
        int tx1 = Math.max(outside.getX(), end.getX()) + (side.getAxis() == Direction.Axis.Z ? 1 : 0);
        int tz0 = Math.min(outside.getZ(), end.getZ()) - (side.getAxis() == Direction.Axis.X ? 1 : 0);
        int tz1 = Math.max(outside.getZ(), end.getZ()) + (side.getAxis() == Direction.Axis.X ? 1 : 0);
        boxes.add(new Box(Kind.TUNNEL, tx0, floorY, tz0, tx1, y1, tz1));
        BlockPos shaft = door.relative(side, TUNNEL_LENGTH + 2);
        shaftTop = shaft;
        boxes.add(new Box(Kind.SHAFT, shaft.getX() - 1, floorY, shaft.getZ() - 1, shaft.getX() + 1, Integer.MAX_VALUE, shaft.getZ() + 1));
        // the shaft joins the end of the tunnel
        boxes.add(new Box(Kind.TUNNEL, Math.min(end.getX(), shaft.getX()) - 1, floorY, Math.min(end.getZ(), shaft.getZ()) - 1,
            Math.max(end.getX(), shaft.getX()) + 1, y1, Math.max(end.getZ(), shaft.getZ()) + 1));
    }

    private void decorate(RandomSource random, List<int[]> ring) {
        int chests = 0;
        for (int[] cell : ring) {
            int rx0 = cellX(cell[0]) + 2;
            int rz0 = cellZ(cell[1]) + 2;
            int rx1 = rx0 + CELL - 5;
            int rz1 = rz0 + CELL - 5;
            // lamps set into the walls
            placements.add(new Placement(new BlockPos(rx0 - 1, floorY + 4, (rz0 + rz1) / 2), Decor.LAMP, Direction.EAST));
            placements.add(new Placement(new BlockPos(rx1 + 1, floorY + 4, (rz0 + rz1) / 2), Decor.LAMP, Direction.WEST));
            // spikes along the floor of some rooms
            if (random.nextInt(3) == 0) {
                int sz = rz0 + 2 + random.nextInt(rz1 - rz0 - 4);
                for (int x = rx0 + 2; x <= rx1 - 2; x++) {
                    if (random.nextInt(4) != 0) {
                        placements.add(new Placement(new BlockPos(x, floorY, sz), Decor.SPIKES, Direction.UP));
                    }
                }
            }
            // dart traps in the walls, aimed across the room
            if (random.nextInt(2) == 0) {
                int z = rz0 + 1 + random.nextInt(rz1 - rz0 - 1);
                boolean west = random.nextBoolean();
                placements.add(new Placement(new BlockPos(west ? rx0 - 1 : rx1 + 1, floorY + 1, z), Decor.DART_TRAP, west ? Direction.EAST : Direction.WEST));
            }
            if (random.nextInt(2) == 0) {
                int x = rx0 + 1 + random.nextInt(rx1 - rx0 - 1);
                boolean north = random.nextBoolean();
                placements.add(new Placement(new BlockPos(x, floorY + 1, north ? rz0 - 1 : rz1 + 1), Decor.DART_TRAP, north ? Direction.SOUTH : Direction.NORTH));
            }
            // a chest in about half of the rooms
            if (chests < 7 && random.nextInt(2) == 0) {
                boolean corner = random.nextBoolean();
                placements.add(new Placement(new BlockPos(corner ? rx0 : rx1, floorY, corner ? rz0 : rz1), Decor.CHEST,
                    corner ? Direction.SOUTH : Direction.NORTH));
                chests++;
            }
        }
        // corridors: a trap at the middle of each long corridor
        for (Box box : List.copyOf(boxes)) {
            if (box.kind() != Kind.CORRIDOR || random.nextInt(3) == 0) {
                continue;
            }
            boolean alongX = box.x1() - box.x0() > box.z1() - box.z0();
            int mx = (box.x0() + box.x1()) / 2;
            int mz = (box.z0() + box.z1()) / 2;
            if (alongX) {
                placements.add(new Placement(new BlockPos(mx, floorY + 1, box.z0() - 1), Decor.DART_TRAP, Direction.SOUTH));
            } else {
                placements.add(new Placement(new BlockPos(box.x0() - 1, floorY + 1, mz), Decor.DART_TRAP, Direction.EAST));
            }
        }
    }
}
