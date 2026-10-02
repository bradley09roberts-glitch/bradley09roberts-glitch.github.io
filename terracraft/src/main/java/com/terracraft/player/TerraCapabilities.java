package com.terracraft.player;

import com.terracraft.TerraCraft;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;

/** Capability definitions and attachment. */
public final class TerraCapabilities {
    public static final Capability<TerraPlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Identifier PLAYER_DATA_ID = TerraCraft.id("player_data");

    private TerraCapabilities() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), TerraCapabilities.class);
    }

    @SubscribeEvent
    static void attachEntityCapabilities(AttachCapabilitiesEvent.Entities event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PLAYER_DATA_ID, new PlayerDataProvider());
        }
    }

    /** Serializable provider wrapping one {@link TerraPlayerData}. */
    private static final class PlayerDataProvider implements ICapabilitySerializable<CompoundTag> {
        private final TerraPlayerData data = new TerraPlayerData();
        private final LazyOptional<TerraPlayerData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PLAYER_DATA.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registryAccess) {
            return data.save(registryAccess);
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registryAccess, CompoundTag nbt) {
            data.load(registryAccess, nbt);
        }
    }
}
