package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.entity.ArcBeamEntity;
import com.starforged.util.Combat;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arc Cannon: hold right-click to charge, release to fire a beam of lightning that pierces everything in a line.
 * Hold it for two full seconds to <b>Overcharge</b>: a far heavier beam that ends in a lightning strike.
 */
public class ArcCannonItem extends Item {
    public static final int OVERCHARGE = 40;
    private static final double RANGE = 48.0;

    public ArcCannonItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (level instanceof ServerLevel server) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.CANNON_CHARGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int held = this.getUseDuration(stack, user) - remaining;
        if (level.isClientSide()) {
            Vec3 muzzle = user.getEyePosition().add(user.getLookAngle().scale(1.0)).add(0, -0.2, 0);
            int sparks = held >= OVERCHARGE ? 3 : 1;
            for (int i = 0; i < sparks; i++) {
                level.addParticle(ModParticles.STATIC_SPARK.get(), muzzle.x + (level.getRandom().nextDouble() - 0.5) * 0.4,
                    muzzle.y + (level.getRandom().nextDouble() - 0.5) * 0.4, muzzle.z + (level.getRandom().nextDouble() - 0.5) * 0.4, 0, 0, 0);
            }
        } else if (held == OVERCHARGE && user instanceof Player player) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), TempestSounds.CHARGE.get(), SoundSource.PLAYERS, 1.0F, 1.4F);
            player.sendOverlayMessage(Component.translatable("item.starforged.arc_cannon.overcharged").withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        int held = this.getUseDuration(stack, user) - remaining;
        if (held < 8 || !(level instanceof ServerLevel server) || !(user instanceof Player player)) {
            return false;
        }
        boolean over = held >= OVERCHARGE;
        float damage = over ? 26.0F : 8.0F + held * 0.25F;
        Vec3 from = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        HitResult block = server.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getLocation();
        double width = over ? 1.4 : 0.8;
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(width),
            e -> e != player && Combat.canHit(server, player, e))) {
            if (distanceToSegment(victim.position().add(0, victim.getBbHeight() * 0.5, 0), from, end) < width + victim.getBbWidth() * 0.5) {
                victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.STORM, player), damage);
            }
        }
        Vec3 muzzle = from.add(dir.scale(0.8)).add(0, -0.25, 0);
        ArcBeamEntity.fire(server, muzzle, end, over ? 0.9F : 0.45F);
        if (over) {
            TempestFx.strike(server, end, player, 12.0F, 3.0);
            TempestFx.thunder(server, end, 2.0F, 0.6F);
        }
        server.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.CANNON_FIRE.get(), SoundSource.PLAYERS, 1.2F,
            over ? 0.8F : 1.1F);
        player.getCooldowns().addCooldown(stack, over ? 40 : 15);
        stack.hurtAndBreak(over ? 3 : 1, player, player.getUsedItemHand());
        return true;
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.arc_cannon");
        LoreItem.addAbility(builder, "ability.starforged.overcharge");
    }
}
