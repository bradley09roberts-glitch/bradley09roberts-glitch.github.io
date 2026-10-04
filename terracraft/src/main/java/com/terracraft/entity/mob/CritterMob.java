package com.terracraft.entity.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * A harmless critter (Truffle Worm, Prismatic Lacewing): it runs from players and is caught by right-clicking it,
 * which gives its item (Terraria uses a Bug Net). It does not count towards the enemy cap.
 */
public class CritterMob extends TerrariaMob {
    private final Supplier<? extends Item> caught;

    public CritterMob(EntityType<? extends CritterMob> type, Level level, Supplier<? extends Item> caught) {
        super(type, level);
        this.caught = caught;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.2, 1.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level() instanceof ServerLevel level && isAlive()) {
            ItemStack stack = new ItemStack(caught.get());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.0);
            playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.4F);
            discard();
        }
        return InteractionResult.SUCCESS;
    }
}
