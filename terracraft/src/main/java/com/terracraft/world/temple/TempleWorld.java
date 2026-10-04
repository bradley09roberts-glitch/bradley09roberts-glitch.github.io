package com.terracraft.world.temple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import com.terracraft.registry.content.TempleContent;
import com.terracraft.world.jungle.JungleFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Places the world's Lihzahrd Temple. The site is found once per world (the nearest stretch of jungle at least
 * {@value #MIN_DISTANCE} blocks from spawn that is jungle all across) and saved in
 * {@code data/terracraft/temple.dat}; the temple is then built a chunk at a time as those chunks load, so it also
 * appears in worlds created before it existed. Built chunks are remembered so nothing is built twice.
 */
public final class TempleWorld extends SavedData {
    private static final int MIN_DISTANCE = 300;
    private static final int MAX_DISTANCE = 4000;
    private static final int FLOOR_Y = -14;
    private static final int CHUNKS_PER_TICK = 2;
    private static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, TerraCraft.id("chests/temple"));

    private static final Codec<TempleWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("located", false).forGetter(t -> t.located),
        Codec.INT.optionalFieldOf("x", 0).forGetter(t -> t.x),
        Codec.INT.optionalFieldOf("z", 0).forGetter(t -> t.z),
        Codec.LONG.listOf().optionalFieldOf("built", List.of()).forGetter(t -> List.copyOf(t.built))
    ).apply(i, TempleWorld::new));
    public static final SavedDataType<TempleWorld> TYPE = new SavedDataType<>(TerraCraft.id("temple"), TempleWorld::new, CODEC, null);

    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static TempleLayout cachedLayout;
    private static long cachedKey = Long.MIN_VALUE;

    private boolean located;
    private int x;
    private int z;
    private final Set<Long> built;

    public TempleWorld() {
        this.built = new HashSet<>();
    }

    private TempleWorld(boolean located, int x, int z, List<Long> built) {
        this.located = located;
        this.x = x;
        this.z = z;
        this.built = new HashSet<>(built);
    }

    public static TempleWorld get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TempleWorld::onChunkLoad);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TempleWorld::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            synchronized (TempleWorld.class) {
                QUEUE.clear();
                QUEUED.clear();
                cachedLayout = null;
                cachedKey = Long.MIN_VALUE;
            }
        });
        com.terracraft.command.TerrariaCommand.addExtension(root -> root.then(Commands.literal("worldgen").then(Commands.literal("temple")
            .executes(ctx -> {
                MinecraftServer server = ctx.getSource().getServer();
                TempleLayout layout = layout(server.overworld());
                BlockPos door = layout.door();
                BlockPos shaft = layout.shaftBottom();
                BlockPos altar = layout.altar();
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "Lihzahrd Temple: door at %d %d %d (facing %s), shaft at %d %d, altar at %d %d %d; %d chunks built",
                    door.getX(), door.getY(), door.getZ(), layout.doorFacing().getSerializedName(), shaft.getX(), shaft.getZ(),
                    altar.getX(), altar.getY(), altar.getZ(), get(server).built.size())).withStyle(ChatFormatting.GOLD), false);
                return 1;
            })
            .then(Commands.literal("tp").executes(ctx -> {
                // to the top of the ladder shaft that leads down to the door
                ServerLevel level = ctx.getSource().getServer().overworld();
                BlockPos shaft = layout(level).shaftBottom();
                level.getChunkAt(shaft);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shaft.getX(), shaft.getZ());
                ctx.getSource().getPlayerOrException().teleportTo(level, shaft.getX() + 0.5, y + 1, shaft.getZ() + 0.5,
                    java.util.Set.of(), layout(level).doorFacing().getOpposite().toYRot(), 30.0F, true);
                return 1;
            })))));
    }

    /** The world's temple layout (the site is searched for, and saved, the first time it is needed). */
    public static synchronized TempleLayout layout(ServerLevel level) {
        TempleWorld data = get(level.getServer());
        if (!data.located) {
            int[] site = findSite(level);
            data.x = site[0];
            data.z = site[1];
            data.located = true;
            data.setDirty();
            TerraCraft.LOGGER.info("Lihzahrd Temple placed at {} {}", data.x, data.z);
        }
        long key = level.getSeed() * 31 + ((long) data.x << 32 ^ data.z);
        if (cachedLayout == null || cachedKey != key) {
            cachedLayout = TempleLayout.build(level.getSeed(), data.x, data.z, FLOOR_Y);
            cachedKey = key;
        }
        return cachedLayout;
    }

    /** Spirals out from spawn for jungle that covers the whole temple footprint. */
    private static int[] findSite(ServerLevel level) {
        int half = TempleLayout.SIZE / 2 + 8;
        int[] firstJungle = null;
        for (int r = MIN_DISTANCE; r <= MAX_DISTANCE; r += 48) {
            int steps = Math.max(8, (int) (2 * Math.PI * r / 48));
            double offset = (level.getSeed() & 0xFFFF) / 65536.0 * Math.PI * 2;
            for (int i = 0; i < steps; i++) {
                double angle = offset + i * Math.PI * 2 / steps;
                int cx = (int) (Math.cos(angle) * r);
                int cz = (int) (Math.sin(angle) * r);
                if (!JungleFeature.isJungle(level, cx, cz)) {
                    continue;
                }
                if (firstJungle == null) {
                    firstJungle = new int[]{cx, cz};
                }
                boolean all = true;
                for (int dx = -half; dx <= half && all; dx += half) {
                    for (int dz = -half; dz <= half && all; dz += half) {
                        all = JungleFeature.isJungle(level, cx + dx, cz + dz);
                    }
                }
                if (all) {
                    return new int[]{cx, cz};
                }
            }
        }
        return firstJungle != null ? firstJungle : new int[]{MIN_DISTANCE * 2, 0};
    }

    // ---------------------------------------------------------------- chunk queue

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            synchronized (TempleWorld.class) {
                long key = event.getChunk().getPos().pack();
                if (cachedLayout != null && !touches(cachedLayout, ChunkPos.getX(key), ChunkPos.getZ(key))) {
                    return;
                }
                if (QUEUED.add(key)) {
                    QUEUE.add(key);
                }
            }
        }
    }

    private static synchronized @Nullable Long poll() {
        Long key = QUEUE.poll();
        if (key != null) {
            QUEUED.remove(key);
        }
        return key;
    }

    private static boolean touches(TempleLayout layout, int cx, int cz) {
        int[] f = layout.footprint();
        return cx * 16 + 15 >= f[0] && cx * 16 <= f[2] && cz * 16 + 15 >= f[1] && cz * 16 <= f[3];
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        TempleLayout layout = layout(level);
        TempleWorld data = get(server);
        int done = 0;
        while (done < CHUNKS_PER_TICK) {
            Long key = poll();
            if (key == null) {
                break;
            }
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (!touches(layout, cx, cz) || data.built.contains(key) || !level.hasChunk(cx, cz)) {
                continue;
            }
            buildChunk(level, layout, cx, cz);
            data.built.add(key);
            data.setDirty();
            done++;
        }
    }

    // ---------------------------------------------------------------- building

    private static void buildChunk(ServerLevel level, TempleLayout layout, int cx, int cz) {
        BlockState brick = TempleContent.LIHZAHRD_BRICK.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int[] b = layout.bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = cx * 16;
        int minZ = cz * 16;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                boolean mass = x >= b[0] && x <= b[3] && z >= b[2] && z <= b[5];
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int top = mass ? b[4] : b[1];
                for (TempleLayout.Box box : layout.boxes()) {
                    if (x >= box.x0() && x <= box.x1() && z >= box.z0() && z <= box.z1()) {
                        top = Math.max(top, Math.min(box.y1(), surface + 1));
                    }
                }
                for (int y = b[1]; y <= top; y++) {
                    TempleLayout.Box inside = boxAt(layout, x, y, z, surface);
                    BlockState want = inside != null ? air : mass && y <= b[4] ? brick : null;
                    if (want == null) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos) != want) {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        for (TempleLayout.Placement placement : layout.placements()) {
            BlockPos at = placement.pos();
            if (at.getX() >> 4 == cx && at.getZ() >> 4 == cz) {
                decorate(level, layout, placement);
            }
        }
        ladders(level, layout, cx, cz);
    }

    private static @Nullable TempleLayout.Box boxAt(TempleLayout layout, int x, int y, int z, int surface) {
        for (TempleLayout.Box box : layout.boxes()) {
            int y1 = box.kind() == TempleLayout.Kind.SHAFT ? surface + 1 : box.y1();
            if (x >= box.x0() && x <= box.x1() && z >= box.z0() && z <= box.z1() && y >= box.y0() && y <= y1) {
                return box;
            }
        }
        return null;
    }

    private static void decorate(ServerLevel level, TempleLayout layout, TempleLayout.Placement placement) {
        BlockPos pos = placement.pos();
        BlockState brick = TempleContent.LIHZAHRD_BRICK.get().defaultBlockState();
        switch (placement.decor()) {
            case CHEST -> {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, placement.facing()), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, CHEST_LOOT);
            }
            case SPIKES -> {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, TempleContent.WOODEN_SPIKES.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            case DART_TRAP -> {
                if (level.getBlockState(pos).is(brick.getBlock())) {
                    level.setBlock(pos, TempleContent.DART_TRAP.get().defaultBlockState()
                        .setValue(com.terracraft.block.DartTrapBlock.FACING, placement.facing()), Block.UPDATE_CLIENTS);
                }
            }
            case LAMP -> {
                if (level.getBlockState(pos).is(brick.getBlock()) || placement.facing() == Direction.UP) {
                    level.setBlock(pos, Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            case ALTAR -> level.setBlock(pos, TempleContent.LIHZAHRD_ALTAR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            case DOOR -> {
                level.setBlock(pos, TempleContent.LOCKED_LIHZAHRD_DOOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(pos.above(), TempleContent.LOCKED_LIHZAHRD_DOOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            default -> { }
        }
    }

    /** A ladder up the far wall of the shaft, with mud behind it wherever the cave leaves no wall. */
    private static void ladders(ServerLevel level, TempleLayout layout, int cx, int cz) {
        Direction side = layout.doorFacing();
        BlockPos ladderColumn = layout.shaftBottom().relative(side);
        if (ladderColumn.getX() >> 4 != cx || ladderColumn.getZ() >> 4 != cz) {
            return;
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, side.getOpposite());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ladderColumn.getX(), ladderColumn.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = layout.floorY(); y <= surface; y++) {
            pos.set(ladderColumn.getX(), y, ladderColumn.getZ());
            BlockPos behind = pos.relative(side);
            if (!level.getBlockState(behind).isSolid()) {
                level.setBlock(behind, Blocks.MUD.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.setBlock(pos, ladder, Block.UPDATE_CLIENTS);
        }
    }
}
