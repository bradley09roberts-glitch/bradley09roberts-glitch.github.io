package com.longnight.world;

import com.longnight.sim.Commodity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered construction plan in local coordinates (x across the front, z towards the back,
 * y = 0 is floor/ground level). Steps are placed strictly in order, bottom up.
 */
public class Blueprint {
    public record Step(int x, int y, int z, BlockState state) {}

    public final int width;
    public final int depth;
    public final List<Step> steps = new ArrayList<>();
    private List<Map<Commodity, Double>> stepCosts;
    private Map<Commodity, Double> total;
    /** local position of special spots (work spot, bed, sign) for AI */
    public final Map<String, BlockPos> marks = new java.util.HashMap<>();
    /** head halves of the beds, found once (citizens look for their bed every tick at night) */
    List<Step> bedHeads;
    public int height;

    public Blueprint(int width, int depth) {
        this.width = width;
        this.depth = depth;
    }

    public Map<Commodity, Double> stepCost(int i) {
        ensureCosts();
        return stepCosts.get(i);
    }

    public Map<Commodity, Double> totalCost() {
        ensureCosts();
        return total;
    }

    private void ensureCosts() {
        if (stepCosts != null) return;
        stepCosts = new ArrayList<>(steps.size());
        total = new EnumMap<>(Commodity.class);
        for (Step s : steps) {
            Map<Commodity, Double> c = Materials.cost(s.state());
            stepCosts.add(c);
            c.forEach((k, v) -> total.merge(k, v, Double::sum));
        }
    }

    /** World position of a local coordinate for a building with given origin and rotation. */
    public static BlockPos toWorld(BlockPos origin, int rotation, int width, int depth, int x, int y, int z) {
        int wx, wz;
        switch (rotation & 3) {
            case 1 -> {
                wx = depth - 1 - z;
                wz = x;
            }
            case 2 -> {
                wx = width - 1 - x;
                wz = depth - 1 - z;
            }
            case 3 -> {
                wx = z;
                wz = width - 1 - x;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        return origin.offset(wx, y, wz);
    }

    public static Rotation rotation(int r) {
        return switch (r & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
