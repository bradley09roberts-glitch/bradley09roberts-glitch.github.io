package com.terracraft.entity.mob;

/**
 * Terraria statistics of an enemy type, in Terraria's own units (Classic mode): life, contact damage,
 * defense, {@code knockbackTaken} (Terraria's knockBackResist: 1 = full knockback, 0 = immune) and
 * {@code coinValue} in copper. Expert/Master scaling is applied at spawn time.
 */
public record MobDefinition(
    int life,
    int damage,
    int defense,
    float knockbackTaken,
    int coinValue,
    double moveSpeed,
    double followRange,
    boolean despawnsAtDay
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int life = 10;
        private int damage = 5;
        private int defense;
        private float knockbackTaken = 1.0F;
        private int coinValue;
        private double moveSpeed = 0.25;
        private double followRange = 32.0;
        private boolean despawnsAtDay;

        public Builder life(int life) { this.life = life; return this; }
        public Builder damage(int damage) { this.damage = damage; return this; }
        public Builder defense(int defense) { this.defense = defense; return this; }
        public Builder knockbackTaken(float value) { this.knockbackTaken = value; return this; }
        public Builder coins(int copper) { this.coinValue = copper; return this; }
        public Builder speed(double speed) { this.moveSpeed = speed; return this; }
        public Builder followRange(double range) { this.followRange = range; return this; }
        /** Night enemies (zombies, demon eyes) leave when the sun rises. */
        public Builder nocturnal() { this.despawnsAtDay = true; return this; }

        public MobDefinition build() {
            return new MobDefinition(life, damage, defense, knockbackTaken, coinValue, moveSpeed, followRange, despawnsAtDay);
        }
    }
}
