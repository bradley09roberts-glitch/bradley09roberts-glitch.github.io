package com.grandline.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Character appearance layered over the player's own skin. */
public final class Appearance {
    public float scale = 1.0f;
    public float build = 0.5f;
    public int featureColor = 0x3A7BD5;
    public int secondaryColor = 0xE8E8E8;
    public int hairColor = 0x2B1B10;
    public int eyeColor = 0x3B2A1A;
    public int scar = 0;
    public int tattoo = 0;
    public int outfit = 0;

    public static final int SCARS = 6;
    public static final int TATTOOS = 6;
    public static final int OUTFITS = 8;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("scale", scale);
        t.putFloat("build", build);
        t.putInt("feature", featureColor);
        t.putInt("secondary", secondaryColor);
        t.putInt("hair", hairColor);
        t.putInt("eye", eyeColor);
        t.putInt("scar", scar);
        t.putInt("tattoo", tattoo);
        t.putInt("outfit", outfit);
        return t;
    }

    public static Appearance load(CompoundTag t) {
        Appearance a = new Appearance();
        if (t.contains("scale")) a.scale = t.getFloat("scale");
        if (t.contains("build")) a.build = t.getFloat("build");
        if (t.contains("feature")) a.featureColor = t.getInt("feature");
        if (t.contains("secondary")) a.secondaryColor = t.getInt("secondary");
        if (t.contains("hair")) a.hairColor = t.getInt("hair");
        if (t.contains("eye")) a.eyeColor = t.getInt("eye");
        a.scar = t.getInt("scar");
        a.tattoo = t.getInt("tattoo");
        a.outfit = t.getInt("outfit");
        return a;
    }

    /** Clamp to legal values for a race; the server always calls this on player-submitted data. */
    public void sanitize(Race race) {
        scale = Mth.clamp(Float.isFinite(scale) ? scale : race.baseScale, race.minScale, race.maxScale);
        build = Mth.clamp(Float.isFinite(build) ? build : 0.5f, 0f, 1f);
        featureColor &= 0xFFFFFF;
        secondaryColor &= 0xFFFFFF;
        hairColor &= 0xFFFFFF;
        eyeColor &= 0xFFFFFF;
        scar = Mth.clamp(scar, 0, SCARS);
        tattoo = Mth.clamp(tattoo, 0, TATTOOS);
        outfit = Mth.clamp(outfit, 0, OUTFITS);
    }

    public Appearance copy() {
        return load(save());
    }
}
