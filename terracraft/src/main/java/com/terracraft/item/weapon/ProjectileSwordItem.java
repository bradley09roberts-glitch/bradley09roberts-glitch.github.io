package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.item.TerraItemStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * A sword that also fires a projectile with every swing (Chlorophyte Claymore's orb, Seedler's seeds), like
 * Terraria's projectile swords: the blade still hits what it touches.
 */
public class ProjectileSwordItem extends MeleeWeaponItem {
    private static final Map<UUID, Long> LAST_SHOT = new WeakHashMap<>();
    private final Supplier<ProjectileKind> projectile;
    private final float damageFactor;
    private final int count;
    private final float spread;

    /**
     * @param damageFactor projectile damage as a fraction of the sword's damage
     * @param count        projectiles per swing (fanned out by {@code spread} radians)
     */
    public ProjectileSwordItem(Properties properties, Supplier<ProjectileKind> projectile, float damageFactor, int count, float spread) {
        super(properties);
        this.projectile = projectile;
        this.damageFactor = damageFactor;
        this.count = count;
        this.spread = spread;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            TerraItemStats stats = TerraItemStats.of(stack);
            long now = level.getGameTime();
            Long last = LAST_SHOT.get(player.getUUID());
            if (last == null || now - last >= Math.max(4, stats.useTime())) {
                LAST_SHOT.put(player.getUUID(), now);
                Vec3 from = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.8));
                Vec3 look = player.getViewVector(1.0F);
                for (int i = 0; i < count; i++) {
                    float offset = count == 1 ? 0.0F : (i - (count - 1) / 2.0F) * spread;
                    TerrariaProjectile.shoot(level, player, projectile.get(), from, look.yRot(offset), Math.max(4.0F, stats.velocity()), 1.0F,
                        stats.damage() * damageFactor, stats.damageClass(), stats.crit(), stats.knockback() * 0.5F);
                }
            }
        }
        return super.onEntitySwing(stack, entity, hand);
    }
}
