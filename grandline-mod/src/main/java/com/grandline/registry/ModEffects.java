package com.grandline.registry;

import com.grandline.GrandLine;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, GrandLine.MODID);

    /** Seastone contact / cuffs / the sea: Devil Fruit powers disabled, body weakened. */
    public static final DeferredHolder<MobEffect, MobEffect> SEASTONE_DRAIN = EFFECTS.register("seastone_drain", () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x2A8F8F)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, GrandLine.id("seastone_slow"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_DAMAGE, GrandLine.id("seastone_weak"), -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Knocked out by Conqueror's Haki (or a heavy blow): cannot move or act. */
    public static final DeferredHolder<MobEffect, MobEffect> KNOCKED_OUT = EFFECTS.register("knocked_out", () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x1A1A1A)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, GrandLine.id("ko_slow"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.JUMP_STRENGTH, GrandLine.id("ko_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    static final class SimpleEffect extends MobEffect {
        SimpleEffect(MobEffectCategory c, int color) {
            super(c, color);
        }
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    private ModEffects() {}
}
