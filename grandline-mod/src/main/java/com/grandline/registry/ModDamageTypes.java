package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.combat.Element;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Data-driven damage types (JSON in data/grandline/damage_type). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> STRIKE = key("strike");
    public static final ResourceKey<DamageType> SLASH = key("slash");
    public static final ResourceKey<DamageType> PIERCE = key("pierce");
    public static final ResourceKey<DamageType> FIRE = key("fruit_fire");
    public static final ResourceKey<DamageType> ICE = key("fruit_ice");
    public static final ResourceKey<DamageType> LIGHTNING = key("fruit_lightning");
    public static final ResourceKey<DamageType> FRUIT = key("fruit");
    public static final ResourceKey<DamageType> HAKI = key("haki");
    public static final ResourceKey<DamageType> CANNON = key("cannon");
    public static final ResourceKey<DamageType> SEASTONE = key("seastone");
    public static final ResourceKey<DamageType> BULLET = key("bullet");

    private static ResourceKey<DamageType> key(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, GrandLine.id(n));
    }

    public static ResourceKey<DamageType> forElement(Element e) {
        return switch (e) {
            case SLASH -> SLASH;
            case PIERCE -> PIERCE;
            case FIRE, MAGMA -> FIRE;
            case ICE -> ICE;
            case LIGHTNING, LIGHT -> LIGHTNING;
            case PHYSICAL, BLUNT, IMPACT -> STRIKE;
            default -> FRUIT;
        };
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, attacker);
    }

    private ModDamageTypes() {}
}
