package com.terracraft.world.event;

/**
 * A world event (Blood Moon, Slime Rain; later invasions and moon events).
 *
 * @param night           true for night events (end at dawn), false for day events (end at dusk)
 * @param spawnRate       multiplier on the enemy spawn chance while active
 * @param capMultiplier   multiplier on the per-player enemy cap while active
 * @param killGoal        kills that end the event early (0 = runs until the time of day changes)
 * @param invasion        invasions ignore the time of day, end only at their kill goal, replace the normal surface
 *                        spawns and show a progress bar
 * @param memberPrefix    entity id prefix of the creatures that count toward an invasion's kill goal
 * @param waves           wave events (Pumpkin Moon, Frost Moon): number of waves; kills earn points that advance the
 *                        wave, and the event still ends at dawn ({@link MoonEvents} spawns their enemies)
 */
public record TerrariaEvent(String id, boolean night, float spawnRate, float capMultiplier, int killGoal, boolean invasion, String memberPrefix,
                            int waves) {
    public TerrariaEvent(String id, boolean night, float spawnRate, float capMultiplier, int killGoal) {
        this(id, night, spawnRate, capMultiplier, killGoal, false, "", 0);
    }

    public TerrariaEvent(String id, boolean night, float spawnRate, float capMultiplier, int killGoal, boolean invasion, String memberPrefix) {
        this(id, night, spawnRate, capMultiplier, killGoal, invasion, memberPrefix, 0);
    }

    /** Whether a creature (by entity id path) belongs to this invasion; {@code memberPrefix} may list several prefixes with '|'. */
    public boolean isMember(String path) {
        for (String prefix : memberPrefix.split("\\|")) {
            if (!prefix.isEmpty() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public String startKey() {
        return "event.terracraft." + id + ".start";
    }

    public String endKey() {
        return "event.terracraft." + id + ".end";
    }
}
