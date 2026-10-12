package com.grandline.registry;

import com.grandline.GrandLine;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, GrandLine.MODID);

    /** Devil Fruit id carried by a fruit item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FRUIT = TYPES.register("fruit",
            () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    /** Island id an Eternal Pose points to / a Log Pose is locked on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> POSE_TARGET = TYPES.register("pose_target",
            () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    /** Player UUID a Vivre Card points to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> VIVRE = TYPES.register("vivre",
            () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    /** Stored impact/flame charge of a Dial. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DIAL_CHARGE = TYPES.register("dial_charge",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Style id taught by a style scroll. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STYLE = TYPES.register("style",
            () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    /** Ship class id on a ship deed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SHIP_CLASS = TYPES.register("ship_class",
            () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private ModDataComponents() {}
}
