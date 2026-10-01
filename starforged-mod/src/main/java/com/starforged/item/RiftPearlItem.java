package com.starforged.item;

import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Fx;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rift Pearl: a reusable pearl that folds space. Right-click to blink to wherever you are looking.
 */
public class RiftPearlItem extends Item {
    private static final double RANGE = 36.0;

    public RiftPearlItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 destination = findDestination(server, player);
            if (destination == null) {
                player.sendOverlayMessage(Component.translatable("item.starforged.rift_pearl.blocked"));
                return InteractionResult.FAIL;
            }
            Vec3 origin = player.position();
            Fx.burst(server, ParticleTypes.REVERSE_PORTAL, origin.add(0, 1, 0), 60, 0.4, 0.15);
            Fx.burst(server, ModParticles.VOID_MOTE.get(), origin.add(0, 1, 0), 25, 0.4, 0.05);
            Fx.line(server, ModParticles.ASTRAL_GLINT.get(), origin.add(0, 1, 0), destination.add(0, 1, 0), 0.6);
            server.playSound(null, origin.x, origin.y, origin.z, ModSounds.RIFT_TELEPORT.get(), SoundSource.PLAYERS, 1.0F, 1.2F);

            player.stopRiding();
            player.teleportTo(destination.x, destination.y, destination.z);
            player.resetFallDistance();

            Fx.burst(server, ParticleTypes.PORTAL, destination.add(0, 1, 0), 60, 0.5, 0.6);
            Fx.burst(server, ModParticles.STAR_SPARKLE.get(), destination.add(0, 1, 0), 20, 0.5, 0.05);
            server.playSound(null, destination.x, destination.y, destination.z, ModSounds.RIFT_TELEPORT.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
            player.getCooldowns().addCooldown(stack, 25);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static @Nullable Vec3 findDestination(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target;
        if (hit.getType() == HitResult.Type.MISS) {
            target = hit.getLocation().subtract(0, player.getEyeHeight(), 0);
        } else if (hit.getDirection() == Direction.UP) {
            target = hit.getLocation();
        } else {
            target = hit.getLocation().add(look.scale(-0.7)).subtract(0, hit.getDirection() == Direction.DOWN ? player.getBbHeight() : 0.9, 0);
        }
        // Nudge upward until the player fits.
        for (int i = 0; i < 4; i++) {
            Vec3 candidate = target.add(0, i * 0.5, 0);
            AABB box = player.getDimensions(player.getPose()).makeBoundingBox(candidate);
            if (level.noCollision(player, box)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.rift_pearl");
    }
}
