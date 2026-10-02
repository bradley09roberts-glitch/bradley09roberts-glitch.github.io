package com.terracraft.registry;

import com.mojang.serialization.MapCodec;
import com.terracraft.TerraCraft;
import com.terracraft.config.ConfigCondition;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge JSON load conditions provided by TerraCraft. */
public final class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
        DeferredRegister.create(ForgeRegistries.Keys.CONDITION_SERIALIZERS, TerraCraft.MODID);

    public static final RegistryObject<MapCodec<? extends ICondition>> CONFIG =
        CONDITIONS.register("config", () -> ConfigCondition.CODEC);

    private ModConditions() {}
}
