package com.starforged.moon.item;

import com.starforged.item.LoreItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.event.MoonAbilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phase Daggers: three dash charges. Right-click to <b>Phase</b> straight through enemies; each one you pass through is
 * left with a glowing crescent cut hanging in the air - a second later every cut detonates at once.
 */
public class PhaseDaggersItem extends Item {
    public static final int CHARGES = 3;

    public PhaseDaggersItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            int charges = MoonCharges.get(stack, CHARGES);
            if (charges <= 0 || MoonAbilities.isPhasing(player)) {
                return InteractionResult.FAIL;
            }
            Vec3 look = player.getLookAngle();
            Vec3 dir = new Vec3(look.x, Math.max(-0.4, Math.min(0.4, look.y)), look.z).normalize();
            MoonAbilities.startPhase(serverPlayer, dir);
            MoonCharges.set(stack, charges - 1);
            player.getCooldowns().addCooldown(stack, 8);
            stack.hurtAndBreak(1, player, hand);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.DAGGER_PHASE.get(), SoundSource.PLAYERS, 1.0F,
                1.0F + charges * 0.1F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        MoonCharges.regen(stack, CHARGES, 50);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.starforged.phase_daggers.charges", MoonCharges.get(stack, CHARGES), CHARGES)
            .withStyle(net.minecraft.ChatFormatting.AQUA));
        LoreItem.addAbility(builder, "ability.starforged.phase_daggers");
    }
}
