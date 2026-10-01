package com.starforged.moon.entity;

import com.starforged.moon.MoonItems;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * Moonkits: small silver foxes of the Pale Reach. Win one over with a Lunar Pearl and it becomes a treasure hunter:
 * <ul>
 *     <li><b>Lunar Scent</b> - every few seconds it sniffs out nearby ores and chests and traces a glimmering trail to
 *     them that only its owner can see.</li>
 *     <li><b>Moon Dig</b> - now and then it digs up a little lunar treasure.</li>
 * </ul>
 */
public class MoonkitEntity extends TamableAnimal {
    private int scentCooldown = 100;
    private int digCooldown = 1200;

    public MoonkitEntity(EntityType<? extends MoonkitEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.4));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.2, 8.0F, 2.0F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(MoonItems.LUNAR_PEARL.get());
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame()) {
            if (this.isFood(stack)) {
                if (this.level() instanceof ServerLevel) {
                    this.usePlayerItem(player, hand, stack);
                    if (this.random.nextInt(3) == 0) {
                        this.tame(player);
                        this.getNavigation().stop();
                        this.setOrderedToSit(false);
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }
        if (this.isOwnedBy(player) && stack.isEmpty()) {
            if (!this.level().isClientSide()) {
                boolean sit = !this.isOrderedToSit();
                this.setOrderedToSit(sit);
                this.getNavigation().stop();
                player.sendOverlayMessage(Component.translatable(sit ? "entity.starforged.moonkit.wait" : "entity.starforged.moonkit.follow")
                    .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        if (this.isOwnedBy(player) && this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide()) {
                this.usePlayerItem(player, hand, stack);
                this.heal(10.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            if (this.random.nextInt(6) == 0) {
                this.level().addParticle(ModParticles.MOON_DUST.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.5, this.getY() + 0.5,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.5, 0.0, 0.01, 0.0);
            }
            return;
        }
        if (!this.isTame() || !(this.level() instanceof ServerLevel level) || !(this.getOwner() instanceof ServerPlayer owner)
            || owner.distanceTo(this) > 16.0) {
            return;
        }
        if (--this.scentCooldown <= 0) {
            this.scentCooldown = 160;
            this.lunarScent(level, owner);
        }
        if (--this.digCooldown <= 0) {
            this.digCooldown = 1800 + this.random.nextInt(1200);
            this.moonDig(level);
        }
    }

    /** Traces glimmering trails from the kit to the nearest ores and chests - only the owner sees them. */
    private void lunarScent(ServerLevel level, ServerPlayer owner) {
        BlockPos origin = this.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-10, -8, -10), origin.offset(10, 6, 10))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.CHESTS)) {
                found.add(pos.immutable());
            }
        }
        if (found.isEmpty()) {
            return;
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MOONKIT_SNIFF.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        Vec3 from = this.position().add(0, 0.4, 0);
        for (BlockPos pos : found.subList(0, Math.min(3, found.size()))) {
            Vec3 to = Vec3.atCenterOf(pos);
            double length = from.distanceTo(to);
            for (double d = 0; d < length; d += 0.6) {
                Vec3 at = from.add(to.subtract(from).scale(d / length));
                level.sendParticles(owner, ModParticles.LUNAR_GLIMMER.get(), true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(owner, ModParticles.LUNAR_GLIMMER.get(), true, true, to.x, to.y, to.z, 12, 0.35, 0.35, 0.35, 0.02);
        }
    }

    private void moonDig(ServerLevel level) {
        if (!this.onGround() || this.random.nextInt(2) != 0) {
            return;
        }
        ItemStack treasure = switch (this.random.nextInt(6)) {
            case 0 -> new ItemStack(MoonItems.RAW_MOONSILVER.get());
            case 1, 2 -> new ItemStack(MoonItems.SELENITE_SHARD.get(), 1 + this.random.nextInt(2));
            case 3 -> new ItemStack(MoonItems.LUNAR_PEARL.get());
            default -> new ItemStack(MoonItems.LUNAR_DUST.get(), 1 + this.random.nextInt(3));
        };
        ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 0.3, this.getZ(), treasure);
        item.setDeltaMovement(0, 0.2, 0);
        level.addFreshEntity(item);
        level.sendParticles(ModParticles.MOON_DUST.get(), this.getX(), this.getY() + 0.1, this.getZ(), 20, 0.3, 0.1, 0.3, 0.03);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MOONKIT_AMBIENT.get(), SoundSource.NEUTRAL, 1.0F, 1.4F);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isTame();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MoonSounds.MOONKIT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.MOONKIT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.MOONKIT_AMBIENT.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.3F;
    }

    public @Nullable LivingEntity ownerEntity() {
        return this.getOwner();
    }
}
