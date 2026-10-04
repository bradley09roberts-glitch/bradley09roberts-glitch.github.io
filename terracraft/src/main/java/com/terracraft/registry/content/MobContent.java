package com.terracraft.registry.content;

import com.terracraft.entity.boss.CelestialPillar;
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
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import com.terracraft.registry.RegistryObject;

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

    // --- Goblin Army ------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> GOBLIN_PEON = register("goblin_peon", WalkerMob::new, 0.6F, 1.5F,
        MobDefinition.builder().life(60).damage(12).defense(4).knockbackTaken(0.8F).coins(100).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> GOBLIN_THIEF = register("goblin_thief", WalkerMob::new, 0.6F, 1.5F,
        MobDefinition.builder().life(80).damage(20).defense(6).knockbackTaken(0.6F).coins(150).speed(0.34));
    public static final RegistryObject<EntityType<WalkerMob>> GOBLIN_WARRIOR = register("goblin_warrior", WalkerMob::new, 0.7F, 1.7F,
        MobDefinition.builder().life(110).damage(27).defense(16).knockbackTaken(0.4F).coins(200).speed(0.22));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> GOBLIN_SORCERER = register("goblin_sorcerer",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CHAOS_BALL, 18.0F),
        0.6F, 1.5F, MobDefinition.builder().life(40).damage(20).defense(0).knockbackTaken(0.7F).coins(200).speed(0.0).followRange(32));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> GOBLIN_ARCHER = register("goblin_archer",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ARROW, 15.0F),
        0.6F, 1.5F, MobDefinition.builder().life(60).damage(20).defense(6).knockbackTaken(0.6F).coins(150).speed(0.26));

    // --- Meteorite ---------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> METEOR_HEAD = registerFireproof("meteor_head",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 0.7F,
        MobDefinition.builder().life(26).damage(40).defense(6).knockbackTaken(0.8F).coins(80).speed(0.2).followRange(40));

    // --- Hardmode: the Hallow ---------------------------------------------------------------------------
    public static final RegistryObject<EntityType<FlyerMob>> PIXIE = register("pixie",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.6F, 0.6F,
        MobDefinition.builder().life(150).damage(45).defense(20).knockbackTaken(0.8F).coins(300).speed(0.3).followRange(40));
    public static final RegistryObject<EntityType<WalkerMob>> UNICORN = register("unicorn", WalkerMob::new, 0.9F, 1.6F,
        MobDefinition.builder().life(400).damage(65).defense(30).knockbackTaken(0.3F).coins(500).speed(0.38).followRange(40));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> GASTROPOD = register("gastropod",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.PINK_LASER,
            30.0F, 2.5F, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME), 0.9F, 0.9F,
        MobDefinition.builder().life(220).damage(60).defense(20).knockbackTaken(0.5F).coins(400).speed(0.12).followRange(40).nocturnal());
    public static final RegistryObject<EntityType<FlyerMob>> ILLUMINANT_BAT = register("illuminant_bat",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.6F, 0.45F,
        MobDefinition.builder().life(200).damage(60).defense(20).knockbackTaken(0.7F).coins(400).speed(0.34));
    public static final RegistryObject<EntityType<SlimeMob>> ILLUMINANT_SLIME = slime("illuminant_slime", 1.0F, 0.75F,
        MobDefinition.builder().life(180).damage(70).defense(30).coins(300));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.TeleporterMob>> CHAOS_ELEMENTAL = register("chaos_elemental",
        com.terracraft.entity.mob.TeleporterMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(300).damage(65).defense(30).knockbackTaken(0.6F).coins(500).speed(0.28).followRange(32));

    // --- Hardmode: Corruption / Crimson ----------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> CORRUPTOR = register("corruptor",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.VILE_SPIT,
            32.0F, 2.0F, net.minecraft.sounds.SoundEvents.LLAMA_SPIT), 1.0F, 0.9F,
        MobDefinition.builder().life(230).damage(55).defense(32).knockbackTaken(0.5F).coins(400).speed(0.16).followRange(40));
    public static final RegistryObject<EntityType<FlyerMob>> SLIMER = register("slimer",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.9F, 0.8F,
        MobDefinition.builder().life(60).damage(45).defense(30).knockbackTaken(0.6F).coins(300).speed(0.24).followRange(40));
    public static final RegistryObject<EntityType<SlimeMob>> CRIMSLIME = slime("crimslime", 1.0F, 0.75F,
        MobDefinition.builder().life(145).damage(50).defense(30).coins(300));
    public static final RegistryObject<EntityType<SlimeMob>> HERPLING = register("herpling", SlimeMob::new, 1.0F, 1.0F,
        MobDefinition.builder().life(350).damage(70).defense(20).knockbackTaken(0.4F).coins(400));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> FLOATY_GROSS = register("floaty_gross",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.9F, 1.4F,
        MobDefinition.builder().life(270).damage(60).defense(30).knockbackTaken(0.4F).coins(400).speed(0.16).followRange(40).nocturnal());

    // --- Hardmode: surface night, sky, underground --------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> WRAITH = register("wraith",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 1.8F,
        MobDefinition.builder().life(200).damage(75).defense(18).knockbackTaken(0.3F).coins(300).speed(0.2).followRange(40).nocturnal());
    public static final RegistryObject<EntityType<WalkerMob>> POSSESSED_ARMOR = register("possessed_armor", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(260).damage(50).defense(20).knockbackTaken(0.4F).coins(400).speed(0.22).nocturnal());
    public static final RegistryObject<EntityType<WalkerMob>> WEREWOLF = register("werewolf", WalkerMob::new, 0.7F, 2.0F,
        MobDefinition.builder().life(400).damage(70).defense(40).knockbackTaken(0.4F).coins(500).speed(0.32).nocturnal());
    public static final WormMob.Spec WYVERN_SPEC = new WormMob.Spec(14, 0.9, 0.55, 0.09, true, true);
    public static final RegistryObject<EntityType<WormMob>> WYVERN = register("wyvern",
        (type, level) -> new WormMob(type, level, WYVERN_SPEC), 1.0F, 1.0F,
        MobDefinition.builder().life(4000).damage(80).defense(10).knockbackTaken(0.0F).coins(1000).followRange(64));
    public static final RegistryObject<EntityType<WalkerMob>> ARMORED_SKELETON = register("armored_skeleton", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(340).damage(60).defense(36).knockbackTaken(0.4F).coins(400).speed(0.26));
    public static final RegistryObject<EntityType<FlyerMob>> GIANT_BAT = register("giant_bat",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.9F, 0.6F,
        MobDefinition.builder().life(160).damage(53).defense(18).knockbackTaken(0.7F).coins(300).speed(0.32));
    public static final RegistryObject<EntityType<SlimeMob>> MIMIC = register("mimic", SlimeMob::new, 1.0F, 1.0F,
        MobDefinition.builder().life(500).damage(80).defense(30).knockbackTaken(0.1F).coins(10_000));

    // --- Mechanical bosses ----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.TheTwins>> RETINAZER = register("retinazer",
        com.terracraft.entity.boss.TheTwins::retinazer, 2.0F, 2.0F,
        MobDefinition.builder().life(20000).damage(45).defense(10).knockbackTaken(0.0F).coins(60_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.TheTwins>> SPAZMATISM = register("spazmatism",
        com.terracraft.entity.boss.TheTwins::spazmatism, 2.0F, 2.0F,
        MobDefinition.builder().life(23000).damage(50).defense(10).knockbackTaken(0.0F).coins(60_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Destroyer>> DESTROYER = registerFireproof("destroyer",
        com.terracraft.entity.boss.Destroyer::new, 1.4F, 1.4F,
        MobDefinition.builder().life(80000).damage(70).defense(0).knockbackTaken(0.0F).coins(0).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> PROBE = register("probe",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.MECH_LASER,
            25.0F, 2.5F, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE), 0.6F, 0.6F,
        MobDefinition.builder().life(200).damage(50).defense(20).knockbackTaken(0.5F).coins(0).speed(0.3).followRange(60));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.SkeletronPrime>> SKELETRON_PRIME = register("skeletron_prime",
        com.terracraft.entity.boss.SkeletronPrime::new, 2.2F, 2.2F,
        MobDefinition.builder().life(28000).damage(47).defense(24).knockbackTaken(0.0F).coins(120_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.SkeletronPrime.Arm>> PRIME_CANNON = primeArm("prime_cannon",
        com.terracraft.entity.boss.SkeletronPrime.Arm.Kind.CANNON, MobDefinition.builder().life(7000).damage(30).defense(23));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.SkeletronPrime.Arm>> PRIME_SAW = primeArm("prime_saw",
        com.terracraft.entity.boss.SkeletronPrime.Arm.Kind.SAW, MobDefinition.builder().life(9000).damage(56).defense(38));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.SkeletronPrime.Arm>> PRIME_VICE = primeArm("prime_vice",
        com.terracraft.entity.boss.SkeletronPrime.Arm.Kind.VICE, MobDefinition.builder().life(9000).damage(52).defense(34));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.SkeletronPrime.Arm>> PRIME_LASER = primeArm("prime_laser",
        com.terracraft.entity.boss.SkeletronPrime.Arm.Kind.LASER, MobDefinition.builder().life(6000).damage(29).defense(20));

    // --- Queen Slime (Hardmode, the Hallow) ------------------------------------------------------------------
    public static final RegistryObject<EntityType<SlimeMob>> CRYSTAL_SLIME = slime("crystal_slime", 0.8F, 0.6F,
        MobDefinition.builder().life(300).damage(50).defense(25).coins(0));
    public static final RegistryObject<EntityType<SlimeMob>> BOUNCY_SLIME = register("bouncy_slime", (type, level) -> new SlimeMob(type, level, 1.6F),
        0.9F, 0.7F, MobDefinition.builder().life(300).damage(55).defense(25).coins(0));
    public static final RegistryObject<EntityType<FlyerMob>> HEAVENLY_SLIME = register("heavenly_slime",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.8F, 0.6F,
        MobDefinition.builder().life(250).damage(55).defense(20).knockbackTaken(0.6F).coins(0).speed(0.28).followRange(48));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.QueenSlime>> QUEEN_SLIME = register("queen_slime",
        com.terracraft.entity.boss.QueenSlime::new, 2.6F, 2.2F,
        MobDefinition.builder().life(18000).damage(60).defense(26).knockbackTaken(0.0F).coins(100_000).followRange(160));

    // --- Pirate Invasion -------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> PIRATE_DECKHAND = register("pirate_deckhand", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(200).damage(40).defense(14).knockbackTaken(0.5F).coins(600).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> PIRATE_CORSAIR = register("pirate_corsair", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(300).damage(50).defense(18).knockbackTaken(0.4F).coins(800).speed(0.36));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> PIRATE_CROSSBOWER = register("pirate_crossbower",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ARROW, 40.0F),
        0.6F, 1.8F, MobDefinition.builder().life(250).damage(30).defense(16).knockbackTaken(0.5F).coins(800).speed(0.26));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> PIRATE_DEADEYE = register("pirate_deadeye",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_BULLET, 40.0F),
        0.6F, 1.8F, MobDefinition.builder().life(250).damage(30).defense(16).knockbackTaken(0.5F).coins(800).speed(0.26));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> PIRATE_CAPTAIN = register("pirate_captain",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_CANNONBALL, 60.0F),
        0.7F, 2.0F, MobDefinition.builder().life(2000).damage(60).defense(25).knockbackTaken(0.2F).coins(5000).speed(0.22));
    public static final RegistryObject<EntityType<FlyerMob>> PARROT = register("parrot",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.5F, 0.6F,
        MobDefinition.builder().life(100).damage(45).defense(10).knockbackTaken(0.8F).coins(300).speed(0.34).followRange(40));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.FlyingDutchman>> FLYING_DUTCHMAN = register("flying_dutchman",
        com.terracraft.entity.mob.FlyingDutchman::new, 7.0F, 5.0F,
        MobDefinition.builder().life(10000).damage(70).defense(30).knockbackTaken(0.0F).coins(50_000).followRange(80));

    // --- Frost Legion -----------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> MISTER_STABBY = register("mister_stabby", WalkerMob::new, 0.7F, 1.8F,
        MobDefinition.builder().life(200).damage(50).defense(10).knockbackTaken(0.5F).coins(400).speed(0.36));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> SNOWMAN_GANGSTA = register("snowman_gangsta",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_BULLET, 25.0F),
        0.7F, 1.8F, MobDefinition.builder().life(200).damage(20).defense(10).knockbackTaken(0.5F).coins(400).speed(0.26));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> SNOW_BALLA = register("snow_balla",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_SNOWBALL, 30.0F),
        0.7F, 1.8F, MobDefinition.builder().life(200).damage(20).defense(10).knockbackTaken(0.5F).coins(400).speed(0.26));

    // --- Hardmode jungle and Plantera --------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.SnapperMob>> ANGRY_TRAPPER = register("angry_trapper",
        (type, level) -> new com.terracraft.entity.mob.SnapperMob(type, level, 8.0), 1.0F, 1.0F,
        MobDefinition.builder().life(1000).damage(80).defense(30).knockbackTaken(0.0F).coins(1500).followRange(20));
    public static final RegistryObject<EntityType<SlimeMob>> DERPLING = register("derpling", (type, level) -> new SlimeMob(type, level, 1.8F),
        1.1F, 0.8F, MobDefinition.builder().life(240).damage(60).defense(28).knockbackTaken(0.6F).coins(700));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Plantera>> PLANTERA = register("plantera",
        com.terracraft.entity.boss.Plantera::new, 2.4F, 2.4F,
        MobDefinition.builder().life(30000).damage(50).defense(14).knockbackTaken(0.0F).coins(150_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Plantera.Hook>> PLANTERA_HOOK = register("plantera_hook",
        com.terracraft.entity.boss.Plantera.Hook::new, 0.9F, 0.9F,
        MobDefinition.builder().life(30000).damage(0).defense(30).knockbackTaken(0.0F).coins(0).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Plantera.Tentacle>> PLANTERA_TENTACLE = register("plantera_tentacle",
        com.terracraft.entity.boss.Plantera.Tentacle::new, 0.8F, 0.8F,
        MobDefinition.builder().life(1000).damage(60).defense(20).knockbackTaken(0.0F).coins(0).followRange(160));

    // --- Lihzahrd Temple and Golem ---------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> LIHZAHRD = register("lihzahrd", WalkerMob::new, 0.7F, 1.6F,
        MobDefinition.builder().life(330).damage(60).defense(24).knockbackTaken(0.5F).coins(1500).speed(0.3));
    public static final RegistryObject<EntityType<FlyerMob>> FLYING_SNAKE = register("flying_snake",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 1.2F, 0.6F,
        MobDefinition.builder().life(200).damage(70).defense(20).knockbackTaken(0.4F).coins(1000).speed(0.33).followRange(48));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Golem>> GOLEM = register("golem",
        com.terracraft.entity.boss.Golem::new, 3.6F, 3.4F,
        MobDefinition.builder().life(39000).damage(72).defense(26).knockbackTaken(0.0F).coins(150_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Golem.Head>> GOLEM_HEAD = register("golem_head",
        com.terracraft.entity.boss.Golem.Head::new, 2.2F, 2.0F,
        MobDefinition.builder().life(16000).damage(64).defense(20).knockbackTaken(0.0F).coins(0).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.Golem.Fist>> GOLEM_FIST = register("golem_fist",
        com.terracraft.entity.boss.Golem.Fist::new, 1.2F, 1.2F,
        MobDefinition.builder().life(9000).damage(59).defense(26).knockbackTaken(0.0F).coins(0).followRange(160));

    // --- Duke Fishron -----------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CritterMob>> TRUFFLE_WORM = register("truffle_worm",
        (type, level) -> new com.terracraft.entity.mob.CritterMob(type, level, FishronContent.TRUFFLE_WORM), 0.5F, 0.3F,
        MobDefinition.builder().life(5).damage(0).defense(0).coins(0).speed(0.3));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.DukeFishron>> DUKE_FISHRON = register("duke_fishron",
        com.terracraft.entity.boss.DukeFishron::new, 3.0F, 2.2F,
        MobDefinition.builder().life(50000).damage(70).defense(50).knockbackTaken(0.0F).coins(250_000).followRange(160));
    public static final RegistryObject<EntityType<FlyerMob>> SHARKRON = register("sharkron",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 1.0F, 0.7F,
        MobDefinition.builder().life(600).damage(60).defense(30).knockbackTaken(0.5F).coins(0).speed(0.4).followRange(60));

    // --- The Dungeon after Plantera --------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> BLUE_ARMORED_BONES = register("blue_armored_bones", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(400).damage(80).defense(34).knockbackTaken(0.4F).coins(1000).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> HELL_ARMORED_BONES = register("hell_armored_bones", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(400).damage(80).defense(34).knockbackTaken(0.4F).coins(1000).speed(0.32));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> PALADIN = register("paladin",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_HAMMER, 90.0F),
        0.9F, 2.6F, MobDefinition.builder().life(2000).damage(100).defense(50).knockbackTaken(0.1F).coins(5000).speed(0.22));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> SKELETON_SNIPER = register("skeleton_sniper",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_BULLET, 120.0F),
        0.6F, 1.8F, MobDefinition.builder().life(400).damage(80).defense(40).knockbackTaken(0.4F).coins(1000).speed(0.2).followRange(48));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> TACTICAL_SKELETON = register("tactical_skeleton",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_BULLET, 60.0F),
        0.6F, 1.8F, MobDefinition.builder().life(400).damage(80).defense(40).knockbackTaken(0.4F).coins(1000).speed(0.24));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> SKELETON_COMMANDO = register("skeleton_commando",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ROCKET, 80.0F),
        0.6F, 1.8F, MobDefinition.builder().life(400).damage(80).defense(40).knockbackTaken(0.4F).coins(1000).speed(0.22));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> RAGGED_CASTER = register("ragged_caster",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.LOST_SOUL, 50.0F),
        0.6F, 1.8F, MobDefinition.builder().life(300).damage(50).defense(10).knockbackTaken(0.6F).coins(1000).speed(0.0).followRange(40));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> NECROMANCER = register("necromancer",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_SHADOWBEAM, 60.0F),
        0.6F, 1.8F, MobDefinition.builder().life(300).damage(50).defense(10).knockbackTaken(0.6F).coins(1000).speed(0.0).followRange(40));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> DUNGEON_SPIRIT = register("dungeon_spirit",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.7F, 0.9F,
        MobDefinition.builder().life(200).damage(70).defense(0).knockbackTaken(0.6F).coins(0).speed(0.38).followRange(48));

    // --- Pumpkin Moon -----------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> SCARECROW = register("scarecrow", WalkerMob::new, 0.6F, 1.9F,
        MobDefinition.builder().life(500).damage(60).defense(20).knockbackTaken(0.4F).coins(800).speed(0.28));
    public static final RegistryObject<EntityType<WalkerMob>> SPLINTERLING = register("splinterling", WalkerMob::new, 0.6F, 1.2F,
        MobDefinition.builder().life(600).damage(70).defense(20).knockbackTaken(0.4F).coins(800).speed(0.32));
    public static final RegistryObject<EntityType<WalkerMob>> HELLHOUND = register("hellhound", WalkerMob::new, 0.9F, 0.9F,
        MobDefinition.builder().life(600).damage(80).defense(25).knockbackTaken(0.4F).coins(1000).speed(0.45));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> POLTERGEIST = register("poltergeist",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.6F, 0.9F,
        MobDefinition.builder().life(500).damage(60).defense(20).knockbackTaken(0.6F).coins(1000).speed(0.36).followRange(64));
    public static final RegistryObject<EntityType<WalkerMob>> HEADLESS_HORSEMAN = register("headless_horseman", WalkerMob::new, 1.2F, 2.6F,
        MobDefinition.builder().life(10000).damage(100).defense(40).knockbackTaken(0.1F).coins(5000).speed(0.42));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> MOURNING_WOOD = register("mourning_wood",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.FLAMING_WOOD, 90F), 1.6F, 4.0F,
        MobDefinition.builder().life(12000).damage(120).defense(28).knockbackTaken(0.0F).coins(10000).speed(0.15));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> PUMPKING = register("pumpking",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.FLAMING_SCYTHE, 90F, 2.0F, net.minecraft.sounds.SoundEvents.BLAZE_SHOOT), 2.0F, 2.0F,
        MobDefinition.builder().life(22000).damage(120).defense(36).knockbackTaken(0.0F).coins(20000).speed(0.32).followRange(64));
    // --- Frost Moon -------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<WalkerMob>> ZOMBIE_ELF = register("zombie_elf", WalkerMob::new, 0.5F, 1.3F,
        MobDefinition.builder().life(300).damage(60).defense(20).knockbackTaken(0.4F).coins(400).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> GINGERBREAD_MAN = register("gingerbread_man", WalkerMob::new, 0.5F, 1.4F,
        MobDefinition.builder().life(300).damage(60).defense(20).knockbackTaken(0.4F).coins(400).speed(0.34));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> ELF_ARCHER = register("elf_archer",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ARROW, 70F), 0.5F, 1.3F,
        MobDefinition.builder().life(300).damage(50).defense(20).knockbackTaken(0.3F).coins(400).speed(0.26));
    public static final RegistryObject<EntityType<WalkerMob>> NUTCRACKER = register("nutcracker", WalkerMob::new, 0.6F, 2.0F,
        MobDefinition.builder().life(600).damage(80).defense(30).knockbackTaken(0.4F).coins(800).speed(0.3));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> YETI = register("yeti",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_SNOWBALL, 80F), 1.4F, 2.6F,
        MobDefinition.builder().life(4000).damage(100).defense(34).knockbackTaken(0.1F).coins(3000).speed(0.28));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> FLOCKO = register("flocko",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ICE_SHARD, 50F, 2.0F, net.minecraft.sounds.SoundEvents.GLASS_BREAK), 0.6F, 0.6F,
        MobDefinition.builder().life(300).damage(50).defense(20).knockbackTaken(0.2F).coins(400).speed(0.34).followRange(64));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> EVERSCREAM = register("everscream",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_PINE_NEEDLE, 70F), 2.0F, 5.0F,
        MobDefinition.builder().life(23000).damage(120).defense(38).knockbackTaken(0.0F).coins(20000).speed(0.0));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> SANTA_NK1 = register("santa_nk1",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ROCKET, 90F), 1.6F, 2.6F,
        MobDefinition.builder().life(34000).damage(130).defense(52).knockbackTaken(0.0F).coins(25000).speed(0.22));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> ICE_QUEEN = register("ice_queen",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ICE_SHARD, 80F, 2.0F, net.minecraft.sounds.SoundEvents.GLASS_BREAK), 2.0F, 2.0F,
        MobDefinition.builder().life(34000).damage(120).defense(38).knockbackTaken(0.0F).coins(25000).speed(0.34).followRange(64));

    // --- Empress of Light -----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.PrismaticLacewing>> PRISMATIC_LACEWING = register("prismatic_lacewing",
        com.terracraft.entity.mob.PrismaticLacewing::new, 0.5F, 0.4F, MobDefinition.builder().life(5).damage(0).defense(0).coins(0));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.EmpressOfLight>> EMPRESS_OF_LIGHT = register("empress_of_light",
        com.terracraft.entity.boss.EmpressOfLight::new, 2.4F, 3.0F,
        MobDefinition.builder().life(70000).damage(80).defense(50).knockbackTaken(0.0F).coins(250_000).followRange(160));
    // --- Martian Madness ------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.MartianProbe>> MARTIAN_PROBE = register("martian_probe",
        com.terracraft.entity.mob.MartianProbe::new, 0.8F, 0.6F, MobDefinition.builder().life(300).damage(0).defense(10).knockbackTaken(0.8F).coins(1000).followRange(48));
    public static final RegistryObject<EntityType<WalkerMob>> GRAY_GRUNT = register("gray_grunt", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(400).damage(70).defense(26).knockbackTaken(0.4F).coins(1000).speed(0.3));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> RAY_GUNNER = register("ray_gunner",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.MARTIAN_LASER, 60.0F), 0.6F, 1.8F,
        MobDefinition.builder().life(400).damage(60).defense(26).knockbackTaken(0.4F).coins(1000).speed(0.26));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> BRAIN_SCRAMBLER = register("brain_scrambler",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.MARTIAN_LASER, 60.0F), 0.6F, 1.8F,
        MobDefinition.builder().life(400).damage(60).defense(26).knockbackTaken(0.4F).coins(1000).speed(0.26));
    public static final RegistryObject<EntityType<WalkerMob>> GIGAZAPPER = register("gigazapper", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(700).damage(80).defense(30).knockbackTaken(0.3F).coins(1500).speed(0.36));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> MARTIAN_OFFICER = register("martian_officer",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.MARTIAN_LASER, 70.0F), 0.6F, 1.9F,
        MobDefinition.builder().life(1000).damage(70).defense(30).knockbackTaken(0.3F).coins(2000).speed(0.26));
    public static final RegistryObject<EntityType<FlyerMob>> MARTIAN_DRONE = register("martian_drone",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.8F, 0.6F,
        MobDefinition.builder().life(400).damage(100).defense(20).knockbackTaken(0.6F).coins(1000).speed(0.42).followRange(64));
    public static final RegistryObject<EntityType<WalkerMob>> SCUTLIX = register("scutlix", WalkerMob::new, 1.4F, 1.4F,
        MobDefinition.builder().life(1000).damage(80).defense(30).knockbackTaken(0.2F).coins(2000).speed(0.42));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.MartianSaucer>> MARTIAN_SAUCER = register("martian_saucer",
        com.terracraft.entity.mob.MartianSaucer::new, 5.0F, 2.0F,
        MobDefinition.builder().life(12000).damage(80).defense(40).knockbackTaken(0.0F).coins(50_000).followRange(100));

    // --- Lunatic Cultist ------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.LunaticCultist>> LUNATIC_CULTIST = register("lunatic_cultist",
        com.terracraft.entity.boss.LunaticCultist::new, 1.0F, 2.2F,
        MobDefinition.builder().life(32000).damage(50).defense(42).knockbackTaken(0.0F).coins(100_000).followRange(160));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.LunaticCultist.Devotee>> CULTIST_DEVOTEE = register("cultist_devotee",
        com.terracraft.entity.boss.LunaticCultist.Devotee::new, 0.8F, 2.0F,
        MobDefinition.builder().life(1000).damage(0).defense(0).knockbackTaken(0.0F).coins(0));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.LunaticCultist.CultistClone>> CULTIST_CLONE = register("cultist_clone",
        com.terracraft.entity.boss.LunaticCultist.CultistClone::new, 1.0F, 2.2F,
        MobDefinition.builder().life(1500).damage(50).defense(42).knockbackTaken(0.0F).coins(0).followRange(160));

    // --- Celestial Pillars ----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<CelestialPillar>> SOLAR_PILLAR = register("solar_pillar",
        (type, level) -> new CelestialPillar(type, level, CelestialPillar.Kind.SOLAR), 4.0F, 10.0F,
        MobDefinition.builder().life(20000).damage(0).defense(20).knockbackTaken(0.0F).coins(0).followRange(120));
    public static final RegistryObject<EntityType<CelestialPillar>> VORTEX_PILLAR = register("vortex_pillar",
        (type, level) -> new CelestialPillar(type, level, CelestialPillar.Kind.VORTEX), 4.0F, 10.0F,
        MobDefinition.builder().life(20000).damage(0).defense(20).knockbackTaken(0.0F).coins(0).followRange(120));
    public static final RegistryObject<EntityType<CelestialPillar>> NEBULA_PILLAR = register("nebula_pillar",
        (type, level) -> new CelestialPillar(type, level, CelestialPillar.Kind.NEBULA), 4.0F, 10.0F,
        MobDefinition.builder().life(20000).damage(0).defense(20).knockbackTaken(0.0F).coins(0).followRange(120));
    public static final RegistryObject<EntityType<CelestialPillar>> STARDUST_PILLAR = register("stardust_pillar",
        (type, level) -> new CelestialPillar(type, level, CelestialPillar.Kind.STARDUST), 4.0F, 10.0F,
        MobDefinition.builder().life(20000).damage(0).defense(20).knockbackTaken(0.0F).coins(0).followRange(120));
    public static final RegistryObject<EntityType<WalkerMob>> SELENIAN = register("selenian", WalkerMob::new, 0.7F, 1.8F,
        MobDefinition.builder().life(1000).damage(80).defense(40).knockbackTaken(0.3F).coins(0).speed(0.4));
    public static final RegistryObject<EntityType<WalkerMob>> SROLLER = register("sroller", WalkerMob::new, 1.0F, 1.0F,
        MobDefinition.builder().life(900).damage(90).defense(40).knockbackTaken(0.2F).coins(0).speed(0.5));
    public static final RegistryObject<EntityType<FlyerMob>> CORITE = register("corite",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 1.0F, 1.0F,
        MobDefinition.builder().life(800).damage(90).defense(30).knockbackTaken(0.4F).coins(0).speed(0.45).followRange(64));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> STORM_DIVER = register("storm_diver",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CELESTIAL_SHOT, 60F), 0.7F, 1.8F,
        MobDefinition.builder().life(800).damage(50).defense(30).knockbackTaken(0.4F).coins(0).speed(0.3));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> ALIEN_HORNET = register("alien_hornet",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CELESTIAL_SHOT, 60F, 2.5F, net.minecraft.sounds.SoundEvents.BEE_LOOP_AGGRESSIVE), 0.9F, 0.8F,
        MobDefinition.builder().life(700).damage(70).defense(25).knockbackTaken(0.4F).coins(0).speed(0.4).followRange(64));
    public static final RegistryObject<EntityType<WalkerMob>> VORTEXIAN = register("vortexian", WalkerMob::new, 0.7F, 1.8F,
        MobDefinition.builder().life(900).damage(70).defense(30).knockbackTaken(0.3F).coins(0).speed(0.36));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> NEBULA_FLOATER = register("nebula_floater",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CELESTIAL_SHOT, 60F, 2.0F, net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL), 1.0F, 1.6F,
        MobDefinition.builder().life(1000).damage(60).defense(30).knockbackTaken(0.3F).coins(0).speed(0.25).followRange(64));
    public static final RegistryObject<EntityType<FlyerMob>> BRAIN_SUCKLER = register("brain_suckler",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.8F, 0.8F,
        MobDefinition.builder().life(500).damage(80).defense(20).knockbackTaken(0.6F).coins(0).speed(0.42).followRange(64));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> PREDICTOR = register("predictor",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CELESTIAL_SHOT, 70F), 0.7F, 1.9F,
        MobDefinition.builder().life(900).damage(60).defense(30).knockbackTaken(0.3F).coins(0).speed(0.3));
    public static final RegistryObject<EntityType<FlyerMob>> STAR_CELL = register("star_cell",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.9F, 0.9F,
        MobDefinition.builder().life(600).damage(70).defense(20).knockbackTaken(0.6F).coins(0).speed(0.32).followRange(64));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> FLOW_INVADER = register("flow_invader",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CELESTIAL_SHOT, 60F, 2.5F, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME), 1.0F, 1.0F,
        MobDefinition.builder().life(900).damage(70).defense(30).knockbackTaken(0.4F).coins(0).speed(0.35).followRange(64));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> TWINKLE_POPPER = register("twinkle_popper",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.CELESTIAL_SHOT, 60F), 0.8F, 1.6F,
        MobDefinition.builder().life(800).damage(50).defense(30).knockbackTaken(0.4F).coins(0).speed(0.3));

    // --- Moon Lord ------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.MoonLord>> MOON_LORD = register("moon_lord",
        com.terracraft.entity.boss.MoonLord::new, 5.0F, 8.0F,
        MobDefinition.builder().life(145000).damage(70).defense(70).knockbackTaken(0.0F).coins(1_000_000).followRange(200));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.MoonLord.Eye>> MOON_LORD_HAND = register("moon_lord_hand",
        com.terracraft.entity.boss.MoonLord.Eye::new, 2.4F, 3.6F,
        MobDefinition.builder().life(25000).damage(80).defense(70).knockbackTaken(0.0F).coins(0).followRange(200));
    public static final RegistryObject<EntityType<com.terracraft.entity.boss.MoonLord.Eye>> MOON_LORD_HEAD = register("moon_lord_head",
        com.terracraft.entity.boss.MoonLord.Eye::new, 3.4F, 3.6F,
        MobDefinition.builder().life(45000).damage(80).defense(70).knockbackTaken(0.0F).coins(0).followRange(200));

    // --- Solar Eclipse ---------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> EYEZOR = register("eyezor",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.PINK_LASER, 50.0F), 0.6F, 1.8F,
        MobDefinition.builder().life(1000).damage(60).defense(24).knockbackTaken(0.4F).coins(1500).speed(0.25));
    public static final RegistryObject<EntityType<WalkerMob>> FRANKENSTEIN = register("frankenstein", WalkerMob::new, 0.7F, 2.1F,
        MobDefinition.builder().life(430).damage(70).defense(28).knockbackTaken(0.3F).coins(1000).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> SWAMP_THING = register("swamp_thing", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(400).damage(60).defense(20).knockbackTaken(0.4F).coins(1000).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> VAMPIRE = register("vampire", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(500).damage(60).defense(24).knockbackTaken(0.4F).coins(1200).speed(0.34));
    public static final RegistryObject<EntityType<WalkerMob>> CREATURE_FROM_THE_DEEP = register("creature_from_the_deep", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(500).damage(64).defense(24).knockbackTaken(0.4F).coins(1200).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> FRITZ = register("fritz", WalkerMob::new, 0.5F, 1.3F,
        MobDefinition.builder().life(300).damage(50).defense(16).knockbackTaken(0.6F).coins(800).speed(0.4));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.GhostFlyerMob>> REAPER = register("reaper",
        (type, level) -> new com.terracraft.entity.mob.GhostFlyerMob(type, level, FlyerMob.Style.CHASER), 0.8F, 1.6F,
        MobDefinition.builder().life(500).damage(70).defense(20).knockbackTaken(0.5F).coins(1500).speed(0.3).followRange(48));
    public static final RegistryObject<EntityType<FlyerMob>> MOTHRON = register("mothron",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 2.6F, 1.6F,
        MobDefinition.builder().life(5000).damage(100).defense(30).knockbackTaken(0.1F).coins(5000).speed(0.32).followRange(64));
    public static final RegistryObject<EntityType<WalkerMob>> BUTCHER = register("butcher", WalkerMob::new, 0.7F, 2.0F,
        MobDefinition.builder().life(1500).damage(110).defense(36).knockbackTaken(0.2F).coins(2500).speed(0.34));
    public static final RegistryObject<EntityType<FlyerMob>> DEADLY_SPHERE = register("deadly_sphere",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.7F, 0.7F,
        MobDefinition.builder().life(500).damage(60).defense(30).knockbackTaken(0.5F).coins(2000).speed(0.45).followRange(48));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> NAILHEAD = register("nailhead",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.NAIL, 60.0F), 0.6F, 1.9F,
        MobDefinition.builder().life(1800).damage(80).defense(36).knockbackTaken(0.3F).coins(2500).speed(0.26));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> DR_MAN_FLY = register("dr_man_fly",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.TOXIC_FLASK, 55.0F), 0.6F, 1.8F,
        MobDefinition.builder().life(800).damage(60).defense(28).knockbackTaken(0.4F).coins(1500).speed(0.28));

    // --- Desert and Underground Desert -----------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> ANTLION = register("antlion",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.SAND_BALL, 13.0F), 0.9F, 0.7F,
        MobDefinition.builder().life(45).damage(10).defense(10).knockbackTaken(0.0F).coins(130).speed(0.0));
    public static final RegistryObject<EntityType<FlyerMob>> VULTURE = register("vulture",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.CHASER), 0.9F, 0.7F,
        MobDefinition.builder().life(40).damage(15).defense(4).knockbackTaken(0.7F).coins(60).speed(0.32).followRange(40));
    public static final RegistryObject<EntityType<WalkerMob>> ANTLION_CHARGER = register("antlion_charger", WalkerMob::new, 0.9F, 0.8F,
        MobDefinition.builder().life(50).damage(20).defense(8).knockbackTaken(0.5F).coins(130).speed(0.45));
    public static final RegistryObject<EntityType<FlyerMob>> ANTLION_SWARMER = register("antlion_swarmer",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.8F, 0.6F,
        MobDefinition.builder().life(45).damage(16).defense(6).knockbackTaken(0.6F).coins(130).speed(0.34));
    public static final WormMob.Spec TOMB_CRAWLER_SPEC = new WormMob.Spec(8, 0.7, 0.4, 0.07, true, false);
    public static final RegistryObject<EntityType<WormMob>> TOMB_CRAWLER = register("tomb_crawler",
        (type, level) -> new WormMob(type, level, TOMB_CRAWLER_SPEC), 0.7F, 0.7F,
        MobDefinition.builder().life(70).damage(20).defense(8).knockbackTaken(0.0F).coins(250).followRange(48));
    public static final RegistryObject<EntityType<WalkerMob>> MUMMY = register("mummy", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(130).damage(50).defense(18).knockbackTaken(0.45F).coins(500).speed(0.24));
    public static final RegistryObject<EntityType<WalkerMob>> GHOUL = register("ghoul", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(220).damage(55).defense(22).knockbackTaken(0.4F).coins(600).speed(0.3));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> DESERT_SPIRIT = register("desert_spirit",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_SPIRIT_FLAME, 50.0F), 0.7F, 1.7F,
        MobDefinition.builder().life(240).damage(60).defense(18).knockbackTaken(0.6F).coins(800));
    public static final WormMob.Spec DUNE_SPLICER_SPEC = new WormMob.Spec(12, 0.85, 0.5, 0.08, true, false);
    public static final RegistryObject<EntityType<WormMob>> DUNE_SPLICER = register("dune_splicer",
        (type, level) -> new WormMob(type, level, DUNE_SPLICER_SPEC), 0.9F, 0.9F,
        MobDefinition.builder().life(400).damage(60).defense(20).knockbackTaken(0.0F).coins(1000).followRange(56));

    // --- Snow and Ice caverns ---------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<SlimeMob>> ICE_SLIME = slime("ice_slime", 0.9F, 0.65F,
        MobDefinition.builder().life(40).damage(16).defense(4).coins(50));
    public static final RegistryObject<EntityType<SlimeMob>> SPIKED_ICE_SLIME = slime("spiked_ice_slime", 1.0F, 0.75F,
        MobDefinition.builder().life(60).damage(22).defense(8).coins(100));
    public static final RegistryObject<EntityType<WalkerMob>> ZOMBIE_ESKIMO = register("zombie_eskimo", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(45).damage(16).defense(8).knockbackTaken(0.5F).coins(70).speed(0.23).nocturnal());
    public static final RegistryObject<EntityType<FlyerMob>> ICE_BAT = register("ice_bat",
        (type, level) -> new FlyerMob(type, level, FlyerMob.Style.ERRATIC), 0.6F, 0.45F,
        MobDefinition.builder().life(16).damage(15).defense(4).knockbackTaken(0.8F).coins(90).speed(0.3));
    public static final RegistryObject<EntityType<WalkerMob>> SNOW_FLINX = register("snow_flinx", WalkerMob::new, 0.7F, 0.7F,
        MobDefinition.builder().life(70).damage(20).defense(6).knockbackTaken(0.5F).coins(100).speed(0.32));
    public static final RegistryObject<EntityType<WalkerMob>> UNDEAD_VIKING = register("undead_viking", WalkerMob::new, 0.6F, 1.8F,
        MobDefinition.builder().life(70).damage(28).defense(14).knockbackTaken(0.5F).coins(150).speed(0.26));
    public static final RegistryObject<EntityType<WalkerMob>> WOLF = register("wolf", WalkerMob::new, 0.9F, 0.85F,
        MobDefinition.builder().life(230).damage(60).defense(18).knockbackTaken(0.5F).coins(800).speed(0.42).nocturnal());
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ArcherMob>> ICE_GOLEM = register("ice_golem",
        (type, level) -> new com.terracraft.entity.mob.ArcherMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ICE_SHARD, 60.0F), 1.6F, 2.6F,
        MobDefinition.builder().life(2000).damage(70).defense(30).knockbackTaken(0.1F).coins(3000).speed(0.22));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> ICE_ELEMENTAL = register("ice_elemental",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ICE_SHARD, 45F, 2.5F, net.minecraft.sounds.SoundEvents.GLASS_BREAK), 0.8F, 1.6F,
        MobDefinition.builder().life(220).damage(55).defense(20).knockbackTaken(0.5F).coins(500).speed(0.25).followRange(40));
    public static final RegistryObject<EntityType<WalkerMob>> ICE_TORTOISE = register("ice_tortoise", WalkerMob::new, 1.1F, 0.9F,
        MobDefinition.builder().life(400).damage(60).defense(40).knockbackTaken(0.2F).coins(800).speed(0.25));
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.CasterMob>> ICY_MERMAN = register("icy_merman",
        (type, level) -> new com.terracraft.entity.mob.CasterMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.ENEMY_ICE_SHARD, 45.0F), 0.6F, 1.8F,
        MobDefinition.builder().life(240).damage(50).defense(20).knockbackTaken(0.5F).coins(600));
    public static final RegistryObject<EntityType<WalkerMob>> ARMORED_VIKING = register("armored_viking", WalkerMob::new, 0.6F, 1.9F,
        MobDefinition.builder().life(400).damage(60).defense(34).knockbackTaken(0.3F).coins(800).speed(0.3));

    // --- Floating Islands -----------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<com.terracraft.entity.mob.ShooterFlyerMob>> HARPY = register("harpy",
        (type, level) -> new com.terracraft.entity.mob.ShooterFlyerMob(type, level, () -> com.terracraft.entity.projectile.ProjectileKinds.HARPY_FEATHER, 12F, 3.0F, net.minecraft.sounds.SoundEvents.PARROT_AMBIENT), 0.8F, 1.4F,
        MobDefinition.builder().life(40).damage(25).defense(8).knockbackTaken(0.6F).coins(250).speed(0.3).followRange(40));

    private MobContent() {}

    public static void init() {
        com.terracraft.TerraCraft.modBus().addListener(MobContent::onAttributes);
    }

    public static List<RegistryObject<? extends EntityType<? extends TerrariaMob>>> all() {
        return ALL;
    }

    private static RegistryObject<EntityType<com.terracraft.entity.boss.SkeletronPrime.Arm>> primeArm(String name,
            com.terracraft.entity.boss.SkeletronPrime.Arm.Kind kind, MobDefinition.Builder stats) {
        return register(name, (type, level) -> new com.terracraft.entity.boss.SkeletronPrime.Arm(type, level, kind), 1.2F, 1.2F,
            stats.knockbackTaken(0.0F).coins(0).followRange(160));
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
