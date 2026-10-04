package com.terracraft.entity.boss;

import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.content.LunarContent;
import com.terracraft.world.event.CelestialEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A Celestial Pillar (Solar, Vortex, Nebula or Stardust): a towering monolith that stands where it landed. While its
 * shield holds it cannot be hurt; every creature of its kind killed near it weakens the shield, and once it is gone
 * the pillar can be destroyed. It drops a heap of its Celestial Fragments.
 */
public class CelestialPillar extends TerrariaMob {
    public enum Kind {
        SOLAR(BossEvent.BossBarColor.YELLOW, ChatFormatting.GOLD), VORTEX(BossEvent.BossBarColor.GREEN, ChatFormatting.AQUA),
        NEBULA(BossEvent.BossBarColor.PINK, ChatFormatting.LIGHT_PURPLE), STARDUST(BossEvent.BossBarColor.BLUE, ChatFormatting.BLUE);

        final BossEvent.BossBarColor color;
        final ChatFormatting chat;

        Kind(BossEvent.BossBarColor color, ChatFormatting chat) {
            this.color = color;
            this.chat = chat;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public ProgressionFlag flag() {
            return switch (this) {
                case SOLAR -> ProgressionFlags.PILLAR_SOLAR;
                case VORTEX -> ProgressionFlags.PILLAR_VORTEX;
                case NEBULA -> ProgressionFlags.PILLAR_NEBULA;
                case STARDUST -> ProgressionFlags.PILLAR_STARDUST;
            };
        }

        public RegistryObject<? extends Item> fragment() {
            return switch (this) {
                case SOLAR -> LunarContent.SOLAR_FRAGMENT;
                case VORTEX -> LunarContent.VORTEX_FRAGMENT;
                case NEBULA -> LunarContent.NEBULA_FRAGMENT;
                case STARDUST -> LunarContent.STARDUST_FRAGMENT;
            };
        }
    }

    public static final int SHIELD = 100;
    private static final EntityDataAccessor<Integer> DATA_SHIELD = SynchedEntityData.defineId(CelestialPillar.class, EntityDataSerializers.INT);
    private final Kind kind;
    private final ServerBossEvent bar;

    public CelestialPillar(EntityType<? extends CelestialPillar> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.bar = new ServerBossEvent(java.util.UUID.randomUUID(), getDisplayName(), kind.color, BossEvent.BossBarOverlay.NOTCHED_10);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHIELD, SHIELD);
    }

    public int shield() {
        return entityData.get(DATA_SHIELD);
    }

    /** One of its creatures fell nearby. */
    public void weaken(ServerLevel level) {
        int left = Math.max(0, shield() - 1);
        entityData.set(DATA_SHIELD, left);
        if (left == 0) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 5, getZ(), 2, 1, 2, 1, 0);
            playSound(SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.5F);
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("event.terracraft.pillar.shield_down", getDisplayName()).withStyle(kind.chat), false);
        }
    }

    @Override
    public String spriteVariant() {
        return shield() > 0 ? "shielded" : "";
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
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
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return shield() > 0 && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 10 == 0) {
            bar.setName(Component.translatable("event.terracraft.pillar.bar", getDisplayName(), shield() > 0
                ? Component.translatable("event.terracraft.pillar.shield", shield()) : Component.literal("")));
            bar.setProgress(shield() > 0 ? shield() / (float) SHIELD : getHealth() / getMaxHealth());
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(this) < 120 * 120) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }
            if (shield() > 0) {
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + random.nextDouble() * getBbHeight(), getZ(), 4, 2.5, 0.5, 2.5, 0.01);
            }
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        bar.removeAllPlayers();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && !isNoAi()) {
            int count = 20 + random.nextInt(11);
            for (int i = 0; i < count; i += 5) {
                ItemEntity drop = new ItemEntity(level, getX(), getY() + getBbHeight() * 0.5, getZ(), new ItemStack(kind.fragment().get(), Math.min(5, count - i)));
                drop.setDeltaMovement((random.nextDouble() - 0.5) * 0.6, 0.4, (random.nextDouble() - 0.5) * 0.6);
                level.addFreshEntity(drop);
            }
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.terracraft.boss.defeated", getDisplayName()).withStyle(kind.chat, ChatFormatting.BOLD), false);
            ProgressionManager.markDefeated(level.getServer(), kind.flag());
            CelestialEvents.pillarDestroyed(level, kind);
        }
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Shield", shield());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_SHIELD, input.getIntOr("Shield", SHIELD));
    }
}
