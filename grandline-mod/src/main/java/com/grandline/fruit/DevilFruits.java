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
        def("tako_kraken", FruitType.MYTHICAL_ZOAN, 8, false, 0x8A2A4A, 0xE07A9A, Element.BLUNT).zoanModel = "kraken";
        def("rasen", FruitType.PARAMECIA, 6, false, 0xE0A04A, 0x8A4A2A, Element.IMPACT);
        def("kane", FruitType.PARAMECIA, 5, false, 0xE0C04A, 0x8A7A2A, Element.SOUND);
        def("tsuchi", FruitType.LOGIA, 6, false, 0x7A5A3A, 0x4A3A2A, Element.PHYSICAL);
        def("kiri", FruitType.LOGIA, 5, false, 0xD8E0E8, 0xA0A8B0, Element.WATER);
        def("tenbin", FruitType.PARAMECIA, 6, false, 0xD0B060, 0x6A5A3A, Element.PHYSICAL);
        def("hari", FruitType.PARAMECIA, 4, false, 0xA0A0A0, 0x606060, Element.PIERCE);
        def("hoshi", FruitType.LOGIA, 9, false, 0x1A1A4A, 0xF0E08A, Element.LIGHT);
        def("uzu", FruitType.PARAMECIA, 6, false, 0x2A6AB0, 0x8AD0F0, Element.WATER);
        // ---- original fruits, third wave (designs: docs/FRUITS_ORIGINAL.md) ----
        def("mitsu", FruitType.LOGIA, 6, false, 0xF0B020, 0xFFE08A, Element.PHYSICAL);
        def("kabi", FruitType.LOGIA, 6, false, 0x6A7A3A, 0xC8D0A0, Element.POISON);
        def("funa", FruitType.PARAMECIA, 5, false, 0x8A5A2A, 0xF0E6C8, Element.BLUNT);
        def("tenki", FruitType.PARAMECIA, 8, false, 0x5A8AD0, 0xF0F0F0, Element.LIGHTNING);
        def("kusuri", FruitType.PARAMECIA, 5, false, 0x6AC08A, 0xF0F0F0, Element.POISON);
        def("wana", FruitType.PARAMECIA, 4, false, 0x5A4A3A, 0xB0A070, Element.PIERCE);
        def("kusari", FruitType.PARAMECIA, 4, false, 0x707880, 0xB0B8C0, Element.BLUNT);
        def("nemu", FruitType.PARAMECIA, 6, false, 0x6A5AA8, 0xD8D0F8, Element.SOUND);
        def("nendo", FruitType.SPECIAL_PARAMECIA, 5, false, 0xB0703A, 0xD8A878, Element.BLUNT);
        def("abura", FruitType.SPECIAL_PARAMECIA, 4, false, 0x2A2418, 0xA08A3A, Element.PHYSICAL);
        def("same_megalodon", FruitType.ANCIENT_ZOAN, 6, false, 0x5A7A8A, 0xE8E8E8, Element.SLASH).zoanModel = "megalodon";
        def("kame_genbu", FruitType.MYTHICAL_ZOAN, 8, false, 0x1E2A2A, 0x3A8A6A, Element.WATER).zoanModel = "genbu";
        def("kaeru_dartfrog", FruitType.ZOAN, 3, false, 0x2A8AE0, 0x1A1A1A, Element.POISON).zoanModel = "dart_frog";
        // ---- original fruits, fourth wave: White Sea, deep sea / Fish-Man Island, Sabaody / New World (docs/FRUITS_ORIGINAL.md) ----
        def("hai", FruitType.LOGIA, 7, false, 0x5A5A60, 0xE0602A, Element.SMOKE);
        def("yako", FruitType.LOGIA, 6, false, 0x10305A, 0x5AF0FF, Element.LIGHT);
        def("shinku", FruitType.LOGIA, 8, false, 0xE4ECF4, 0x2A2A3E, Element.WIND);
        def("kai", FruitType.SPECIAL_PARAMECIA, 6, false, 0xF0E2C8, 0xD8907A, Element.PHYSICAL);
        def("fusen", FruitType.PARAMECIA, 4, false, 0xE8405A, 0xFFE04A, Element.BLUNT);
        def("shinju", FruitType.PARAMECIA, 5, false, 0xF4F0EA, 0xC8B4E0, Element.PHYSICAL);
        def("kagi", FruitType.PARAMECIA, 7, false, 0xC8A040, 0x3A2A1A, Element.PHYSICAL);
        def("hanabi", FruitType.PARAMECIA, 5, false, 0xE83A5A, 0x4AC8FF, Element.FIRE);
        def("hata", FruitType.PARAMECIA, 7, false, 0x1A1A1A, 0xF4F4F4, Element.PHYSICAL);
        def("taru", FruitType.PARAMECIA, 3, false, 0x8A5A2A, 0x5A5A5A, Element.BLUNT);
        def("kujira_bakekujira", FruitType.MYTHICAL_ZOAN, 9, false, 0xD8E0E8, 0x2A3A5A, Element.DARKNESS).zoanModel = "bakekujira";
        def("itachi_raiju", FruitType.MYTHICAL_ZOAN, 8, false, 0x3A5AD8, 0xF2E14A, Element.LIGHTNING).zoanModel = "raiju";
        def("sasori_seascorpion", FruitType.ANCIENT_ZOAN, 6, false, 0x7A3A2A, 0xD8A060, Element.PIERCE).zoanModel = "sea_scorpion";
        def("tonbo_meganeura", FruitType.ANCIENT_ZOAN, 5, false, 0x2A8A7A, 0xC8F0E8, Element.SLASH).zoanModel = "meganeura";
        def("kaba_hippo", FruitType.ZOAN, 4, false, 0x8A6A7A, 0xE87A7A, Element.BLUNT).zoanModel = "hippo";
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
        // ---- third wave: original fruits ----
        com.grandline.fruit.impl.TakoKrakenFruit.init(get("tako_kraken"));
        com.grandline.fruit.impl.RasenFruit.init(get("rasen"));
        com.grandline.fruit.impl.TsuchiFruit.init(get("tsuchi"));
        com.grandline.fruit.impl.KiriFruit.init(get("kiri"));
        com.grandline.fruit.impl.TenbinFruit.init(get("tenbin"));
        com.grandline.fruit.impl.HariFruit.init(get("hari"));
        com.grandline.fruit.impl.HoshiFruit.init(get("hoshi"));
        com.grandline.fruit.impl.MitsuFruit.init(get("mitsu"));
        com.grandline.fruit.impl.KabiFruit.init(get("kabi"));
        com.grandline.fruit.impl.FunaFruit.init(get("funa"));
        com.grandline.fruit.impl.TenkiFruit.init(get("tenki"));
        com.grandline.fruit.impl.KusuriFruit.init(get("kusuri"));
        com.grandline.fruit.impl.WanaFruit.init(get("wana"));
        com.grandline.fruit.impl.KusariFruit.init(get("kusari"));
        com.grandline.fruit.impl.NemuFruit.init(get("nemu"));
        com.grandline.fruit.impl.NendoFruit.init(get("nendo"));
        com.grandline.fruit.impl.AburaFruit.init(get("abura"));
        com.grandline.fruit.impl.MegalodonFruit.init(get("same_megalodon"));
        com.grandline.fruit.impl.GenbuFruit.init(get("kame_genbu"));
        com.grandline.fruit.impl.DartFrogFruit.init(get("kaeru_dartfrog"));
        // ---- fourth wave: original fruits ----
        com.grandline.fruit.impl.HaiFruit.init(get("hai"));
        com.grandline.fruit.impl.YakoFruit.init(get("yako"));
        com.grandline.fruit.impl.ShinkuFruit.init(get("shinku"));
        com.grandline.fruit.impl.KaiFruit.init(get("kai"));
        com.grandline.fruit.impl.FusenFruit.init(get("fusen"));
        com.grandline.fruit.impl.ShinjuFruit.init(get("shinju"));
        com.grandline.fruit.impl.KagiFruit.init(get("kagi"));
        com.grandline.fruit.impl.HanabiFruit.init(get("hanabi"));
        com.grandline.fruit.impl.HataFruit.init(get("hata"));
        com.grandline.fruit.impl.TaruFruit.init(get("taru"));
        com.grandline.fruit.impl.BakekujiraFruit.init(get("kujira_bakekujira"));
        com.grandline.fruit.impl.RaijuFruit.init(get("itachi_raiju"));
        com.grandline.fruit.impl.SeaScorpionFruit.init(get("sasori_seascorpion"));
        com.grandline.fruit.impl.MeganeuraFruit.init(get("tonbo_meganeura"));
        com.grandline.fruit.impl.HippoFruit.init(get("kaba_hippo"));
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
