package com.grandline.quest.story;

import com.grandline.world.SeaZone;
import net.minecraft.util.RandomSource;

import java.util.Locale;
import java.util.Set;

/**
 * Deterministic names for the people of the seas: first names, family names and epithets flavoured by the sea a
 * person comes from (East Blue: Wa-flavoured; North Blue: northern; West Blue: Latin; South Blue: southern and island
 * sounds; the Grand Line mixes all of them). Everything is built from syllables and word parts, so names are original;
 * a short list of canon names is rejected outright in case a combination lands on one. Same random state, same name.
 */
public final class PeopleNames {
    /** Naming culture of a sea. */
    public enum Culture { EAST, NORTH, WEST, SOUTH, GRAND, NEW }

    public static Culture culture(SeaZone zone) {
        return switch (zone) {
            case EAST_BLUE -> Culture.EAST;
            case NORTH_BLUE -> Culture.NORTH;
            case WEST_BLUE -> Culture.WEST;
            case SOUTH_BLUE -> Culture.SOUTH;
            case NEW_WORLD, WORLD_EDGE -> Culture.NEW;
            default -> Culture.GRAND;
        };
    }

    // ---------------------------------------------------------------- word parts per culture
    private static final String[] EAST_A = {"Ka", "Shi", "Mo", "Hi", "Ku", "Na", "Ta", "Yo", "Ri", "Sa", "Mi", "To", "Ha", "Ki", "Fu", "Ya", "Ao", "Su", "Ko", "Ne", "Chi", "Se", "U", "E", "O", "I", "Ma", "Ra"};
    private static final String[] EAST_B = {"ra", "ki", "ne", "mo", "ri", "ka", "ta", "shi", "no", "ru", "sa", "mi", "do", "ya", "ko", "ne", "zu", "ho", "e", "o"};
    private static final String[] EAST_END = {"", "", "", "ko", "ro", "ta", "suke", "ya", "mi", "e", "no", "ji", "ka"};
    private static final String[] EAST_FAM_A = {"Kiri", "Shio", "Ishi", "Taka", "Mizu", "Kaze", "Hoshi", "Kuro", "Shiro", "Ume", "Matsu", "Hama", "Yuki", "Kawa", "Tsuchi", "Ami", "Funa", "Ura", "Oki", "Nagi"};
    private static final String[] EAST_FAM_B = {"yama", "kawa", "mura", "hara", "zaki", "moto", "gaki", "tani", "hashi", "sawa", "no", "shima", "mori", "ura", "gawa", "ishi"};
    private static final String[] EAST_WEST = {"Bram", "Odile", "Jory", "Maelle", "Tobin", "Wynn", "Ottilie", "Cass", "Dunstan", "Ilse", "Marek", "Rosel", "Tebbe", "Hesper"};

    private static final String[] NORTH_A = {"Ing", "Hal", "Sig", "Bre", "Tor", "Yl", "Ast", "Gun", "Rag", "Ei", "Sol", "Kja", "Ul", "Hed", "Vig", "Run", "Fro", "Sten", "Alf", "Brun", "Dag", "Gry", "Lyk", "Ost"};
    private static final String[] NORTH_B = {"var", "dis", "rid", "vor", "ja", "a", "ulf", "hild", "mund", "ny", "stein", "rik", "gard", "ve", "laug", "ny", "borg", "ke", "mar", "in"};
    private static final String[] NORTH_FAM_A = {"Frost", "Hall", "Yst", "Kald", "Grim", "Skarp", "Ness", "Ravn", "Vinter", "Ask", "Bjel", "Strom", "Ulv", "Myr", "Torv", "Hvid"};
    private static final String[] NORTH_FAM_B = {"vik", "berg", "strand", "mark", "holm", "dahl", "gren", "lund", "fjell", "sund", "by", "nes", "rud", "stad"};

    private static final String[] WEST_A = {"Lu", "Cor", "Al", "Ser", "Ma", "Vit", "Ro", "Gi", "Fa", "Ste", "Ot", "Ber", "Ce", "Da", "Ele", "Fio", "Ila", "Ne", "Pa", "Ti"};
    private static final String[] WEST_B = {"enzo", "ina", "aldo", "etta", "ino", "ella", "ario", "ica", "one", "ia", "ero", "essa", "ano", "ilde", "uccio", "ora"};
    private static final String[] WEST_FAM_A = {"Bell", "Marr", "Castel", "Ross", "Vent", "Fior", "Mont", "Serr", "Lup", "Corv", "Sant", "Bianc", "Torr", "Pall", "Grig", "Ver"};
    private static final String[] WEST_FAM_B = {"andi", "ano", "ucci", "etti", "oni", "ero", "ari", "elli", "ini", "ese", "ardo", "aglia", "ozzi", "esi"};

    private static final String[] SOUTH_A = {"Ma", "Ka", "Lei", "Ta", "Ri", "Pa", "Ko", "Al", "In", "Es", "Te", "Ha", "Lo", "Mo", "Ana", "Ke", "Ola", "Ro", "Va", "Xi"};
    private static final String[] SOUTH_B = {"nuel", "lani", "ita", "oa", "ndro", "ena", "ilo", "aru", "iana", "ito", "kai", "mara", "ueo", "esa", "inho", "oli"};
    private static final String[] SOUTH_FAM_A = {"Cal", "Mar", "Puna", "Sol", "Ara", "Val", "Kai", "Ord", "Lua", "Pele", "Tem", "Ver", "Cor", "Mau", "Rio", "Nalu"};
    private static final String[] SOUTH_FAM_B = {"ez", "era", "lua", "apo", "ado", "anu", "iva", "oza", "ales", "ia", "ona", "ilo", "aca", "ehe"};

    private static final String[] NEW_A = {"Dra", "Kor", "Vex", "Zan", "Gar", "Mor", "Thra", "Bal", "Rak", "Sor", "Ny", "Qua", "Ir", "Ke", "Ul", "Jha", "Oz", "Bre"};
    private static final String[] NEW_B = {"kar", "eth", "ion", "ura", "ax", "isk", "ova", "ane", "uun", "ek", "ira", "os", "ael", "umi", "ard", "yx"};
    private static final String[] NEW_FAM_A = {"Black", "Ash", "Storm", "Iron", "Gale", "Red", "Thorn", "Cinder", "Dusk", "Grave", "Bright", "Salt", "Wolf", "Ember", "Drift", "Hollow"};
    private static final String[] NEW_FAM_B = {"wake", "hold", "mere", "ridge", "fall", "crest", "tide", "gate", "moor", "reach", "spire", "water", "wood", "helm"};

    /** Epithets: two-part ("Saltjaw") and phrase ("the Quiet Tide") forms. Original words only. */
    private static final String[] EPI_A = {"Salt", "Iron", "Rust", "Copper", "Lantern", "Mud", "Gull", "Crab", "Storm", "Ash", "Bone", "Coal", "Tar", "Hook", "Knot", "Brine",
            "Barnacle", "Thunder", "Cinder", "Kelp", "Pearl", "Whale", "Grin", "Silk", "Stone", "Glass", "Frost", "Bell", "Ink", "Ember", "Bilge", "Sail"};
    private static final String[] EPI_B = {"jaw", "wake", "fist", "eye", "beard", "hand", "foot", "tooth", "tongue", "back", "heart", "neck", "brow", "knuckle", "grip", "coat", "skull", "fang"};
    private static final String[] EPI_PHRASE = {"the Quiet Tide", "the Gull", "Two-Knots", "Three-Fingers", "the Lamplighter", "the Undertow", "the Laughing Reef", "the Patient",
            "Seven Bells", "the Barrel", "Low-Water", "the Red Gull", "Half-Hitch", "the Last Lantern", "the Mudlark", "the Weathervane", "Dead Calm", "the Bilge Rat",
            "the Smiling Shark", "the Tin Crown", "Wrong-Way", "the Long Rope", "the Black Squall", "Glasswater", "the Spindrift", "Old Tar"};

    /** Canon names never to be produced (lowercase). Combinations are re-rolled if they hit one. */
    private static final Set<String> CANON = new java.util.HashSet<>(java.util.Arrays.asList("luffy", "zoro", "nami", "usopp", "sanji", "chopper", "robin", "franky", "brook", "jinbe", "ace", "sabo", "shanks",
            "buggy", "kaido", "linlin", "teach", "garp", "sengoku", "smoker", "tashigi", "koby", "helmeppo", "vivi", "kuro", "kaya", "arlong", "nojiko", "bellemere",
            "genzo", "hatchan", "krieg", "gin", "zeff", "patty", "alvida", "morgan", "wapol", "dalton", "kureha", "crocodile", "mihawk", "moria", "kuma", "hancock",
            "law", "kid", "killer", "bonney", "hawkins", "drake", "apoo", "urouge", "bege", "momo", "hiyori", "oden", "kinemon", "yamato", "kiku", "izo", "marco",
            "vista", "jozu", "rayleigh", "shakky", "iva", "inazuma", "hina", "pell", "chaka", "igaram", "kohza", "cobra", "laboon", "crocus", "kuina", "koshiro",
            "rebecca", "kyros", "viola", "pudding", "katakuri", "lola", "absalom", "perona", "hogback", "ryuma", "lucci", "kaku", "kalifa", "blueno", "jabra",
            "spandam", "iceburg", "paulie", "tom", "kokoro", "chimney", "conis", "enel", "wyper", "satori", "shura", "ohm", "aisa", "laki", "raki", "leo", "wicca",
            "jango", "django", "sham", "buchi", "gaimon", "bentham", "daz", "zala", "paula", "makino", "dadan", "dogra", "magra", "kaya", "merry", "yasopp", "lucky",
            "benn", "rockstar", "nico", "olvia", "saul", "clover", "tama", "ussop", "senor", "gol", "roger", "toki", "uta", "carrot", "wanda", "pedro", "pekoms",
            "bepo", "shachi", "penguin", "ikkaku", "cavendish", "bartolomeo", "sai", "boo", "ideo", "hajrudin", "orlumbus", "leo", "mansherry", "sugar", "trebol",
            "diamante", "pica", "vergo", "monet", "caesar", "kin", "kanjuro", "raizo", "ashura", "kawamatsu", "denjiro", "inuarashi", "nekomamushi", "shinobu",
            "tsuru", "kong", "akainu", "kizaru", "aokiji", "kuzan", "borsalino", "sakazuki", "issho", "fujitora", "ryokugyu", "momonga", "onigumo", "doberman",
            "strawberry", "bastille", "vegapunk", "stussy", "lilith", "edison", "pythagoras", "atlas", "york", "shaka", "kalgara", "noland", "mont", "blanc",
            "imu", "saturn", "warcury", "nusjuro", "ju", "peter", "dragon", "koala", "hack", "belo", "betty", "morley", "karasu", "lindbergh", "ivankov", "emporio",
            "magellan", "hannyabal", "sadi", "shiryu", "doc", "vasco", "lafitte", "burgess", "auger", "devon", "catarina", "avalo", "pizarro", "sanjuan", "wolf"));

    // ---------------------------------------------------------------- public generators

    /** A first name of this culture. */
    public static String first(Culture c, RandomSource r) {
        for (int i = 0; i < 8; i++) {
            String n = rawFirst(c, r);
            if (!CANON.contains(n.toLowerCase(Locale.ROOT)) && n.length() >= 3) return n;
        }
        return "Tobin";
    }

    /** A family name of this culture. */
    public static String family(Culture c, RandomSource r) {
        for (int i = 0; i < 8; i++) {
            String n = rawFamily(c, r);
            if (!CANON.contains(n.toLowerCase(Locale.ROOT))) return n;
        }
        return "Kirisame";
    }

    /** An epithet ("Saltjaw", "the Quiet Tide"). */
    public static String epithet(RandomSource r) {
        if (r.nextInt(3) == 0) return EPI_PHRASE[r.nextInt(EPI_PHRASE.length)];
        return EPI_A[r.nextInt(EPI_A.length)] + EPI_B[r.nextInt(EPI_B.length)];
    }

    /** An ordinary person: "First Family". */
    public static String person(Culture c, RandomSource r) {
        return first(c, r) + " " + family(c, r);
    }

    /** A villain or outlaw: "First 'Epithet' Family" or "First the Epithet". */
    public static String outlaw(Culture c, RandomSource r) {
        String f = first(c, r);
        String e = epithet(r);
        if (e.startsWith("the ")) return f + " " + e;
        return f + " \"" + e + "\" " + family(c, r);
    }

    /** A townsperson's short name (first name, sometimes with an epithet for rough sorts). */
    public static String townsfolk(Culture c, RandomSource r, boolean rough) {
        String f = first(c, r);
        return rough ? f + " " + epithet(r) : f;
    }

    private static final String[] SHIP_A = {"Gilded", "Weeping", "Crooked", "Merry", "Silver", "Drowsy", "Scarlet", "Patient", "Lucky", "Grey", "Wandering", "Stubborn",
            "Singing", "Sleeping", "Brass", "Midnight", "Honest", "Restless", "Golden", "Little"};
    private static final String[] SHIP_B = {"Gull", "Anchor", "Mackerel", "Maiden", "Lantern", "Tern", "Oyster", "Petrel", "Compass", "Dolphin", "Herring", "Bell", "Swallow",
            "Kettle", "Albatross", "Comet", "Barrel", "Rose", "Otter", "Wave"};

    /** A ship's name, without the article ("Gilded Gull"). */
    public static String ship(RandomSource r) {
        return SHIP_A[r.nextInt(SHIP_A.length)] + " " + SHIP_B[r.nextInt(SHIP_B.length)];
    }

    /** One word for a crew's flag ("Rustfang", used as "the Rustfang Pirates"). */
    public static String crew(RandomSource r) {
        return EPI_A[r.nextInt(EPI_A.length)] + EPI_B[r.nextInt(EPI_B.length)];
    }

    /** A feared beast's nickname ("Old Barnaclejaw"). */
    public static String beast(RandomSource r) {
        return "Old " + EPI_A[r.nextInt(EPI_A.length)] + EPI_B[r.nextInt(EPI_B.length)];
    }

    // ---------------------------------------------------------------- internals

    private static String rawFirst(Culture c, RandomSource r) {
        return switch (c) {
            case EAST -> r.nextInt(5) == 0 ? EAST_WEST[r.nextInt(EAST_WEST.length)]
                    : cap(pick(EAST_A, r) + pick(EAST_B, r) + pick(EAST_END, r));
            case NORTH -> pick(NORTH_A, r) + pick(NORTH_B, r);
            case WEST -> pick(WEST_A, r) + pick(WEST_B, r);
            case SOUTH -> pick(SOUTH_A, r) + pick(SOUTH_B, r);
            case NEW -> pick(NEW_A, r) + pick(NEW_B, r);
            case GRAND -> rawFirst(Culture.values()[r.nextInt(5)], r);
        };
    }

    private static String rawFamily(Culture c, RandomSource r) {
        return switch (c) {
            case EAST -> pick(EAST_FAM_A, r) + pick(EAST_FAM_B, r);
            case NORTH -> pick(NORTH_FAM_A, r) + pick(NORTH_FAM_B, r);
            case WEST -> pick(WEST_FAM_A, r) + pick(WEST_FAM_B, r);
            case SOUTH -> pick(SOUTH_FAM_A, r) + pick(SOUTH_FAM_B, r);
            case NEW -> pick(NEW_FAM_A, r) + pick(NEW_FAM_B, r);
            case GRAND -> rawFamily(Culture.values()[r.nextInt(5)], r);
        };
    }

    private static String pick(String[] a, RandomSource r) {
        return a[r.nextInt(a.length)];
    }

    private static String cap(String s) {
        if (s.isEmpty()) return s;
        String t = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private PeopleNames() {}
}
