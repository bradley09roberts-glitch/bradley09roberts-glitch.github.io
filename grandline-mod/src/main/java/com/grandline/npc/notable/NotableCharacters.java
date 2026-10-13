package com.grandline.npc.notable;

import com.grandline.player.Faction;
import com.grandline.player.Race;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The original cast of "The Saltfire Saga" (docs/STORY.md) as data. Every entry is spawned once per world on its
 * home island by {@link NotableSpawner}. Names, titles and lines live in the language file:
 * {@code notable.grandline.<id>}, {@code notable.grandline.<id>.title}, {@code dialogue.grandline.notable.<id>}
 * (greeting) and, for recruitable crew, {@code dialogue.grandline.notable.<id>.crew} and {@code .joined}.
 */
public final class NotableCharacters {
    /** Story grouping (for docs, tests and dialogue flavour). */
    public enum Group { LEGEND, EMBER, EMPEROR, EMPEROR_OFFICER, STEWARD, WORLD_NOBLE, CIPHER_POL, MARINE, REVOLUTIONARY, WARLORD, SUPERNOVA, RULER, VILLAIN, PATRON, CREW }

    /**
     * How a notable treats players. FRIENDLY never starts a fight; RIVAL only fights the deeply hated; LAWFUL
     * attacks wanted criminals; VILLAIN attacks anyone it does not like. FRIENDLY and RIVAL characters (and every
     * crew member) yield when beaten instead of dying; striking a yielded character again is fatal.
     */
    public enum Disposition { FRIENDLY, RIVAL, LAWFUL, VILLAIN }

    public record Notable(String id, Group group, Faction faction, String role, int tier, String island, int dx, int dz,
                          @Nullable String fruit, String style, int obs, int arm, int coq, String race, float scale,
                          String weapon, Disposition disposition, boolean recruitable, String crewRole) {
        public String nameKey() { return "notable.grandline." + id; }
        public String titleKey() { return "notable.grandline." + id + ".title"; }
        public String greetingKey() { return "dialogue.grandline.notable." + id; }
        public String crewGreetingKey() { return "dialogue.grandline.notable." + id + ".crew"; }
        public String joinedKey() { return "dialogue.grandline.notable." + id + ".joined"; }
        public String crewRoleKey() { return "crew_role.grandline." + crewRole; }
        public String recruitQuest() { return "recruit_" + id; }
        public String personalQuest() { return "personal_" + id; }
        public String giverRole() { return "notable:" + id; }

        public boolean yields() {
            return recruitable || disposition == Disposition.FRIENDLY || disposition == Disposition.RIVAL;
        }

        /** Only the deadliest villains finish off knocked-down players; the rest rob, jail or gloat. */
        public boolean killsDowned() {
            return disposition == Disposition.VILLAIN && tier >= 9;
        }

        public float maxHealth() {
            float hp = 40 + 18f * tier * tier;
            return disposition == Disposition.VILLAIN || disposition == Disposition.LAWFUL ? hp : hp * 0.85f;
        }

        public Race raceEnum() {
            return Race.byId(race);
        }
    }

    private static final Map<String, Notable> ALL = new LinkedHashMap<>();
    private static final Map<String, List<Notable>> BY_ISLAND = new HashMap<>();
    /**
     * People generated for the current world from its seed (island stories, job-board outlaws and contacts:
     * quest/story). Installed when the world starts, never saved: the seed regenerates them. Their names and titles are
     * components (generated names) instead of language keys.
     */
    private static final Map<String, Notable> GENERATED = new LinkedHashMap<>();
    private static final Map<String, List<Notable>> GENERATED_BY_ISLAND = new HashMap<>();
    private static final Map<String, net.minecraft.network.chat.Component> NAMES = new HashMap<>(), TITLES = new HashMap<>();

    // ------------------------------------------------------------------ builder

    static final class B {
        final String id, role, island;
        final Group group;
        final Faction faction;
        final int tier, dx, dz;
        String fruit, style = "brawler", race = "human", weapon = "", crewRole = "";
        int obs, arm, coq;
        float scale = 0;
        Disposition disp = Disposition.RIVAL;

        B(String id, Group g, Faction f, String role, int tier, String island, int dx, int dz) {
            this.id = id;
            this.group = g;
            this.faction = f;
            this.role = role;
            this.tier = tier;
            this.island = island;
            this.dx = dx;
            this.dz = dz;
        }

        B fruit(String f) { fruit = f; return this; }
        B style(String s) { style = s; return this; }
        B haki(int o, int a, int c) { obs = o; arm = a; coq = c; return this; }
        B race(String r) { race = r; return this; }
        B scale(float s) { scale = s; return this; }
        B weapon(String w) { weapon = w; return this; }
        B disp(Disposition d) { disp = d; return this; }
        B crew(String role) { crewRole = role; return this; }
    }

    static B n(String id, Group g, Faction f, String role, int tier, String island, int dx, int dz) {
        return new B(id, g, f, role, tier, island, dx, dz);
    }

    static void add(B b) {
        Race r = Race.byId(b.race);
        float scale = b.scale > 0 ? b.scale : r.baseScale;
        Notable n = new Notable(b.id, b.group, b.faction, b.role, b.tier, b.island, b.dx, b.dz, b.fruit, b.style, b.obs, b.arm, b.coq,
                b.race, scale, b.weapon, b.disp, !b.crewRole.isEmpty(), b.crewRole);
        if (ALL.put(n.id(), n) != null) throw new IllegalStateException("duplicate notable " + n.id());
        BY_ISLAND.computeIfAbsent(n.island(), k -> new ArrayList<>()).add(n);
    }

    static {
        final Group LEGEND = Group.LEGEND, EMBER = Group.EMBER, EMP = Group.EMPEROR, OFF = Group.EMPEROR_OFFICER, STEW = Group.STEWARD,
                NOBLE = Group.WORLD_NOBLE, CP = Group.CIPHER_POL, MAR = Group.MARINE, REV = Group.REVOLUTIONARY, WAR = Group.WARLORD,
                NOVA = Group.SUPERNOVA, RULER = Group.RULER, VIL = Group.VILLAIN, PATRON = Group.PATRON, CREW = Group.CREW;
        final Faction PIRATE = Faction.PIRATE, MARINE = Faction.MARINE, WG = Faction.WORLD_GOVERNMENT, CPF = Faction.CIPHER_POL,
                REVF = Faction.REVOLUTIONARY, BH = Faction.BOUNTY_HUNTER, CIV = Faction.CIVILIAN;
        final Disposition FRIENDLY = Disposition.FRIENDLY, RIVAL = Disposition.RIVAL, LAWFUL = Disposition.LAWFUL, VILLAIN = Disposition.VILLAIN;

        // ---------------- The legend ----------------
        add(n("haruka", LEGEND, CIV, "elder", 9, "marineford", -18, 22).haki(95, 60, 0).disp(FRIENDLY));
        add(n("wilhelmina", LEGEND, CIV, "lighthouse_keeper", 5, "twin_capes", 5, 4).haki(70, 30, 0).disp(FRIENDLY));

        // ---------------- The Four Embers (the Saltfire King's retired crew, one per starter isle) ----------------
        add(n("fumio", EMBER, CIV, "elder", 6, "shiokaze_isle", 30, -48).style("black_leg").haki(60, 55, 0).disp(FRIENDLY));
        add(n("solveig", EMBER, CIV, "elder", 6, "fuyugane_isle", 30, 48).style("gunslinger").weapon("flintlock").haki(55, 50, 0).disp(FRIENDLY));
        add(n("percival", EMBER, CIV, "elder", 6, "kirinoha_isle", -30, 48).style("storm_dance").weapon("spear").haki(60, 50, 0).disp(FRIENDLY));
        add(n("mahina", EMBER, CIV, "elder", 6, "hinata_isle", -30, -48).style("life_return").haki(65, 45, 0).disp(FRIENDLY));

        // ---------------- The Four Emperors ----------------
        add(n("hrolf", EMP, PIRATE, "giant_warrior", 12, "elbaph", 22, 30).style("hasshoken").weapon("iron_kanabo").haki(90, 98, 90).race("giant").scale(4.5f).disp(RIVAL));
        add(n("kurogane", EMP, PIRATE, "pirate_captain", 12, "onigashima", 10, 10).fruit("tako_kraken").weapon("iron_kanabo").haki(85, 95, 85).disp(VILLAIN));
        add(n("barnum", EMP, PIRATE, "pirate_captain", 12, "whole_cake_island", 12, -12).fruit("hobi").weapon("war_hammer").haki(80, 90, 80).race("buccaneer").disp(VILLAIN));
        add(n("aurelia", EMP, PIRATE, "pirate_captain", 12, "lodestar_island", 0, 20).fruit("hoshi").style("ittoryu").weapon("fine_katana").haki(92, 90, 92).disp(RIVAL));
        // ---------------- Emperor officers ----------------
        add(n("sigrid", OFF, PIRATE, "giant_warrior", 10, "elbaph", -26, 30).weapon("war_hammer").haki(70, 85, 0).race("giant").scale(4.0f).disp(RIVAL));
        add(n("ulfric", OFF, PIRATE, "pirate_captain", 9, "elbaph", 36, -20).style("gunslinger").weapon("flintlock").haki(60, 70, 0).disp(RIVAL));
        add(n("gorou", OFF, PIRATE, "pirate_captain", 10, "onigashima", -20, 16).fruit("ryu_allosaurus").weapon("kanabo").haki(70, 80, 0).disp(VILLAIN));
        add(n("tsubaki", OFF, PIRATE, "pirate_captain", 10, "onigashima", 20, -20).style("nitoryu").weapon("fine_katana").haki(75, 80, 0).disp(VILLAIN));
        add(n("marzipan", OFF, PIRATE, "pirate_captain", 10, "whole_cake_island", -20, 10).fruit("mochi").haki(85, 80, 40).disp(VILLAIN));
        add(n("fennimore", OFF, PIRATE, "pirate_captain", 9, "whole_cake_island", 26, 20).style("storm_dance").weapon("steel_spear").haki(70, 70, 0).race("long_leg").disp(VILLAIN));
        add(n("asagao", OFF, PIRATE, "pirate_captain", 10, "lodestar_island", 20, -10).style("ittoryu").weapon("fine_katana").haki(80, 85, 0).disp(RIVAL));
        add(n("thaddeus", OFF, PIRATE, "pirate_captain", 9, "lodestar_island", -20, -10).style("tide_grapple").haki(85, 70, 0).disp(RIVAL));

        // ---------------- The Five Stewards of the Pale Throne ----------------
        add(n("aldric", STEW, WG, "celestial_guard", 11, "mary_geoise", 6, 6).fruit("toki").style("rokushiki").weapon("fine_katana").haki(90, 90, 60).disp(LAWFUL));
        add(n("morwenna", STEW, WG, "celestial_guard", 11, "mary_geoise", 8, -6).fruit("soru").style("rokushiki").haki(90, 85, 60).disp(LAWFUL));
        add(n("cassius", STEW, WG, "celestial_guard", 11, "mary_geoise", -6, 8).fruit("gura").style("rokushiki").haki(85, 95, 60).disp(LAWFUL));
        add(n("ottavia", STEW, WG, "celestial_guard", 11, "mary_geoise", -8, -6).fruit("bari").style("rokushiki").haki(85, 90, 60).disp(LAWFUL));
        add(n("leopold", STEW, WG, "celestial_guard", 11, "mary_geoise", 0, 10).fruit("numa").style("rokushiki").haki(85, 90, 60).disp(LAWFUL));
        add(n("pompadour", NOBLE, WG, "noble", 3, "sabaody_archipelago", 250, 172).weapon("flintlock").disp(LAWFUL));

        // ---------------- Cipher Pol ----------------
        add(n("vespera", CP, CPF, "cp_leader", 11, "egghead", 15, 15).fruit("ito").style("rokushiki").haki(90, 90, 30).disp(LAWFUL));
        add(n("gregor", CP, CPF, "cp_leader", 9, "enies_lobby", 6, 12).fruit("hari").style("rokushiki").haki(80, 85, 0).disp(LAWFUL));
        add(n("nimue", CP, CPF, "cp_agent", 8, "water_7", 30, -22).fruit("ryu_pteranodon").style("rokushiki").haki(70, 75, 0).disp(LAWFUL));
        add(n("brick", CP, CPF, "cp_agent", 8, "water_7", 410, 248).fruit("tsuchi").style("rokushiki").haki(65, 75, 0).disp(LAWFUL));

        // ---------------- Marines ----------------
        add(n("constance", MAR, MARINE, "admiral", 12, "marineford", 0, 8).fruit("uzu").style("rokushiki").weapon("fine_katana").haki(95, 95, 70).disp(LAWFUL));
        add(n("zenith", MAR, MARINE, "admiral", 11, "sabaody_archipelago", -40, 30).fruit("pika").style("rokushiki").haki(90, 90, 0).disp(LAWFUL));
        add(n("nadir", MAR, MARINE, "admiral", 11, "marineford", 12, -8).fruit("yami").style("rokushiki").haki(90, 90, 0).disp(LAWFUL));
        add(n("meridian", MAR, MARINE, "admiral", 11, "new_marineford", 0, 10).fruit("magu").style("rokushiki").haki(85, 92, 0).disp(LAWFUL));
        add(n("osric", MAR, MARINE, "vice_admiral", 9, "marineford", -10, -12).style("hasshoken").weapon("iron_kanabo").haki(75, 85, 0).disp(LAWFUL));
        add(n("horatio", MAR, MARINE, "vice_admiral", 8, "baratie", 3, 8).haki(70, 80, 0).disp(FRIENDLY));
        add(n("dmitri", MAR, MARINE, "vice_admiral", 9, "enies_lobby", 370, -140).style("rokushiki").weapon("fine_cutlass").haki(75, 80, 0).disp(LAWFUL));
        add(n("seraphine", MAR, MARINE, "vice_admiral", 9, "g5_base", 10, 10).style("gunslinger").weapon("flintlock").haki(70, 75, 0).disp(LAWFUL));
        add(n("prudence", MAR, MARINE, "vice_admiral", 9, "new_marineford", -12, 10).style("iaido").weapon("fine_katana").haki(80, 80, 0).disp(LAWFUL));
        add(n("gallowgate", MAR, MARINE, "marine_captain", 3, "shells_town", 96, -34).weapon("war_hammer").haki(0, 10, 0).disp(VILLAIN));
        add(n("valka", MAR, MARINE, "marine_captain", 6, "loguetown", 210, 112).fruit("yuki").style("rokushiki").weapon("seastone_jitte").haki(60, 60, 0).disp(LAWFUL));
        add(n("grimsby", MAR, WG, "warden", 9, "impel_down", 4, -8).weapon("seastone_jitte").haki(70, 80, 0).disp(LAWFUL));

        // ---------------- Revolutionary Army ----------------
        add(n("sigrun", REV, REVF, "revolutionary", 11, "baltigo", 10, 10).fruit("tori_phoenix").haki(92, 90, 70).disp(RIVAL));
        add(n("lysander", REV, REVF, "revolutionary", 9, "baltigo", -12, 8).style("iaido").weapon("fine_katana").haki(80, 80, 0).disp(RIVAL));
        add(n("tamsin", REV, REVF, "revolutionary", 7, "mirror_ball_island", 0, 10).style("tide_grapple").haki(60, 70, 0).race("whalefolk").disp(RIVAL));
        add(n("bjorn", REV, REVF, "revolutionary", 7, "swallow_island", 10, 0).style("anchor_brawl").weapon("anchor_chain").haki(60, 70, 0).race("stonehide").disp(RIVAL));
        add(n("oleander", REV, REVF, "revolutionary", 7, "ohara", 15, 15).style("storm_dance").weapon("naginata").haki(65, 65, 0).disp(RIVAL));
        add(n("mireille", REV, REVF, "revolutionary", 7, "baterilla", 0, 15).style("black_leg").haki(60, 70, 0).race("emberkin").disp(RIVAL));
        add(n("pim", REV, REVF, "revolutionary", 4, "loguetown", -40, 30).weapon("bo_staff").haki(40, 30, 0).race("long_leg").disp(FRIENDLY));

        // ---------------- The Seven Warlords of the Sea ----------------
        add(n("saltmarsh", WAR, PIRATE, "pirate_captain", 8, "alabasta", 240, -500).fruit("shio").weapon("fine_cutlass").haki(60, 75, 0).disp(VILLAIN));
        add(n("sterling", WAR, PIRATE, "pirate_captain", 10, "hachinosu", 10, 10).fruit("tenbin").style("gunslinger").weapon("flintlock").haki(80, 85, 60).disp(VILLAIN));
        add(n("lucrezia", WAR, PIRATE, "pirate_captain", 8, "thriller_bark", 10, 10).fruit("kagami").weapon("rapier").haki(60, 70, 0).disp(VILLAIN));
        add(n("hisui", WAR, PIRATE, "kuja_warrior", 9, "amazon_lily", 10, 15).fruit("hebi_orochi").weapon("war_bow").haki(85, 90, 70).disp(RIVAL));
        add(n("jinpachi", WAR, PIRATE, "master", 10, "kuraigana_island", 8, 8).style("iaido").weapon("yoru").haki(90, 95, 0).disp(RIVAL));
        add(n("leonhart", WAR, PIRATE, "master", 9, "zou", 15, 0).style("electro").haki(80, 85, 0).race("mink").disp(RIVAL));
        add(n("pipistrello", WAR, PIRATE, "pirate_captain", 8, "green_bit", 5, 5).fruit("rasen").haki(50, 70, 0).race("tontatta").disp(VILLAIN));

        // ---------------- Supernovas (rival rookies) ----------------
        add(n("tobias", NOVA, PIRATE, "pirate_captain", 4, "loguetown", -60, -40).fruit("bomu").haki(30, 30, 20).disp(RIVAL));
        add(n("felicity", NOVA, PIRATE, "pirate_captain", 6, "jaya", 30, 40).fruit("neko_leopard").style("electro").weapon("knife").haki(50, 40, 0).race("mink").disp(RIVAL));
        add(n("matthias", NOVA, PIRATE, "pirate_captain", 6, "water_7", -50, 30).fruit("zou_elephant").style("hasshoken").weapon("bo_staff").haki(50, 60, 0).disp(RIVAL));
        add(n("coralie", NOVA, PIRATE, "pirate_captain", 6, "sabaody_archipelago", -60, -40).style("tide_grapple").haki(55, 50, 0).race("merfolk").disp(RIVAL));
        add(n("mortimer", NOVA, PIRATE, "pirate_captain", 6, "thriller_bark", -30, 20).fruit("horo").style("anchor_brawl").weapon("anchor_chain").haki(45, 55, 0).race("long_arm").disp(RIVAL));
        add(n("edison", NOVA, PIRATE, "pirate_captain", 6, "sabaody_archipelago", 60, -60).style("sniper").weapon("musket").haki(50, 40, 0).race("three_eye").disp(RIVAL));
        add(n("ayame", NOVA, PIRATE, "pirate_captain", 5, "long_ring_long_land", 480, 0).weapon("whip").haki(50, 50, 0).disp(RIVAL));
        add(n("raven", NOVA, PIRATE, "pirate_captain", 5, "drum_island", 120, -60).fruit("moku").style("storm_dance").weapon("naginata").haki(50, 50, 0).race("stormwing").disp(RIVAL));
        add(n("brunhilde", NOVA, PIRATE, "pirate_captain", 6, "little_garden", -60, 40).fruit("ushi_bison").weapon("war_hammer").haki(40, 60, 0).race("giant").scale(1.6f).disp(RIVAL));
        add(n("nox", NOVA, PIRATE, "pirate_captain", 11, "laugh_tale", 15, -15).fruit("gasu").style("rokushiki").haki(85, 85, 40).disp(VILLAIN));

        // ---------------- Blue rulers and villains ----------------
        add(n("rusty", VIL, PIRATE, "pirate_captain", 2, "spider_miles", 10, 10).style("anchor_brawl").weapon("anchor_chain").disp(VILLAIN));
        add(n("haakon", RULER, CIV, "noble", 3, "lvneel", 20, 20).weapon("fine_cutlass").haki(20, 20, 0).disp(FRIENDLY));
        add(n("kagerou", RULER, CIV, "master", 6, "kano_country", 5, 46).style("ittoryu").weapon("fine_katana").haki(50, 50, 0).disp(FRIENDLY));
        add(n("isadora", RULER, CIV, "noble", 3, "ilisia_kingdom", 10, -140).weapon("rapier").haki(30, 10, 0).disp(FRIENDLY));
        add(n("kiko", RULER, CIV, "master", 6, "karate_island", 6, 6).style("tide_grapple").haki(50, 50, 0).disp(FRIENDLY));
        add(n("gelato", RULER, CIV, "noble", 2, "sorbet_kingdom", 0, 20).disp(FRIENDLY));
        add(n("rotgut", VIL, PIRATE, "pirate_captain", 2, "orange_town", 40, -30).weapon("cutlass").disp(VILLAIN));
        add(n("gizmo", VIL, PIRATE, "pirate_captain", 3, "syrup_village", -80, 60).style("gunslinger").weapon("flintlock").haki(10, 0, 0).disp(VILLAIN));
        add(n("fosco", VIL, PIRATE, "pirate_captain", 4, "baratie", -4, -14).weapon("kanabo").haki(10, 20, 0).disp(VILLAIN));
        add(n("gorgo", VIL, PIRATE, "fishman_boss", 5, "cocoyasi", 322, -52).style("fishman_karate").weapon("war_trident").haki(20, 30, 0).race("fishman").disp(VILLAIN));
        // ---------------- Part I chapters (quest/arc): the missing skipper, the honest lieutenant, the mayor, two new villains ----------------
        add(n("mio", PATRON, CIV, "dockworker", 2, "shells_town", 78, -44).disp(FRIENDLY));
        add(n("hayashi", MAR, MARINE, "marine_officer", 3, "shells_town", 104, -26).weapon("cutlass").haki(10, 10, 0).disp(FRIENDLY));
        add(n("clementine", PATRON, CIV, "farmer", 2, "orange_town", -10, -22).disp(FRIENDLY));
        add(n("corvel", VIL, CIV, "noble", 2, "ilisia_kingdom", -16, -128).weapon("rapier").haki(10, 0, 0).disp(VILLAIN));
        add(n("brinicle", VIL, PIRATE, "pirate_captain", 3, "sorbet_kingdom", 64, -36).style("tide_grapple").haki(10, 10, 0).disp(VILLAIN));
        // ---------------- Paradise rulers and villains ----------------
        add(n("doubloon", VIL, PIRATE, "pirate_captain", 4, "whisky_peak", -190, 10).style("gunslinger").weapon("flintlock").haki(20, 20, 0).disp(VILLAIN));
        add(n("farthing", VIL, PIRATE, "pirate_captain", 4, "whisky_peak", -210, -10).weapon("dual_blades").haki(20, 20, 0).disp(VILLAIN));
        add(n("astrid", RULER, CIV, "giant_warrior", 8, "little_garden", 10, 10).weapon("war_hammer").haki(60, 70, 0).race("giant").scale(4.5f).disp(FRIENDLY));
        add(n("ylva", RULER, CIV, "giant_warrior", 8, "little_garden", -10, -10).weapon("iron_kanabo").haki(60, 70, 0).race("giant").scale(4.5f).disp(FRIENDLY));
        add(n("shilling", VIL, PIRATE, "pirate_captain", 5, "little_garden", 210, 130).fruit("doru").haki(30, 30, 0).disp(VILLAIN));
        add(n("panacea", VIL, PIRATE, "pirate_captain", 4, "drum_island", 4, 4).weapon("knife").haki(10, 10, 0).disp(VILLAIN));
        add(n("rashid", RULER, CIV, "noble", 6, "alabasta", -340, 10).fruit("mori").haki(50, 40, 20).disp(FRIENDLY));
        add(n("celestine", VIL, PIRATE, "pirate_captain", 6, "alabasta", 260, -510).fruit("hana").haki(40, 40, 0).disp(VILLAIN));
        add(n("usurio", VIL, PIRATE, "pirate_captain", 5, "jaya", -20, -20).style("gunslinger").weapon("flintlock").haki(30, 30, 0).disp(VILLAIN));
        add(n("hollis", RULER, CIV, "shipwright", 6, "water_7", 8, 8).weapon("war_hammer").haki(40, 50, 0).disp(FRIENDLY));
        add(n("augustin", VIL, PIRATE, "pirate_captain", 5, "sabaody_archipelago", 266, 150).weapon("war_hammer").haki(20, 20, 0).disp(VILLAIN));
        add(n("ume", PATRON, CIV, "elder", 6, "sabaody_archipelago", 10, 10).haki(70, 50, 0).disp(FRIENDLY));
        add(n("tsukumo", PATRON, CIV, "master", 9, "rusukaina", 0, 0).style("hasshoken").haki(98, 90, 0).disp(FRIENDLY));
        add(n("hesper", VIL, WG, "scholar", 7, "punk_hazard", 10, 10).weapon("hand_cannon").haki(20, 20, 0).disp(VILLAIN));
        add(n("florian", VIL, PIRATE, "noble", 7, "dressrosa", -190, -90).weapon("rapier").haki(50, 50, 0).disp(VILLAIN));
        add(n("hagane", VIL, PIRATE, "master", 10, "wano_country", 10, -190).fruit("uo_seiryu").style("iaido").weapon("fine_katana").haki(80, 85, 0).disp(VILLAIN));
        add(n("minerva", PATRON, WG, "scholar", 6, "egghead", -10, 10).haki(60, 20, 0).disp(FRIENDLY));
        add(n("hugo", PATRON, BH, "bartender", 7, "whisky_peak", 20, 20).style("gunslinger").weapon("musket").haki(60, 60, 0).disp(FRIENDLY));
        add(n("bartolo", PATRON, CIV, "merchant", 2, "loguetown", 30, -30).disp(FRIENDLY));

        // ---------------- Recruitable crew (each has recruit_<id> and personal_<id> quests) ----------------
        add(n("odile", CREW, CIV, "merchant", 4, "loguetown", 60, 40).weapon("weather_staff").haki(40, 10, 0).disp(FRIENDLY).crew("navigator"));
        add(n("hilde", CREW, CIV, "head_chef", 5, "little_garden", 40, -30).weapon("war_hammer").haki(30, 40, 0).race("giant").scale(2.2f).disp(FRIENDLY).crew("cook"));
        add(n("pippa", CREW, CIV, "doctor", 4, "drum_island", -30, 20).style("life_return").haki(40, 20, 0).race("long_arm").disp(FRIENDLY).crew("doctor"));
        add(n("kanna", CREW, CIV, "shipwright", 5, "water_7", 400, 270).style("anchor_brawl").weapon("anchor_chain").haki(30, 40, 0).disp(FRIENDLY).crew("shipwright"));
        add(n("petra", CREW, MARINE, "marine", 3, "shells_town", 80, -24).style("sniper").weapon("musket").haki(30, 10, 0).disp(FRIENDLY).crew("sniper"));
        add(n("carmen", CREW, CIV, "bartender", 3, "orange_town", -20, 15).fruit("kane").haki(20, 10, 0).disp(FRIENDLY).crew("musician"));
        add(n("sefa", CREW, CIV, "scholar", 5, "alabasta", -360, 30).fruit("suna").haki(40, 20, 0).disp(FRIENDLY).crew("archaeologist"));
        add(n("tomas", CREW, CIV, "dockworker", 3, "syrup_village", 60, -50).style("gunslinger").weapon("flintlock").haki(30, 10, 0).disp(FRIENDLY).crew("helmsman"));
        add(n("kenji", CREW, CIV, "waiter", 4, "baratie", 2, -6).style("nitoryu").weapon("dual_blades").haki(30, 30, 0).disp(FRIENDLY).crew("swordsman"));
        add(n("kaimon", CREW, CIV, "fishman_brute", 5, "cocoyasi", -110, 70).style("fishman_karate").haki(30, 40, 0).race("fishman").disp(FRIENDLY).crew("fighter"));
        add(n("aria", CREW, CIV, "lookout", 5, "jaya", 510, 290).style("sniper").weapon("war_bow").haki(50, 10, 0).race("sky_people").disp(FRIENDLY).crew("lookout"));
        add(n("ignatz", CREW, CIV, "merchant", 4, "whisky_peak", -180, 30).style("gunslinger").weapon("flintlock").haki(30, 20, 0).disp(FRIENDLY).crew("quartermaster"));
        add(n("nimbus", CREW, CIV, "cp_agent", 7, "enies_lobby", -20, 20).fruit("kiri").style("rokushiki").haki(60, 50, 0).disp(FRIENDLY).crew("spy"));
        add(n("gunnar", CREW, CIV, "giant_warrior", 6, "long_ring_long_land", 470, 30).style("hasshoken").haki(40, 60, 0).race("stonehide").disp(FRIENDLY).crew("bodyguard"));
        add(n("rin", CREW, CIV, "master", 8, "wano_country", 1250, 20).style("iaido").weapon("fine_katana").haki(70, 70, 0).disp(FRIENDLY).crew("swordswoman"));
        add(n("pashmina", CREW, CIV, "kuja_warrior", 8, "zou", -20, 10).style("electro").weapon("steel_spear").haki(70, 60, 0).race("mink").disp(FRIENDLY).crew("warrior"));
        add(n("bolt", CREW, CIV, "lookout", 7, "green_bit", -5, 5).style("sniper").weapon("slingshot").haki(50, 40, 0).race("tontatta").disp(FRIENDLY).crew("engineer"));
        add(n("elowen", CREW, CIV, "scholar", 9, "egghead", 20, -20).style("sniper").weapon("war_bow").haki(80, 60, 0).race("lunarian").disp(FRIENDLY).crew("stargazer"));
        NotableCharactersExtra.register(); // Skypiea, Fish-Man Island and the islanders of every other canon island
    }

    // ------------------------------------------------------------------ queries

    @Nullable
    public static Notable get(String id) {
        Notable n = ALL.get(id);
        return n != null ? n : GENERATED.get(id);
    }

    /** The whole cast: the hand-written characters, then the people generated for the current world. */
    public static Collection<Notable> all() {
        if (GENERATED.isEmpty()) return Collections.unmodifiableCollection(ALL.values());
        List<Notable> l = new ArrayList<>(ALL.size() + GENERATED.size());
        l.addAll(ALL.values());
        l.addAll(GENERATED.values());
        return Collections.unmodifiableList(l);
    }

    public static List<Notable> onIsland(String islandId) {
        List<Notable> a = BY_ISLAND.getOrDefault(islandId, List.of()), g = GENERATED_BY_ISLAND.getOrDefault(islandId, List.of());
        if (g.isEmpty()) return a;
        if (a.isEmpty()) return g;
        List<Notable> l = new ArrayList<>(a);
        l.addAll(g);
        return l;
    }

    public static Set<String> islands() {
        if (GENERATED_BY_ISLAND.isEmpty()) return Collections.unmodifiableSet(BY_ISLAND.keySet());
        Set<String> s = new LinkedHashSet<>(BY_ISLAND.keySet());
        s.addAll(GENERATED_BY_ISLAND.keySet());
        return s;
    }

    // ------------------------------------------------------------------ generated people (per world)

    /** A generated person: plain data, never registered in the hand-written cast. */
    public static Notable generated(String id, Group g, Faction f, String role, int tier, String island, int dx, int dz, String style, int obs, int arm,
                                    String race, String weapon, Disposition disp) {
        Race r = Race.byId(race);
        return new Notable(id, g, f, role, tier, island, dx, dz, null, style, obs, arm, 0, race, r.baseScale, weapon, disp, false, "");
    }

    /** Replace the generated people with these (name and title per id). */
    public static void installGenerated(Collection<Notable> people, Map<String, net.minecraft.network.chat.Component> names,
                                        Map<String, net.minecraft.network.chat.Component> titles) {
        clearGenerated();
        for (Notable n : people) {
            if (ALL.containsKey(n.id()) || GENERATED.put(n.id(), n) != null) throw new IllegalStateException("duplicate notable " + n.id());
            GENERATED_BY_ISLAND.computeIfAbsent(n.island(), k -> new ArrayList<>()).add(n);
        }
        NAMES.putAll(names);
        TITLES.putAll(titles);
    }

    public static void clearGenerated() {
        GENERATED.clear();
        GENERATED_BY_ISLAND.clear();
        NAMES.clear();
        TITLES.clear();
    }

    public static boolean isGenerated(String id) {
        return GENERATED.containsKey(id);
    }

    public static int generatedCount() {
        return GENERATED.size();
    }

    /** Display name: the language entry for the hand-written cast, the generated name for generated people. */
    public static net.minecraft.network.chat.Component name(Notable n) {
        net.minecraft.network.chat.Component c = NAMES.get(n.id());
        return c != null ? c : net.minecraft.network.chat.Component.translatable(n.nameKey());
    }

    public static net.minecraft.network.chat.Component title(Notable n) {
        net.minecraft.network.chat.Component c = TITLES.get(n.id());
        return c != null ? c : net.minecraft.network.chat.Component.translatable(n.titleKey());
    }

    public static List<Notable> recruitable() {
        List<Notable> l = new ArrayList<>();
        for (Notable n : ALL.values()) if (n.recruitable()) l.add(n);
        return l;
    }

    public static int count(Group g) {
        int c = 0;
        for (Notable n : ALL.values()) if (n.group() == g) c++;
        return c;
    }

    private NotableCharacters() {}
}
