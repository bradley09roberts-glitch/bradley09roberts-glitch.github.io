package com.starforged.tempest;

import com.starforged.Starforged;
import com.starforged.tempest.boss.StormConductorEntity;
import com.starforged.tempest.boss.StormEyeEntity;
import com.starforged.tempest.boss.VeyrEntity;
import com.starforged.tempest.entity.ArcBeamEntity;
import com.starforged.tempest.entity.CycloneEntity;
import com.starforged.tempest.entity.ShardwingEntity;
import com.starforged.tempest.entity.StaticWispEntity;
import com.starforged.tempest.entity.StormRocEntity;
import com.starforged.tempest.entity.StormShardEntity;
import com.starforged.tempest.entity.StormboundEntity;
import com.starforged.tempest.entity.StormhookEntity;
import com.starforged.tempest.entity.TempestJavelinEntity;
import com.starforged.tempest.entity.ThunderjawAlphaEntity;
import com.starforged.tempest.entity.ThunderjawEntity;
import com.starforged.tempest.entity.ZephyrSpriteEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Creatures, projectiles and effects of the Tempestforged expansion. */
public final class TempestEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Starforged.MODID);

    // --- Creatures ---------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<StaticWispEntity>> STATIC_WISP = register("static_wisp",
        EntityType.Builder.of(StaticWispEntity::new, MobCategory.MONSTER).sized(0.6F, 0.6F).eyeHeight(0.3F).fireImmune().clientTrackingRange(10));
    public static final RegistryObject<EntityType<ShardwingEntity>> SHARDWING = register("shardwing",
        EntityType.Builder.of(ShardwingEntity::new, MobCategory.MONSTER).sized(1.4F, 0.9F).eyeHeight(0.5F).clientTrackingRange(12));
    public static final RegistryObject<EntityType<StormboundEntity>> STORMBOUND = register("stormbound",
        EntityType.Builder.of(StormboundEntity::new, MobCategory.MONSTER).sized(0.8F, 2.4F).eyeHeight(2.1F).fireImmune().clientTrackingRange(10));
    public static final RegistryObject<EntityType<ThunderjawEntity>> THUNDERJAW = register("thunderjaw",
        EntityType.Builder.of(ThunderjawEntity::new, MobCategory.MONSTER).sized(2.0F, 2.4F).eyeHeight(1.8F).clientTrackingRange(12));
    public static final RegistryObject<EntityType<ZephyrSpriteEntity>> ZEPHYR_SPRITE = register("zephyr_sprite",
        EntityType.Builder.of(ZephyrSpriteEntity::new, MobCategory.CREATURE).sized(0.5F, 0.8F).eyeHeight(0.6F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<StormRocEntity>> STORM_ROC = register("storm_roc",
        EntityType.Builder.of(StormRocEntity::new, MobCategory.CREATURE).sized(2.0F, 1.6F).eyeHeight(1.4F)
            .passengerAttachments(new Vec3(0.0, 1.55, 0.2)).clientTrackingRange(14));
    public static final RegistryObject<EntityType<ThunderjawAlphaEntity>> THUNDERJAW_ALPHA = register("thunderjaw_alpha",
        EntityType.Builder.of(ThunderjawAlphaEntity::new, MobCategory.MONSTER).sized(3.0F, 3.6F).eyeHeight(2.8F).fireImmune().clientTrackingRange(16));
    public static final RegistryObject<EntityType<VeyrEntity>> VEYR = register("veyr",
        EntityType.Builder.of(VeyrEntity::new, MobCategory.MONSTER).sized(1.4F, 3.8F).eyeHeight(3.3F).fireImmune().clientTrackingRange(16));

    // --- Boss props --------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<StormConductorEntity>> STORM_CONDUCTOR = register("storm_conductor",
        EntityType.Builder.<StormConductorEntity>of(StormConductorEntity::new, MobCategory.MISC).sized(1.2F, 3.0F).fireImmune().noLootTable()
            .clientTrackingRange(16));
    public static final RegistryObject<EntityType<StormEyeEntity>> STORM_EYE = register("storm_eye",
        EntityType.Builder.<StormEyeEntity>of(StormEyeEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune().noLootTable().noSave()
            .clientTrackingRange(16).updateInterval(2));

    // --- Projectiles & effects ----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<CycloneEntity>> CYCLONE = register("cyclone",
        EntityType.Builder.<CycloneEntity>of(CycloneEntity::new, MobCategory.MISC).sized(1.4F, 2.6F).fireImmune().noLootTable().noSave()
            .clientTrackingRange(10).updateInterval(1));
    public static final RegistryObject<EntityType<TempestJavelinEntity>> TEMPEST_JAVELIN = register("tempest_javelin",
        EntityType.Builder.<TempestJavelinEntity>of(TempestJavelinEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noLootTable()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<StormhookEntity>> STORMHOOK = register("stormhook",
        EntityType.Builder.<StormhookEntity>of(StormhookEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<StormShardEntity>> STORM_SHARD = register("storm_shard",
        EntityType.Builder.<StormShardEntity>of(StormShardEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<ArcBeamEntity>> ARC_BEAM = register("arc_beam",
        EntityType.Builder.<ArcBeamEntity>of(ArcBeamEntity::new, MobCategory.MISC).sized(0.2F, 0.2F).noLootTable().noSave()
            .clientTrackingRange(10).updateInterval(1));

    private static <T extends Entity> RegistryObject<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.build(ENTITIES.key(name)));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(STATIC_WISP.get(), StaticWispEntity.createAttributes().build());
        event.put(SHARDWING.get(), ShardwingEntity.createAttributes().build());
        event.put(STORMBOUND.get(), StormboundEntity.createAttributes().build());
        event.put(THUNDERJAW.get(), ThunderjawEntity.createAttributes().build());
        event.put(ZEPHYR_SPRITE.get(), ZephyrSpriteEntity.createAttributes().build());
        event.put(STORM_ROC.get(), StormRocEntity.createAttributes().build());
        event.put(THUNDERJAW_ALPHA.get(), ThunderjawAlphaEntity.createAttributes().build());
        event.put(VEYR.get(), VeyrEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(STATIC_WISP.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(SHARDWING.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(STORMBOUND.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(THUNDERJAW.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ZEPHYR_SPRITE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(STORM_ROC.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isSolidRender(), SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private TempestEntities() {
    }
}
