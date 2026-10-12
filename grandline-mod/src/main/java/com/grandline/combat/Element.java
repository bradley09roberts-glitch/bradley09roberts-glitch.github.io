package com.grandline.combat;

/** Damage element. Logia bodies are intangible to physical elements unless Haki or seastone is involved. */
public enum Element {
    PHYSICAL, SLASH, PIERCE, BLUNT, FIRE, ICE, LIGHTNING, LIGHT, MAGMA, SAND, SMOKE, DARKNESS, POISON, WATER, SOUND, IMPACT, WIND;

    public boolean physical() {
        return this == PHYSICAL || this == SLASH || this == PIERCE || this == BLUNT || this == IMPACT;
    }
}
