package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.item.TerraItemStats;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Data component types. Instances are created eagerly (static fields) because Forge registers items
 * before data components; items reference these objects in their default component maps, which are
 * resolved lazily after all registries are populated.
 */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TerraCraft.MODID);

    /** Terraria statistics of an item (damage, use time, powers, rarity, value...). */
    public static final DataComponentType<TerraItemStats> STATS = register("stats",
        DataComponentType.<TerraItemStats>builder().persistent(TerraItemStats.CODEC).networkSynchronized(TerraItemStats.STREAM_CODEC).cacheEncoding().build());

    /** Terraria prefix of a weapon, tool or accessory ("legendary", "warding"...), see {@code item.modifier.Modifiers}. */
    public static final DataComponentType<String> MODIFIER = register("modifier",
        DataComponentType.<String>builder().persistent(com.mojang.serialization.Codec.STRING)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8).build());

    private ModDataComponents() {}

    static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        COMPONENTS.register(name, () -> type);
        return type;
    }
}
