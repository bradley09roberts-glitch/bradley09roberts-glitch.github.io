package com.terracraft.registry;

import com.mojang.serialization.MapCodec;
import com.terracraft.TerraCraft;
import com.terracraft.config.ConfigCondition;
import net.neoforged.neoforge.common.conditions.ICondition;

/** JSON load conditions provided by TerraCraft. */
public final class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
        DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, TerraCraft.MODID);

    public static final RegistryObject<MapCodec<? extends ICondition>> CONFIG =
        CONDITIONS.register("config", () -> ConfigCondition.CODEC);

    private ModConditions() {}
}
