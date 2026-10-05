package com.squidgame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mod configuration, stored as {@code config/squidgame.json} (created with defaults on first run,
 * edited by hand or at runtime with {@code /squid config <key> <value>}). All durations are in
 * seconds unless the name says ticks.
 */
public final class SquidConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SquidConfig instance = new SquidConfig();

    // ---- population
    /** Total contestants (humans + NPCs). NPC count = total - humans unless {@link #npcCount} >= 0. */
    public int totalContestants = 100;
    /** If >= 0, the exact number of NPC contestants regardless of the player count. */
    public int npcCount = -1;
    /** Hard cap on contestants (arenas are sized for 128). */
    public int maxContestants = 128;
    /** normal | hard | extreme */
    public String defaultDifficulty = "normal";

    // ---- phase durations (seconds)
    public int registrationSeconds = 30;
    public int instructionSeconds = 22;
    public int countdownSeconds = 5;
    public int resultsSeconds = 14;
    public int transitionSeconds = 30;
    public int winnerSeconds = 25;
    public int restartDelaySeconds = 15;
    /** Speeds up (<1) or slows down (>1) every phase and game timer. Handy for testing (0.2 = 5x faster). */
    public double timeScale = 1.0;

    // ---- flow
    /** Start a fresh registration automatically after a tournament ends (servers). */
    public boolean autoRestart = false;
    /** Players joining mid-tournament become spectators; if false they are sent home. */
    public boolean lateJoinSpectate = true;
    /** Seconds an absent contestant's auto-pilot stand-in keeps playing before the contestant is eliminated. */
    public int disconnectGraceSeconds = 90;
    /** After a server restart: resume the tournament at the start of the current game (true) or reset it (false). */
    public boolean resumeOnRestart = true;
    /** Eliminated humans keep spectating until the tournament ends (false = sent home right after results). */
    public boolean spectateAfterElimination = true;

    // ---- building
    public int buildBlocksPerTick = 8000;
    public int buildMillisPerTick = 14;
    /** Build the hub automatically the first time someone enters. */
    public boolean autoBuildOnEnter = true;

    // ---- rules
    /** Restrict participants (adventure mode, no flying/teleporting/building) inside the arena. */
    public boolean enforceRules = true;
    public boolean allowOpsToBypassRules = true;

    // ---- games
    /** mixed | odd_even | throw (target_throw is accepted too); anything else means mixed */
    public String marblesVariant = "mixed";

    // ---- performance
    /** NPCs further than this many blocks from every human run their AI at a reduced rate. */
    public int npcFarDistance = 80;
    public int npcFarTickInterval = 3;

    // ---- debugging
    public boolean debug = false;

    public static SquidConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("squidgame.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                SquidConfig c = GSON.fromJson(r, SquidConfig.class);
                if (c != null) {
                    instance = c;
                }
            } catch (IOException | RuntimeException e) {
                SquidGameMod.LOGGER.error("Could not read {}, using defaults: {}", p, e.getMessage());
            }
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            SquidGameMod.LOGGER.error("Could not write {}: {}", p, e.getMessage());
        }
    }

    private void sanitize() {
        maxContestants = Math.max(2, Math.min(456, maxContestants));
        totalContestants = Math.max(2, Math.min(maxContestants, totalContestants));
        timeScale = Math.max(0.02, Math.min(10.0, timeScale));
        buildBlocksPerTick = Math.max(500, buildBlocksPerTick);
        buildMillisPerTick = Math.max(2, Math.min(40, buildMillisPerTick));
        npcFarTickInterval = Math.max(1, Math.min(10, npcFarTickInterval));
    }

    /** Scales a duration in seconds to ticks using {@link #timeScale}. */
    public int ticks(double seconds) {
        return (int) Math.max(1, Math.round(seconds * 20.0 * timeScale));
    }

    // ------------------------------------------------------------ runtime editing

    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Field f : SquidConfig.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                out.add(f.getName());
            }
        }
        return out;
    }

    public String getValue(String key) {
        try {
            Field f = SquidConfig.class.getDeclaredField(key);
            return String.valueOf(f.get(this));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Sets a primitive/String option by name; returns an error message or null on success. */
    public String setValue(String key, String value) {
        try {
            Field f = SquidConfig.class.getDeclaredField(key);
            if (Modifier.isStatic(f.getModifiers())) {
                return "not an option";
            }
            Class<?> t = f.getType();
            if (t == int.class) {
                f.setInt(this, Integer.parseInt(value));
            } else if (t == double.class) {
                f.setDouble(this, Double.parseDouble(value));
            } else if (t == boolean.class) {
                f.setBoolean(this, Boolean.parseBoolean(value));
            } else if (t == String.class) {
                f.set(this, value);
            } else {
                return "unsupported type";
            }
            sanitize();
            save();
            return null;
        } catch (NoSuchFieldException e) {
            return "unknown option '" + key + "'";
        } catch (NumberFormatException e) {
            return "invalid number '" + value + "'";
        } catch (ReflectiveOperationException e) {
            return e.getMessage();
        }
    }
}
