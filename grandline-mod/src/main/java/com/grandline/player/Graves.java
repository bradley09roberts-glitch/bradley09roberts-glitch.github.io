package com.grandline.player;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A fallen character's belongings stay in a grave chest where they died (agreed permadeath rule). */
public final class Graves {
    @Nullable
    public static BlockPos findGraveSpot(ServerLevel level, BlockPos death) {
        BlockPos p = death;
        if (p.getY() <= level.getMinBuildHeight() + 1) {
            p = new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()), p.getZ());
        }
        // float to the surface if submerged; graves at sea become driftwood chests on the water
        for (int i = 0; i < 64 && (level.getBlockState(p).liquid() || !level.getBlockState(p).canBeReplaced()); i++) p = p.above();
        if (!level.getBlockState(p).canBeReplaced()) return null;
        return p;
    }

    public static int placeGrave(ServerLevel level, BlockPos pos, List<ItemStack> items, String name) {
        int placed = 0;
        BlockPos[] spots = {pos, pos.east()};
        int idx = 0;
        for (BlockPos spot : spots) {
            if (idx >= items.size()) break;
            if (!level.getBlockState(spot).canBeReplaced()) continue;
            if (level.getBlockState(spot.below()).liquid()) level.setBlock(spot.below(), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            level.setBlock(spot, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.SINGLE), 3);
            if (level.getBlockEntity(spot) instanceof ChestBlockEntity chest) {
                chest.applyComponents(net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("grandline.grave.name", name)).build(), net.minecraft.core.component.DataComponentPatch.EMPTY);
                for (int slot = 0; slot < chest.getContainerSize() && idx < items.size(); slot++) {
                    chest.setItem(slot, items.get(idx++));
                    placed++;
                }
            }
        }
        return placed;
    }

    private Graves() {}
}
