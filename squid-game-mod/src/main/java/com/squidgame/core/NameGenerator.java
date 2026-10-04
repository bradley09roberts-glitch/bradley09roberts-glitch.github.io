package com.squidgame.core;

import com.squidgame.core.util.Rng;

import java.util.HashSet;
import java.util.Set;

/** Generates plausible, entirely fictional contestant names (romanised Korean style). */
public final class NameGenerator {
    private static final String[] SURNAMES = {
            "Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon", "Jang", "Lim", "Han", "Oh",
            "Seo", "Shin", "Kwon", "Hwang", "Ahn", "Song", "Yoo", "Hong", "Jeon", "Ko", "Moon",
            "Yang", "Son", "Bae", "Baek", "Heo", "Nam", "Shim"
    };
    private static final String[] GIVEN = {
            "Min-jun", "Seo-yeon", "Ji-ho", "Ha-eun", "Do-yun", "Soo-ah", "Hyun-woo", "Eun-ji",
            "Tae-yang", "Yu-na", "Joon-ho", "Mi-rae", "Sung-min", "Da-eun", "Kang-dae", "Ye-jin",
            "Woo-jin", "Hye-won", "Sang-hoon", "Na-ri", "Jae-min", "Bo-ra", "Dong-hyun", "Sun-hee",
            "Gi-tae", "Mi-na", "Chul-soo", "Young-ja", "Ho-seok", "Ji-woo", "Byung-gi", "Hana",
            "Tae-ho", "Soo-jin", "Min-seo", "Jun-seo", "Ara", "Dae-ho", "Yeon-woo", "Seung-ri"
    };

    private NameGenerator() {
    }

    public static String generate(Rng rng) {
        return SURNAMES[rng.nextInt(SURNAMES.length)] + " " + GIVEN[rng.nextInt(GIVEN.length)];
    }

    /** Generates {@code count} distinct names. */
    public static java.util.List<String> generateUnique(Rng rng, int count) {
        Set<String> seen = new HashSet<>();
        java.util.ArrayList<String> out = new java.util.ArrayList<>(count);
        int guard = 0;
        while (out.size() < count) {
            String n = generate(rng);
            if (seen.add(n) || guard++ > count * 20) {
                out.add(n);
            }
        }
        return out;
    }
}
