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
