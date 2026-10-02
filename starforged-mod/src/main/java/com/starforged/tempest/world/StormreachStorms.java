package com.starforged.tempest.world;

import com.starforged.Starforged;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.entity.ThunderjawAlphaEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The weather of Stormreach:
 * <ul>
 *     <li>Lightning strikes the islands around every player. Stay out of the open, or wear Aetherium.</li>
 *     <li><b>Shardwind Cliffs</b> - every twenty seconds a crosswind tears across the cliffs for four seconds.</li>
 *     <li><b>Endless Eye</b> - the wind never stops, wheeling slowly around the compass.</li>
 *     <li><b>Supercell</b> - now and then a supercell rolls in: lightning hammers the area for a minute and a
 *     Thunderjaw Alpha charges out of the storm.</li>
 * </ul>
 */
public final class StormreachStorms {
    public static final ResourceKey<Biome> SHARDWIND_CLIFFS = biome("shardwind_cliffs");
    public static final ResourceKey<Biome> ENDLESS_EYE = biome("endless_eye");
    private static final int GUST_PERIOD = 400;
    private static final int GUST_LENGTH = 80;
    private static final int SUPERCELL_LENGTH = 1200;
    private static final List<Supercell> SUPERCELLS = new ArrayList<>();
    private static long supercellCooldownUntil;
    /** When each player arrived in the Stormreach: a Supercell never greets a traveller in their first few minutes. */
    private static final java.util.Map<java.util.UUID, Long> ARRIVED = new java.util.HashMap<>();
    private static final long SUPERCELL_GRACE = 6000;

    private StormreachStorms() {
    }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, Starforged.id(name));
    }

    private static final class Supercell {
        final Vec3 center;
        int age;
        boolean alphaSpawned;

        Supercell(Vec3 center) {
            this.center = center;
        }
    }

    public static void tick(ServerLevel level) {
        if (!StormreachTravel.isStormreach(level)) {
            return;
        }
        long time = level.getGameTime();
        RandomSource random = level.getRandom();
        if (time % 200 == 0) {
            ARRIVED.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            long arrived = ARRIVED.computeIfAbsent(player.getUUID(), id -> time);
            Holder<Biome> biome = level.getBiome(player.blockPosition());
            if (biome.is(SHARDWIND_CLIFFS)) {
                crosswind(level, player, time);
            } else if (biome.is(ENDLESS_EYE)) {
                double angle = time * 0.004;
                push(player, new Vec3(Math.cos(angle), 0, Math.sin(angle)), 0.03);
            }
            if ((time + player.getId()) % 30 == 0 && random.nextInt(5) == 0) {
                strikeNear(level, player.position(), 3, 30, random);
            }
            if ((time + player.getId()) % 240 == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.STORM_WIND.get(), SoundSource.WEATHER, 0.6F,
                    0.9F + random.nextFloat() * 0.2F);
            }
            if (time % 400 == 0 && time > supercellCooldownUntil && time - arrived > SUPERCELL_GRACE && SUPERCELLS.isEmpty()
                && random.nextInt(60) == 0) {
                startSupercell(level, player.position().add(random.nextInt(31) - 15, 0, random.nextInt(31) - 15));
            }
        }
        tickSupercells(level);
    }

    private static void crosswind(ServerLevel level, ServerPlayer player, long time) {
        long phase = time % GUST_PERIOD;
        if (phase >= GUST_LENGTH) {
            return;
        }
        double angle = (time / GUST_PERIOD) * 2.39996; // a new direction every gust (golden angle)
        Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        if (phase == 0) {
            player.sendOverlayMessage(Component.translatable("event.starforged.stormreach.gust").withStyle(ChatFormatting.AQUA));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.GUST.get(), SoundSource.WEATHER, 1.0F, 1.0F);
        }
        push(player, dir, player.isShiftKeyDown() ? 0.015 : 0.06);
        if (phase % 2 == 0) {
            level.sendParticles(player, ModParticles.STORM_WISP.get(), false, false, player.getX() - dir.x * 4, player.getY() + 1.0,
                player.getZ() - dir.z * 4, 4, 2.0, 1.0, 2.0, 0.0);
        }
    }

    private static void push(ServerPlayer player, Vec3 dir, double strength) {
        if (player.getAbilities().flying || player.isPassenger()) {
            return;
        }
        double resist = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        double s = strength * (1.0 - Mth.clamp(resist, 0.0, 0.8));
        player.setDeltaMovement(player.getDeltaMovement().add(dir.scale(s)));
        player.hurtMarked = true;
    }

    /** One of the storm's own bolts, somewhere between {@code min} and {@code max} blocks from {@code around}. */
    public static void strikeNear(ServerLevel level, Vec3 around, int min, int max, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = min + random.nextDouble() * (max - min);
        int x = Mth.floor(around.x + Math.cos(angle) * dist);
        int z = Mth.floor(around.z + Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinY() + 4) {
            return;
        }
        TempestFx.strike(level, new Vec3(x + 0.5, y, z + 0.5), null, 6.0F, 2.5);
    }

    public static boolean startSupercell(ServerLevel level, Vec3 center) {
        if (!SUPERCELLS.isEmpty()) {
            return false;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(center.x), Mth.floor(center.z));
        SUPERCELLS.add(new Supercell(new Vec3(center.x, Math.max(y, level.getMinY() + 64), center.z)));
        supercellCooldownUntil = level.getGameTime() + 18000;
        SunFx.titleNear(level, center, 160.0, Component.translatable("event.starforged.supercell.title").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD),
            Component.translatable("event.starforged.supercell.subtitle").withStyle(ChatFormatting.GRAY), 10, 60, 20);
        level.playSound(null, center.x, center.y, center.z, TempestSounds.SUPERCELL.get(), SoundSource.WEATHER, 8.0F, 1.0F);
        return true;
    }

    private static void tickSupercells(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (Iterator<Supercell> it = SUPERCELLS.iterator(); it.hasNext(); ) {
            Supercell cell = it.next();
            cell.age++;
            if (cell.age % 12 == 0) {
                strikeNear(level, cell.center, 4, 30, random);
            }
            if (!cell.alphaSpawned && cell.age == 100) {
                cell.alphaSpawned = true;
                ThunderjawAlphaEntity alpha = TempestEntities.THUNDERJAW_ALPHA.get().create(level, EntitySpawnReason.EVENT);
                if (alpha != null) {
                    // Lands a little way off from the storm's eye, on solid ground if there is any.
                    BlockPos ground = BlockPos.containing(cell.center);
                    for (int attempt = 0; attempt < 8; attempt++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        BlockPos probe = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            BlockPos.containing(cell.center.add(Math.cos(a) * 12.0, 0, Math.sin(a) * 12.0)));
                        if (probe.getY() > level.getMinY() + 4) {
                            ground = probe;
                            break;
                        }
                    }
                    alpha.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                    level.addFreshEntity(alpha);
                    alpha.announce(level);
                }
            }
            if (cell.age >= SUPERCELL_LENGTH) {
                it.remove();
                SunFx.messageNear(level, cell.center, 160.0, Component.translatable("event.starforged.supercell.end").withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
