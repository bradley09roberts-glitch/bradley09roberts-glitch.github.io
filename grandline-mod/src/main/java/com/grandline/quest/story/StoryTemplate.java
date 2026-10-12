package com.grandline.quest.story;

import com.grandline.creature.WildBeast;
import com.grandline.npc.notable.NotableCharacters.Disposition;
import com.grandline.player.Faction;
import com.grandline.quest.story.StoryBuilder.Step;
import com.grandline.quest.story.StoryContext.Spot;
import com.grandline.quest.story.StoryModel.Person;
import com.grandline.quest.story.StoryModel.Site;
import com.grandline.world.Island;
import com.grandline.world.IslandTheme;
import com.grandline.world.SeaZone;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The twenty story templates of the original islands (docs/STORY.md, "Original islands"). Each says how well it fits
 * an island (0 = not at all) and builds the story from the island's facts. Text lives in lang/en_us/island_stories.json;
 * the comment on each template lists its text arguments (%1$s is always the island).
 */
public enum StoryTemplate {
    /** %2 headman, %3 victim, %4 bandit chief. */
    BANDIT_TOLL("bandit_toll"),
    /** %2 fisher, %3 witness, %4 the beast's kind, %5 its nickname. */
    SEA_BEAST("sea_beast"),
    /** %2 parent, %3 the child's friend, %4 the missing child. */
    LOST_CHILD("lost_child"),
    /** %2 whistleblower (a Marine), %3 the crooked officer. */
    CROOKED_OFFICER("crooked_officer"),
    /** %2 head of the first house, %3 head of the second, %4 and %5 the two family names, %6 the peace gift. */
    FAMILY_FEUD("family_feud"),
    /** %2 priest, %3 the spirit the shrine was raised to, %4 the offering. */
    CURSED_SHRINE("cursed_shrine"),
    /** %2 harbourmaster, %3 informant, %4 smuggler boss. */
    SMUGGLERS("smugglers"),
    /** %2 widow, %3 old salt, %4 the lost ship. */
    HEIRLOOM("heirloom"),
    /** %2 barkeep (or deckhand on a wild island), %3 rival captain, %4 the crew's flag word. */
    RIVAL_CREW("rival_crew"),
    /** %2 doctor, %3 herbalist, %4 the herbalist's island, %5 the herb. */
    FEVER("fever"),
    /** %2 organizer, %3 raider captain, %4 the festival, %5 the festival goods. */
    FESTIVAL("festival"),
    /** %2 debtor, %3 second debtor, %4 the Warlord's agent, %5 the Warlord's epithet, %6 the bodyguards' kind. */
    DEBT_COLLECTOR("debt_collector"),
    /** %2 castaway, %3 the castaway's home island, %4 the food he asks for. */
    CASTAWAY("castaway"),
    /** %2 hunter, %3 the beast's kind. */
    BEAST_NEST("beast_nest"),
    /** %2 leader of the townsfolk, %3 the occupying captain, %4 the crew's flag word. */
    OCCUPATION("occupation"),
    /** %2 cell leader (a Revolutionary), %3 courier, %4 the Cipher Pol agent. */
    REBEL_CELL("rebel_cell"),
    /** %2 commander, %3 the deserter. */
    DESERTER("deserter"),
    /** %2 old pirate, %3 the dead captain whose map it is. */
    TREASURE_MAP("treasure_map"),
    /** %2 guard, %3 %4 %5 the three suspects, %6 the culprit, %7 the crime. */
    WHODUNIT("whodunit"),
    /** %2 scholar. */
    RUIN_SCHOLAR("ruin_scholar");

    public final String id;

    StoryTemplate(String id) {
        this.id = id;
    }

    public static StoryTemplate byId(String id) {
        for (StoryTemplate t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    // ================================================================== fit

    /** How well this template suits the island (relative weight; 0 = never). */
    public double weight(StoryContext c) {
        SettlementType s = c.settlement();
        boolean blue = c.isl.zone.isBlue();
        return switch (this) {
            case BANDIT_TOLL -> switch (s) {
                case HAMLET, VILLAGE -> blue ? 4 : 2.5;
                case TOWN, PORT -> 2;
                default -> 0;
            };
            case SEA_BEAST -> c.town() && c.hasDock() || c.town() && s == SettlementType.HAMLET ? (c.isl.zone == SeaZone.CALM_BELT ? 6 : 3) : 0;
            case LOST_CHILD -> c.town() ? 2 : 0;
            case CROOKED_OFFICER -> s == SettlementType.MARINE_BASE ? 5 : c.bigTown() ? 1 : 0;
            case FAMILY_FEUD -> switch (s) {
                case HAMLET, VILLAGE, TOWN -> 2;
                default -> 0;
            };
            case CURSED_SHRINE -> c.secret(BuildingType.LOST_SHRINE) != null ? 5
                    : (c.isl.theme == IslandTheme.DARK || c.isl.theme == IslandTheme.MUSHROOM || c.isl.theme == IslandTheme.SWAMP) ? 2 : 0.3;
            case SMUGGLERS -> c.hasDock() && c.town() && s != SettlementType.HAMLET ? (s == SettlementType.PORT ? 5 : 2.5) : 0;
            case HEIRLOOM -> c.secret(BuildingType.SHIPWRECK) != null ? 4 : 0;
            case RIVAL_CREW -> s == SettlementType.NONE ? 3 : 1.2;
            case FEVER -> c.town() && !c.neighbours(1, false).isEmpty() ? 2 : 0;
            case FESTIVAL -> c.town() && s != SettlementType.HAMLET ? 2 : 0;
            case DEBT_COLLECTOR -> c.tier() >= 3 && c.town() && s != SettlementType.HAMLET ? 3 : 0;
            case CASTAWAY -> s == SettlementType.NONE ? 5 : s == SettlementType.RUINS ? 3 : 0;
            case BEAST_NEST -> c.beast() == null ? 0 : s == SettlementType.NONE ? 4 : 2.5;
            case OCCUPATION -> c.isl.faction.equals("pirate") && c.settled() ? 7 : 0;
            case REBEL_CELL -> c.isl.faction.equals("revolutionary") ? 8 : c.tier() >= 3 && c.bigTown() ? 0.6 : 0;
            case DESERTER -> s == SettlementType.MARINE_BASE ? 4 : 0;
            case TREASURE_MAP -> c.secret(BuildingType.BURIED_TREASURE) != null ? (s == SettlementType.NONE ? 3 : 1.5) : 0.5;
            case WHODUNIT -> c.town() && s != SettlementType.HAMLET ? 2 : 0;
            case RUIN_SCHOLAR -> c.landmark("ancient_ruin") != null ? 6 : 0;
        };
    }

    // ================================================================== build

    void build(StoryBuilder b) {
        StoryContext c = b.c;
        int t = c.tier();
        boolean blue = c.isl.zone.isBlue();
        boolean newWorld = c.isl.zone == SeaZone.NEW_WORLD;
        switch (this) {
            case BANDIT_TOLL -> {
                Person head = b.giver("headman", "elder", c.townSpot(0));
                Person victim = b.friend("victim", c.r.nextBoolean() ? "shopkeeper" : "farmer", c.townSpot(2));
                Spot campAt = secretOr(c, BuildingType.HIDDEN_CACHE, "camp");
                Person chief = b.villain("chief", "bandit", Faction.WILD, 1, campAt, false);
                Site camp = b.foes("camp", campAt, "bandit");
                b.step(1).talk(victim).weight(0.5);
                b.step(2).kill("bandit", 3 + t / 3, camp).item(StoryBuilder.mod("meat_on_bone", 3));
                b.step(3).defeat(chief).talk(head).island(100).rep("civilian", 20);
                b.variant(3, "marine").arrest(chief).talk(head).island(100).rep("marine", 60);
                b.variant(3, "bounty_hunter").arrest(chief).talk(head).island(80).rep("bounty_hunter", 60).beli(1.6);
                b.variant(3, "pirate").defeat(chief).talk(victim).beli(2.0).island(20).rep("pirate", 40).bounty(bounty(t, 1));
            }
            case SEA_BEAST -> {
                Person fisher = b.giver("fisher", "dockworker", c.dock());
                Person witness = b.friend("witness", "villager", c.townSpot(1));
                boolean king = c.isl.zone == SeaZone.CALM_BELT || c.grandLine() && c.r.nextInt(3) == 0;
                WildBeast.Kind k = c.beast() != null ? c.beast() : WildBeast.Kind.SEA_CAT;
                String kind = king ? "sea_king" : k.id();
                b.arg(Component.translatable("entity.grandline." + kind));
                String nick = PeopleNames.beast(c.r);
                b.arg(nick);
                Site nets = b.reach("nets", c.beach(3));
                Site lair = b.beasts("lair", king ? c.sea(110, 10) : c.beach(7), kind, Component.literal(nick));
                b.step(1).talk(witness).weight(0.5);
                b.step(2).reach(nets).weight(0.6);
                b.step(3).slay(kind, 1, lair).talk(fisher).weight(king ? 1.6 : 1.2).island(100)
                        .item(king ? StoryBuilder.mod("sea_king_steak", 3) : StoryBuilder.mod("meat_on_bone", 4));
            }
            case LOST_CHILD -> {
                String family = PeopleNames.family(c.culture, c.r);
                Person parent = b.giver("parent", "villager", Faction.CIVILIAN, 1, c.townSpot(1), PeopleNames.first(c.culture, c.r) + " " + family);
                Person friend = b.friend("friend", "child", c.townSpot(3));
                Spot hollowAt = c.wild("hollow");
                Person child = b.person("child", "child", Faction.CIVILIAN, Disposition.FRIENDLY, 1, hollowAt, PeopleNames.first(c.culture, c.r) + " " + family, false);
                Site hollow = b.reach("hollow", hollowAt);
                WildBeast.Kind k = c.beast();
                Step s2 = b.step(2).reach(hollow);
                if (k != null) s2.slay(k.id(), 2, b.beasts("hollow_beasts", hollowAt, k.id(), null));
                else s2.kill("bandit", 2, b.foes("hollow_bandits", hollowAt, "bandit"));
                s2.talk(child);
                b.step(1).talk(friend).weight(0.4);
                b.step(3).talk(parent).island(120).rep("civilian", 30).item(StoryBuilder.vanilla("cake", 1));
            }
            case CROOKED_OFFICER -> {
                Person wb = b.giver("whistleblower", "marine", Faction.MARINE, 2, c.townSpot(2), b.name());
                Spot officeAt = c.townSpot(6);
                Person officer = b.person("officer", "marine_captain", Faction.MARINE, Disposition.VILLAIN, t + 1, officeAt, b.name(), false);
                Site box = b.reach("strongbox", c.wild("strongbox"));
                Site office = b.foes("office", officeAt, "marine");
                b.step(1).reach(box).weight(0.7);
                b.step(2).kill("marine", 2, office).defeat(officer).talk(wb).island(80).rep("civilian", 30);
                b.variant(2, "marine").arrest(officer).talk(wb).island(80).rep("marine", 120);
                b.variant(2, "bounty_hunter").arrest(officer).talk(wb).rep("bounty_hunter", 60).beli(1.5);
                b.variant(2, "revolutionary").kill("marine", 3, office).defeat(officer).talk(wb).island(60).rep("revolutionary", 100);
            }
            case FAMILY_FEUD -> {
                String famA = PeopleNames.family(c.culture, c.r), famB = PeopleNames.family(c.culture, c.r);
                Person a = b.giver("head_a", "farmer", Faction.CIVILIAN, 2, c.townSpot(1), PeopleNames.first(c.culture, c.r) + " " + famA);
                Person bb = b.person("head_b", "merchant", Faction.CIVILIAN, Disposition.FRIENDLY, 2, c.townSpot(4), PeopleNames.first(c.culture, c.r) + " " + famB, false);
                b.arg(famA);
                b.arg(famB);
                String gift = t <= 2 ? "#minecraft:small_flowers" : "minecraft:cake";
                int n = t <= 2 ? 8 : 1;
                b.arg(itemName(gift));
                Site orchard = b.foes("orchard", c.wild("orchard"), "bandit");
                b.step(1).talk(bb).weight(0.4);
                b.step(2).kill("bandit", 3, orchard).weight(0.9);
                b.step(3).collect(gift, n).talk(bb).talk(a).island(120).rep("civilian", 20);
            }
            case CURSED_SHRINE -> {
                Person priest = b.giver("priest", "priest", c.settled() ? c.townSpot(4) : c.beach(2));
                b.arg(PeopleNames.first(c.culture, c.r));
                String offering = blue ? "#minecraft:small_flowers" : "minecraft:gold_ingot";
                int n = blue ? 6 : newWorld ? 4 : 2;
                b.arg(itemName(offering));
                Spot at = secretOr(c, BuildingType.LOST_SHRINE, "shrine");
                Site shrine = b.reach("shrine", at);
                Site guardians = b.foes("shrine_guardians", at, "ruin_guardian");
                b.step(1).reach(shrine).weight(0.6);
                b.step(2).kill("ruin_guardian", blue ? 2 : 3, guardians);
                Step s3 = b.step(3).collect(offering, n).talk(priest).island(100);
                if (c.r.nextInt(5) < 2) s3.item(StoryBuilder.scroll("life_return"));
            }
            case SMUGGLERS -> {
                Person hm = b.giver("harbourmaster", "dockworker", c.dock());
                Person informant = b.friend("informant", "patron", c.townSpot(3));
                Spot cacheAt = secretOr(c, BuildingType.HIDDEN_CACHE, "cache");
                Person boss = b.villain("smuggler", "pirate_captain", Faction.PIRATE, 1, cacheAt, false);
                Site cache = b.reach("cache", cacheAt);
                Site crew = b.foes("cache_crew", cacheAt, "pirate");
                b.step(1).talk(informant).weight(0.5);
                b.step(2).reach(cache).kill("pirate", 3, crew);
                b.step(3).defeat(boss).talk(hm).island(100).rep("marine", 10);
                b.variant(3, "marine").arrest(boss).talk(hm).island(100).rep("marine", 80);
                b.variant(3, "bounty_hunter").arrest(boss).talk(hm).rep("bounty_hunter", 60).beli(1.6);
                b.variant(3, "pirate").defeat(boss).talk(informant).beli(2.2).rep("pirate", 50).island(-20).bounty(bounty(t, 2));
            }
            case HEIRLOOM -> {
                Person widow = b.giver("widow", "elder", c.townSpot(2));
                Person salt = b.friend("old_salt", "dockworker", c.dock());
                b.arg(PeopleNames.ship(c.r));
                Plot wreck = c.secret(BuildingType.SHIPWRECK);
                Site site = b.reach("wreck", wreck != null ? c.at(wreck) : c.beach(9));
                b.step(1).talk(salt).weight(0.4);
                b.step(2).reach(site).weight(0.8);
                Step s3 = b.step(3).talk(widow).island(100).item(StoryBuilder.vanilla("gold_ingot", 2 + t / 2));
                if (c.r.nextInt(3) == 0) s3.item(StoryBuilder.scroll(StoryBuilder.SCROLLS[c.r.nextInt(StoryBuilder.SCROLLS.length)]));
            }
            case RIVAL_CREW -> {
                Person giver = c.settled() ? b.giver("barkeep", "bartender", c.townSpot(1)) : b.giver("deckhand", "villager", c.beach(1));
                Spot campAt = c.settled() ? c.wild("rival_camp") : c.beach(4);
                Person rival = b.person("rival", "pirate_captain", Faction.PIRATE, Disposition.RIVAL, t + 1, campAt, b.outlawName(), false);
                b.arg(PeopleNames.crew(c.r));
                Site camp = b.foes("rival_camp", campAt, "pirate");
                b.step(1).kill("pirate", 3, camp).weight(0.8);
                b.step(2).defeat(rival).rep("pirate", 20);
                b.variant(2, "marine").arrest(rival).rep("marine", 60);
                b.variant(2, "bounty_hunter").arrest(rival).rep("bounty_hunter", 50).beli(1.5);
                Step s3 = b.step(3).talk(rival).island(60);
                if (List.of(StoryBuilder.SCROLLS).contains(rival.style())) s3.item(StoryBuilder.scroll(rival.style()));
                b.variant(3, "marine").talk(giver).island(60);
                b.variant(3, "bounty_hunter").talk(giver).island(60);
            }
            case FEVER -> {
                Island other = c.neighbours(1, false).get(0);
                StoryContext oc = new StoryContext(c.plan, other, c.prefix, 7);
                Person doc = b.giver("doctor", "doctor", c.townSpot(2));
                Person herbalist = b.elsewhere("herbalist", "scholar", oc, oc.settled() ? oc.townSpot(4) : oc.beach(2));
                b.arg(oc.islandName());
                String[] herb = herbFor(other.theme);
                b.arg(itemName(herb[0]));
                b.step(1).discover(other.id).talk(herbalist).weight(0.8);
                b.step(2).collect(herb[0], Integer.parseInt(herb[1])).weight(0.6);
                b.step(3).talk(doc).island(120).rep("civilian", 40).item(StoryBuilder.vanilla("golden_apple", 1));
            }
            case FESTIVAL -> {
                Person org = b.giver("organizer", "innkeeper", c.townSpot(1));
                Person raider = b.villain("raider", "pirate_captain", Faction.PIRATE, 1, c.dock(), true);
                b.arg(Component.translatable("story.grandline.festival.kind." + c.r.nextInt(6)));
                String goods = blue ? "#minecraft:small_flowers" : "minecraft:lantern";
                int n = blue ? 10 : newWorld ? 5 : 3;
                b.arg(itemName(goods));
                Site square = b.foes("square", c.townSpot(0), "pirate");
                b.step(1).collect(goods, n).weight(0.6);
                b.step(2).kill("pirate", 4, square).defeat(raider);
                b.variant(2, "marine").kill("pirate", 4, square).arrest(raider).rep("marine", 60);
                b.variant(2, "bounty_hunter").kill("pirate", 4, square).arrest(raider).beli(1.5).rep("bounty_hunter", 50);
                b.step(3).talk(org).island(120).rep("civilian", 30).item(StoryBuilder.vanilla("cake", 1)).item(StoryBuilder.vanilla("firework_rocket", 6));
            }
            case DEBT_COLLECTOR -> {
                Person debtor = b.giver("debtor", "merchant", c.townSpot(2));
                Person debtor2 = b.friend("debtor2", "shopkeeper", c.townSpot(4));
                Spot officeAt = c.wild("office");
                Person agent = b.villain("agent", "pirate_captain", Faction.PIRATE, 2, officeAt, false);
                b.arg(PeopleNames.epithet(c.r));
                String guard = t >= 6 ? "buccaneer_brawler" : "pirate";
                b.arg(Component.translatable("role.grandline." + guard));
                Site office = b.foes("office", officeAt, guard);
                b.step(1).talk(debtor2).weight(0.5);
                b.step(2).kill(guard, 3, office);
                b.step(3).defeat(agent).talk(debtor).island(120);
                b.variant(3, "marine").arrest(agent).talk(debtor).island(120).rep("marine", 100);
                b.variant(3, "bounty_hunter").arrest(agent).talk(debtor).rep("bounty_hunter", 80).beli(1.6);
                b.variant(3, "pirate").defeat(agent).talk(debtor2).beli(2.4).rep("pirate", 60).island(30).bounty(bounty(t, 3));
                b.variant(3, "revolutionary").defeat(agent).talk(debtor).island(120).rep("revolutionary", 120);
            }
            case CASTAWAY -> {
                Person cast = b.giver("castaway", "villager", c.beach(0));
                Island home = null;
                for (Island i : c.neighbours(12, false)) if (i.canon && i.starterFor == null) { home = i; break; }
                if (home == null) home = c.neighbours(1, false).isEmpty() ? c.isl : c.neighbours(1, false).get(0);
                b.arg(StoryContext.islandName(home));
                String food = t <= 2 ? "minecraft:bread" : "minecraft:cooked_cod";
                b.arg(itemName(food));
                Site stash = b.reach("stash", secretOr(c, BuildingType.BURIED_TREASURE, "stash"));
                b.step(1).collect(food, 3 + t / 3).weight(0.5);
                b.step(2).reach(stash).weight(0.7);
                b.step(3).talk(cast).item(StoryBuilder.pose(home.id)).item(StoryBuilder.vanilla("gold_ingot", 2 + t / 2)).rep("civilian", 30);
            }
            case BEAST_NEST -> {
                WildBeast.Kind k = c.beast();
                Person hunter = c.settled() ? b.giver("hunter", "farmer", c.townSpot(3)) : b.giver("hunter", "villager", c.beach(0));
                b.arg(Component.translatable("entity.grandline." + k.id()));
                Site tracks = b.reach("tracks", c.wild("tracks"));
                Site nest = b.beasts("nest", c.wild("nest"), k.id(), null);
                b.step(1).reach(tracks).weight(0.5);
                b.step(2).slay(k.id(), 3 + t / 3, nest);
                b.step(3).talk(hunter).island(80).item(StoryBuilder.mod("meat_on_bone", 6));
            }
            case OCCUPATION -> {
                Person leader = b.giver("leader", "resident", c.townSpot(3));
                Person occupier = b.villain("occupier", "pirate_captain", Faction.PIRATE, 2, c.townSpot(0), false);
                b.arg(PeopleNames.crew(c.r));
                Site post = b.foes("post", c.townSpot(5), "pirate");
                b.step(1).kill("pirate", 5, post);
                b.step(2).defeat(occupier).island(150).rep("civilian", 30);
                b.variant(2, "marine").arrest(occupier).island(150).rep("marine", 120);
                b.variant(2, "bounty_hunter").arrest(occupier).beli(1.8).rep("bounty_hunter", 80);
                b.variant(2, "revolutionary").defeat(occupier).island(150).rep("revolutionary", 150);
                b.step(3).talk(leader).island(60).item(StoryBuilder.vanilla("gold_ingot", 3 + t / 2));
            }
            case REBEL_CELL -> {
                Person leader = b.giver("cell_leader", "revolutionary", Faction.REVOLUTIONARY, Math.max(3, t), c.townSpot(2), b.name());
                Person courier = b.friend("courier", "villager", c.dock());
                Person agent = b.villain("cp_hunter", "cp_agent", Faction.CIPHER_POL, 1, c.wild("agent"), false);
                b.step(1).talk(courier).weight(0.5);
                b.step(2).defeat(agent).rep("revolutionary", 60);
                Step s3 = b.step(3).talk(leader).rep("revolutionary", 100).island(80);
                if (c.r.nextBoolean()) s3.item(StoryBuilder.scroll("storm_dance"));
            }
            case DESERTER -> {
                Person cmd = b.giver("commander", "marine_officer", Faction.MARINE, t + 1, c.townSpot(0), b.name());
                Spot campAt = c.wild("deserter_camp");
                Person des = b.person("deserter", "marine", Faction.MARINE, Disposition.RIVAL, t + 1, campAt, b.name(), false);
                Site camp = b.reach("deserter_camp", campAt);
                Site payroll = b.reach("payroll", c.wild("payroll"));
                b.step(1).reach(camp).weight(0.6);
                b.step(2).defeat(des).rep("marine", 30);
                b.variant(2, "marine").arrest(des).rep("marine", 100);
                b.variant(2, "bounty_hunter").arrest(des).rep("bounty_hunter", 50).beli(1.4);
                b.variant(2, "pirate").talk(des).rep("pirate", 30);
                b.step(3).talk(cmd).island(80);
                b.variant(3, "pirate").reach(payroll).beli(1.8);
            }
            case TREASURE_MAP -> {
                Person old = c.settled() ? b.giver("old_pirate", "bartender", c.townSpot(1)) : b.giver("old_pirate", "villager", c.beach(0));
                b.arg(PeopleNames.outlaw(c.culture, c.r));
                Spot at = secretOr(c, BuildingType.BURIED_TREASURE, "treasure");
                Site spot = b.reach("treasure", at);
                Site rivals = b.foes("treasure_rivals", at, "pirate");
                b.step(1).reach(spot).kill("pirate", 3, rivals);
                b.step(2).talk(old).island(60).item(StoryBuilder.vanilla("gold_ingot", 3 + t / 2));
                b.variant(2, "pirate").talk(old).beli(2.0).rep("pirate", 30).item(StoryBuilder.vanilla("gold_ingot", 3 + t));
            }
            case WHODUNIT -> {
                Person guard = b.giver("guard", "guard", c.townSpot(0));
                String[] roles = {"merchant", "cook", "farmer", "shopkeeper", "patron", "miller"};
                int culprit = c.r.nextInt(3);
                Person[] s = new Person[3];
                for (int i = 0; i < 3; i++)
                    s[i] = b.person("suspect_" + (i + 1), roles[c.r.nextInt(roles.length)], Faction.CIVILIAN, i == culprit ? Disposition.RIVAL : Disposition.FRIENDLY,
                            i == culprit ? t + 1 : 1, c.townSpot(2 + i * 2), b.name(), false);
                b.arg(s[culprit].name());
                b.arg(Component.translatable("story.grandline.whodunit.crime." + c.r.nextInt(5)));
                b.step(1).talk(s[0]).talk(s[1]).talk(s[2]).weight(0.7);
                b.step(2).defeat(s[culprit]).talk(guard).island(120).rep("civilian", 20);
                b.variant(2, "marine").arrest(s[culprit]).talk(guard).island(120).rep("marine", 60);
                b.variant(2, "bounty_hunter").arrest(s[culprit]).talk(guard).rep("bounty_hunter", 40).beli(1.4);
            }
            case RUIN_SCHOLAR -> {
                Person scholar = b.giver("scholar", "scholar", c.settled() ? c.townSpot(4) : c.beach(1));
                Spot ruinAt = c.landmark("ancient_ruin");
                Site ruin = b.reach("ruin", ruinAt);
                Site guardians = b.foes("ruin_guardians", ruinAt, "ruin_guardian");
                b.step(1).reach(ruin).weight(0.6);
                b.step(2).kill("ruin_guardian", 2, guardians).poneglyph().weight(1.2);
                b.step(3).talk(scholar).island(80).rep("revolutionary", 20).item(StoryBuilder.mod("sea_chart", 1));
            }
        }
    }

    // ================================================================== helpers

    private static Spot secretOr(StoryContext c, BuildingType type, String salt) {
        Plot p = c.secret(type);
        return p != null ? c.at(p) : c.wild(salt);
    }

    /** Bounty for a pirate who takes a cut (scaled by tier). */
    private static long bounty(int tier, int weight) {
        return 1_000_000L * tier * weight;
    }

    /** The herb for the fever story by the herbalist's island theme: {item, count}. */
    static String[] herbFor(IslandTheme theme) {
        return switch (theme) {
            case SNOW, ICE, TAIGA -> new String[]{"minecraft:sweet_berries", "8"};
            case TROPICAL, SWAMP, CHERRY -> new String[]{"minecraft:cocoa_beans", "6"};
            case DESERT, BADLANDS, SAVANNA -> new String[]{"minecraft:cactus", "4"};
            case MUSHROOM, DARK -> new String[]{"minecraft:brown_mushroom", "6"};
            case FLOWER, MEADOW, CANDY -> new String[]{"#minecraft:small_flowers", "8"};
            default -> new String[]{"minecraft:kelp", "8"};
        };
    }

    /** Display name of a collect target (an item, or one of the item tags stories use). */
    static Component itemName(String target) {
        if (target.startsWith("#")) return Component.translatable("story.grandline.item." + target.substring(target.indexOf(':') + 1));
        var rl = net.minecraft.resources.ResourceLocation.tryParse(target);
        var item = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        return item == null ? Component.literal(target) : Component.translatable(item.getDescriptionId());
    }
}
