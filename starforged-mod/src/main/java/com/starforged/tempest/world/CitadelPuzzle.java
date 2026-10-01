package com.starforged.tempest.world;

import com.starforged.sun.SunFx;
import com.starforged.tempest.TempestBlocks;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.block.CitadelCoreBlock;
import com.starforged.tempest.block.TempestSealBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Tempest Citadel's lock: Tempest Seals guard the spire, and they break only when every Citadel Core near them is
 * powered by a storm circuit. An Overload Relay knocks the cores of its wing back offline.
 */
public final class CitadelPuzzle {
    private static final int RANGE = 36;
    private static final int HEIGHT = 20;

    private CitadelPuzzle() {
    }

    public static void onCoreLit(ServerLevel level, BlockPos core) {
        List<BlockPos> seals = new ArrayList<>();
        int total = 0;
        int lit = 0;
        for (BlockPos p : BlockPos.betweenClosed(core.offset(-RANGE, -HEIGHT, -RANGE), core.offset(RANGE, HEIGHT, RANGE))) {
            BlockState state = level.getBlockState(p);
            if (state.is(TempestBlocks.CITADEL_CORE.get())) {
                total++;
                if (state.getValue(CitadelCoreBlock.LIT)) {
                    lit++;
                }
            } else if (state.is(TempestBlocks.TEMPEST_SEAL.get())) {
                seals.add(p.immutable());
            }
        }
        if (seals.isEmpty()) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(core);
        if (lit < total) {
            SunFx.messageNear(level, at, 64.0, Component.translatable("block.starforged.citadel_core.progress", lit, total)
                .withStyle(ChatFormatting.AQUA));
            return;
        }
        SunFx.messageNear(level, at, 96.0, Component.translatable("block.starforged.citadel_core.all").withStyle(ChatFormatting.AQUA,
            ChatFormatting.BOLD));
        level.playSound(null, core, TempestSounds.SEAL_OPEN.get(), SoundSource.BLOCKS, 3.0F, 0.8F);
        for (BlockPos seal : seals) {
            TempestSealBlock.open(level, seal, 10 + level.getRandom().nextInt(10));
        }
    }

    /** Knocks every Citadel Core within {@code radius} back offline. */
    public static void resetCores(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -8, -radius), center.offset(radius, 8, radius))) {
            BlockState state = level.getBlockState(p);
            if (state.is(TempestBlocks.CITADEL_CORE.get()) && state.getValue(CitadelCoreBlock.LIT)) {
                level.setBlock(p, state.setValue(CitadelCoreBlock.LIT, false), Block.UPDATE_ALL);
            }
        }
    }
}
