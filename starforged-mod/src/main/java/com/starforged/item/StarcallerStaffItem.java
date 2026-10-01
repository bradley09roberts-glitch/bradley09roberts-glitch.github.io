package com.starforged.item;

import com.starforged.entity.projectile.MeteorEntity;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Fx;
import com.starforged.util.Targeting;
import java.util.function.Consumer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Starcaller Staff: point at the ground and a meteor screams down from the sky onto that spot.
 * Sneak to call a whole meteor shower.
 */
public class StarcallerStaffItem extends Item {
    public static final int COOLDOWN = 50;
    public static final int SHOWER_COOLDOWN = 260;

    public StarcallerStaffItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            boolean shower = player.isShiftKeyDown();
            Vec3 target = Targeting.groundTarget(server, player, 80.0);
            Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

            if (shower) {
                for (int i = 0; i < 7; i++) {
                    double angle = server.getRandom().nextDouble() * Math.PI * 2;
                    double dist = i == 0 ? 0 : 3.0 + server.getRandom().nextDouble() * 6.0;
                    Vec3 spot = target.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                    Vec3 start = spot.add(flat.scale(-22.0 - i * 3)).add(0, 42.0 + i * 7.0, 0);
                    MeteorEntity.launch(server, start, spot, 0.8F + server.getRandom().nextFloat() * 0.5F, MeteorEntity.Kind.SUMMONED, player, 1.7F);
                }
                player.getCooldowns().addCooldown(stack, SHOWER_COOLDOWN);
                stack.hurtAndBreak(6, player, hand);
            } else {
                Vec3 start = target.add(flat.scale(-24.0)).add(0, 46.0, 0);
                MeteorEntity.launch(server, start, target, 1.25F, MeteorEntity.Kind.SUMMONED, player, 1.8F);
                player.getCooldowns().addCooldown(stack, COOLDOWN);
                stack.hurtAndBreak(1, player, hand);
            }

            Vec3 hand3d = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.3, 0);
            Fx.burst(server, ModParticles.STAR_SPARKLE.get(), hand3d, 18, 0.25, 0.08);
            Fx.column(server, ModParticles.ASTRAL_GLINT.get(), player.position(), 2.5, 16, 0.8, 0.12);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.STAFF_CAST.get(), SoundSource.PLAYERS, 1.2F,
                shower ? 0.75F : 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.starcaller");
        LoreItem.addAbility(builder, "ability.starforged.starcaller_shower");
    }
}
