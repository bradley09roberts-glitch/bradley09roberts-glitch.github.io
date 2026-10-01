package com.starforged.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.Starforged;
import com.starforged.StarforgedConfig;
import com.starforged.entity.projectile.MeteorEntity;
import com.starforged.network.ModNetwork;
import com.starforged.network.SkyStatePacket;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Starfall: on some nights the sky breaks open and meteors rain down around every player.
 * Natural meteors blast craters and leave meteorites rich in Starmetal; some carry Star Mites, Void Stalkers
 * or - very rarely - an Astral Egg. Nebula Rays drift across the violet sky while it lasts.
 */
public final class StarfallManager {
    private static final List<ImpactSite> SITES = new ArrayList<>();
    private static float eclipseLevel;

    private record ImpactSite(ResourceKey<Level> dimension, Vec3 pos, boolean hollow, long until) {
    }

    public static final class Data extends SavedData {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("active").forGetter(d -> d.active),
            Codec.LONG.fieldOf("last_rolled_day").forGetter(d -> d.lastRolledDay),
            Codec.BOOL.fieldOf("first_done").forGetter(d -> d.firstDone),
            Codec.LONG.fieldOf("forced_until").forGetter(d -> d.forcedUntil),
            Codec.INT.optionalFieldOf("sovereigns_defeated", 0).forGetter(d -> d.sovereignsDefeated)
        ).apply(i, Data::new));
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Starforged.id("starfall"), Data::new, CODEC, null);

        boolean active;
        long lastRolledDay;
        boolean firstDone;
        long forcedUntil;
        int sovereignsDefeated;

        public Data() {
            this(false, -1L, false, 0L, 0);
        }

        private Data(boolean active, long lastRolledDay, boolean firstDone, long forcedUntil, int sovereignsDefeated) {
            this.active = active;
            this.lastRolledDay = lastRolledDay;
            this.firstDone = firstDone;
            this.forcedUntil = forcedUntil;
            this.sovereignsDefeated = sovereignsDefeated;
        }

        public int sovereignsDefeated() {
            return this.sovereignsDefeated;
        }
    }

    private StarfallManager() {
    }

    public static Data data(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(Data.TYPE);
    }

    public static boolean isActive(MinecraftServer server) {
        return data(server).active;
    }

    public static void recordSovereignDefeat(MinecraftServer server) {
        Data data = data(server);
        data.sovereignsDefeated++;
        data.setDirty();
    }

    /** Called once per server tick for the overworld. */
    public static void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Data data = data(server);
        long time = level.getOverworldClockTime();
        long timeOfDay = Math.floorMod(time, 24000L);
        long day = Math.floorDiv(time, 24000L);
        boolean night = timeOfDay >= 12600L && timeOfDay < 23200L;

        if (!data.active) {
            if (night && data.lastRolledDay != day) {
                data.lastRolledDay = day;
                data.setDirty();
                boolean first = !data.firstDone && StarforgedConfig.FIRST_NIGHT_STARFALL.get();
                if (first || level.getRandom().nextDouble() < StarforgedConfig.STARFALL_CHANCE.get()) {
                    begin(level, data, 0L);
                }
            }
        } else {
            boolean forced = data.forcedUntil > 0L;
            boolean expired = forced ? level.getGameTime() > data.forcedUntil : !night;
            if (expired) {
                end(level, data);
            } else {
                spawnAroundPlayers(level);
            }
        }
        tickSites(level);
    }

    public static void begin(ServerLevel level, Data data, long forcedDuration) {
        data.active = true;
        data.firstDone = true;
        data.forcedUntil = forcedDuration > 0 ? level.getGameTime() + forcedDuration : 0L;
        data.setDirty();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 25));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("event.starforged.starfall.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("event.starforged.starfall.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            player.displayClientMessage(Component.translatable("event.starforged.starfall.begin").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.STARFALL_BEGIN.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        }
        syncSky(level.getServer());
    }

    public static void end(ServerLevel level, Data data) {
        data.active = false;
        data.forcedUntil = 0L;
        data.setDirty();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.displayClientMessage(Component.translatable("event.starforged.starfall.end").withStyle(ChatFormatting.GRAY), false);
        }
        syncSky(level.getServer());
    }

    public static void forceStart(ServerLevel level, long durationTicks) {
        begin(level, data(level.getServer()), Math.max(200L, durationTicks));
    }

    public static void forceStop(ServerLevel level) {
        Data data = data(level.getServer());
        if (data.active) {
            end(level, data);
        }
    }

    /** Sets how dark the Eclipse Sovereign's fight makes the sky (0..1) and syncs it to every client. */
    public static void setEclipse(MinecraftServer server, float value) {
        if (Math.abs(value - eclipseLevel) > 0.001F) {
            eclipseLevel = value;
            syncSky(server);
        }
    }

    public static void syncSky(MinecraftServer server) {
        SkyStatePacket packet = new SkyStatePacket(isActive(server), eclipseLevel);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.sendTo(player, packet);
        }
    }

    public static void syncSky(ServerPlayer player) {
        ModNetwork.sendTo(player, new SkyStatePacket(isActive(player.level().getServer()), eclipseLevel));
    }

    private static void spawnAroundPlayers(ServerLevel level) {
        RandomSource random = level.getRandom();
        int interval = StarforgedConfig.METEOR_INTERVAL.get();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (random.nextInt(interval) == 0) {
                spawnMeteorNear(level, player.position(), random);
            }
            if (random.nextInt(1400) == 0) {
                spawnCreatureNear(level, player, ModEntities.NEBULA_RAY.get(), 20, 40, 22 + random.nextInt(14), 3);
            }
            if (random.nextInt(900) == 0 && level.isDarkOutside()) {
                spawnCreatureNear(level, player, ModEntities.VOID_STALKER.get(), 18, 36, 0, 3);
            }
        }
    }

    public static MeteorEntity spawnMeteorNear(ServerLevel level, Vec3 center, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 20.0 + Math.pow(random.nextDouble(), 0.8) * 90.0;
        int x = (int) (center.x + Math.cos(angle) * dist);
        int z = (int) (center.z + Math.sin(angle) * dist);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        Vec3 target = Vec3.atBottomCenterOf(ground);
        double approach = random.nextDouble() * Math.PI * 2;
        Vec3 start = target.add(Math.cos(approach) * 55.0, 95.0 + random.nextDouble() * 20.0, Math.sin(approach) * 55.0);
        boolean big = random.nextFloat() < 0.08F;
        float size = big ? 2.0F + random.nextFloat() * 0.4F : 0.7F + random.nextFloat() * 1.0F;
        MeteorEntity.Kind kind = random.nextFloat() < 0.04F ? MeteorEntity.Kind.HOLLOW : MeteorEntity.Kind.NATURAL;
        return MeteorEntity.launch(level, start, target, size, kind, null, 1.7F + random.nextFloat() * 0.6F);
    }

    private static void spawnCreatureNear(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, int minDist, int maxDist, int heightAbove, int cap) {
        AABB area = player.getBoundingBox().inflate(64.0);
        if (level.getEntitiesOfClass(Mob.class, area, e -> e.getType() == type).size() >= cap) {
            return;
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = minDist + random.nextDouble() * (maxDist - minDist);
        int x = (int) (player.getX() + Math.cos(angle) * dist);
        int z = (int) (player.getZ() + Math.sin(angle) * dist);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (heightAbove == 0 && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, ground) > 7) {
            return;
        }
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        mob.snapTo(ground.getX() + 0.5, ground.getY() + heightAbove, ground.getZ() + 0.5, random.nextFloat() * 360F, 0);
        if (level.noCollision(mob)) {
            level.addFreshEntity(mob);
        }
    }

    public static void markImpact(ServerLevel level, Vec3 pos, boolean hollow) {
        SITES.add(new ImpactSite(level.dimension(), pos, hollow, level.getGameTime() + (hollow ? 20 * 180 : 20 * 50)));
    }

    private static void tickSites(ServerLevel level) {
        if (SITES.isEmpty() || level.getGameTime() % 3 != 0) {
            return;
        }
        Iterator<ImpactSite> it = SITES.iterator();
        while (it.hasNext()) {
            ImpactSite site = it.next();
            if (!site.dimension.equals(level.dimension())) {
                continue;
            }
            if (level.getGameTime() > site.until) {
                it.remove();
                continue;
            }
            Vec3 p = site.pos;
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, p.x, p.y + 0.5, p.z, 1, 0.6, 0.2, 0.6, 0.01);
            if (site.hollow) {
                // A shimmering beacon marks the resting place of the Astral Egg.
                for (int i = 0; i < 6; i++) {
                    double y = p.y + level.getRandom().nextDouble() * 48.0;
                    level.sendParticles(ModParticles.ASTRAL_GLINT.get(), true, true, p.x + (level.getRandom().nextDouble() - 0.5) * 0.4, y,
                        p.z + (level.getRandom().nextDouble() - 0.5) * 0.4, 1, 0, 0, 0, 0);
                }
                level.sendParticles(ParticleTypes.END_ROD, true, true, p.x, p.y + 1.0, p.z, 1, 0.1, 2.0, 0.1, 0.02);
            }
        }
    }
}
