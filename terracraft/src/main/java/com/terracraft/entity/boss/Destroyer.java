package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.mob.WormMob;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.BossContent;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.MobContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Destroyer: a mechanical worm, summoned with a Mechanical Worm at night. All segments share the head's life
 * (one boss bar). It tunnels through terrain at the player; its body segments fire red lasers and, when struck,
 * release Probes (small flying drones that shoot lasers too). It leaves at daybreak.
 */
public class Destroyer extends WormMob {
    public static final Spec SPEC = new Spec(40, 1.5, 0.75, 0.06, true, true);

    private @Nullable ServerBossEvent bar;
    private boolean hasProbe = true;
    private int dayTicks;

    public Destroyer(EntityType<? extends Destroyer> type, Level level) {
        super(type, level, SPEC);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        float multiplier = switch (TerrariaDifficulty.of(level.getLevel())) {
            case CLASSIC -> 1.0F;
            case EXPERT -> 1.4F;
            case MASTER -> 1.68F;
        };
        applyLife(definition().life() * multiplier);
        return data;
    }

    @Override
    public int terrariaDefense() {
        return switch (part()) {
            case HEAD -> 0;
            case TAIL -> 35;
            default -> 30;
        };
    }

    @Override
    public float contactDamage() {
        float base = part() == HEAD ? 70 : part() == TAIL ? 30 : 40;
        return base * TerrariaDifficulty.enemyDamageMultiplier(level());
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public long coinValue() {
        return isHead() ? 120_000 : 0;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (isHead()) {
            headTick(level);
        } else if (target != null && target.isAlive() && random.nextInt(part() == TAIL ? 400 : 1200) == 0
            && distanceToSqr(target) < 40 * 40 && hasLineOfSight(target)) {
            Vec3 from = position().add(0, getBbHeight() / 2, 0);
            Vec3 aim = target.getEyePosition().subtract(from).normalize();
            TerrariaProjectile.shoot(level, this, ProjectileKinds.MECH_LASER, from, aim, 9.0F, 1.0F,
                22.0F * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 1.0F);
            playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 2.0F);
        }
    }

    private void headTick(ServerLevel level) {
        if (bar == null) {
            bar = new ServerBossEvent(java.util.UUID.randomUUID(), getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(this) < 160 * 160) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }
        }
        boolean noTarget = getTarget() == null || !getTarget().isAlive() || distanceToSqr(getTarget()) > 160 * 160;
        dayTicks = level.isBrightOutside() || noTarget ? dayTicks + 1 : 0;
        if (dayTicks > 0) {
            setTarget(null);   // dive away and despawn
        }
        if (dayTicks > 200) {
            discard();
        }
    }

    @Override
    protected void headAi(ServerLevel level) {
        if (dayTicks > 0) {
            Vec3 motion = getDeltaMovement().add(0, -0.08, 0);
            setDeltaMovement(motion);
            move(net.minecraft.world.entity.MoverType.SELF, motion);
            return;
        }
        super.headAi(level);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!isHead() && hasProbe && random.nextFloat() < 0.25F) {
            hasProbe = false;
            TerrariaMob probe = MobContent.PROBE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (probe != null) {
                probe.snapTo(getX(), getY() + 0.5, getZ(), random.nextFloat() * 360.0F, 0.0F);
                probe.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                probe.setTarget(getTarget());
                level.addFreshEntity(probe);
            }
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
            || source.is(net.minecraft.world.damagesource.DamageTypes.FALL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        if (isHead() && reason != RemovalReason.UNLOADED_TO_CHUNK && level() instanceof ServerLevel level) {
            // the Probes go with the Destroyer
            for (var probe : level.getEntities(MobContent.PROBE.get(), getBoundingBox().inflate(160), e -> true)) {
                probe.discard();
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!isHead() || !(level() instanceof ServerLevel level)) {
            return;
        }
        if (bar != null) {
            bar.removeAllPlayers();
        }
        if (isNoAi()) {
            return;   // a display, not a fight
        }
        if (bar != null) {
            bar.removeAllPlayers();
        }
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.terracraft.boss.defeated", getDisplayName())
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.DESTROYER);
        drop(level, CoreItems.GREATER_HEALING_POTION.get(), 5 + random.nextInt(11));
        drop(level, BossContent.SOUL_OF_MIGHT.get(), 25 + random.nextInt(16));
        drop(level, BossContent.HALLOWED_BAR.get(), 15 + random.nextInt(16));
    }

    private void drop(ServerLevel level, Item item, int count) {
        level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.5, getZ(), new ItemStack(item, count)));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Probe", hasProbe);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        hasProbe = input.getBooleanOr("Probe", true);
    }
}
