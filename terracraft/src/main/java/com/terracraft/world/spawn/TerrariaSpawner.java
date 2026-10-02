package com.terracraft.world.spawn;

import com.google.gson.JsonElement;
import com.terracraft.TerraCraft;
import com.terracraft.config.TerraConfig;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.WorldProgression;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Terraria-style enemy spawning, run per player once a second.
 * <p>
 * Like Terraria, each player has a cap on nearby enemies (higher at night and underground) and a spawn
 * chance; candidates are picked 24-44 blocks away, the position's context (biome, Terraria layer, time, sky,
 * progression) filters the data-driven {@link SpawnRule}s and a weighted pick decides what appears.
 * Cost per player per second: one entity count query plus at most a few column scans.
 */
public final class TerrariaSpawner {
    private static volatile List<SpawnRule> rules = List.of();
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 44;

    private TerrariaSpawner() {}

    public static void register() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(TerrariaSpawner::onPlayerTick);
        SpawnCommands.register();
    }

    public static void load(Map<Identifier, JsonElement> data) {
        List<SpawnRule> loaded = new ArrayList<>();
        data.forEach((id, json) -> {
            try {
                loaded.addAll(SpawnRule.parseFile(json));
            } catch (Exception e) {
                TerraCraft.LOGGER.error("Invalid spawn rule file {}", id, e);
            }
        });
        rules = List.copyOf(loaded);
        TerraCraft.LOGGER.info("Loaded {} Terraria spawn rules", loaded.size());
    }

    public static List<SpawnRule> rules() {
        return rules;
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.player() instanceof ServerPlayer player && (player.tickCount + player.getId()) % 20 == 0) {
            tick(player);
        }
    }

    /** Terraria's per-player enemy cap for the player's surroundings. */
    public static int maxSpawns(ServerLevel level, TerrariaLayer layer) {
        int base = switch (layer) {
            case SURFACE, SPACE -> level.isBrightOutside() ? 4 : 7;
            case UNDERGROUND -> 6;
            case CAVERN, UNDERWORLD -> 7;
        };
        var event = com.terracraft.world.event.EventManager.active(level.getServer());
        float eventCap = event != null && layer.ordinal() <= TerrariaLayer.SURFACE.ordinal() ? event.capMultiplier() : 1.0F;
        return Math.max(0, Mth.floor(base * eventCap * TerraConfig.COMMON.maxSpawnsMultiplier.get()));
    }

    private static float spawnChance(ServerLevel level, TerrariaLayer layer) {
        float base = switch (layer) {
            case SURFACE, SPACE -> level.isBrightOutside() ? 0.12F : 0.3F;
            case UNDERGROUND -> 0.25F;
            case CAVERN, UNDERWORLD -> 0.3F;
        };
        var event = com.terracraft.world.event.EventManager.active(level.getServer());
        float eventRate = event != null && layer.ordinal() <= TerrariaLayer.SURFACE.ordinal() ? event.spawnRate() : 1.0F;
        return Math.min(1.0F, base * eventRate * TerraConfig.COMMON.spawnRateMultiplier.get().floatValue());
    }

    public static int nearbyEnemies(ServerPlayer player) {
        return player.level().getEntitiesOfClass(TerrariaMob.class, player.getBoundingBox().inflate(64, 40, 64), TerrariaMob::countsTowardSpawnCap).size();
    }

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!TerraConfig.COMMON.terrariaSpawning.get() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL || rules.isEmpty()) {
            return;
        }
        TerrariaLayer layer = TerrariaLayer.ofHeight(player.getBlockY());
        int cap = maxSpawns(level, layer);
        int nearby = nearbyEnemies(player);
        if (nearby >= cap) {
            return;
        }
        RandomSource random = level.getRandom();
        float chance = spawnChance(level, layer) * (1.0F - 0.5F * nearby / (float) Math.max(cap, 1));
        if (random.nextFloat() >= chance) {
            return;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            if (trySpawn(level, player, random)) {
                return;
            }
        }
    }

    /** Spawns one group near the player if a valid position and rule are found. Also used by debug commands. */
    public static boolean trySpawn(ServerLevel level, ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        BlockPos ground = findGround(level, x, player.getBlockY(), z, random);
        if (ground == null || level.getNearestPlayer(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, MIN_DISTANCE - 4, false) != null) {
            return false;
        }
        SpawnRule.SpawnContext context = new SpawnRule.SpawnContext(level.dimension(), level.getBiome(ground),
            TerrariaLayer.ofHeight(ground.getY()), level.isBrightOutside(), level.canSeeSky(ground), level.getBlockState(ground.below()),
            com.terracraft.world.event.EventState.get(level.getServer()).active());
        WorldProgression progression = WorldProgression.get(level.getServer());
        List<SpawnRule> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (SpawnRule rule : rules) {
            if (rule.weight() > 0 && rule.matches(context, progression)) {
                eligible.add(rule);
                totalWeight += rule.weight();
            }
        }
        if (totalWeight <= 0) {
            return false;
        }
        int pick = random.nextInt(totalWeight);
        for (SpawnRule rule : eligible) {
            pick -= rule.weight();
            if (pick < 0) {
                return spawnGroup(level, rule, ground, random) > 0;
            }
        }
        return false;
    }

    public static int spawnGroup(ServerLevel level, SpawnRule rule, BlockPos ground, RandomSource random) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(rule.entity());
        if (type == null) {
            return 0;
        }
        int count = rule.minGroup() + random.nextInt(rule.maxGroup() - rule.minGroup() + 1);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = ground.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            if (rule.placement() == SpawnRule.Placement.AIR) {
                pos = pos.above(3 + random.nextInt(4));
            }
            Entity entity = type.create(level, EntitySpawnReason.NATURAL);
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox())) {
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            level.addFreshEntity(mob);
            spawned++;
        }
        return spawned;
    }

    /** Finds a standable position in the column near the given height (top-down on the surface). */
    private static BlockPos findGround(ServerLevel level, int x, int nearY, int z, RandomSource random) {
        if (!level.hasChunkAt(new BlockPos(x, nearY, z))) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        int top = Math.min(level.getMaxY() - 3, nearY + 16);
        int bottom = Math.max(level.getMinY() + 1, nearY - 16);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            pos.set(x, y - 1, z);
            BlockState below = level.getBlockState(pos);
            if (!below.isFaceSturdy(level, pos, Direction.UP)) {
                continue;
            }
            BlockPos feet = new BlockPos(x, y, z);
            AABB space = new AABB(x + 0.2, y, z + 0.2, x + 0.8, y + 1.9, z + 0.8);
            if (level.noCollision(space) && !level.containsAnyLiquid(space)) {
                candidates.add(feet);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }
}
