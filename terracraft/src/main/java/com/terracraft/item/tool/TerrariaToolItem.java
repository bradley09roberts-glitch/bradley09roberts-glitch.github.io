package com.terracraft.item.tool;

import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.weapon.WeaponProperties;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Tool;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Terraria pickaxes, axes, hammers and combinations. Tools never break (Terraria has no durability).
 * Mining speed is a vanilla {@link Tool} component; what a tool may break is decided by its pickaxe /
 * axe / hammer power ({@link com.terracraft.mining.MiningPower}).
 */
public class TerrariaToolItem extends TerraItem {
    public TerrariaToolItem(Properties properties) {
        super(properties);
    }

    /** Builds tool properties: melee stats + a Tool component for every power the tool has. */
    public static Item.Properties properties(Item.Properties properties, TerraItemStats stats, float miningSpeed) {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        List<Tool.Rule> rules = new ArrayList<>();
        if (stats.pickPower() > 0) {
            rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), miningSpeed));
        }
        if (stats.axePower() > 0) {
            rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), miningSpeed));
        }
        if (stats.hammerPower() > 0) {
            rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(com.terracraft.mining.MiningTags.MINEABLE_WITH_HAMMER), miningSpeed));
        }
        return WeaponProperties.melee(properties, stats).component(DataComponents.TOOL, new Tool(rules, 1.0F, 0, true));
    }
}
