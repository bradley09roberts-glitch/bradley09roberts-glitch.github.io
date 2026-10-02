package com.terracraft.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.combat.DamageClass;
import com.terracraft.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Terraria statistics of an item, stored as the {@code terracraft:stats} data component.
 * <p>
 * Every TerraCraft item declares its stats in one line through {@link #builder()}; gameplay code only
 * ever reads them from the stack, so per-stack overrides (commands, loot functions, future reforging
 * prefixes) work everywhere automatically.
 * <p>
 * Units are Terraria's: {@code useTime} is in Terraria ticks (60 per second), {@code value} is the buy
 * price in copper coins (an item sells for a fifth of it), velocities are Terraria shoot speeds.
 */
public record TerraItemStats(
    DamageClass damageClass,
    int damage,
    int useTime,
    float knockback,
    int crit,
    int mana,
    float velocity,
    int pickPower,
    int axePower,
    int hammerPower,
    int defense,
    TerraRarity rarity,
    int value
) {
    public static final TerraItemStats NONE = new TerraItemStats(DamageClass.GENERIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, TerraRarity.WHITE, 0);

    public static final Codec<TerraItemStats> CODEC = RecordCodecBuilder.create(i -> i.group(
        DamageClass.CODEC.optionalFieldOf("class", DamageClass.GENERIC).forGetter(TerraItemStats::damageClass),
        Codec.INT.optionalFieldOf("damage", 0).forGetter(TerraItemStats::damage),
        Codec.INT.optionalFieldOf("use_time", 0).forGetter(TerraItemStats::useTime),
        Codec.FLOAT.optionalFieldOf("knockback", 0.0F).forGetter(TerraItemStats::knockback),
        Codec.INT.optionalFieldOf("crit", 0).forGetter(TerraItemStats::crit),
        Codec.INT.optionalFieldOf("mana", 0).forGetter(TerraItemStats::mana),
        Codec.FLOAT.optionalFieldOf("velocity", 0.0F).forGetter(TerraItemStats::velocity),
        Codec.INT.optionalFieldOf("pickaxe_power", 0).forGetter(TerraItemStats::pickPower),
        Codec.INT.optionalFieldOf("axe_power", 0).forGetter(TerraItemStats::axePower),
        Codec.INT.optionalFieldOf("hammer_power", 0).forGetter(TerraItemStats::hammerPower),
        Codec.INT.optionalFieldOf("defense", 0).forGetter(TerraItemStats::defense),
        TerraRarity.CODEC.optionalFieldOf("rarity", TerraRarity.WHITE).forGetter(TerraItemStats::rarity),
        Codec.INT.optionalFieldOf("value", 0).forGetter(TerraItemStats::value)
    ).apply(i, TerraItemStats::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerraItemStats> STREAM_CODEC = StreamCodec.of(
        (buf, s) -> {
            buf.writeEnum(s.damageClass);
            buf.writeVarInt(s.damage);
            buf.writeVarInt(s.useTime);
            buf.writeFloat(s.knockback);
            buf.writeVarInt(s.crit);
            buf.writeVarInt(s.mana);
            buf.writeFloat(s.velocity);
            buf.writeVarInt(s.pickPower);
            buf.writeVarInt(s.axePower);
            buf.writeVarInt(s.hammerPower);
            buf.writeVarInt(s.defense);
            buf.writeVarInt(s.rarity.level() + 20);
            buf.writeVarInt(s.value);
        },
        buf -> new TerraItemStats(buf.readEnum(DamageClass.class), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
            buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), TerraRarity.byLevel(buf.readVarInt() - 20), buf.readVarInt())
    );

    /** Stats of a stack ({@link #NONE} when the stack has none). */
    public static TerraItemStats of(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.STATS, NONE);
    }

    public boolean isWeapon() {
        return damage > 0;
    }

    /** Use time converted to Minecraft ticks (min 1). */
    public int useTicks() {
        return Math.max(1, Math.round(useTime / 3.0F));
    }

    /** Attacks per second, Minecraft's ATTACK_SPEED attribute value. */
    public float attacksPerSecond() {
        return useTime <= 0 ? 4.0F : 60.0F / useTime;
    }

    public int sellValue() {
        return value / 5;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DamageClass damageClass = DamageClass.GENERIC;
        private int damage;
        private int useTime;
        private float knockback;
        private int crit;
        private int mana;
        private float velocity;
        private int pickPower;
        private int axePower;
        private int hammerPower;
        private int defense;
        private TerraRarity rarity = TerraRarity.WHITE;
        private int value;

        public Builder damage(DamageClass type, int damage) { this.damageClass = type; this.damage = damage; return this; }
        public Builder melee(int damage) { return damage(DamageClass.MELEE, damage); }
        public Builder ranged(int damage) { return damage(DamageClass.RANGED, damage); }
        public Builder magic(int damage) { return damage(DamageClass.MAGIC, damage); }
        public Builder summon(int damage) { return damage(DamageClass.SUMMON, damage); }
        public Builder useTime(int ticks) { this.useTime = ticks; return this; }
        public Builder knockback(float kb) { this.knockback = kb; return this; }
        public Builder crit(int crit) { this.crit = crit; return this; }
        public Builder mana(int mana) { this.mana = mana; return this; }
        public Builder velocity(float velocity) { this.velocity = velocity; return this; }
        public Builder pickaxe(int power) { this.pickPower = power; return this; }
        public Builder axe(int power) { this.axePower = power; return this; }
        public Builder hammer(int power) { this.hammerPower = power; return this; }
        public Builder defense(int defense) { this.defense = defense; return this; }
        public Builder rarity(TerraRarity rarity) { this.rarity = rarity; return this; }
        /** Buy price in copper coins. */
        public Builder value(int copper) { this.value = copper; return this; }
        /** Buy price given as gold/silver/copper. */
        public Builder value(int gold, int silver, int copper) { return value(gold * 10000 + silver * 100 + copper); }

        public TerraItemStats build() {
            return new TerraItemStats(damageClass, damage, useTime, knockback, crit, mana, velocity, pickPower, axePower,
                hammerPower, defense, rarity, value);
        }
    }
}
