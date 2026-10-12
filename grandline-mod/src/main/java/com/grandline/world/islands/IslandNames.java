package com.grandline.world.islands;

import net.minecraft.util.RandomSource;

/** Original island names in the setting's mixed Japanese/European naming style. */
public final class IslandNames {
    private static final String[] JP_START = {"Ka", "Shi", "Mo", "Hi", "Ku", "Na", "Ta", "Yo", "Ri", "Sa", "Mi", "To", "Ha", "Ki", "Fu", "Ya", "Ao", "Su", "Ko", "Ne", "Ro", "Me", "Tsu", "Ho"};
    private static final String[] JP_MID = {"ra", "ki", "na", "mo", "ze", "ri", "ka", "ta", "shi", "no", "ga", "ru", "sa", "mi", "do", "be", "ya", "ko", "ne", "gu"};
    private static final String[] JP_END = {"jima", "shima", "mori", "hama", "zaki", "oka", "yama", "ura", "gawa", "kawa", "sato", "dake"};
    private static final String[] EU_START = {"Port ", "San ", "Saint ", "Isla ", "Cape ", "Fort ", "Old ", "New ", "", "", "", ""};
    private static final String[] EU_ROOT = {"Vell", "Mar", "Corv", "Bell", "Tarr", "Lum", "Ost", "Gald", "Rosm", "Brin", "Calv", "Dorn", "Ess", "Fenn", "Gris", "Harl", "Iver", "Jarn", "Kest", "Lorr", "Mirr", "Norv", "Orr", "Pell", "Quill", "Rav", "Sorr", "Tamb", "Ulv", "Varr", "Wyn", "Zeph"};
    private static final String[] EU_END = {"ora", "ia", "ence", "ano", "ville", "mouth", "haven", "aro", "esse", "ola", "ington", "arde", "uga", "eria", "osa", "enne"};
    private static final String[] SUFFIX = {" Island", " Island", " Isle", " Kingdom", " Islet", " Atoll", " Reef", " Rock", " Archipelago", " Island"};

    public static String generate(RandomSource r) {
        String core;
        if (r.nextBoolean()) {
            core = JP_START[r.nextInt(JP_START.length)] + (r.nextInt(3) == 0 ? "" : JP_MID[r.nextInt(JP_MID.length)]) + JP_END[r.nextInt(JP_END.length)];
            return core + (r.nextInt(3) == 0 ? " Island" : "");
        }
        String start = EU_START[r.nextInt(EU_START.length)];
        core = EU_ROOT[r.nextInt(EU_ROOT.length)] + EU_END[r.nextInt(EU_END.length)];
        if (!start.isEmpty()) return start + core;
        return core + SUFFIX[r.nextInt(SUFFIX.length)];
    }

    private IslandNames() {}
}
