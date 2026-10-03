package com.terracraft.player;

import com.terracraft.config.TerraConfig;
import com.terracraft.player.stats.PlayerStats;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player Terraria state, attached to every player as a Forge capability and saved with the player.
 * <p>
 * Persistent: permanent upgrades (life crystals, life fruit, mana crystals, extra accessory slots),
 * current mana and the accessory inventory.
 * Runtime only: regeneration counters, the computed {@link PlayerStats} and sync bookkeeping.
 * <p>
 * All changes happen on the logical server; the owning client receives a
 * {@link com.terracraft.network.packet.SyncPlayerStatsPacket} mirror.
 */
public final class TerraPlayerData implements net.neoforged.neoforge.common.util.ValueIOSerializable {
    /** Hard cap on accessory slots (inventory capacity). The usable count comes from config + upgrades. */
    public static final int MAX_ACCESSORY_SLOTS = 10;

    private int lifeCrystals;
    private int lifeFruit;
    private int manaCrystals;
    private int extraAccessorySlots;
    private float mana = -1.0F;
    private boolean initialised;
    private final SimpleContainer accessories = new SimpleContainer(MAX_ACCESSORY_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            markStatsDirty();
        }
    };

    // ------------------------------------------------------------------ runtime state (not saved)
    /** Terraria regen counters, in Terraria ticks (60/s). */
    public int manaRegenDelay;
    public int manaRegenCount;
    public int lifeRegenTime;
    public int lifeRegenCount;
    /** Ticks a mana-sickness style "no mana regen" lockout remains (potions, etc.). */
    public int manaRegenLockout;
    /** Double jumps still available this airtime (refilled on landing). */
    public int doubleJumpsUsed;
    /** Remaining ticks of lava immunity (Lava Charm style budget, refills outside lava). */
    public int lavaImmunityTicks;
    private final PlayerStats stats = new PlayerStats();
    private boolean statsDirty = true;
    private boolean syncDirty = true;
    private int lastSyncedMana = Integer.MIN_VALUE;

    // ------------------------------------------------------------------ access

    /** Returns the data of a player. Never null for a living player; returns a detached instance otherwise. */
    public static TerraPlayerData get(Player player) {
        return player.getData(TerraAttachments.PLAYER_DATA);
    }

    @Nullable
    public static TerraPlayerData getOrNull(Player player) {
        return player.getData(TerraAttachments.PLAYER_DATA);
    }

    // ------------------------------------------------------------------ permanent upgrades

    public int lifeCrystals() {
        return lifeCrystals;
    }

    public void setLifeCrystals(int value) {
        lifeCrystals = Math.max(0, Math.min(value, TerraConfig.COMMON.maxLifeCrystals.get()));
        markStatsDirty();
    }

    public int lifeFruit() {
        return lifeFruit;
    }

    public void setLifeFruit(int value) {
        lifeFruit = Math.max(0, Math.min(value, TerraConfig.COMMON.maxLifeFruit.get()));
        markStatsDirty();
    }

    public int manaCrystals() {
        return manaCrystals;
    }

    public void setManaCrystals(int value) {
        manaCrystals = Math.max(0, Math.min(value, TerraConfig.COMMON.maxManaCrystals.get()));
        markStatsDirty();
    }

    public int extraAccessorySlots() {
        return extraAccessorySlots;
    }

    public void setExtraAccessorySlots(int value) {
        extraAccessorySlots = Math.max(0, Math.min(value, MAX_ACCESSORY_SLOTS));
        markStatsDirty();
    }

    /** Permanent maximum life before accessories/buffs (base + crystals + fruit). */
    public int baseMaxLife() {
        TerraConfig.Common c = TerraConfig.COMMON;
        return c.baseMaxLife.get() + lifeCrystals * c.lifeCrystalLife.get() + lifeFruit * c.lifeFruitLife.get();
    }

    /** Permanent maximum mana before accessories/buffs (base + crystals). */
    public int baseMaxMana() {
        TerraConfig.Common c = TerraConfig.COMMON;
        return c.baseMaxMana.get() + manaCrystals * c.manaCrystalMana.get();
    }

    // ------------------------------------------------------------------ mana

    public float mana() {
        return Math.max(0.0F, mana);
    }

    public boolean manaInitialised() {
        return mana >= 0.0F;
    }

    public void setMana(float value) {
        float clamped = Math.max(0.0F, Math.min(value, stats.maxMana));
        if (clamped != mana) {
            mana = clamped;
        }
    }

    public int maxMana() {
        return stats.maxMana;
    }

    // ------------------------------------------------------------------ accessories

    public SimpleContainer accessories() {
        return accessories;
    }

    /** Number of accessory slots this player can currently use. */
    public int usableAccessorySlots() {
        return Math.min(MAX_ACCESSORY_SLOTS, TerraConfig.COMMON.accessorySlots.get() + extraAccessorySlots);
    }

    // ------------------------------------------------------------------ stats

    public PlayerStats stats() {
        return stats;
    }

    public void markStatsDirty() {
        statsDirty = true;
        syncDirty = true;
    }

    public boolean consumeStatsDirty() {
        boolean dirty = statsDirty;
        statsDirty = false;
        return dirty;
    }

    public void markSyncDirty() {
        syncDirty = true;
    }

    /** Returns true if a sync packet should be sent now (state changed or mana moved by >= 1). */
    public boolean consumeSyncDirty() {
        int manaInt = (int) Math.floor(mana());
        boolean dirty = syncDirty || manaInt != lastSyncedMana;
        syncDirty = false;
        lastSyncedMana = manaInt;
        return dirty;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public void setInitialised(boolean initialised) {
        this.initialised = initialised;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void serialize(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("LifeCrystals", lifeCrystals);
        output.putInt("LifeFruit", lifeFruit);
        output.putInt("ManaCrystals", manaCrystals);
        output.putInt("ExtraAccessorySlots", extraAccessorySlots);
        output.putFloat("Mana", mana);
        output.putBoolean("Initialised", initialised);
        net.minecraft.world.level.storage.ValueOutput.ValueOutputList items = output.childrenList("Accessories");
        for (int slot = 0; slot < accessories.getContainerSize(); slot++) {
            ItemStack stack = accessories.getItem(slot);
            if (!stack.isEmpty()) {
                net.minecraft.world.level.storage.ValueOutput entry = items.addChild();
                entry.putInt("Slot", slot);
                entry.store("Item", ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    public void deserialize(net.minecraft.world.level.storage.ValueInput input) {
        lifeCrystals = input.getIntOr("LifeCrystals", 0);
        lifeFruit = input.getIntOr("LifeFruit", 0);
        manaCrystals = input.getIntOr("ManaCrystals", 0);
        extraAccessorySlots = input.getIntOr("ExtraAccessorySlots", 0);
        mana = input.getFloatOr("Mana", -1.0F);
        initialised = input.getBooleanOr("Initialised", false);
        accessories.clearContent();
        for (net.minecraft.world.level.storage.ValueInput entry : input.childrenListOrEmpty("Accessories")) {
            int slot = entry.getIntOr("Slot", -1);
            if (slot >= 0 && slot < accessories.getContainerSize()) {
                entry.read("Item", ItemStack.CODEC).ifPresent(stack -> accessories.setItem(slot, stack));
            }
        }
        markStatsDirty();
    }

    /** Copies persistent state from an old player entity (death / dimension change). */
    public void copyFrom(TerraPlayerData other, boolean death) {
        lifeCrystals = other.lifeCrystals;
        lifeFruit = other.lifeFruit;
        manaCrystals = other.manaCrystals;
        extraAccessorySlots = other.extraAccessorySlots;
        initialised = other.initialised;
        // Terraria keeps equipped accessories through death (softcore); mana refills on respawn.
        for (int slot = 0; slot < accessories.getContainerSize(); slot++) {
            accessories.setItem(slot, other.accessories.getItem(slot).copy());
        }
        mana = death ? -1.0F : other.mana;
        markStatsDirty();
    }
}
