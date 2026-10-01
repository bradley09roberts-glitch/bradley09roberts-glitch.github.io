package com.starforged.moon;

import com.starforged.Starforged;
import com.starforged.moon.boss.LunarAnchorEntity;
import com.starforged.moon.boss.MatriarchEchoEntity;
import com.starforged.moon.boss.PaleMatriarchEntity;
import com.starforged.moon.entity.CrescentGlaiveEntity;
import com.starforged.moon.entity.FallingMoonEntity;
import com.starforged.moon.entity.LunarMothEntity;
import com.starforged.moon.entity.MoonkitEntity;
import com.starforged.moon.entity.MoonleaperEntity;
import com.starforged.moon.entity.MoonletEntity;
import com.starforged.moon.entity.MoonshotBoltEntity;
import com.starforged.moon.entity.RegolithSkimmerEntity;
import com.starforged.moon.entity.SeleniteSentinelEntity;
import com.starforged.moon.entity.TetherHookEntity;
import com.starforged.moon.entity.TideWaveEntity;
import com.starforged.moon.entity.UmbralLurkerEntity;
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

/** Creatures, projectiles and effects of the Moonforged expansion. */
public final class MoonEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Starforged.MODID);

    // --- Creatures ---------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<RegolithSkimmerEntity>> REGOLITH_SKIMMER = register("regolith_skimmer",
        EntityType.Builder.of(RegolithSkimmerEntity::new, MobCategory.MONSTER).sized(1.6F, 0.6F).eyeHeight(0.4F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<LunarMothEntity>> LUNAR_MOTH = register("lunar_moth",
        EntityType.Builder.of(LunarMothEntity::new, MobCategory.MONSTER).sized(1.0F, 0.9F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<SeleniteSentinelEntity>> SELENITE_SENTINEL = register("selenite_sentinel",
        EntityType.Builder.of(SeleniteSentinelEntity::new, MobCategory.MONSTER).sized(1.2F, 2.6F).eyeHeight(2.2F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<UmbralLurkerEntity>> UMBRAL_LURKER = register("umbral_lurker",
        EntityType.Builder.of(UmbralLurkerEntity::new, MobCategory.MONSTER).sized(0.7F, 2.4F).eyeHeight(2.1F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<MoonkitEntity>> MOONKIT = register("moonkit",
        EntityType.Builder.of(MoonkitEntity::new, MobCategory.CREATURE).sized(0.6F, 0.8F).eyeHeight(0.65F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<MoonleaperEntity>> MOONLEAPER = register("moonleaper",
        EntityType.Builder.of(MoonleaperEntity::new, MobCategory.CREATURE).sized(1.4F, 1.7F).eyeHeight(1.5F)
            .passengerAttachments(new Vec3(0.0, 1.45, 0.1)).clientTrackingRange(12));

    // --- Boss --------------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<PaleMatriarchEntity>> PALE_MATRIARCH = register("pale_matriarch",
        EntityType.Builder.of(PaleMatriarchEntity::new, MobCategory.MONSTER).sized(2.0F, 4.6F).eyeHeight(3.9F).fireImmune().clientTrackingRange(16));
    public static final RegistryObject<EntityType<MatriarchEchoEntity>> MATRIARCH_ECHO = register("matriarch_echo",
        EntityType.Builder.of(MatriarchEchoEntity::new, MobCategory.MONSTER).sized(2.0F, 4.6F).eyeHeight(3.9F).fireImmune().noLootTable()
            .clientTrackingRange(16));
    public static final RegistryObject<EntityType<LunarAnchorEntity>> LUNAR_ANCHOR = register("lunar_anchor",
        EntityType.Builder.<LunarAnchorEntity>of(LunarAnchorEntity::new, MobCategory.MISC).sized(1.2F, 3.2F).fireImmune().noLootTable()
            .clientTrackingRange(16).updateInterval(2));
    public static final RegistryObject<EntityType<FallingMoonEntity>> FALLING_MOON = register("falling_moon",
        EntityType.Builder.<FallingMoonEntity>of(FallingMoonEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune().noLootTable().noSave()
            .clientTrackingRange(20).updateInterval(1));

    // --- Projectiles & effects ----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<MoonletEntity>> MOONLET = register("moonlet",
        EntityType.Builder.<MoonletEntity>of(MoonletEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).fireImmune().noLootTable()
            .clientTrackingRange(10).updateInterval(1));
    public static final RegistryObject<EntityType<CrescentGlaiveEntity>> CRESCENT_GLAIVE = register("crescent_glaive",
        EntityType.Builder.<CrescentGlaiveEntity>of(CrescentGlaiveEntity::new, MobCategory.MISC).sized(1.0F, 0.3F).noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<MoonshotBoltEntity>> MOONSHOT_BOLT = register("moonshot_bolt",
        EntityType.Builder.<MoonshotBoltEntity>of(MoonshotBoltEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<TetherHookEntity>> TETHER_HOOK = register("tether_hook",
        EntityType.Builder.<TetherHookEntity>of(TetherHookEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));
    public static final RegistryObject<EntityType<TideWaveEntity>> TIDE_WAVE = register("tide_wave",
        EntityType.Builder.<TideWaveEntity>of(TideWaveEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).noLootTable().noSave()
            .clientTrackingRange(8).updateInterval(1));

    private static <T extends Entity> RegistryObject<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.build(ENTITIES.key(name)));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(REGOLITH_SKIMMER.get(), RegolithSkimmerEntity.createAttributes().build());
        event.put(LUNAR_MOTH.get(), LunarMothEntity.createAttributes().build());
        event.put(SELENITE_SENTINEL.get(), SeleniteSentinelEntity.createAttributes().build());
        event.put(UMBRAL_LURKER.get(), UmbralLurkerEntity.createAttributes().build());
        event.put(MOONKIT.get(), MoonkitEntity.createAttributes().build());
        event.put(MOONLEAPER.get(), MoonleaperEntity.createAttributes().build());
        event.put(PALE_MATRIARCH.get(), PaleMatriarchEntity.createAttributes().build());
        event.put(MATRIARCH_ECHO.get(), MatriarchEchoEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(REGOLITH_SKIMMER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LUNAR_MOTH.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(SELENITE_SENTINEL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(UMBRAL_LURKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MOONKIT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isSolidRender(), SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MOONLEAPER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isSolidRender(), SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private MoonEntities() {
    }
}
