package com.longnight.registry;

import com.longnight.LongNight;
import com.longnight.block.BoundaryStoneBlock;
import com.longnight.block.GravestoneBlock;
import com.longnight.block.HearthstoneBlock;
import com.longnight.block.NoticeBoardBlock;
import com.longnight.block.StorehouseBlock;
import com.longnight.block.WardStoneBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LongNight.MODID);

    public static final DeferredBlock<HearthstoneBlock> HEARTHSTONE = BLOCKS.register("hearthstone",
            () -> new HearthstoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4f, 1200f)
                    .requiresCorrectToolForDrops().lightLevel(s -> 10).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<StorehouseBlock> STOREHOUSE = BLOCKS.register("storehouse",
            () -> new StorehouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3f, 600f).sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK), false));
    public static final DeferredBlock<StorehouseBlock> MARKET_STALL = BLOCKS.register("market_stall",
            () -> new StorehouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 600f).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.BLOCK), true));
    public static final DeferredBlock<GravestoneBlock> GRAVESTONE = BLOCKS.register("gravestone",
            () -> new GravestoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f, 1200f).noOcclusion()
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final DeferredBlock<BoundaryStoneBlock> BOUNDARY_STONE = BLOCKS.register("boundary_stone",
            () -> new BoundaryStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3f, 1200f)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final DeferredBlock<NoticeBoardBlock> NOTICE_BOARD = BLOCKS.register("notice_board",
            () -> new NoticeBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 600f).noOcclusion()
                    .sound(SoundType.WOOD)));
    public static final DeferredBlock<WardStoneBlock> WARD_STONE = BLOCKS.register("ward_stone",
            () -> new WardStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(5f, 1200f)
                    .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)
                    .lightLevel(s -> s.getValue(WardStoneBlock.ACTIVE) ? 9 : 0)));

    public static void registerItems() {
        for (DeferredBlock<? extends Block> b : new DeferredBlock[]{HEARTHSTONE, STOREHOUSE, MARKET_STALL, GRAVESTONE, BOUNDARY_STONE,
                NOTICE_BOARD, WARD_STONE}) {
            ModItems.ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
    }
}
