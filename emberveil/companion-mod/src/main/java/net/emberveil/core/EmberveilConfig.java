package net.emberveil.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EmberveilConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GIVE_ALMANAC_ON_FIRST_JOIN;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_ALMANAC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("almanac");
        GIVE_ALMANAC_ON_FIRST_JOIN = b
                .comment("Give each player one Wayfarer's Almanac the first time they join a world.",
                        "Tracked per player in save data, so it is never repeated after death, relog or dimension change.")
                .define("give_on_first_join", true);
        ANNOUNCE_ALMANAC = b
                .comment("Show a one-time chat hint explaining how to open the Almanac when it is given.")
                .define("announce_on_first_join", true);
        b.pop();
        SPEC = b.build();
    }

    private EmberveilConfig() {
    }
}
