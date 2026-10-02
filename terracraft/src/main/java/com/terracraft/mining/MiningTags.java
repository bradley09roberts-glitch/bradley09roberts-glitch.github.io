package com.terracraft.mining;

import com.terracraft.TerraCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags used by the mining system. */
public final class MiningTags {
    /** Blocks Terraria hammers break efficiently (altars, hammer-only furniture...). */
    public static final TagKey<Block> MINEABLE_WITH_HAMMER = TagKey.create(Registries.BLOCK, TerraCraft.id("mineable/hammer"));

    private MiningTags() {}
}
