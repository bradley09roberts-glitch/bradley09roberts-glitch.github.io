package com.terracraft.npc;

import com.terracraft.combat.HasTerrariaDefense;
import com.terracraft.combat.DamageClass;
import com.terracraft.entity.SpriteEntity;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TargetRules;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Terraria town NPC: friendly, cannot be hurt by players, lives in a house (goes home at night, wanders
 * around it by day), stops to talk when right-clicked, and defends itself against nearby enemies.
 * Arrival, death and respawn are handled by {@link NpcManager}.
 */
public class TownNpc extends PathfinderMob implements TargetRules.FriendlyToPlayers, SpriteEntity, HasTerrariaDefense {
    public static final int LIFE = 250;
    public static final int DEFENSE = 15;

    private String npcName = "";
    private @Nullable BlockPos house;
    private @Nullable UUID talkingTo;
    private int attackCooldown;

    public TownNpc(EntityType<? extends TownNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        getNavigation().setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, LIFE)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.FOLLOW_RANGE, 24.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    public TownNpcType npcType() {
        return TownNpcs.byEntity(getType());
    }

    public String npcName() {
        return npcName;
    }

    public void setNpcName(String name) {
        this.npcName = name;
        // nameless NPCs (the Old Man) are called by their role only
        setCustomName(name.isEmpty() ? Component.translatable(npcType().roleKey())
            : Component.translatable("npc.terracraft.name_format", name, Component.translatable(npcType().roleKey())));
    }

    public @Nullable BlockPos house() {
        return house;
    }

    public void setHouse(@Nullable BlockPos house) {
        this.house = house;
    }

    public boolean isTalkingTo(Player player) {
        return player.getUUID().equals(talkingTo);
    }

    public void startTalking(ServerPlayer player) {
        talkingTo = player.getUUID();
        getNavigation().stop();
    }

    public void stopTalking() {
        talkingTo = null;
    }

    @Override
    public int terrariaDefense() {
        return DEFENSE;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TalkGoal());
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new GoHomeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && isAlive()) {
            NpcManager.openChat(serverPlayer, this, null);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (talkingTo != null) {
            Player player = level.getPlayerByUUID(talkingTo);
            if (player == null || player.distanceToSqr(this) > 64) {
                talkingTo = null;
            }
        }
        if (tickCount % 40 == 0) {
            // wander around the house by day, stay inside at night
            if (house != null) {
                setHomeTo(house, level.isDarkOutside() ? 3 : 14);
            } else {
                clearHome();
            }
        }
        if (TownNpcs.isBound(npcType())) {
            // tied up: navigation already ran this tick, so also cancel the move it queued and any drift
            getNavigation().stop();
            getMoveControl().setWait();
            setZza(0.0F);
            setXxa(0.0F);
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        } else {
            defend(level);
        }
    }

    /** Terraria town NPCs throw or shoot their weapon at enemies that come close. */
    private void defend(ServerLevel level) {
        TownNpcType type = npcType();
        if (type.attack() == null) {
            return;
        }
        List<TerrariaMob> enemies = level.getEntitiesOfClass(TerrariaMob.class, getBoundingBox().inflate(10.0), e -> e.isAlive() && hasLineOfSight(e));
        if (enemies.isEmpty()) {
            return;
        }
        TerrariaMob enemy = enemies.get(0);
        ProjectileKind kind = type.attack().get();
        Vec3 from = getEyePosition();
        Vec3 aim = enemy.getBoundingBox().getCenter().subtract(from).add(0, from.distanceTo(enemy.position()) * 0.04, 0);
        getLookControl().setLookAt(enemy);
        TerrariaProjectile.shoot(level, this, kind, from, aim.normalize(), 10.0F, 2.0F, type.attackDamage(), DamageClass.RANGED, 0, 2.0F);
        attackCooldown = 25 + random.nextInt(15);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        // Players cannot hurt town NPCs (creative players can, for testing).
        if (source.getEntity() instanceof Player player && !player.isCreative()) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            NpcManager.onDeath(level, this, source);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean showsHealthBar() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("NpcName", npcName);
        if (house != null) {
            output.putLong("House", house.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        npcName = input.getStringOr("NpcName", "");
        Optional<Long> home = input.getLong("House");
        house = home.map(BlockPos::of).orElse(null);
    }

    public String typeId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
    }

    /** Stands still and faces the player while its chat window is open. */
    private class TalkGoal extends Goal {
        TalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return talkingTo != null;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            Player player = level().getPlayerByUUID(talkingTo);
            if (player != null) {
                getLookControl().setLookAt(player);
            }
        }
    }

    /** Walks back into its house at night (or when it strayed too far). */
    private class GoHomeGoal extends Goal {
        GoHomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (house == null) {
                return false;
            }
            double distance = house.distToCenterSqr(position());
            return level().isDarkOutside() ? distance > 4 : distance > 24 * 24;
        }

        @Override
        public void start() {
            PathNavigation navigation = getNavigation();
            navigation.moveTo(house.getX() + 0.5, house.getY(), house.getZ() + 0.5, 0.8);
        }

        @Override
        public boolean canContinueToUse() {
            return house != null && !getNavigation().isDone() && house.distToCenterSqr(position()) > 2;
        }
    }
}
