package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.BossContent;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Twins, summoned together with a Mechanical Eye at night.
 * <ul>
 *     <li>Retinazer hovers off to the player's side firing lasers and now and then charges. At 40% life it
 *     transforms (its iris opens into a laser cannon) and fires rapid volleys.</li>
 *     <li>Spazmatism charges like the Eye of Cthulhu and spits cursed flames. Transformed, it chases the player
 *     with a cursed-fire flamethrower between bursts of fast charges.</li>
 * </ul>
 * The fight (flag, Souls of Sight, Hallowed Bars) completes when the second twin dies. Both leave at daybreak.
 */
public class TheTwins extends TerrariaBoss {
    public static final int PHASE_EYE = 0;
    public static final int PHASE_TRANSFORM = 1;
    public static final int PHASE_MECH = 2;

    private static final int HOVER = 0;
    private static final int CHARGE = 1;
    private static final int TRANSFORM = 2;
    private static final int SPRAY = 3;

    private final boolean laser;
    private int charges;

    public TheTwins(EntityType<? extends TheTwins> type, Level level, boolean laser) {
        super(type, level, laser ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
        this.laser = laser;
        setNoGravity(true);
        noPhysics = true;
    }

    public static TheTwins retinazer(EntityType<? extends TheTwins> type, Level level) {
        return new TheTwins(type, level, true);
    }

    public static TheTwins spazmatism(EntityType<? extends TheTwins> type, Level level) {
        return new TheTwins(type, level, false);
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.TWINS;
    }

    @Override
    protected boolean fleesAtDay() {
        return true;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        if (laser && reason == EntitySpawnReason.EVENT) {
            // summoning Retinazer (Mechanical Eye, boss command) brings its twin along
            TheTwins twin = MobContent.SPAZMATISM.get().create(level.getLevel(), EntitySpawnReason.MOB_SUMMONED);
            if (twin != null) {
                twin.snapTo(getX() + 6, getY(), getZ() + 6, getYRot(), 0.0F);
                twin.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
                level.addFreshEntity(twin);
            }
        }
        return data;
    }

    @Override
    public int terrariaDefense() {
        // Terraria: 10 defense as eyes, 20 (Retinazer) / 28 (Spazmatism) once mechanical
        return phase() == PHASE_MECH ? (laser ? 20 : 28) : super.terrariaDefense();
    }

    @Override
    public float contactDamage() {
        return super.contactDamage() * (phase() == PHASE_MECH ? 1.5F : 1.0F);
    }

    @Override
    public String spriteVariant() {
        return phase() == PHASE_MECH ? "mouth" : "";
    }

    @Override
    public float spriteSpin(float partialTicks) {
        return phase() == PHASE_TRANSFORM ? (tickCount + partialTicks) * 40.0F : 0.0F;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player target = findTarget(level);
        if (isLeaving(level) || target == null) {
            Vec3 away = getDeltaMovement().scale(0.9).add(0.0, 0.08, 0.0);
            setDeltaMovement(away);
            move(MoverType.SELF, away);
            return;
        }
        setTarget(target);
        if (phase() == PHASE_EYE && getHealth() < getMaxHealth() * 0.4F) {
            setPhase(PHASE_TRANSFORM);
            setAiState(TRANSFORM);
            playSound(SoundEvents.IRON_GOLEM_DAMAGE, 3.0F, 0.5F);
        }
        boolean mech = phase() == PHASE_MECH;
        switch (aiState) {
            case TRANSFORM -> transform(level);
            case CHARGE -> charge(target, mech);
            case SPRAY -> spray(level, target);
            default -> hover(level, target, mech);
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    private void hover(ServerLevel level, Player target, boolean mech) {
        // Retinazer keeps to one side of the player, Spazmatism to the other
        double angle = (laser ? 0.0 : Math.PI) + aiTimer * 0.01;
        Vec3 goal = target.position().add(Math.cos(angle) * 10.0, 6.0, Math.sin(angle) * 10.0);
        Vec3 toward = goal.subtract(position());
        double speed = mech ? 0.7 : 0.5;
        Vec3 desired = toward.lengthSqr() > 1 ? toward.normalize().scale(Math.min(speed, toward.length() * 0.12)) : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().lerp(desired, 0.12));
        lookAt(target.getEyePosition());
        if (laser) {
            int every = mech ? 6 : 40;
            boolean volley = !mech || aiTimer % 120 < 60;
            if (volley && aiTimer % every == every - 1) {
                shoot(level, target, ProjectileKinds.MECH_LASER, mech ? 20.0F : 25.0F, 2.6F);
            }
            if (aiTimer > (mech ? 360 : 300)) {
                charges = 0;
                startCharge(target, mech);
            }
        } else {
            if (!mech && aiTimer % 50 == 49) {
                shoot(level, target, ProjectileKinds.CURSED_FLAME, 25.0F, 1.6F);
            }
            if (aiTimer > (mech ? 60 : 140)) {
                charges = 0;
                if (mech) {
                    setAiState(SPRAY);
                } else {
                    startCharge(target, false);
                }
            }
        }
    }

    private void spray(ServerLevel level, Player target) {
        // chase the player, breathing cursed fire
        Vec3 toward = target.getEyePosition().subtract(position());
        setDeltaMovement(getDeltaMovement().lerp(toward.normalize().scale(0.45), 0.1));
        lookAt(target.getEyePosition());
        if (aiTimer % 3 == 0 && toward.length() < 14) {
            shoot(level, target, ProjectileKinds.CURSED_SPRAY, 30.0F, 1.4F);
        }
        if (aiTimer > 120) {
            charges = 0;
            startCharge(target, true);
        }
    }

    private void startCharge(Player target, boolean mech) {
        setAiState(CHARGE);
        Vec3 direction = target.getEyePosition().subtract(position()).normalize();
        setDeltaMovement(direction.scale(mech ? 1.4 : 1.0));
        lookAt(position().add(direction));
        playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, mech ? 0.8F : 0.6F);
    }

    private void charge(Player target, boolean mech) {
        setDeltaMovement(getDeltaMovement().scale(0.975));
        lookAt(position().add(getDeltaMovement()));
        if (aiTimer > (mech ? 20 : 28)) {
            charges++;
            if (charges >= (laser ? 3 : mech ? 5 : 3)) {
                setAiState(HOVER);
            } else {
                startCharge(target, mech);
            }
        }
    }

    private void transform(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().scale(0.85));
        if (aiTimer % 6 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 8, 0.8, 0.8, 0.8, 0.05);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 6, 0.8, 0.8, 0.8, 0.2);
        }
        if (aiTimer >= 80) {
            setPhase(PHASE_MECH);
            setAiState(HOVER);
            playSound(SoundEvents.IRON_GOLEM_REPAIR, 3.0F, 0.6F);
        }
    }

    private void shoot(ServerLevel level, Player target, ProjectileKind kind, float terrariaDamage, float speed) {
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        Vec3 aim = target.getEyePosition().subtract(from).normalize();
        float damage = terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level);
        TerrariaProjectile.shoot(level, this, kind, from.add(aim.scale(1.2)), aim, speed * 4.0F, kind == ProjectileKinds.CURSED_SPRAY ? 4.0F : 0.5F,
            damage, DamageClass.GENERIC, 0, 1.0F);
        if (kind != ProjectileKinds.CURSED_SPRAY || aiTimer % 9 == 0) {
            playSound(laser ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BLAZE_SHOOT, 1.2F, laser ? 2.0F : 1.2F);
        }
    }

    private void lookAt(Vec3 point) {
        Vec3 d = point.subtract(getX(), getY() + getBbHeight() / 2, getZ());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setXRot((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected boolean completesFight(ServerLevel level) {
        return level.getEntitiesOfClass(TheTwins.class, getBoundingBox().inflate(200), twin -> twin != this && twin.isAlive()).isEmpty();
    }

    @Override
    public Component announceName() {
        return Component.translatable("entity.terracraft.the_twins");
    }

    @Override
    protected Item healingPotion() {
        return CoreItems.GREATER_HEALING_POTION.get();
    }

    @Override
    protected void dropFightLoot(ServerLevel level) {
        dropStack(level, BossContent.SOUL_OF_SIGHT.get(), 25 + random.nextInt(16));
        dropStack(level, BossContent.HALLOWED_BAR.get(), 15 + random.nextInt(16));
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }
}
