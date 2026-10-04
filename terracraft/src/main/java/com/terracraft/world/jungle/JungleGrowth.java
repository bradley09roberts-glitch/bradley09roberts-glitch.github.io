package com.terracraft.world.jungle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.JungleContent;
import com.terracraft.registry.content.PlanteraContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Things that grow in the underground jungle as the world progresses, near players who are down there:
 * <ul>
 *     <li>Life Fruit, once any mechanical boss has been defeated;</li>
 *     <li>Plantera's Bulb, once all three are dead (one at a time within {@link #BULB_SPACING} blocks; the bulbs
 *     are remembered in {@code data/terracraft/jungle_growth.dat}).</li>
 * </ul>
 */
public final class JungleGrowth extends SavedData {
    private static final int INTERVAL = 600;
    private static final int BULB_SPACING = 120;

    private static final Codec<JungleGrowth> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.listOf().optionalFieldOf("bulbs", List.of()).forGetter(g -> g.bulbs)
    ).apply(i, JungleGrowth::new));
    public static final SavedDataType<JungleGrowth> TYPE = new SavedDataType<>(TerraCraft.id("jungle_growth"), JungleGrowth::new, CODEC, null);

    private final List<Long> bulbs;

    public JungleGrowth() {
        this.bulbs = new ArrayList<>();
    }

    private JungleGrowth(List<Long> bulbs) {
        this.bulbs = new ArrayList<>(bulbs);
    }

    public static JungleGrowth get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(JungleGrowth::onServerTick);
        com.terracraft.command.TerrariaCommand.addExtension(root -> root.then(net.minecraft.commands.Commands.literal("worldgen")
            .then(net.minecraft.commands.Commands.literal("bulb").executes(ctx -> {
                BlockPos at = forceBulb(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()));
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(at == null
                    ? "No jungle grass cave floor found nearby" : "Plantera's Bulb grown at " + at.toShortString()), false);
                return at == null ? 0 : 1;
            }))));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0 || !ProgressionManager.has(server, ProgressionFlags.ANY_MECH_BOSS)) {
            return;
        }
        ServerLevel level = server.overworld();
        boolean bulbs = ProgressionManager.has(server, ProgressionFlags.MECH_BOSSES);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !inUndergroundJungle(level, player.blockPosition())) {
                continue;
            }
            RandomSource random = player.getRandom();
            if (random.nextInt(3) == 0) {
                growLifeFruit(level, player.blockPosition(), random);
            }
            if (bulbs && random.nextInt(4) == 0) {
                growBulb(level, player.blockPosition(), random);
            }
        }
    }

    public static boolean inUndergroundJungle(ServerLevel level, BlockPos pos) {
        return JungleFeature.isJungle(level, pos.getX(), pos.getZ())
            && pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 15;
    }

    /** Grows a Life Fruit on a jungle-grass cave floor near the player, unless one is already close by. */
    private static void growLifeFruit(ServerLevel level, BlockPos center, RandomSource random) {
        BlockPos spot = findSpot(level, center, random, 1);
        if (spot == null) {
            return;
        }
        Block fruit = PlanteraContent.LIFE_FRUIT_PLANT.get();
        for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-8, -4, -8), spot.offset(8, 4, 8))) {
            if (level.getBlockState(pos).is(fruit)) {
                return;
            }
        }
        level.setBlock(spot, fruit.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Grows Plantera's Bulb near the player, if no other bulb stands within {@link #BULB_SPACING} blocks. */
    private static void growBulb(ServerLevel level, BlockPos center, RandomSource random) {
        JungleGrowth data = get(level.getServer());
        Block bulb = PlanteraContent.PLANTERA_BULB.get();
        data.bulbs.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            return level.isLoaded(pos) && !level.getBlockState(pos).is(bulb);
        });
        for (long packed : data.bulbs) {
            if (BlockPos.of(packed).closerThan(center, BULB_SPACING)) {
                return;
            }
        }
        BlockPos spot = findSpot(level, center, random, 2);
        if (spot != null) {
            level.setBlock(spot, bulb.defaultBlockState(), Block.UPDATE_ALL);
            data.bulbs.add(spot.asLong());
            data.setDirty();
        }
    }

    /** Air above jungle grass, under a roof, with {@code headroom} free blocks, 10-32 blocks from the player. */
    private static @Nullable BlockPos findSpot(ServerLevel level, BlockPos center, RandomSource random, int headroom) {
        Block grass = JungleContent.JUNGLE_GRASS.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 48; attempt++) {
            // a random column, searched top to bottom for a grassy cave floor
            int x = center.getX() + random.nextInt(65) - 32;
            int z = center.getZ() + random.nextInt(65) - 32;
            if (Math.abs(x - center.getX()) < 10 && Math.abs(z - center.getZ()) < 10 || !level.isLoaded(pos.set(x, center.getY(), z))) {
                continue;
            }
            for (int y = center.getY() + 16; y >= center.getY() - 16; y--) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos.below()).is(grass) || level.canSeeSky(pos)) {
                    continue;
                }
                boolean free = true;
                for (int h = 0; h < headroom; h++) {
                    free &= level.getBlockState(pos.above(h)).isAir();
                }
                if (free) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    /** Debug/showcase: grows a bulb right now near the given spot (ignores progression). */
    public static @Nullable BlockPos forceBulb(ServerLevel level, BlockPos center) {
        BlockPos spot = findSpot(level, center, level.getRandom(), 2);
        if (spot != null) {
            level.setBlock(spot, PlanteraContent.PLANTERA_BULB.get().defaultBlockState(), Block.UPDATE_ALL);
            JungleGrowth data = get(level.getServer());
            data.bulbs.add(spot.asLong());
            data.setDirty();
        }
        return spot;
    }
}
