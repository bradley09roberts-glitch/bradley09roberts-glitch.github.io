package com.terracraft.progression;

import com.terracraft.TerraCraft;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The catalogue of well-known progression flags.
 * <p>
 * Naming follows the project brief ({@code boss_eye_defeated}, {@code hardmode_active}...). Any other
 * identifier is still a valid flag (category CUSTOM) so content packs can add their own milestones.
 */
public final class ProgressionFlags {
    private static final Map<Identifier, ProgressionFlag> FLAGS = new LinkedHashMap<>();

    // ---------------------------------------------------------------- pre-hardmode bosses
    public static final ProgressionFlag KING_SLIME = boss("boss_king_slime_defeated");
    public static final ProgressionFlag EYE_OF_CTHULHU = boss("boss_eye_defeated");
    public static final ProgressionFlag EATER_OF_WORLDS = boss("boss_eater_of_worlds_defeated");
    public static final ProgressionFlag BRAIN_OF_CTHULHU = boss("boss_brain_of_cthulhu_defeated");
    /** Set whenever either world-evil boss dies (the generic "evil boss" milestone). */
    public static final ProgressionFlag EVIL_BOSS = boss("boss_evil_defeated");
    public static final ProgressionFlag QUEEN_BEE = boss("boss_queen_bee_defeated");
    public static final ProgressionFlag DEERCLOPS = boss("boss_deerclops_defeated");
    public static final ProgressionFlag SKELETRON = boss("boss_skeletron_defeated");
    public static final ProgressionFlag WALL_OF_FLESH = boss("boss_wall_of_flesh_defeated");

    // ---------------------------------------------------------------- world state
    public static final ProgressionFlag HARDMODE = world("hardmode_active");
    public static final ProgressionFlag METEOR_LANDED = world("meteor_landed");
    public static final ProgressionFlag ORB_SMASHED = world("shadow_orb_smashed");
    public static final ProgressionFlag ALTAR_SMASHED = world("altar_smashed");
    public static final ProgressionFlag CELESTIAL_EVENTS = world("celestial_events_active");

    // ---------------------------------------------------------------- hardmode bosses
    public static final ProgressionFlag QUEEN_SLIME = boss("boss_queen_slime_defeated");
    public static final ProgressionFlag DESTROYER = boss("boss_destroyer_defeated");
    public static final ProgressionFlag TWINS = boss("boss_twins_defeated");
    public static final ProgressionFlag SKELETRON_PRIME = boss("boss_skeletron_prime_defeated");
    public static final ProgressionFlag PLANTERA = boss("boss_plantera_defeated");
    public static final ProgressionFlag GOLEM = boss("boss_golem_defeated");
    public static final ProgressionFlag DUKE_FISHRON = boss("boss_duke_fishron_defeated");
    public static final ProgressionFlag EMPRESS_OF_LIGHT = boss("boss_empress_of_light_defeated");
    public static final ProgressionFlag LUNATIC_CULTIST = boss("boss_cultist_defeated");
    public static final ProgressionFlag PILLAR_SOLAR = boss("pillar_solar_defeated");
    public static final ProgressionFlag PILLAR_VORTEX = boss("pillar_vortex_defeated");
    public static final ProgressionFlag PILLAR_NEBULA = boss("pillar_nebula_defeated");
    public static final ProgressionFlag PILLAR_STARDUST = boss("pillar_stardust_defeated");
    public static final ProgressionFlag MOON_LORD = boss("moon_lord_defeated");

    // ---------------------------------------------------------------- events
    public static final ProgressionFlag GOBLIN_ARMY = event("event_goblin_army_defeated");
    public static final ProgressionFlag FROST_LEGION = event("event_frost_legion_defeated");
    public static final ProgressionFlag PIRATES = event("event_pirate_invasion_defeated");
    public static final ProgressionFlag MARTIANS = event("event_martian_madness_defeated");
    public static final ProgressionFlag PUMPKIN_MOON = event("event_pumpkin_moon_cleared");
    public static final ProgressionFlag FROST_MOON = event("event_frost_moon_cleared");
    public static final ProgressionFlag SOLAR_ECLIPSE = event("event_solar_eclipse_seen");
    public static final ProgressionFlag BLOOD_MOON = event("event_blood_moon_seen");

    // ---------------------------------------------------------------- NPC unlocks
    public static final ProgressionFlag GOBLIN_TINKERER_RESCUED = npc("npc_goblin_tinkerer_rescued");
    public static final ProgressionFlag MECHANIC_RESCUED = npc("npc_mechanic_rescued");
    public static final ProgressionFlag WIZARD_RESCUED = npc("npc_wizard_rescued");
    public static final ProgressionFlag STYLIST_RESCUED = npc("npc_stylist_rescued");
    public static final ProgressionFlag ANGLER_RESCUED = npc("npc_angler_rescued");
    public static final ProgressionFlag TAX_COLLECTOR_RESCUED = npc("npc_tax_collector_rescued");

    // ---------------------------------------------------------------- derived flags
    public static final ProgressionFlag ANY_MECH_BOSS = derived("any_mech_boss_defeated",
        p -> p.has(DESTROYER) || p.has(TWINS) || p.has(SKELETRON_PRIME));
    public static final ProgressionFlag MECH_BOSSES = derived("mech_bosses_defeated",
        p -> p.has(DESTROYER) && p.has(TWINS) && p.has(SKELETRON_PRIME));
    public static final ProgressionFlag ALL_PILLARS = derived("celestial_pillars_defeated",
        p -> p.has(PILLAR_SOLAR) && p.has(PILLAR_VORTEX) && p.has(PILLAR_NEBULA) && p.has(PILLAR_STARDUST));
    public static final ProgressionFlag PRE_HARDMODE = derived("pre_hardmode",
        p -> !p.has(HARDMODE));

    private ProgressionFlags() {}

    private static ProgressionFlag boss(String path) {
        return register(path, ProgressionFlag.Category.BOSS, null);
    }

    private static ProgressionFlag world(String path) {
        return register(path, ProgressionFlag.Category.WORLD, null);
    }

    private static ProgressionFlag event(String path) {
        return register(path, ProgressionFlag.Category.EVENT, null);
    }

    private static ProgressionFlag npc(String path) {
        return register(path, ProgressionFlag.Category.NPC, null);
    }

    private static ProgressionFlag derived(String path, Predicate<ProgressionView> derivation) {
        return register(path, ProgressionFlag.Category.DERIVED, derivation);
    }

    private static ProgressionFlag register(String path, ProgressionFlag.Category category, Predicate<ProgressionView> derivation) {
        ProgressionFlag flag = new ProgressionFlag(TerraCraft.id(path), category, derivation);
        if (FLAGS.put(flag.id(), flag) != null) {
            throw new IllegalStateException("Duplicate progression flag " + flag.id());
        }
        return flag;
    }

    /** Every well-known flag, in declaration order. */
    public static Collection<ProgressionFlag> all() {
        return Collections.unmodifiableCollection(FLAGS.values());
    }

    public static Optional<ProgressionFlag> byId(Identifier id) {
        return Optional.ofNullable(FLAGS.get(id));
    }

    /**
     * Resolves a flag from an identifier, accepting short names ({@code hardmode_active}) and creating a
     * CUSTOM flag for unknown identifiers.
     */
    public static ProgressionFlag resolve(Identifier id) {
        ProgressionFlag known = FLAGS.get(id);
        if (known != null) {
            return known;
        }
        return new ProgressionFlag(id, ProgressionFlag.Category.CUSTOM, null);
    }

    /** Short aliases accepted by commands and data files. */
    private static final Map<String, String> ALIASES = Map.of(
        "hardmode", "hardmode_active",
        "eye_of_cthulhu", "boss_eye_defeated",
        "king_slime", "boss_king_slime_defeated",
        "skeletron", "boss_skeletron_defeated",
        "wall_of_flesh", "boss_wall_of_flesh_defeated",
        "plantera", "boss_plantera_defeated",
        "golem", "boss_golem_defeated",
        "moon_lord", "moon_lord_defeated"
    );

    /** Parses "hardmode_active", "hardmode" (alias) or "terracraft:hardmode_active" style strings. */
    public static ProgressionFlag resolve(String text) {
        String trimmed = text.trim();
        Identifier id = trimmed.contains(":") ? Identifier.parse(trimmed) : TerraCraft.id(ALIASES.getOrDefault(trimmed, trimmed));
        return resolve(id);
    }
}
