package com.terracraft.combat;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Terraria's damage classes. Equipment determines a player's playstyle; there are no permanent classes.
 * GENERIC damage benefits only from "all damage" bonuses.
 */
public enum DamageClass implements StringRepresentable {
    GENERIC(ChatFormatting.GRAY),
    MELEE(ChatFormatting.RED),
    RANGED(ChatFormatting.GREEN),
    MAGIC(ChatFormatting.LIGHT_PURPLE),
    SUMMON(ChatFormatting.AQUA);

    public static final Codec<DamageClass> CODEC = StringRepresentable.fromEnum(DamageClass::values);
    private final ChatFormatting color;

    DamageClass(ChatFormatting color) {
        this.color = color;
    }

    public ChatFormatting color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("damage_class.terracraft." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
