package com.grandline.combat.weapon;

import com.grandline.combat.Element;
import com.grandline.combat.Strike;

import java.util.List;
import java.util.Locale;

/**
 * Weapon families. Each defines a light-attack chain, a heavy strike, reach and ranged behaviour.
 * Damage values are multipliers of the wielder's attack damage attribute (which already includes the item).
 */
public enum WeaponClass {
    KATANA(false, false, Element.SLASH, chain(
            Strike.of(1.0f, 3.2, 100, 0.3f, 3, 7, "slash_right"),
            Strike.of(1.0f, 3.2, 100, 0.3f, 3, 7, "slash_left"),
            Strike.of(1.5f, 3.6, 60, 0.8f, 5, 12, "thrust").lunge(0.6)),
            Strike.of(2.4f, 3.8, 150, 1.2f, 9, 18, "heavy_overhead")),
    CUTLASS(false, false, Element.SLASH, chain(
            Strike.of(1.0f, 3.0, 110, 0.3f, 3, 6, "slash_right"),
            Strike.of(1.1f, 3.0, 110, 0.4f, 3, 7, "slash_left"),
            Strike.of(1.3f, 3.2, 140, 0.7f, 4, 10, "spin_slash")),
            Strike.of(2.2f, 3.4, 160, 1.0f, 8, 16, "heavy_cleave")),
    RAPIER(false, false, Element.PIERCE, chain(
            Strike.of(0.8f, 3.6, 30, 0.1f, 2, 4, "thrust"),
            Strike.of(0.8f, 3.6, 30, 0.1f, 2, 4, "thrust"),
            Strike.of(0.9f, 3.8, 30, 0.2f, 2, 5, "thrust"),
            Strike.of(1.4f, 4.2, 30, 0.6f, 4, 10, "thrust").lunge(0.9)),
            Strike.of(2.0f, 5.0, 25, 0.9f, 8, 14, "heavy_thrust").lunge(1.2)),
    NODACHI(true, false, Element.SLASH, chain(
            Strike.of(1.3f, 4.0, 140, 0.6f, 6, 12, "wide_slash"),
            Strike.of(1.5f, 4.0, 160, 0.8f, 7, 14, "wide_slash_back")),
            Strike.of(3.0f, 4.6, 180, 1.6f, 14, 24, "heavy_overhead")),
    DUAL_BLADES(false, false, Element.SLASH, chain(
            Strike.of(0.7f, 2.8, 100, 0.2f, 2, 4, "dual_slash_right"),
            Strike.of(0.7f, 2.8, 100, 0.2f, 2, 4, "dual_slash_left"),
            Strike.of(0.8f, 2.8, 120, 0.2f, 2, 4, "dual_cross"),
            Strike.of(1.2f, 3.0, 360, 0.6f, 4, 10, "spin_slash")),
            Strike.of(2.0f, 3.2, 120, 1.0f, 8, 14, "dual_x_cut").lunge(0.8)),
    KNIFE(false, false, Element.PIERCE, chain(
            Strike.of(0.7f, 2.4, 70, 0.1f, 2, 3, "stab"),
            Strike.of(0.7f, 2.4, 70, 0.1f, 2, 3, "slash_left"),
            Strike.of(1.1f, 2.6, 70, 0.3f, 3, 7, "stab")),
            Strike.of(1.8f, 2.8, 60, 0.4f, 6, 10, "backstab").lunge(1.0)),
    SPEAR(true, false, Element.PIERCE, chain(
            Strike.of(1.0f, 4.6, 30, 0.4f, 4, 8, "spear_thrust"),
            Strike.of(1.0f, 4.6, 30, 0.4f, 4, 8, "spear_thrust"),
            Strike.of(1.3f, 4.2, 220, 0.9f, 6, 12, "spear_sweep")),
            Strike.of(2.4f, 5.5, 30, 1.4f, 10, 18, "heavy_thrust").lunge(1.4)),
    NAGINATA(true, false, Element.SLASH, chain(
            Strike.of(1.1f, 4.4, 150, 0.5f, 4, 9, "polearm_sweep"),
            Strike.of(1.1f, 4.4, 150, 0.5f, 4, 9, "polearm_sweep_back"),
            Strike.of(1.6f, 4.6, 360, 1.0f, 7, 14, "polearm_spin")),
            Strike.of(2.6f, 5.0, 160, 1.5f, 11, 20, "heavy_overhead")),
    TRIDENT_POLE(true, false, Element.PIERCE, chain(
            Strike.of(1.1f, 4.2, 40, 0.5f, 4, 9, "spear_thrust"),
            Strike.of(1.4f, 4.2, 40, 0.8f, 5, 11, "spear_thrust")),
            Strike.of(2.4f, 4.6, 40, 1.4f, 10, 18, "heavy_thrust")),
    BO_STAFF(true, false, Element.BLUNT, chain(
            Strike.of(0.8f, 3.6, 140, 0.6f, 3, 6, "staff_swing"),
            Strike.of(0.8f, 3.6, 140, 0.6f, 3, 6, "staff_swing_back"),
            Strike.of(0.9f, 3.8, 360, 0.9f, 4, 9, "polearm_spin")),
            Strike.of(2.0f, 4.0, 60, 1.8f, 9, 16, "heavy_overhead")),
    KANABO(true, false, Element.BLUNT, chain(
            Strike.of(1.6f, 3.4, 120, 1.2f, 8, 14, "heavy_swing"),
            Strike.of(1.8f, 3.4, 120, 1.4f, 9, 16, "heavy_swing_back")),
            Strike.of(3.4f, 3.6, 90, 2.4f, 16, 26, "ground_slam")),
    HAMMER(true, false, Element.BLUNT, chain(
            Strike.of(1.5f, 3.2, 100, 1.2f, 8, 14, "heavy_swing"),
            Strike.of(1.7f, 3.2, 100, 1.4f, 8, 15, "heavy_overhead")),
            Strike.of(3.2f, 3.4, 360, 2.2f, 16, 26, "ground_slam")),
    TONFA(false, false, Element.BLUNT, chain(
            Strike.of(0.7f, 2.6, 80, 0.4f, 2, 4, "jab_right"),
            Strike.of(0.7f, 2.6, 80, 0.4f, 2, 4, "jab_left"),
            Strike.of(1.1f, 2.8, 100, 0.9f, 3, 8, "uppercut")),
            Strike.of(1.8f, 3.0, 90, 1.5f, 7, 13, "heavy_punch").lunge(0.6)),
    WHIP(false, false, Element.SLASH, chain(
            Strike.of(0.8f, 5.5, 50, 0.4f, 4, 8, "whip_crack"),
            Strike.of(0.9f, 5.5, 100, 0.4f, 4, 8, "whip_sweep")),
            Strike.of(1.6f, 6.5, 40, 0.2f, 8, 14, "whip_lash")),
    GAUNTLETS(false, false, Element.BLUNT, chain(
            Strike.of(0.8f, 2.6, 70, 0.4f, 2, 4, "jab_right"),
            Strike.of(0.8f, 2.6, 70, 0.4f, 2, 4, "jab_left"),
            Strike.of(0.9f, 2.6, 70, 0.5f, 2, 5, "hook_right"),
            Strike.of(1.3f, 2.8, 80, 1.2f, 4, 9, "uppercut")),
            Strike.of(2.2f, 3.0, 70, 2.0f, 8, 15, "heavy_punch").lunge(0.8)),
    JITTE(false, false, Element.BLUNT, chain(
            Strike.of(0.8f, 2.8, 90, 0.4f, 3, 6, "slash_right"),
            Strike.of(0.9f, 2.8, 90, 0.5f, 3, 7, "slash_left")),
            Strike.of(1.6f, 3.0, 60, 0.6f, 7, 12, "thrust")),
    ANCHOR_CHAIN(true, false, Element.BLUNT, chain(
            Strike.of(1.4f, 5.0, 120, 1.4f, 8, 14, "chain_swing"),
            Strike.of(1.5f, 5.0, 360, 1.6f, 9, 15, "chain_spin")),
            Strike.of(3.0f, 6.0, 40, 2.6f, 14, 24, "anchor_throw")),
    // ---- ranged ----
    FLINTLOCK(false, true, Element.PIERCE, chain(Strike.of(1.0f, 2.6, 80, 0.5f, 3, 6, "pistol_whip")), Strike.of(1.0f, 2.6, 80, 0.5f, 3, 6, "pistol_whip")),
    RIFLE(true, true, Element.PIERCE, chain(Strike.of(1.0f, 3.0, 60, 0.7f, 4, 8, "rifle_butt")), Strike.of(1.0f, 3.0, 60, 0.7f, 4, 8, "rifle_butt")),
    BAZOOKA(true, true, Element.IMPACT, chain(Strike.of(1.2f, 3.0, 90, 1.2f, 6, 12, "heavy_swing")), Strike.of(1.2f, 3.0, 90, 1.2f, 6, 12, "heavy_swing")),
    HAND_CANNON(true, true, Element.IMPACT, chain(Strike.of(1.2f, 3.0, 90, 1.2f, 6, 12, "heavy_swing")), Strike.of(1.2f, 3.0, 90, 1.2f, 6, 12, "heavy_swing")),
    SLINGSHOT(false, true, Element.IMPACT, chain(Strike.of(0.5f, 2.4, 60, 0.2f, 2, 5, "jab_right")), Strike.of(0.5f, 2.4, 60, 0.2f, 2, 5, "jab_right")),
    BOW(true, true, Element.PIERCE, chain(Strike.of(0.5f, 2.4, 60, 0.2f, 2, 5, "jab_right")), Strike.of(0.5f, 2.4, 60, 0.2f, 2, 5, "jab_right")),
    THROWING_KNIVES(false, true, Element.PIERCE, chain(Strike.of(0.7f, 2.4, 70, 0.1f, 2, 3, "stab")), Strike.of(0.7f, 2.4, 70, 0.1f, 2, 3, "stab")),
    WEATHER_STAFF(true, true, Element.LIGHTNING, chain(
            Strike.of(0.8f, 3.4, 140, 0.5f, 3, 6, "staff_swing"),
            Strike.of(0.8f, 3.4, 140, 0.5f, 3, 6, "staff_swing_back")),
            Strike.of(1.4f, 3.6, 60, 1.0f, 7, 12, "staff_thrust")),
    DIAL(false, true, Element.IMPACT, chain(Strike.of(0.6f, 2.4, 60, 0.3f, 2, 4, "jab_right")), Strike.of(0.6f, 2.4, 60, 0.3f, 2, 4, "jab_right"));

    public final boolean twoHanded;
    public final boolean ranged;
    public final Element element;
    public final List<Strike> light;
    public final Strike heavy;

    WeaponClass(boolean twoHanded, boolean ranged, Element element, List<Strike> light, Strike heavy) {
        this.twoHanded = twoHanded;
        this.ranged = ranged;
        this.element = element;
        this.light = light;
        this.heavy = heavy;
    }

    private static List<Strike> chain(Strike... s) {
        return List.of(s);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isSword() {
        return this == KATANA || this == CUTLASS || this == RAPIER || this == NODACHI || this == DUAL_BLADES;
    }
}
