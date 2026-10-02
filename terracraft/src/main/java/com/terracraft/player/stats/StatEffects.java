package com.terracraft.player.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A bundle of stat modifiers and abilities. Used by accessories, armor pieces, armor set bonuses and buffs,
 * so all four sources share one implementation of "what does this give the player".
 */
public record StatEffects(List<StatModifier> modifiers, Set<Ability> abilities) {
    public static final StatEffects NONE = new StatEffects(List.of(), Set.of());

    public static final Codec<StatEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
        StatModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(StatEffects::modifiers),
        Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(e -> List.copyOf(e.abilities))
    ).apply(i, (mods, abilities) -> new StatEffects(mods, abilities.isEmpty() ? Set.of() : EnumSet.copyOf(abilities))));

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return modifiers.isEmpty() && abilities.isEmpty();
    }

    /** Applies everything to a stats accumulator. */
    public void applyTo(PlayerStats stats) {
        for (StatModifier modifier : modifiers) {
            stats.add(modifier.stat(), modifier.amount());
        }
        stats.abilities.addAll(abilities);
    }

    public void appendTooltip(Consumer<Component> tooltip) {
        for (StatModifier modifier : modifiers) {
            tooltip.accept(modifier.tooltip());
        }
        for (Ability ability : abilities) {
            tooltip.accept(ability.description().copy().withStyle(ChatFormatting.BLUE));
        }
    }

    public static final class Builder {
        private final List<StatModifier> modifiers = new ArrayList<>();
        private final EnumSet<Ability> abilities = EnumSet.noneOf(Ability.class);

        public Builder add(Stat stat, float amount) {
            modifiers.add(new StatModifier(stat, amount));
            return this;
        }

        public Builder ability(Ability ability) {
            abilities.add(ability);
            return this;
        }

        public StatEffects build() {
            return new StatEffects(List.copyOf(modifiers), abilities.isEmpty() ? Set.of() : EnumSet.copyOf(abilities));
        }
    }
}
