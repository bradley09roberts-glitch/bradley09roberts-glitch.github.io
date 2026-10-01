package com.starforged.client.particle;

import com.starforged.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/**
 * One full-bright, animated, fading particle class configured per Starforged particle type.
 */
public class StarforgedParticle extends SingleQuadParticle {
    public enum Style {
        STAR(0xFFF3C2, 0xFFD27A, 0.0F, 22, 0.16F, 0.96F, false),
        GLINT(0x8FF7FF, 0x6A8CFF, -0.002F, 26, 0.12F, 0.94F, false),
        VOID(0xB06CFF, 0x2A0F4F, -0.004F, 34, 0.18F, 0.95F, false),
        EMBER(0xFFC060, 0xFF3A10, -0.006F, 24, 0.14F, 0.93F, false),
        FLARE(0xFFFFFF, 0xB45CFF, 0.0F, 14, 0.6F, 0.9F, false),
        SHOOTING(0xFFFFFF, 0xFFD27A, 0.0F, 34, 0.5F, 1.0F, true),
        SOLAR(0xFFF6B0, 0xFF6A10, -0.008F, 28, 0.13F, 0.94F, false),
        ASH(0x9a948e, 0x4a4542, 0.0015F, 90, 0.09F, 0.98F, false),
        DUST(0xE8ECF4, 0xB4BCCC, -0.0012F, 80, 0.045F, 0.985F, false),
        GLIMMER(0xFFFFFF, 0x9CC8FF, -0.004F, 30, 0.075F, 0.93F, false),
        SPARK(0xF0FAFF, 0x3A7CFF, 0.0F, 9, 0.06F, 0.8F, false),
        WISP(0xAEB8CC, 0x5E6880, 0.0F, 60, 0.12F, 0.99F, false);

        final int start;
        final int end;
        final float gravity;
        final int life;
        final float size;
        final float friction;
        final boolean trail;

        Style(int start, int end, float gravity, int life, float size, float friction, boolean trail) {
            this.start = start;
            this.end = end;
            this.gravity = gravity;
            this.life = life;
            this.size = size;
            this.friction = friction;
            this.trail = trail;
        }
    }

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;

    protected StarforgedParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Style style) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = style.gravity;
        this.friction = style.friction;
        this.hasPhysics = false;
        this.lifetime = (int) (style.life * (0.7F + this.random.nextFloat() * 0.6F));
        this.baseSize = style.size * (0.7F + this.random.nextFloat() * 0.6F);
        this.quadSize = this.baseSize;
        this.applyColor(0.0F);
        this.setSpriteFromAge(sprites);
    }

    private void applyColor(float t) {
        int a = this.style.start;
        int b = this.style.end;
        float r = ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t) / 255.0F;
        float g = ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t) / 255.0F;
        float bl = ((a & 255) + ((b & 255) - (a & 255)) * t) / 255.0F;
        this.setColor(r, g, bl);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float t = this.age / (float) this.lifetime;
        this.setSpriteFromAge(this.sprites);
        this.applyColor(t);
        this.setAlpha(t < 0.7F ? 1.0F : 1.0F - (t - 0.7F) / 0.3F);
        this.quadSize = this.baseSize * (this.style == Style.FLARE ? 1.0F + t : (1.0F - t * 0.5F));
        if (this.style.trail) {
            for (int i = 0; i < 3; i++) {
                double f = i / 3.0;
                this.level.addAlwaysVisibleParticle(ModParticles.STAR_SPARKLE.get(), this.x - this.xd * f, this.y - this.yd * f, this.z - this.zd * f, 0, 0, 0);
            }
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float partialTick) {
        if (this.style == Style.ASH || this.style == Style.WISP) {
            return super.getLightCoords(partialTick);
        }
        return 15728880;
    }

    private static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
        return (SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) ->
            new StarforgedParticle(level, x, y, z, xd, yd, zd, sprites, style);
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STAR_SPARKLE.get(), sprites -> provider(sprites, Style.STAR));
        event.registerSpriteSet(ModParticles.ASTRAL_GLINT.get(), sprites -> provider(sprites, Style.GLINT));
        event.registerSpriteSet(ModParticles.VOID_MOTE.get(), sprites -> provider(sprites, Style.VOID));
        event.registerSpriteSet(ModParticles.METEOR_EMBER.get(), sprites -> provider(sprites, Style.EMBER));
        event.registerSpriteSet(ModParticles.ECLIPSE_FLARE.get(), sprites -> provider(sprites, Style.FLARE));
        event.registerSpriteSet(ModParticles.SHOOTING_STAR.get(), sprites -> provider(sprites, Style.SHOOTING));
        event.registerSpriteSet(ModParticles.SOLAR_SPARK.get(), sprites -> provider(sprites, Style.SOLAR));
        event.registerSpriteSet(ModParticles.ASH_FLAKE.get(), sprites -> provider(sprites, Style.ASH));
        event.registerSpriteSet(ModParticles.MOON_DUST.get(), sprites -> provider(sprites, Style.DUST));
        event.registerSpriteSet(ModParticles.LUNAR_GLIMMER.get(), sprites -> provider(sprites, Style.GLIMMER));
        event.registerSpriteSet(ModParticles.STATIC_SPARK.get(), sprites -> provider(sprites, Style.SPARK));
        event.registerSpriteSet(ModParticles.STORM_WISP.get(), sprites -> provider(sprites, Style.WISP));
    }
}
