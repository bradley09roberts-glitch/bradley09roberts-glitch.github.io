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

    private ProjectileKinds() {}

    private static ProjectileKind register(ProjectileKind.Builder builder) {
        ProjectileKind kind = builder.build();
        KINDS.put(kind.id(), kind);
        return kind;
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
