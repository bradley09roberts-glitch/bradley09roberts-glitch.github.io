package com.squidgame.build;

/**
 * An entity to be spawned when the structure is placed (signage text displays, decorative item
 * displays...). {@code snbt} is the entity's NBT in SNBT form <b>without</b> the {@code id} (it is
 * added from {@code type}); the placer also adds the {@code squidgame_static} scoreboard tag so
 * the structure's entities can be removed cleanly.
 */
public record EntitySpec(String type, double x, double y, double z, float yaw, float pitch, String snbt) {
}
