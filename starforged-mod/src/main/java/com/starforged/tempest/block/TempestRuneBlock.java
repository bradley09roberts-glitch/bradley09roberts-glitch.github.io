package com.starforged.tempest.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Citadel floor runes (trap tiles). Step on one without sneaking:
 * <ul>
 *     <li><b>Thunder Rune</b> - lightning strikes the spot.</li>
 *     <li><b>Gale Rune</b> - a blast of wind hurls you up and back.</li>
 * </ul>
 * A rune goes dark for three seconds after it fires.
 */
public class TempestRuneBlock extends Block {
    public static final MapCodec<TempestRuneBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(Codec.BOOL.fieldOf("thunder").forGetter(b -> b.thunder), propertiesCodec()).apply(i, TempestRuneBlock::new));
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    private final boolean thunder;

    public TempestRuneBlock(boolean thunder, BlockBehaviour.Properties properties) {
        super(properties);
        this.thunder = thunder;
        this.registerDefaultState(this.stateDefinition.any().setValue(ARMED, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && state.getValue(ARMED) && entity instanceof LivingEntity living && !living.isSteppingCarefully()
            && !living.is(ModTags.STORMBORN) && !(living instanceof net.minecraft.world.entity.player.Player p && p.isCreative())) {
            server.setBlock(pos, state.setValue(ARMED, false), Block.UPDATE_ALL);
            server.scheduleTick(pos, this, 60);
            if (this.thunder) {
                StormNetwork.visualBolt(server, pos.above());
                living.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.STORM, null), 8.0F);
                living.igniteForSeconds(2);
            } else {
                Vec3 back = living.getDeltaMovement().multiply(-1, 0, -1).normalize().scale(1.2);
                living.setDeltaMovement(back.x, 1.1, back.z);
                living.hurtMarked = true;
                server.sendParticles(ModParticles.STORM_WISP.get(), living.getX(), living.getY() + 0.3, living.getZ(), 30, 0.5, 0.2, 0.5, 0.3);
                server.playSound(null, pos, TempestSounds.GUST.get(), SoundSource.BLOCKS, 1.2F, 0.8F);
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, state.setValue(ARMED, true), Block.UPDATE_ALL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ARMED) && random.nextInt(4) == 0) {
            level.addParticle(this.thunder ? ModParticles.STATIC_SPARK.get() : ModParticles.STORM_WISP.get(),
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03, 0.0);
        }
    }
}
