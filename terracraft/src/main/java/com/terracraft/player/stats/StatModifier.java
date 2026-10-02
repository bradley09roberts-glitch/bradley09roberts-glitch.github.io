package com.terracraft.player.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** One additive change to a {@link Stat}. */
public record StatModifier(Stat stat, float amount) {
    public static final Codec<StatModifier> CODEC = RecordCodecBuilder.create(i -> i.group(
        Stat.CODEC.fieldOf("stat").forGetter(StatModifier::stat),
        Codec.FLOAT.fieldOf("amount").forGetter(StatModifier::amount)
    ).apply(i, StatModifier::new));

    /** Terraria-style tooltip line, e.g. "+8% movement speed" or "+2 defense". */
    public Component tooltip() {
        String sign = amount >= 0 ? "+" : "";
        String value = switch (stat.format()) {
            case PERCENT -> sign + format(amount * 100.0F) + "%";
            case FLAT_PERCENT -> sign + format(amount) + "%";
            case REGEN -> sign + format(amount / 2.0F) + " HP/s";
            case FLAT -> sign + format(amount);
        };
        MutableComponent line = Component.literal(value + " ").append(stat.displayName());
        return line.withStyle(amount >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED);
    }

    private static String format(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
