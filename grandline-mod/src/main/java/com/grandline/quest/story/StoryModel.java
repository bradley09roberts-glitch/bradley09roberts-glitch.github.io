package com.grandline.quest.story;

import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Plain data produced by story and job generation (nothing here is saved: the seed regenerates it). */
public final class StoryModel {
    /** A generated person: becomes a notable (spawned once per world near its home, permadeath applies). */
    public record Person(String id, String slot, String name, Component title, String role, int tier, Faction faction,
                         NotableCharacters.Disposition disposition, String style, String weapon, String race, String island, int x, int z,
                         boolean conditional, NotableCharacters.Group group) {}

    /** What happens at a site while a quest is active. */
    public enum SiteKind {
        /** The player must come here ("reach" objective). */
        REACH,
        /** Humanoids of a role are fielded here (for a "kill" objective or as guards). */
        ROLE,
        /** Island wildlife of a kind (for a "slay" objective). */
        BEAST,
        /** A Sea King in open water (for a "slay sea_king" objective). */
        SEA_KING
    }

    /**
     * A place a quest needs: where to go, or where its foes appear. {@code key} is unique world-wide (it is also the
     * target of "reach" objectives). {@code what} is the role or creature kind; {@code name} names a beast, or null.
     */
    public record Site(String key, String island, int x, int z, int y, SiteKind kind, String what, int tier, @Nullable Component name, Component label) {}

    /** Binding of a quest objective (or -1: guards for the whole quest) to a site. */
    public record SiteRef(int objective, String site, int count) {}

    /** One island's story: its template, people, quests (with texts) and places. */
    public record IslandStory(String island, StoryTemplate template, List<Person> people, List<QuestDef> quests,
                              Map<String, Component> titles, Map<String, Component> descs, Map<String, List<SiteRef>> sites,
                              List<Site> places, Object[] args, String giver, String lastStep) {}

    /** A world's generated jobs and stories, ready to install. */
    public record Generated(List<IslandStory> stories, List<QuestDef> quests, List<Person> people, Map<String, Component> titles,
                            Map<String, Component> descs, Map<String, List<SiteRef>> questSites, List<Site> sites,
                            Map<String, JobBoard.StartGift> gifts) {}

    private StoryModel() {}
}
