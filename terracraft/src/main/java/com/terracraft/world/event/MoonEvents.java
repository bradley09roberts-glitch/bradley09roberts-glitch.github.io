package com.terracraft.world.event;

import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.content.MobContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pumpkin Moon and Frost Moon: wave events. Every kill of an event creature earns points; enough points start the
 * next wave, which brings tougher creatures and more of the event bosses (Mourning Wood and Pumpking, Everscream,
 * Santa-NK1 and the Ice Queen). The moon sets at dawn wherever you got to; reaching the last wave marks it cleared.
 * The enemies are spawned here around players on the surface (the normal surface spawns are paused).
 */
public final class MoonEvents {
    /**
     * One creature of a moon's roster.
     *
     * @param fromWave  first wave it appears in
     * @param weight    spawn weight
     * @param maxAlive  most alive at once near a player (0 = no limit); doubles from {@code moreFrom}
     * @param points    points earned for killing it
     * @param flying    spawns in the air
     */
    private record Entry(RegistryObject<? extends EntityType<? extends TerrariaMob>> type, int fromWave, int weight, int maxAlive, int moreFrom,
                         int points, boolean flying) {}

    private static List<Entry> roster(TerrariaEvent event) {
        if (event == TerrariaEvents.PUMPKIN_MOON) {
            return List.of(
                new Entry(MobContent.SCARECROW, 1, 10, 0, 0, 5, false),
                new Entry(MobContent.SPLINTERLING, 2, 8, 0, 0, 10, false),
                new Entry(MobContent.MOURNING_WOOD, 2, 2, 1, 8, 50, false),
                new Entry(MobContent.HELLHOUND, 3, 7, 0, 0, 10, false),
                new Entry(MobContent.POLTERGEIST, 4, 6, 0, 0, 20, true),
                new Entry(MobContent.PUMPKING, 4, 2, 1, 10, 100, true),
                new Entry(MobContent.HEADLESS_HORSEMAN, 5, 3, 2, 0, 25, false));
        }
        return List.of(
            new Entry(MobContent.ZOMBIE_ELF, 1, 10, 0, 0, 1, false),
            new Entry(MobContent.GINGERBREAD_MAN, 1, 8, 0, 0, 2, false),
            new Entry(MobContent.ELF_ARCHER, 2, 7, 0, 0, 2, false),
            new Entry(MobContent.EVERSCREAM, 2, 2, 1, 9, 50, false),
            new Entry(MobContent.NUTCRACKER, 3, 6, 0, 0, 4, false),
            new Entry(MobContent.FLOCKO, 3, 6, 0, 0, 3, true),
            new Entry(MobContent.YETI, 4, 4, 2, 0, 15, false),
            new Entry(MobContent.SANTA_NK1, 7, 2, 1, 14, 75, false),
            new Entry(MobContent.ICE_QUEEN, 11, 2, 1, 16, 100, true));
    }

    private MoonEvents() {}

    /** Points needed to finish a wave. */
    private static int threshold(int wave) {
        return 25 * wave;
    }

    public static float waveProgress(TerrariaEvent event, EventState state) {
        return state.wave() >= event.waves() ? 1.0F : Mth.clamp(state.kills() / (float) threshold(state.wave()), 0.0F, 1.0F);
    }

    public static void announceWave(MinecraftServer server, TerrariaEvent event, int wave) {
        String names = roster(event).stream().filter(e -> e.fromWave() == wave || wave == 1 && e.fromWave() <= 1)
            .map(e -> Component.translatable(e.type().get().getDescriptionId()).getString()).collect(Collectors.joining(", "));
        Component text = Component.translatable("event.terracraft.wave", Component.translatable("event.terracraft." + event.id()), wave);
        server.getPlayerList().broadcastSystemMessage(names.isEmpty() ? text.copy().withStyle(ChatFormatting.GOLD)
            : text.copy().append(": " + names).withStyle(ChatFormatting.GOLD), false);
    }

    /** A kill: its points, and the next wave once the current one's points are reached. */
    static void onKill(MinecraftServer server, TerrariaEvent event, EventState state, LivingEntity killed) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).getPath();
        int points = roster(event).stream().filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.type().get()).getPath().equals(path))
            .mapToInt(Entry::points).findFirst().orElse(1);
        if (state.wave() >= event.waves()) {
            return;
        }
        state.kills += points;
        while (state.wave < event.waves() && state.kills >= threshold(state.wave)) {
            state.kills -= threshold(state.wave);
            state.wave++;
            announceWave(server, event, state.wave);
            if (state.wave == event.waves()) {
                ProgressionManager.markDefeated(server, event == TerrariaEvents.PUMPKIN_MOON ? ProgressionFlags.PUMPKIN_MOON : ProgressionFlags.FROST_MOON);
            }
        }
        state.setDirty();
    }

    /** Keeps the moon's creatures coming around every player out under the sky. */
    static void spawn(ServerLevel level, TerrariaEvent event, EventState state, RandomSource random) {
        List<Entry> available = roster(event).stream().filter(e -> e.fromWave() <= state.wave()).toList();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !level.canSeeSky(player.blockPosition().above())) {
                continue;
            }
            List<TerrariaMob> around = level.getEntitiesOfClass(TerrariaMob.class, player.getBoundingBox().inflate(64),
                m -> event.isMember(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath()));
            int cap = 8 + state.wave() / 2;
            for (int n = 0; n < 2 && around.size() < cap; n++) {
                Entry entry = pick(available, around, state.wave(), random);
                if (entry == null) {
                    break;
                }
                TerrariaMob mob = entry.type().get().create(level, EntitySpawnReason.EVENT);
                if (mob == null) {
                    break;
                }
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 22 + random.nextDouble() * 12;
                int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + (entry.flying() ? 8 : 0);
                if (com.terracraft.world.spawn.SafeZones.isSafe(level.getServer(), x, y, z)) {
                    break;
                }
                mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
                mob.setTarget(player);
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
                around = new ArrayList<>(around);
                around.add(mob);
            }
        }
    }

    private static Entry pick(List<Entry> available, List<TerrariaMob> around, int wave, RandomSource random) {
        Map<EntityType<?>, Long> counts = around.stream().collect(Collectors.groupingBy(m -> (EntityType<?>) m.getType(), Collectors.counting()));
        List<Entry> allowed = available.stream().filter(e -> {
            if (e.maxAlive() == 0) {
                return true;
            }
            int max = e.maxAlive() * (e.moreFrom() > 0 && wave >= e.moreFrom() ? 2 : 1);
            return counts.getOrDefault(e.type().get(), 0L) < max;
        }).toList();
        int total = allowed.stream().mapToInt(Entry::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Entry e : allowed) {
            roll -= e.weight();
            if (roll < 0) {
                return e;
            }
        }
        return null;
    }
}
