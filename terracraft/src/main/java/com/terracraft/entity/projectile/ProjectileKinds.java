package com.terracraft.entity.projectile;

import com.terracraft.TerraCraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of projectile kinds. Velocities given to {@link TerrariaProjectile#shoot} are Terraria shoot
 * speeds; {@link #VELOCITY_SCALE} converts them to blocks per tick.
 */
public final class ProjectileKinds {
    /** Terraria shoot speed (pixels/frame) -> Minecraft blocks/tick. */
    public static final float VELOCITY_SCALE = 0.25F;
    private static final Map<Identifier, ProjectileKind> KINDS = new LinkedHashMap<>();

    // ---------------------------------------------------------------- ranged
    public static final ProjectileKind WOODEN_ARROW = register(ProjectileKind.builder("wooden_arrow")
        .gravity(0.03).drag(0.995F).lifetime(200).size(0.25F, 0.6F));
    public static final ProjectileKind FLAMING_ARROW = register(ProjectileKind.builder("flaming_arrow")
        .gravity(0.03).drag(0.995F).lifetime(200).size(0.25F, 0.6F).fullbright()
        .trail(() -> ParticleTypes.FLAME).ignites(60, 0.33F));
    public static final ProjectileKind MUSKET_BALL = register(ProjectileKind.builder("musket_ball")
        .lifetime(60).size(0.15F, 0.3F).hitCooldown(4));
    public static final ProjectileKind SHURIKEN = register(ProjectileKind.builder("shuriken")
        .gravity(0.02).lifetime(100).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.SPIN).pierce(1));
    public static final ProjectileKind THROWING_KNIFE = register(ProjectileKind.builder("throwing_knife")
        .gravity(0.025).lifetime(100).size(0.25F, 0.55F));
    public static final ProjectileKind WOODEN_BOOMERANG = register(ProjectileKind.builder("wooden_boomerang")
        .lifetime(200).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.SPIN)
        .behavior(ProjectileKind.Behavior.BOOMERANG).pierce(-1).hitCooldown(8));

    // ---------------------------------------------------------------- magic
    public static final ProjectileKind SPARK = register(ProjectileKind.builder("spark")
        .gravity(0.01).lifetime(40).size(0.2F, 0.45F).orientation(ProjectileKind.Orientation.BILLBOARD)
        .fullbright().magic().trail(() -> ParticleTypes.FLAME).ignites(60, 0.5F));
    public static final ProjectileKind AMETHYST_BOLT = register(ProjectileKind.builder("amethyst_bolt")
        .lifetime(60).size(0.25F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD)
        .fullbright().magic().trail(() -> ParticleTypes.WITCH));
    public static final ProjectileKind MAGIC_MISSILE = register(ProjectileKind.builder("magic_missile")
        .lifetime(120).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD)
        .fullbright().magic().homing(0.12F, 20.0F).trail(() -> ParticleTypes.ELECTRIC_SPARK));

    public static final ProjectileKind VILETHORN = register(ProjectileKind.builder("vilethorn")
        .lifetime(24).size(0.4F, 0.8F).orientation(ProjectileKind.Orientation.VELOCITY).magic().pierce(-1).hitCooldown(10)
        .trail(() -> ParticleTypes.SPORE_BLOSSOM_AIR));

    // ---------------------------------------------------------------- dungeon
    public static final ProjectileKind WATER_BOLT = register(ProjectileKind.builder("water_bolt")
        .lifetime(240).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).bounces(6).pierce(2)
        .fullbright().magic().trail(() -> ParticleTypes.SPLASH));
    public static final ProjectileKind AQUA_STREAM = register(ProjectileKind.builder("aqua_stream")
        .gravity(0.008).lifetime(30).size(0.25F, 0.45F).orientation(ProjectileKind.Orientation.BILLBOARD).pierce(3)
        .magic().trail(() -> ParticleTypes.FALLING_WATER));
    public static final ProjectileKind BOOK_SKULL = register(ProjectileKind.builder("book_skull")
        .lifetime(120).size(0.35F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.06F, 16.0F)
        .magic().trail(() -> ParticleTypes.SMOKE));
    /** Skeletron's skulls (after its hands are gone) and the Dark Caster's water sphere. */
    public static final ProjectileKind SKELETRON_SKULL = register(ProjectileKind.builder("skeletron_skull")
        .lifetime(160).size(0.4F, 0.8F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.035F, 48.0F).noTileCollide()
        .enemy().trail(() -> ParticleTypes.SMOKE));
    public static final ProjectileKind WATER_SPHERE = register(ProjectileKind.builder("water_sphere")
        .lifetime(200).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.03F, 40.0F).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.FALLING_WATER));

    // ---------------------------------------------------------------- jungle
    public static final ProjectileKind STINGER = register(ProjectileKind.builder("stinger")
        .lifetime(80).size(0.2F, 0.45F).enemy().debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 140, 0.5F));
    /** Friendly bees (Bee Gun, Bee Keeper, Honey Comb): short-lived and homing. */
    public static final ProjectileKind BEE = register(ProjectileKind.builder("bee")
        .lifetime(90).size(0.25F, 0.45F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.2F, 12.0F).bounces(3)
        .hitCooldown(10));
    public static final ProjectileKind BEE_ARROW = register(ProjectileKind.builder("bee_arrow")
        .gravity(0.02).drag(0.995F).lifetime(160).size(0.25F, 0.6F).homing(0.05F, 10.0F).pierce(1));

    // ---------------------------------------------------------------- underworld
    public static final ProjectileKind IMP_FIREBALL = register(ProjectileKind.builder("imp_fireball")
        .lifetime(160).size(0.35F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.03F, 40.0F).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.FLAME).ignites(80, 1.0F));
    public static final ProjectileKind DEMON_SCYTHE = register(ProjectileKind.builder("demon_scythe")
        .lifetime(120).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.WITCH));
    // ---------------------------------------------------------------- Hardmode enemies
    public static final ProjectileKind VILE_SPIT = register(ProjectileKind.builder("vile_spit")
        .lifetime(120).size(0.35F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.SQUID_INK).debuff(() -> net.minecraft.world.effect.MobEffects.WEAKNESS, 140, 1.0F));
    public static final ProjectileKind PINK_LASER = register(ProjectileKind.builder("pink_laser")
        .lifetime(60).size(0.2F, 0.6F).fullbright().enemy());
    // ---------------------------------------------------------------- Queen Slime
    public static final ProjectileKind REGAL_GEL = register(ProjectileKind.builder("regal_gel")
        .gravity(0.035).lifetime(120).size(0.4F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).bounces(2)
        .fullbright().enemy().trail(() -> ParticleTypes.ITEM_SLIME));
    public static final ProjectileKind VOLATILE_GEL = register(ProjectileKind.builder("volatile_gel")
        .gravity(0.03).lifetime(90).size(0.35F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD).bounces(3).pierce(2)
        .fullbright().trail(() -> ParticleTypes.ITEM_SLIME));
    // ---------------------------------------------------------------- Pirate Invasion
    public static final ProjectileKind COPPER_COIN_SHOT = register(ProjectileKind.builder("copper_coin_shot")
        .lifetime(60).size(0.2F, 0.4F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().hitCooldown(4));
    public static final ProjectileKind SILVER_COIN_SHOT = register(ProjectileKind.builder("silver_coin_shot")
        .lifetime(60).size(0.2F, 0.4F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().hitCooldown(4));
    public static final ProjectileKind GOLD_COIN_SHOT = register(ProjectileKind.builder("gold_coin_shot")
        .lifetime(60).size(0.2F, 0.4F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().pierce(1).hitCooldown(4));
    public static final ProjectileKind PLATINUM_COIN_SHOT = register(ProjectileKind.builder("platinum_coin_shot")
        .lifetime(60).size(0.2F, 0.4F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().pierce(2).hitCooldown(4)
        .trail(() -> ParticleTypes.END_ROD));
    /** Thrown by players (Pirate's Cannonball) - explodes on impact without breaking blocks. */
    public static final ProjectileKind CANNONBALL = register(ProjectileKind.builder("cannonball")
        .gravity(0.05).lifetime(120).size(0.4F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).explosion(2.5F)
        .trail(() -> ParticleTypes.SMOKE));
    /** Fired by the Pirate Captain and the Flying Dutchman's cannons. */
    public static final ProjectileKind ENEMY_CANNONBALL = register(ProjectileKind.builder("enemy_cannonball").texture("cannonball")
        .gravity(0.04).lifetime(160).size(0.45F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).explosion(2.0F).enemy()
        .trail(() -> ParticleTypes.SMOKE));
    public static final ProjectileKind ENEMY_BULLET = register(ProjectileKind.builder("enemy_bullet").texture("musket_ball")
        .lifetime(60).size(0.15F, 0.3F).enemy());
    /** Thrown by the Frost Legion's Snow Balla: a heavy, arcing snowball that slows. */
    public static final ProjectileKind ENEMY_SNOWBALL = register(ProjectileKind.builder("enemy_snowball")
        .gravity(0.035).lifetime(100).size(0.35F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD).enemy()
        .trail(() -> ParticleTypes.SNOWFLAKE).debuff(() -> net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 0.5F));
    // ---------------------------------------------------------------- Chlorophyte and the Hardmode jungle
    /** Chlorophyte Claymore's swing: a slow green orb. */
    public static final ProjectileKind CHLOROPHYTE_ORB = register(ProjectileKind.builder("chlorophyte_orb")
        .lifetime(50).size(0.4F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).pierce(2).noTileCollide()
        .fullbright().trail(() -> ParticleTypes.COMPOSTER));
    /** Chlorophyte armor's Leaf Crystal (and the Leaf Blower): a fast spinning leaf. */
    public static final ProjectileKind CRYSTAL_LEAF = register(ProjectileKind.builder("crystal_leaf")
        .lifetime(60).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.SPIN).fullbright().trail(() -> ParticleTypes.COMPOSTER));
    // ---------------------------------------------------------------- Plantera
    public static final ProjectileKind PLANTERA_SEED = register(ProjectileKind.builder("plantera_seed")
        .lifetime(100).size(0.25F, 0.5F).enemy());
    public static final ProjectileKind POISON_SEED = register(ProjectileKind.builder("poison_seed")
        .lifetime(100).size(0.25F, 0.5F).enemy().trail(() -> ParticleTypes.ITEM_SLIME)
        .debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 200, 1.0F));
    /** Bounces around the arena for a long time. */
    public static final ProjectileKind THORN_BALL = register(ProjectileKind.builder("thorn_ball")
        .lifetime(400).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).bounces(12).enemy());
    /** Phase two: slow pink spores that drift after the player. */
    public static final ProjectileKind PLANTERA_SPORE = register(ProjectileKind.builder("plantera_spore")
        .lifetime(240).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.04F, 30.0F).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.SPORE_BLOSSOM_AIR));
    /** Seedler's swing: seeds that burst on hitting something. */
    public static final ProjectileKind SEEDLER_SEED = register(ProjectileKind.builder("seedler_seed")
        .lifetime(40).size(0.25F, 0.5F).pierce(1).trail(() -> ParticleTypes.COMPOSTER));
    // ---------------------------------------------------------------- Lihzahrd Temple
    /** Super Dart Trap: a fast poison dart. */
    public static final ProjectileKind POISON_DART = register(ProjectileKind.builder("poison_dart")
        .lifetime(40).size(0.2F, 0.5F).enemy().debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 200, 1.0F));
    /** Lihzahrd and Golem fireballs. */
    public static final ProjectileKind GOLEM_FIREBALL = register(ProjectileKind.builder("golem_fireball")
        .lifetime(120).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().enemy()
        .trail(() -> ParticleTypes.FLAME).ignites(80, 0.5F));
    public static final ProjectileKind GOLEM_LASER = register(ProjectileKind.builder("golem_laser").texture("mech_laser")
        .lifetime(60).size(0.2F, 0.6F).fullbright().enemy());
    /** Heat Ray: a piercing golden beam. */
    public static final ProjectileKind HEAT_RAY = register(ProjectileKind.builder("heat_ray")
        .lifetime(40).size(0.2F, 0.6F).pierce(3).fullbright().magic().trail(() -> ParticleTypes.SMALL_FLAME));
    /** Possessed Hatchet: a homing boomerang axe. */
    public static final ProjectileKind POSSESSED_HATCHET = register(ProjectileKind.builder("possessed_hatchet")
        .lifetime(200).size(0.5F, 0.9F).orientation(ProjectileKind.Orientation.SPIN).homing(0.12F, 20.0F).pierce(4)
        .behavior(ProjectileKind.Behavior.BOOMERANG).noTileCollide());
    // ---------------------------------------------------------------- Duke Fishron
    /** Duke Fishron's bubbles: slow, homing, pop on contact. */
    public static final ProjectileKind DETONATING_BUBBLE = register(ProjectileKind.builder("detonating_bubble")
        .lifetime(200).size(0.6F, 0.9F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.04F, 24.0F).noTileCollide()
        .enemy().trail(() -> ParticleTypes.BUBBLE));
    /** Razorblade Typhoon: a spinning blade that seeks enemies. */
    public static final ProjectileKind RAZORBLADE = register(ProjectileKind.builder("razorblade")
        .lifetime(120).size(0.5F, 0.9F).orientation(ProjectileKind.Orientation.SPIN).homing(0.15F, 20.0F).pierce(3).noTileCollide()
        .magic().trail(() -> ParticleTypes.BUBBLE));
    /** Bubble Gun. */
    public static final ProjectileKind BUBBLE = register(ProjectileKind.builder("bubble")
        .lifetime(40).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD).magic().trail(() -> ParticleTypes.BUBBLE));
    // ---------------------------------------------------------------- the Dungeon after Plantera
    public static final ProjectileKind SHADOWBEAM = register(ProjectileKind.builder("shadowbeam").texture("shadowbeam")
        .lifetime(50).size(0.2F, 0.6F).pierce(4).bounces(4).fullbright().magic().trail(() -> ParticleTypes.WITCH));
    public static final ProjectileKind ENEMY_SHADOWBEAM = register(ProjectileKind.builder("enemy_shadowbeam").texture("shadowbeam")
        .lifetime(50).size(0.2F, 0.6F).bounces(3).fullbright().enemy().trail(() -> ParticleTypes.WITCH));
    /** Ragged Caster: slow homing lost souls. */
    public static final ProjectileKind LOST_SOUL = register(ProjectileKind.builder("lost_soul")
        .lifetime(160).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.05F, 30.0F).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.SOUL));
    public static final ProjectileKind PALADINS_HAMMER = register(ProjectileKind.builder("paladins_hammer")
        .lifetime(200).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).behavior(ProjectileKind.Behavior.BOOMERANG).pierce(-1)
        .hitCooldown(8).noTileCollide());
    public static final ProjectileKind ENEMY_HAMMER = register(ProjectileKind.builder("enemy_hammer").texture("paladins_hammer")
        .lifetime(80).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).noTileCollide().enemy());
    public static final ProjectileKind ENEMY_ROCKET = register(ProjectileKind.builder("enemy_rocket")
        .lifetime(100).size(0.3F, 0.6F).explosion(2.5F).enemy().trail(() -> ParticleTypes.SMOKE));
    // ---------------------------------------------------------------- Pumpkin Moon and Frost Moon
    public static final ProjectileKind FLAMING_WOOD = register(ProjectileKind.builder("flaming_wood")
        .gravity(0.03).lifetime(120).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.SPIN).fullbright().enemy()
        .trail(() -> ParticleTypes.FLAME).ignites(80, 1.0F));
    public static final ProjectileKind FLAMING_SCYTHE = register(ProjectileKind.builder("flaming_scythe")
        .lifetime(120).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).homing(0.02F, 30.0F).noTileCollide().fullbright().enemy()
        .trail(() -> ParticleTypes.FLAME).ignites(80, 0.5F));
    public static final ProjectileKind ENEMY_PINE_NEEDLE = register(ProjectileKind.builder("enemy_pine_needle").texture("pine_needle")
        .lifetime(60).size(0.2F, 0.5F).enemy());
    public static final ProjectileKind ENEMY_ICE_SHARD = register(ProjectileKind.builder("enemy_ice_shard").texture("ice_shard")
        .lifetime(80).size(0.3F, 0.6F).fullbright().enemy().trail(() -> ParticleTypes.SNOWFLAKE)
        .debuff(() -> net.minecraft.world.effect.MobEffects.SLOWNESS, 80, 0.5F));
    public static final ProjectileKind PUMPKIN_HEAD = register(ProjectileKind.builder("pumpkin_head")
        .lifetime(60).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.SPIN).homing(0.1F, 16.0F).pierce(1).noTileCollide()
        .fullbright().trail(() -> ParticleTypes.FLAME));
    public static final ProjectileKind BAT = register(ProjectileKind.builder("bat")
        .lifetime(90).size(0.4F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.12F, 24.0F).noTileCollide().magic());
    public static final ProjectileKind ORNAMENT = register(ProjectileKind.builder("ornament")
        .gravity(0.02).lifetime(60).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).explosion(1.5F).fullbright());
    public static final ProjectileKind PINE_NEEDLE = register(ProjectileKind.builder("pine_needle")
        .lifetime(50).size(0.2F, 0.5F).pierce(1).magic());
    public static final ProjectileKind ELF_FLAME = register(ProjectileKind.builder("elf_flame").texture("cursed_flame")
        .lifetime(16).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.BILLBOARD).pierce(-1).hitCooldown(6).noTileCollide().fullbright()
        .magic().trail(() -> ParticleTypes.FLAME).ignites(60, 0.5F));
    public static final ProjectileKind SNOWFLAKE = register(ProjectileKind.builder("snowflake")
        .lifetime(60).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.SPIN).pierce(2).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.SNOWFLAKE));
    public static final ProjectileKind ICE_SHARD = register(ProjectileKind.builder("ice_shard")
        .gravity(0.02).lifetime(80).size(0.3F, 0.6F).pierce(1).fullbright().magic().trail(() -> ParticleTypes.SNOWFLAKE));
    // ---------------------------------------------------------------- Empress of Light
    public static final ProjectileKind PRISMATIC_BOLT = register(ProjectileKind.builder("prismatic_bolt")
        .lifetime(180).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.03F, 40.0F).noTileCollide().fullbright().enemy()
        .trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind ETHEREAL_LANCE = register(ProjectileKind.builder("ethereal_lance")
        .lifetime(70).size(0.3F, 1.2F).noTileCollide().fullbright().enemy().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind SUN_RAY = register(ProjectileKind.builder("sun_ray")
        .lifetime(50).size(0.4F, 1.0F).noTileCollide().fullbright().enemy().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind NIGHTGLOW = register(ProjectileKind.builder("nightglow")
        .lifetime(120).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.12F, 24.0F).noTileCollide().fullbright().magic()
        .trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind STARLIGHT = register(ProjectileKind.builder("starlight")
        .lifetime(16).size(0.3F, 0.7F).pierce(2).noTileCollide().fullbright().trail(() -> ParticleTypes.END_ROD));
    // ---------------------------------------------------------------- Martian Madness
    public static final ProjectileKind MARTIAN_LASER = register(ProjectileKind.builder("martian_laser")
        .lifetime(60).size(0.2F, 0.6F).fullbright().enemy());
    public static final ProjectileKind LASER_BEAM = register(ProjectileKind.builder("laser_beam").texture("martian_laser")
        .lifetime(40).size(0.2F, 0.6F).pierce(2).fullbright().magic());
    public static final ProjectileKind INFLUX_WAVE = register(ProjectileKind.builder("influx_wave")
        .lifetime(40).size(0.5F, 0.9F).orientation(ProjectileKind.Orientation.SPIN).homing(0.1F, 16.0F).pierce(2).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.ELECTRIC_SPARK));
    // ---------------------------------------------------------------- Lunatic Cultist, Celestial Pillars, Moon Lord
    public static final ProjectileKind CULTIST_FIREBALL = register(ProjectileKind.builder("cultist_fireball")
        .lifetime(160).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.04F, 40.0F).noTileCollide().fullbright().enemy()
        .trail(() -> ParticleTypes.FLAME).ignites(80, 0.5F));
    public static final ProjectileKind CULTIST_LIGHTNING = register(ProjectileKind.builder("cultist_lightning")
        .lifetime(50).size(0.3F, 0.8F).noTileCollide().fullbright().enemy().trail(() -> ParticleTypes.ELECTRIC_SPARK));
    public static final ProjectileKind ICE_MIST = register(ProjectileKind.builder("ice_mist")
        .lifetime(200).size(1.2F, 2.0F).orientation(ProjectileKind.Orientation.SPIN).pierce(-1).hitCooldown(15).noTileCollide().fullbright()
        .enemy().trail(() -> ParticleTypes.SNOWFLAKE).debuff(() -> net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 1.0F));
    public static final ProjectileKind ANCIENT_LIGHT = register(ProjectileKind.builder("ancient_light")
        .lifetime(140).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.06F, 40.0F).noTileCollide().fullbright()
        .enemy().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind CELESTIAL_SHOT = register(ProjectileKind.builder("celestial_shot")
        .lifetime(80).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().enemy().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind PHANTASMAL_EYE = register(ProjectileKind.builder("phantasmal_eye")
        .lifetime(160).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.05F, 48.0F).noTileCollide().fullbright()
        .enemy().trail(() -> ParticleTypes.SOUL_FIRE_FLAME));
    public static final ProjectileKind PHANTASMAL_SPHERE = register(ProjectileKind.builder("phantasmal_sphere")
        .lifetime(200).size(0.9F, 1.4F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide().fullbright().enemy()
        .trail(() -> ParticleTypes.SOUL_FIRE_FLAME));
    public static final ProjectileKind PHANTASMAL_BOLT = register(ProjectileKind.builder("phantasmal_bolt")
        .lifetime(60).size(0.3F, 0.8F).noTileCollide().fullbright().enemy().trail(() -> ParticleTypes.SOUL_FIRE_FLAME));
    public static final ProjectileKind DEATHRAY = register(ProjectileKind.builder("deathray")
        .lifetime(30).size(0.9F, 1.6F).pierce(-1).hitCooldown(10).noTileCollide().fullbright().enemy().trail(() -> ParticleTypes.END_ROD));
    // player weapons
    public static final ProjectileKind SOLAR_ERUPTION = register(ProjectileKind.builder("solar_eruption")
        .lifetime(20).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.BILLBOARD).pierce(-1).hitCooldown(8).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.FLAME).ignites(100, 1.0F));
    public static final ProjectileKind DAYBREAK = register(ProjectileKind.builder("daybreak")
        .gravity(0.02).lifetime(80).size(0.4F, 1.2F).pierce(2).fullbright().trail(() -> ParticleTypes.FLAME).ignites(160, 1.0F));
    public static final ProjectileKind NEBULA_BLAZE = register(ProjectileKind.builder("nebula_blaze")
        .lifetime(100).size(0.4F, 0.8F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.14F, 30.0F).noTileCollide().fullbright().magic()
        .trail(() -> ParticleTypes.REVERSE_PORTAL));
    public static final ProjectileKind NEBULA_ARCANUM = register(ProjectileKind.builder("nebula_arcanum")
        .lifetime(160).size(0.8F, 1.3F).orientation(ProjectileKind.Orientation.SPIN).homing(0.08F, 30.0F).pierce(4).noTileCollide().fullbright()
        .magic().trail(() -> ParticleTypes.REVERSE_PORTAL));
    public static final ProjectileKind STARDUST_DRAGON = register(ProjectileKind.builder("stardust_dragon")
        .lifetime(200).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.18F, 32.0F).pierce(-1).hitCooldown(10)
        .noTileCollide().fullbright().magic().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind STARDUST_CELL = register(ProjectileKind.builder("stardust_cell")
        .lifetime(120).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.1F, 24.0F).pierce(2).noTileCollide().fullbright()
        .magic().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind MEOWMERE = register(ProjectileKind.builder("meowmere")
        .gravity(0.03).lifetime(100).size(0.5F, 0.9F).orientation(ProjectileKind.Orientation.SPIN).bounces(4).pierce(3).fullbright()
        .trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind STAR_WRATH = register(ProjectileKind.builder("star_wrath")
        .lifetime(60).size(0.5F, 0.9F).orientation(ProjectileKind.Orientation.SPIN).pierce(2).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind LAST_PRISM = register(ProjectileKind.builder("last_prism")
        .lifetime(30).size(0.4F, 0.8F).pierce(-1).hitCooldown(6).noTileCollide().fullbright().magic().trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind LUNAR_FLARE = register(ProjectileKind.builder("lunar_flare")
        .lifetime(80).size(0.5F, 0.9F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.12F, 24.0F).explosion(2.0F).noTileCollide()
        .fullbright().magic().trail(() -> ParticleTypes.SOUL_FIRE_FLAME));
    // ---------------------------------------------------------------- mechanical bosses
    public static final ProjectileKind MECH_LASER = register(ProjectileKind.builder("mech_laser")
        .lifetime(80).size(0.2F, 0.6F).noTileCollide().fullbright().enemy());
    public static final ProjectileKind CURSED_FLAME = register(ProjectileKind.builder("cursed_flame")
        .lifetime(100).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide().homing(0.02F, 30.0F)
        .fullbright().enemy().trail(() -> ParticleTypes.SOUL_FIRE_FLAME).ignites(100, 1.0F));
    /** Spazmatism's second form: a short-lived stream of cursed fire. */
    public static final ProjectileKind CURSED_SPRAY = register(ProjectileKind.builder("cursed_spray").texture("cursed_flame")
        .lifetime(18).drag(0.95F).size(0.5F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide().pierce(-1)
        .fullbright().enemy().trail(() -> ParticleTypes.SOUL_FIRE_FLAME).ignites(80, 0.5F));
    public static final ProjectileKind PRIME_BOMB = register(ProjectileKind.builder("prime_bomb")
        .gravity(0.04).lifetime(120).size(0.4F, 0.7F).orientation(ProjectileKind.Orientation.SPIN).explosion(2.5F)
        .enemy().trail(() -> ParticleTypes.SMOKE));
    public static final ProjectileKind WOF_LASER = register(ProjectileKind.builder("wof_laser")
        .lifetime(60).size(0.2F, 0.6F).noTileCollide().fullbright().enemy());
    public static final ProjectileKind FLAMELASH = register(ProjectileKind.builder("flamelash")
        .lifetime(140).size(0.35F, 0.7F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.18F, 24.0F)
        .fullbright().magic().trail(() -> ParticleTypes.FLAME).ignites(120, 0.5F));
    public static final ProjectileKind FLOWER_OF_FIRE = register(ProjectileKind.builder("flower_of_fire")
        .gravity(0.02).lifetime(120).size(0.35F, 0.6F).orientation(ProjectileKind.Orientation.SPIN).bounces(6)
        .fullbright().magic().trail(() -> ParticleTypes.FLAME).ignites(120, 0.5F));
    public static final ProjectileKind HELLWING = register(ProjectileKind.builder("hellwing")
        .lifetime(100).size(0.35F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.12F, 20.0F).noTileCollide()
        .fullbright().trail(() -> ParticleTypes.FLAME).ignites(80, 0.5F));
    public static final ProjectileKind PLAYER_DEMON_SCYTHE = register(ProjectileKind.builder("player_demon_scythe")
        .texture("demon_scythe").drag(1.04F).lifetime(60).size(0.6F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).pierce(-1)
        .noTileCollide().fullbright().magic().trail(() -> ParticleTypes.WITCH));
    public static final ProjectileKind LASER = register(ProjectileKind.builder("laser")
        .lifetime(40).size(0.15F, 0.6F).pierce(2).fullbright().magic());

    // ---------------------------------------------------------------- goblin army
    public static final ProjectileKind CHAOS_BALL = register(ProjectileKind.builder("chaos_ball")
        .lifetime(140).size(0.35F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.02F, 30.0F).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.WITCH));
    public static final ProjectileKind ENEMY_ARROW = register(ProjectileKind.builder("enemy_arrow").texture("wooden_arrow")
        .gravity(0.03).drag(0.995F).lifetime(160).size(0.25F, 0.6F).enemy());

    // ---------------------------------------------------------------- meteorite
    public static final ProjectileKind SPACE_LASER = register(ProjectileKind.builder("space_laser").texture("laser")
        .lifetime(40).size(0.15F, 0.6F).pierce(1).fullbright().magic());

    // ---------------------------------------------------------------- summoner (minion and sentry shots, whips)
    public static final ProjectileKind MINION_FIREBALL = register(ProjectileKind.builder("minion_fireball").texture("imp_fireball")
        .lifetime(60).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.FLAME).ignites(60, 0.5F));
    public static final ProjectileKind MINION_STINGER = register(ProjectileKind.builder("minion_stinger").texture("stinger")
        .lifetime(50).size(0.2F, 0.45F).debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 100, 0.5F));
    public static final ProjectileKind MINION_LASER = register(ProjectileKind.builder("minion_laser").texture("mech_laser")
        .lifetime(40).size(0.2F, 0.6F).fullbright());
    public static final ProjectileKind PYGMY_SPEAR = register(ProjectileKind.builder("pygmy_spear")
        .gravity(0.02).lifetime(60).size(0.25F, 0.7F).pierce(1));
    public static final ProjectileKind MINI_SHARK = register(ProjectileKind.builder("mini_shark")
        .lifetime(60).size(0.35F, 0.7F).homing(0.1F, 16.0F).noTileCollide());
    public static final ProjectileKind UFO_LASER = register(ProjectileKind.builder("ufo_laser").texture("martian_laser")
        .lifetime(30).size(0.2F, 0.6F).pierce(1).fullbright());
    public static final ProjectileKind STARDUST_SHOT = register(ProjectileKind.builder("stardust_shot")
        .lifetime(50).size(0.25F, 0.45F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind RAINBOW_BOLT = register(ProjectileKind.builder("rainbow_bolt")
        .lifetime(60).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD).noTileCollide().fullbright().pierce(1)
        .trail(() -> ParticleTypes.END_ROD));
    public static final ProjectileKind PORTAL_LASER = register(ProjectileKind.builder("portal_laser")
        .lifetime(20).size(0.3F, 0.8F).pierce(-1).hitCooldown(10).noTileCollide().fullbright());

    // ---------------------------------------------------------------- Solar Eclipse
    public static final ProjectileKind NAIL = register(ProjectileKind.builder("nail")
        .gravity(0.01).lifetime(80).size(0.2F, 0.45F).enemy().explosion(1.5F));
    public static final ProjectileKind TOXIC_FLASK = register(ProjectileKind.builder("toxic_flask")
        .gravity(0.04).lifetime(100).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.SPIN).enemy()
        .debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 160, 1.0F).explosion(1.5F));
    public static final ProjectileKind PLAYER_NAIL = register(ProjectileKind.builder("player_nail").texture("nail")
        .gravity(0.01).lifetime(80).size(0.2F, 0.45F).explosion(2.0F));
    public static final ProjectileKind PLAYER_TOXIC_FLASK = register(ProjectileKind.builder("player_toxic_flask").texture("toxic_flask")
        .gravity(0.04).lifetime(100).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.SPIN).magic()
        .debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 160, 1.0F).explosion(2.0F));
    public static final ProjectileKind DEATH_SICKLE = register(ProjectileKind.builder("death_sickle")
        .drag(0.97F).lifetime(50).size(0.7F, 1.1F).orientation(ProjectileKind.Orientation.SPIN).pierce(-1).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.SOUL));
    public static final ProjectileKind TERRA_BEAM = register(ProjectileKind.builder("terra_beam")
        .lifetime(40).size(0.6F, 1.2F).orientation(ProjectileKind.Orientation.SPIN).pierce(3).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.HAPPY_VILLAGER));
    public static final ProjectileKind TRUE_EXCALIBUR_BEAM = register(ProjectileKind.builder("true_excalibur_beam")
        .lifetime(30).size(0.5F, 1.0F).orientation(ProjectileKind.Orientation.SPIN).pierce(2).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.END_ROD));

    // ---------------------------------------------------------------- Desert, Snow, Sky (Stage 10)
    public static final ProjectileKind SAND_BALL = register(ProjectileKind.builder("sand_ball")
        .gravity(0.03).lifetime(80).size(0.3F, 0.5F).orientation(ProjectileKind.Orientation.BILLBOARD).enemy());
    public static final ProjectileKind HARPY_FEATHER = register(ProjectileKind.builder("harpy_feather")
        .lifetime(80).size(0.25F, 0.55F).enemy());
    public static final ProjectileKind ENEMY_SPIRIT_FLAME = register(ProjectileKind.builder("enemy_spirit_flame").texture("spirit_flame")
        .lifetime(120).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.04F, 30.0F).noTileCollide()
        .fullbright().enemy().trail(() -> ParticleTypes.SOUL_FIRE_FLAME));
    public static final ProjectileKind SPIRIT_FLAME = register(ProjectileKind.builder("spirit_flame")
        .lifetime(90).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).homing(0.12F, 20.0F).noTileCollide()
        .fullbright().magic().trail(() -> ParticleTypes.SOUL_FIRE_FLAME));
    public static final ProjectileKind ICE_BOLT = register(ProjectileKind.builder("ice_bolt")
        .lifetime(30).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright()
        .trail(() -> ParticleTypes.SNOWFLAKE).debuff(() -> net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 0.5F));
    public static final ProjectileKind FROST_BOLT = register(ProjectileKind.builder("frost_bolt")
        .lifetime(60).size(0.3F, 0.6F).orientation(ProjectileKind.Orientation.BILLBOARD).fullbright().magic()
        .trail(() -> ParticleTypes.SNOWFLAKE).debuff(() -> net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 1.0F));
    public static final ProjectileKind ICE_BOOMERANG = register(ProjectileKind.builder("ice_boomerang")
        .lifetime(200).size(0.5F, 0.8F).orientation(ProjectileKind.Orientation.SPIN)
        .behavior(ProjectileKind.Behavior.BOOMERANG).pierce(-1).hitCooldown(8).trail(() -> ParticleTypes.SNOWFLAKE));
    public static final ProjectileKind STARFURY_STAR = register(ProjectileKind.builder("starfury_star")
        .lifetime(60).size(0.4F, 0.8F).orientation(ProjectileKind.Orientation.SPIN).pierce(2).noTileCollide().fullbright()
        .trail(() -> ParticleTypes.END_ROD));

    private ProjectileKinds() {}

    private static ProjectileKind register(ProjectileKind.Builder builder) {
        ProjectileKind kind = builder.build();
        KINDS.put(kind.id(), kind);
        return kind;
    }

    /** Registers a kind defined outside this class (e.g. whip lashes in SummonContent). */
    public static ProjectileKind registerExternal(ProjectileKind.Builder builder) {
        return register(builder);
    }

    public static ProjectileKind get(Identifier id) {
        return KINDS.getOrDefault(id, WOODEN_ARROW);
    }

    public static Collection<ProjectileKind> all() {
        return Collections.unmodifiableCollection(KINDS.values());
    }

    /** Forces class init; logs the number of kinds. */
    public static void init() {
        TerraCraft.LOGGER.debug("{} projectile kinds registered", KINDS.size());
    }
}
