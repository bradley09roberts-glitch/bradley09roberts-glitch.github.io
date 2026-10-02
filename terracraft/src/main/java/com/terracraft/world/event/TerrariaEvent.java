package com.terracraft.world.event;

/**
 * A world event (Blood Moon, Slime Rain; later invasions and moon events).
 *
 * @param night           true for night events (end at dawn), false for day events (end at dusk)
 * @param spawnRate       multiplier on the enemy spawn chance while active
 * @param capMultiplier   multiplier on the per-player enemy cap while active
 * @param killGoal        kills that end the event early (0 = runs until the time of day changes)
 */
public record TerrariaEvent(String id, boolean night, float spawnRate, float capMultiplier, int killGoal) {
    public String startKey() {
        return "event.terracraft." + id + ".start";
    }

    public String endKey() {
        return "event.terracraft." + id + ".end";
    }
}
