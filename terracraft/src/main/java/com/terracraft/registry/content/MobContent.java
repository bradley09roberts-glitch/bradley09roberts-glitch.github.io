package com.terracraft.registry.content;

import com.terracraft.TerraCraft;
import com.terracraft.entity.boss.EyeOfCthulhu;
import com.terracraft.entity.boss.KingSlime;
import com.terracraft.entity.mob.FlyerMob;
import com.terracraft.entity.mob.MobDefinition;
import com.terracraft.entity.mob.MotherSlimeMob;
import com.terracraft.entity.mob.SlimeMob;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.mob.TerrariaMobs;
import com.terracraft.entity.mob.WalkerMob;
import com.terracraft.entity.mob.WormMob;
import com.terracraft.entity.mob.ClimberMob;
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

    // --- Worms ------------------------------------------------------------------------------------------
    public static final WormMob.Spec DEVOURER_SPEC = new WormMob.Spec(9, 0.75, 0.42, 0.07, true, false);
    public static final WormMob.Spec GIANT_WORM_SPEC = new WormMob.Spec(6, 0.6, 0.35, 0.06, true, false);
    public static final RegistryObject<EntityType<WormMob>> DEVOURER = register("devourer",
        (type, level) -> new WormMob(type, level, DEVOURER_SPEC), 0.8F, 0.8F,
        MobDefinition.builder().life(140).damage(26).defense(10).knockbackTaken(0.0F).coins(300).followRange(48));
    public static final RegistryObject<EntityType<WormMob>> GIANT_WORM = register("giant_worm",
        (type, level) -> new WormMob(type, level, GIANT_WORM_SPEC), 0.6F, 0.6F,
        MobDefinition.builder().life(30).damage(8).defense(0).knockbackTaken(0.0F).coins(200).followRange(40));

    // --- Corruption / Crimson ---------------------------------------------------------------------------
    public static final RegistryObject<EntityType<FlyerMob>> EATER_OF_SOULS = register("eater_of_souls",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 0.7F,
        MobDefinition.builder().life(40).damage(22).defense(8).knockbackTaken(0.8F).coins(90).speed(0.3).followRange(48));
    public static final RegistryObject<EntityType<FlyerMob>> CRIMERA = register("crimera",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 0.7F,
        MobDefinition.builder().life(45).damage(22).defense(8).knockbackTaken(0.8F).coins(90).speed(0.3).followRange(48));
    public static final RegistryObject<EntityType<WalkerMob>> FACE_MONSTER = register("face_monster", WalkerMob::new, 0.7F, 1.9F,
        MobDefinition.builder().life(100).damage(25).defense(10).knockbackTaken(0.4F).coins(250).speed(0.24));
    public static final RegistryObject<EntityType<ClimberMob>> BLOOD_CRAWLER = register("blood_crawler", ClimberMob::new, 1.1F, 0.6F,
        MobDefinition.builder().life(70).damage(24).defense(10).knockbackTaken(0.5F).coins(250).speed(0.32));

    // --- Blood Moon ---------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> BLOOD_ZOMBIE = register("blood_zombie", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(90).damage(26).defense(10).knockbackTaken(0.4F).coins(150).speed(0.26).nocturnal());
    public static final RegistryObject<EntityType<FlyerMob>> DRIPPLER = register("drippler",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.9F, 0.9F,
        MobDefinition.builder().life(80).damage(20).defense(4).knockbackTaken(0.2F).coins(200).speed(0.12).followRange(48).nocturnal());

    // --- Boss minions ------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<FlyerMob>> SERVANT_OF_CTHULHU = register("servant_of_cthulhu",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.55F, 0.55F,
        MobDefinition.builder().life(8).damage(12).defense(0).coins(0).speed(0.32).followRange(64));

    // --- Bosses -------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<KingSlime>> KING_SLIME = register("king_slime", KingSlime::new, 3.0F, 2.2F,
        MobDefinition.builder().life(2000).damage(40).defense(10).knockbackTaken(0.0F).coins(10_000).followRange(128));
    public static final RegistryObject<EntityType<EyeOfCthulhu>> EYE_OF_CTHULHU = register("eye_of_cthulhu", EyeOfCthulhu::new, 2.4F, 2.4F,
        MobDefinition.builder().life(2800).damage(15).defense(12).knockbackTaken(0.0F).coins(30_000).followRange(128));

    public static final RegistryObject<EntityType<com.terracraft.entity.boss.EaterOfWorlds>> EATER_OF_WORLDS = register("eater_of_worlds",
        com.terracraft.entity.boss.EaterOfWorlds::new, 1.4F, 1.4F,
        MobDefinition.builder().life(150).damage(22).defense(4).knockbackTaken(0.0F).coins(300).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.BrainOfCthulhu>> BRAIN_OF_CTHULHU = register("brain_of_cthulhu",
        com.terracraft.entity.boss.BrainOfCthulhu::new, 2.2F, 2.0F,
        MobDefinition.builder().life(1000).damage(30).defense(14).knockbackTaken(0.0F).coins(50_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.BrainOfCthulhu.BrainCreeper>> BRAIN_CREEPER = register("creeper",
        com.terracraft.entity.boss.BrainOfCthulhu.BrainCreeper::new, 0.7F, 0.7F,
        MobDefinition.builder().life(100).damage(20).defense(10).knockbackTaken(0.0F).coins(0).followRange(64));

    // --- Dungeon ----------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> ANGRY_BONES = register("angry_bones", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(80).damage(26).defense(8).knockbackTaken(0.8F).coins(130).speed(0.27));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> DARK_CASTER = register("dark_caster",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.WATER_SPHERE, 20.0F),
        0.6F, 1.8F, MobDefinition.builder().life(50).damage(20).defense(2).knockbackTaken(0.6F).coins(250).speed(0.0).followRange(32));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> CURSED_SKULL = register("cursed_skull",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 0.7F,
        MobDefinition.builder().life(40).damage(35).defense(6).knockbackTaken(0.8F).coins(150).speed(0.16).followRange(40));
    public static final RegistryObject<EntityType<SlimeMob>> DUNGEON_SLIME = slime("dungeon_slime", 1.0F, 0.75F,
        MobDefinition.builder().life(150).damage(30).defense(7).knockbackTaken(0.6F).coins(2500));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.DungeonGuardian>> DUNGEON_GUARDIAN = register("dungeon_guardian",
        com.terracraft.entity.mob.DungeonGuardian::new, 1.6F, 1.6F,
        MobDefinition.builder().life(9999).damage(1000).defense(9999).knockbackTaken(0.0F).coins(0).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Skeletron>> SKELETRON = register("skeletron",
        com.terracraft.entity.boss.Skeletron::new, 2.2F, 2.2F,
        MobDefinition.builder().life(4400).damage(32).defense(10).knockbackTaken(0.0F).coins(50_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Skeletron.Hand>> SKELETRON_HAND = register("skeletron_hand",
        com.terracraft.entity.boss.Skeletron.Hand::new, 1.2F, 1.2F,
        MobDefinition.builder().life(600).damage(20).defense(14).knockbackTaken(0.0F).coins(0).followRange(160));

    // --- Jungle -----------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<SlimeMob>> JUNGLE_SLIME = slime("jungle_slime", 1.0F, 0.75F,
        MobDefinition.builder().life(60).damage(18).defense(6).coins(100));
    public static final RegistryObject<EntityType<FlyerMob>> JUNGLE_BAT = register("jungle_bat",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.6F, 0.45F,
        MobDefinition.builder().life(34).damage(20).defense(4).knockbackTaken(0.8F).coins(100).speed(0.3));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> HORNET = register("hornet",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.STINGER, 22.0F, 8.0F,
            net.minecraft.sounds.SoundEvents.BEE_STING), 0.7F, 0.7F,
        MobDefinition.builder().life(34).damage(26).defense(12).knockbackTaken(0.5F).coins(200).speed(0.16).followRange(40));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.SnapperMob>> MAN_EATER = register("man_eater",
        (type, level) -> new com.terracraft.entity.mob.SnapperMob(type, level, 5.0), 0.8F, 0.8F,
        MobDefinition.builder().life(130).damage(42).defense(14).knockbackTaken(0.0F).coins(300).followRange(16));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.SnapperMob>> SNATCHER = register("snatcher",
        (type, level) -> new com.terracraft.entity.mob.SnapperMob(type, level, 4.0), 0.7F, 0.7F,
        MobDefinition.builder().life(60).damage(30).defense(10).knockbackTaken(0.0F).coins(100).followRange(14));
    /** The Queen Bee's bees. */
    public static final RegistryObject<EntityType<FlyerMob>> BEE = register("bee",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.35F, 0.3F,
        MobDefinition.builder().life(5).damage(20).defense(0).knockbackTaken(1.0F).coins(0).speed(0.4).followRange(48));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.QueenBee>> QUEEN_BEE = register("queen_bee",
        com.terracraft.entity.boss.QueenBee::new, 2.0F, 1.8F,
        MobDefinition.builder().life(3400).damage(30).defense(8).knockbackTaken(0.0F).coins(100_000).followRange(160));

    // --- Underworld (all fire immune) ---------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> IMP = registerFireproof("imp",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.IMP_FIREBALL, 21.0F),
        0.6F, 1.5F, MobDefinition.builder().life(70).damage(34).defense(16).knockbackTaken(0.5F).coins(300).speed(0.0).followRange(32));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> DEMON = registerFireproof("demon",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.DEMON_SCYTHE,
            26.0F, 2.5F, net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL), 1.2F, 1.2F,
        MobDefinition.builder().life(120).damage(32).defense(8).knockbackTaken(0.2F).coins(300).speed(0.16).followRange(40));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> VOODOO_DEMON = registerFireproof("voodoo_demon",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.DEMON_SCYTHE,
            26.0F, 2.5F, net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL), 1.2F, 1.2F,
        MobDefinition.builder().life(140).damage(32).defense(8).knockbackTaken(0.2F).coins(400).speed(0.16).followRange(40));
    public static final RegistryObject<EntityType<SlimeMob>> LAVA_SLIME = registerFireproof("lava_slime", SlimeMob::new, 1.0F, 0.75F,
        MobDefinition.builder().life(50).damage(15).defense(10).coins(250));
    public static final RegistryObject<EntityType<FlyerMob>> HELLBAT = registerFireproof("hellbat",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.6F, 0.45F,
        MobDefinition.builder().life(35).damage(21).defense(8).knockbackTaken(0.8F).coins(90).speed(0.32));
    public static final WormMob.Spec BONE_SERPENT_SPEC = new WormMob.Spec(12, 0.8, 0.45, 0.07, true, false);
    public static final RegistryObject<EntityType<WormMob>> BONE_SERPENT = registerFireproof("bone_serpent",
        (type, level) -> new WormMob(type, level, BONE_SERPENT_SPEC), 0.8F, 0.8F,
        MobDefinition.builder().life(150).damage(30).defense(10).knockbackTaken(0.0F).coins(400).followRange(48));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.WallOfFlesh>> WALL_OF_FLESH = registerFireproof("wall_of_flesh",
        com.terracraft.entity.boss.WallOfFlesh::new, 3.0F, 4.0F,
        MobDefinition.builder().life(8000).damage(50).defense(12).knockbackTaken(0.0F).coins(80_000).followRange(200));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.WallOfFlesh.Eye>> WALL_OF_FLESH_EYE = registerFireproof("wall_of_flesh_eye",
        com.terracraft.entity.boss.WallOfFlesh.Eye::new, 2.0F, 2.0F,
        MobDefinition.builder().life(8000).damage(30).defense(12).knockbackTaken(0.0F).coins(0).followRange(200));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.WallOfFlesh.Hungry>> THE_HUNGRY = registerFireproof("the_hungry",
        com.terracraft.entity.boss.WallOfFlesh.Hungry::new, 1.0F, 1.0F,
        MobDefinition.builder().life(240).damage(30).defense(0).knockbackTaken(0.5F).coins(0).followRange(64));

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
        return register(name, factory, width, height, stats, false);
    }

    /** Underworld creatures: immune to fire and lava. */
    private static <T extends TerrariaMob> RegistryObject<EntityType<T>> registerFireproof(String name, EntityType.EntityFactory<T> factory,
                                                                                          float width, float height, MobDefinition.Builder stats) {
        return register(name, factory, width, height, stats, true);
    }

    private static <T extends TerrariaMob> RegistryObject<EntityType<T>> register(String name, EntityType.EntityFactory<T> factory,
                                                                                 float width, float height, MobDefinition.Builder stats,
                                                                                 boolean fireImmune) {
        TerrariaMobs.define(TerraCraft.id(name), stats.build());
        RegistryObject<EntityType<T>> type = ModEntities.ENTITY_TYPES.register(name, () -> {
            EntityType.Builder<T> builder = EntityType.Builder.of(factory, MobCategory.MONSTER).sized(width, height).clientTrackingRange(10);
            if (fireImmune) {
                builder.fireImmune();
            }
            return builder.build(ModEntities.ENTITY_TYPES.key(name));
        });
        ALL.add(type);
        return type;
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        for (RegistryObject<? extends EntityType<? extends TerrariaMob>> type : ALL) {
            event.put(type.get(), TerrariaMob.attributes(TerrariaMobs.definition(type.get())).build());
        }
    }
}
