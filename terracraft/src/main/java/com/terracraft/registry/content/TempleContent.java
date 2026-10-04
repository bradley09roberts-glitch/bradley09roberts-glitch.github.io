package com.terracraft.registry.content;

import com.terracraft.block.DartTrapBlock;
import com.terracraft.block.LihzahrdAltarBlock;
import com.terracraft.block.LockedTempleDoorBlock;
import com.terracraft.block.SpikeBlock;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Lihzahrd Temple (built by {@code world.temple.TempleWorld}): its brick (pickaxe power 210, so only the Picksaw
 * Golem drops can mine it), the locked door opened by Plantera's Temple Key, dart traps, wooden spikes, the altar
 * and the Power Cell that summons Golem.
 */
public final class TempleContent {
    public static final RegistryObject<Block> LIHZAHRD_BRICK = ModBlocks.register("lihzahrd_brick", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).requiresCorrectToolForDrops().strength(5.0F, 1200.0F)
            .sound(SoundType.DEEPSLATE_BRICKS));
    public static final RegistryObject<LockedTempleDoorBlock> LOCKED_LIHZAHRD_DOOR = ModBlocks.registerNoItem("locked_lihzahrd_door",
        LockedTempleDoorBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(-1.0F, 3600000.0F)
            .sound(SoundType.DEEPSLATE_BRICKS).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<DartTrapBlock> DART_TRAP = ModBlocks.register("dart_trap", TabGroup.BLOCKS, DartTrapBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).requiresCorrectToolForDrops().strength(5.0F, 1200.0F)
            .sound(SoundType.DEEPSLATE_BRICKS));
    public static final RegistryObject<SpikeBlock> WOODEN_SPIKES = ModBlocks.register("wooden_spikes", TabGroup.BLOCKS,
        p -> new SpikeBlock(p, 60.0F), () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(5.0F, 1200.0F).noOcclusion()
            .requiresCorrectToolForDrops().sound(SoundType.WOOD));
    public static final RegistryObject<LihzahrdAltarBlock> LIHZAHRD_ALTAR = ModBlocks.registerNoItem("lihzahrd_altar", LihzahrdAltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(-1.0F, 3600000.0F).noOcclusion()
            .lightLevel(s -> 9).sound(SoundType.DEEPSLATE_BRICKS).pushReaction(PushReaction.BLOCK));

    /** Lihzahrd Power Cell: found in temple chests; placed in the altar it awakens Golem. */
    public static final RegistryObject<TerraItem> POWER_CELL = ModItems.register("lihzahrd_power_cell", TabGroup.CONSUMABLES, TerraItem::new,
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.LIME, 0)));

    private TempleContent() {}

    public static void init() {}
}
