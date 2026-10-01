package com.starforged.sun.world;

import com.starforged.sun.SunBlocks;
import com.starforged.sun.SunSounds;
import com.starforged.sun.block.SolarBrazierBlock;
import com.starforged.sun.block.SunSealBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Hall of Braziers: when the last brazier near a Sun Seal is lit, every seal in the area burns away.
 */
public final class SunTemplePuzzle {
    private static final int BRAZIER_RADIUS = 20;
    private static final int SEAL_RADIUS = 28;

    private SunTemplePuzzle() {
    }

    public static void onBrazierLit(ServerLevel level, BlockPos lit, Player player) {
        int total = 0;
        int burning = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -BRAZIER_RADIUS; dx <= BRAZIER_RADIUS; dx++) {
                for (int dz = -BRAZIER_RADIUS; dz <= BRAZIER_RADIUS; dz++) {
                    m.set(lit.getX() + dx, lit.getY() + dy, lit.getZ() + dz);
                    BlockState state = level.getBlockState(m);
                    if (state.is(SunBlocks.SOLAR_BRAZIER.get())) {
                        total++;
                        if (state.getValue(SolarBrazierBlock.LIT)) {
                            burning++;
                        }
                    }
                }
            }
        }
        List<BlockPos> seals = new ArrayList<>();
        for (int dy = -8; dy <= 8; dy++) {
            for (int dx = -SEAL_RADIUS; dx <= SEAL_RADIUS; dx++) {
                for (int dz = -SEAL_RADIUS; dz <= SEAL_RADIUS; dz++) {
                    m.set(lit.getX() + dx, lit.getY() + dy, lit.getZ() + dz);
                    if (level.getBlockState(m).is(SunBlocks.SUN_SEAL.get())) {
                        seals.add(m.immutable());
                    }
                }
            }
        }
        if (seals.isEmpty() || total < 2) {
            return;
        }
        if (burning < total) {
            player.sendOverlayMessage(Component.translatable("block.starforged.solar_brazier.progress", burning, total).withStyle(ChatFormatting.GOLD));
            return;
        }
        player.sendOverlayMessage(Component.translatable("block.starforged.solar_brazier.done").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        level.playSound(null, lit, SunSounds.SUN_SEAL_OPEN.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
        for (BlockPos seal : seals) {
            SunSealBlock.open(level, seal, 10 + (int) Math.sqrt(seal.distSqr(lit)));
        }
    }
}
