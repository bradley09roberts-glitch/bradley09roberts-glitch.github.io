package com.terracraft.world.hardmode;

import com.terracraft.TerraCraft;
import com.terracraft.block.AltarBlock;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.progression.WorldVariants;
import com.terracraft.registry.content.EvilContent;
import com.terracraft.registry.content.HardmodeContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The world's change at Hardmode, the way Terraria does it:
 * <ul>
 *     <li>when the Wall of Flesh dies, "The ancient spirits of light and dark have been released": two long stripes
 *     spread out from near spawn in a V, one turned into the Hallow (pearlstone, hallowed grass, pearlsand,
 *     pearlwood) and one into the world's evil;</li>
 *     <li>the Hallow and the evil biomes slowly spread into the blocks around them;</li>
 *     <li>each Demon/Crimson Altar smashed with a Pwnhammer blesses the world with the next Hardmode ore
 *     (Cobalt/Palladium, then Mythril/Orichalcum, then Adamantite/Titanium).</li>
 * </ul>
 * Chunks are changed when they are loaded (a few per tick), and each change is remembered per chunk
 * ({@link HardmodeChunks}), so the whole world converts as players explore it, including chunks generated later.
 */
public final class HardmodeWorld {
    public static final Identifier ALTARS = TerraCraft.id("altars_smashed");
    private static final int CHUNKS_PER_TICK = 3;
    private static final Deque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();

    public enum Infection { NONE, HALLOW, EVIL }

    private HardmodeWorld() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HardmodeWorld::onChunkLoad);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HardmodeWorld::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HardmodeWorld::onBlockBreak);
        ProgressionManager.addListener((server, flag, value) -> {
            if (flag == ProgressionFlags.HARDMODE && value) {    // the announcement itself comes from ProgressionManager
                queueLoaded(server.overworld());
            }
        });
    }

    // ---------------------------------------------------------------- stripes

    /** Which stripe a column lies in: the Hallow ray and the evil ray leave the spawn area in a V. */
    public static Infection stripe(long seed, double x, double z) {
        double angle = (Math.floorMod(seed * 31 + 17, 360)) * Mth.DEG_TO_RAD;
        if (inRay(x, z, angle)) {
            return Infection.HALLOW;
        }
        if (inRay(x, z, angle + 1.9)) {
            return Infection.EVIL;
        }
        return Infection.NONE;
    }

    private static boolean inRay(double x, double z, double angle) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        double along = x * dx + z * dz;
        if (along < 160 || along > 4000) {
            return false;
        }
        double across = Math.abs(-x * dz + z * dx);
        return across < 34 + 10 * Math.sin(along * 0.021) + 6 * Math.sin(along * 0.057);
    }

    // ---------------------------------------------------------------- chunk queue

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            enqueue(event.getChunk().getPos().pack());
        }
    }

    private static synchronized void enqueue(long key) {
        if (QUEUED.add(key)) {
            QUEUE.add(key);
        }
    }

    private static synchronized Long poll() {
        Long key = QUEUE.poll();
        if (key != null) {
            QUEUED.remove(key);
        }
        return key;
    }

    /** Re-checks every chunk loaded around the players (Hardmode starts, an altar was smashed). */
    public static void queueLoaded(ServerLevel level) {
        int radius = level.getServer().getPlayerList().getViewDistance() + 1;
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    enqueue(ChunkPos.pack(center.x() + dx, center.z() + dz));
                }
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!ProgressionManager.isHardmode(server)) {
            return;
        }
        ServerLevel level = server.overworld();
        HardmodeChunks data = HardmodeChunks.get(server);
        int altars = ProgressionManager.data(server).counter(ALTARS);
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            Long key = poll();
            if (key == null) {
                break;
            }
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (!level.hasChunk(cx, cz)) {
                continue;
            }
            update(level, data, cx, cz, altars);
        }
        if (server.getTickCount() % 20 == 3) {
            spread(level);
        }
    }

    private static void update(ServerLevel level, HardmodeChunks data, int cx, int cz, int altars) {
        long key = ChunkPos.pack(cx, cz);
        int done = data.flags(key);
        int now = done;
        if ((done & HardmodeChunks.STRIPES) == 0) {
            convertStripes(level, cx, cz);
            now |= HardmodeChunks.STRIPES;
        }
        for (int tier = 1; tier <= Math.min(3, altars); tier++) {
            int bit = HardmodeChunks.tierBit(tier);
            if ((done & bit) == 0) {
                placeOre(level, cx, cz, tier);
                now |= bit;
            }
        }
        if (now != done) {
            data.setFlags(key, now);
        }
    }

    // ---------------------------------------------------------------- conversion

    private static void convertStripes(ServerLevel level, int cx, int cz) {
        long seed = level.getSeed();
        boolean crimson = ProgressionManager.data(level.getServer()).variants().evil() == WorldVariants.WorldEvil.CRIMSON;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = cx * 16 + dx;
                int z = cz * 16 + dz;
                Infection kind = stripe(seed, x, z);
                if (kind == Infection.NONE) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int bottom = Math.max(level.getMinY() + 5, -48);
                for (int y = surface; y >= bottom; y--) {
                    pos.set(x, y, z);
                    BlockState converted = converted(level.getBlockState(pos), kind, crimson);
                    if (converted != null) {
                        level.setBlock(pos, converted, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** What a block becomes in the Hallow or the world evil, or null if it does not convert. */
    public static BlockState converted(BlockState state, Infection kind, boolean crimson) {
        boolean hallow = kind == Infection.HALLOW;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            return (hallow ? HardmodeContent.HALLOWED_GRASS : crimson ? EvilContent.CRIMSON_GRASS : EvilContent.CORRUPT_GRASS).get().defaultBlockState();
        }
        if (state.is(BlockTags.LOGS) && state.hasProperty(RotatedPillarBlock.AXIS) && !isInfected(state)) {
            Block log = (hallow ? HardmodeContent.PEARLWOOD.get() : crimson ? EvilContent.SHADEWOOD.get() : EvilContent.EBONWOOD.get());
            return log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        if (state.is(BlockTags.LEAVES) && !isInfected(state)) {
            return (hallow ? HardmodeContent.HALLOWED_LEAVES : crimson ? EvilContent.SHADEWOOD_LEAVES : EvilContent.EBONWOOD_LEAVES).get().defaultBlockState();
        }
        if (hallow && (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND))) {
            return HardmodeContent.PEARLSAND.get().defaultBlockState();
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
            || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF) || state.is(Blocks.SANDSTONE) || state.is(Blocks.COBBLESTONE)) {
            return (hallow ? HardmodeContent.PEARLSTONE : crimson ? EvilContent.CRIMSTONE : EvilContent.EBONSTONE).get().defaultBlockState();
        }
        if (hallow && (state.is(EvilContent.EBONSTONE.get()) || state.is(EvilContent.CRIMSTONE.get()))) {
            return null;   // the Hallow and the evil do not convert each other
        }
        return null;
    }

    public static Infection infectionOf(BlockState state) {
        if (state.is(HardmodeContent.PEARLSTONE.get()) || state.is(HardmodeContent.HALLOWED_GRASS.get()) || state.is(HardmodeContent.PEARLSAND.get())) {
            return Infection.HALLOW;
        }
        if (state.is(EvilContent.EBONSTONE.get()) || state.is(EvilContent.CRIMSTONE.get()) || state.is(EvilContent.CORRUPT_GRASS.get())
            || state.is(EvilContent.CRIMSON_GRASS.get())) {
            return Infection.EVIL;
        }
        return Infection.NONE;
    }

    private static boolean isInfected(BlockState state) {
        return state.is(HardmodeContent.PEARLWOOD.get()) || state.is(HardmodeContent.HALLOWED_LEAVES.get()) || state.is(EvilContent.EBONWOOD.get())
            || state.is(EvilContent.SHADEWOOD.get()) || state.is(EvilContent.EBONWOOD_LEAVES.get()) || state.is(EvilContent.SHADEWOOD_LEAVES.get());
    }

    /**
     * Biome spread near players: random infected blocks convert a random block next to them. Slow, like Terraria
     * (where it can be stopped by digging trenches; here as well, since only touching blocks convert).
     */
    private static void spread(ServerLevel level) {
        RandomSource random = level.getRandom();
        boolean crimson = ProgressionManager.data(level.getServer()).variants().evil() == WorldVariants.WorldEvil.CRIMSON;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < 40; i++) {
                pos.set(player.getBlockX() + random.nextInt(81) - 40, player.getBlockY() + random.nextInt(49) - 24, player.getBlockZ() + random.nextInt(81) - 40);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                Infection kind = infectionOf(level.getBlockState(pos));
                if (kind == Infection.NONE) {
                    continue;
                }
                pos.move(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState converted = converted(level.getBlockState(pos), kind, crimson);
                if (converted != null) {
                    level.setBlock(pos, converted, Block.UPDATE_ALL);
                }
            }
        }
    }

    // ---------------------------------------------------------------- altars and Hardmode ores

    private static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getState().getBlock() instanceof AltarBlock)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!ProgressionManager.isHardmode(server)) {
            return;
        }
        int count = ProgressionManager.incrementCounter(server, ALTARS, 1);
        ProgressionManager.set(server, ProgressionFlags.ALTAR_SMASHED, true);
        if (count <= 3) {
            String ore = oreName(server, count);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.terracraft.ore_blessing",
                Component.translatable("ore.terracraft." + ore)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            queueLoaded(level);
        }
    }

    /** The ore of a tier this world uses (one of each Terraria pair). */
    public static String oreName(MinecraftServer server, int tier) {
        WorldVariants.OrePair pair = switch (tier) {
            case 1 -> WorldVariants.OrePair.COBALT_PALLADIUM;
            case 2 -> WorldVariants.OrePair.MYTHRIL_ORICHALCUM;
            default -> WorldVariants.OrePair.ADAMANTITE_TITANIUM;
        };
        return ProgressionManager.data(server).variants().chosenOre(pair);
    }

    /** Scatters veins of a tier's ore through the chunk's rock: deeper and rarer for each higher tier. */
    private static void placeOre(ServerLevel level, int cx, int cz, int tier) {
        BlockState ore = HardmodeContent.byName(oreName(level.getServer(), tier)).ore().get().defaultBlockState();
        RandomSource random = RandomSource.create(ChunkPos.pack(cx, cz) * 31 + tier * 7919L + level.getSeed());
        int veins = switch (tier) {
            case 1 -> 4;
            case 2 -> 3;
            default -> 2;
        };
        int top = switch (tier) {
            case 1 -> 40;
            case 2 -> 10;
            default -> -10;
        };
        int bottom = level.getMinY() + 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int v = 0; v < veins; v++) {
            int x = cx * 16 + random.nextInt(16);
            int z = cz * 16 + random.nextInt(16);
            int y = bottom + random.nextInt(Math.max(1, top - bottom));
            int size = 4 + random.nextInt(5);
            for (int b = 0; b < size; b++) {
                pos.set(x + random.nextInt(3) - 1, y + random.nextInt(3) - 1, z + random.nextInt(3) - 1);
                if (pos.getX() >> 4 != cx || pos.getZ() >> 4 != cz) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(HardmodeContent.PEARLSTONE.get()) || state.is(EvilContent.EBONSTONE.get())
                    || state.is(EvilContent.CRIMSTONE.get())) {
                    level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
                }
                x = pos.getX();
                y = pos.getY();
                z = pos.getZ();
            }
        }
    }
}
