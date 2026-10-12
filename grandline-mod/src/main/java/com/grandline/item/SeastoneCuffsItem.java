package com.grandline.item;

import com.grandline.combat.CombatState;
import com.grandline.fruit.FruitRules;
import com.grandline.npc.GLNpc;
import com.grandline.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Cuff a staggered, knocked-out or defeated target: nullifies Devil Fruit powers and arrests NPCs. */
public class SeastoneCuffsItem extends Item {
    public SeastoneCuffsItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        long now = player.level().getGameTime();
        boolean helpless = CombatState.of(target).staggered(now) || target.hasEffect(ModEffects.KNOCKED_OUT) || target.getHealth() < target.getMaxHealth() * 0.25f;
        if (!helpless) {
            player.displayClientMessage(Component.translatable("grandline.cuffs.resist"), true);
            return InteractionResult.FAIL;
        }
        target.addEffect(new MobEffectInstance(ModEffects.SEASTONE_DRAIN, 20 * 60 * 5, 0));
        FruitRules.applySeastoneContact(target, 20 * 60 * 5);
        if (target instanceof GLNpc npc) npc.arrestedBy(player);
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.CONSUME;
    }
}
