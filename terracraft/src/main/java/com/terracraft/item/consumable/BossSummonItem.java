package com.terracraft.item.consumable;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.item.TerraItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** Slime Crown, Suspicious Looking Eye...: consumed to summon a boss when its conditions are met. */
public class BossSummonItem extends TerraItem {
    private final Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> boss;
    private final BossSummoning.Arrival arrival;
    private final boolean nightOnly;
    private final java.util.function.Predicate<ServerPlayer> requirement;

    public BossSummonItem(Properties properties, Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> boss, BossSummoning.Arrival arrival,
                          boolean nightOnly) {
        this(properties, boss, arrival, nightOnly, player -> true);
    }

    /** @param requirement extra condition (e.g. standing in the Corruption) */
    public BossSummonItem(Properties properties, Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> boss, BossSummoning.Arrival arrival,
                          boolean nightOnly, java.util.function.Predicate<ServerPlayer> requirement) {
        super(properties);
        this.boss = boss;
        this.arrival = arrival;
        this.nightOnly = nightOnly;
        this.requirement = requirement;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!BossSummoning.inOverworld(serverPlayer) || nightOnly && !serverPlayer.level().isDarkOutside() || !requirement.test(serverPlayer)) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.terracraft.boss.nothing_happens").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (BossSummoning.summon(serverPlayer.level(), serverPlayer, boss.get(), arrival) == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.terracraft.boss.already_active").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0F, 1.2F);
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
