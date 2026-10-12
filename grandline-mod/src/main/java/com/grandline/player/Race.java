package com.grandline.player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every race in the game. Canon races preserve the source's biology; original races are new to this mod.
 * Size values are multipliers on the vanilla {@code minecraft:generic.scale} attribute, which scales hitbox,
 * eye/camera height and step height together.
 */
public enum Race {
    //            playable canon  base   min    max    hp   speed  reach  step  jump  armor water  o2  dmg   kbRes parts
    HUMAN(          true,  true,  1.00f, 0.90f, 1.15f,  0,  0.00f, 0.0f, 0.0f, 0.00f, 0, 0.00f, 0, 1.00f, 0.0f, 0),
    FISHMAN(        true,  true,  1.15f, 1.05f, 1.35f,  4,  0.00f, 0.0f, 0.0f, 0.00f, 1, 1.00f, 9, 1.10f, 0.1f, Parts.FINS),
    MERFOLK(        true,  true,  1.00f, 0.90f, 1.15f,  0, -0.20f, 0.0f, 0.0f,-0.20f, 0, 1.00f, 9, 0.95f, 0.0f, Parts.TAIL_FIN | Parts.FINS),
    MINK(           true,  true,  1.00f, 0.85f, 1.25f,  2,  0.15f, 0.0f, 0.0f, 0.10f, 0, 0.00f, 0, 1.00f, 0.0f, Parts.EARS | Parts.TAIL | Parts.FUR),
    SKY_PEOPLE(     true,  true,  0.95f, 0.85f, 1.05f,  0,  0.05f, 0.0f, 0.0f, 0.05f, 0, 0.00f, 0, 0.95f, 0.0f, Parts.SMALL_WINGS),
    LONG_ARM(       true,  true,  1.05f, 0.95f, 1.15f,  0,  0.00f, 1.5f, 0.0f, 0.00f, 0, 0.00f, 0, 1.00f, 0.0f, Parts.LONG_ARMS),
    LONG_LEG(       true,  true,  1.25f, 1.15f, 1.40f,  0,  0.15f, 0.5f, 0.5f, 0.10f, 0, 0.00f, 0, 1.00f, 0.0f, Parts.LONG_LEGS),
    THREE_EYE(      true,  true,  1.00f, 0.90f, 1.10f,  0,  0.00f, 0.0f, 0.0f, 0.00f, 0, 0.00f, 0, 1.00f, 0.0f, Parts.THIRD_EYE),
    TONTATTA(       true,  true,  0.40f, 0.35f, 0.45f, -6,  0.35f,-0.5f, 0.0f, 0.15f, 0, 0.00f, 0, 1.30f, 0.0f, Parts.TAIL),
    GIANT(          true,  true,  1.95f, 1.80f, 2.00f, 20, -0.12f, 2.0f, 0.5f, 0.10f, 2, 0.00f, 0, 1.45f, 0.6f, 0),
    LUNARIAN(       false, true,  1.10f, 1.05f, 1.20f,  6,  0.00f, 0.0f, 0.0f, 0.05f, 6, 0.00f, 0, 1.10f, 0.2f, Parts.BLACK_WINGS),
    BUCCANEER(      false, true,  1.60f, 1.50f, 1.75f, 12, -0.05f, 1.0f, 0.5f, 0.05f, 2, 0.00f, 0, 1.30f, 0.4f, 0),
    SNAKENECK(      false, true,  1.10f, 1.00f, 1.20f,  0,  0.00f, 0.5f, 0.0f, 0.00f, 0, 0.00f, 0, 1.00f, 0.0f, Parts.LONG_NECK),
    // ---- Original races ----
    REEFBORN(       true,  false, 1.05f, 0.95f, 1.20f,  2,  0.00f, 0.0f, 0.0f, 0.00f, 2, 0.60f, 6, 1.00f, 0.1f, Parts.CORAL),
    STORMWING(      true,  false, 0.90f, 0.80f, 1.00f, -2,  0.10f, 0.0f, 0.0f, 0.15f,-1, 0.00f, 0, 0.95f, 0.0f, Parts.FEATHER_WINGS),
    STONEHIDE(      true,  false, 1.30f, 1.20f, 1.45f,  8, -0.10f, 0.5f, 0.0f,-0.10f, 6, 0.00f, 0, 1.15f, 0.5f, Parts.STONE_SKIN),
    ABYSSAL(        true,  false, 1.00f, 0.90f, 1.15f,  0,  0.05f, 0.0f, 0.0f, 0.00f, 0, 0.50f, 6, 1.05f, 0.0f, Parts.GLOW_EYES | Parts.FINS),
    EMBERKIN(       true,  false, 1.00f, 0.90f, 1.15f,  2,  0.05f, 0.0f, 0.0f, 0.00f, 1, 0.00f, 0, 1.05f, 0.0f, Parts.EMBER_VEINS | Parts.HORNS),
    VINEFOLK(       true,  false, 1.00f, 0.90f, 1.20f,  4,  0.00f, 0.0f, 0.0f, 0.00f, 0, 0.00f, 0, 0.95f, 0.0f, Parts.LEAF_HAIR),
    WHALEFOLK(      true,  false, 1.60f, 1.45f, 1.75f, 14, -0.08f, 1.0f, 0.5f, 0.00f, 2, 0.40f,18, 1.20f, 0.5f, Parts.FINS | Parts.BLOWHOLE),
    STARBORN(       true,  false, 0.95f, 0.85f, 1.05f,  0,  0.05f, 0.0f, 0.0f, 0.10f, 0, 0.00f, 0, 1.00f, 0.0f, Parts.STAR_FRECKLES);

    public final boolean playable;
    public final boolean canon;
    public final float baseScale, minScale, maxScale;
    public final int healthBonus;
    public final float speedBonus, reachBonus, stepBonus, jumpBonus;
    public final int armorBonus;
    public final float waterMovement;
    public final int oxygenBonus;
    public final float damageMultiplier;
    public final float knockbackResistance;
    public final int parts;

    Race(boolean playable, boolean canon, float baseScale, float minScale, float maxScale, int healthBonus, float speedBonus,
         float reachBonus, float stepBonus, float jumpBonus, int armorBonus, float waterMovement, int oxygenBonus,
         float damageMultiplier, float knockbackResistance, int parts) {
        this.playable = playable;
        this.canon = canon;
        this.baseScale = baseScale;
        this.minScale = minScale;
        this.maxScale = maxScale;
        this.healthBonus = healthBonus;
        this.speedBonus = speedBonus;
        this.reachBonus = reachBonus;
        this.stepBonus = stepBonus;
        this.jumpBonus = jumpBonus;
        this.armorBonus = armorBonus;
        this.waterMovement = waterMovement;
        this.oxygenBonus = oxygenBonus;
        this.damageMultiplier = damageMultiplier;
        this.knockbackResistance = knockbackResistance;
        this.parts = parts;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "race.grandline." + id();
    }

    public boolean has(int part) {
        return (parts & part) != 0;
    }

    public boolean breathesWater() {
        return this == FISHMAN || this == MERFOLK || this == REEFBORN || this == ABYSSAL;
    }

    /** Races too large for rowboats and dinghies. */
    public boolean isLarge() {
        return baseScale >= 1.5f;
    }

    public static Race byId(String id) {
        for (Race r : values()) {
            if (r.id().equals(id)) return r;
        }
        return HUMAN;
    }

    public static List<Race> playableRaces() {
        return Arrays.stream(values()).filter(r -> r.playable).toList();
    }

    /** Bit flags for race body parts rendered by the race layer. */
    public static final class Parts {
        public static final int FINS = 1;
        public static final int TAIL_FIN = 1 << 1;
        public static final int EARS = 1 << 2;
        public static final int TAIL = 1 << 3;
        public static final int FUR = 1 << 4;
        public static final int SMALL_WINGS = 1 << 5;
        public static final int LONG_ARMS = 1 << 6;
        public static final int LONG_LEGS = 1 << 7;
        public static final int THIRD_EYE = 1 << 8;
        public static final int BLACK_WINGS = 1 << 9;
        public static final int LONG_NECK = 1 << 10;
        public static final int CORAL = 1 << 11;
        public static final int FEATHER_WINGS = 1 << 12;
        public static final int STONE_SKIN = 1 << 13;
        public static final int GLOW_EYES = 1 << 14;
        public static final int EMBER_VEINS = 1 << 15;
        public static final int HORNS = 1 << 16;
        public static final int LEAF_HAIR = 1 << 17;
        public static final int BLOWHOLE = 1 << 18;
        public static final int STAR_FRECKLES = 1 << 19;

        private Parts() {}
    }
}
