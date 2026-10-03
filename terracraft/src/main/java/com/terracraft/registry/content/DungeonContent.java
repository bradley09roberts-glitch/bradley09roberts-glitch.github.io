package com.terracraft.registry.content;

import com.terracraft.block.DungeonBookshelfBlock;
import com.terracraft.block.LockedChestBlock;
import com.terracraft.block.SpikeBlock;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.dungeon.DungeonLayout;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import com.terracraft.registry.RegistryObject;

/** Blocks and keys of the Dungeon (worldgen: {@code world.dungeon.DungeonFeature}). */
public final class DungeonContent {
    public static final RegistryObject<Block> BLUE_BRICK = brick("blue_brick", MapColor.COLOR_BLUE);
    public static final RegistryObject<Block> GREEN_BRICK = brick("green_brick", MapColor.COLOR_GREEN);
    public static final RegistryObject<Block> PINK_BRICK = brick("pink_brick", MapColor.COLOR_PINK);
    public static final RegistryObject<SpikeBlock> SPIKES = ModBlocks.register("spikes", TabGroup.BLOCKS, SpikeBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 1200.0F).sound(SoundType.METAL).noOcclusion()
            .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<LockedChestBlock> LOCKED_GOLD_CHEST = ModBlocks.register("locked_gold_chest", TabGroup.BLOCKS,
        p -> new LockedChestBlock(p, "golden_key", true, "chests/dungeon_gold"),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion()
            .pushReaction(PushReaction.BLOCK));

    /** Bookcase full of books; some hold a Water Bolt (see {@link DungeonBookshelfBlock}). */
    public static final RegistryObject<DungeonBookshelfBlock> DUNGEON_BOOKSHELF = ModBlocks.register("dungeon_bookshelf", TabGroup.BLOCKS,
        DungeonBookshelfBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).ignitedByLava());

    /** Opens one Locked Gold Chest (consumed). */
    public static final RegistryObject<TerraItem> GOLDEN_KEY = CoreItems.material("golden_key", TerraRarity.WHITE, 0);
    /** Opens Shadow Chests in the Underworld (not consumed). */
    public static final RegistryObject<TerraItem> SHADOW_KEY = ModItems.register("shadow_key", TabGroup.MATERIALS, TerraItem::new,
        p -> WeaponProperties.stats(p.stacksTo(1), CoreItems.stats(TerraRarity.BLUE, 0)));

    private DungeonContent() {}

    public static void init() {}

    public static BlockState brick(DungeonLayout.Brick brick) {
        return (switch (brick) {
            case BLUE -> BLUE_BRICK;
            case GREEN -> GREEN_BRICK;
            case PINK -> PINK_BRICK;
        }).get().defaultBlockState();
    }

    /** Dungeon bricks: Terraria needs 65% pickaxe power (data: mining_power/dungeon_brick) and they shrug off explosions. */
    private static RegistryObject<Block> brick(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(3.0F, 1200.0F).sound(SoundType.STONE));
    }
}
