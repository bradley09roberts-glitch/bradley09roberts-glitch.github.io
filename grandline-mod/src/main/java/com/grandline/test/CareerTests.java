package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.career.*;
import com.grandline.dialogue.Dialogues;
import com.grandline.dialogue.Shops;
import com.grandline.journey.Journey;
import com.grandline.navigation.Navigation;
import com.grandline.npc.Bounties;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.NpcOutcomes;
import com.grandline.player.*;
import com.grandline.quest.QuestEvents;
import com.grandline.quest.Quests;
import com.grandline.registry.ModBlocks;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.grandline.test.SelfTest.check;

/**
 * Self-test steps for careers (added to phase 1 by {@link SelfTest}; quick run: GL_SELFTEST=careers).
 * <ul>
 *   <li>Marine: merit from real arrests, a kill, a sea battle won (a pirate sloop sunk through the hull hook) and a path
 *   quest climbs the ranks through the promotion talk (one rank per talk, the officer must be senior enough, the gates
 *   hold: enlistment, sea victories, story Parts, Haki, the Admiral and Fleet Admiral seats); issued kit (coat, jitte,
 *   sword, black snail); rank pay on login once a day; the Marine supply shop (and not for a pirate); a squad call
 *   (members follow, never turn on their leader, cooldown, dismissed); a warship called at sea; a Buster Call request.
 *   Striking Marines and civilians and crimes earn demerits: demotions, then desertion (bounty, "Deserter"); a Marine
 *   whose standing collapses deserts through the existing reputation rule too.</li>
 *   <li>Pirate: Supernova follows bounty, Paradise and deeds (two rival notables beaten: yields); the Government envoy's
 *   letter of marque (Marines stand down, at sea too; duty paid; a crime revokes it, the bounty comes back, no second
 *   letter); Emperor after an Emperor yields; territory claimed ("Emperor of ...", tribute).</li>
 *   <li>Revolutionary: the path quest, three missions (inciting an island in conversation, sabotage by sinking a Marine
 *   ship, liberation at the Sabaody auction house), promotions in person, the safe house hiding them from the law,
 *   contacts' shop and word; bounty hunter: licence by the path quest and arrests, the payout bonus, tracking on a
 *   poster, Gold for a notable head; civilian: charts, a poneglyph, trade, titles with the specialty, discount.</li>
 *   <li>Titles in the display name, the tab list and the visuals; journey milestones for rank-ups; careers survive a
 *   restart (players saved to the world's playerdata in phase 1 and loaded back by the player list in phase 2) and
 *   start over on permadeath (the real Clone event: seats freed).</li>
 * </ul>
 */
final class CareerTests {
    private static final Map<String, JourneyTests.AccountPlayer> PLAYERS = new HashMap<>();
    private static final Map<UUID, List<Component>> INBOX = new HashMap<>();
    private static final List<Component> NEWS = new ArrayList<>();
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final Set<Long> FORCED = new HashSet<>();
    private static final double[] SEA = new double[2];

    static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("gl-career-" + name).getBytes(StandardCharsets.UTF_8)), name);
    }

    static JourneyTests.AccountPlayer account(MinecraftServer s, String name) {
        return PLAYERS.computeIfAbsent(name, n -> new JourneyTests.AccountPlayer(s.overworld(), profile(n)));
    }

    private static CharacterData create(ServerPlayer p, String name, String faction) {
        CompoundTag req = new CompoundTag();
        req.putString("name", name);
        req.putString("race", "human");
        req.putString("faction", faction);
        req.putString("origin", "east");
        req.put("appearance", new Appearance().save());
        CharacterManager.create(p, req);
        return Characters.get(p);
    }

    private static boolean told(ServerPlayer p, String key) {
        for (Component c : INBOX.getOrDefault(p.getUUID(), List.of())) if (JourneyTests.flat(c).contains(key)) return true;
        return false;
    }

    private static long news(String key) {
        return NEWS.stream().filter(c -> JourneyTests.flat(c).contains(key)).count();
    }

    private static String name(ServerPlayer p) {
        p.refreshDisplayName();
        return JourneyTests.flat(p.getDisplayName());
    }

    private static String tab(ServerPlayer p) {
        p.refreshTabListName();
        Component c = p.getTabListDisplayName();
        return c == null ? "" : JourneyTests.flat(c);
    }

    private static HumanoidNpc npc(ServerLevel l, String role, int tier, BlockPos at, String island) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        n.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        n.configure(role, tier, island, at, "", l.random);
        l.addFreshEntity(n);
        SPAWNED.add(n);
        return n;
    }

    private static HumanoidNpc npc(ServerLevel l, String role, int tier, BlockPos at) {
        return npc(l, role, tier, at, "");
    }

    private static void place(ServerPlayer p, BlockPos at) {
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    private static void kill(GLNpc n, ServerPlayer by) {
        n.invulnerableTime = 0;
        n.hurt(by.damageSources().playerAttack(by), 1_000_000f);
    }

    /** One option list as the dialogue would show it (career options only). */
    private static List<String> options(ServerPlayer sp, GLNpc npc) {
        ListTag opts = new ListTag();
        CareerPerks.addOptions(sp, npc, opts);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < opts.size(); i++) out.add(opts.getString(i));
        return out;
    }

    private static String rankId(CharacterData d) {
        CareerTrack t = CareerTrack.of(d.faction);
        return t == null ? "-" : t.rank(Careers.rank(d)).id();
    }

    private static int ground(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void force(ServerLevel l, double x, double z, int r) {
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (FORCED.add(ChunkPos.asLong(cx + dx, cz + dz))) l.setChunkForced(cx + dx, cz + dz, true);
                l.getChunk(cx + dx, cz + dz);
            }
    }

    private static void release(ServerLevel l) {
        for (long c : FORCED) l.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        FORCED.clear();
    }

    private static void scrap(ServerLevel l, ShipEntity s) {
        if (s == null || s.isRemoved()) return;
        for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s))) if (l.getEntity(u) != null) l.getEntity(u).discard();
        for (GLNpc e : l.getEntitiesOfClass(GLNpc.class, s.getBoundingBox().inflate(s.cls.length), e -> s.carries(e) || e.getVehicle() == s)) e.discard();
        NavalCombat.unlink(s);
        s.discard();
    }

    private static void clearSea(ServerLevel l) {
        AABB box = new AABB(SEA[0] - 200, 0, SEA[1] - 200, SEA[0] + 200, 200, SEA[1] + 200);
        for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, box, s -> s.flag(ShipEntity.F_NPC) || s.flag(ShipEntity.F_SINKING))) scrap(l, s);
        for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> e.getTags().contains(LivingSea.CREW) || e.getTags().contains(LivingSea.SALVAGE))) e.discard();
    }

    /** Open water around (plan-sampled), away from the other tests' waters. */
    private static double[] openSea(WorldPlan p, Island home) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int r = 0; r < 80; r++) {
            int x = home.x - 1700 - r * 97, z = home.z + home.radius + 600 + (r % 5) * 61;
            boolean open = true;
            for (int ox = -170; ox <= 170 && open; ox += 15)
                for (int oz = -170; oz <= 170 && open; oz += 15) {
                    p.sample(x + ox, z + oz, smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 5) open = false;
                }
            if (open) return new double[]{x + 0.5, z + 0.5};
        }
        return new double[]{home.x - 1700.5, home.z + home.radius + 600.5};
    }

    private static Vec3 sea(double dx, double dz) {
        return new Vec3(SEA[0] + dx, WorldPlan.SEA_LEVEL + 0.9, SEA[1] + dz);
    }

    private static String resource(String path) {
        try (InputStream in = CareerTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** A rowboat registered to the player (a record in the ship registry, as after a purchase) flying the given colours. */
    private static ShipEntity registeredBoat(MinecraftServer s, ServerPlayer p, ShipLook look) {
        ShipEntity boat = ModEntities.SHIPS.get(ShipClass.ROWBOAT).get().create(s.overworld());
        boat.owner = p.getUUID();
        boat.ownerName = Characters.get(p).name;
        boat.moveTo(p.getX(), WorldPlan.SEA_LEVEL + 1, p.getZ());
        boat.setLook(look);
        ShipRegistry.get(s).register(boat);
        ShipRegistry.get(s).update(boat);
        return boat;
    }

    private static ShipLook lookOf(MinecraftServer s, ShipEntity boat) {
        var r = ShipRegistry.get(s).get(boat.shipId);
        return r == null ? ShipLook.DEFAULT : ShipLook.decode(r.look);
    }

    /** Sink an NPC ship through the hull hook (the player fought it): LivingSea rewards and careers follow. */
    private static void sink(ShipEntity ship, ServerPlayer by) {
        ship.hurtHull(ship.hull() + 1, by);
    }

    // ================================================================== phase 1

    static void phase1(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            INBOX.clear();
            NEWS.clear();
            Careers.tap = (p, c) -> (p == null ? NEWS : INBOX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>())).add(c);
            CareerPerks.lookup = u -> {
                for (var p : PLAYERS.values()) if (p.getUUID().equals(u)) return p;
                return null;
            };
            return 0;
        });
        data(steps);
        seaStation(steps);
        marine(steps);
        crimes(steps);
        pirate(steps);
        bossCredit(steps);
        revolutionary(steps);
        hunter(steps);
        civilian(steps);
        persistence(steps);
        permadeath(steps);
        steps.add(s -> {
            ServerLevel l = s.overworld();
            clearSea(l);
            for (Entity e : SPAWNED) if (!e.isRemoved()) e.discard();
            SPAWNED.clear();
            for (var p : PLAYERS.values()) Squads.dismiss(p);
            release(l);
            Careers.tap = null;
            CareerPerks.lookup = null;
            return 0;
        });
    }

    // ------------------------------------------------------------------ data

    private static void data(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            String lang = resource("/assets/grandline/lang/en_us.json");
            List<String> miss = new ArrayList<>();
            for (CareerTrack t : CareerTrack.values())
                for (int i = 0; i <= t.top(); i++) if (lang == null || !lang.contains("\"" + t.rankKey(i) + "\"")) miss.add(t.rankKey(i));
            for (String k : List.of("career.grandline.pirate.warlord", "career.grandline.pirate.emperor_of", "career.grandline.pirate.deserter",
                    "career.grandline.civilian.spec.explorer", "career.grandline.req.seat", "grandline.career.announce.deserter", "item.grandline.marine_coat",
                    "item.grandline.letter_of_marque", "role.grandline.wg_envoy", "dialogue.grandline.option.career_promote", "screen.grandline.career"))
                if (lang == null || !lang.contains("\"" + k + "\"")) miss.add(k);
            CareerTrack m = CareerTrack.MARINE;
            boolean ladders = m.ranks.size() == 13 && m.rank(0).id().equals("seaman_recruit") && m.rank(12).id().equals("fleet_admiral")
                    && CareerTrack.PIRATE.ranks.size() == 3 && CareerTrack.REVOLUTIONARY.rank(3).id().equals("chief_of_staff")
                    && CareerTrack.BOUNTY_HUNTER.ranks.size() == 6 && CareerTrack.CIVILIAN.ranks.size() == 5;
            for (int i = 1; i <= m.top(); i++) if (m.rank(i).need() <= m.rank(i - 1).need() || m.rank(i).level() < m.rank(i - 1).level()) ladders = false;
            check("career ladders: Marine 13 ranks (Seaman Recruit to Fleet Admiral, rising merit and level), pirate 3, revolutionary 4, hunter 6, civilian 5; all translated",
                    ladders && miss.isEmpty(), miss.isEmpty() ? "" : "untranslated " + miss);
            boolean coat = CareerRegistry.MARINE_COAT.get() instanceof ArmorItem ai && ai.getType() == ArmorItem.Type.CHESTPLATE
                    && CareerTests.class.getResource("/assets/grandline/textures/models/armor/marine_coat_layer_1.png") != null
                    && CareerTests.class.getResource("/assets/grandline/textures/item/marine_coat.png") != null
                    && CareerTests.class.getResource("/assets/grandline/textures/item/letter_of_marque.png") != null;
            int advs = 0;
            for (var h : s.getAdvancements().getAllAdvancements()) if (h.id().getNamespace().equals(GrandLine.MODID) && h.id().getPath().startsWith("journey/career_")) advs++;
            boolean ids = Journey.allIds().containsAll(Careers.MILESTONES.values());
            check("the Marine coat (chest armour with its own layer texture) and the letter of marque exist; 19 career advancements, every rank milestone fired",
                    coat && advs == 19 && ids, "coat " + coat + ", " + advs + " advancements, ids " + ids);
            return 0;
        });
    }

    // ------------------------------------------------------------------ the sea station (sea battles, ship calls)

    private static void seaStation(Deque<SelfTest.Step> steps) {
        final int[] waited = {0};
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            double[] o = openSea(p, p.byId("shiokaze_isle"));
            SEA[0] = o[0];
            SEA[1] = o[1];
            force(s.overworld(), SEA[0], SEA[1], 8);
            return 1;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            for (double[] d : new double[][]{{0, 0}, {110, 0}, {-110, 0}, {0, 110}, {0, -110}})
                if (!l.isPositionEntityTicking(BlockPos.containing(SEA[0] + d[0], 64, SEA[1] + d[1])) && ++waited[0] < 3000) return -1;
            clearSea(l);
            check("career sea station loaded", true, (int) SEA[0] + "," + (int) SEA[1] + " after " + waited[0] + " ticks");
            return 0;
        });
    }

    // ------------------------------------------------------------------ Marines

    private static void marine(Deque<SelfTest.Step> steps) {
        final GLNpc[] officer = new GLNpc[2];
        // enlisted: the title, merit from arrests, the promotion talk
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var m = account(s, "CareerM");
            CharacterData d = create(m, "Mara Seawall", "marine");
            place(m, a);
            d.level = 10;
            boolean start = d.career.track.equals("marine") && Careers.rank(d) == 0 && name(m).contains("career.grandline.marine.seaman_recruit")
                    && tab(m).contains("career.grandline.marine.seaman_recruit");
            for (int i = 0; i < 3; i++) npc(l, "pirate", 3, a.offset(3, 0, i - 1)).arrestedBy(m);
            boolean due = d.career.merit >= 30 && d.career.count("arrests") == 3 && d.career.count("brought_in") > 0 && Careers.rank(d) == 0 && d.career.due
                    && told(m, "grandline.career.due.marine");
            officer[0] = npc(l, "marine_officer", 2, a.offset(2, 0, 2)); // configured to tier 4: an officer for the junior ranks
            GLNpc plain = npc(l, "marine", 2, a.offset(-2, 0, 2));
            List<String> opts = options(m, officer[0]);
            boolean plainNo = !options(m, plain).contains("career_promote") && !CareerPerks.officerFor(CareerTrack.MARINE, 1, plain);
            plain.discard();
            Dialogues.open(m, officer[0]);
            Dialogues.choose(m, officer[0].getId(), "career_promote");
            boolean seaman = Careers.rank(d) == 1 && m.getInventory().countItem(CareerRegistry.MARINE_COAT.get()) == 1 && told(m, "grandline.career.promoted")
                    && name(m).contains("career.grandline.marine.seaman") && !name(m).contains("seaman_recruit")
                    && CharacterManager.visuals(m).getString("careerTitle").contains("career.grandline.marine.seaman");
            boolean adv = Journey.done(m, "career_marine_seaman") && Journey.done(m, "career_path");
            check("a Marine starts as Seaman Recruit (title in chat and tab names), earns merit from arrests and is told to report", start && due,
                    "start " + start + ", merit " + d.career.merit + ", arrests " + d.career.count("arrests") + ", brought in " + d.career.count("brought_in") + ", due " + d.career.due);
            check("the promotion talk with a Marine officer makes a Seaman (coat issued, title, milestone); a plain Marine cannot promote",
                    opts.contains("career_promote") && opts.contains("career_supply") && seaman && adv && plainNo,
                    "options " + opts + ", rank " + rankId(d) + ", coat " + m.getInventory().countItem(CareerRegistry.MARINE_COAT.get()) + ", milestone " + adv + ", plain " + plainNo);
            return 0;
        });
        // climbing by merit, one rank per talk; the enlistment gate and the path quest
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var m = account(s, "CareerM");
            CharacterData d = Characters.get(m);
            int talks = 0, arrests = 0;
            GLNpc pk = npc(l, "pirate", 4, a.offset(4, 0, 0));
            long before = d.career.merit;
            kill(pk, m);
            boolean killMerit = d.career.merit > before;
            while (Careers.rank(d) < 3 && arrests < 40) {
                npc(l, "pirate", 3, a.offset(3, 0, 0)).arrestedBy(m);
                arrests++;
                if (d.career.due) {
                    int r = Careers.rank(d);
                    Dialogues.choose(m, officer[0].getId(), "career_promote");
                    if (Careers.rank(d) == r + 1) talks++;
                }
            }
            boolean climbed = Careers.rank(d) == 3 && talks == 2;
            // Ensign needs the enlistment (path quest)
            d.career.merit = Math.max(d.career.merit, 300);
            d.career.due = false;
            Careers.check(m);
            Dialogues.choose(m, officer[0].getId(), "career_promote");
            boolean gated = Careers.rank(d) == 3 && Careers.missing(m, d, CareerTrack.MARINE, 4).stream().anyMatch(c -> JourneyTests.flat(c).contains("career.grandline.req.enlisted"))
                    && told(m, "grandline.career.not_yet");
            Quests.start(m, "path_marine");
            Quests.progress(m, "kill", "pirate", 5);
            Quests.progress(m, "kill", "bandit", 5);
            boolean enlisted = d.flags.contains(Careers.ENLISTED);
            Dialogues.choose(m, officer[0].getId(), "career_promote");
            boolean ensign = Careers.rank(d) == 4 && Journey.done(m, "career_marine_ensign")
                    && m.getInventory().items.stream().anyMatch(st -> st.is(CareerPerks_item("seastone_jitte")) && st.has(DataComponents.CUSTOM_NAME));
            check("merit from arrests and a kill climbs Seaman → Petty Officer → Chief Petty Officer, one rank per talk",
                    killMerit && climbed, "kill merit " + killMerit + ", " + arrests + " arrests, " + talks + " talks, rank " + rankId(d) + ", merit " + d.career.merit);
            check("Ensign is gated on the enlistment: refused, then the path quest (enlisted) and the talk make an Ensign (seastone jitte issued)",
                    gated && enlisted && ensign, "gated " + gated + ", enlisted " + enlisted + ", rank " + rankId(d));
            return 0;
        });
        // Captain needs a sea battle won against pirates (the real LivingSea hook)
        steps.add(s -> {
            ServerLevel l = s.overworld();
            var m = account(s, "CareerM");
            CharacterData d = Characters.get(m);
            d.career.merit = 1_000;
            d.level = 25;
            for (int i = 0; i < 3; i++) Dialogues.choose(m, officer[0].getId(), "career_promote");
            boolean blocked = Careers.rank(d) == 6 && Careers.missing(m, d, CareerTrack.MARINE, 7).stream().anyMatch(c -> JourneyTests.flat(c).contains("career.grandline.req.sea_wins"));
            place(m, BlockPos.containing(sea(0, 0)));
            ShipEntity quarry = SeaEncounters.spawnShip(l, sea(30, 0), 0, ShipClass.SLOOP, LivingSea.PIRATE, 1, "Career Quarry");
            long merit0 = d.career.merit;
            if (quarry != null) sink(quarry, m);
            boolean won = d.career.count("sea_wins") == 1 && d.career.merit == merit0 + 30;
            clearSea(l);
            place(m, SelfTest.arena(l));
            long news0 = news("grandline.career.announce.marine");
            Dialogues.choose(m, officer[0].getId(), "career_promote");
            boolean captain = Careers.rank(d) == 7 && Journey.done(m, "career_marine_captain") && news("grandline.career.announce.marine") == news0 + 1
                    && m.getInventory().items.stream().anyMatch(st -> st.is(CareerPerks_item("fine_katana")) && st.has(DataComponents.CUSTOM_NAME));
            check("Captain waits for a sea battle won: a pirate sloop sunk through the hull hook counts (merit +30), then the talk makes Captain (announced, sword)",
                    blocked && quarry != null && won && captain, "blocked at " + rankId(d) + "=" + blocked + ", won " + won + " (wins " + d.career.count("sea_wins") + "), rank " + rankId(d));
            return 0;
        });
        // perks at Captain: squad, pay, the supply shop
        steps.add(s -> {
            ServerLevel l = s.overworld();
            var m = account(s, "CareerM");
            CharacterData d = Characters.get(m);
            int n = Squads.call(m);
            List<GLNpc> squad = Squads.members(m);
            boolean loyal = !squad.isEmpty();
            for (GLNpc g : squad) loyal &= g.faction() == Faction.MARINE && !g.isHostileTo(m) && Squads.isMember(g);
            int again = Squads.call(m);
            Squads.dismiss(m);
            boolean gone = Squads.members(m).isEmpty() && squad.stream().allMatch(Entity::isRemoved);
            check("a Captain calls a squad of three Marines who stand by their leader; the call has a cooldown; dismissed they leave",
                    n == 3 && squad.size() == 3 && loyal && again == -2 && gone, "called " + n + ", loyal " + loyal + ", again " + again + ", gone " + gone);
            long day = s.overworld().getDayTime() / 24000L;
            d.career.lastPaidDay = day - 1;
            long beli0 = d.beli;
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(m));
            long paid = d.beli - beli0;
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(m));
            boolean once = d.beli - beli0 == paid;
            check("rank pay on login: a Captain is paid 2,300 Beli once a day (a second login the same day pays nothing)",
                    paid == 2_300 && once && told(m, "grandline.career.pay.marine"), "paid " + paid + ", once " + once);
            var shop = CareerPerks.shop(m, officer[0]);
            var p = account(s, "CareerShopPirate");
            create(p, "Shop Pirate", "pirate");
            place(p, SelfTest.arena(l));
            d.beli = 50_000;
            int coats = m.getInventory().countItem(CareerRegistry.MARINE_COAT.get());
            Shops.buy(m, officer[0].getId(), 0);
            boolean bought = m.getInventory().countItem(CareerRegistry.MARINE_COAT.get()) == coats + 1 && d.beli < 50_000;
            boolean ranked = shop != null && shop.stream().anyMatch(o -> o.item().equals("grandline:seastone_jitte")) && shop.stream().anyMatch(o -> o.item().equals("grandline:fine_katana"));
            check("Marine quartermasters sell to Marines by rank (coat bought from an officer); a pirate gets no Marine supply",
                    ranked && bought && CareerPerks.shop(p, officer[0]) == null, "offers " + (shop == null ? 0 : shop.size()) + ", bought " + bought);
            return 0;
        });
        // the top ranks: senior officers, story, Haki, seats
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var m = account(s, "CareerM");
            CharacterData d = Characters.get(m);
            d.career.merit = 6_000;
            d.level = 80;
            d.career.counts.put("sea_wins", 3L);
            Dialogues.choose(m, officer[0].getId(), "career_promote");
            boolean junior = Careers.rank(d) == 7 && !options(m, officer[0]).contains("career_promote");
            officer[1] = npc(l, "vice_admiral", 8, a.offset(-2, 0, -2));
            for (int i = 0; i < 3; i++) Dialogues.choose(m, officer[1].getId(), "career_promote");
            boolean partGate = Careers.rank(d) == 8 && Careers.missing(m, d, CareerTrack.MARINE, 9).stream().anyMatch(c -> JourneyTests.flat(c).contains("career.grandline.req.part"));
            d.completedQuests.addAll(List.of("sabaody_3", "war_3", "lodestar_1"));
            d.observationUnlocked = true;
            Dialogues.choose(m, officer[1].getId(), "career_promote");
            Dialogues.choose(m, officer[1].getId(), "career_promote");
            boolean va = Careers.rank(d) == 10 && Journey.done(m, "career_marine_vice_admiral")
                    && m.getInventory().countItem(com.grandline.journey.JourneyRegistry.BLACK_DEN_DEN_MUSHI.get()) == 1;
            Dialogues.choose(m, officer[1].getId(), "career_promote");
            boolean hakiGate = Careers.rank(d) == 10;
            d.armamentUnlocked = true;
            d.conquerorAwakened = true;
            Dialogues.choose(m, officer[1].getId(), "career_promote");
            CareerWorldData w = CareerWorldData.get(s);
            boolean admiral = Careers.rank(d) == 11 && w.holds(CareerWorldData.ADMIRAL, d.characterId) && Journey.done(m, "career_marine_admiral");
            Dialogues.choose(m, officer[1].getId(), "career_promote");
            boolean fleet = Careers.rank(d) == 12 && w.holds(CareerWorldData.FLEET_ADMIRAL, d.characterId) && !w.holds(CareerWorldData.ADMIRAL, d.characterId)
                    && Journey.done(m, "career_marine_fleet_admiral") && name(m).contains("career.grandline.marine.fleet_admiral");
            // a second Marine just as qualified finds the Fleet Admiral's seat taken
            var m2 = account(s, "CareerM2");
            CharacterData d2 = create(m2, "Second Seawall", "marine");
            Careers.ensure(m2, d2);
            d2.factionRank = 11;
            d2.career.merit = 6_000;
            d2.level = 80;
            d2.flags.add(Careers.ENLISTED);
            d2.career.counts.put("sea_wins", 3L);
            d2.completedQuests.addAll(d.completedQuests);
            d2.observationUnlocked = d2.armamentUnlocked = d2.conquerorAwakened = true;
            List<Component> miss2 = Careers.missing(m2, d2, CareerTrack.MARINE, 12);
            boolean seatGate = miss2.size() == 1 && JourneyTests.flat(miss2.get(0)).contains("career.grandline.req.seat");
            check("the top ranks are gated hard: a Lieutenant's officer cannot promote a Commodore, Rear Admiral needs Part II, Admiral Part III and both Haki",
                    junior && partGate && va && hakiGate, "junior " + junior + ", part " + partGate + ", vice admiral " + va + " (black snail), haki gate " + hakiGate);
            check("Admiral and Fleet Admiral take limited seats (the Admiral's seat freed on promotion); a second qualified Marine finds the Fleet Admiral's seat taken",
                    admiral && fleet && seatGate, "admiral " + admiral + ", fleet " + fleet + ", second " + miss2.stream().map(JourneyTests::flat).toList());
            return 0;
        });
        // Buster Call request (Vice Admiral and up) and a warship at sea (Commodore and up)
        steps.add(s -> {
            ServerLevel l = s.overworld();
            var m = account(s, "CareerM");
            var t = account(s, "CareerT");
            CharacterData dt = create(t, "Tess Wanted", "pirate");
            dt.bounty = 600_000_000L;
            place(t, SelfTest.arena(l).offset(1000, 0, 0));
            var m2 = account(s, "CareerM2");
            Characters.get(m2).factionRank = 7;
            boolean captainNo = !CareerPerks.requestBusterCall(m2, t) && told(m2, "grandline.career.buster.rank");
            boolean granted = CareerPerks.requestBusterCall(m, t);
            BusterCall.Call call = BusterCall.activeFor(t);
            boolean again = !CareerPerks.requestBusterCall(m, t);
            boolean cancelled = BusterCall.cancel(s, t);
            check("a Fleet Admiral's Buster Call request on a 600M pirate is granted (a Captain's refused; one request an hour)",
                    captainNo && granted && call != null && again && cancelled, "captain refused " + captainNo + ", granted " + granted + ", call " + (call != null) + ", again refused " + again);
            place(m, BlockPos.containing(sea(0, 0)));
            ShipEntity ship = CareerPerks.callShip(m);
            boolean ok = ship != null && ship.flag(ShipEntity.F_NPC) && ship.npcFaction.equals(LivingSea.MARINE) && ship.distanceTo(m) < 140
                    && CareerPerks.callShip(m) == null;
            check("a Marine flag officer calls a warship to the open sea nearby (once in a while)", ok,
                    ship == null ? "no ship" : ship.shipName() + " " + ship.cls.id() + " " + (int) ship.distanceTo(m) + " blocks");
            clearSea(l);
            place(m, SelfTest.arena(l));
            return 0;
        });
    }

    /** A mod item by id (the issued kit). */
    private static net.minecraft.world.item.Item CareerPerks_item(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(GrandLine.id(id));
    }

    // ------------------------------------------------------------------ crimes: demotion, desertion

    private static void crimes(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var x = account(s, "CareerX");
            CharacterData d = create(x, "Xander Turncoat", "marine");
            place(x, a);
            Careers.ensure(x, d);
            d.factionRank = 2;
            d.career.merit = 100;
            for (int i = 0; i < 3; i++) npc(l, "marine", 2, a.offset(2, 0, i - 1)).hurt(x.damageSources().playerAttack(x), 1f);
            boolean demoted = Careers.rank(d) == 1 && d.career.demerits == 2 && told(x, "grandline.career.demoted") && d.career.merit <= 30;
            String demotedTo = rankId(d) + " (merit " + d.career.merit + ")";
            npc(l, "villager", 1, a.offset(-2, 0, 0)).hurt(x.damageSources().playerAttack(x), 1f);
            int afterCivilian = d.career.demerits;
            ShipEntity gull = registeredBoat(s, x, ShipLook.MARINE);
            Bounties.raise(x, d, 3_000_000L, Component.literal("test crime"));
            boolean recruit = Careers.rank(d) == 0 && d.faction == Faction.MARINE && d.bounty == 0;
            long news0 = news("grandline.career.announce.deserter");
            Bounties.raise(x, d, 3_000_000L, Component.literal("test crime"));
            boolean deserted = d.faction == Faction.PIRATE && d.flags.contains(Careers.DESERTER) && d.bounty >= 5_000_000L && d.career.track.equals("pirate")
                    && news("grandline.career.announce.deserter") == news0 + 1 && name(x).contains("career.grandline.pirate.deserter")
                    && lookOf(s, gull).emblem().equals("skull");
            ShipRegistry.get(s).remove(gull.shipId);
            check("striking Marines and civilians earns demerits: a Petty Officer is demoted (merit cut back)", demoted && afterCivilian == 5,
                    "demoted to " + demotedTo + ", demerits after civilian " + afterCivilian);
            check("further crimes demote to Seaman Recruit, then the recruit deserts: a pirate with a bounty, announced, titled Deserter, the gull struck from their ship",
                    recruit && deserted,
                    "recruit " + recruit + ", faction " + d.faction.id() + ", bounty " + d.bounty + ", flags deserter " + d.flags.contains(Careers.DESERTER));
            // the old rule still holds: a Marine whose standing collapses deserts (now with the career's bounty)
            var y = account(s, "CareerY");
            CharacterData dy = create(y, "Yara Overboard", "marine");
            Careers.ensure(y, dy);
            dy.factionRank = 5;
            dy.reputation.put(Faction.MARINE.id(), -299);
            Bounties.raise(y, dy, 1_000_000L, Component.literal("test crime"));
            check("a Marine whose standing falls below -300 deserts (Bounties' rule) with the career's deserter bounty (5M per rank held)",
                    dy.faction == Faction.PIRATE && dy.bounty >= 31_000_000L && dy.flags.contains(Careers.DESERTER) && Careers.rank(dy) == 0,
                    "faction " + dy.faction.id() + ", bounty " + dy.bounty);
            return 0;
        });
    }

    // ------------------------------------------------------------------ pirates: Supernova, Warlord, Emperor, territory

    /** A copy of a named rival on the arena, beaten (it yields). */
    private static void beat(ServerLevel l, ServerPlayer by, String notable, BlockPos at) {
        HumanoidNpc n = npc(l, "pirate_captain", 9, at);
        n.makeNotable(notable, null, 0, 0, 0, "brawler");
        n.invulnerableTime = 0;
        n.hurt(by.damageSources().playerAttack(by), 1_000_000f);
    }

    private static void pirate(Deque<SelfTest.Step> steps) {
        final GLNpc[] marine = new GLNpc[1];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var p = account(s, "CareerP");
            CharacterData d = create(p, "Pell Blackwake", "pirate");
            place(p, a);
            boolean rookie = name(p).contains("career.grandline.pirate.rookie");
            Bounties.raise(p, d, 120_000_000L, Component.literal("test deeds"));
            boolean notYet = Careers.rank(d) == 0;
            WorldPlan plan = WorldPlan.of(s);
            Island ala = plan.byId("alabasta");
            p.moveTo(ala.x + 0.5, ground(l, ala.x, ala.z) + 1, ala.z + 0.5);
            Navigation.tick(p, d);
            place(p, a);
            boolean paradise = d.discoveredIslands.contains("alabasta") && Careers.rank(d) == 0;
            long news0 = news("grandline.career.announce.pirate");
            beat(l, p, "sigrid", a.offset(3, 0, 0));
            beat(l, p, "ulfric", a.offset(-3, 0, 0));
            boolean nova = Careers.rank(d) == 1 && d.career.count("deeds") == 2 && name(p).contains("career.grandline.pirate.supernova")
                    && news("grandline.career.announce.pirate") == news0 + 1 && Journey.done(p, "career_pirate_supernova");
            check("pirate infamy: a Rookie with 120M is not yet a Supernova; Paradise (Navigation at Alabasta) and two named rivals beaten (yields) make one, announced",
                    rookie && notYet && paradise && nova, "rookie " + rookie + ", deeds " + d.career.count("deeds") + ", rank " + rankId(d) + ", paradise " + paradise);
            // the banner and the ports of call
            HumanoidNpc deckhand = npc(l, "pirate", 3, a.offset(0, 0, 4));
            boolean asked = options(p, deckhand).contains("career_banner") && !deckhand.isHostileTo(p);
            Dialogues.choose(p, deckhand.getId(), "career_banner");
            boolean joined = Squads.bannerCount(p) == 1 && Squads.isMember(deckhand);
            Squads.dismiss(p);
            Island haven = null;
            for (Island i : plan.islands) if (i.faction.equals("pirate")) {
                haven = i;
                break;
            }
            HumanoidNpc fence = haven == null ? null : npc(l, "pirate", 3, a.offset(-1, 0, 4), haven.id);
            var fenceShop = fence == null ? null : CareerPerks.shop(p, fence);
            boolean port = fenceShop != null && fenceShop.stream().anyMatch(o -> o.item().equals("grandline:grappling_hook")) && options(p, fence).contains("career_port");
            check("a Supernova's banner: a pirate joins when asked (and is gone when dismissed); a pirate-held island's port of call sells to a known pirate",
                    asked && joined && deckhand.isRemoved() && port, "asked " + asked + ", joined " + joined + ", haven " + (haven == null ? "none" : haven.id) + ", port " + port);
            if (fence != null) fence.discard();
            return 0;
        });
        // the letter of marque
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var p = account(s, "CareerP");
            CharacterData d = Characters.get(p);
            marine[0] = npc(l, "marine_captain", 5, a.offset(-4, 0, 3));
            boolean hostileBefore = marine[0].isHostileTo(p);
            HumanoidNpc envoy = Warlords.spawnEnvoy(p);
            List<String> opts = envoy == null ? List.of() : options(p, envoy);
            if (envoy != null) Dialogues.choose(p, envoy.getId(), "career_marque");
            CareerWorldData w = CareerWorldData.get(s);
            boolean granted = d.career.warlord && w.holds(CareerWorldData.WARLORD, d.characterId) && p.getInventory().countItem(CareerRegistry.LETTER_OF_MARQUE.get()) == 1
                    && name(p).contains("career.grandline.pirate.warlord") && Journey.done(p, "career_pirate_warlord") && news("grandline.career.announce.warlord") >= 1
                    && envoy != null && envoy.isRemoved();
            boolean protectedNow = !marine[0].isHostileTo(p) && Careers.protectedAtSea(p);
            check("a World Government envoy offers a Supernova the letter of marque; accepted: Warlord of the Sea (seat, letter, title, milestone, announced)",
                    opts.contains("career_marque") && granted, "options " + opts + ", warlord " + d.career.warlord);
            check("a Warlord is protected: a Marine captain hostile before stands down (and Marine ships at sea)", hostileBefore && protectedNow,
                    "before " + hostileBefore + ", now hostile " + marine[0].isHostileTo(p));
            // duties: pirates hunted, the period ends, the share paid
            for (int i = 0; i < 3; i++) kill(npc(l, "pirate", 2, a.offset(4, 0, i - 1)), p);
            int duty = d.career.dutyDone;
            d.career.dutyDue = s.overworld().getGameTime() - 1;
            long beli0 = d.beli;
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
            boolean paid = d.career.warlord && d.beli - beli0 >= Warlords.SHARE && d.career.dutyDone == 0 && d.career.dutyDue > s.overworld().getGameTime();
            // a crime revokes the letter
            long bounty0 = d.bounty;
            Bounties.raise(p, d, 1_000_000L, Component.literal("test crime"));
            boolean revoked = !d.career.warlord && d.flags.contains(Warlords.REVOKED) && d.bounty >= bounty0 + 50_000_000L && !w.holds(CareerWorldData.WARLORD, d.characterId)
                    && news("grandline.career.announce.warlord_revoked") >= 1 && marine[0].isHostileTo(p) && !Warlords.eligible(p, d)
                    && name(p).contains("career.grandline.pirate.supernova");
            check("a Warlord's duty (three pirates hunted) is paid at the end of the period; a crime revokes the letter: bounty back, seat freed, Marines hostile, no second letter",
                    duty == 3 && paid && revoked, "duty " + duty + ", paid " + paid + ", revoked " + revoked + ", bounty " + bounty0 + " -> " + d.bounty);
            return 0;
        });
        // Emperor and territory
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var p = account(s, "CareerP");
            CharacterData d = Characters.get(p);
            Bounties.raise(p, d, 1_200_000_000L, Component.literal("test deeds"));
            d.level = 50;
            Careers.check(p);
            boolean waits = Careers.rank(d) == 1 && Careers.missing(p, d, CareerTrack.PIRATE, 2).stream().anyMatch(c -> JourneyTests.flat(c).contains("career.grandline.req.emperor_deed"));
            ShipEntity flagship = registeredBoat(s, p, new ShipLook("skull", "flame", 0xF0F0F0, 0x1A1A1A));
            beat(l, p, "hrolf", a.offset(3, 0, 3));
            ShipLook colours = lookOf(s, flagship);
            ShipRegistry.get(s).remove(flagship.shipId);
            boolean emperor = Careers.rank(d) == 2 && d.career.emperorDeed && name(p).contains("career.grandline.pirate.emperor") && Journey.done(p, "career_pirate_emperor")
                    && colours.emblem().equals("flame") && colours.flagColor() == 0x9E2222 && colours.emblemColor() == 0xE8C040;
            check("a billion-Beli Supernova waits for an Emperor-class deed; beating an Emperor (Hrolf yields) makes an Emperor of the Sea; their ships fly their Jolly Roger in gold on crimson",
                    waits && emperor, "waits " + waits + ", rank " + rankId(d) + ", deed " + d.career.emperorDeed + ", flag " + colours.encode());
            // territory: a settled island of the Grand Line
            WorldPlan plan = WorldPlan.of(s);
            Island target = null;
            for (Island i : plan.islands)
                if (i.zone.isGrandLine() && i.starterFor == null && i.faction.equals("civilian") && i.landmarks.isEmpty() && !i.canon
                        && (i.settlementType == SettlementType.VILLAGE || i.settlementType == SettlementType.HAMLET || i.settlementType == SettlementType.TOWN)) {
                    target = i;
                    break;
                }
            Component why = null;
            if (target != null) {
                p.moveTo(target.x + 0.5, ground(l, target.x, target.z) + 1, target.z + 0.5);
                why = CareerPerks.claim(p);
            }
            CareerWorldData.Holder h = target == null ? null : CareerWorldData.get(s).territory(target.id);
            boolean claimed = why == null && h != null && h.character().equals(d.characterId) && name(p).contains("career.grandline.pirate.emperor_of")
                    && news("grandline.career.announce.territory") >= 1;
            long day = s.overworld().getDayTime() / 24000L;
            d.career.lastPaidDay = day - 1;
            long beli0 = d.beli;
            CareerPerks.payDay(p, d);
            boolean tribute = d.beli - beli0 == CareerPerks.TRIBUTE;
            place(p, a);
            check("an Emperor claims an island of the Grand Line: titled Emperor of it, tribute paid each day",
                    claimed && tribute, (target == null ? "no island" : target.id) + ", why " + (why == null ? "-" : JourneyTests.flat(why)) + ", tribute " + tribute);
            return 0;
        });
    }

    /** Boss fights reward every fighter in their records: an Emperor fought counts (for this life only). */
    private static void bossCredit(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            var e = account(s, "CareerE");
            CharacterData d = create(e, "Edda Stormcrown", "pirate");
            Careers.ensure(e, d);
            d.factionRank = 1;
            d.bounty = 1_200_000_000L;
            d.level = 50;
            com.grandline.boss.BossRecords.get(s).claim(e.getUUID(), "kurogane"); // as BossRewards does for everyone who fought
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(e));
            boolean emperor = Careers.rank(d) == 2 && d.career.emperorDeed;
            // an earlier life's fight does not count for a new character
            var o = account(s, "CareerE2");
            com.grandline.boss.BossRecords.get(s).claim(o.getUUID(), "barnum");
            CharacterData od = create(o, "Olin Latecomer", "pirate");
            Careers.ensure(o, od);
            od.factionRank = 1;
            od.bounty = 1_200_000_000L;
            od.level = 50;
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(o));
            boolean notInherited = Careers.rank(od) == 1 && !od.career.emperorDeed;
            check("an Emperor fought in a boss fight (the fight's reward records) makes a billion-Beli Supernova an Emperor; an earlier life's fight does not count",
                    emperor && notInherited, "rank " + rankId(d) + ", deed " + d.career.emperorDeed + "; earlier life: rank " + rankId(od) + ", deed " + od.career.emperorDeed);
            return 0;
        });
    }

    // ------------------------------------------------------------------ revolutionaries

    private static void revolutionary(Deque<SelfTest.Step> steps) {
        final GLNpc[] contact = new GLNpc[1];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var r = account(s, "CareerR");
            CharacterData d = create(r, "Rook Dawnward", "revolutionary");
            place(r, a);
            boolean cell = name(r).contains("career.grandline.revolutionary.cell_member");
            Quests.start(r, "path_revolutionary");
            Quests.progress(r, "kill", "cp_agent", 1);
            Quests.progress(r, "discover", "any_canon", 2);
            boolean contactFlag = d.flags.contains(Careers.REV_CONTACT) && d.career.merit >= 40;
            contact[0] = npc(l, "revolutionary", 4, a.offset(2, 0, -2));
            List<String> opts = options(r, contact[0]);
            Dialogues.choose(r, contact[0].getId(), "career_mission");
            boolean incite = d.career.mission.equals("incite");
            // rouse Shiokaze Isle: three islanders, then the elder
            HumanoidNpc elder = npc(l, "elder", 1, a.offset(-3, 0, -3), "shiokaze_isle");
            Dialogues.open(r, elder);
            boolean early = d.career.missionProgress == 0;
            for (int i = 0; i < 3; i++) Dialogues.open(r, npc(l, "villager", 1, a.offset(-3 + i, 0, 3), "shiokaze_isle"));
            Dialogues.open(r, elder);
            boolean incited = d.career.count("missions") == 1 && d.career.mission.isEmpty();
            check("a revolutionary: Cell Member, the path quest makes contact; missions from a revolutionary (incite: three islanders, then the elder)",
                    cell && contactFlag && opts.contains("career_mission") && opts.contains("career_safe_house") && incite && early && incited,
                    "options " + opts + ", contact " + contactFlag + ", early " + early + ", missions " + d.career.count("missions"));
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            var r = account(s, "CareerR");
            CharacterData d = Characters.get(r);
            Dialogues.choose(r, contact[0].getId(), "career_mission");
            boolean sab = d.career.mission.equals("sabotage");
            place(r, BlockPos.containing(sea(0, 0)));
            ShipEntity prey = SeaEncounters.spawnShip(l, sea(-30, 0), 0, ShipClass.SLOOP, LivingSea.MARINE, 1, "Career Patrol");
            if (prey != null) sink(prey, r);
            boolean sabotaged = d.career.count("missions") == 2 && d.career.mission.isEmpty();
            clearSea(l);
            place(r, SelfTest.arena(l));
            Dialogues.choose(r, contact[0].getId(), "career_mission");
            boolean lib = d.career.mission.equals("liberate");
            int[] at = Missions.auctionHouse(r);
            boolean freed = false;
            if (at != null) {
                force(l, at[0], at[1], 1);
                int y = ground(l, at[0], at[1]);
                r.moveTo(at[0] + 0.5, y + 1, at[1] + 0.5);
                for (int i = 0; i < 2; i++) kill(npc(l, "guard", 2, new BlockPos(at[0] + 2 + i, y + 1, at[1] + 2), "sabaody_archipelago"), r);
                freed = d.career.count("missions") == 3 && d.career.count("freed") == 3;
                place(r, SelfTest.arena(l));
            }
            check("revolutionary missions: sabotage (a Marine ship sunk) and liberation (two auction-house guards at Sabaody beaten, three captives freed)",
                    sab && prey != null && sabotaged && lib && freed, "sabotage " + sabotaged + ", liberate " + freed + ", missions " + d.career.count("missions") + ", merit " + d.career.merit);
            // promotions in person
            d.level = 15;
            Dialogues.choose(r, contact[0].getId(), "career_promote");
            boolean officer = Careers.rank(d) == 1 && Journey.done(r, "career_revolutionary_officer") && name(r).contains("career.grandline.revolutionary.officer");
            d.career.merit = 900;
            d.career.counts.put("missions", 6L);
            d.level = 30;
            Dialogues.choose(r, contact[0].getId(), "career_promote");
            boolean commander = Careers.rank(d) == 2 && Journey.done(r, "career_revolutionary_commander") && news("grandline.career.announce.revolutionary") >= 1;
            int cell = Squads.call(r);
            boolean revs = Squads.members(r).stream().allMatch(g -> g.faction() == Faction.REVOLUTIONARY);
            Squads.dismiss(r);
            check("revolutionary promotions in person: Officer, then Army Commander (announced) who calls a cell of three", officer && commander && cell == 3 && revs,
                    "officer " + officer + ", commander " + commander + ", cell " + cell);
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var r = account(s, "CareerR");
            CharacterData d = Characters.get(r);
            GLNpc law = npc(l, "marine_officer", 4, a.offset(-4, 0, -4));
            boolean hunted = d.bounty > 0 && law.isHostileTo(r);
            Dialogues.choose(r, contact[0].getId(), "career_safe_house");
            boolean hidden = d.career.safeUntil > l.getGameTime() && !law.isHostileTo(r);
            var shop = CareerPerks.shop(r, contact[0]);
            Dialogues.choose(r, contact[0].getId(), "career_contacts");
            boolean contacts = shop != null && shop.stream().anyMatch(o -> o.item().equals("grandline:musket")) && told(r, "grandline.career.intel");
            long day = l.getDayTime() / 24000L;
            d.career.lastPaidDay = day - 1;
            long beli0 = d.beli;
            CareerPerks.payDay(r, d);
            check("a safe house hides a wanted revolutionary from the law for a while; contacts sell supplies and name the nearest Marine base; the Army pays",
                    hunted && hidden && contacts && d.beli - beli0 == 500, "hunted " + hunted + ", hidden " + hidden + ", contacts " + contacts + ", pay " + (d.beli - beli0));
            law.discard();
            return 0;
        });
    }

    // ------------------------------------------------------------------ bounty hunters

    private static void hunter(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var h = account(s, "CareerH");
            CharacterData d = create(h, "Hale Ironwrit", "bounty_hunter");
            place(h, a);
            Quests.start(h, "path_bounty_hunter");
            for (int i = 0; i < 2; i++) npc(l, "pirate_captain", 6, a.offset(3, 0, i)).arrestedBy(h);
            boolean licensed = Careers.rank(d) == 1 && d.flags.contains(Careers.LICENSED) && Journey.done(h, "career_hunter_licensed")
                    && name(h).contains("career.grandline.bounty_hunter.licensed") && h.getInventory().countItem(ModItems.SEASTONE_CUFFS.get()) >= 2;
            HumanoidNpc cap = npc(l, "pirate_captain", 6, a.offset(3, 0, 2));
            long base = NpcOutcomes.rewardFor(cap), beli0 = d.beli;
            cap.arrestedBy(h);
            boolean bonus = d.beli - beli0 == base + base * 10 / 100;
            check("a bounty hunter's licence: the path quest and two captains brought in alive; then 10% more per licence tier on the reward",
                    licensed && bonus, "rank " + rankId(d) + ", collected " + d.career.collected + ", reward " + (d.beli - beli0) + " for base " + base);
            // Bronze: tracking on wanted posters
            d.level = 12;
            for (int i = 0; i < 8 && Careers.rank(d) < 2; i++) npc(l, "pirate_captain", 6, a.offset(3, 0, 0)).arrestedBy(h);
            var t = account(s, "CareerT");
            CharacterData dt = Characters.get(t);
            place(t, a.offset(1500, 0, 0));
            ItemStack poster = Bounties.poster(dt);
            poster.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, c -> c.update(tag -> tag.putUUID("uuid", t.getUUID())));
            h.setItemInHand(InteractionHand.MAIN_HAND, poster);
            poster.getItem().use(l, h, InteractionHand.MAIN_HAND);
            boolean tracked = Careers.rank(d) == 2 && told(h, "grandline.career.track.rough") && told(h, "grandline.career.dir.e");
            var c = account(s, "CareerC");
            boolean civNo = CareerPerks.trackingHint(c, poster.get(DataComponents.CUSTOM_DATA).copyTag()) == null;
            h.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // Gold: a notable outlaw brought in alive
            d.level = 40;
            d.career.collected = Math.max(d.career.collected, 2_500_000L);
            Careers.check(h);
            boolean waits = Careers.rank(d) == 3;
            HumanoidNpc outlaw = npc(l, "pirate_captain", 7, a.offset(3, 0, -2));
            outlaw.makeNotable("career_test_outlaw", null, 0, 0, 0, "brawler");
            outlaw.arrestedBy(h);
            boolean gold = Careers.rank(d) == 4 && d.career.count("heads") == 1 && Journey.done(h, "career_hunter_gold") && news("grandline.career.announce.bounty_hunter") >= 1;
            check("a Bronze-licence hunter reads where a wanted player is from the poster (1,500 blocks east); a civilian reads nothing",
                    tracked && civNo, "rank " + rankId(d) + ", tracked " + tracked + ", " + INBOX.getOrDefault(h.getUUID(), List.of()).stream().map(JourneyTests::flat)
                            .filter(x -> x.contains("track")).findFirst().orElse("-"));
            check("Silver waits for a notable head; a notable outlaw brought in alive makes Gold (announced)", waits && gold,
                    "waits " + waits + ", rank " + rankId(d) + ", heads " + d.career.count("heads"));
            place(h, BlockPos.containing(sea(0, 0)));
            ShipEntity ship = CareerPerks.callShip(h);
            check("a Gold-licence hunter calls a hunter ship", ship != null && ship.npcFaction.equals(LivingSea.HUNTER), ship == null ? "none" : ship.shipName());
            clearSea(l);
            place(h, a);
            return 0;
        });
    }

    // ------------------------------------------------------------------ civilians

    private static void civilian(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos a = SelfTest.arena(l);
            var c = account(s, "CareerC");
            CharacterData d = create(c, "Cass Inkwell", "civilian");
            place(c, a);
            boolean towns = name(c).contains("career.grandline.civilian.townsfolk");
            List<String> isl = List.of("orange_town", "syrup_village", "baratie", "cocoyasi", "loguetown", "shells_town", "dawn_island", "mirror_ball_island");
            long beli0 = d.beli;
            for (int i = 0; i < 6; i++) {
                d.discoveredIslands.add(isl.get(i));
                QuestEvents.onDiscover(c, isl.get(i));
            }
            boolean apprentice = Careers.rank(d) == 1 && CareerTrack.specialty(d).equals("explorer") && name(c).contains("career.grandline.civilian.apprentice")
                    && name(c).contains("career.grandline.civilian.spec.explorer") && Journey.done(c, "career_civilian_apprentice");
            long chartBeli = d.beli;
            d.discoveredIslands.add(isl.get(6));
            QuestEvents.onDiscover(c, isl.get(6));
            boolean chart = d.beli - chartBeli == 150;
            BlockPos glyph = a.offset(5, 0, 5);
            l.setBlock(glyph, ModBlocks.PONEGLYPH.get().defaultBlockState(), 3);
            long glyphBeli = d.beli;
            l.getBlockState(glyph).useWithoutItem(l, c, new BlockHitResult(Vec3.atCenterOf(glyph), Direction.UP, glyph, false));
            boolean scholar = d.flags.stream().anyMatch(f -> f.startsWith("poneglyph_")) && d.beli - glyphBeli >= 1_000;
            l.setBlock(glyph, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            check("a civilian makes a name: charted islands make an Apprentice Cartographer (title with the trade, milestone); charts and a poneglyph read pay",
                    towns && apprentice && chart && scholar, "towns " + towns + ", rank " + rankId(d) + ", chart " + chart + ", scholar " + scholar);
            // merchant: trade in a shop, then a grand merchant with a discount
            GLNpc shopkeeper = npc(l, "shopkeeper", 1, a.offset(-3, 0, 0));
            d.beli = 1_000_000;
            long trade0 = d.career.trade;
            Shops.buy(c, shopkeeper.getId(), 0);
            boolean traded = d.career.trade > trade0;
            d.career.trade = 600_000;
            d.level = 35;
            Careers.check(c);
            boolean grand = Careers.rank(d) == 4 && CareerTrack.specialty(d).equals("merchant") && name(c).contains("career.grandline.civilian.grand")
                    && name(c).contains("career.grandline.civilian.spec.merchant") && Journey.done(c, "career_civilian_grand") && Journey.done(c, "career_civilian_master")
                    && Math.abs(CareerPerks.priceFactor(d) - 0.88) < 1e-6;
            check("trade in shops counts for a merchant; a Grand Merchant (announced) pays 12% less", traded && grand && news("grandline.career.announce.civilian") >= 1,
                    "traded " + traded + ", rank " + rankId(d) + ", factor " + CareerPerks.priceFactor(d));
            return 0;
        });
    }

    // ------------------------------------------------------------------ persistence (phase 2 reads it back)

    private static void persistence(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            List<String> saved = new ArrayList<>();
            for (String n : List.of("CareerM", "CareerP")) {
                var p = account(s, n);
                Path dir = s.getWorldPath(LevelResource.PLAYER_DATA_DIR);
                Files.createDirectories(dir);
                NbtIo.writeCompressed(p.saveWithoutId(new CompoundTag()), dir.resolve(p.getStringUUID() + ".dat")); // as PlayerDataStorage.save does
                saved.add(n + " " + rankId(Characters.get(p)));
            }
            CareerWorldData.get(s).setDirty();
            check("career players written to the world's playerdata for phase 2", true, String.join(", ", saved));
            return 0;
        });
    }

    // ------------------------------------------------------------------ permadeath

    private static void permadeath(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            var z = account(s, "CareerZ");
            CharacterData d = create(z, "Zed Lastwatch", "marine");
            Careers.ensure(z, d);
            d.factionRank = 10;
            d.career.merit = 4_000;
            d.level = 70;
            d.flags.add(Careers.ENLISTED);
            d.career.counts.put("sea_wins", 3L);
            d.completedQuests.addAll(List.of("sabaody_3", "war_3"));
            d.observationUnlocked = d.armamentUnlocked = true;
            CareerWorldData w = CareerWorldData.get(s);
            boolean admiral = Careers.promote(z, d, CareerTrack.MARINE) && w.holds(CareerWorldData.ADMIRAL, d.characterId);
            UUID oldId = d.characterId;
            var reborn = new JourneyTests.AccountPlayer(s.overworld(), profile("CareerZ"));
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(reborn, z, true)); // the real respawn after a true death
            CharacterData nd = Characters.get(reborn);
            boolean reset = !nd.created && nd.generation == 1 && nd.career.track.isEmpty() && nd.factionRank == 0 && nd.career.merit == 0
                    && !w.holds(CareerWorldData.ADMIRAL, oldId) && CareerTrack.title(nd) == null;
            check("careers belong to the character: a permadeath frees the Admiral's seat and the next life starts with no career", admiral && reset,
                    "admiral " + admiral + ", created " + nd.created + ", track '" + nd.career.track + "', rank " + nd.factionRank + ", seat " + w.holds(CareerWorldData.ADMIRAL, oldId));
            PLAYERS.put("CareerZ", reborn);
            return 0;
        });
    }

    // ================================================================== phase 2

    static void phase2(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            List<String> out = new ArrayList<>();
            boolean ok = true;
            CareerWorldData w = CareerWorldData.get(s);
            for (String n : List.of("CareerM", "CareerP")) {
                var p = new JourneyTests.AccountPlayer(s.overworld(), profile(n));
                boolean loaded = s.getPlayerList().load(p).isPresent(); // the player list reads the saved player as at login
                CharacterData d = Characters.get(p);
                String nm = name(p);
                if (n.equals("CareerM")) {
                    boolean m = loaded && d.created && d.faction == Faction.MARINE && d.career.track.equals("marine") && Careers.rank(d) == 12
                            && d.career.merit >= 5_000 && d.career.count("arrests") >= 3 && nm.contains("career.grandline.marine.fleet_admiral")
                            && w.holds(CareerWorldData.FLEET_ADMIRAL, d.characterId);
                    ok &= m;
                    out.add("M loaded " + loaded + " rank " + rankId(d) + " seat " + w.holds(CareerWorldData.FLEET_ADMIRAL, d.characterId));
                } else {
                    boolean held = !d.career.territories.isEmpty() && w.territory(d.career.territories.get(0)) != null
                            && w.territory(d.career.territories.get(0)).character().equals(d.characterId);
                    boolean pp = loaded && d.faction == Faction.PIRATE && Careers.rank(d) == 2 && d.career.emperorDeed && d.flags.contains(Warlords.REVOKED) && held
                            && nm.contains("career.grandline.pirate.emperor_of");
                    ok &= pp;
                    out.add("P loaded " + loaded + " rank " + rankId(d) + " territory " + held);
                }
            }
            check("careers survive a restart: the Fleet Admiral (rank, merit, tallies, seat) and the Emperor (territory, revoked letter) read back with their titles", ok,
                    String.join("; ", out));
            return 0;
        });
    }

    private CareerTests() {}
}
