package com.grandline.fruit;

import com.grandline.combat.Element;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The agreed fruit roster (canon + original). Only fruits with implemented abilities ({@link #implemented})
 * can appear in the world; the rest are planned entries tracked in docs/INVENTORY.md.
 */
public final class DevilFruits {
    private static final Map<String, DevilFruit> ALL = new LinkedHashMap<>();
    private static boolean booted;

    private static DevilFruit def(String id, FruitType type, int tier, boolean canon, int color, int color2, Element e) {
        DevilFruit f = new DevilFruit(id, type, tier, canon, color, color2, e);
        ALL.put(id, f);
        return f;
    }

    static {
        // ---------------- Logia ----------------
        def("mera", FruitType.LOGIA, 6, true, 0xE8501E, 0xFFC23A, Element.FIRE);
        def("hie", FruitType.LOGIA, 6, true, 0x8FD8FF, 0xE8FBFF, Element.ICE);
        def("goro", FruitType.LOGIA, 8, true, 0xF2E14A, 0x6A4BD8, Element.LIGHTNING);
        def("pika", FruitType.LOGIA, 9, true, 0xFFF59A, 0xFFFFFF, Element.LIGHT);
        def("magu", FruitType.LOGIA, 9, true, 0x9A1E0A, 0xFF6A00, Element.MAGMA);
        def("suna", FruitType.LOGIA, 6, true, 0xD9B56A, 0x8C6A2E, Element.SAND);
        def("moku", FruitType.LOGIA, 5, true, 0xBFBFBF, 0x8A8A8A, Element.SMOKE);
        def("yuki", FruitType.LOGIA, 5, true, 0xF4FAFF, 0xB6D8F0, Element.ICE);
        def("gasu", FruitType.LOGIA, 6, true, 0x7E3FA8, 0xB98AD6, Element.POISON);
        def("yami", FruitType.LOGIA, 10, true, 0x1A0F24, 0x5A2A7A, Element.DARKNESS);
        def("numa", FruitType.LOGIA, 6, true, 0x4A3A22, 0x7A6A3A, Element.PHYSICAL);
        def("mori", FruitType.LOGIA, 7, true, 0x2E7A2E, 0x8AC94A, Element.PHYSICAL);
        // ---------------- Paramecia ----------------
        def("gomu", FruitType.PARAMECIA, 7, true, 0xB04ACF, 0x6A2A88, Element.BLUNT);
        def("bara", FruitType.PARAMECIA, 3, true, 0xC9483B, 0xF0C060, Element.PHYSICAL);
        def("ope", FruitType.PARAMECIA, 8, true, 0xE94B6B, 0x6ACBE8, Element.PHYSICAL);
        def("hana", FruitType.PARAMECIA, 5, true, 0xF29AC9, 0x7AC96A, Element.PHYSICAL);
        def("ito", FruitType.PARAMECIA, 8, true, 0xF4F4F4, 0xE07AE0, Element.SLASH);
        def("gura", FruitType.PARAMECIA, 10, true, 0xFAFAF0, 0x8ABAE0, Element.IMPACT);
        def("bari", FruitType.PARAMECIA, 5, true, 0x9AE0FF, 0xFFFFFF, Element.PHYSICAL);
        def("bomu", FruitType.PARAMECIA, 4, true, 0x5A5A5A, 0xFF7A2A, Element.IMPACT);
        def("supa", FruitType.PARAMECIA, 4, true, 0xC0C0C8, 0x7A7A88, Element.SLASH);
        def("nikyu", FruitType.PARAMECIA, 8, true, 0xFFD0E0, 0xAA6A80, Element.IMPACT);
        def("mochi", FruitType.SPECIAL_PARAMECIA, 8, true, 0xF5EEDC, 0xD8C8A8, Element.BLUNT);
        def("horo", FruitType.PARAMECIA, 5, true, 0xF0D0F0, 0xC08AC0, Element.PHYSICAL);
        def("doru", FruitType.PARAMECIA, 4, true, 0xF0F0E0, 0xC0C0A0, Element.PHYSICAL);
        def("soru", FruitType.PARAMECIA, 9, true, 0xF5C04A, 0xFF6A6A, Element.PHYSICAL);
        def("hobi", FruitType.PARAMECIA, 7, true, 0xF0E0A0, 0xC090D0, Element.PHYSICAL);
        def("toki", FruitType.PARAMECIA, 6, true, 0xD0D0FF, 0x8A8AE0, Element.PHYSICAL);
        // ---------------- Zoan ----------------
        def("ushi_bison", FruitType.ZOAN, 3, true, 0x7A4A2A, 0xC09A6A, Element.BLUNT);
        def("inu_wolf", FruitType.ZOAN, 4, true, 0x8A8A9A, 0xD0D0E0, Element.SLASH).zoanModel = "wolf";
        def("neko_leopard", FruitType.ZOAN, 5, true, 0xE0B04A, 0x3A2A1A, Element.SLASH).zoanModel = "leopard";
        def("zou_elephant", FruitType.ZOAN, 4, true, 0x9A9AA8, 0x6A6A7A, Element.BLUNT).zoanModel = "elephant";
        def("ryu_allosaurus", FruitType.ANCIENT_ZOAN, 6, true, 0x6A8A4A, 0x3A5A2A, Element.SLASH).zoanModel = "allosaurus";
        def("ryu_pteranodon", FruitType.ANCIENT_ZOAN, 6, true, 0x8A6A4A, 0xC09A6A, Element.SLASH).zoanModel = "pteranodon";
        def("ryu_spinosaurus", FruitType.ANCIENT_ZOAN, 6, true, 0x4A6A6A, 0xC04A3A, Element.SLASH).zoanModel = "spinosaurus";
        def("tori_phoenix", FruitType.MYTHICAL_ZOAN, 9, true, 0x3AB0FF, 0xFFE04A, Element.FIRE);
        def("uo_seiryu", FruitType.MYTHICAL_ZOAN, 10, true, 0x2A5AD0, 0xC0E0FF, Element.FIRE).zoanModel = "seiryu";
        def("hebi_orochi", FruitType.MYTHICAL_ZOAN, 7, true, 0x5A2A6A, 0xA04AC0, Element.PIERCE).zoanModel = "orochi";
        // ---------------- Original fruits (this mod's new powers) ----------------
        def("kaze", FruitType.LOGIA, 7, false, 0x9AE8C8, 0xF0FFF8, Element.WIND);
        def("shio", FruitType.PARAMECIA, 5, false, 0xF4F4F4, 0xBFD8E0, Element.PHYSICAL);
        def("kagami", FruitType.PARAMECIA, 7, false, 0xC0E8FF, 0xFFFFFF, Element.LIGHT);
        def("tako_kraken", FruitType.MYTHICAL_ZOAN, 8, false, 0x8A2A4A, 0xE07A9A, Element.BLUNT);
        def("rasen", FruitType.PARAMECIA, 6, false, 0xE0A04A, 0x8A4A2A, Element.IMPACT);
        def("kane", FruitType.PARAMECIA, 5, false, 0xE0C04A, 0x8A7A2A, Element.SOUND);
        def("tsuchi", FruitType.LOGIA, 6, false, 0x7A5A3A, 0x4A3A2A, Element.PHYSICAL);
        def("kiri", FruitType.LOGIA, 5, false, 0xD8E0E8, 0xA0A8B0, Element.WATER);
        def("tenbin", FruitType.PARAMECIA, 6, false, 0xD0B060, 0x6A5A3A, Element.PHYSICAL);
        def("hari", FruitType.PARAMECIA, 4, false, 0xA0A0A0, 0x606060, Element.PIERCE);
        def("hoshi", FruitType.LOGIA, 9, false, 0x1A1A4A, 0xF0E08A, Element.LIGHT);
        def("uzu", FruitType.PARAMECIA, 6, false, 0x2A6AB0, 0x8AD0F0, Element.WATER);
    }

    /** Load implementation classes (each registers its abilities onto its fruit). */
    public static synchronized void bootstrap() {
        if (booted) return;
        booted = true;
        com.grandline.fruit.impl.MeraFruit.init(get("mera"));
        com.grandline.fruit.impl.HieFruit.init(get("hie"));
        com.grandline.fruit.impl.GoroFruit.init(get("goro"));
        com.grandline.fruit.impl.GomuFruit.init(get("gomu"));
        com.grandline.fruit.impl.BaraFruit.init(get("bara"));
        com.grandline.fruit.impl.OpeFruit.init(get("ope"));
        com.grandline.fruit.impl.WolfFruit.init(get("inu_wolf"));
        com.grandline.fruit.impl.KazeFruit.init(get("kaze"));
        // ---- second wave ----
        com.grandline.fruit.impl.PikaFruit.init(get("pika"));
        com.grandline.fruit.impl.MaguFruit.init(get("magu"));
        com.grandline.fruit.impl.SunaFruit.init(get("suna"));
        com.grandline.fruit.impl.MokuFruit.init(get("moku"));
        com.grandline.fruit.impl.YamiFruit.init(get("yami"));
        com.grandline.fruit.impl.GuraFruit.init(get("gura"));
        com.grandline.fruit.impl.ItoFruit.init(get("ito"));
        com.grandline.fruit.impl.HanaFruit.init(get("hana"));
        com.grandline.fruit.impl.BariFruit.init(get("bari"));
        com.grandline.fruit.impl.PhoenixFruit.init(get("tori_phoenix"));
        com.grandline.fruit.impl.BisonFruit.init(get("ushi_bison"));
        com.grandline.fruit.impl.LeopardFruit.init(get("neko_leopard"));
        com.grandline.fruit.impl.ShioFruit.init(get("shio"));
        com.grandline.fruit.impl.KagamiFruit.init(get("kagami"));
        com.grandline.fruit.impl.UzuFruit.init(get("uzu"));
        com.grandline.fruit.impl.KaneFruit.init(get("kane"));
        // ---- third wave: remaining canon fruits ----
        com.grandline.fruit.impl.YukiFruit.init(get("yuki"));
        com.grandline.fruit.impl.GasuFruit.init(get("gasu"));
        com.grandline.fruit.impl.NumaFruit.init(get("numa"));
        com.grandline.fruit.impl.MoriFruit.init(get("mori"));
        com.grandline.fruit.impl.BomuFruit.init(get("bomu"));
        com.grandline.fruit.impl.SupaFruit.init(get("supa"));
        com.grandline.fruit.impl.NikyuFruit.init(get("nikyu"));
        com.grandline.fruit.impl.MochiFruit.init(get("mochi"));
        com.grandline.fruit.impl.HoroFruit.init(get("horo"));
        com.grandline.fruit.impl.DoruFruit.init(get("doru"));
        com.grandline.fruit.impl.SoruFruit.init(get("soru"));
        com.grandline.fruit.impl.HobiFruit.init(get("hobi"));
        com.grandline.fruit.impl.TokiFruit.init(get("toki"));
        com.grandline.fruit.impl.ElephantFruit.init(get("zou_elephant"));
        com.grandline.fruit.impl.AllosaurusFruit.init(get("ryu_allosaurus"));
        com.grandline.fruit.impl.PteranodonFruit.init(get("ryu_pteranodon"));
        com.grandline.fruit.impl.SpinosaurusFruit.init(get("ryu_spinosaurus"));
        com.grandline.fruit.impl.SeiryuFruit.init(get("uo_seiryu"));
        com.grandline.fruit.impl.OrochiFruit.init(get("hebi_orochi"));
    }

    @Nullable
    public static DevilFruit get(String id) {
        return ALL.get(id);
    }

    public static Collection<DevilFruit> all() {
        return ALL.values();
    }

    public static boolean implemented(DevilFruit f) {
        return !f.abilities().isEmpty();
    }

    /** Fruits that may appear in the world (implemented only, so nobody eats a powerless fruit). */
    public static List<DevilFruit> available() {
        List<DevilFruit> l = new ArrayList<>();
        for (DevilFruit f : ALL.values()) if (implemented(f)) l.add(f);
        return l;
    }

    private DevilFruits() {}
}
