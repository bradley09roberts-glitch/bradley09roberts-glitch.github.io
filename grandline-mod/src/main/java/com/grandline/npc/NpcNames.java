package com.grandline.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/** Original NPC names in the setting's style. */
public final class NpcNames {
    private static final String[] FIRST = {"Tobio", "Kazu", "Rinne", "Maro", "Sora", "Haku", "Benji", "Lotte", "Gren", "Ines", "Pell", "Oro", "Nami-no", "Tetsu", "Yuzu",
            "Bram", "Cosette", "Dax", "Elio", "Fumi", "Gaspard", "Hana", "Iggy", "Jiro", "Kip", "Luca", "Mika", "Noa", "Otto", "Pia", "Quint", "Rook", "Suki", "Tamsin",
            "Umi", "Vito", "Wren", "Yori", "Zeno", "Akane", "Bastian", "Chiyo", "Dorian", "Emi", "Fennick", "Goro-san", "Hollis", "Isao", "Juno", "Kenta"};
    private static final String[] EPITHET = {"the Gull", "Saltjaw", "the Quick", "Barrelback", "Two-Knots", "the Driftwood", "Ironbrow", "Whistler", "the Tide",
            "Red-Scarf", "Anchorhand", "the Lantern", "Seafoam", "the Bold", "Crowbeak", "Stormcoat", "the Patient", "Stonefist", "Half-Moon", "Kelp"};

    public static Component nameFor(Archetype a, RandomSource r) {
        String n = FIRST[r.nextInt(FIRST.length)];
        if (a.baseTier() >= 4 || a.id().contains("pirate") || a.id().contains("bandit")) n = n + " " + EPITHET[r.nextInt(EPITHET.length)];
        return Component.literal(n);
    }

    private NpcNames() {}
}
