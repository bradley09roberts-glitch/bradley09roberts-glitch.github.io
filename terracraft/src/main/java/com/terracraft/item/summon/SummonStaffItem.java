package com.terracraft.item.summon;

import com.terracraft.entity.summon.MinionEntity;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.weapon.UsableWeapon;
import com.terracraft.item.weapon.WeaponFiring;
import com.terracraft.player.ManaManager;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.Stat;
import com.terracraft.registry.RegistryObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * A summon staff: spends mana to call a minion (which takes a minion slot) or place a sentry where the player is
 * looking (a sentry slot). Past the limit the oldest one is dismissed. Minion slots are 1 plus the Max Minions
 * stat; sentry slots 1 plus Max Sentries.
 */
public class SummonStaffItem extends TerraItem implements UsableWeapon {
    private final RegistryObject<? extends EntityType<? extends MinionEntity>> minion;

    public SummonStaffItem(Properties properties, RegistryObject<? extends EntityType<? extends MinionEntity>> minion) {
        super(properties);
        this.minion = minion;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return UsableWeapon.use(this, level, player, hand);
    }

    @Override
    public boolean fire(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.level();
        TerraItemStats stats = TerraItemStats.of(stack);
        TerraPlayerData data = TerraPlayerData.get(player);
        MinionEntity entity = minion.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null || !ManaManager.consume(player, data, stats.mana())) {
            return false;
        }
        boolean sentry = entity.isSentry();
        int slots = 1 + data.stats().getInt(sentry ? Stat.MAX_SENTRIES : Stat.MAX_MINIONS);
        List<MinionEntity> mine = new java.util.ArrayList<>(level.getEntitiesOfClass(MinionEntity.class, player.getBoundingBox().inflate(160),
            m -> m.isSentry() == sentry && m.ownedBy(player)));
        mine.sort(Comparator.comparingInt(m -> -m.tickCount));
        while (mine.size() >= Math.max(1, slots)) {
            mine.remove(0).discard();
        }
        Vec3 at;
        if (sentry) {
            Vec3 eye = player.getEyePosition();
            HitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(20)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
            at = hit.getLocation().subtract(player.getLookAngle().scale(0.5)).add(0, 0.5, 0);
        } else {
            at = player.position().add(0, 1.5, 0);
        }
        entity.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
        entity.setup(player, stats.damage(), stats.knockback());
        level.addFreshEntity(entity);
        level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.5, at.z, 16, 0.4, 0.4, 0.4, 0.05);
        WeaponFiring.sound(player, SoundEvents.EVOKER_PREPARE_SUMMON, 1.6F);
        return true;
    }
}
