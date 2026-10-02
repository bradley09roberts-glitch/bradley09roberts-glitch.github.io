package com.terracraft.crafting;

import com.terracraft.TerraCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Terraria crafting stations. A station is satisfied by any block in the block tag
 * {@code #terracraft:stations/<name>} within reach of the player (Terraria's crafting range), so new blocks
 * can count as stations purely through datapack tags (e.g. a Mythril Anvil is also an Anvil).
 * The special stations {@code water}, {@code lava} and {@code honey} are satisfied by nearby fluid.
 */
public final class CraftingStations {
    public static final Identifier WORK_BENCH = TerraCraft.id("work_bench");
    public static final Identifier FURNACE = TerraCraft.id("furnace");
    public static final Identifier ANVIL = TerraCraft.id("anvil");
    public static final Identifier SAWMILL = TerraCraft.id("sawmill");
    public static final Identifier LOOM = TerraCraft.id("loom");
    public static final Identifier ALCHEMY = TerraCraft.id("alchemy");
    public static final Identifier HELLFORGE = TerraCraft.id("hellforge");
    public static final Identifier DEMON_ALTAR = TerraCraft.id("demon_altar");
    public static final Identifier HARDMODE_ANVIL = TerraCraft.id("hardmode_anvil");
    public static final Identifier HARDMODE_FORGE = TerraCraft.id("hardmode_forge");
    public static final Identifier TINKERERS_WORKSHOP = TerraCraft.id("tinkerers_workshop");
    public static final Identifier WATER = TerraCraft.id("water");
    public static final Identifier LAVA = TerraCraft.id("lava");

    /** Horizontal / vertical crafting reach in blocks. */
    public static final int RANGE_HORIZONTAL = 4;
    public static final int RANGE_VERTICAL = 3;

    private CraftingStations() {}

    public static TagKey<Block> tag(Identifier station) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(station.getNamespace(), "stations/" + station.getPath()));
    }

    public static Component displayName(Identifier station) {
        return Component.translatable("station." + station.getNamespace() + "." + station.getPath());
    }

    /** Returns which of the wanted stations are within reach of {@code center}. */
    public static Set<Identifier> nearby(Level level, BlockPos center, Collection<Identifier> wanted) {
        Set<Identifier> found = new HashSet<>();
        if (wanted.isEmpty()) {
            return found;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RANGE_HORIZONTAL; dx <= RANGE_HORIZONTAL; dx++) {
            for (int dz = -RANGE_HORIZONTAL; dz <= RANGE_HORIZONTAL; dz++) {
                for (int dy = -RANGE_VERTICAL; dy <= RANGE_VERTICAL; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    for (Identifier station : wanted) {
                        if (!found.contains(station) && matches(state, station)) {
                            found.add(station);
                        }
                    }
                    if (found.size() == wanted.size()) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    private static boolean matches(BlockState state, Identifier station) {
        if (station.equals(WATER)) {
            return state.getFluidState().is(FluidTags.WATER);
        }
        if (station.equals(LAVA)) {
            return state.getFluidState().is(FluidTags.LAVA);
        }
        return state.is(tag(station));
    }
}
