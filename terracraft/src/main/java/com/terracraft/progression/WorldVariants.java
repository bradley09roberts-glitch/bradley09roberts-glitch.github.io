package com.terracraft.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-world random choices that Terraria makes at world creation: which world evil generates and which
 * ore of every ore pair the world uses. Chosen deterministically from the world seed so world generation,
 * which may run before the saved data exists, can always derive the same answer.
 */
public record WorldVariants(WorldEvil evil, Map<OrePair, Boolean> secondaryOre) {

    public enum WorldEvil implements StringRepresentable {
        CORRUPTION, CRIMSON;

        public static final Codec<WorldEvil> CODEC = StringRepresentable.fromEnum(WorldEvil::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Terraria's alternative ore pairs. The first ore is the "primary" variant. */
    public enum OrePair implements StringRepresentable {
        COPPER_TIN("copper", "tin"),
        IRON_LEAD("iron", "lead"),
        SILVER_TUNGSTEN("silver", "tungsten"),
        GOLD_PLATINUM("gold", "platinum"),
        COBALT_PALLADIUM("cobalt", "palladium"),
        MYTHRIL_ORICHALCUM("mythril", "orichalcum"),
        ADAMANTITE_TITANIUM("adamantite", "titanium");

        public static final Codec<OrePair> CODEC = StringRepresentable.fromEnum(OrePair::values);

        private final String primary;
        private final String secondary;

        OrePair(String primary, String secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        public String primary() {
            return primary;
        }

        public String secondary() {
            return secondary;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<WorldVariants> CODEC = RecordCodecBuilder.create(i -> i.group(
        WorldEvil.CODEC.fieldOf("evil").forGetter(WorldVariants::evil),
        Codec.unboundedMap(OrePair.CODEC, Codec.BOOL).fieldOf("secondary_ore").forGetter(WorldVariants::secondaryOre)
    ).apply(i, WorldVariants::new));

    public static final StreamCodec<FriendlyByteBuf, WorldVariants> STREAM_CODEC = StreamCodec.of(
        (buf, v) -> {
            buf.writeEnum(v.evil);
            for (OrePair pair : OrePair.values()) {
                buf.writeBoolean(v.usesSecondary(pair));
            }
        },
        buf -> {
            WorldEvil evil = buf.readEnum(WorldEvil.class);
            Map<OrePair, Boolean> map = new EnumMap<>(OrePair.class);
            for (OrePair pair : OrePair.values()) {
                map.put(pair, buf.readBoolean());
            }
            return new WorldVariants(evil, map);
        }
    );

    /** Default used before a world has been initialised (client before sync, unit tests...). */
    public static final WorldVariants DEFAULT = fromSeed(0L);

    public static WorldVariants fromSeed(long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x7E44A1A5_1DEAL);
        WorldEvil evil = random.nextBoolean() ? WorldEvil.CORRUPTION : WorldEvil.CRIMSON;
        Map<OrePair, Boolean> map = new EnumMap<>(OrePair.class);
        for (OrePair pair : OrePair.values()) {
            map.put(pair, random.nextBoolean());
        }
        return new WorldVariants(evil, map);
    }

    public boolean usesSecondary(OrePair pair) {
        return secondaryOre.getOrDefault(pair, false);
    }

    /** Name of the ore this world primarily generates for the given pair, e.g. "tin". */
    public String chosenOre(OrePair pair) {
        return usesSecondary(pair) ? pair.secondary() : pair.primary();
    }

    public WorldVariants withEvil(WorldEvil newEvil) {
        return new WorldVariants(newEvil, secondaryOre);
    }
}
