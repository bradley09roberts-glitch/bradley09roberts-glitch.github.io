package com.terracraft.entity.projectile;

import com.terracraft.TerraCraft;
import com.terracraft.combat.TerraDamageTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Data definition of a projectile. One generic entity ({@link TerrariaProjectile}) plays every kind, so
 * adding an arrow, bullet, spell or thrown weapon is a single {@link ProjectileKinds} entry plus a texture.
 *
 * @param gravity        blocks/tick^2 pulled down each tick
 * @param drag           velocity multiplier per tick (1 = none)
 * @param lifetime       ticks before the projectile disappears
 * @param pierce         extra enemies it can pass through (-1 = infinite)
 * @param bounces        times it can bounce off blocks
 * @param homing         steering strength (0 = none) and search range
 * @param tileCollide    false = passes through blocks (spectral / magic)
 * @param behavior       special movement program
 * @param hitCooldown    ticks before the same enemy can be hit again (Terraria local immunity)
 * @param whipRange      how far a whip lashes out (blocks)
 * @param tagDamage      a whip's tag: extra damage its owner's minions deal to the enemy it struck
 */
public record ProjectileKind(
    Identifier id,
    Identifier texture,
    float size,
    float renderScale,
    Orientation orientation,
    boolean fullbright,
    double gravity,
    float drag,
    int lifetime,
    int pierce,
    int bounces,
    float homing,
    float homingRange,
    boolean tileCollide,
    Behavior behavior,
    int hitCooldown,
    ResourceKey<DamageType> damageType,
    @Nullable Supplier<? extends ParticleOptions> trail,
    @Nullable Supplier<Holder<MobEffect>> debuff,
    int debuffTicks,
    float debuffChance,
    int igniteTicks,
    float igniteChance,
    float explosionRadius,
    float whipRange,
    int tagDamage
) {
    public enum Orientation {
        /** Long axis follows the velocity (arrows, bullets, knives). */
        VELOCITY,
        /** Always faces the camera (orbs, bolts). */
        BILLBOARD,
        /** Faces the camera and spins (shuriken, boomerangs). */
        SPIN
    }

    public enum Behavior {
        STANDARD,
        /** Flies out, then returns to the thrower (boomerangs). */
        BOOMERANG,
        /** Lashes out from the owner's hand and back along the look direction (whips). */
        WHIP
    }

    public static Builder builder(String name) {
        return new Builder(TerraCraft.id(name));
    }

    public static final class Builder {
        private final Identifier id;
        private Identifier texture;
        private float size = 0.25F;
        private float renderScale = 0.5F;
        private Orientation orientation = Orientation.VELOCITY;
        private boolean fullbright;
        private double gravity;
        private float drag = 1.0F;
        private int lifetime = 100;
        private int pierce;
        private int bounces;
        private float homing;
        private float homingRange = 16.0F;
        private boolean tileCollide = true;
        private Behavior behavior = Behavior.STANDARD;
        private int hitCooldown = 10;
        private ResourceKey<DamageType> damageType = TerraDamageTypes.PROJECTILE;
        private Supplier<? extends ParticleOptions> trail;
        private Supplier<Holder<MobEffect>> debuff;
        private int debuffTicks;
        private float debuffChance;
        private float explosionRadius;
        private int igniteTicks;
        private float igniteChance;
        private float whipRange;
        private int tagDamage;

        private Builder(Identifier id) {
            this.id = id;
            this.texture = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/entity/projectile/" + id.getPath() + ".png");
        }

        public Builder size(float hitbox, float render) { this.size = hitbox; this.renderScale = render; return this; }
        public Builder orientation(Orientation orientation) { this.orientation = orientation; return this; }
        public Builder fullbright() { this.fullbright = true; return this; }
        public Builder gravity(double gravity) { this.gravity = gravity; return this; }
        public Builder drag(float drag) { this.drag = drag; return this; }
        public Builder lifetime(int ticks) { this.lifetime = ticks; return this; }
        public Builder pierce(int pierce) { this.pierce = pierce; return this; }
        public Builder bounces(int bounces) { this.bounces = bounces; return this; }
        public Builder homing(float strength, float range) { this.homing = strength; this.homingRange = range; return this; }
        public Builder noTileCollide() { this.tileCollide = false; return this; }
        public Builder behavior(Behavior behavior) { this.behavior = behavior; return this; }
        public Builder hitCooldown(int ticks) { this.hitCooldown = ticks; return this; }
        public Builder magic() { this.damageType = TerraDamageTypes.MAGIC; return this; }
        public Builder enemy() { this.damageType = TerraDamageTypes.ENEMY_PROJECTILE; return this; }
        public Builder trail(Supplier<? extends ParticleOptions> trail) { this.trail = trail; return this; }
        public Builder debuff(Supplier<Holder<MobEffect>> effect, int ticks, float chance) {
            this.debuff = effect; this.debuffTicks = ticks; this.debuffChance = chance; return this;
        }
        /** Terraria's "On Fire!" debuff: sets the target burning. */
        public Builder ignites(int ticks, float chance) { this.igniteTicks = ticks; this.igniteChance = chance; return this; }
        public Builder explosion(float radius) { this.explosionRadius = radius; return this; }
        /** A whip: lashes out {@code range} blocks over the projectile's lifetime and tags what it hits. */
        public Builder whip(float range, int tag) {
            this.behavior = Behavior.WHIP; this.whipRange = range; this.tagDamage = tag; this.tileCollide = false; this.pierce = -1;
            this.hitCooldown = 1000; return this;
        }
        public Builder texture(String name) {
            this.texture = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/entity/projectile/" + name + ".png");
            return this;
        }

        public ProjectileKind build() {
            return new ProjectileKind(id, texture, size, renderScale, orientation, fullbright, gravity, drag, lifetime, pierce,
                bounces, homing, homingRange, tileCollide, behavior, hitCooldown, damageType, trail, debuff, debuffTicks,
                debuffChance, igniteTicks, igniteChance, explosionRadius, whipRange, tagDamage);
        }
    }
}
