package com.terracraft.npc;

import com.terracraft.item.TerraItem;
import com.terracraft.registry.content.EvilContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Purification Powder: thrown in front of the player, turns Corruption/Crimson blocks back to their pure forms. */
public class PurificationPowderItem extends TerraItem {
    private static final int RADIUS = 3;

    public PurificationPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(3.0));
            BlockPos center = BlockPos.containing(at);
            int converted = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
                BlockState state = server.getBlockState(pos);
                BlockState pure = purify(state);
                if (pure != null) {
                    server.setBlock(pos, pure, Block.UPDATE_ALL);
                    converted++;
                }
            }
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 30, RADIUS * 0.5, RADIUS * 0.5, RADIUS * 0.5, 0.0);
            server.playSound(null, center, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
            if (!player.getAbilities().instabuild) {
                player.getItemInHand(hand).shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Pure counterpart of an evil block, or null if the block is not evil. */
    public static BlockState purify(BlockState state) {
        if (state.is(EvilContent.EBONSTONE.get()) || state.is(EvilContent.CRIMSTONE.get())) {
            return Blocks.STONE.defaultBlockState();
        }
        if (state.is(EvilContent.CORRUPT_GRASS.get()) || state.is(EvilContent.CRIMSON_GRASS.get())) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (state.is(EvilContent.EBONWOOD.get()) || state.is(EvilContent.SHADEWOOD.get())) {
            return Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        if (state.is(EvilContent.EBONWOOD_LEAVES.get()) || state.is(EvilContent.SHADEWOOD_LEAVES.get())) {
            return Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        }
        if (state.is(EvilContent.VILE_MUSHROOM.get()) || state.is(EvilContent.VICIOUS_MUSHROOM.get())) {
            return Blocks.AIR.defaultBlockState();
        }
        // the Hallow is cleansed too
        if (state.is(com.terracraft.registry.content.HardmodeContent.PEARLSTONE.get())) {
            return Blocks.STONE.defaultBlockState();
        }
        if (state.is(com.terracraft.registry.content.HardmodeContent.HALLOWED_GRASS.get())) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (state.is(com.terracraft.registry.content.HardmodeContent.PEARLSAND.get())) {
            return Blocks.SAND.defaultBlockState();
        }
        if (state.is(com.terracraft.registry.content.HardmodeContent.PEARLWOOD.get())) {
            return Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        if (state.is(com.terracraft.registry.content.HardmodeContent.HALLOWED_LEAVES.get())) {
            return Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        }
        return null;
    }
}
