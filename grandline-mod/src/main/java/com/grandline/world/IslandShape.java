package com.grandline.world;

/** Terrain profile families. Each island combines one shape with a theme and noise. */
public enum IslandShape {
    HILLS, MOUNTAIN, PLATEAU, FLAT, SPIRES, CRATER, RING, TERRACES, DUNES, DRUM, ARCHIPELAGO, CLIFF_WALL, GIANT_TREE, ATOLL,
    /** No land: seabed only, for set pieces built on the water (e.g. a sea restaurant). */
    SEA,
    /** A perfectly round, sea-filled blast crater ringed by sheer cliffs (a kingdom wiped off the map). */
    BLAST
}
