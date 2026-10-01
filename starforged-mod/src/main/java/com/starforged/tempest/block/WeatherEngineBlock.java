package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.tempest.world.StormreachTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Weather Engine: feed it a Charged Aetherium Ingot to turn the weather one step: clear, then rain, then a thunderstorm,
 * then clear again. Stormreach's storm is beyond its reach.
 */
public class WeatherEngineBlock extends Block {
    public static final MapCodec<WeatherEngineBlock> CODEC = simpleCodec(WeatherEngineBlock::new);

    public WeatherEngineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(TempestItems.CHARGED_AETHERIUM_INGOT.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            if (StormreachTravel.isStormreach(server)) {
                player.sendOverlayMessage(Component.translatable("block.starforged.weather_engine.stormreach").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            String result;
            if (server.isThundering()) {
                server.getServer().setWeatherParameters(24000, 0, false, false);
                result = "clear";
            } else if (server.isRaining()) {
                server.getServer().setWeatherParameters(0, 12000, true, true);
                result = "thunder";
            } else {
                server.getServer().setWeatherParameters(0, 12000, true, false);
                result = "rain";
            }
            stack.consume(1, player);
            StormNetwork.visualBolt(server, pos.above());
            StormNetwork.arcTo(server, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos).add(0, 12, 0));
            server.playSound(null, pos, TempestSounds.SUPERCELL.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
            player.sendOverlayMessage(Component.translatable("block.starforged.weather_engine." + result).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.weather_engine.hint").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        StormBlocksFx.sparks(level, pos, random, 1);
    }
}
