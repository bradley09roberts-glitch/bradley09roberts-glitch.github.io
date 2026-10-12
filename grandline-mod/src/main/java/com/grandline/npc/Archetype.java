package com.grandline.npc;

import com.grandline.player.Faction;
import com.grandline.player.Race;

import java.util.List;

/**
 * Template for an NPC role: faction, base tier, equipment, style, skins and behaviour flags.
 * {@code hostility}: PASSIVE (never starts fights), DEFENSIVE (fights back, protects allies), LAWFUL (attacks
 * wanted criminals), AGGRESSIVE (attacks anyone not allied).
 */
public record Archetype(String id, Faction faction, int baseTier, List<String> weapons, String style, List<String> skins,
                        Hostility hostility, boolean killsDowned, double fruitChance, double hakiChance, float health,
                        int armor, Race race, boolean merchant, boolean coward) {
    public enum Hostility { PASSIVE, DEFENSIVE, LAWFUL, AGGRESSIVE }
}
