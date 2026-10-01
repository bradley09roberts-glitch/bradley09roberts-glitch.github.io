package com.starforged.sun.item;

import com.starforged.item.LoreItem;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunSounds;
import com.starforged.sun.entity.SolarFlareEntity;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Flare Greatsword, the Sun Warden's own blade. Right-click hurls an exploding solar flare; sneak + right-click
 * plants the blade and erupts in a ring of sunfire.
 */
public class FlareGreatswordItem extends Item {
    public FlareGreatswordItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                Vec3 center = player.position().add(0, 0.5, 0);
                for (LivingEntity target : Combat.targetsAround(server, player, center, 6.5)) {
                    target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.SUNFIRE, player), 12.0F);
                    target.igniteForSeconds(6.0F);
                    Combat.blast(target, center, 1.4, 0.6);
                }
                for (int r = 1; r <= 3; r++) {
                    Fx.ring(server, ParticleTypes.FLAME, center, r * 2.0, 24 + r * 12, 0.25, 0.25);
                }
                Fx.ring(server, ModParticles.SOLAR_SPARK.get(), center, 1.0, 60, 0.6, 0.3);
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), SunSounds.GREATSWORD_FLARE.get(), SoundSource.PLAYERS, 1.6F, 0.7F);
                Fx.shake(server, center, 16.0, 0.8F, 12);
                player.getCooldowns().addCooldown(stack, 160);
                stack.hurtAndBreak(5, player, hand);
            } else {
                Vec3 eye = player.getEyePosition();
                SolarFlareEntity.shoot(server, player, eye.add(player.getLookAngle().scale(0.8)), player.getLookAngle(), 1.4F, 12.0F,
                    SolarFlareEntity.Kind.FLARE, null);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), SunSounds.GREATSWORD_FLARE.get(), SoundSource.PLAYERS, 1.2F, 1.2F);
                player.getCooldowns().addCooldown(stack, 40);
                stack.hurtAndBreak(2, player, hand);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        target.igniteForSeconds(6.0F);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.solar_flare");
        LoreItem.addAbility(builder, "ability.starforged.solar_eruption");
    }
}
