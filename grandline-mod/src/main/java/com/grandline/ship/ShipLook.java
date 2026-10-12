package com.grandline.ship;

import java.util.List;

/**
 * A ship's personal look beyond hull/sail paint: figurehead carving, the emblem on its flag and main sail, and the
 * flag's colours. Encoded as one short string (synced to clients, saved on the entity and in the registry).
 */
public record ShipLook(String figurehead, String emblem, int emblemColor, int flagColor) {
    /** Figurehead carvings rendered at the bow (see client ShipDecor). */
    public static final List<String> FIGUREHEADS = List.of("classic", "lion", "ram", "swan", "dragon", "skull", "serpent");
    /** Emblems players may fly. "marine" is the Marine gull, reserved for Marines (and Marine NPC ships). */
    public static final List<String> EMBLEMS = List.of("skull", "swords", "hat", "flame", "anchor", "star", "crescent", "marine");
    public static final int[] SAILS = {0xF4EEDC, 0xF4F4F4, 0x2A2A2A, 0xC9483B, 0x3A7BD5, 0xE8B04A, 0x2E9E6A, 0x8A5AC9};
    public static final int[] HULLS = {0x8A5A32, 0x5A3A22, 0xE8E8E8, 0x2A2A2A, 0x9E2A2A, 0x2A4E8A, 0x2E6E46, 0xC9A24A};
    public static final int[] FLAGS = {0x1A1A1A, 0xF4F4F4, 0x9E2222, 0x23417E, 0x2E6E46, 0x5A2A7A, 0xD89A2A, 0x5A3A22};
    public static final int[] EMBLEM_COLORS = {0xF0F0F0, 0x1A1A1A, 0xE8C040, 0xD03030, 0x60B0F0, 0x70D070, 0xF090C0, 0xFF8A20};

    public static final ShipLook DEFAULT = new ShipLook("classic", "skull", 0xF0F0F0, 0x1A1A1A);
    public static final ShipLook MARINE = new ShipLook("classic", "marine", 0x2A5ABE, 0xF4F4F4);

    public String encode() {
        return figurehead + "|" + emblem + "|" + Integer.toHexString(emblemColor & 0xFFFFFF) + "|" + Integer.toHexString(flagColor & 0xFFFFFF);
    }

    private static final java.util.Map<String, ShipLook> DECODED = new java.util.concurrent.ConcurrentHashMap<>();

    public static ShipLook decode(String s) {
        if (s == null || s.isEmpty()) return DEFAULT;
        ShipLook cached = DECODED.get(s); // renderers ask every frame
        if (cached != null) return cached;
        String[] p = s.split("\\|");
        ShipLook out;
        try {
            String fig = p.length > 0 && FIGUREHEADS.contains(p[0]) ? p[0] : DEFAULT.figurehead;
            String emb = p.length > 1 && EMBLEMS.contains(p[1]) ? p[1] : DEFAULT.emblem;
            int ec = p.length > 2 ? Integer.parseInt(p[2], 16) & 0xFFFFFF : DEFAULT.emblemColor;
            int fc = p.length > 3 ? Integer.parseInt(p[3], 16) & 0xFFFFFF : DEFAULT.flagColor;
            out = new ShipLook(fig, emb, ec, fc);
        } catch (NumberFormatException e) {
            out = DEFAULT;
        }
        if (DECODED.size() < 4096) DECODED.put(s, out);
        return out;
    }

    /** Sanitised copy (unknown ids fall back to defaults, colours to 24 bits). */
    public ShipLook valid() {
        return decode(encode());
    }

    public ShipLook withFigurehead(String f) {
        return new ShipLook(f, emblem, emblemColor, flagColor);
    }

    /** A random pirate look for NPC raiders (no Marine gull). */
    public static ShipLook randomPirate(net.minecraft.util.RandomSource r) {
        String emb = EMBLEMS.get(r.nextInt(EMBLEMS.size() - 1));
        String fig = FIGUREHEADS.get(r.nextInt(FIGUREHEADS.size()));
        int ec = r.nextInt(3) == 0 ? EMBLEM_COLORS[r.nextInt(EMBLEM_COLORS.length)] : 0xF0F0F0;
        return new ShipLook(fig, emb, ec, r.nextInt(4) == 0 ? 0x9E2222 : 0x1A1A1A);
    }
}
