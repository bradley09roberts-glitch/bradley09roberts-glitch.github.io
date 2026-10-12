package com.grandline.quest.story;

import com.grandline.GrandLine;
import com.grandline.npc.notable.NotableCharacters.Disposition;
import com.grandline.npc.notable.NotableCharacters.Group;
import com.grandline.player.Faction;
import com.grandline.player.Race;
import com.grandline.quest.QuestDef;
import com.grandline.quest.story.StoryContext.Spot;
import com.grandline.quest.story.StoryModel.*;
import com.grandline.registry.ModDataComponents;
import com.grandline.registry.ModItems;
import com.grandline.world.IslandTheme;
import com.grandline.world.SeaZone;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Supplier;

/**
 * Assembles one island story: people (named, placed), places (sites), and 2–4 steps with faction variants, then turns
 * them into quests. Text keys: {@code story.grandline.<template>.<step>} (title), {@code ...<step>.desc} (or
 * {@code .desc.<faction>} for a variant with its own text), {@code story.grandline.<template>.greet.<slot>},
 * {@code .thanks.<slot>} and {@code .rumor}. Every text of a story shares one argument list: %1$s is the island, then
 * each person's name in the order they were added, then the extra arguments in the order they were added.
 */
final class StoryBuilder {
    /** Rewards per island tier (1..10) for a step of weight 1. */
    static final int[] XP = {0, 400, 700, 2200, 3200, 4500, 15000, 20000, 26000, 32000, 40000};
    static final long[] BELI = {0, 500, 900, 3000, 4500, 6000, 18000, 24000, 30000, 38000, 48000};

    final StoryContext c;
    final StoryTemplate t;
    final List<Object> args = new ArrayList<>();
    final List<Person> people = new ArrayList<>();
    final Map<String, Site> sites = new LinkedHashMap<>();
    final List<Step> steps = new ArrayList<>();
    Person giver;

    StoryBuilder(StoryContext c, StoryTemplate t) {
        this.c = c;
        this.t = t;
        args.add(c.islandName());
    }

    // ------------------------------------------------------------------ people

    String name() {
        return PeopleNames.person(c.culture, c.r);
    }

    String outlawName() {
        return PeopleNames.outlaw(c.culture, c.r);
    }

    /** A friendly islander who hands out the story. */
    Person giver(String slot, String role, Spot at) {
        giver = person(slot, role, Faction.CIVILIAN, Disposition.FRIENDLY, 2, at, name(), false);
        return giver;
    }

    /** A giver of another allegiance (a Marine commander, a Revolutionary) or with a chosen name. */
    Person giver(String slot, String role, Faction f, int tier, Spot at, String name) {
        giver = person(slot, role, f, Disposition.FRIENDLY, tier, at, name, false);
        return giver;
    }

    Person friend(String slot, String role, Spot at) {
        return person(slot, role, Faction.CIVILIAN, Disposition.FRIENDLY, 1, at, name(), false);
    }

    /** A hostile boss: tier above the island's, armed by style. */
    Person villain(String slot, String role, Faction f, int tierBonus, Spot at, boolean conditional) {
        int tier = c.tier() + tierBonus;
        // outlaws carry a bounty of their own, shown under their name
        Component title = f == Faction.PIRATE || f == Faction.WILD
                ? Component.translatable("story.grandline.who." + slot, c.islandName(), String.format("%,d", villainBounty(tier))) : null;
        return person(slot, role, f, Disposition.VILLAIN, tier, at, outlawName(), conditional, title);
    }

    /** The bounty on a story villain of this tier: what bringing them in pays (see NpcOutcomes#rewardFor). */
    static long villainBounty(int tier) {
        return (long) (Math.pow(Math.max(1, Math.min(12, tier)), 2.3) * 400);
    }

    Person person(String slot, String role, Faction f, Disposition d, int tier, Spot at, String name, boolean conditional) {
        return person(slot, role, f, d, tier, at, name, conditional, null);
    }

    Person person(String slot, String role, Faction f, Disposition d, int tier, Spot at, String name, boolean conditional, Component title) {
        String id = c.prefix + c.isl.id + "@" + slot;
        boolean fighter = d == Disposition.VILLAIN || d == Disposition.RIVAL || d == Disposition.LAWFUL;
        String[] kit = fighter ? kit(c.r, role) : new String[]{"brawler", ""};
        Group g = d == Disposition.VILLAIN ? Group.VILLAIN : Group.PATRON;
        Person p = new Person(id, slot, name, title != null ? title : Component.translatable("story.grandline.who." + slot, c.islandName()), role, Math.max(1, Math.min(12, tier)), f, d,
                kit[0], kit[1], race(c, role), c.isl.id, at.x(), at.z(), conditional, g);
        people.add(p);
        args.add(name);
        return p;
    }

    /** A person living on another island (a herbalist, a contact): placed in that island's town or on its beach. */
    Person elsewhere(String slot, String role, StoryContext other, Spot at) {
        String id = c.prefix + c.isl.id + "@" + slot;
        Person p = new Person(id, slot, PeopleNames.person(other.culture, c.r), Component.translatable("story.grandline.who." + slot, other.islandName()), role, 2,
                Faction.CIVILIAN, Disposition.FRIENDLY, "brawler", "", race(other, role), other.isl.id, at.x(), at.z(), false, Group.PATRON);
        people.add(p);
        args.add(p.name());
        return p;
    }

    void arg(Object o) {
        args.add(o);
    }

    /** Fighting style and weapon for a story fighter of this role. */
    static String[] kit(net.minecraft.util.RandomSource r, String role) {
        if (role.startsWith("marine")) return r.nextBoolean() ? new String[]{"rokushiki", "seastone_jitte"} : new String[]{"ittoryu", "fine_cutlass"};
        if (role.equals("cp_agent")) return new String[]{"rokushiki", ""};
        if (role.equals("revolutionary")) return new String[]{"storm_dance", "spear"};
        return switch (r.nextInt(6)) {
            case 0 -> new String[]{"gunslinger", "flintlock"};
            case 1 -> new String[]{"ittoryu", "katana"};
            case 2 -> new String[]{"black_leg", ""};
            case 3 -> new String[]{"nitoryu", "cutlass"};
            case 4 -> new String[]{"storm_dance", "spear"};
            default -> new String[]{"brawler", role.equals("bandit") ? "bo_staff" : "cutlass"};
        };
    }

    /** Mostly human; the original peoples where their islands suit them, the rarer canon peoples on the Grand Line. */
    static String race(StoryContext c, String role) {
        if (role.startsWith("marine") || role.equals("cp_agent") || role.equals("child")) return "human";
        int roll = c.r.nextInt(100);
        if (roll < 72) return "human";
        IslandTheme th = c.isl.theme;
        if (roll < 86) {
            Race r = switch (th) {
                case VOLCANIC, BADLANDS -> Race.EMBERKIN;
                case TROPICAL, FLOWER, MEADOW, SWAMP, CHERRY -> Race.VINEFOLK;
                case ROCKY, WHITE_ROCK -> Race.STONEHIDE;
                case DARK, MUSHROOM -> Race.ABYSSAL;
                case SNOW, ICE, TAIGA -> Race.STORMWING;
                default -> c.isl.shape == com.grandline.world.IslandShape.ATOLL || c.isl.shape == com.grandline.world.IslandShape.RING ? Race.REEFBORN : Race.STARBORN;
            };
            return r.id();
        }
        if (c.isl.zone == SeaZone.NEW_WORLD || c.grandLine()) {
            Race[] gl = {Race.FISHMAN, Race.LONG_ARM, Race.LONG_LEG, Race.THREE_EYE, Race.MINK, Race.SNAKENECK};
            return gl[c.r.nextInt(gl.length)].id();
        }
        return "human";
    }

    // ------------------------------------------------------------------ places

    Site site(String tag, Spot at, SiteKind kind, String what, Component name) {
        String key = c.prefix + c.isl.id + "#" + tag;
        Site s = new Site(key, c.isl.id, at.x(), at.z(), at.y(), kind, what, c.tier(), name, Component.translatable("story.grandline.site." + tag));
        sites.put(key, s);
        return s;
    }

    Site reach(String tag, Spot at) {
        return site(tag, at, SiteKind.REACH, "", null);
    }

    /** Foes of a role fielded at a place (shares the place's key prefix). */
    Site foes(String tag, Spot at, String role) {
        return site(tag, at, SiteKind.ROLE, role, null);
    }

    Site beasts(String tag, Spot at, String kind, Component name) {
        return site(tag, at, kind.equals("sea_king") ? SiteKind.SEA_KING : SiteKind.BEAST, kind, name);
    }

    // ------------------------------------------------------------------ steps

    final class Step {
        final int n;
        final String faction;
        final List<QuestDef.Objective> obj = new ArrayList<>();
        final List<SiteRef> refs = new ArrayList<>();
        final Map<String, Integer> rep = new LinkedHashMap<>();
        final List<Supplier<ItemStack>> items = new ArrayList<>();
        double weight = 1;
        double beliMul = 1;
        long bounty;
        boolean ownText;

        Step(int n, String faction) {
            this.n = n;
            this.faction = faction;
        }

        Step o(String type, String target, int count) {
            obj.add(new QuestDef.Objective(type, target, count));
            return this;
        }

        Step at(Site s) {
            refs.add(new SiteRef(obj.size() - 1, s.key(), obj.get(obj.size() - 1).count()));
            return this;
        }

        Step talk(Person p) { return o("talk", p.id(), 1); }
        Step defeat(Person p) { return o("defeat", p.id(), 1); }
        Step arrest(Person p) { return o("arrest", p.id(), 1); }
        Step kill(String role, int n, Site s) { return o("kill", role, n).at(s); }
        Step slay(String kind, int n, Site s) { return o("slay", kind, n).at(s); }
        Step reach(Site s) { return o("reach", s.key(), 1).at(s); }
        Step collect(String item, int n) { return o("collect", item, n); }
        Step discover(String island) { return o("discover", island, 1); }
        Step poneglyph() { return o("poneglyph", "any", 1); }

        /** Guards fielded at a site for as long as this step is active (not counted by any objective). */
        Step guards(Site s, int count) {
            refs.add(new SiteRef(-1, s.key(), count));
            return this;
        }

        Step weight(double w) { weight = w; return this; }
        Step beli(double m) { beliMul = m; return this; }
        Step bounty(long b) { bounty = b; return this; }
        Step rep(String k, int v) { rep.merge(k, v, Integer::sum); return this; }
        Step island(int v) { return rep("island/" + c.isl.id, v); }
        Step item(Supplier<ItemStack> s) { items.add(s); return this; }
        /** This faction variant has its own description ({@code .desc.<faction>}). */
        Step text() { ownText = true; return this; }
    }

    Step step(int n) {
        Step s = new Step(n, null);
        steps.add(s);
        return s;
    }

    /** A faction's own version of step {@code n} ("marine", "pirate", "bounty_hunter", "revolutionary", "civilian"). */
    Step variant(int n, String faction) {
        Step s = new Step(n, faction).text();
        steps.add(s);
        return s;
    }

    // ------------------------------------------------------------------ build

    String questId(int n) {
        return c.prefix + "story_" + c.isl.id + "_" + n;
    }

    IslandStory build() {
        Object[] a = args.toArray();
        int last = 0;
        for (Step s : steps) last = Math.max(last, s.n);
        List<QuestDef> quests = new ArrayList<>();
        Map<String, Component> titles = new LinkedHashMap<>(), descs = new LinkedHashMap<>();
        Map<String, List<SiteRef>> refs = new LinkedHashMap<>();
        int tier = c.tier();
        for (Step s : steps) {
            String id = questId(s.n) + (s.faction == null ? "" : "." + s.faction);
            boolean final_ = s.n == last;
            int xp = (int) Math.round(XP[tier] * s.weight * (final_ ? 1.5 : 1));
            long beli = Math.round(BELI[tier] * s.weight * s.beliMul * (final_ ? 1.5 : 1));
            Map<String, Integer> rep = new LinkedHashMap<>(s.rep);
            if (final_ && !rep.containsKey("island/" + c.isl.id)) rep.put("island/" + c.isl.id, 60);
            String next = s.n == last ? null : questId(s.n + 1);
            List<String> req = s.n == 1 ? List.of() : List.of(questId(s.n - 1));
            quests.add(new QuestDef(id, "notable:" + giver.id(), c.isl.id, List.copyOf(s.obj), xp, beli, s.bounty, Map.copyOf(rep), List.copyOf(s.items), req, next, null, false, "island_story"));
            String key = "story.grandline." + t.id + "." + s.n;
            titles.put(id, Component.translatable(key, a));
            descs.put(id, Component.translatable(key + ".desc" + (s.ownText && s.faction != null ? "." + s.faction : ""), a));
            if (!s.refs.isEmpty()) refs.put(id, List.copyOf(s.refs));
        }
        return new IslandStory(c.isl.id, t, List.copyOf(people), quests, titles, descs, refs, List.copyOf(sites.values()), a, giver.id(), questId(last));
    }

    // ------------------------------------------------------------------ reward items

    static Supplier<ItemStack> mod(String id, int n) {
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, id)), n);
    }

    static Supplier<ItemStack> vanilla(String id, int n) {
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(id)), n);
    }

    static Supplier<ItemStack> scroll(String style) {
        return () -> {
            ItemStack s = new ItemStack(ModItems.STYLE_SCROLL.get());
            s.set(ModDataComponents.STYLE.get(), style);
            return s;
        };
    }

    static Supplier<ItemStack> pose(String island) {
        return () -> {
            ItemStack s = new ItemStack(ModItems.ETERNAL_POSE.get());
            s.set(ModDataComponents.POSE_TARGET.get(), island);
            return s;
        };
    }

    /** Styles a story can teach as a reward (no race-bound or legendary ones). */
    static final String[] SCROLLS = {"black_leg", "ittoryu", "nitoryu", "gunslinger", "sniper", "storm_dance", "tide_grapple", "anchor_brawl", "life_return", "okama_kenpo"};
}
