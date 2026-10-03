package com.terracraft.world;

import com.terracraft.TerraCraft;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.MeteorContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Meteorites, Terraria style: smashing a Shadow Orb or Crimson Heart has a 1 in 2 chance to send a meteor down
 * that night, and any night after the first smash has a small chance of one. It lands some distance from a
 * player ("A meteorite has landed!"), blasting a crater lined with Meteorite. {@code /terraria meteor} drops one.
 */
public final class MeteorManager {
    public static final Identifier PENDING = TerraCraft.id("meteor_pending");
    private static final Identifier LAST_X = TerraCraft.id("meteor_x");
    private static final Identifier LAST_Y = TerraCraft.id("meteor_y");
    private static final Identifier LAST_Z = TerraCraft.id("meteor_z");
    /** World counters cannot be negative, so crater coordinates are stored shifted by this offset. */
    private static final int COORD_OFFSET = 30_000_000;

    private MeteorManager() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MeteorManager::onServerTick);
        com.terracraft.command.TerrariaCommand.addExtension(root -> root.then(net.minecraft.commands.Commands.literal("meteor").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            return land(player.level(), player) ? 1 : 0;
        })));
    }

    /** Called when an orb or heart is smashed. */
    public static void onOrbSmashed(MinecraftServer server, RandomSource random) {
        if (random.nextBoolean()) {
            ProgressionManager.setCounter(server, PENDING, 1);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 == 31 && ProgressionManager.has(server, ProgressionFlags.METEOR_LANDED)) {
            spawnMeteorHeads(server.overworld());
        }
        if (server.getTickCount() % 200 != 77 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (!level.isDarkOutside()) {
            return;
        }
        boolean pending = ProgressionManager.data(server).counter(PENDING) > 0;
        // about 1 in 50 nights once an orb has been smashed (40 checks per night)
        boolean natural = ProgressionManager.has(server, ProgressionFlags.ORB_SMASHED) && level.getRandom().nextInt(2000) == 0;
        if (pending || natural) {
            List<ServerPlayer> players = level.players();
            ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
            if (land(level, player)) {
                ProgressionManager.setCounter(server, PENDING, 0);
            }
        }
    }

    /** Drops a meteorite 60-110 blocks from the player in loaded terrain. */
    public static boolean land(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 60 + random.nextDouble() * 50;
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surface = new BlockPos(x, y, z);
            if (!level.getFluidState(surface.below()).isEmpty() || y <= level.getSeaLevel() - 2) {
                continue;   // not into the sea
            }
            crater(level, surface, random);
            ProgressionManager.set(level.getServer(), ProgressionFlags.METEOR_LANDED, true);
            ProgressionManager.setCounter(level.getServer(), LAST_X, surface.getX() + COORD_OFFSET);
            ProgressionManager.setCounter(level.getServer(), LAST_Y, surface.getY() + COORD_OFFSET);
            ProgressionManager.setCounter(level.getServer(), LAST_Z, surface.getZ() + COORD_OFFSET);
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.terracraft.meteor.landed").withStyle(ChatFormatting.GREEN), false);
            TerraCraft.LOGGER.info("Meteorite landed at {}", surface);
            return true;
        }
        return false;
    }

    /** Meteor Heads haunt the latest crater while a player is near it (Terraria's Meteorite biome). */
    private static void spawnMeteorHeads(ServerLevel level) {
        var data = ProgressionManager.data(level.getServer());
        if (data.counter(LAST_X) == 0) {
            return;   // no crater recorded
        }
        BlockPos crater = new BlockPos(data.counter(LAST_X) - COORD_OFFSET, data.counter(LAST_Y) - COORD_OFFSET, data.counter(LAST_Z) - COORD_OFFSET);
        ServerPlayer player = null;
        for (ServerPlayer candidate : level.players()) {
            if (!candidate.isSpectator() && !candidate.isCreative() && candidate.blockPosition().distSqr(crater) < 40 * 40) {
                player = candidate;
                break;
            }
        }
        if (player == null || level.getEntitiesOfClass(com.terracraft.entity.mob.TerrariaMob.class, new net.minecraft.world.phys.AABB(crater).inflate(48),
            e -> e.getType() == com.terracraft.registry.content.MobContent.METEOR_HEAD.get()).size() >= 4) {
            return;
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double x = crater.getX() + Math.cos(angle) * (8 + random.nextInt(10));
        double z = crater.getZ() + Math.sin(angle) * (8 + random.nextInt(10));
        double y = crater.getY() + 6 + random.nextInt(6);
        var head = com.terracraft.registry.content.MobContent.METEOR_HEAD.get().create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
        if (head != null) {
            head.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
            head.finalizeSpawn(level, level.getCurrentDifficultyAt(crater), net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
            level.addFreshEntity(head);
        }
    }

    private static void crater(ServerLevel level, BlockPos surface, RandomSource random) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState meteorite = MeteorContent.METEORITE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = 8;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -11; dy <= 8; dy++) {
                    pos.set(surface.getX() + dx, surface.getY() + dy, surface.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    double crater = Math.sqrt(dx * dx + (dy - 1) * (dy - 1) * 1.4 + dz * dz);
                    double core = Math.sqrt(dx * dx + (dy + 4) * (dy + 4) * 1.6 + dz * dz);
                    if (crater <= 7.0) {
                        level.setBlock(pos, air, Block.UPDATE_ALL);
                    } else if (core <= 6.0 + random.nextDouble() * 0.8 && !state.isAir()) {
                        level.setBlock(pos, meteorite, Block.UPDATE_ALL);
                    }
                }
            }
        }
        level.playSound(null, surface, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, surface.getX() + 0.5, surface.getY() + 1, surface.getZ() + 0.5, 3, 2, 1, 2, 0);
        level.sendParticles(ParticleTypes.LAVA, surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5, 40, 3, 1, 3, 0);
    }
}
