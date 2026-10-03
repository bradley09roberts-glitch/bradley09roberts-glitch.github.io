package com.terracraft.item.accessory;

import com.terracraft.player.stats.StatEffects;

/**
 * Wings: an accessory that lets the player fly. Hold jump in the air to rise for {@link Flight#flightTicks}
 * ticks (refilled on landing), then keep holding it to glide down slowly. Only the best pair worn counts.
 * The look on the player is the 3D wing model with {@code textures/entity/wings/<style>.png}.
 */
public class WingsItem extends AccessoryItem {
    /** @param ascent maximum upward speed in blocks per tick */
    public record Flight(String style, int flightTicks, float ascent) {}

    private final Flight flight;

    public WingsItem(Properties properties, StatEffects effects, Flight flight) {
        super(properties, effects);
        this.flight = flight;
    }

    public Flight flight() {
        return flight;
    }
}
