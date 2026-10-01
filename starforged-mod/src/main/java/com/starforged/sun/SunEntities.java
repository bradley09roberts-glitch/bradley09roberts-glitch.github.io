package com.starforged.sun;

import com.starforged.Starforged;
import com.starforged.sun.boss.SolarPylonEntity;
import com.starforged.sun.boss.SunWardenEntity;
import com.starforged.sun.entity.AshenKnightEntity;
import com.starforged.sun.entity.CinderChakramEntity;
import com.starforged.sun.entity.CinderImpEntity;
import com.starforged.sun.entity.EmberHoundEntity;
import com.starforged.sun.entity.HeliosOrbEntity;
import com.starforged.sun.entity.MagmaCrawlerEntity;
import com.starforged.sun.entity.SolarFlareEntity;
import com.starforged.sun.entity.SolarPhoenixEntity;
import com.starforged.sun.entity.SunburstFlaskEntity;
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

/** Creatures, projectiles and effects of the Sunforged expansion. */
public final class SunEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Starforged.MODID);

    // --- Creatures ---------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<CinderImpEntity>> CINDER_IMP = register("cinder_imp",
        EntityType.Builder.of(CinderImpEntity::new, MobCategory.MONSTER).sized(0.6F, 0.95F).eyeHeight(0.7F).fireImmune().clientTrackingRange(8));
    public static final RegistryObject<EntityType<MagmaCrawlerEntity>> MAGMA_CRAWLER = register("magma_crawler",
        EntityType.Builder.of(MagmaCrawlerEntity::new, MobCategory.MONSTER).sized(1.3F, 0.7F).eyeHeight(0.5F).fireImmune().clientTrackingRange(8));
    public static final RegistryObject<EntityType<EmberHoundEntity>> EMBER_HOUND = register("ember_hound",
        EntityType.Builder.of(EmberHoundEntity::new, MobCategory.MONSTER).sized(0.7F, 0.9F).eyeHeight(0.7F).fireImmune().clientTrackingRange(10));
    public static final RegistryObject<EntityType<AshenKnightEntity>> ASHEN_KNIGHT = register("ashen_knight",
        EntityType.Builder.of(AshenKnightEntity::new, MobCategory.MONSTER).sized(0.8F, 2.3F).eyeHeight(2.0F).fireImmune().clientTrackingRange(10));
    public static final RegistryObject<EntityType<SolarPhoenixEntity>> SOLAR_PHOENIX = register("solar_phoenix",
        EntityType.Builder.of(SolarPhoenixEntity::new, MobCategory.CREATURE).sized(1.8F, 1.3F).eyeHeight(1.0F).fireImmune()
            .passengerAttachments(new Vec3(0.0, 1.15, 0.0)).clientTrackingRange(12));

    // --- Boss --------------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<SunWardenEntity>> SUN_WARDEN = register("sun_warden",
        EntityType.Builder.of(SunWardenEntity::new, MobCategory.MONSTER).sized(2.6F, 6.4F).eyeHeight(5.6F).fireImmune().clientTrackingRange(16));
    public static final RegistryObject<EntityType<SolarPylonEntity>> SOLAR_PYLON = register("solar_pylon",
        EntityType.Builder.<SolarPylonEntity>of(SolarPylonEntity::new, MobCategory.MISC).sized(1.2F, 3.4F).fireImmune().noLootTable()
            .clientTrackingRange(16).updateInterval(2));

    // --- Projectiles & effects ----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<SolarFlareEntity>> SOLAR_FLARE = register("solar_flare",
        EntityType.Builder.<SolarFlareEntity>of(SolarFlareEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).fireImmune().noLootTable()
            .clientTrackingRange(10).updateInterval(1));
    public static final RegistryObject<EntityType<CinderChakramEntity>> CINDER_CHAKRAM = register("cinder_chakram",
        EntityType.Builder.<CinderChakramEntity>of(CinderChakramEntity::new, MobCategory.MISC).sized(0.8F, 0.3F).fireImmune().noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<SunburstFlaskEntity>> SUNBURST_FLASK = register("sunburst_flask",
        EntityType.Builder.<SunburstFlaskEntity>of(SunburstFlaskEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).noLootTable()
            .clientTrackingRange(4).updateInterval(10));
    public static final RegistryObject<EntityType<HeliosOrbEntity>> HELIOS_ORB = register("helios_orb",
        EntityType.Builder.<HeliosOrbEntity>of(HeliosOrbEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).fireImmune().noLootTable()
            .clientTrackingRange(10).updateInterval(1));

    private static <T extends Entity> RegistryObject<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.build(ENTITIES.key(name)));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CINDER_IMP.get(), CinderImpEntity.createAttributes().build());
        event.put(MAGMA_CRAWLER.get(), MagmaCrawlerEntity.createAttributes().build());
        event.put(EMBER_HOUND.get(), EmberHoundEntity.createAttributes().build());
        event.put(ASHEN_KNIGHT.get(), AshenKnightEntity.createAttributes().build());
        event.put(SOLAR_PHOENIX.get(), SolarPhoenixEntity.createAttributes().build());
        event.put(SUN_WARDEN.get(), SunWardenEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(CINDER_IMP.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MAGMA_CRAWLER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(EMBER_HOUND.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isSolidRender(), SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ASHEN_KNIGHT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(SOLAR_PHOENIX.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
            (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private SunEntities() {
    }
}
