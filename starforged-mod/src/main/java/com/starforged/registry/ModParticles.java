package com.starforged.registry;

import com.starforged.Starforged;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Starforged.MODID);

    /** Twinkling golden-white star. */
    public static final RegistryObject<SimpleParticleType> STAR_SPARKLE = PARTICLES.register("star_sparkle", () -> new SimpleParticleType(true));
    /** Cyan astral glint. */
    public static final RegistryObject<SimpleParticleType> ASTRAL_GLINT = PARTICLES.register("astral_glint", () -> new SimpleParticleType(true));
    /** Dark violet void mote that drifts upward. */
    public static final RegistryObject<SimpleParticleType> VOID_MOTE = PARTICLES.register("void_mote", () -> new SimpleParticleType(true));
    /** Orange-hot meteor ember. */
    public static final RegistryObject<SimpleParticleType> METEOR_EMBER = PARTICLES.register("meteor_ember", () -> new SimpleParticleType(true));
    /** Large soft glow used for eclipse / beam effects. */
    public static final RegistryObject<SimpleParticleType> ECLIPSE_FLARE = PARTICLES.register("eclipse_flare", () -> new SimpleParticleType(true));

    /** A bright streak that crosses the night sky during a Starfall (client-side ambience). */
    public static final RegistryObject<SimpleParticleType> SHOOTING_STAR = PARTICLES.register("shooting_star", () -> new SimpleParticleType(true));

    /** Golden-orange spark that rises off sunfire (Sunforged). */
    public static final RegistryObject<SimpleParticleType> SOLAR_SPARK = PARTICLES.register("solar_spark", () -> new SimpleParticleType(true));
    /** Grey ash flake that drifts on the Sunlands wind. */
    public static final RegistryObject<SimpleParticleType> ASH_FLAKE = PARTICLES.register("ash_flake", () -> new SimpleParticleType(false));

    /** Silver moon dust that drifts slowly upward in low gravity (Moonforged). */
    public static final RegistryObject<SimpleParticleType> MOON_DUST = PARTICLES.register("moon_dust", () -> new SimpleParticleType(true));
    /** Pale blue-white lunar glimmer (Moonforged weapons and the Matriarch). */
    public static final RegistryObject<SimpleParticleType> LUNAR_GLIMMER = PARTICLES.register("lunar_glimmer", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
