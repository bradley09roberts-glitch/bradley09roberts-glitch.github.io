package com.grandline.quest.arc;

import com.grandline.GrandLine;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.quest.QuestGuide;
import com.grandline.quest.story.StoryModel.Site;
import com.grandline.quest.story.StoryModel.SiteKind;
import com.grandline.quest.story.StoryModel.SiteRef;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The places of the saga's chapters, for the same machinery the generated stories use
 * ({@link com.grandline.quest.story.StorySites}): a step's objective can be bound to a place on its island, which then
 * gives the journal and the minimap a precise marker, and, for a kill objective, has the foes it needs turn up there
 * while the player is near (so a step never waits on random encounters: the levy collectors are at the harbour, the
 * hired jailers at the cell block). Places are laid out from the island itself (its wilds, its pier, its square, a
 * notable's home) when the world's stories are installed. Labels: {@code arc.grandline.site.<name>}.
 */
public final class ArcSites {
    record Decl(String quest, String island, int objective, String name, String anchor, @Nullable String role, int count) {}

    private static final List<Decl> DECLS = new ArrayList<>();

    private ArcSites() {}

    static void declare(String quest, String island, int objective, String name, String anchor, @Nullable String role) {
        DECLS.add(new Decl(quest, island, objective, name, anchor, role, 0));
    }

    /** Every declared place name (self-test: each has a label). */
    public static Set<String> names() {
        Set<String> s = new LinkedHashSet<>();
        for (Decl d : DECLS) s.add(d.name());
        return s;
    }

    /** Lay the places out on this world's islands and bind them to their steps. */
    public static void install(WorldPlan plan, Map<String, List<SiteRef>> questSites, Map<String, Site> sites) {
        int made = 0;
        for (Decl d : DECLS) {
            Island isl = plan.byId(d.island());
            if (isl == null) continue;
            String key = "arc." + d.name();
            Site site = sites.get(key);
            if (site == null) {
                int[] at = anchor(isl, d.anchor());
                if (at == null) {
                    GrandLine.LOGGER.warn("Story place {} has no anchor {} on {}", d.name(), d.anchor(), d.island());
                    continue;
                }
                site = new Site(key, isl.id, at[0], at[1], 0, d.role() == null ? SiteKind.REACH : SiteKind.ROLE, d.role() == null ? "" : d.role(),
                        Math.max(1, Math.min(10, isl.tier)), null, Component.translatable("arc.grandline.site." + d.name()));
                sites.put(key, site);
                made++;
            }
            List<SiteRef> refs = new ArrayList<>(questSites.getOrDefault(d.quest(), List.of()));
            refs.add(new SiteRef(d.objective(), key, d.count()));
            questSites.put(d.quest(), List.copyOf(refs));
        }
        GrandLine.LOGGER.debug("Story chapters: {} places laid out", made);
    }

    /** A spot on an island: "wilds", "dock", "center", "home:<notable>[,dx,dz]", "at:dx,dz" (from the centre). */
    @Nullable
    static int[] anchor(Island isl, String anchor) {
        Settlement s = isl.settlement();
        if (anchor.equals("wilds")) return QuestGuide.wilds(isl);
        if (anchor.equals("dock")) return s.hasDock ? new int[]{s.dockX, s.dockZ} : s.hasCenter ? new int[]{s.centerX, s.centerZ} : new int[]{isl.x, isl.z};
        if (anchor.equals("center")) return s.hasCenter ? new int[]{s.centerX, s.centerZ} : new int[]{isl.x, isl.z};
        if (anchor.startsWith("home:")) {
            String[] p = anchor.substring(5).split(",");
            var n = NotableCharacters.get(p[0]);
            if (n == null) return null;
            int ox = p.length > 2 ? Integer.parseInt(p[1].trim()) : 0, oz = p.length > 2 ? Integer.parseInt(p[2].trim()) : 0;
            return new int[]{isl.x + n.dx() + ox, isl.z + n.dz() + oz};
        }
        if (anchor.startsWith("at:")) {
            String[] p = anchor.substring(3).split(",");
            return new int[]{isl.x + Integer.parseInt(p[0].trim()), isl.z + Integer.parseInt(p[1].trim())};
        }
        return null;
    }
}
