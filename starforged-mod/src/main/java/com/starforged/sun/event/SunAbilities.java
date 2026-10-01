package com.starforged.sun.event;

import com.starforged.item.StarforgedArmorItem;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunItems;
import com.starforged.sun.SunSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side logic for Sunforged gear:
 * <ul>
 *     <li>Sunsteel set: immune to fire and lava; anything that strikes you is set ablaze.</li>
 *     <li>Phoenix Mantle: rocket-free gliding on a sunwind, and once every five minutes, <b>Rebirth</b> - cheat death in a burst of flame.</li>
 *     <li>Magma Treads: lava hardens into a crust beneath your feet so you can walk across it.</li>
 *     <li>Solar Crown: Radiance - nearby enemies smoulder; you see in the dark.</li>
 *     <li>Solar Lance dash (tracked here so it can hit everything along the path).</li>
 * </ul>
 */
public final class SunAbilities {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<UUID, Long> LAST_REBIRTH = new HashMap<>();
    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final List<Crust> CRUSTS = new ArrayList<>();
    public static final long REBIRTH_COOLDOWN = 6000;

    private static final class Dash {
        int ticks;
        final Vec3 direction;
        final float damage;
        final Set<Integer> hit = new HashSet<>();

        Dash(int ticks, Vec3 direction, float damage) {
            this.ticks = ticks;
            this.direction = direction;
            this.damage = damage;
        }
    }

    private record Crust(ResourceKey<Level> dimension, BlockPos pos, long expires) {
    }

    private SunAbilities() {
    }

    private static boolean has(LivingEntity entity, EquipmentSlot slot, StarforgedArmorItem.Ability ability) {
        ItemStack stack = entity.getItemBySlot(slot);
        return stack.getItem() instanceof StarforgedArmorItem armor && armor.ability() == ability;
    }

    public static boolean hasSunsteelSet(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR) {
            if (!has(entity, slot, StarforgedArmorItem.Ability.SUNSTEEL_SET)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasPhoenixMantle(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(SunItems.PHOENIX_MANTLE.get());
    }

    public static boolean hasMagmaTreads(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).is(SunItems.MAGMA_TREADS.get());
    }

    public static boolean hasSolarCrown(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(SunItems.SOLAR_CROWN.get());
    }

    /** Called every server tick for each player. */
    public static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        if (player.tickCount % 20 == 0) {
            if (hasSunsteelSet(player) || hasSolarCrown(player) || hasMagmaTreads(player)) {
                refresh(player, MobEffects.FIRE_RESISTANCE);
            }
            if (hasSolarCrown(player)) {
                refresh(player, MobEffects.NIGHT_VISION);
            }
        }
        if (hasSolarCrown(player) && player.tickCount % 40 == 0) {
            for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6.0), e -> e instanceof Enemy && e.isAlive())) {
                mob.igniteForSeconds(3.0F);
                level.sendParticles(ModParticles.SOLAR_SPARK.get(), mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (hasMagmaTreads(player)) {
            crustUnder(player, level);
        }
        Dash dash = DASHES.get(player.getUUID());
        if (dash != null) {
            tickDash(player, level, dash);
        }
    }

    private static void refresh(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 240) {
            player.addEffect(new MobEffectInstance(effect, 320, 0, true, false, true));
        }
    }

    /** Lava within a step of the wearer's feet hardens into magma for a few seconds. */
    private static void crustUnder(ServerPlayer player, ServerLevel level) {
        if (player.isShiftKeyDown()) {
            return;
        }
        BlockPos feet = player.blockPosition();
        long now = level.getGameTime();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = feet.offset(dx, -1, dz);
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.LAVA) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    CRUSTS.add(new Crust(level.dimension(), pos.immutable(), now + 60 + level.getRandom().nextInt(20)));
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.2, 0.0, 0.2, 0.01);
                }
            }
        }
    }

    /** Melts expired magma crusts back into lava. Called once per level tick. */
    public static void tickLevel(ServerLevel level) {
        if (CRUSTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Crust> it = CRUSTS.iterator();
        while (it.hasNext()) {
            Crust crust = it.next();
            if (!crust.dimension().equals(level.dimension()) || crust.expires() > now) {
                continue;
            }
            if (level.getBlockState(crust.pos()).is(Blocks.MAGMA_BLOCK)) {
                boolean occupied = !level.getEntitiesOfClass(Player.class, new AABB(crust.pos().above()), p -> hasMagmaTreads(p)).isEmpty();
                if (occupied) {
                    continue;
                }
                level.setBlock(crust.pos(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            }
            it.remove();
        }
    }

    // --- Rebirth ---------------------------------------------------------------------------------------------------

    /** @return true if the Phoenix Mantle saved this player from death. */
    public static boolean tryRebirth(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasPhoenixMantle(player)) {
            return false;
        }
        ServerLevel level = serverPlayer.level();
        long now = level.getGameTime();
        Long last = LAST_REBIRTH.get(player.getUUID());
        if (last != null && now - last < REBIRTH_COOLDOWN) {
            return false;
        }
        LAST_REBIRTH.put(player.getUUID(), now);
        player.setHealth(12.0F);
        player.removeAllEffects();
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 2));
        Vec3 center = player.position().add(0, 1.0, 0);
        for (LivingEntity target : Combat.targetsAround(level, player, center, 7.0)) {
            target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SUNFIRE, player), 12.0F);
            target.igniteForSeconds(8.0F);
            Combat.blast(target, center, 1.6, 0.7);
        }
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE9A0), center.x, center.y, center.z, 2, 0.3, 0.3, 0.3, 0.0);
        Fx.sphere(level, ParticleTypes.FLAME, center, 0.5, 160, 0.7);
        Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), center, 0.5, 120, 0.9);
        Fx.column(level, ParticleTypes.FLAME, player.position(), 8.0, 80, 0.6, 0.2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SunSounds.REBIRTH.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        Fx.shake(level, center, 24.0, 1.0F, 20);
        player.sendSystemMessage(Component.translatable("ability.starforged.phoenix_mantle.rebirth").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return true;
    }

    /** Sunsteel armor sets attackers ablaze. */
    public static void onHurt(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayer && source.getEntity() instanceof LivingEntity attacker && attacker != victim && hasSunsteelSet(victim)
            && source.getDirectEntity() == attacker) {
            attacker.igniteForSeconds(5.0F);
        }
    }

    // --- Solar Lance dash ------------------------------------------------------------------------------------------

    public static void startDash(ServerPlayer player, Vec3 direction, float damage) {
        DASHES.put(player.getUUID(), new Dash(10, direction, damage));
    }

    public static boolean isDashing(Player player) {
        return DASHES.containsKey(player.getUUID());
    }

    private static void tickDash(ServerPlayer player, ServerLevel level, Dash dash) {
        Vec3 velocity = dash.direction.scale(1.35);
        player.setDeltaMovement(velocity.x, Math.max(velocity.y, -0.2) + 0.02, velocity.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        // The trail streams out behind the rider so the flames never block their own view.
        Vec3 trail = player.position().subtract(dash.direction.multiply(1, 0, 1).scale(1.4)).add(0, 0.9, 0);
        level.sendParticles(ParticleTypes.FLAME, trail.x, trail.y, trail.z, 6, 0.3, 0.4, 0.3, 0.02);
        level.sendParticles(ModParticles.SOLAR_SPARK.get(), trail.x, trail.y, trail.z, 4, 0.3, 0.4, 0.3, 0.05);
        for (LivingEntity victim : Combat.targetsAround(level, player, player.position().add(0, 1.0, 0), 2.2)) {
            if (dash.hit.add(victim.getId())) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SUNFIRE, player), dash.damage);
                victim.igniteForSeconds(5.0F);
                Combat.knock(victim, 0.9, dash.direction.multiply(1, 0, 1).normalize());
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            }
        }
        if (--dash.ticks <= 0 || player.horizontalCollision) {
            DASHES.remove(player.getUUID());
            player.setDeltaMovement(player.getDeltaMovement().scale(0.25));
            player.hurtMarked = true;
        }
    }

    public static void clear(Player player) {
        DASHES.remove(player.getUUID());
    }
}
