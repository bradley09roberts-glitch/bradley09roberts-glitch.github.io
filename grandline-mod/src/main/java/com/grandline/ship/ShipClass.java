package com.grandline.ship;

import java.util.Locale;

/**
 * Ready-made ship classes (agreed: rowboat → flagship, built at shipwrights, upgradeable).
 * Dimensions in blocks; speed in blocks/tick at full sail with a fair wind.
 */
public enum ShipClass {
    //          len  beam freeboard draft  hull  speed  turn  cannons masts seats price    interior
    ROWBOAT(     5,   2,  0.6f,    0.4f,   40, 0.22f, 4.0f,   0,      0,    3,    800,     false),
    SLOOP(      11,   4,  1.2f,    1.0f,  140, 0.42f, 2.6f,   2,      1,    4,   12000,    true),
    CARAVEL(    17,   6,  1.8f,    1.4f,  280, 0.52f, 2.0f,   4,      2,    6,   45000,    true),
    BRIGANTINE( 23,   7,  2.2f,    1.6f,  460, 0.58f, 1.6f,   8,      2,    8,  120000,    true),
    GALLEON(    31,   9,  2.8f,    2.0f,  760, 0.56f, 1.2f,  14,      3,   10,  350000,    true),
    FLAGSHIP(   37,  11,  3.2f,    2.4f, 1100, 0.62f, 1.0f,  20,      3,   12, 1000000,    true);

    public final int length, beam;
    public final float freeboard, draft;
    public final int hull;
    public final float speed, turnRate;
    public final int cannons, masts, seats;
    public final long price;
    public final boolean interior;

    ShipClass(int length, int beam, float freeboard, float draft, int hull, float speed, float turnRate, int cannons, int masts, int seats, long price, boolean interior) {
        this.length = length;
        this.beam = beam;
        this.freeboard = freeboard;
        this.draft = draft;
        this.hull = hull;
        this.speed = speed;
        this.turnRate = turnRate;
        this.cannons = cannons;
        this.masts = masts;
        this.seats = seats;
        this.price = price;
        this.interior = interior;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Half-width of the hull at a given fraction along its length (0 = stern, 1 = bow). */
    public float halfBeamAt(float t) {
        float h = beam / 2f;
        if (t > 0.7f) return h * Math.max(0.15f, 1f - (t - 0.7f) / 0.3f * 0.85f); // bow taper
        if (t < 0.12f) return h * (0.75f + t / 0.12f * 0.25f);                  // stern
        return h;
    }

    public static ShipClass byId(String id) {
        for (ShipClass c : values()) if (c.id().equals(id)) return c;
        return SLOOP;
    }
}
