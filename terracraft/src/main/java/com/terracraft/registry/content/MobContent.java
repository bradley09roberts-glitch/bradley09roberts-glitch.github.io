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
