package com.grandline.npc;

import com.grandline.quest.story.PeopleNames;
import com.grandline.world.SeaZone;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/** Original NPC names in the setting's style, flavoured by the sea the NPC lives in (see {@link PeopleNames}). */
public final class NpcNames {
    public static Component nameFor(Archetype a, RandomSource r) {
        return nameFor(a, r, null);
    }

    /** A townsperson's name; rough sorts (pirates, bandits, strong fighters) also get an epithet. */
    public static Component nameFor(Archetype a, RandomSource r, @Nullable SeaZone zone) {
        boolean rough = a.baseTier() >= 4 || a.id().contains("pirate") || a.id().contains("bandit");
        PeopleNames.Culture c = zone == null ? PeopleNames.Culture.GRAND : PeopleNames.culture(zone);
        return Component.literal(PeopleNames.townsfolk(c, r, rough));
    }

    private NpcNames() {}
}
