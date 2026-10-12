package com.grandline.journey;

import com.grandline.GrandLine;
import com.grandline.activity.ActivityItems;
import com.grandline.activity.SeaFishing;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Race;
import com.grandline.quest.Awakening;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.ShipLook;
import com.grandline.ship.ShipRegistry;
import com.grandline.ship.SpecialRoutes;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.SkyWorld;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The journey: vanilla advancements (data/grandline/advancement/journey/, their own tab) granted through one custom
 * criterion, {@code grandline:milestone} with an id. Game code calls {@link #milestone} from the hook where the thing
 * happens (an island discovered, a fruit eaten, a bounty threshold crossed, a ship bought...). On login (and right
 * after a character is made) {@link #credit} grants whatever the saved data already shows, so characters made before
 * the journey existed are not left behind.
 * <p>
 * Advancements belong to the player <b>account</b>, not the character: they stay when a character dies for good, and a
 * new life can earn the race milestones it has not yet earned (three races make "Many Lives").
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Journey {
    // ================================================================== milestone ids
    public static final String BEGIN = "begin";
    // the route through the seas
    public static final String SET_SAIL = "set_sail", FIRST_ISLAND = "first_island", REVERSE_MOUNTAIN = "reverse_mountain",
            PARADISE = "paradise", ALABASTA = "alabasta", WATER_7 = "water_7", SABAODY = "sabaody", HOLY_LAND = "holy_land",
            CALM_BELT = "calm_belt", COATING = "coating", FISHMAN_ISLAND = "fishman_island", NEW_WORLD = "new_world",
            SKYPIEA = "skypiea", LAUGH_TALE = "laugh_tale";
    public static final String EAST_BLUE = "east_blue", WEST_BLUE = "west_blue", NORTH_BLUE = "north_blue", SOUTH_BLUE = "south_blue";
    // Devil Fruits and Haki
    public static final String FRUIT_EAT = "fruit_eat", FRUIT_MASTER = "fruit_master", FRUIT_AWAKEN = "fruit_awaken";
    public static final String HAKI = "haki_";
    public static final List<String> HAKI_KINDS = List.of("observation", "armament", "conqueror", "future_sight", "emission", "internal", "infusion");
    // bounties
    public static final String BOUNTY_POSTER = "bounty_poster", BOUNTY_10M = "bounty_10m", BOUNTY_100M = "bounty_100m", BOUNTY_1B = "bounty_1b",
            BUSTER_CALL = "buster_call_survived";
    public static final long[] BOUNTY_TIERS = {1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L};
    private static final String[] BOUNTY_IDS = {BOUNTY_POSTER, BOUNTY_10M, BOUNTY_100M, BOUNTY_1B};
    // ships
    public static final String SHIP_BUY = "ship_buy", SHIP_UPGRADE = "ship_upgrade", SHIP_CUSTOMISE = "ship_customise", SHIP_PRIZE = "ship_prize",
            SEA_KING = "sea_king";
    // crew
    public static final String CREW_RECRUIT = "crew_recruit", CREW_FIVE = "crew_five", CREW_ROLE = "crew_role_";
    /** The posts of a classic crew ("Every Post Filled": swordsman or swordswoman). */
    public static final List<String> CREW_POSTS = List.of("navigator", "cook", "doctor", "shipwright", "musician", "sniper", "helmsman",
            "swordsman", "swordswoman", "archaeologist");
    // the Saltfire Saga
    public static final String STORY_PART = "story_part";
    public static final List<String> ENDINGS = List.of("ending_saltfire_kindled", "ending_true_justice", "ending_dawn", "ending_last_bounty", "ending_free_press");
    /** The quests that close each Part (any faction variant counts); Part I closes in whichever Blue you began. */
    static final Map<String, Integer> PART_ENDS = Map.of("logue_3", 1, "north_lvneel_passage", 1, "west_ilisia_audience", 1, "south_sorbet_royal", 1,
            "sabaody_3", 2, "war_3", 3, "lodestar_1", 4);
    // activities
    public static final String TREASURE_MAP = "treasure_map", GIANT_CATCH = "giant_catch", SIGNATURE_DISH = "signature_dish";
    // races (account-wide: each life may add one)
    public static final String RACE = "race_", MANY_LIVES = "many_lives";
    public static final int MANY_LIVES_RACES = 3;
    // Den Den Mushi
    public static final String DEN_CALL = "den_call", DEN_CREW = "den_crew", DEN_EAVESDROP = "den_eavesdrop";

    /** Islands that are milestones of their own when discovered. */
    static final Map<String, String> ISLANDS = Map.of("twin_capes", REVERSE_MOUNTAIN, "alabasta", ALABASTA, "water_7", WATER_7,
            "sabaody_archipelago", SABAODY, "mary_geoise", HOLY_LAND, "fishman_island", FISHMAN_ISLAND, "laugh_tale", LAUGH_TALE);

    /** Every milestone id the game fires (each must be the criterion of at least one journey advancement). */
    public static Set<String> allIds() {
        Set<String> s = new LinkedHashSet<>(List.of(BEGIN, SET_SAIL, FIRST_ISLAND, REVERSE_MOUNTAIN, PARADISE, ALABASTA, WATER_7, SABAODY, HOLY_LAND,
                CALM_BELT, COATING, FISHMAN_ISLAND, NEW_WORLD, SKYPIEA, LAUGH_TALE, EAST_BLUE, WEST_BLUE, NORTH_BLUE, SOUTH_BLUE,
                FRUIT_EAT, FRUIT_MASTER, FRUIT_AWAKEN, BOUNTY_POSTER, BOUNTY_10M, BOUNTY_100M, BOUNTY_1B, BUSTER_CALL,
                SHIP_BUY, SHIP_UPGRADE, SHIP_CUSTOMISE, SHIP_PRIZE, SEA_KING, CREW_RECRUIT, CREW_FIVE,
                TREASURE_MAP, GIANT_CATCH, SIGNATURE_DISH, MANY_LIVES, DEN_CALL, DEN_CREW, DEN_EAVESDROP));
        for (String h : HAKI_KINDS) s.add(HAKI + h);
        for (String p : CREW_POSTS) s.add(CREW_ROLE + p);
        for (int i = 1; i <= 4; i++) s.add(STORY_PART + i);
        s.addAll(ENDINGS);
        for (Race r : Race.playableRaces()) s.add(RACE + r.id());
        s.addAll(com.grandline.career.Careers.MILESTONES.values()); // careers: rank-ups
        return s;
    }

    // ================================================================== granting

    /** Fire the milestone criterion for a player (a no-op for advancements already done, unknown ids and fake players). */
    public static void milestone(@Nullable ServerPlayer sp, String id) {
        if (sp == null || sp.level().isClientSide) return;
        JourneyRegistry.MILESTONE.get().trigger(sp, id);
        if (id.startsWith(RACE)) manyLives(sp);
    }

    /** Whether the player's account has the journey advancement {@code journey/<name>}. */
    public static boolean done(ServerPlayer sp, String name) {
        AdvancementHolder h = sp.server.getAdvancements().get(GrandLine.id("journey/" + name));
        return h != null && sp.getAdvancements().getOrStartProgress(h).isDone();
    }

    private static void manyLives(ServerPlayer sp) {
        int n = 0;
        for (Race r : Race.playableRaces()) if (done(sp, RACE + r.id())) n++;
        if (n >= MANY_LIVES_RACES) JourneyRegistry.MILESTONE.get().trigger(sp, MANY_LIVES);
    }

    /** Grant everything the player's saved data already shows. Returns the milestones found. */
    public static Set<String> credit(ServerPlayer sp) {
        Set<String> ids = earned(sp);
        for (String id : ids) milestone(sp, id);
        return ids;
    }

    private static final Pattern FALLEN = Pattern.compile("\\((\\w+), Lv \\d+, bounty (-?\\d+)\\)$");

    /** Milestones the saved data shows: the character, its fallen predecessors, its crew and the account's ships. */
    public static Set<String> earned(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        Set<String> out = new LinkedHashSet<>();
        // earlier lives (permadeath archive: "Name (race, Lv n, bounty b)")
        for (String f : d.fallenCharacters) {
            Matcher m = FALLEN.matcher(f);
            if (!m.find()) continue;
            Race r = Race.byId(m.group(1));
            if (r.playable && r.id().equals(m.group(1))) out.add(RACE + r.id());
            bounty(Long.parseLong(m.group(2)), out);
        }
        if (!d.created) return out;
        out.add(BEGIN);
        out.add(RACE + d.race.id());
        MinecraftServer server = sp.server;
        List<WorldPlan> plans = plans(server);
        boolean beyondHome = false;
        for (String id : d.discoveredIslands) {
            Island isl = find(plans, id);
            if (isl == null) continue;
            if (isl.starterFor == null) {
                out.add(FIRST_ISLAND);
                beyondHome = true;
            }
            String m = ISLANDS.get(id);
            if (m != null) out.add(m);
            String z = zone(isl.zone);
            if (z != null) out.add(z);
            if (isl.zone == SeaZone.PARADISE) out.add(REVERSE_MOUNTAIN); // Paradise is entered over Reverse Mountain
            if (isl.zone == SeaZone.SKY_SEA) out.add(SKYPIEA);
            if (id.equals("fishman_island")) out.add(COATING); // only a coated ship dives that deep
        }
        if (d.fruit != null) {
            out.add(FRUIT_EAT);
            if (d.mastery("fruit/" + d.fruit) >= Awakening.MASTERY) out.add(FRUIT_MASTER);
            if (d.fruitAwakened) out.add(FRUIT_AWAKEN);
        }
        boolean[] haki = {d.observationUnlocked, d.armamentUnlocked, d.conquerorAwakened, d.futureSight, d.emission, d.internalDestruction, d.conquerorInfusion};
        for (int i = 0; i < haki.length; i++) if (haki[i]) out.add(HAKI + HAKI_KINDS.get(i));
        bounty(d.bounty, out);
        for (var e : PART_ENDS.entrySet()) if (QuestRegistry.isDone(d, e.getKey())) out.add(STORY_PART + e.getValue());
        for (String f : ENDINGS) if (d.flags.contains(f)) out.add(f);
        out.addAll(com.grandline.career.Careers.earnedMilestones(d)); // careers: ranks reached
        crew(CrewManager.members(server, d.characterId), out);
        boolean ownsShip = false;
        for (ShipRegistry.Record r : ShipRegistry.get(server).ownedBy(sp.getUUID())) {
            ownsShip = true;
            out.add(r.prize ? SHIP_PRIZE : SHIP_BUY);
            if (!r.upgrades.isEmpty()) out.add(SHIP_UPGRADE);
            if (Arrays.asList(r.upgrades.split(",")).contains(SpecialRoutes.COATING)) out.add(COATING);
            if (!r.look.equals(ShipLook.DEFAULT.encode())) out.add(SHIP_CUSTOMISE);
        }
        if (ownsShip && beyondHome) out.add(SET_SAIL);
        return out;
    }

    private static void bounty(long b, Set<String> out) {
        for (int i = 0; i < BOUNTY_TIERS.length; i++) if (b >= BOUNTY_TIERS[i]) out.add(BOUNTY_IDS[i]);
    }

    private static void crew(List<String> members, Set<String> out) {
        if (members.isEmpty()) return;
        out.add(CREW_RECRUIT);
        if (members.size() >= 5) out.add(CREW_FIVE);
        for (String id : members) {
            var n = NotableCharacters.get(id);
            if (n != null && CREW_POSTS.contains(n.crewRole())) out.add(CREW_ROLE + n.crewRole());
        }
    }

    @Nullable
    static String zone(SeaZone z) {
        return switch (z) {
            case EAST_BLUE -> EAST_BLUE;
            case WEST_BLUE -> WEST_BLUE;
            case NORTH_BLUE -> NORTH_BLUE;
            case SOUTH_BLUE -> SOUTH_BLUE;
            case PARADISE -> PARADISE;
            case CALM_BELT -> CALM_BELT;
            case NEW_WORLD -> NEW_WORLD;
            default -> null;
        };
    }

    private static List<WorldPlan> plans(MinecraftServer server) {
        List<WorldPlan> l = new ArrayList<>();
        for (ServerLevel lv : server.getAllLevels()) {
            WorldPlan p = WorldPlan.forLevel(lv);
            if (p != null && !l.contains(p)) l.add(p);
        }
        return l;
    }

    @Nullable
    private static Island find(List<WorldPlan> plans, String id) {
        for (WorldPlan p : plans) {
            Island i = p.byId(id);
            if (i != null) return i;
        }
        return null;
    }

    // ================================================================== hooks (one call from each place things happen)

    /** A character was just made on the creation screen. */
    public static void onCreated(ServerPlayer sp) {
        credit(sp);
    }

    /** Navigation: an island was discovered. */
    public static void onDiscover(ServerPlayer sp, Island isl) {
        if (isl.starterFor == null) milestone(sp, FIRST_ISLAND);
        String m = ISLANDS.get(isl.id);
        if (m != null) milestone(sp, m);
        String z = zone(isl.zone);
        if (z != null) milestone(sp, z);
    }

    private static final Map<UUID, SeaZone> LAST_ZONE = new HashMap<>();

    /** Navigation's two-second check: entering a new sea of the ocean world (fires once per change of sea). */
    public static void onSea(ServerPlayer sp) {
        if (sp.level().dimension() != Level.OVERWORLD || WorldPlan.of(sp.server) == null) return;
        SeaZone z = WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ());
        if (LAST_ZONE.put(sp.getUUID(), z) == z) return;
        String m = zone(z);
        if (m != null) milestone(sp, m);
    }

    /** A ship came down the last canal of Reverse Mountain into Paradise: everyone aboard. */
    public static void onCanalCrossed(ShipEntity ship) {
        if (!(ship.level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) if (p.getVehicle() == ship || (p.distanceToSqr(ship) < 64 * 64 && ship.carries(p))) milestone(p, REVERSE_MOUNTAIN);
    }

    /** A player raised the sails of a ship they command. */
    public static void onSetSail(ServerPlayer sp) {
        milestone(sp, SET_SAIL);
    }

    public static void onFruitEaten(ServerPlayer sp) {
        milestone(sp, FRUIT_EAT);
    }

    /** A mastery level went up (combat training). */
    public static void onMastery(ServerPlayer sp, CharacterData d, String key) {
        if (d.fruit != null && key.equals("fruit/" + d.fruit) && d.mastery(key) >= Awakening.MASTERY) milestone(sp, FRUIT_MASTER);
    }

    public static void onAwakened(ServerPlayer sp) {
        milestone(sp, FRUIT_AWAKEN);
    }

    /** Haki unlock announcement ({@code grandline.haki.unlock.<kind>}). */
    public static void onHaki(ServerPlayer sp, String unlockKey) {
        String kind = unlockKey.substring(unlockKey.lastIndexOf('.') + 1);
        if (HAKI_KINDS.contains(kind)) milestone(sp, HAKI + kind);
    }

    /** The bounty went up (Bounties.raise). */
    public static void onBounty(ServerPlayer sp, long bounty) {
        for (int i = 0; i < BOUNTY_TIERS.length; i++) if (bounty >= BOUNTY_TIERS[i]) milestone(sp, BOUNTY_IDS[i]);
    }

    public static void onShipBought(ServerPlayer sp) {
        milestone(sp, SHIP_BUY);
    }

    public static void onShipUpgraded(ServerPlayer sp, String upgrade) {
        milestone(sp, SHIP_UPGRADE);
        if (upgrade.equals(SpecialRoutes.COATING)) milestone(sp, COATING);
    }

    public static void onShipCustomised(ServerPlayer sp) {
        milestone(sp, SHIP_CUSTOMISE);
    }

    public static void onPrize(Player p) {
        if (p instanceof ServerPlayer sp) milestone(sp, SHIP_PRIZE);
    }

    public static void onSeaKingSlain(ServerPlayer sp) {
        milestone(sp, SEA_KING);
    }

    /** Someone joined the player's crew. */
    public static void onRecruit(ServerPlayer sp) {
        Set<String> out = new LinkedHashSet<>();
        crew(CrewManager.members(sp.server, Characters.get(sp).characterId), out);
        for (String id : out) milestone(sp, id);
    }

    /** A quest was completed (Quests.finish): the Parts of the saga and its endings. */
    public static void onQuestDone(ServerPlayer sp, QuestDef q) {
        Integer part = PART_ENDS.get(QuestRegistry.baseId(q.id()));
        if (part != null) milestone(sp, STORY_PART + part);
        if (q.flag() != null && ENDINGS.contains(q.flag())) milestone(sp, q.flag());
    }

    public static void onTreasureSolved(ServerPlayer sp) {
        milestone(sp, TREASURE_MAP);
    }

    /** Something came up on the line (SeaFishing). */
    public static void onCatch(ServerPlayer sp, ItemStack st) {
        if (SeaFishing.isGiant(st)) milestone(sp, GIANT_CATCH);
    }

    /** A dish was cooked (Meals). */
    public static void onCooked(Player p, ItemStack dish) {
        if (p instanceof ServerPlayer sp && signature(dish)) milestone(sp, SIGNATURE_DISH);
    }

    public static boolean signature(ItemStack s) {
        return s.is(ActivityItems.SALTFIRE_CURRY.get()) || s.is(ActivityItems.TIDEWATER_SASHIMI.get()) || s.is(ActivityItems.SEA_KING_HOTPOT.get())
                || s.is(ActivityItems.PIRATES_FEAST.get());
    }

    // ================================================================== events

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) credit(sp);
    }

    /** The only way up to the White Sea is the Knock Up Stream (or falling from it): arriving there is the milestone. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.getTo() == SkyWorld.LEVEL && Characters.get(sp).created) milestone(sp, SKYPIEA);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_ZONE.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        LAST_ZONE.clear();
    }

    private Journey() {}
}
