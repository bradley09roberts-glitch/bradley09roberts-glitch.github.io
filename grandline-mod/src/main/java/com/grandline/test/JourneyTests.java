package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.activity.ActivityItems;
import com.grandline.activity.ActivityRegistry;
import com.grandline.activity.Meals;
import com.grandline.activity.SeaFishing;
import com.grandline.activity.TreasureMaps;
import com.grandline.activity.TreasureTarget;
import com.grandline.combat.Hits;
import com.grandline.core.GLConfig;
import com.grandline.creature.SeaKing;
import com.grandline.creature.SeaKingSpawner;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.FruitManager;
import com.grandline.fruit.FruitWorldData;
import com.grandline.fruit.TerrainChanges;
import com.grandline.haki.Haki;
import com.grandline.journey.DenDenMushi;
import com.grandline.journey.DenDenMushiItem;
import com.grandline.journey.Journey;
import com.grandline.journey.JourneyRegistry;
import com.grandline.journey.MilestoneTrigger;
import com.grandline.navigation.Navigation;
import com.grandline.npc.Bounties;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.Appearance;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.Awakening;
import com.grandline.quest.QuestEvents;
import com.grandline.quest.Quests;
import com.grandline.registry.ModEntities;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.grandline.world.settlement.SettlementType;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.grandline.test.SelfTest.check;

/**
 * Self-test steps for the journey (added at the end of phase 1 by {@link SelfTest}; quick run: GL_SELFTEST=journey).
 * <ul>
 *   <li>The advancements: every JSON under data/grandline/advancement/journey loads (counted against the files), sits
 *   in the journey tab under one root with the sea background, uses only the {@code grandline:milestone} criterion with
 *   ids the game fires, and every id the game fires has an advancement.</li>
 *   <li>The milestones are granted by the real hooks: character creation, Navigation discovering islands, eating a
 *   fruit, mastery training, the Awakening Trial, Haki unlocks, Bounties.raise, the shipwright (buy, upgrade,
 *   customise), taking a prize, slaying a Sea King, recruiting, finishing story quests, a giant catch, a signature
 *   dish and a solved treasure map; a permadeath keeps them (they are the account's) and three races make Many Lives;
 *   a login credits a character's saved data retroactively.</li>
 *   <li>Den Den Mushi between account players outside the player list (the API is driven directly): a call to someone
 *   without a snail fails, a declined call ends, an accepted call connects and chat goes only to the other party,
 *   hanging up ends it, the crew snail reaches crewmates only, a Baby Den Den is used up, a black snail overhears a
 *   Marine ship.</li>
 *   <li>Buster Call: only past the bounty threshold, not when disabled, issued by a crime near a Marine base and by a
 *   Vice Admiral who lived; a full call gives warning, spawns warships within the NPC ship cap (a Vice Admiral on
 *   each), they converge and bombard, it ends (survived: advancement and bounty) and the fleet withdraws. Shell bursts
 *   hurt a pirate and a ship but spare Marines, never change town blocks, and crater the wild only through
 *   TerrainChanges (reverted).</li>
 * </ul>
 * Players here are "account players": fake players whose advancements are real (vanilla gives fake players a no-op
 * advancement store), so the vanilla grant, persistence and the custom trigger are all exercised. Phase 2 checks the
 * advancements of one of them survived the restart.
 */
final class JourneyTests {
    /** A fake player with real, saved advancements (vanilla gives fake players a no-op advancement store). */
    static final class AccountPlayer extends FakePlayer {
        AccountPlayer(ServerLevel l, GameProfile p) {
            super(l, p);
        }

        @Override
        public boolean isFakePlayer() {
            return false;
        }
    }

    private static final Map<String, AccountPlayer> PLAYERS = new HashMap<>();
    private static final Set<Long> FORCED = new HashSet<>();
    private static final Map<UUID, List<Component>> INBOX = new HashMap<>();

    static AccountPlayer account(MinecraftServer s, String name) {
        return PLAYERS.computeIfAbsent(name, n -> new AccountPlayer(s.overworld(),
                new GameProfile(UUID.nameUUIDFromBytes(("gl-journey-" + n).getBytes(StandardCharsets.UTF_8)), n)));
    }

    private static void create(ServerPlayer p, String name, String race, String faction) {
        CompoundTag req = new CompoundTag();
        req.putString("name", name);
        req.putString("race", race);
        req.putString("faction", faction);
        req.putString("origin", "east");
        req.put("appearance", new Appearance().save());
        CharacterManager.create(p, req);
    }

    private static String missing(ServerPlayer p, String... names) {
        List<String> m = new ArrayList<>();
        for (String n : names) if (!Journey.done(p, n)) m.add(n);
        return m.isEmpty() ? "" : "missing " + m;
    }

    private static boolean criterion(ServerPlayer p, String adv, String crit) {
        AdvancementHolder h = p.server.getAdvancements().get(GrandLine.id("journey/" + adv));
        if (h == null) return false;
        CriterionProgress c = p.getAdvancements().getOrStartProgress(h).getCriterion(crit);
        return c != null && c.isDone();
    }

    private static void force(ServerLevel l, int cx, int cz) {
        if (FORCED.add(ChunkPos.asLong(cx, cz))) l.setChunkForced(cx, cz, true);
    }

    /** Generate and keep ticking the chunks within r chunks of (x, z). */
    private static void loadNow(ServerLevel l, double x, double z, int r) {
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                force(l, cx + dx, cz + dz);
                l.getChunk(cx + dx, cz + dz);
            }
    }

    private static void release(ServerLevel l) {
        for (long c : FORCED) l.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        FORCED.clear();
    }

    private static int ground(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void stand(ServerPlayer p, Island isl) {
        int y = ground(p.serverLevel(), isl.x, isl.z);
        p.moveTo(isl.x + 0.5, y + 1, isl.z + 0.5);
    }

    /** Every text in a component tree: literals, translation keys and their arguments. */
    static String flat(Component c) {
        StringBuilder sb = new StringBuilder();
        flat(c, sb);
        return sb.toString();
    }

    private static void flat(Component c, StringBuilder sb) {
        if (c.getContents() instanceof TranslatableContents tc) {
            sb.append('{').append(tc.getKey()).append('}');
            for (Object a : tc.getArgs()) {
                if (a instanceof Component ac) flat(ac, sb);
                else sb.append(a);
                sb.append(' ');
            }
        } else if (c.getContents() instanceof PlainTextContents pt) sb.append(pt.text());
        for (Component s : c.getSiblings()) flat(s, sb);
    }

    private static List<String> inbox(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (Component c : INBOX.getOrDefault(p.getUUID(), List.of())) out.add(flat(c));
        return out;
    }

    private static long count(ServerPlayer p, String needle) {
        return inbox(p).stream().filter(s -> s.contains(needle)).count();
    }

    private static String resource(String path) {
        try (InputStream in = JourneyTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** A settled island with a dock in the open Blues (not a starter, not Marine-held, no Marine base near). */
    private static Island dockIsland(WorldPlan p) {
        Island home = p.byId("shiokaze_isle");
        List<Island> l = new ArrayList<>();
        for (Island i : p.islands) {
            if (i.starterFor != null || !i.zone.isBlue() || i.radius > 420 || i.radius < 120 || !i.landmarks.isEmpty()) continue;
            if (i.settlementType != SettlementType.VILLAGE && i.settlementType != SettlementType.TOWN && i.settlementType != SettlementType.HAMLET) continue;
            if (i.faction.equals("marine") || !i.settlement().hasDock) continue;
            boolean base = false;
            for (Island m : p.islands)
                if ((m.faction.equals("marine") || m.settlementType == SettlementType.MARINE_BASE) && m.distance(i.x, i.z) < m.radius + BusterCall.BASE_REACH + 200) base = true;
            if (!base) l.add(i);
        }
        l.sort(Comparator.comparingDouble(i -> i.distance(home.x, home.z)));
        return l.isEmpty() ? null : l.get(0);
    }

    private static Vec3 dockTip(ServerLevel l, Island isl) {
        Settlement st = isl.settlement();
        int x = st.dockTipX(), z = st.dockTipZ();
        return new Vec3(x + 0.5, ground(l, x, z) + 1, z + 0.5);
    }

    private static void scrap(ServerLevel l, ShipEntity s) {
        if (s == null || s.isRemoved()) return;
        for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s))) if (l.getEntity(u) != null) l.getEntity(u).discard();
        for (GLNpc e : l.getEntitiesOfClass(GLNpc.class, s.getBoundingBox().inflate(s.cls.length), e -> s.carries(e) || e.getVehicle() == s)) e.discard();
        NavalCombat.unlink(s);
        s.discard();
    }

    private static void removeShipsOf(MinecraftServer s, ServerPlayer p) {
        ShipRegistry reg = ShipRegistry.get(s);
        for (ShipRegistry.Record r : new ArrayList<>(reg.ownedBy(p.getUUID()))) {
            for (ServerLevel l : s.getAllLevels())
                for (ShipEntity e : l.getEntitiesOfClass(ShipEntity.class, new AABB(r.x, r.y, r.z, r.x, r.y, r.z).inflate(80), e -> e.shipId.equals(r.shipId))) scrap(l, e);
            reg.remove(r.shipId);
        }
    }

    private static HumanoidNpc npc(ServerLevel l, String role, int tier, Vec3 at) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        if (n == null) return null;
        n.moveTo(at.x, at.y, at.z, 0, 0);
        n.configure(role, tier, "", BlockPos.containing(at), "", l.random);
        l.addFreshEntity(n);
        return n;
    }

    // ================================================================== phase 1

    private static final Island[] DOCK = new Island[1];
    private static final Vec3[] TIP = new Vec3[1];

    static void phase1(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            INBOX.clear();
            DenDenMushi.tap = (p, msg) -> INBOX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(msg);
            return 0;
        });
        advancementData(steps);
        hooks(steps);
        permadeathAndRaces(steps);
        retroactive(steps);
        denDenMushi(steps);
        busterCallRules(steps);
        busterCallRun(steps);
        shellBursts(steps);
        steps.add(s -> {
            // keep the account's advancements for phase 2, put everything back
            account(s, "JourneyJ").getAdvancements().save();
            DenDenMushi.tap = null;
            BusterCall.hookRules = null;
            release(s.overworld());
            for (AccountPlayer p : PLAYERS.values()) {
                removeShipsOf(s, p);
                Characters.get(p).crewId = null;
            }
            return 0;
        });
    }

    // ------------------------------------------------------------------ the advancements as data

    private static void advancementData(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            int files = s.getResourceManager().listResources("advancement", rl -> rl.getNamespace().equals(GrandLine.MODID)
                    && rl.getPath().startsWith("advancement/journey/") && rl.getPath().endsWith(".json")).size();
            List<AdvancementHolder> mine = new ArrayList<>();
            for (AdvancementHolder h : s.getAdvancements().getAllAdvancements())
                if (h.id().getNamespace().equals(GrandLine.MODID) && h.id().getPath().startsWith("journey/")) mine.add(h);
            AdvancementHolder root = s.getAdvancements().get(GrandLine.id("journey/root"));
            String lang = resource("/assets/grandline/lang/en_us.json");
            Set<String> fired = Journey.allIds(), used = new TreeSet<>();
            List<String> problems = new ArrayList<>();
            for (AdvancementHolder h : mine) {
                var a = h.value();
                String id = h.id().getPath();
                if (a.display().isEmpty()) problems.add(id + ": no display");
                else {
                    var d = a.display().get();
                    for (Component c : List.of(d.getTitle(), d.getDescription()))
                        if (!(c.getContents() instanceof TranslatableContents tc) || lang == null || !lang.contains("\"" + tc.getKey() + "\"")) problems.add(id + ": untranslated " + flat(c));
                    if (d.getIcon().isEmpty()) problems.add(id + ": no icon");
                    else if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(d.getIcon().getItem()).getNamespace().equals(GrandLine.MODID)) problems.add(id + ": icon not a mod item");
                }
                AdvancementNode node = s.getAdvancements().tree().get(h);
                if (node == null || root == null || node.root().holder() != root) problems.add(id + ": not in the journey tab");
                for (var c : a.criteria().values()) {
                    if (c.triggerInstance() instanceof MilestoneTrigger.Instance mi) {
                        used.add(mi.id());
                        if (!fired.contains(mi.id())) problems.add(id + ": milestone '" + mi.id() + "' is never fired");
                    } else problems.add(id + ": criterion is not grandline:milestone");
                }
            }
            Set<String> unused = new TreeSet<>(fired);
            unused.removeAll(used);
            if (!unused.isEmpty()) problems.add("fired but no advancement: " + unused);
            check("all journey advancement JSON loads, translated, one tab, only the milestone trigger", files >= 70 && mine.size() == files && problems.isEmpty(),
                    mine.size() + " loaded of " + files + " files, " + used.size() + " milestone ids" + (problems.isEmpty() ? "" : "; " + problems));
            ResourceLocation bg = GrandLine.id("textures/gui/advancements/backgrounds/sea.png");
            boolean bgOk = root != null && root.value().display().isPresent() && root.value().display().get().getBackground().equals(Optional.of(bg))
                    && JourneyTests.class.getResource("/assets/grandline/textures/gui/advancements/backgrounds/sea.png") != null;
            check("the journey tab has its nautical background", bgOk, String.valueOf(root == null ? null : root.value().display().map(d -> d.getBackground()).orElse(null)));
            // the snails: model, texture, name; recipes for the craftable three; the chat glyph font
            List<String> miss = new ArrayList<>();
            for (var it : JourneyRegistry.SNAILS) {
                String n = it.getId().getPath();
                if (resource("/assets/grandline/models/item/" + n + ".json") == null) miss.add(n + " model");
                if (JourneyTests.class.getResource("/assets/grandline/textures/item/" + n + ".png") == null) miss.add(n + " texture");
                if (lang == null || !lang.contains("\"item.grandline." + n + "\"")) miss.add(n + " name");
            }
            for (String r : List.of("den_den_mushi", "crew_den_den_mushi", "baby_den_den_mushi"))
                if (s.getRecipeManager().byKey(GrandLine.id(r)).isEmpty()) miss.add(r + " recipe");
            if (s.getRecipeManager().byKey(GrandLine.id("black_den_den_mushi")).isPresent()) miss.add("black snail must not be craftable");
            String font = resource("/assets/grandline/font/den_den.json");
            if (font == null || JourneyTests.class.getResource("/assets/grandline/textures/font/den_den.png") == null) miss.add("snail font");
            boolean sold = JourneyRegistry.shopExtras("shopkeeper").stream().anyMatch(o -> o.item().equals("grandline:den_den_mushi"));
            check("Den Den Mushi items: models, textures, names, recipes (black one not craftable), chat glyph font, sold in shops", miss.isEmpty() && sold,
                    JourneyRegistry.SNAILS.size() + " snails" + (miss.isEmpty() ? "" : ", missing: " + miss) + ", sold=" + sold);
            return 0;
        });
    }

    // ------------------------------------------------------------------ the real hooks

    private static void hooks(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            create(j, "Journey Tester", "human", "pirate");
            boolean root = Journey.done(j, "root"), races = Journey.done(j, "races"), human = Journey.done(j, "race_human");
            check("making a character grants the journey root, Born of the Sea and the race", root && races && human && !Journey.done(j, "first_island"),
                    "root=" + root + " races=" + races + " race_human=" + human);
            return 0;
        });
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            WorldPlan p = WorldPlan.of(s);
            stand(j, p.byId("orange_town"));
            Navigation.tick(j, d);
            boolean east = Journey.done(j, "first_island") && Journey.done(j, "east_blue") && criterion(j, "four_blues", "east_blue") && !Journey.done(j, "four_blues");
            stand(j, p.byId("alabasta"));
            Navigation.tick(j, d);
            check("discovering islands (Navigation) grants Landfall, the Blue, Paradise and its islands",
                    east && Journey.done(j, "paradise") && Journey.done(j, "alabasta") && !Journey.done(j, "water_7"),
                    "discovered " + d.discoveredIslands + " " + missing(j, "first_island", "east_blue", "paradise", "alabasta") + " four_blues/east=" + criterion(j, "four_blues", "east_blue"));
            return 0;
        });
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            DevilFruit fruit = null;
            for (DevilFruit f : DevilFruits.available())
                if (FruitWorldData.get(s).claimSpecific(f.id)) {
                    fruit = f;
                    break;
                }
            if (fruit == null) {
                check("eating a fruit, mastering it and the Awakening Trial grant the fruit milestones", false, "no free fruit in the ledger");
                return 0;
            }
            FruitManager.eat(j, fruit);
            boolean ate = Journey.done(j, "fruit_eat");
            String key = "fruit/" + fruit.id;
            d.masteryXp.put(key, 39_000); // level 49
            Hits.train(j, key, 2_000);
            boolean master = Journey.done(j, "fruit_master");
            d.flags.add(Awakening.TRIAL_FLAG);
            Awakening.check(j, d);
            check("eating a fruit, mastering it and the Awakening Trial grant the fruit milestones", ate && master && Journey.done(j, "fruit_awaken"),
                    fruit.id + " eat=" + ate + " mastery " + d.mastery(key) + " master=" + master + " awakened=" + d.fruitAwakened + " " + missing(j, "fruit_awaken"));
            FruitWorldData.get(s).release(s, fruit.id, j.getUUID());
            d.fruit = null;
            d.fruitAwakened = false;
            d.flags.remove(Awakening.TRIAL_FLAG);
            return 0;
        });
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            d.level = 30;
            d.conquerorTrait = true;
            d.flags.add("survived_knockdown");
            d.masteryXp.put("combat/meditation", 2_000);
            d.masteryXp.put(Haki.OBS, 57_000);
            d.masteryXp.put(Haki.ARM, 77_000);
            d.masteryXp.put(Haki.COQ, 26_000);
            Haki.tick(j, d);
            String m = missing(j, "haki_observation", "haki_armament", "haki_conqueror", "haki_future_sight", "haki_emission", "haki_internal", "haki_infusion");
            check("Haki unlocks grant the three colours and the advanced forms", m.isEmpty(), m.isEmpty() ? "all seven" : m);
            return 0;
        });
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            Island base = BusterCall.marineBaseNear(j);
            Bounties.raise(j, d, 12_000_000, Component.literal("test"));
            boolean poster = Journey.done(j, "bounty_poster") && Journey.done(j, "bounty_10m") && !Journey.done(j, "bounty_100m");
            Bounties.raise(j, d, 150_000_000, Component.literal("test"));
            boolean hundred = Journey.done(j, "bounty_100m") && !Journey.done(j, "bounty_1b");
            Bounties.raise(j, d, 1_000_000_000, Component.literal("test"));
            check("bounty thresholds (Bounties.raise) grant the poster, 10M, 100M and 1B in turn", poster && hundred && Journey.done(j, "bounty_1b"),
                    "bounty " + d.bounty + " poster/10M=" + poster + " 100M=" + hundred + " 1B=" + Journey.done(j, "bounty_1b") + " (marine base near: " + (base == null ? "none" : base.id) + ")");
            check("a bounty raise far from any Marine base brings no Buster Call", BusterCall.activeFor(j) == null && base == null, "");
            return 0;
        });
        // the shipwright, a prize and a Sea King at a docked island (the Buster Call is tested here later too)
        steps.add(s -> {
            ServerLevel l = s.overworld();
            DOCK[0] = dockIsland(WorldPlan.of(s));
            if (DOCK[0] == null) return 0;
            Settlement st = DOCK[0].settlement();
            loadNow(l, st.dockTipX(), st.dockTipZ(), 9);
            TIP[0] = dockTip(l, DOCK[0]);
            return 20;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            if (DOCK[0] == null) {
                check("the shipwright (buy, upgrade, customise) grants the ship milestones", false, "no docked island found");
                return 0;
            }
            j.moveTo(TIP[0].x, TIP[0].y, TIP[0].z);
            HumanoidNpc wright = npc(l, "shipwright", 2, TIP[0]);
            d.beli = 5_000_000;
            CompoundTag t = new CompoundTag();
            t.putInt("npc", wright.getId());
            t.putString("op", "buy");
            t.putString("cls", "sloop");
            Shipyard.action(j, t);
            int owned = ShipRegistry.get(s).ownedBy(j.getUUID()).size();
            boolean bought = Journey.done(j, "ship_buy");
            t.putString("op", "upgrade");
            t.putString("upgrade", "reinforced_hull");
            Shipyard.action(j, t);
            boolean upgraded = Journey.done(j, "ship_upgrade");
            t.putString("op", "customise");
            t.putString("figurehead", "lion");
            t.putString("emblem", "star");
            t.putInt("emblemc", 0xE8C040);
            t.putInt("flagc", 0x9E2222);
            t.putInt("sail", 0xC03030);
            t.putInt("hullc", 0x202020);
            Shipyard.action(j, t);
            check("the shipwright (buy, upgrade, customise) grants the ship milestones", owned == 1 && bought && upgraded && Journey.done(j, "ship_customise"),
                    DOCK[0].id + ": ships owned " + owned + " buy=" + bought + " upgrade=" + upgraded + " customise=" + Journey.done(j, "ship_customise"));
            wright.discard();
            removeShipsOf(s, j);
            // a prize: an NPC sloop whose crew is gone
            Vec3 water = Shipyard.findWaterPublic(l, BlockPos.containing(TIP[0]));
            ShipEntity prize = water == null ? null : SeaEncounters.spawnShip(l, water, 0, ShipClass.SLOOP, LivingSea.PIRATE, 1, "Test Prize");
            boolean took = false;
            if (prize != null) {
                for (UUID u : new ArrayList<>(NavalCombat.npcCrew(prize))) if (l.getEntity(u) != null) l.getEntity(u).discard();
                took = NavalCombat.capture(prize, j);
            }
            // a Sea King
            SeaKing king = water == null ? null : SeaKingSpawner.spawn(l, water.x + 30, water.z, 0, 0, 1, 25, WorldPlan.SEA_LEVEL - 3);
            if (king != null) {
                king.die(j.damageSources().playerAttack(j));
                king.discard();
            }
            check("taking a prize and slaying a Sea King grant their milestones", took && Journey.done(j, "ship_prize") && king != null && Journey.done(j, "sea_king"),
                    "prize spawned=" + (prize != null) + " taken=" + took + " sea king=" + (king != null) + " " + missing(j, "ship_prize", "sea_king"));
            if (prize != null) scrap(l, prize);
            removeShipsOf(s, j);
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            NotableCharacters.Notable pick = null;
            for (NotableCharacters.Notable n : NotableCharacters.all()) {
                if (!n.recruitable() || n.id().equals("hilde") || n.id().equals("petra") || NotableState.get(s).status(n.id()) != NotableState.Status.UNSPAWNED) continue;
                if (!Journey.CREW_POSTS.contains(n.crewRole())) continue;
                pick = n;
                if (n.crewRole().equals("navigator")) break;
            }
            if (pick == null || TIP[0] == null) {
                check("recruiting (CrewManager) grants First Mate and the crew post", false, "no free recruitable notable or dock");
                return 0;
            }
            HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
            BlockPos at = BlockPos.containing(TIP[0]);
            n.moveTo(TIP[0].x + 1, TIP[0].y, TIP[0].z, 0, 0);
            n.configure(pick.role(), pick.tier(), pick.island(), at, "", l.random);
            n.makeNotable(pick.id(), null, pick.obs(), pick.arm(), pick.coq(), pick.style());
            l.addFreshEntity(n);
            boolean joined = CrewManager.recruit(j, n);
            check("recruiting (CrewManager) grants First Mate and the crew post", joined && Journey.done(j, "crew_recruit") && criterion(j, "crew_full", pick.crewRole().equals("swordswoman") ? "swordswoman" : pick.crewRole()),
                    pick.id() + " (" + pick.crewRole() + ") joined=" + joined + " " + missing(j, "crew_recruit"));
            CrewManager.disband(s, d.characterId);
            if (!n.isRemoved()) n.discard();
            NotableState.get(s).set(pick.id(), NotableState.Status.UNSPAWNED, null, "", l.getGameTime());
            return 0;
        });
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            d.completedQuests.add("logue_2");
            d.discoveredIslands.add("twin_capes");
            boolean started = Quests.start(j, "logue_3");
            boolean part1 = Journey.done(j, "story_part1");
            d.completedQuests.add("laughtale_1");
            boolean finale = Quests.start(j, "laughtale_2");
            QuestEvents.onPoneglyph(j, BlockPos.ZERO);
            check("finishing story quests (Quests) grants the Part and the ending", started && part1 && finale && Journey.done(j, "ending_saltfire_kindled"),
                    "logue_3 started=" + started + " part1=" + part1 + " laughtale_2 started=" + finale + " done=" + d.completedQuests.contains("laughtale_2") + " " + missing(j, "ending_saltfire_kindled"));
            return 0;
        });
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            SeaFishing.onCatch(j, List.of(new ItemStack(ActivityItems.HARBOR_BREAM.get())));
            boolean small = Journey.done(j, "giant_catch");
            SeaFishing.onCatch(j, List.of(new ItemStack(ActivityItems.BEHEMOTH_CARP.get())));
            j.containerMenu = j.inventoryMenu;
            Meals.onCooked(new ItemStack(ActivityItems.GRILLED_FISH.get()), j);
            boolean plain = Journey.done(j, "signature_dish");
            Meals.onCooked(new ItemStack(ActivityItems.SALTFIRE_CURRY.get()), j);
            TreasureTarget t = TreasureMaps.pick(s, j.blockPosition(), 4242L, j, false);
            int solved = 0;
            if (t != null) {
                ItemStack map = new ItemStack(ActivityItems.TREASURE_MAP.get());
                map.set(ActivityRegistry.TREASURE.get(), t);
                j.getInventory().setItem(21, map);
                solved = TreasureMaps.opened(j, new BlockPos(t.x(), t.y(), t.z()));
                j.getInventory().setItem(21, ItemStack.EMPTY);
            }
            check("a giant catch, a signature dish and a solved treasure map grant their milestones (a small fish and a plain dish do not)",
                    !small && !plain && Journey.done(j, "giant_catch") && Journey.done(j, "signature_dish") && solved > 0 && Journey.done(j, "treasure_map"),
                    "small fish=" + small + " plain dish=" + plain + " map solved=" + solved + " " + missing(j, "giant_catch", "signature_dish", "treasure_map"));
            // a plain fake player (no advancement store) is ignored without harm
            FakePlayer plainFake = SelfTest.fake(s);
            Journey.milestone(plainFake, Journey.SEA_KING);
            check("milestones for a plain fake player are a harmless no-op", !Journey.done(plainFake, "sea_king"), "");
            return 0;
        });
    }

    // ------------------------------------------------------------------ permadeath keeps them; three races make Many Lives

    private static void permadeathAndRaces(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ");
            CharacterData d = Characters.get(j);
            List<String> before = new ArrayList<>();
            for (String n : List.of("root", "bounty_1b", "fruit_awaken", "haki_conqueror", "ship_buy", "story_part1")) if (Journey.done(j, n)) before.add(n);
            d.archiveAndReset(); // permadeath: the character is gone
            boolean kept = true;
            for (String n : before) kept &= Journey.done(j, n);
            create(j, "Second Life", "fishman", "pirate");
            boolean fish = Journey.done(j, "race_fishman") && !Journey.done(j, "many_lives");
            d.archiveAndReset();
            create(j, "Third Life", "giant", "pirate");
            check("advancements belong to the account: a permadeath keeps them, and a third race makes Many Lives",
                    before.size() == 6 && kept && fish && Journey.done(j, "race_giant") && Journey.done(j, "many_lives"),
                    "kept " + before + "=" + kept + " generation " + Characters.get(j).generation + " fishman=" + fish + " " + missing(j, "race_giant", "many_lives"));
            return 0;
        });
    }

    // ------------------------------------------------------------------ retroactive credit on login

    private static void retroactive(Deque<SelfTest.Step> steps) {
        String[] expected = {"first_island", "east_blue", "paradise", "alabasta", "water_7", "sabaody", "reverse_mountain", "holy_land", "coating",
                "fishman_island", "new_world", "laugh_tale", "skypiea", "fruit_eat", "fruit_master", "fruit_awaken", "haki_observation", "haki_armament",
                "haki_conqueror", "haki_future_sight", "haki_emission", "haki_internal", "haki_infusion", "bounty_poster", "bounty_10m", "bounty_100m",
                "bounty_1b", "story_part1", "story_part2", "story_part3", "story_part4", "ending_true_justice", "ship_buy", "ship_upgrade",
                "ship_customise", "set_sail", "race_human", "race_mink"};
        steps.add(s -> {
            ServerLevel l = s.overworld();
            AccountPlayer r = account(s, "JourneyRetro");
            create(r, "Old Hand", "human", "pirate");
            CharacterData d = Characters.get(r);
            int before = 0;
            for (String n : expected) if (Journey.done(r, n)) before++;
            // a character from before the journey existed: its saved data already shows a long voyage
            d.discoveredIslands.addAll(List.of("dawn_island", "alabasta", "water_7", "sabaody_archipelago", "mary_geoise", "fishman_island", "dressrosa",
                    "laugh_tale", "angel_island"));
            d.fruit = "mera";
            d.masteryXp.put("fruit/mera", 40_000);
            d.fruitAwakened = true;
            d.observationUnlocked = d.armamentUnlocked = d.conquerorAwakened = d.futureSight = d.emission = d.internalDestruction = d.conquerorInfusion = true;
            d.bounty = 1_200_000_000L;
            d.completedQuests.addAll(List.of("logue_3", "sabaody_3", "war_3.marine", "lodestar_1", "laughtale_2.marine"));
            d.flags.add("ending_true_justice");
            d.fallenCharacters.add("Salty Pete (mink, Lv 12, bounty 3000000)");
            ShipEntity boat = ModEntities.SHIPS.get(ShipClass.ROWBOAT).get().create(l);
            boat.owner = r.getUUID();
            boat.ownerName = d.name;
            boat.moveTo(r.getX(), WorldPlan.SEA_LEVEL + 1, r.getZ());
            boat.addUpgrade(SpecialRoutes.COATING);
            boat.setLook(new ShipLook("lion", "star", 0xE8C040, 0x9E2222));
            ShipRegistry.get(s).register(boat);
            ShipRegistry.get(s).update(boat);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(r)); // the real login event
            String m = missing(r, expected);
            boolean notEarned = !Journey.done(r, "giant_catch") && !Journey.done(r, "den_call") && !Journey.done(r, "four_blues") && !Journey.done(r, "crew_recruit")
                    && !Journey.done(r, "ending_dawn") && !Journey.done(r, "many_lives");
            check("logging in credits a character's saved data retroactively (islands, fruit, Haki, bounty, saga, ships, earlier lives)",
                    before <= 1 && m.isEmpty() && notEarned, expected.length + " expected, " + before + " before login; " + (m.isEmpty() ? "all granted" : m)
                            + "; nothing unearned=" + notEarned);
            ShipRegistry.get(s).remove(boat.shipId);
            d.fruit = null;
            return 0;
        });
    }

    // ------------------------------------------------------------------ Den Den Mushi

    private static void denDenMushi(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            AccountPlayer a = account(s, "SnailA"), b = account(s, "SnailB"), c = account(s, "SnailC");
            create(a, "Ann Caller", "human", "pirate");
            create(b, "Ben Answer", "human", "pirate");
            create(c, "Cal Outsider", "human", "pirate");
            for (AccountPlayer p : List.of(a, b, c)) p.getInventory().clearContent();
            a.getInventory().add(DenDenMushi.stack(DenDenMushiItem.Kind.PERSONAL));
            b.getInventory().add(DenDenMushi.stack(DenDenMushiItem.Kind.PERSONAL));
            INBOX.clear();
            var none = DenDenMushi.call(a, c);
            var ring = DenDenMushi.call(a, b);
            boolean ringing = ring == DenDenMushi.Result.RINGING && DenDenMushi.callOf(b) != null && count(b, "grandline.den.ringing") == 1;
            boolean declined = DenDenMushi.decline(b);
            boolean ended = DenDenMushi.callOf(a) == null && DenDenMushi.callOf(b) == null && count(a, "grandline.den.declined") == 1;
            check("a Den Den Mushi call rings only someone carrying a snail, and a declined call ends", none == DenDenMushi.Result.NO_SNAIL_THERE && ringing && declined && ended,
                    "to snail-less=" + none + " ring=" + ring + " declined=" + declined + " ended=" + ended);
            INBOX.clear();
            DenDenMushi.call(a, b);
            boolean accepted = DenDenMushi.accept(b);
            var call = DenDenMushi.callOf(a);
            ServerChatEvent chat = new ServerChatEvent(a, "ahoy there", Component.literal("ahoy there"));
            NeoForge.EVENT_BUS.post(chat);
            boolean said = DenDenMushi.say(b, "aye aye");
            ServerChatEvent open = new ServerChatEvent(c, "hello all", Component.literal("hello all"));
            NeoForge.EVENT_BUS.post(open);
            boolean routed = chat.isCanceled() && count(b, "ahoy there") == 1 && count(a, "aye aye") == 1 && count(c, "ahoy") == 0 && count(c, "aye") == 0 && !open.isCanceled();
            check("an accepted call connects both and chat goes only down the line", accepted && call != null && call.connected && routed
                            && Journey.done(a, "den_call") && Journey.done(b, "den_call"),
                    "accepted=" + accepted + " chat cancelled=" + chat.isCanceled() + " B heard=" + count(b, "ahoy there") + " A heard=" + count(a, "aye aye")
                            + " C heard=" + (count(c, "ahoy") + count(c, "aye")) + " C's own chat stays public=" + !open.isCanceled() + " " + missing(a, "den_call"));
            boolean hung = DenDenMushi.hangUp(a);
            boolean after = !DenDenMushi.say(a, "anyone?") && DenDenMushi.callOf(b) == null;
            check("hanging up ends the call for both", hung && after && count(b, "grandline.den.ended") == 1, "hung=" + hung + " silent after=" + after);
            // the crew snail
            INBOX.clear();
            c.getInventory().add(DenDenMushi.stack(DenDenMushiItem.Kind.PERSONAL));
            a.getInventory().add(DenDenMushi.stack(DenDenMushiItem.Kind.CREW));
            boolean invited = DenDenMushi.invite(a, b), joined = DenDenMushi.join(b);
            int heard = DenDenMushi.crewCall(a, "all hands on deck", List.of(a, b, c));
            check("the crew snail reaches crewmates (and only them)", invited && joined && heard == 1 && count(b, "all hands on deck") == 1
                            && count(c, "all hands on deck") == 0 && Journey.done(a, "den_crew") && Characters.get(b).crewId.equals(Characters.get(a).crewId),
                    "invited=" + invited + " joined=" + joined + " heard by " + heard + " B=" + count(b, "all hands") + " C=" + count(c, "all hands"));
            // the baby snail
            ItemStack babies = DenDenMushi.stack(DenDenMushiItem.Kind.BABY);
            babies.setCount(2);
            a.getInventory().add(babies);
            boolean sent = DenDenMushi.baby(a, c, "meet me at the dock");
            int left = 0;
            for (int i = 0; i < a.getInventory().getContainerSize(); i++)
                if (a.getInventory().getItem(i).is(JourneyRegistry.BABY_DEN_DEN_MUSHI.get())) left += a.getInventory().getItem(i).getCount();
            check("a Baby Den Den carries one message and is used up", sent && count(c, "meet me at the dock") == 1 && left == 1, "sent=" + sent + " left=" + left);
            // a call nobody answers rings out
            INBOX.clear();
            DenDenMushi.call(a, b);
            return (int) DenDenMushi.RING_TICKS + 5;
        });
        steps.add(s -> {
            AccountPlayer a = account(s, "SnailA"), b = account(s, "SnailB");
            check("an unanswered call rings out (missed call on both sides)", DenDenMushi.callOf(a) == null && DenDenMushi.callOf(b) == null
                            && count(a, "grandline.den.missed") == 1 && count(b, "grandline.den.missed_callee") == 1,
                    "caller told=" + count(a, "grandline.den.missed") + " callee told=" + count(b, "grandline.den.missed_callee"));
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            AccountPlayer a = account(s, "SnailA");
            if (TIP[0] == null) {
                check("a black snail overhears a Marine ship nearby", false, "no dock");
                return 0;
            }
            a.moveTo(TIP[0].x, TIP[0].y, TIP[0].z);
            Vec3 water = Shipyard.findWaterPublic(l, BlockPos.containing(TIP[0]).offset(20, 0, 20));
            ShipEntity patrol = water == null ? null : SeaEncounters.spawnShip(l, water, 90, ShipClass.CARAVEL, LivingSea.MARINE, 2, "Marine Patrol Kestrel");
            INBOX.clear();
            List<Component> deaf = DenDenMushi.eavesdrop(a);
            a.getInventory().add(DenDenMushi.stack(DenDenMushiItem.Kind.BLACK));
            List<Component> heard = DenDenMushi.eavesdrop(a);
            boolean named = heard.stream().anyMatch(c -> flat(c).contains("Marine Patrol Kestrel"));
            check("a black snail overhears a Marine ship nearby (none without one)", patrol != null && deaf.isEmpty() && named && Journey.done(a, "den_eavesdrop"),
                    "patrol=" + (patrol != null) + " without=" + deaf.size() + " with=" + heard.stream().map(JourneyTests::flat).toList());
            check("black snails are taken from Marine officers only", DenDenMushi.blackSnailChance("vice_admiral") > 0.3 && DenDenMushi.blackSnailChance("marine_officer") > 0
                    && DenDenMushi.blackSnailChance("pirate") == 0 && DenDenMushi.blackSnailChance("marine") == 0, "");
            if (patrol != null) scrap(l, patrol);
            return 0;
        });
    }

    // ------------------------------------------------------------------ Buster Call: when it may be issued

    private static void busterCallRules(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = s.overworld();
            AccountPlayer t = account(s, "BusterLow");
            create(t, "Small Fry", "human", "pirate");
            CharacterData d = Characters.get(t);
            if (TIP[0] != null) t.moveTo(TIP[0].x, TIP[0].y, TIP[0].z);
            BusterCall.Rules force = new BusterCall.Rules();
            force.force = true;
            force.warnTicks = 2000;
            d.bounty = GLConfig.BUSTER_CALL_BOUNTY.get() - 1;
            boolean below = BusterCall.report(t, "test", force) == null;
            d.bounty = GLConfig.BUSTER_CALL_BOUNTY.get() + 100_000_000L;
            GLConfig.BUSTER_CALL.set(false);
            boolean disabled = BusterCall.report(t, "test", force) == null;
            GLConfig.BUSTER_CALL.set(true);
            check("a Buster Call needs the bounty threshold and can be disabled in the config", below && disabled, "below threshold refused=" + below + " disabled refused=" + disabled);
            // a crime seen from a Marine base (the real hook: Bounties.raise)
            AccountPlayer c = account(s, "BusterCrime");
            create(c, "Harbour Thief", "human", "pirate");
            Island shells = WorldPlan.of(s).byId("shells_town");
            c.moveTo(shells.x + 0.5, 80, shells.z + 0.5);
            Characters.get(c).bounty = GLConfig.BUSTER_CALL_BOUNTY.get() + 1;
            BusterCall.hookRules = force;
            Bounties.raise(c, Characters.get(c), 1_000_000, Component.literal("theft"));
            BusterCall.Call crime = BusterCall.activeFor(c);
            boolean again = BusterCall.report(t, "test", force) == null; // one call at a time
            boolean cancelled = BusterCall.cancel(s, c);
            check("a crime near a Marine base reports the player and may bring a Buster Call (one at a time)", crime != null && crime.reason.startsWith("crime:") && again && cancelled,
                    "call=" + (crime == null ? null : crime.reason) + " second refused=" + again + " cancelled=" + cancelled);
            // a Vice Admiral who fought the player and lived reports them; a dead one tells no tales
            AccountPlayer w = account(s, "BusterWitness"), dead = account(s, "BusterSilenced");
            create(w, "Admiral's Foe", "human", "pirate");
            create(dead, "Silent Blade", "human", "pirate");
            for (AccountPlayer p : List.of(w, dead)) {
                Characters.get(p).bounty = GLConfig.BUSTER_CALL_BOUNTY.get() + 1;
                if (TIP[0] != null) p.moveTo(TIP[0].x, TIP[0].y, TIP[0].z);
            }
            Vec3 at = TIP[0] == null ? w.position() : TIP[0];
            HumanoidNpc va = npc(l, "vice_admiral", 8, at.add(2, 0, 0)), va2 = npc(l, "vice_admiral", 8, at.add(-2, 0, 0));
            va.hurt(w.damageSources().playerAttack(w), 1f);
            va2.hurt(dead.damageSources().playerAttack(dead), 1f);
            boolean seen = BusterCall.witnessed(va.getUUID()) && BusterCall.witnessed(va2.getUUID());
            va2.discard(); // beaten for good
            int reports = BusterCall.checkWitnesses(s, l.getGameTime() + BusterCall.WITNESS_QUIET + 1);
            BusterCall.Call byWitness = BusterCall.activeFor(w);
            boolean silent = BusterCall.activeFor(dead) == null;
            check("a Vice Admiral who fought the player and lived reports them (a beaten one does not)", seen && reports == 1 && byWitness != null
                            && byWitness.reason.equals("vice_admiral") && silent,
                    "witnessed=" + seen + " reports=" + reports + " call=" + (byWitness == null ? null : byWitness.reason) + " silenced=" + silent);
            BusterCall.cancel(s, w);
            va.discard();
            BusterCall.hookRules = null;
            return 0;
        });
    }

    // ------------------------------------------------------------------ Buster Call: a full call

    private static final BusterCall.Call[] CALL = new BusterCall.Call[1];
    private static final int[] WAITED = new int[1], ROOM = new int[3];
    private static final Map<UUID, double[]> DIST = new HashMap<>();
    private static final Map<BlockPos, BlockState> TOWN = new HashMap<>();
    private static final Entity[] BYSTANDERS = new Entity[2];

    private static void busterCallRun(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = s.overworld();
            if (TIP[0] == null) {
                check("a Buster Call gives warning first", false, "no docked island");
                return 0;
            }
            AccountPlayer t = account(s, "BusterTarget");
            create(t, "Most Wanted", "human", "pirate");
            CharacterData d = Characters.get(t);
            d.bounty = GLConfig.BUSTER_CALL_BOUNTY.get() + 200_000_000L;
            t.moveTo(TIP[0].x, TIP[0].y, TIP[0].z);
            // town blocks to watch: around the dock tip and the plots of the town near it
            TOWN.clear();
            List<BlockPos> watch = new ArrayList<>(List.of(BlockPos.containing(TIP[0])));
            for (Plot p : DOCK[0].settlement().plots)
                if (watch.size() < 5 && Math.hypot(p.centerX() - TIP[0].x, p.centerZ() - TIP[0].z) < 70)
                    watch.add(new BlockPos(p.centerX(), ground(l, p.centerX(), p.centerZ()), p.centerZ()));
            for (BlockPos c : watch)
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-5, -4, -5), c.offset(5, 6, 5))) TOWN.put(q.immutable(), l.getBlockState(q));
            ROOM[0] = SeaEncounters.cap(l.players().size()) - SeaEncounters.countNpcShips(l);
            ROOM[1] = SeaEncounters.cap(l.players().size());
            BusterCall.Rules r = new BusterCall.Rules();
            r.force = true;
            r.warnTicks = 60;
            r.bombardTicks = 300;
            r.ships = 6;
            r.spawnDist = 125;
            r.withdrawTicks = 80;
            CALL[0] = BusterCall.report(t, "test", r);
            WAITED[0] = 0;
            DIST.clear();
            check("a Buster Call is issued past the threshold with a report", CALL[0] != null, "bounty " + d.bounty + " room under the cap " + ROOM[0] + "/" + ROOM[1]);
            return 20;
        });
        steps.add(s -> {
            BusterCall.Call c = CALL[0];
            if (c == null) return 0;
            check("a Buster Call gives warning first (no ships during the countdown)", c.phase == BusterCall.Phase.WARNING && c.ships.isEmpty()
                    && c.warnUntil - c.issuedAt == 60, "phase " + c.phase + " ships " + c.ships.size() + " warning " + (c.warnUntil - c.issuedAt) + " ticks");
            return 0;
        });
        steps.add(s -> {
            BusterCall.Call c = CALL[0];
            if (c == null) return 0;
            if (c.phase == BusterCall.Phase.WARNING && ++WAITED[0] < 1200) return -1;
            ServerLevel l = s.overworld();
            boolean admirals = true;
            for (ShipEntity sh : c.ships) {
                boolean va = false;
                for (UUID u : NavalCombat.npcCrew(sh)) if (l.getEntity(u) instanceof GLNpc m && m.role().equals("vice_admiral")) va = true;
                admirals &= va && sh.getTags().contains(BusterCall.TAG);
                DIST.put(sh.getUUID(), new double[]{Math.hypot(sh.getX() - c.focus.x, sh.getZ() - c.focus.z), Double.MAX_VALUE});
            }
            int count = SeaEncounters.countNpcShips(l);
            check("the Buster Call fleet sails within the NPC ship cap, a Vice Admiral aboard each", !c.ships.isEmpty() && c.ships.size() <= Math.max(0, ROOM[0])
                            && c.ships.size() <= 6 && count <= ROOM[1] && admirals,
                    c.ships.size() + " warships (asked 6, room " + ROOM[0] + ", cap " + ROOM[1] + "), NPC ships now " + count + ", vice admirals=" + admirals + " after "
                            + WAITED[0] + " ticks, phase " + c.phase);
            // bystanders on the dock: a pirate, and the target's moored rowboat
            Vec3 tip = TIP[0];
            BYSTANDERS[0] = npc(l, "pirate", 2, tip.add(1.5, 0, 1.5));
            Vec3 water = Shipyard.findWaterPublic(l, BlockPos.containing(tip));
            ShipEntity boat = ModEntities.SHIPS.get(ShipClass.ROWBOAT).get().create(l);
            if (water != null && boat != null) {
                boat.moveTo(water.x, water.y, water.z);
                boat.owner = c.target.getUUID();
                boat.setHull(boat.cls.hull);
                l.addFreshEntity(boat);
                BYSTANDERS[1] = boat;
            }
            WAITED[0] = 0;
            return 0;
        });
        steps.add(s -> {
            BusterCall.Call c = CALL[0];
            if (c == null) return 0;
            if (s.overworld().getGameTime() % 10 == 0)
                for (ShipEntity sh : c.ships) {
                    double[] dd = DIST.get(sh.getUUID());
                    if (dd != null && !sh.isRemoved()) dd[1] = Math.min(dd[1], Math.hypot(sh.getX() - c.focus.x, sh.getZ() - c.focus.z));
                }
            boolean over = c.phase == BusterCall.Phase.WITHDRAW || c.phase == BusterCall.Phase.DONE;
            if (!over && ++WAITED[0] < 20 * 120) return -1;
            double start = 0, closest = 0;
            for (double[] dd : DIST.values()) {
                start += dd[0];
                closest += dd[1];
            }
            int n = Math.max(1, DIST.size());
            check("the warships converge on the target; reinforcements stay within the cap", closest / n < start / n - 15 && c.spawned <= Math.max(0, ROOM[0]),
                    String.format("mean distance %.0f -> closest %.0f over the first %d ships; %d of %d wanted in all (room %d)", start / n, closest / n, DIST.size(),
                            c.spawned, c.wanted, ROOM[0]));
            check("the fleet bombards the target (shells fired and bursting)", c.shells > 0 && c.impacts > 0 && c.bombardFrom > 0,
                    c.shells + " shells, " + c.impacts + " bursts, hurt " + c.hurt + " creatures and " + c.hulls + " hulls (pirate bystander hurt="
                            + (BYSTANDERS[0] instanceof GLNpc g && (g.getHealth() < g.getMaxHealth() || !g.isAlive())) + ", rowboat hull="
                            + (BYSTANDERS[1] instanceof ShipEntity b ? (int) b.hull() + "/" + b.cls.hull : "?") + ")");
            AccountPlayer t = account(s, "BusterTarget");
            check("the Buster Call ends; surviving it is an advancement and raises the bounty", over && c.survived && c.outcome.equals("survived")
                            && Journey.done(t, "buster_call_survived") && Characters.get(t).bounty >= GLConfig.BUSTER_CALL_BOUNTY.get() + 250_000_000L,
                    "phase " + c.phase + " outcome " + c.outcome + " bounty " + Characters.get(t).bounty + " " + missing(t, "buster_call_survived"));
            WAITED[0] = 0;
            return 0;
        });
        steps.add(s -> {
            BusterCall.Call c = CALL[0];
            if (c == null) return 0;
            if (c.phase != BusterCall.Phase.DONE && ++WAITED[0] < 400) return -1;
            ServerLevel l = s.overworld();
            int left = l.getEntitiesOfClass(ShipEntity.class, new AABB(TIP[0], TIP[0]).inflate(400, 60, 400), sh -> sh.getTags().contains(BusterCall.TAG)).size();
            int changed = 0; // a block destroyed or replaced (doors swinging and crops growing are not damage)
            for (var e : TOWN.entrySet()) if (!e.getValue().isAir() && l.getBlockState(e.getKey()).getBlock() != e.getValue().getBlock()) changed++;
            check("the fleet withdraws and is gone; no town block was broken by the bombardment", c.phase == BusterCall.Phase.DONE && left == 0 && changed == 0
                            && !BusterCall.calls().contains(c),
                    "phase " + c.phase + ", warships left " + left + ", town blocks changed " + changed + "/" + TOWN.size());
            for (Entity e : BYSTANDERS) if (e != null && !e.isRemoved()) e.discard();
            return 0;
        });
    }

    // ------------------------------------------------------------------ Buster Call: what a shell does

    private static void shellBursts(Deque<SelfTest.Step> steps) {
        final List<BlockPos> crater = new ArrayList<>();
        final Map<BlockPos, BlockState> before = new HashMap<>();
        steps.add(s -> {
            ServerLevel l = s.overworld();
            if (TIP[0] == null) return 0;
            Vec3 tip = TIP[0];
            HumanoidNpc pirate = npc(l, "pirate", 2, tip.add(0, 0, 0)), marine = npc(l, "marine", 2, tip.add(1, 0, 0));
            float ph = pirate.getHealth(), mh = marine.getHealth();
            BusterCall.Impact im = BusterCall.burst(l, pirate.position(), null, null);
            boolean npcs = pirate.getHealth() < ph && marine.getHealth() == mh;
            Vec3 water = Shipyard.findWaterPublic(l, BlockPos.containing(tip).offset(-10, 0, 10));
            ShipEntity boat = water == null ? null : ModEntities.SHIPS.get(ShipClass.SLOOP).get().create(l);
            float hull = 0;
            BusterCall.Impact onShip = null;
            if (boat != null) {
                boat.moveTo(water.x, water.y, water.z);
                boat.setHull(boat.cls.hull);
                l.addFreshEntity(boat);
                onShip = BusterCall.burst(l, boat.position().add(0, 1, 0), null, null);
                hull = boat.hull();
            }
            check("a shell burst hurts a pirate and a ship but spares a Marine", npcs && im.hurt() >= 1 && boat != null && hull < boat.cls.hull && onShip.hulls() == 1,
                    "pirate " + ph + " -> " + pirate.getHealth() + ", marine " + mh + " -> " + marine.getHealth() + ", sloop hull " + (boat == null ? "?" : hull + "/" + boat.cls.hull));
            pirate.discard();
            marine.discard();
            if (boat != null) boat.discard();
            // a burst in town changes no block; one in the wild craters (temporarily)
            Settlement st = DOCK[0].settlement();
            Plot plot = st.plots.isEmpty() ? null : st.plots.get(0);
            BlockPos town = plot == null ? BlockPos.containing(tip) : new BlockPos(plot.centerX(), ground(l, plot.centerX(), plot.centerZ()), plot.centerZ());
            Map<BlockPos, BlockState> snap = new HashMap<>();
            for (BlockPos q : BlockPos.betweenClosed(town.offset(-4, -4, -4), town.offset(4, 4, 4))) snap.put(q.immutable(), l.getBlockState(q));
            BusterCall.Impact inTown = BusterCall.burst(l, Vec3.atCenterOf(town), null, null);
            int changed = 0;
            for (var e : snap.entrySet()) if (!l.getBlockState(e.getKey()).equals(e.getValue())) changed++;
            check("a shell burst in town never changes a block", inTown.blocks() == 0 && changed == 0 && !BusterCall.wild(l, Vec3.atCenterOf(town)),
                    "town at " + town.toShortString() + ": blocks changed " + changed + ", wild=" + BusterCall.wild(l, Vec3.atCenterOf(town)));
            // the wild: island land outside every settlement
            Island isl = DOCK[0];
            BlockPos wildPos = null;
            WorldPlan.Sample smp = new WorldPlan.Sample();
            WorldPlan plan = WorldPlan.of(s);
            search:
            for (double f = 0.35; f < 0.85; f += 0.1)
                for (int k = 0; k < 16; k++) {
                    int x = isl.x + (int) (Math.cos(k * Math.PI / 8) * isl.radius * f), z = isl.z + (int) (Math.sin(k * Math.PI / 8) * isl.radius * f);
                    plan.sample(x, z, smp);
                    if (smp.height < WorldPlan.SEA_LEVEL + 3) continue;
                    BlockPos g = new BlockPos(x, ground(l, x, z), z);
                    if (!BusterCall.wild(l, Vec3.atCenterOf(g)) || !l.getBlockState(g).isAir()) continue; // bare ground (plants keep their column)
                    wildPos = g;
                    break search;
                }
            if (wildPos == null) {
                check("a shell burst in the wild craters it through TerrainChanges, which reverts it", false, "no wild land found on " + isl.id);
                return 0;
            }
            for (BlockPos q : BlockPos.betweenClosed(wildPos.offset(-3, -3, -3), wildPos.offset(3, 2, 3))) before.put(q.immutable(), l.getBlockState(q));
            BusterCall.Impact wild = BusterCall.burst(l, Vec3.atCenterOf(wildPos.below()), null, null);
            for (var e : before.entrySet()) if (!l.getBlockState(e.getKey()).equals(e.getValue())) crater.add(e.getKey());
            int reverted = TerrainChanges.revertAll(s);
            int restored = 0;
            for (BlockPos q : crater) if (l.getBlockState(q).equals(before.get(q))) restored++;
            check("a shell burst in the wild craters it through TerrainChanges, which reverts it", wild.blocks() > 0 && !crater.isEmpty() && restored == crater.size(),
                    "at " + wildPos.toShortString() + ": " + wild.blocks() + " blocks blasted, " + crater.size() + " changed, " + reverted + " reverted, " + restored + " restored");
            return 0;
        });
    }

    // ================================================================== phase 2

    static void phase2(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            AccountPlayer j = account(s, "JourneyJ"); // the same account, its advancements read back from the world's save
            String m = missing(j, "root", "bounty_1b", "fruit_awaken", "haki_infusion", "ship_customise", "race_giant", "many_lives", "treasure_map");
            check("journey advancements persist with the account across a restart", m.isEmpty(), m.isEmpty() ? "all present" : m);
            return 0;
        });
    }

    private JourneyTests() {}
}
