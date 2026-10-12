package com.grandline.fruit;

import com.grandline.combat.Ability;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of one Devil Fruit. Abilities are registered by the fruit's implementation class; passive hooks
 * (tick, incoming damage, intangibility) are overridden where a fruit needs them.
 */
public class DevilFruit {
    public final String id;
    public final FruitType type;
    public final int tier;          // 1 (common) .. 10 (legendary); drives where it can appear
    public final boolean canon;
    public final int color;         // primary item colour
    public final int color2;        // swirl colour
    public final Element element;   // Logia element (PHYSICAL for non-elemental)
    final List<Ability> abilities = new ArrayList<>();
    /** Zoan model id for transformations (GeckoLib), empty if none. */
    public String zoanModel = "";

    public DevilFruit(String id, FruitType type, int tier, boolean canon, int color, int color2, Element element) {
        this.id = id;
        this.type = type;
        this.tier = tier;
        this.canon = canon;
        this.color = color;
        this.color2 = color2;
        this.element = element;
    }

    public String translationKey() {
        return "fruit.grandline." + id;
    }

    public List<Ability> abilities() {
        return abilities;
    }

    public Ability.Builder ability(String name) {
        return Ability.builder("fruit/" + id + "/" + name, Ability.Source.FRUIT, id);
    }

    public void add(Ability a) {
        abilities.add(a);
    }

    // ---- passive hooks ----

    @FunctionalInterface
    public interface TickHook { void tick(LivingEntity user); }

    @FunctionalInterface
    public interface IncomingHook { float modify(LivingEntity user, LivingEntity attacker, float amount, Hits.Info info); }

    public TickHook tickHook;
    public IncomingHook incomingHook;

    /** Called every server tick for the user while not weakened. */
    public void tick(LivingEntity user) {
        if (tickHook != null) tickHook.tick(user);
    }

    /** Logia: physical damage passes through unless Haki/seastone/water. */
    public boolean intangible(LivingEntity user, LivingEntity attacker, Hits.Info info) {
        return type == FruitType.LOGIA && info.element() != Element.WATER;
    }

    /** Modify damage that reaches the user (resistances like rubber vs blunt). */
    public float modifyIncoming(LivingEntity user, LivingEntity attacker, float amount, Hits.Info info) {
        float a = amount;
        if (info.element() == element && element != Element.PHYSICAL && element != Element.SLASH && element != Element.BLUNT && element != Element.PIERCE) a = 0; // immune to own element
        return incomingHook != null ? incomingHook.modify(user, attacker, a, info) : a;
    }
}
