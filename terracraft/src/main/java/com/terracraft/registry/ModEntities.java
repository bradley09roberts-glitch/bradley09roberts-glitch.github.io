package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity types. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TerraCraft.MODID);

    /** Generic data-driven projectile (see ProjectileKinds). */
    public static final RegistryObject<EntityType<TerrariaProjectile>> PROJECTILE = ENTITY_TYPES.register("projectile",
        () -> EntityType.Builder.<TerrariaProjectile>of(TerrariaProjectile::new, MobCategory.MISC)
            .sized(0.3F, 0.3F)
            .clientTrackingRange(6)
            .updateInterval(2)
            .build(ENTITY_TYPES.key("projectile")));

    private ModEntities() {}
}
