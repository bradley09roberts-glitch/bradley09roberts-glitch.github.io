package com.grandline.ship;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Grappling hook: thrown from your ship's deck at another ship within {@value #RANGE} blocks, it hooks the rail and
 * the lines haul both hulls alongside so crews can cross. Thrown at another ship of your own (a captured prize, say)
 * it rigs a tow line instead. Sneak-use cuts your ship's lines.
 */
public class GrapplingHookItem extends Item {
    public static final double RANGE = 24;

    public GrapplingHookItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        ShipEntity own = NavalCombat.shipOf(sp);
        if (own == null) {
            sp.displayClientMessage(Component.translatable("grandline.naval.hook_need_ship"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (sp.isShiftKeyDown()) {
            if (own.linkedId() >= 0) {
                NavalCombat.unlink(own);
                level.playSound(null, sp.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1f, 0.8f);
                sp.displayClientMessage(Component.translatable("grandline.naval.lines_cut"), true);
            }
            return InteractionResultHolder.success(stack);
        }
        ShipEntity target = aimedShip((ServerLevel) level, sp, own);
        if (target == null) {
            sp.displayClientMessage(Component.translatable("grandline.naval.hook_miss"), true);
            sp.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.fail(stack);
        }
        boolean tow = sp.getUUID().equals(target.owner) && target.getFirstPassengerAtSeat(target.helmSeat) == null;
        if (!tow && !NavalCombat.mayGrapple(sp, target)) {
            sp.displayClientMessage(Component.translatable("grandline.naval.hook_refused").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        NavalCombat.link(own, target, tow);
        if (!tow) NavalCombat.noteAggression(target, own);
        level.playSound(null, sp.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.2f, 0.8f);
        sp.displayClientMessage(Component.translatable(tow ? "grandline.naval.towing" : "grandline.naval.hooked", target.shipName()).withStyle(ChatFormatting.GOLD), true);
        sp.getCooldowns().addCooldown(this, 40);
        if (!sp.isCreative()) stack.hurtAndBreak(1, sp, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.success(stack);
    }

    /** The other ship whose hull the player is looking at, within range. */
    @Nullable
    static ShipEntity aimedShip(ServerLevel level, Player p, ShipEntity own) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(RANGE));
        ShipEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ShipEntity s : level.getEntitiesOfClass(ShipEntity.class, new AABB(eye, eye).inflate(RANGE + 40), s -> s != own && s.isAlive() && !s.isSinking() && s.ferryTarget == null)) {
            for (var part : s.getParts()) {
                Optional<Vec3> hit = part.getBoundingBox().inflate(0.4).clip(eye, end);
                if (hit.isPresent() && hit.get().distanceToSqr(eye) < bd) {
                    bd = hit.get().distanceToSqr(eye);
                    best = s;
                }
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.grandline.grappling_hook.desc").withStyle(ChatFormatting.GRAY));
    }
}
