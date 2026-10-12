package com.grandline.activity;

import com.grandline.GrandLine;
import com.grandline.registry.ModCreativeTabs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registration of the sea activities (treasure maps, fishing, cooking): the data components, the fishing loot
 * modifier and the items (in {@link ActivityItems}, which registers into the shared item register).
 */
public final class ActivityRegistry {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, GrandLine.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, GrandLine.MODID);

    /** The secret a treasure map points at, and whether it has been found. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TreasureTarget>> TREASURE = COMPONENTS.register("treasure",
            () -> DataComponentType.<TreasureTarget>builder().persistent(TreasureTarget.CODEC).networkSynchronized(TreasureTarget.STREAM).build());
    /** Quality of a catch or a dish: 0 plain, 1 fine, 2 prime. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> QUALITY = COMPONENTS.register("quality",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Which half of a torn map (0 left, 1 right). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MAP_HALF = COMPONENTS.register("map_half",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 1)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /** Sea catches replace vanilla fish when fishing at sea (data/grandline/loot_modifiers/sea_fishing.json). */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<SeaFishing.Modifier>> SEA_FISHING =
            LOOT_MODIFIERS.register("sea_fishing", () -> SeaFishing.Modifier.CODEC);

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        ActivityItems.init();
        modBus.addListener(ActivityRegistry::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        for (var it : ActivityItems.ALL) e.accept(it.get());
    }

    private ActivityRegistry() {}
}
