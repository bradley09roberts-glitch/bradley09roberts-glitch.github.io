package com.terracraft.npc;

import com.terracraft.TerraCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Terraria housing rules translated to 3D: starting from an air block, the room is flood-filled through
 * non-solid blocks. A valid house
 * <ul>
 *     <li>is enclosed (the fill stops before {@value #MAX_VOLUME} blocks),</li>
 *     <li>is big enough ({@value #MIN_VOLUME}+ air blocks),</li>
 *     <li>has a door ({@code #terracraft:housing/doors}) in its walls,</li>
 *     <li>has a light source (any light-emitting block inside or in the walls),</li>
 *     <li>has a comfort item ({@code #terracraft:housing/comfort}: beds, chairs/stairs) and a flat surface
 *     ({@code #terracraft:housing/tables}: tables, work benches, crafting tables).</li>
 * </ul>
 * The result's {@code anchor} (lowest interior floor block) identifies the house.
 */
public final class HousingChecker {
    public static final int MIN_VOLUME = 30;
    public static final int MAX_VOLUME = 1200;
    public static final TagKey<Block> DOORS = TagKey.create(Registries.BLOCK, TerraCraft.id("housing/doors"));
    public static final TagKey<Block> COMFORT = TagKey.create(Registries.BLOCK, TerraCraft.id("housing/comfort"));
    public static final TagKey<Block> TABLES = TagKey.create(Registries.BLOCK, TerraCraft.id("housing/tables"));
    public static final TagKey<Block> LIGHTS = TagKey.create(Registries.BLOCK, TerraCraft.id("housing/lights"));

    public enum Problem {
        NONE, NOT_ENCLOSED, TOO_SMALL, NO_DOOR, NO_LIGHT, NO_COMFORT, NO_TABLE, NOT_AIR
    }

    public record Result(Problem problem, @Nullable BlockPos anchor, int volume) {
        public boolean valid() {
            return problem == Problem.NONE;
        }

        public String translationKey() {
            return "message.terracraft.housing." + problem.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private HousingChecker() {}

    /** Checks the room containing {@code start} (or the block above it if {@code start} is solid). */
    public static Result check(ServerLevel level, BlockPos start) {
        if (!isOpen(level, start)) {
            start = start.above();
            if (!isOpen(level, start)) {
                return new Result(Problem.NOT_AIR, null, 0);
            }
        }
        if (level.canSeeSky(start)) {
            return new Result(Problem.NOT_ENCLOSED, null, 0);
        }
        Set<BlockPos> interior = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        interior.add(start.immutable());
        boolean door = false;
        boolean light = false;
        boolean comfort = false;
        boolean table = false;
        BlockPos anchor = null;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState here = level.getBlockState(pos);
            light |= isLight(here);
            comfort |= here.is(COMFORT);
            table |= here.is(TABLES);
            if (!isOpen(level, pos.below()) && (anchor == null || pos.getY() < anchor.getY()
                || pos.getY() == anchor.getY() && (pos.getX() < anchor.getX() || pos.getX() == anchor.getX() && pos.getZ() < anchor.getZ()))) {
                anchor = pos;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (interior.contains(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(DOORS)) {
                    door = true;
                    continue;
                }
                if (isOpen(level, next)) {
                    if (!level.isLoaded(next) || interior.size() >= MAX_VOLUME || level.canSeeSky(next)) {
                        return new Result(Problem.NOT_ENCLOSED, null, interior.size());
                    }
                    interior.add(next);
                    queue.add(next);
                } else {
                    // wall, floor, ceiling or furniture block
                    light |= isLight(state);
                    comfort |= state.is(COMFORT);
                    table |= state.is(TABLES);
                }
            }
        }
        Problem problem = interior.size() < MIN_VOLUME ? Problem.TOO_SMALL
            : !door ? Problem.NO_DOOR
            : !light ? Problem.NO_LIGHT
            : !comfort ? Problem.NO_COMFORT
            : !table ? Problem.NO_TABLE
            : Problem.NONE;
        return new Result(problem, anchor, interior.size());
    }

    /** Air-like: no collision (furniture such as beds, slabs and stairs counts as part of the walls/floor). */
    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.is(DOORS) && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isLight(BlockState state) {
        return state.getLightEmission() > 0 || state.is(LIGHTS);
    }
}
