package com.terracraft.item.tool;

import com.terracraft.item.TerraItem;
import com.terracraft.npc.PurificationPowderItem;
import com.terracraft.world.hardmode.HardmodeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Steampunker's Clentaminator: sprays the first solution found in the inventory along the line of sight,
 * converting blocks up to 16 blocks away (Green purifies, Blue spreads the Hallow, Purple the Corruption, Red the
 * Crimson). Each spray uses one solution.
 */
public class ClentaminatorItem extends TerraItem {
    public ClentaminatorItem(Properties properties) {
        super(properties);
    }

    /** A solution item and what it does. */
    public static class SolutionItem extends TerraItem {
        public enum Kind { GREEN, BLUE, PURPLE, RED }

        private final Kind kind;
        private final int color;

        public SolutionItem(Properties properties, Kind kind, int color) {
            super(properties);
            this.kind = kind;
            this.color = color;
        }

        public Kind kind() {
            return kind;
        }

        public int color() {
            return color;
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack solution = findSolution(player);
        if (solution.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        SolutionItem item = (SolutionItem) solution.getItem();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        DustParticleOptions dust = new DustParticleOptions(item.color(), 1.4F);
        for (int step = 2; step <= 16; step++) {
            Vec3 point = eye.add(look.scale(step));
            server.sendParticles(dust, point.x, point.y, point.z, 4, 0.3, 0.3, 0.3, 0.0);
            BlockPos center = BlockPos.containing(point);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                BlockState state = server.getBlockState(pos);
                BlockState result = convert(state, item.kind());
                if (result != null && result != state) {
                    server.setBlock(pos, result, Block.UPDATE_ALL);
                }
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
        if (!player.getAbilities().instabuild) {
            solution.shrink(1);
        }
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 5);
        return InteractionResult.SUCCESS;
    }

    static BlockState convert(BlockState state, SolutionItem.Kind kind) {
        BlockState pure = PurificationPowderItem.purify(state);
        if (kind == SolutionItem.Kind.GREEN) {
            return pure;
        }
        BlockState base = pure != null ? pure : state;
        HardmodeWorld.Infection infection = kind == SolutionItem.Kind.BLUE ? HardmodeWorld.Infection.HALLOW : HardmodeWorld.Infection.EVIL;
        BlockState converted = HardmodeWorld.converted(base, infection, kind == SolutionItem.Kind.RED);
        return converted != null ? converted : pure;
    }

    private static ItemStack findSolution(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SolutionItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
