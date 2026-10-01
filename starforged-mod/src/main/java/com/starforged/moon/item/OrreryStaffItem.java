package com.starforged.moon.item;

import com.starforged.item.LoreItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.entity.MoonletEntity;
import com.starforged.registry.ModParticles;
import com.starforged.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Orrery Staff: three little moons orbit you while you hold it. Right-click flings one at your target; they slowly
 * re-form. Sneak + right-click: <b>Moonfall</b> - every remaining moon merges overhead into one great moon that crashes
 * down where you aim. The more moons, the bigger the impact.
 */
public class OrreryStaffItem extends Item {
    public static final int MOONS = 3;

    public OrreryStaffItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        int moons = MoonCharges.get(stack, MOONS);
        if (moons <= 0) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                0.6F, 0.6F);
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            Vec3 ground = Targeting.groundTarget(server, player, 40.0);
            Vec3 start = ground.add(0, 18.0, 0);
            MoonletEntity.shoot(server, player, start, new Vec3(0, -1, 0), 0.4F, 8.0F + 7.0F * moons, MoonletEntity.Kind.MOONFALL, null)
                .withSize(1.6F + moons * 0.9F);
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), start.x, start.y, start.z, 60, 1.0, 1.0, 1.0, 0.1);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.STAFF_MOONFALL.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
            MoonCharges.set(stack, 0);
            player.getCooldowns().addCooldown(stack, 100);
            stack.hurtAndBreak(3, player, hand);
        } else {
            LivingEntity target = Targeting.homingTarget(server, player, 40.0, 0.85);
            Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.2, 0);
            MoonletEntity.shoot(server, player, from, player.getLookAngle(), 1.2F, 9.0F, MoonletEntity.Kind.ORB, target);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.STAFF_FIRE.get(), SoundSource.PLAYERS, 1.0F,
                1.0F + moons * 0.1F);
            MoonCharges.set(stack, moons - 1);
            player.getCooldowns().addCooldown(stack, 10);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        MoonCharges.regen(stack, MOONS, 60);
        if (slot == EquipmentSlot.MAINHAND && owner.tickCount % 2 == 0) {
            int moons = MoonCharges.get(stack, MOONS);
            for (int i = 0; i < moons; i++) {
                double a = owner.tickCount * 0.12 + i * Math.PI * 2 / MOONS;
                level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), owner.getX() + Math.cos(a) * 1.1, owner.getY() + 1.3 + Math.sin(a * 2) * 0.15,
                    owner.getZ() + Math.sin(a) * 1.1, 1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.starforged.orrery_staff.moons", MoonCharges.get(stack, MOONS), MOONS)
            .withStyle(net.minecraft.ChatFormatting.AQUA));
        LoreItem.addAbility(builder, "ability.starforged.orrery_staff");
        LoreItem.addAbility(builder, "ability.starforged.moonfall");
    }
}
