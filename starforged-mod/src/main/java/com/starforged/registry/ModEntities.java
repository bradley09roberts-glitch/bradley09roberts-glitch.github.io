package com.starforged.registry;

import com.starforged.Starforged;
import com.starforged.entity.animal.NebulaRayEntity;
import com.starforged.entity.animal.StarlingEntity;
import com.starforged.entity.boss.EclipseCrystalEntity;
import com.starforged.entity.boss.EclipseSovereignEntity;
import com.starforged.entity.monster.AstralGolemEntity;
import com.starforged.entity.monster.AstralWraithEntity;
import com.starforged.entity.monster.MimicEntity;
import com.starforged.entity.monster.StarMiteEntity;
import com.starforged.entity.monster.VoidStalkerEntity;
import com.starforged.entity.projectile.EclipseWaveEntity;
import com.starforged.entity.projectile.MeteorEntity;
import com.starforged.entity.projectile.SingularityEntity;
import com.starforged.entity.projectile.StarboltEntity;
import com.starforged.entity.projectile.ThrownSingularityGrenade;
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

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Starforged.MODID);

    // --- Creatures ---------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<StarMiteEntity>> STAR_MITE = register("star_mite",
        EntityType.Builder.of(StarMiteEntity::new, MobCategory.MONSTER).sized(0.8F, 0.45F).eyeHeight(0.3F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<VoidStalkerEntity>> VOID_STALKER = register("void_stalker",
        EntityType.Builder.of(VoidStalkerEntity::new, MobCategory.MONSTER).sized(0.7F, 2.7F).eyeHeight(2.4F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<AstralGolemEntity>> ASTRAL_GOLEM = register("astral_golem",
        EntityType.Builder.of(AstralGolemEntity::new, MobCategory.MONSTER).sized(1.9F, 3.3F).eyeHeight(2.7F).fireImmune().clientTrackingRange(10));
    public static final RegistryObject<EntityType<MimicEntity>> MIMIC = register("mimic",
        EntityType.Builder.of(MimicEntity::new, MobCategory.MONSTER).sized(0.9F, 0.9F).eyeHeight(0.6F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<AstralWraithEntity>> ASTRAL_WRAITH = register("astral_wraith",
        EntityType.Builder.of(AstralWraithEntity::new, MobCategory.MONSTER).sized(0.7F, 2.1F).eyeHeight(1.75F).fireImmune().clientTrackingRange(8));
    public static final RegistryObject<EntityType<StarlingEntity>> STARLING = register("starling",
        EntityType.Builder.of(StarlingEntity::new, MobCategory.CREATURE).sized(0.55F, 0.55F).eyeHeight(0.32F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<NebulaRayEntity>> NEBULA_RAY = register("nebula_ray",
        EntityType.Builder.of(NebulaRayEntity::new, MobCategory.CREATURE).sized(3.2F, 0.9F).eyeHeight(0.5F)
            .passengerAttachments(new Vec3(0.0, 0.85, 0.1)).clientTrackingRange(12));

    // --- Boss --------------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<EclipseSovereignEntity>> ECLIPSE_SOVEREIGN = register("eclipse_sovereign",
        EntityType.Builder.of(EclipseSovereignEntity::new, MobCategory.MONSTER).sized(3.0F, 6.0F).eyeHeight(4.8F).fireImmune().clientTrackingRange(16));
    public static final RegistryObject<EntityType<EclipseCrystalEntity>> ECLIPSE_CRYSTAL = register("eclipse_crystal",
        EntityType.Builder.<EclipseCrystalEntity>of(EclipseCrystalEntity::new, MobCategory.MISC).sized(1.4F, 2.2F).fireImmune().noLootTable()
            .clientTrackingRange(16).updateInterval(1));

    // --- Projectiles & effects ----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<MeteorEntity>> METEOR = register("meteor",
        EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC).sized(1.2F, 1.2F).fireImmune().noLootTable().noSave()
            .clientTrackingRange(16).updateInterval(1));
    public static final RegistryObject<EntityType<StarboltEntity>> STARBOLT = register("starbolt",
        EntityType.Builder.<StarboltEntity>of(StarboltEntity::new, MobCategory.MISC).sized(0.4F, 0.4F).noLootTable()
            .clientTrackingRange(6).updateInterval(1));
    public static final RegistryObject<EntityType<EclipseWaveEntity>> ECLIPSE_WAVE = register("eclipse_wave",
        EntityType.Builder.<EclipseWaveEntity>of(EclipseWaveEntity::new, MobCategory.MISC).sized(2.4F, 0.6F).noLootTable()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<SingularityEntity>> SINGULARITY = register("singularity",
        EntityType.Builder.<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC).sized(1.2F, 1.2F).fireImmune().noLootTable()
            .clientTrackingRange(10).updateInterval(5));
    public static final RegistryObject<EntityType<ThrownSingularityGrenade>> THROWN_SINGULARITY = register("thrown_singularity",
        EntityType.Builder.<ThrownSingularityGrenade>of(ThrownSingularityGrenade::new, MobCategory.MISC).sized(0.25F, 0.25F).noLootTable()
            .clientTrackingRange(4).updateInterval(10));

    private static <T extends Entity> RegistryObject<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.build(ENTITIES.key(name)));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(STAR_MITE.get(), StarMiteEntity.createAttributes().build());
        event.put(VOID_STALKER.get(), VoidStalkerEntity.createAttributes().build());
        event.put(ASTRAL_GOLEM.get(), AstralGolemEntity.createAttributes().build());
        event.put(MIMIC.get(), MimicEntity.createAttributes().build());
        event.put(ASTRAL_WRAITH.get(), AstralWraithEntity.createAttributes().build());
        event.put(STARLING.get(), StarlingEntity.createAttributes().build());
        event.put(NEBULA_RAY.get(), NebulaRayEntity.createAttributes().build());
        event.put(ECLIPSE_SOVEREIGN.get(), EclipseSovereignEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(STAR_MITE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(VOID_STALKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ASTRAL_WRAITH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ASTRAL_GOLEM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MIMIC.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(NEBULA_RAY.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
            (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(STARLING.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
            (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private ModEntities() {
    }
}
