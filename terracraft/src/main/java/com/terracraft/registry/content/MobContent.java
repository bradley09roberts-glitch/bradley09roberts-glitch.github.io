package com.terracraft.registry.content;

import com.terracraft.TerraCraft;
import com.terracraft.entity.mob.FlyerMob;
import com.terracraft.entity.mob.MobDefinition;
import com.terracraft.entity.mob.MotherSlimeMob;
import com.terracraft.entity.mob.SlimeMob;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.mob.TerrariaMobs;
import com.terracraft.entity.mob.WalkerMob;
import com.terracraft.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Terraria enemies: one line per enemy (AI family, hitbox in blocks, Terraria stats).
 * Spawning is data-driven ({@code data/<ns>/terracraft/spawns}), loot uses {@code <ns>:entities/<id>} loot tables
 * and art is a sprite sheet at {@code textures/entity/mob/<id>.png}.
 */
public final class MobContent {
    private static final List<RegistryObject<? extends EntityType<? extends TerrariaMob>>> ALL = new ArrayList<>();

    // --- Slimes ---------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<SlimeMob>> GREEN_SLIME = slime("green_slime", 0.9F, 0.65F,
        MobDefinition.builder().life(14).damage(6).defense(0).coins(25));
    public static final RegistryObject<EntityType<SlimeMob>> BLUE_SLIME = slime("blue_slime", 0.9F, 0.65F,
        MobDefinition.builder().life(25).damage(7).defense(2).coins(25));
    public static final RegistryObject<EntityType<SlimeMob>> RED_SLIME = slime("red_slime", 0.95F, 0.7F,
        MobDefinition.builder().life(35).damage(12).defense(4).coins(50));
    public static final RegistryObject<EntityType<SlimeMob>> PURPLE_SLIME = slime("purple_slime", 1.1F, 0.8F,
        MobDefinition.builder().life(40).damage(12).defense(6).knockbackTaken(0.9F).coins(80));
    public static final RegistryObject<EntityType<SlimeMob>> YELLOW_SLIME = slime("yellow_slime", 1.0F, 0.75F,
        MobDefinition.builder().life(45).damage(15).defense(7).coins(100));
    public static final RegistryObject<EntityType<SlimeMob>> BLACK_SLIME = slime("black_slime", 1.0F, 0.75F,
        MobDefinition.builder().life(45).damage(15).defense(8).coins(75));
    public static final RegistryObject<EntityType<SlimeMob>> BABY_SLIME = slime("baby_slime", 0.55F, 0.45F,
        MobDefinition.builder().life(30).damage(13).defense(4).coins(0));
    public static final RegistryObject<EntityType<MotherSlimeMob>> MOTHER_SLIME = register("mother_slime",
        (type, level) -> new MotherSlimeMob(type, level, BABY_SLIME, 2, 3), 1.3F, 1.0F,
        MobDefinition.builder().life(65).damage(20).defense(7).knockbackTaken(0.5F).coins(120));

    // --- Night surface ----------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> ZOMBIE = register("zombie", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(45).damage(14).defense(6).knockbackTaken(0.5F).coins(60).speed(0.23).nocturnal());
    public static final RegistryObject<EntityType<FlyerMob>> DEMON_EYE = register("demon_eye",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 0.7F,
        MobDefinition.builder().life(60).damage(18).defense(2).knockbackTaken(0.8F).coins(75).speed(0.25).followRange(48).nocturnal());

    // --- Underground / caverns --------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> SKELETON = register("skeleton", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(60).damage(20).defense(8).knockbackTaken(0.5F).coins(100).speed(0.25));
    public static final RegistryObject<EntityType<FlyerMob>> CAVE_BAT = register("cave_bat",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.6F, 0.45F,
        MobDefinition.builder().life(16).damage(13).defense(2).knockbackTaken(0.8F).coins(90).speed(0.3));

    private MobContent() {}

    public static void init() {
        EntityAttributeCreationEvent.BUS.addListener(MobContent::onAttributes);
    }

    public static List<RegistryObject<? extends EntityType<? extends TerrariaMob>>> all() {
        return ALL;
    }

    private static RegistryObject<EntityType<SlimeMob>> slime(String name, float width, float height, MobDefinition.Builder stats) {
        return register(name, SlimeMob::new, width, height, stats);
    }

    private static <T extends TerrariaMob> RegistryObject<EntityType<T>> register(String name, EntityType.EntityFactory<T> factory,
                                                                                 float width, float height, MobDefinition.Builder stats) {
        TerrariaMobs.define(TerraCraft.id(name), stats.build());
        RegistryObject<EntityType<T>> type = ModEntities.ENTITY_TYPES.register(name,
            () -> EntityType.Builder.of(factory, MobCategory.MONSTER)
                .sized(width, height)
                .clientTrackingRange(10)
                .build(ModEntities.ENTITY_TYPES.key(name)));
        ALL.add(type);
        return type;
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        for (RegistryObject<? extends EntityType<? extends TerrariaMob>> type : ALL) {
            event.put(type.get(), TerrariaMob.attributes(TerrariaMobs.definition(type.get())).build());
        }
    }
}
