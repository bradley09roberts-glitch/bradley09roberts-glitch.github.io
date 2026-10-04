package com.squidgame.tournament;

import com.squidgame.SquidGameMod;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything about a player that the tournament changes, so it can be restored exactly when they leave or the
 * tournament ends (even after a crash: snapshots are persisted with the tournament data).
 */
public final class PlayerSnapshot {
    private final CompoundTag data;

    private PlayerSnapshot(CompoundTag data) {
        this.data = data;
    }

    public static PlayerSnapshot load(CompoundTag tag) {
        return new PlayerSnapshot(tag);
    }

    public CompoundTag save() {
        return data;
    }

    public static PlayerSnapshot capture(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        t.putFloat("pitch", p.getXRot());
        t.putInt("gamemode", p.gameMode.getGameModeForPlayer().getId());
        t.putInt("prevgamemode", p.gameMode.getPreviousGameModeForPlayer() == null ? -1 : p.gameMode.getPreviousGameModeForPlayer().getId());
        ListTag inv = new ListTag();
        p.getInventory().save(inv);
        t.put("inventory", inv);
        t.putInt("slot", p.getInventory().selected);
        t.putInt("xpLevel", p.experienceLevel);
        t.putFloat("xpProgress", p.experienceProgress);
        t.putInt("xpTotal", p.totalExperience);
        t.putFloat("health", p.getHealth());
        t.putInt("food", p.getFoodData().getFoodLevel());
        t.putFloat("saturation", p.getFoodData().getSaturationLevel());
        CompoundTag abilities = new CompoundTag();
        p.getAbilities().addSaveData(abilities);
        t.put("abilities", abilities);
        ListTag effects = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) {
            DataResult<Tag> r = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, e);
            r.result().ifPresent(effects::add);
        }
        t.put("effects", effects);
        BlockPos respawn = p.getRespawnPosition();
        if (respawn != null) {
            t.putInt("rx", respawn.getX());
            t.putInt("ry", respawn.getY());
            t.putInt("rz", respawn.getZ());
            t.putString("rdim", p.getRespawnDimension().location().toString());
            t.putFloat("rangle", p.getRespawnAngle());
            t.putBoolean("rforced", p.isRespawnForced());
        }
        return new PlayerSnapshot(t);
    }

    /** Restores the captured state and teleports the player back. */
    public void restore(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        p.getInventory().clearContent();
        p.getInventory().load(data.getList("inventory", Tag.TAG_COMPOUND));
        p.getInventory().selected = data.getInt("slot");
        p.experienceLevel = data.getInt("xpLevel");
        p.experienceProgress = data.getFloat("xpProgress");
        p.totalExperience = data.getInt("xpTotal");
        p.removeAllEffects();
        ListTag effects = data.getList("effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance.CODEC.parse(NbtOps.INSTANCE, effects.get(i)).result().ifPresent(p::addEffect);
        }
        p.getAbilities().loadSaveData(data.getCompound("abilities"));
        p.onUpdateAbilities();
        p.setHealth(Math.max(1f, data.getFloat("health")));
        p.getFoodData().setFoodLevel(data.getInt("food"));
        p.getFoodData().setSaturation(data.getFloat("saturation"));
        p.setRemainingFireTicks(0);
        p.fallDistance = 0;
        p.setInvulnerable(false);
        if (data.contains("rx")) {
            ResourceKey<Level> rdim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.getString("rdim")));
            p.setRespawnPosition(rdim, new BlockPos(data.getInt("rx"), data.getInt("ry"), data.getInt("rz")),
                    data.getFloat("rangle"), data.getBoolean("rforced"), false);
        } else {
            p.setRespawnPosition(Level.OVERWORLD, null, 0f, false, false);
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.getString("dim")));
        ServerLevel level = server.getLevel(dim);
        if (level == null) {
            level = server.overworld();
        }
        p.teleportTo(level, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"), data.getFloat("yaw"), data.getFloat("pitch"));
        GameType gt = GameType.byId(data.getInt("gamemode"));
        p.setGameMode(gt);
        SquidGameMod.LOGGER.info("Restored {} to {}", p.getGameProfile().getName(), level.dimension().location());
    }

    /** Positions are kept in case the home dimension vanished. */
    public List<Double> position() {
        List<Double> l = new ArrayList<>();
        l.add(data.getDouble("x"));
        l.add(data.getDouble("y"));
        l.add(data.getDouble("z"));
        return l;
    }
}
