package com.terracraft.registry.content;

import com.terracraft.block.CraftingStationBlock;
import com.terracraft.block.LockedChestBlock;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

/** Blocks and materials of the Underworld (worldgen: {@code world.underworld.UnderworldFeature}). */
public final class UnderworldContent {
    public static final RegistryObject<Block> ASH = ModBlocks.register("ash", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.8F).sound(SoundType.SAND));
    /** Hellstone: 65% pickaxe power (data: mining_power/hellstone); glows. */
    public static final RegistryObject<Block> HELLSTONE = ModBlocks.register("hellstone", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).requiresCorrectToolForDrops().strength(4.0F, 9.0F).sound(SoundType.NETHERRACK)
            .lightLevel(s -> 7));
    public static final RegistryObject<Block> OBSIDIAN_BRICK = ModBlocks.register("obsidian_brick", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(10.0F, 1200.0F).sound(SoundType.STONE));
    public static final RegistryObject<Block> HELLSTONE_BRICK = ModBlocks.register("hellstone_brick", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).requiresCorrectToolForDrops().strength(4.0F, 9.0F).sound(SoundType.NETHER_BRICKS)
            .lightLevel(s -> 4));
    public static final RegistryObject<CraftingStationBlock> HELLFORGE = ModBlocks.register("hellforge", TabGroup.BLOCKS,
        p -> new CraftingStationBlock(p, Block.box(0, 0, 1, 16, 14, 15)),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F).sound(SoundType.STONE)
            .lightLevel(s -> 13).noOcclusion());
    /** Shadow Chest: opened (not consumed) with the Shadow Key from the Dungeon. */
    public static final RegistryObject<LockedChestBlock> LOCKED_SHADOW_CHEST = ModBlocks.register("locked_shadow_chest", TabGroup.BLOCKS,
        p -> new LockedChestBlock(p, "shadow_key", false, "chests/shadow"),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion()
            .pushReaction(PushReaction.BLOCK));

    public static final RegistryObject<TerraItem> HELLSTONE_BAR = CoreItems.material("hellstone_bar", TerraRarity.ORANGE, 4000);
    /** Throw it into Underworld lava while the Guide lives to summon the Wall of Flesh. Floats in lava. */
    public static final RegistryObject<TerraItem> GUIDE_VOODOO_DOLL = ModItems.register("guide_voodoo_doll", TabGroup.CONSUMABLES, TerraItem::new,
        p -> WeaponProperties.stats(p.stacksTo(1).fireResistant(), CoreItems.stats(TerraRarity.ORANGE, 0)));

    private UnderworldContent() {}

    public static void init() {}
}
