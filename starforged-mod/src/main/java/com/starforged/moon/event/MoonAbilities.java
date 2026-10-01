package com.starforged.moon.event;

import com.starforged.item.StarforgedArmorItem;
import com.starforged.moon.MoonItems;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.world.MoonGravity;
import com.starforged.moon.world.MoonTides;
import com.starforged.moon.world.PaleReachTravel;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
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
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side logic for Moonforged gear:
 * <ul>
 *     <li>Moonsilver set: <b>Lunar Ward</b> (absorbs one heavy hit every 30 s and slows the attacker), <b>Gravity Shift</b>
 *     (sneak + jump toggles light gravity, anywhere) and, at High Tide in the Pale Reach, Speed and Haste.</li>
 *     <li>Crown of Tides: water breathing, light gravity, and creatures lurking in the dark are outlined.</li>
 *     <li>Phase Daggers' dash and delayed crescent cuts, the Stasis Bell's frozen time, and the Tidecaller's reversed gravity.</li>
 * </ul>
 */
public final class MoonAbilities {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int WARD_COOLDOWN = 600;
    private static final Map<UUID, Long> WARD_READY = new HashMap<>();
    private static final Set<UUID> LIGHT = new HashSet<>();
    private static final Map<UUID, Boolean> LAST_SHIFT_JUMP = new HashMap<>();
    private static final Map<UUID, Phase> PHASES = new HashMap<>();
    private static final List<Cut> CUTS = new ArrayList<>();
    private static final List<Frozen> FROZEN = new ArrayList<>();
    private static final List<Slam> SLAMS = new ArrayList<>();

    private static final class Phase {
        int ticks = 7;
        final Vec3 direction;
        final Set<Integer> cut = new HashSet<>();

        Phase(Vec3 direction) {
            this.direction = direction;
        }
    }

    private record Cut(ResourceKey<Level> dimension, int entity, UUID owner, long detonate) {
    }

    private static final class Frozen {
        final ResourceKey<Level> dimension;
        final int entity;
        final Vec3 pos;
        final Vec3 velocity;
        final boolean hadNoAi;
        final long until;

        Frozen(ResourceKey<Level> dimension, Entity entity, long until) {
            this.dimension = dimension;
            this.entity = entity.getId();
            this.pos = entity.position();
            this.velocity = entity.getDeltaMovement();
            this.hadNoAi = entity instanceof Mob mob && mob.isNoAi();
            this.until = until;
        }
    }

    private record Slam(ResourceKey<Level> dimension, int entity, UUID owner, long at) {
    }

    private MoonAbilities() {
    }

    // --- Armor ----------------------------------------------------------------------------------------------------

    public static boolean hasMoonsilverSet(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof StarforgedArmorItem armor && armor.ability() == StarforgedArmorItem.Ability.MOONSILVER_SET)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasTideCrown(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(MoonItems.CROWN_OF_TIDES.get());
    }

    public static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        boolean set = hasMoonsilverSet(player);
        boolean crown = hasTideCrown(player);
        // Gravity Shift: sneak + jump toggles light gravity.
        boolean combo = player.getLastClientInput().shift() && player.getLastClientInput().jump();
        boolean was = LAST_SHIFT_JUMP.getOrDefault(player.getUUID(), false);
        LAST_SHIFT_JUMP.put(player.getUUID(), combo);
        if (combo && !was && set) {
            boolean on = !LIGHT.remove(player.getUUID());
            if (on) {
                LIGHT.add(player.getUUID());
            }
            player.sendOverlayMessage(Component.translatable(on ? "ability.starforged.gravity_shift.on" : "ability.starforged.gravity_shift.off")
                .withStyle(ChatFormatting.AQUA));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.GRAVITY_PLATE.get(), SoundSource.PLAYERS, 0.6F, on ? 1.5F : 0.8F);
            level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.4, 0.1, 0.4, 0.1);
        }
        boolean light = crown || (set && LIGHT.contains(player.getUUID()));
        MoonGravity.tickPlayer(player, light);

        if (player.tickCount % 20 == 0) {
            if (set && MoonTides.isHighTide(level)) {
                refresh(player, MobEffects.SPEED);
                refresh(player, MobEffects.HASTE);
            }
            if (crown) {
                refresh(player, MobEffects.WATER_BREATHING);
                for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24.0),
                    e -> e instanceof Enemy && e.isAlive() && level.getMaxLocalRawBrightness(e.blockPosition()) < 8)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false), player);
                }
            }
            if (set && wardReady(player) && player.tickCount % 40 == 0) {
                Fx.ring(level, ModParticles.LUNAR_GLIMMER.get(), player.position().add(0, 1.0, 0), 0.8, 12, 0.0, 0.0);
            }
        }
        Phase phase = PHASES.get(player.getUUID());
        if (phase != null) {
            tickPhase(player, level, phase);
        }
    }

    private static void refresh(ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 30) {
            player.addEffect(new MobEffectInstance(effect, 60, 0, true, false, true));
        }
    }

    private static boolean wardReady(Player player) {
        return WARD_READY.getOrDefault(player.getUUID(), 0L) <= player.level().getGameTime();
    }

    /** Returns the damage multiplier for {@code victim} (0 when the Lunar Ward absorbs the hit). */
    public static float onHurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim instanceof ServerPlayer player && amount >= 5.0F && hasMoonsilverSet(player) && wardReady(player)
            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            ServerLevel level = player.level();
            WARD_READY.put(player.getUUID(), level.getGameTime() + WARD_COOLDOWN);
            if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), player);
            }
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), player.position().add(0, 1.0, 0), 0.8, 50, 0.3);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.WARD_BREAK.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            player.sendOverlayMessage(Component.translatable("ability.starforged.lunar_ward.absorbed").withStyle(ChatFormatting.AQUA));
            return 0.0F;
        }
        return 1.0F;
    }

    // --- Phase Daggers --------------------------------------------------------------------------------------------

    public static void startPhase(ServerPlayer player, Vec3 direction) {
        PHASES.put(player.getUUID(), new Phase(direction));
    }

    public static boolean isPhasing(Player player) {
        return PHASES.containsKey(player.getUUID());
    }

    private static void tickPhase(ServerPlayer player, ServerLevel level, Phase phase) {
        Vec3 v = phase.direction.scale(1.5);
        player.setDeltaMovement(v.x, Math.max(v.y, -0.2) + 0.02, v.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        Vec3 trail = player.position().add(0, 1.0, 0).subtract(phase.direction.scale(1.2));
        level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), trail.x, trail.y, trail.z, 6, 0.2, 0.3, 0.2, 0.02);
        for (LivingEntity victim : Combat.targetsAround(level, player, player.position().add(0, 1.0, 0), 2.0)) {
            if (phase.cut.add(victim.getId())) {
                CUTS.add(new Cut(level.dimension(), victim.getId(), player.getUUID(), level.getGameTime() + 20));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 1, 0, 0, 0, 0);
            }
        }
        if (--phase.ticks <= 0 || player.horizontalCollision) {
            PHASES.remove(player.getUUID());
            player.setDeltaMovement(player.getDeltaMovement().scale(0.3));
            player.hurtMarked = true;
        }
    }

    // --- Stasis Bell ----------------------------------------------------------------------------------------------

    public static void ringStasis(ServerPlayer player, double radius, int ticks) {
        ServerLevel level = player.level();
        long until = level.getGameTime() + ticks;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(radius), e -> !(e instanceof Player) && !(e instanceof ItemEntity))) {
            if (entity instanceof LivingEntity living && (living.getMaxHealth() > 300.0F || !living.isAlive())) {
                continue;
            }
            if (entity instanceof OwnableEntity pet && pet.getOwner() == player) {
                continue;
            }
            if (!(entity instanceof LivingEntity) && !(entity instanceof Projectile)) {
                continue;
            }
            FROZEN.add(new Frozen(level.dimension(), entity, until));
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }
        for (int i = 0; i < 3; i++) {
            Fx.ring(level, ModParticles.LUNAR_GLIMMER.get(), player.position().add(0, 0.5 + i, 0), radius * (0.4 + i * 0.3), 60, 0.0, 0.0);
        }
    }

    // --- Tidecaller: reverse gravity ------------------------------------------------------------------------------

    public static void reverseGravity(ServerLevel level, Player player, double radius) {
        for (LivingEntity victim : Combat.targetsAround(level, player, player.position(), radius)) {
            victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 2), player);
            SLAMS.add(new Slam(level.dimension(), victim.getId(), player.getUUID(), level.getGameTime() + 42));
        }
        Fx.ring(level, ModParticles.LUNAR_GLIMMER.get(), player.position().add(0, 0.2, 0), 1.0, 80, 0.7, 0.2);
        Fx.column(level, ParticleTypes.SPLASH, player.position(), 3.0, 80, radius * 0.5, 0.3);
    }

    // --- Per-level tick -------------------------------------------------------------------------------------------

    public static void tickLevel(ServerLevel level) {
        long now = level.getGameTime();
        if (!CUTS.isEmpty()) {
            Iterator<Cut> it = CUTS.iterator();
            while (it.hasNext()) {
                Cut cut = it.next();
                if (cut.dimension != level.dimension()) {
                    continue;
                }
                Entity entity = level.getEntity(cut.entity);
                if (entity == null || !entity.isAlive()) {
                    it.remove();
                    continue;
                }
                Vec3 at = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
                if (now < cut.detonate) {
                    level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), at.x, at.y, at.z, 2, 0.25, 0.25, 0.25, 0.0);
                    continue;
                }
                Player owner = level.getPlayerByUUID(cut.owner);
                entity.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.MOONLIGHT, owner), 10.0F);
                Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), at, 0.3, 30, 0.35);
                level.playSound(null, at.x, at.y, at.z, MoonSounds.DAGGER_DETONATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F + level.getRandom().nextFloat() * 0.3F);
                it.remove();
            }
        }
        if (!FROZEN.isEmpty()) {
            Iterator<Frozen> it = FROZEN.iterator();
            while (it.hasNext()) {
                Frozen f = it.next();
                if (f.dimension != level.dimension()) {
                    continue;
                }
                Entity entity = level.getEntity(f.entity);
                if (entity == null || !entity.isAlive()) {
                    it.remove();
                    continue;
                }
                if (now >= f.until) {
                    if (entity instanceof Mob mob && !f.hadNoAi) {
                        mob.setNoAi(false);
                    }
                    if (entity instanceof Projectile) {
                        entity.setDeltaMovement(f.velocity);
                        entity.hurtMarked = true;
                    }
                    it.remove();
                    continue;
                }
                entity.setPos(f.pos.x, f.pos.y, f.pos.z);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.hurtMarked = true;
                if (now % 6 == 0) {
                    level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(), 2,
                        0.2, 0.1, 0.2, 0.0);
                }
            }
        }
        if (!SLAMS.isEmpty()) {
            Iterator<Slam> it = SLAMS.iterator();
            while (it.hasNext()) {
                Slam slam = it.next();
                if (slam.dimension != level.dimension() || now < slam.at) {
                    continue;
                }
                Entity entity = level.getEntity(slam.entity);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    living.removeEffect(MobEffects.LEVITATION);
                    living.setDeltaMovement(0, -2.4, 0);
                    living.hurtMarked = true;
                    living.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.GRAVITY, level.getPlayerByUUID(slam.owner)), 9.0F);
                    level.sendParticles(ModParticles.MOON_DUST.get(), living.getX(), living.getY(), living.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
                }
                it.remove();
            }
        }
    }

    public static void clear(Player player) {
        PHASES.remove(player.getUUID());
        LIGHT.remove(player.getUUID());
        MoonGravity.clear(player);
    }

    public static boolean inPaleReach(Player player) {
        return PaleReachTravel.isPaleReach(player.level());
    }
}
