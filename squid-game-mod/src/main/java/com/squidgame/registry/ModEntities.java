package com.squidgame.registry;

import com.squidgame.SquidGameMod;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.DollEntity;
import com.squidgame.entity.GuardEntity;
import com.squidgame.entity.MarbleProjectile;
import com.squidgame.entity.RopeEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<ContestantEntity> CONTESTANT = register("contestant",
            EntityType.Builder.<ContestantEntity>of(ContestantEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(9).updateInterval(2).eyeHeight(1.62f));

    public static final EntityType<GuardEntity> GUARD = register("guard",
            EntityType.Builder.<GuardEntity>of(GuardEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.9f).clientTrackingRange(10).updateInterval(2).eyeHeight(1.7f));

    public static final EntityType<DollEntity> DOLL = register("doll",
            EntityType.Builder.<DollEntity>of(DollEntity::new, MobCategory.MISC)
                    .sized(2.4f, 9.0f).clientTrackingRange(24).updateInterval(5).fireImmune().eyeHeight(8.0f));

    public static final EntityType<RopeEntity> ROPE = register("rope",
            EntityType.Builder.<RopeEntity>of(RopeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(2).fireImmune());

    public static final EntityType<MarbleProjectile> MARBLE = register("marble",
            EntityType.Builder.<MarbleProjectile>of(MarbleProjectile::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(2));

    private ModEntities() {
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String id, EntityType.Builder<T> b) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SquidGameMod.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(id));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(CONTESTANT, ContestantEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GUARD, GuardEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DOLL, DollEntity.createAttributes());
    }
}
