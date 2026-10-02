package com.terracraft.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.crafting.conditions.ICondition;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Forge JSON condition {@code terracraft:config}: a data file is only loaded when a boolean config option
 * has the expected value.
 * <pre>
 * "forge:condition": { "type": "terracraft:config", "option": "disableDiamondGear", "value": false }
 * </pre>
 * Used to make vanilla recipe overrides (diamond gear, enchanting table, brewing stand...) follow the config.
 */
public record ConfigCondition(String option, boolean value) implements ICondition {
    public static final MapCodec<ConfigCondition> CODEC = RecordCodecBuilder.mapCodec(b -> b.group(
        Codec.STRING.fieldOf("option").forGetter(ConfigCondition::option),
        Codec.BOOL.optionalFieldOf("value", true).forGetter(ConfigCondition::value)
    ).apply(b, ConfigCondition::new));

    private static Map<String, Supplier<Boolean>> options() {
        TerraConfig.Common c = TerraConfig.COMMON;
        return Map.ofEntries(
            entry("disableVillagers", c.disableVillagers),
            entry("disableWanderingTraders", c.disableWanderingTraders),
            entry("disableVanillaHostileSpawns", c.disableVanillaHostileSpawns),
            entry("disableNether", c.disableNether),
            entry("disableEnd", c.disableEnd),
            entry("disableEnchanting", c.disableEnchanting),
            entry("disableVanillaBrewing", c.disableVanillaBrewing),
            entry("disableDiamondGear", c.disableDiamondGear),
            entry("disableNetheriteGear", c.disableNetheriteGear),
            entry("biomeSpread", c.biomeSpread)
        );
    }

    private static Map.Entry<String, Supplier<Boolean>> entry(String name, ForgeConfigSpec.BooleanValue value) {
        return Map.entry(name, value::get);
    }

    @Override
    public boolean test(IContext context, DynamicOps<?> ops) {
        Supplier<Boolean> supplier = options().get(option);
        if (supplier == null) {
            TerraCraft.LOGGER.warn("Unknown TerraCraft config option '{}' used in a data condition", option);
            return false;
        }
        return supplier.get() == value;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "terracraft:config(" + option + "=" + value + ")";
    }
}
