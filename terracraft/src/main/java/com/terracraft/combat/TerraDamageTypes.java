package com.terracraft.combat;

import com.terracraft.TerraCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** TerraCraft damage types (defined as JSON in data/terracraft/damage_type). */
public final class TerraDamageTypes {
    /** Player projectiles (arrows, bullets, thrown weapons, magic bolts). */
    public static final ResourceKey<DamageType> PROJECTILE = key("projectile");
    /** Magic projectiles and spells. */
    public static final ResourceKey<DamageType> MAGIC = key("magic");
    /** Enemy contact damage. */
    public static final ResourceKey<DamageType> ENEMY = key("enemy");
    /** Enemy projectiles. */
    public static final ResourceKey<DamageType> ENEMY_PROJECTILE = key("enemy_projectile");

    private TerraDamageTypes() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, TerraCraft.id(name));
    }

    public static TerraDamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity cause, TerraHit hit) {
        return new TerraDamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, cause, hit);
    }
}
