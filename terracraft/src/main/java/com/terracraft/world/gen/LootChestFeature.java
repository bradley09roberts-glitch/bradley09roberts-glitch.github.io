package com.terracraft.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places a loot chest on a cave floor (use with an {@code environment_scan} placement). Terraria's
 * underground wooden and gold chests use this with different loot tables.
 * <pre>{"type": "terracraft:loot_chest", "config": {"chest": {"Name": "minecraft:chest"}, "loot_table": "terracraft:chests/underground"}}</pre>
 */
public class LootChestFeature extends Feature<LootChestFeature.Config> {
    public record Config(BlockState chest, ResourceKey<LootTable> lootTable) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockState.CODEC.fieldOf("chest").forGetter(Config::chest),
            ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(Config::lootTable)
        ).apply(i, Config::new));
    }

    public LootChestFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos pos = context.origin();
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
            || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        BlockState state = context.config().chest();
        if (state.hasProperty(ChestBlock.FACING)) {
            state = state.setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(context.random()));
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        RandomizableContainer.setBlockEntityLootTable(level, context.random(), pos, context.config().lootTable());
        return true;
    }
}
