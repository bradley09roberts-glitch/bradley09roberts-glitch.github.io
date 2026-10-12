package com.grandline.combat.style;

import com.grandline.combat.Ability;
import com.grandline.combat.Strike;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.player.Race;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A martial art: its own basic combo plus techniques unlocked by style mastery. */
public final class FightingStyle {
    public final String id;
    public final boolean canon;
    /** Weapons this style works with; empty = unarmed (or any hands-free use). */
    public final Set<WeaponClass> weapons;
    @Nullable public final Race race;
    public final List<Strike> light;
    public final Strike heavy;
    public final List<Ability> abilities = new ArrayList<>();

    FightingStyle(String id, boolean canon, Set<WeaponClass> weapons, @Nullable Race race, List<Strike> light, Strike heavy) {
        this.id = id;
        this.canon = canon;
        this.weapons = weapons;
        this.race = race;
        this.light = light;
        this.heavy = heavy;
    }

    public boolean unarmed() {
        return weapons.isEmpty();
    }

    public boolean worksWith(@Nullable WeaponClass wc) {
        if (weapons.isEmpty()) return wc == null || wc == WeaponClass.GAUNTLETS;
        return wc != null && weapons.contains(wc);
    }

    public String translationKey() {
        return "style.grandline." + id;
    }

    Ability.Builder ability(String name) {
        return Ability.builder("style/" + id + "/" + name, Ability.Source.STYLE, id);
    }

    static Set<WeaponClass> swords() {
        return EnumSet.of(WeaponClass.KATANA, WeaponClass.CUTLASS, WeaponClass.RAPIER, WeaponClass.NODACHI, WeaponClass.DUAL_BLADES);
    }
}
