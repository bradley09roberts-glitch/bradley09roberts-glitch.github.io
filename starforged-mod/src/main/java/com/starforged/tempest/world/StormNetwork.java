package com.starforged.tempest.world;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.block.StormNode;
import com.starforged.util.Fx;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Storm networks: a Storm Dynamo fires a pulse of lightning out of its face. The pulse arcs through open air (up to
 * {@link #MAX_GAP} blocks) until it meets a {@link StormNode} - a conductor, relay, splitter, capacitor, core, lift or
 * overload relay - which decides where it goes next. Solid blocks stop it.
 */
public final class StormNetwork {
    /** How far a pulse can arc through the air between two nodes. */
    public static final int MAX_GAP = 8;
    private static final int MAX_SEGMENTS = 96;
    /** Tag put on lightning the network itself calls down, so it doesn't trigger the dynamo again. */
    public static final String OWN_BOLT = "starforged_storm_network";

    private StormNetwork() {
    }

    private record Beam(BlockPos from, Direction dir) {
    }

    public static void pulse(ServerLevel level, BlockPos source, Direction out) {
        ArrayDeque<Beam> queue = new ArrayDeque<>();
        Set<Beam> seen = new HashSet<>();
        queue.add(new Beam(source, out));
        int segments = 0;
        while (!queue.isEmpty() && segments++ < MAX_SEGMENTS) {
            Beam beam = queue.poll();
            if (!seen.add(beam)) {
                continue;
            }
            BlockPos.MutableBlockPos p = beam.from.mutable();
            for (int step = 1; step <= MAX_GAP; step++) {
                p.move(beam.dir);
                BlockState state = level.getBlockState(p);
                if (state.getBlock() instanceof StormNode node) {
                    BlockPos hit = p.immutable();
                    arc(level, beam.from, hit);
                    for (Direction next : node.receive(level, hit, state, beam.dir)) {
                        queue.add(new Beam(hit, next));
                    }
                    break;
                }
                if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty()) {
                    // Fizzles against the wall.
                    Vec3 end = Vec3.atCenterOf(p).subtract(beam.dir.getStepX() * 0.5, beam.dir.getStepY() * 0.5, beam.dir.getStepZ() * 0.5);
                    arcTo(level, Vec3.atCenterOf(beam.from), end);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
                    break;
                }
                if (step == MAX_GAP) {
                    arcTo(level, Vec3.atCenterOf(beam.from), Vec3.atCenterOf(p));
                }
            }
        }
    }

    private static void arc(ServerLevel level, BlockPos from, BlockPos to) {
        arcTo(level, Vec3.atCenterOf(from), Vec3.atCenterOf(to));
        level.playSound(null, to, TempestSounds.NETWORK_PULSE.get(), SoundSource.BLOCKS, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.3F);
    }

    /** A jagged line of sparks: the visible arc. */
    public static void arcTo(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        int points = Math.max(2, (int) (length * 4));
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            double jitter = i == 0 || i == points ? 0.0 : 0.12;
            Vec3 at = from.add(delta.scale(t)).add(level.getRandom().nextGaussian() * jitter, level.getRandom().nextGaussian() * jitter,
                level.getRandom().nextGaussian() * jitter);
            level.sendParticles(ModParticles.STATIC_SPARK.get(), true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Calls a harmless bolt down onto {@code pos} (for show; tagged so dynamos ignore it). */
    public static void visualBolt(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(true);
            bolt.addTag(OWN_BOLT);
            level.addFreshEntity(bolt);
        }
    }

    /** Lists of directions are used a lot by nodes. */
    public static List<Direction> horizontalSides(Direction travel) {
        if (travel.getAxis().isVertical()) {
            return List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
        }
        return List.of(travel.getClockWise(), travel.getCounterClockWise());
    }

    /** Sparks around a block that just took a pulse. */
    public static void flash(ServerLevel level, BlockPos pos) {
        Fx.burst(level, ModParticles.STATIC_SPARK.get(), Vec3.atCenterOf(pos), 8, 0.35, 0.05);
    }
}
