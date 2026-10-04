package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.item.TerraItemStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Starfury: every swing calls a star down from the sky onto the spot the player is looking at. */
public class SkyfallSwordItem extends MeleeWeaponItem {
    private static final Map<UUID, Long> LAST_SHOT = new WeakHashMap<>();
    private final Supplier<ProjectileKind> star;

    public SkyfallSwordItem(Properties properties, Supplier<ProjectileKind> star) {
        super(properties);
        this.star = star;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            TerraItemStats stats = TerraItemStats.of(stack);
            long now = level.getGameTime();
            Long last = LAST_SHOT.get(player.getUUID());
            if (last == null || now - last >= Math.max(4, stats.useTime())) {
                LAST_SHOT.put(player.getUUID(), now);
                Vec3 eye = player.getEyePosition();
                Vec3 target = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(24)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getLocation();
                Vec3 from = target.add((player.getRandom().nextDouble() - 0.5) * 8, 16, (player.getRandom().nextDouble() - 0.5) * 8);
                TerrariaProjectile.shoot(level, player, star.get(), from, target.subtract(from).normalize(), Math.max(8.0F, stats.velocity()), 0.0F,
                    stats.damage(), stats.damageClass(), stats.crit(), stats.knockback());
                WeaponFiring.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F);
            }
        }
        return super.onEntitySwing(stack, entity, hand);
    }
}
