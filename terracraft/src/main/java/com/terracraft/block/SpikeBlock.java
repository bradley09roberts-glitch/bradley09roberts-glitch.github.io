package com.terracraft.block;

import com.mojang.serialization.MapCodec;
import com.terracraft.combat.TerraDamageTypes;
import com.terracraft.combat.TerraHit;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Terraria Spikes: a low bed of spikes that hurts anything walking over or falling onto it (20 damage, plus
 * Expert scaling). Enemies are not hurt, like in Terraria.
 */
public class SpikeBlock extends Block {
    public static final MapCodec<SpikeBlock> CODEC = simpleCodec(SpikeBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    private final float damage;

    public SpikeBlock(Properties properties) {
        this(properties, 20.0F);
    }

    /** Wooden Spikes in the Lihzahrd Temple hit much harder. */
    public SpikeBlock(Properties properties, float damage) {
        super(properties);
        this.damage = damage;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living && !(entity instanceof TerrariaMob)
            && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            entity.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(0.6, 1.0, 0.6));
            living.hurtServer(server, TerraDamageTypes.source(level, TerraDamageTypes.ENEMY, null, null, TerraHit.enemy(0.0F)),
                damage * TerrariaDifficulty.enemyDamageMultiplier(level));
        }
    }
}
