package com.starforged.registry;

import com.starforged.Starforged;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Data-driven damage types (see data/starforged/damage_type). They give the mod its own death messages,
 * e.g. "Steve was flattened by a falling star".
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> METEOR = key("meteor");
    public static final ResourceKey<DamageType> ECLIPSE_BEAM = key("eclipse_beam");
    public static final ResourceKey<DamageType> SINGULARITY = key("singularity");
    public static final ResourceKey<DamageType> STARLIGHT = key("starlight");
    public static final ResourceKey<DamageType> VOID_REND = key("void_rend");
    public static final ResourceKey<DamageType> SHOCKWAVE = key("shockwave");
    public static final ResourceKey<DamageType> SUNFIRE = key("sunfire");
    public static final ResourceKey<DamageType> SOLAR_BEAM = key("solar_beam");
    public static final ResourceKey<DamageType> MOONLIGHT = key("moonlight");
    public static final ResourceKey<DamageType> GRAVITY = key("gravity");
    public static final ResourceKey<DamageType> STORM = key("storm");
    public static final ResourceKey<DamageType> GALE = key("gale");

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, causing);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity causing) {
        return source(level, type, causing, causing);
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Starforged.id(name));
    }

    private ModDamageTypes() {
    }
}
