package com.terracraft.entity.mob;

import com.terracraft.combat.HasTerrariaDefense;
import com.terracraft.combat.TerraDamageTypes;
import com.terracraft.combat.TerraHit;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.economy.EconomyEvents;
import com.terracraft.entity.projectile.TargetRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class of every TerraCraft enemy.
 * <ul>
 *     <li>Terraria stats come from its {@link MobDefinition} (see {@link TerrariaMobs}).</li>
 *     <li>Contact damage: touching the enemy hurts players (Expert/Master scaled, player defense applies).</li>
 *     <li>Terraria defense and coin value feed the damage pipeline and the economy.</li>
 *     <li>Health is the real Terraria life (bosses have tens of thousands): {@link #raiseHealthCap()} lifts
 *     Minecraft's 1024 limit on the max health attribute at startup.</li>
 *     <li>Dies the Terraria way: vanishes in a puff instead of falling over.</li>
 * </ul>
 * Families ({@link SlimeMob}, {@link WalkerMob}, {@link FlyerMob}) only implement movement.
 */
public abstract class TerrariaMob extends Monster implements HasTerrariaDefense, EconomyEvents.CoinValue, com.terracraft.entity.SpriteEntity {
    /** Highest max health an entity may have (Minecraft's own limit is 1024). */
    public static final double HEALTH_CAP = 10_000_000.0;
    private final MobDefinition definition;

    protected TerrariaMob(EntityType<? extends TerrariaMob> type, Level level) {
        super(type, level);
        this.definition = TerrariaMobs.definition(type);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes(MobDefinition definition) {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, definition.life())
            .add(Attributes.ATTACK_DAMAGE, definition.damage())
            .add(Attributes.MOVEMENT_SPEED, definition.moveSpeed())
            .add(Attributes.FOLLOW_RANGE, definition.followRange())
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0 - definition.knockbackTaken())
            .add(Attributes.ARMOR, 0.0);
    }

    public MobDefinition definition() {
        return definition;
    }

    /** Lifts Minecraft's 1024 cap on max health (called once at mod construction, on both sides). */
    public static void raiseHealthCap() {
        if (Attributes.MAX_HEALTH.value() instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged) {
            ranged.maxValue = HEALTH_CAP;
        }
    }

    public float terrariaLife() {
        return getHealth();
    }

    public float terrariaMaxLife() {
        return getMaxHealth();
    }

    /** Sets maximum life (Terraria units = Minecraft health) and refills it. */
    public void applyLife(float life) {
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(life);
        }
        setHealth(getMaxHealth());
    }

    @Override
    public int terrariaDefense() {
        return definition.defense();
    }

    @Override
    public long coinValue() {
        return definition.coinValue();
    }

    /** Contact damage after Expert/Master scaling. */
    public float contactDamage() {
        return definition.damage() * TerrariaDifficulty.enemyDamageMultiplier(level());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        applyLife(definition.life() * TerrariaDifficulty.enemyHealthMultiplier(level.getLevel()));
        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel serverLevel && isAlive()) {
            if (definition.damage() > 0 && dealsContactDamage()) {
                tickContactDamage(serverLevel);
            }
            if (definition.despawnsAtDay() && tickCount % 20 == 0 && serverLevel.isBrightOutside() && !isPersistenceRequired()
                && serverLevel.canSeeSky(blockPosition())) {
                leaveAtDawn(serverLevel);
            }
        }
    }

    /** Whether this entity counts as an enemy for the spawn cap (worm body segments do not). */
    public boolean countsTowardSpawnCap() {
        return true;
    }

    /** Whether touching the enemy hurts right now (e.g. not while a boss is teleporting). */
    protected boolean dealsContactDamage() {
        return true;
    }

    /** Nocturnal enemies quietly leave in daylight when no player is close (Terraria despawns them off-screen). */
    protected void leaveAtDawn(ServerLevel level) {
        if (level.getNearestPlayer(this, 20.0) == null || random.nextInt(30) == 0) {
            discard();
        }
    }

    /** Terraria contact damage: any player (or friendly entity) overlapping the enemy gets hurt. */
    protected void tickContactDamage(ServerLevel level) {
        List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.05),
            e -> e != this && e.isAlive() && (e instanceof Player p ? !p.isCreative() && !p.isSpectator() : TargetRules.isFriendlyToPlayers(e)));
        for (LivingEntity victim : touching) {
            if (hurtTarget(level, victim, contactDamage())) {
                onContactHit(victim);
            }
        }
    }

    /** Called after contact damage landed (flyers bounce off, for example). */
    protected void onContactHit(LivingEntity victim) {
    }

    /** Hurts a victim with Terraria enemy damage (the victim's defense applies in the damage pipeline). */
    public boolean hurtTarget(ServerLevel level, Entity victim, float damage) {
        return victim.hurtServer(level, TerraDamageTypes.source(level, TerraDamageTypes.ENEMY, this, this, TerraHit.enemy(1.0F)), damage);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = hurtTarget(level, target, contactDamage());
        if (hurt) {
            setLastHurtMob(target);
        }
        return hurt;
    }

    /**
     * Older saves kept health under 1000 with a "TerrariaLifeScale"; convert them to real life (the attributes and
     * health are already loaded at this point).
     */
    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        float oldScale = input.getFloatOr("TerrariaLifeScale", 1.0F);
        if (oldScale > 1.0F) {
            float fraction = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1.0F;
            AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() * oldScale);
            }
            setHealth(getMaxHealth() * fraction);
        }
    }

    /** Terraria enemies never take fall damage. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, net.minecraft.world.damagesource.DamageSource damageSource) {
        return false;
    }

    @Override
    protected void tickDeath() {
        if (++deathTime >= 4 && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }
}
