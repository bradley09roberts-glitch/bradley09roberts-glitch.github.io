package com.grandline.test;

import com.grandline.dialogue.Shops;
import com.grandline.navigation.Navigation;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.ConvoyCap;
import com.grandline.quest.story.IslandStories;
import com.grandline.quest.story.JobBoard;
import com.grandline.quest.story.SeaJobs;
import com.grandline.registry.ModEntities;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Self-test of the sea polish (ship/HiredGunners, ship/RaidPlunder, quest/story/SeaJobMarks, quest/story/ConvoyCap)
 * on the dedicated server, with fake players (counted as online for the gunners through
 * {@link HiredGunners#testPlayers}; they cannot ride, so they stand on decks):
 * <ul>
 *   <li>Hired gunners: a shipwright offers them; hiring takes the fee and seats them at the guns; they fire on a pirate
 *   ship within range with the captain's shot, never on a merchantman, a Marine ship (for a civilian) or another
 *   player's ship; for a wanted pirate captain they fire on the Marines; "hold fire" stands them down; refusals (poor,
 *   no guns); dismissal.</li>
 *   <li>Raid plunder: raiders landed in a town carry off goods from its market (the shops stop selling them), beaten
 *   raiders drop them, the market restocks once the time is up.</li>
 *   <li>Chart marks: a named ship's last-seen point and a convoy's port (and her merchantman) are on the job holder's
 *   chart only.</li>
 *   <li>Convoy cap: with the NPC ship cap full the merchantman waits in port (with a message) and puts out once there
 *   is room.</li>
 * </ul>
 * Phase 2: a ship's hired gunners are back at their guns after the restart; the market's plundered goods are still
 * missing. Quick run: GL_SELFTEST=seapolish.
 */
final class SeaPolishTests {
    private static final String FIXTURE = "selftest-seapolish.txt";
    private static final Set<Long> FORCED = new HashSet<>(), TICKETS = new HashSet<>();
    private static final Map<UUID, List<String>> INBOX = new HashMap<>();

    // ------------------------------------------------------------------ helpers

    private static FakePlayer fp(ServerLevel l, String name) {
        return FakePlayerFactory.get(l, new GameProfile(UUID.nameUUIDFromBytes(("gl-seapolish-" + name).getBytes(StandardCharsets.UTF_8)), name));
    }

    private static FakePlayer character(ServerLevel l, String name, Faction f, long beli) {
        FakePlayer p = fp(l, name);
        CharacterData d = Characters.get(p);
        d.created = true;
        d.name = name;
        d.faction = f;
        d.beli = beli;
        d.bounty = 0;
        d.knockdownTicks = 0;
        HiredGunners.testPlayers.put(p.getUUID(), p);
        return p;
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : c.getString();
    }

    private static boolean got(FakePlayer p, String key) {
        return INBOX.getOrDefault(p.getUUID(), List.of()).contains(key);
    }

    private static void force(ServerLevel l, int cx, int cz) {
        if (FORCED.add(ChunkPos.asLong(cx, cz))) l.setChunkForced(cx, cz, true);
    }

    private static void loadNow(ServerLevel l, double x, double z, int r) {
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                force(l, cx + dx, cz + dz);
                l.getChunk(cx + dx, cz + dz);
            }
    }

    private static boolean ticking(ServerLevel l, double x, double z) {
        return l.isPositionEntityTicking(BlockPos.containing(x, 64, z));
    }

    private static void keep(ServerLevel l, Entity e) {
        if (e == null || e.isRemoved()) return;
        ChunkPos cp = e.chunkPosition();
        if (TICKETS.add(cp.toLong())) l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
        force(l, cp.x, cp.z);
    }

    private static void release(ServerLevel l) {
        for (long c : FORCED) l.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        for (long c : TICKETS) {
            ChunkPos cp = new ChunkPos(c);
            l.getChunkSource().removeRegionTicket(TicketType.FORCED, cp, 3, cp);
        }
        FORCED.clear();
        TICKETS.clear();
    }

    private static void scrap(ServerLevel l, ShipEntity s) {
        if (s == null || s.isRemoved()) return;
        for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s))) if (l.getEntity(u) != null) l.getEntity(u).discard();
        for (GLNpc e : l.getEntitiesOfClass(GLNpc.class, s.getBoundingBox().inflate(s.cls.length), e -> s.carries(e) || e.getVehicle() == s)) e.discard();
        NavalCombat.unlink(s);
        s.discard();
    }

    private static void clearNpcShips(ServerLevel l) {
        List<ShipEntity> all = new ArrayList<>();
        for (Entity e : l.getAllEntities()) if (e instanceof ShipEntity s && s.flag(ShipEntity.F_NPC) && !s.isRemoved()) all.add(s);
        for (ShipEntity s : all) scrap(l, s);
    }

    private static ShipEntity ship(ServerLevel l, ShipClass c, double x, double z, float yaw, String name) {
        ShipEntity s = ModEntities.SHIPS.get(c).get().create(l);
        s.moveTo(x, WorldPlan.SEA_LEVEL + 0.9, z, yaw, 0);
        s.setShipName(name);
        s.setHull(c.hull);
        l.addFreshEntity(s);
        return s;
    }

    private static ShipEntity ownShip(ServerLevel l, ServerPlayer owner, ShipClass c, double x, double z, String name) {
        ShipEntity s = ship(l, c, x, z, 0, name);
        s.owner = owner.getUUID();
        s.ownerName = owner.getName().getString();
        s.setAnchored(true);
        return s;
    }

    /** An NPC ship of a side lying still (her AI off) with one hand aboard (so her crew is not beaten). */
    private static ShipEntity npcShip(ServerLevel l, ShipClass c, double x, double z, String side, String name) {
        ShipEntity s = ship(l, c, x, z, 0, name);
        s.setFlag(ShipEntity.F_NPC, true);
        s.npcFaction = side;
        s.npcAiOff = true;
        s.noDespawn = true;
        s.setAnchored(true);
        HumanoidNpc hand = ModEntities.HUMANOID_NPC.get().create(l);
        Vec3 deck = s.toWorld(0, s.cls.freeboard + 0.05, -1);
        hand.moveTo(deck.x, deck.y, deck.z, 0, 0);
        hand.configure(side.equals(LivingSea.MARINE) ? "marine" : side.equals(LivingSea.PIRATE) ? "pirate" : "sailor", 2, "", BlockPos.ZERO, "", l.random);
        hand.setNoAi(true);
        l.addFreshEntity(hand);
        NavalCombat.npcCrew(s).add(hand.getUUID());
        return s;
    }

    private static void onDeck(ServerPlayer p, ShipEntity s) {
        Vec3 deck = s.toWorld(0, s.cls.freeboard + 0.05, 1);
        p.moveTo(deck.x, deck.y, deck.z);
    }

    private static int count(ServerPlayer p, Item it) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(it)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    /** Open water near Shiokaze Isle (plan-sampled, so it works before the chunks exist). */
    private static double[] openSea(WorldPlan p, Island home, int dx) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int r = 0; r < 40; r++) {
            int x = home.x + dx + r * 97, z = home.z - home.radius - 560 - (r % 5) * 61;
            boolean open = true;
            for (int ox = -100; ox <= 100 && open; ox += 15)
                for (int oz = -100; oz <= 140 && open; oz += 15) {
                    p.sample(x + ox, z + oz, smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 4) open = false;
                }
            if (open) return new double[]{x + 0.5, z + 0.5};
        }
        return new double[]{home.x + dx + 0.5, home.z - home.radius - 560.5};
    }

    /** Every item the island's shops (general store, shipwright, blacksmith) offer this player now: the real shop lists. */
    private static Set<String> shopItems(ServerPlayer sp, ServerLevel l, Island isl) throws Exception {
        Method offers = Shops.class.getDeclaredMethod("offers", ServerPlayer.class, GLNpc.class);
        offers.setAccessible(true);
        Set<String> out = new HashSet<>();
        for (String role : List.of("shopkeeper", "shipwright", "blacksmith")) {
            HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(l); // not added to the world: only asked what it sells
            npc.configure(role, Math.max(1, isl.tier), isl.id, BlockPos.ZERO, "", l.random);
            for (Object o : (List<?>) offers.invoke(null, sp, npc)) {
                Method im = o.getClass().getDeclaredMethod("item");
                im.setAccessible(true);
                out.add((String) im.invoke(o));
            }
        }
        return out;
    }

    private static List<CompoundTag> marks(ServerPlayer sp, String kind) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag t = Navigation.chartData(sp);
        if (t == null) return out;
        ListTag l = t.getList("seajobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) if (kind == null || l.getCompound(i).getString("kind").equals(kind)) out.add(l.getCompound(i));
        return out;
    }

    // ------------------------------------------------------------------ phase 1

    static void phase1(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            SeaJobs.tap = (p, m) -> INBOX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(key(m));
            return 0;
        });
        gunners(steps);
        plunder(steps);
        chart(steps);
        convoyCap(steps);
        fixture(steps);
        steps.add(s -> {
            SeaJobs.tap = null;
            RaidPlunder.takeAfter = 300;
            release(s.overworld());
            return 0;
        });
    }

    // ---------- hired gunners ----------
    private static void gunners(Deque<SelfTest.Step> steps) {
        // 0 captain's ship, 1 pirate, 2 merchant, 3 marine, 4 other player's ship, 5 rowboat
        final ShipEntity[] sh = new ShipEntity[8];
        final Object[] b = new Object[6]; // 0 captain, 1 shipwright, 2 rival
        final double[] at = new double[2];
        final double[] num = new double[12];
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan p = WorldPlan.of(s);
            double[] o = openSea(p, p.byId("shiokaze_isle"), 2300);
            at[0] = o[0];
            at[1] = o[1];
            loadNow(l, at[0], at[1], 4);
            clearNpcShips(l);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            if (!ticking(l, at[0], at[1]) && ++waited[0] < 2000) return -1;
            FakePlayer cap = character(l, "GunCaptain", Faction.CIVILIAN, 60_000);
            b[0] = cap;
            sh[0] = ownShip(l, cap, ShipClass.CARAVEL, at[0], at[1], "Selftest Gunnery");
            onDeck(cap, sh[0]);
            HumanoidNpc wright = ModEntities.HUMANOID_NPC.get().create(l);
            Vec3 deck = sh[0].toWorld(0, sh[0].cls.freeboard + 0.05, -2);
            wright.moveTo(deck.x, deck.y, deck.z, 0, 0);
            wright.configure("shipwright", 2, "shiokaze_isle", BlockPos.ZERO, "", l.random);
            wright.setNoAi(true);
            l.addFreshEntity(wright);
            b[1] = wright;
            ListTag opts = new ListTag();
            HiredGunners.addOptions(cap, wright, opts);
            boolean offered = opts.stream().anyMatch(t -> t.getAsString().equals("gunners"));
            // too poor for one gunner: refused, nothing taken
            CharacterData d = Characters.get(cap);
            long fee = HiredGunners.fee(wright);
            d.beli = fee - 1;
            int poor = HiredGunners.hire(cap, wright, 1);
            boolean poorOk = poor == 0 && d.beli == fee - 1 && HiredGunners.gunnersOf(sh[0]).isEmpty();
            d.beli = 60_000;
            int n = HiredGunners.hire(cap, wright, 2);
            List<HumanoidNpc> gs = HiredGunners.gunnersOf(sh[0]);
            StringBuilder seats = new StringBuilder();
            boolean atGuns = gs.size() == 2;
            for (HumanoidNpc g : gs) {
                int seat = sh[0].seatIndex(g);
                seats.append(seat).append(' ');
                if (seat < 1 || seat > sh[0].cls.cannons || g.getVehicle() != sh[0]) atGuns = false;
            }
            SelfTest.check("sea polish: a shipwright offers gunners; too poor for one, nothing is taken", offered && poorOk,
                    "option offered=" + offered + ", poor hire " + poor + " (fee " + fee + "), gunners " + HiredGunners.gunnersOf(sh[0]).size());
            SelfTest.check("sea polish: hiring takes the Beli and puts the gunners at the guns", n == 2 && d.beli == 60_000 - 2 * fee && atGuns && sh[0].flag(ShipEntity.F_FIRE_AT_WILL),
                    n + " hired for " + (60_000 - d.beli) + " Beli (fee " + fee + " each), seats " + seats.toString().trim() + " of " + sh[0].cls.cannons + " guns, fire at will "
                            + sh[0].flag(ShipEntity.F_FIRE_AT_WILL));
            // a pirate ship lies abeam, 34 blocks off
            sh[1] = npcShip(l, ShipClass.SLOOP, at[0] + 34, at[1], LivingSea.PIRATE, "Selftest Corsair");
            cap.getInventory().add(new ItemStack(item("grandline:cannonball"), 16));
            num[0] = HiredGunners.shots;
            num[1] = sh[1].hull();
            num[2] = count(cap, item("grandline:cannonball"));
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer cap = (FakePlayer) b[0];
            if ((HiredGunners.shots - num[0] < 2 || sh[1].hull() >= num[1]) && ++waited[0] < 500) return -1;
            int fired = (int) (HiredGunners.shots - num[0]);
            int used = (int) num[2] - count(cap, item("grandline:cannonball"));
            SelfTest.check("sea polish: hired gunners fire on a pirate ship within range, with the captain's shot", fired > 0 && sh[1].hull() < num[1] && (cap.isCreative() || used == fired),
                    String.format("%d shots in %d ticks, pirate hull %.0f -> %.0f, %d cannonballs taken from the captain", fired, waited[0], num[1], sh[1].hull(), used));
            scrap(l, sh[1]);
            return 80; // let the shots still in flight fall
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            // friendly ships around her: a merchantman, a Marine ship (the captain is a civilian), another player's ship
            sh[2] = npcShip(l, ShipClass.SLOOP, at[0] + 34, at[1], LivingSea.MERCHANT, "Selftest Trader");
            sh[3] = npcShip(l, ShipClass.SLOOP, at[0] - 34, at[1], LivingSea.MARINE, "Selftest Patrol");
            FakePlayer rival = character(l, "GunRival", Faction.PIRATE, 1000);
            b[2] = rival;
            sh[4] = ownShip(l, rival, ShipClass.SLOOP, at[0] + 30, at[1] + 14, "Selftest Rival");
            num[0] = HiredGunners.shots;
            num[3] = sh[2].hull();
            num[4] = sh[3].hull();
            num[5] = sh[4].hull();
            waited[0] = 0;
            return 260;
        });
        steps.add(s -> {
            SelfTest.check("sea polish: hired gunners hold fire on a merchantman, a Marine ship and another player's ship", HiredGunners.shots == num[0]
                            && sh[2].hull() >= num[3] && sh[3].hull() >= num[4] && sh[4].hull() >= num[5],
                    (int) (HiredGunners.shots - num[0]) + " shots in 260 ticks; hulls " + (int) sh[2].hull() + "/" + (int) sh[3].hull() + "/" + (int) sh[4].hull());
            // the captain turns pirate with a bounty: the Marine ship is now the enemy
            FakePlayer cap = (FakePlayer) b[0];
            CharacterData d = Characters.get(cap);
            d.faction = Faction.PIRATE;
            d.bounty = 5_000_000;
            num[0] = HiredGunners.shots;
            num[3] = sh[2].hull();
            num[4] = sh[3].hull();
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if ((HiredGunners.shots == num[0] || sh[3].hull() >= num[4]) && ++waited[0] < 500) return -1;
            SelfTest.check("sea polish: for a wanted pirate captain the hired gunners fire on a Marine ship (still not on the merchantman)",
                    HiredGunners.shots > num[0] && sh[3].hull() < num[4] && sh[2].hull() >= num[3],
                    (int) (HiredGunners.shots - num[0]) + " shots after " + waited[0] + " ticks; merchant hull " + (int) sh[2].hull() + ", Marine hull " + (int) num[4] + " -> " + (int) sh[3].hull());
            NavalCombat.setFireAtWill(sh[0], null, false); // the captain orders "hold fire"
            num[0] = HiredGunners.shots;
            return 200;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer cap = (FakePlayer) b[0];
            SelfTest.check("sea polish: \"hold fire\" stands the hired gunners down", HiredGunners.shots == num[0], (int) (HiredGunners.shots - num[0]) + " shots in 200 ticks after the order");
            CharacterData d = Characters.get(cap);
            d.faction = Faction.CIVILIAN;
            d.bounty = 0;
            for (int i = 2; i <= 4; i++) scrap(l, sh[i]);
            // a gunner thrown off his station goes back to a gun
            HumanoidNpc off = HiredGunners.gunnersOf(sh[0]).get(0);
            off.stopRiding();
            b[3] = off;
            num[6] = off.isPassenger() ? 1 : 0;
            return 60;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer cap = (FakePlayer) b[0];
            CharacterData d = Characters.get(cap);
            HumanoidNpc off = (HumanoidNpc) b[3];
            int seat = sh[0].seatIndex(off);
            SelfTest.check("sea polish: a hired gunner knocked off his station goes back to a gun", num[6] == 0 && off.getVehicle() == sh[0] && seat >= 1 && seat <= sh[0].cls.cannons,
                    "off the gun: " + (num[6] == 0) + ", 60 ticks later seat " + seat);
            // a rowboat has no guns
            sh[5] = ownShip(l, cap, ShipClass.ROWBOAT, at[0] - 20, at[1] + 30, "Selftest Dinghy");
            onDeck(cap, sh[5]);
            long beli = d.beli;
            int none = HiredGunners.hire(cap, (GLNpc) b[1], 1);
            onDeck(cap, sh[0]);
            scrap(l, sh[5]);
            // dismissal: they go ashore, no refund
            long before = d.beli;
            int gone = HiredGunners.dismiss(cap);
            int left = HiredGunners.gunnersOf(sh[0]).size();
            int seated = 0;
            for (Entity e : sh[0].getPassengers()) if (HiredGunners.isHired(e)) seated++;
            SelfTest.check("sea polish: no gunners for a ship without cannons", none == 0 && d.beli == beli, "hired " + none + ", Beli " + beli + " -> " + d.beli);
            SelfTest.check("sea polish: dismissed gunners leave the ship (no refund)", gone == 2 && left == 0 && seated == 0 && d.beli == before,
                    gone + " dismissed, " + left + " left aboard, " + seated + " at the guns, Beli " + before + " -> " + d.beli);
            ((GLNpc) b[1]).discard();
            scrap(l, sh[0]);
            return 0;
        });
    }

    // ---------- harbour raids carry off the market's goods ----------
    private static void plunder(Deque<SelfTest.Step> steps) {
        final Object[] b = new Object[6]; // 0 town, 1 raid ship, 2 shop list before, 3 defender
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan p = WorldPlan.of(s);
            Island home = p.byId("shiokaze_isle");
            Island town = null;
            for (Island i : p.islands)
                if (LivingSea.raidable(i) && i.zone == home.zone && (town == null || i.distance(home.x, home.z) < town.distance(home.x, home.z))) town = i;
            if (town == null) {
                SelfTest.check("sea polish: raiders carry off goods from the town's market", false, "no raidable town in " + home.zone.id());
                return 0;
            }
            b[0] = town;
            Settlement st = town.settlement();
            loadNow(l, st.dockTipX(), st.dockTipZ(), 7);
            RaidPlunder.restockNow(s, town.id);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            Island town = (Island) b[0];
            Settlement st = town.settlement();
            if ((!ticking(l, st.dockTipX(), st.dockTipZ()) || HarbourRaids.landingSpots(l, town).isEmpty()) && ++waited[0] < 2000) return -1;
            clearNpcShips(l);
            FakePlayer shopper = character(l, "MarketShopper", Faction.CIVILIAN, 1000);
            b[2] = shopItems(shopper, l, town);
            ShipEntity r = HarbourRaids.start(l, town, new Vec3(st.dockTipX(), 64, st.dockTipZ()), 100);
            if (r == null) {
                SelfTest.check("sea polish: raiders carry off goods from the town's market", false, "no raid ship at " + town.id);
                b[0] = null;
                return 0;
            }
            r.noDespawn = true;
            b[1] = r;
            RaidPlunder.takeAfter = 0;
            waited[0] = 0;
            return 20;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            ShipEntity r = (ShipEntity) b[1];
            Island town = (Island) b[0];
            keep(l, r);
            boolean landed = RaidPlunder.landNow(r, l, town);
            if (!landed) {
                SelfTest.check("sea polish: raiders carry off goods from the town's market", false, "the raid ship could not land her party at " + town.id);
                b[0] = null;
                return 0;
            }
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            ShipEntity r = (ShipEntity) b[1];
            Island town = (Island) b[0];
            keep(l, r);
            if (RaidPlunder.missing(s, town.id).size() < 2 && r.life.raidPhase == HarbourRaids.ASHORE && ++waited[0] < 300) return -1;
            List<RaidPlunder.Taken> gone = RaidPlunder.missing(s, town.id);
            @SuppressWarnings("unchecked") Set<String> before = (Set<String>) b[2];
            Set<String> now = shopItems(character(l, "MarketShopper", Faction.CIVILIAN, 1000), l, town);
            int carriers = 0;
            for (GLNpc m : HarbourRaids.raiders(r, l)) if (!RaidPlunder.carried(m).isEmpty()) carriers++;
            boolean shopsShort = !gone.isEmpty();
            for (RaidPlunder.Taken t : gone) if (!before.contains(t.item()) || now.contains(t.item())) shopsShort = false;
            StringBuilder what = new StringBuilder();
            for (RaidPlunder.Taken t : gone) what.append(t.count()).append(' ').append(t.item()).append(", ");
            SelfTest.check("sea polish: raiders carry off goods from the town's market (its shops stop selling them)", gone.size() >= 1 && shopsShort,
                    town.id + ": " + what + carriers + " raiders still carrying, shop goods " + before.size() + " -> " + now.size() + " after " + waited[0] + " ticks");
            // a defender cuts the raiders down
            FakePlayer de = character(l, "MarketDefender", Faction.CIVILIAN, 1000);
            for (GLNpc m : HarbourRaids.raiders(r, l)) if (m.isAlive()) {
                de.moveTo(m.getX() + 1, m.getY(), m.getZ());
                m.hurt(de.damageSources().playerAttack(de), 5000f);
            }
            de.moveTo(town.x + 900, 90, town.z + 900);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            ShipEntity r = (ShipEntity) b[1];
            Island town = (Island) b[0];
            keep(l, r);
            if (r.life.raidPhase != HarbourRaids.LEAVING && ++waited[0] < 300) return -1;
            List<RaidPlunder.Taken> gone = RaidPlunder.missing(s, town.id);
            Settlement st = town.settlement();
            AABB box = new AABB(st.dockTipX() - 120, 0, st.dockTipZ() - 120, st.dockTipX() + 120, 256, st.dockTipZ() + 120);
            Map<String, Integer> dropped = new HashMap<>();
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, box))
                dropped.merge(BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).toString(), ie.getItem().getCount(), Integer::sum);
            boolean all = !gone.isEmpty();
            for (RaidPlunder.Taken t : gone) if (dropped.getOrDefault(t.item(), 0) < t.count()) all = false;
            int stillCarrying = 0;
            for (GLNpc m : HarbourRaids.raiders(r, l)) if (!RaidPlunder.carried(m).isEmpty()) stillCarrying++;
            SelfTest.check("sea polish: the beaten raiders drop the goods they carried off", r.life.raidPhase == HarbourRaids.LEAVING && all && stillCarrying == 0,
                    "raid phase " + r.life.raidPhase + " after " + waited[0] + " ticks; taken " + gone.size() + " goods, on the ground " + dropped + ", still carried by " + stillCarrying);
            // restocked once the time is up (the first good aged past its restock time), the rest still missing
            if (!gone.isEmpty()) {
                RaidPlunder.Taken first = gone.get(0);
                RaidPlunder.age(s, town.id, first.item(), RaidPlunder.restockTicks + 1);
                Set<String> now = shopItems(character(l, "MarketShopper", Faction.CIVILIAN, 1000), l, town);
                boolean others = true;
                for (int i = 1; i < gone.size(); i++) if (now.contains(gone.get(i).item())) others = false;
                SelfTest.check("sea polish: the market restocks a plundered good once its time is up", now.contains(first.item()) && others && RaidPlunder.missing(s, town.id).size() == gone.size() - 1,
                        first.item() + " back in the shops: " + now.contains(first.item()) + ", " + (gone.size() - 1) + " others still missing: " + others);
            }
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, box)) ie.discard();
            for (Entity e : l.getEntitiesOfClass(GLNpc.class, box, e -> e.getTags().contains(LivingSea.RAIDER) || e.getTags().contains(LivingSea.RESPONDER))) e.discard();
            scrap(l, r);
            return 0;
        });
    }

    // ---------- sea-job marks on the chart ----------
    private static String job(MinecraftServer s, String kind) {
        WorldPlan plan = WorldPlan.of(s);
        for (Island i : plan.islands) {
            String id = "job_" + kind + "_" + i.id;
            if (QuestRegistry.get(id) != null && JobBoard.gift(id) != null) return id;
        }
        return null;
    }

    private static void chart(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = s.overworld();
            IslandStories.ensure(s);
            String id = job(s, SeaJobs.SINK);
            if (id == null) {
                SelfTest.check("sea polish: a named ship's last-seen point is on the job holder's chart only", false, "no sink job");
                return 0;
            }
            JobBoard.StartGift g = JobBoard.gift(id);
            FakePlayer holder = character(l, "ChartHunter", Faction.CIVILIAN, 1000), other = character(l, "ChartOther", Faction.CIVILIAN, 1000);
            holder.moveTo(g.x() + 2000, 90, g.z() + 2000); // far from her, so she is not put to sea
            other.moveTo(g.x() + 2000, 90, g.z() + 2000);
            boolean started = Quests.start(holder, id);
            List<CompoundTag> mine = marks(holder, "sink"), theirs = marks(other, null);
            boolean right = mine.size() == 1 && mine.get(0).getInt("x") == g.x() && mine.get(0).getInt("z") == g.z() && mine.get(0).getString("name").equals(g.name());
            SelfTest.check("sea polish: a named ship's last-seen point is on the job holder's chart only", started && right && theirs.isEmpty(),
                    id + ": holder marks " + mine + ", other player's sea-job marks " + theirs.size());
            SeaJobs.abandon(holder, id);
            SelfTest.check("sea polish: the mark goes with the job", marks(holder, null).isEmpty(), marks(holder, null).size() + " sea-job marks after abandoning");
            return 0;
        });
    }

    // ---------- the convoy's merchantman respects the NPC ship cap (and her marks) ----------
    private static void convoyCap(Deque<SelfTest.Step> steps) {
        final Object[] b = new Object[6]; // 0 job, 1 origin, 2 player, 3 fillers
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel l = s.overworld();
            String id = null;
            WorldPlan plan = WorldPlan.of(s);
            for (Island i : plan.islands) {
                String q = "job_convoy_" + i.id;
                JobBoard.StartGift g = JobBoard.gift(q);
                if (QuestRegistry.get(q) != null && g != null && i.settlement().hasDock && LivingSea.openZone(i.zone)) {
                    id = q;
                    break;
                }
            }
            if (id == null) {
                SelfTest.check("sea polish: a convoy waits in port while the NPC ship cap is full", false, "no convoy job");
                return 0;
            }
            Island origin = plan.byId(QuestRegistry.get(id).island());
            b[0] = id;
            b[1] = origin;
            Vec3 od = anchorage(origin);
            loadNow(l, od.x, od.z, 4);
            loadNow(l, origin.settlement().dockX, origin.settlement().dockZ, 2);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            Island origin = (Island) b[1];
            Vec3 od = anchorage(origin);
            if ((!ticking(l, od.x, od.z) || !ticking(l, origin.settlement().dockX, origin.settlement().dockZ)) && ++waited[0] < 2000) return -1;
            clearNpcShips(l);
            int cap = SeaEncounters.cap(l.players().size());
            List<ShipEntity> fill = new ArrayList<>();
            for (int k = 0; k < cap; k++) fill.add(npcShip(l, ShipClass.SLOOP, od.x - 40 + k * 16, od.z + 50, LivingSea.PIRATE, "Selftest Filler " + k));
            b[3] = fill;
            FakePlayer p = character(l, "ConvoyWaiter", Faction.CIVILIAN, 1000);
            b[2] = p;
            String id = (String) b[0];
            BlockPos quay = NotableSpawner.findStand(l, (int) origin.settlement().dockX, (int) origin.settlement().dockZ, 12);
            if (quay != null) p.moveTo(quay.getX() + 0.5, quay.getY(), quay.getZ() + 0.5);
            int heldBefore = ConvoyCap.held;
            boolean started = Quests.start(p, id);
            p.moveTo(od.x + 30, WorldPlan.SEA_LEVEL - 0.5, od.z);
            SeaJobs.tickPlayer(p);
            SeaJobs.tickPlayer(p);
            SeaJobs.Convoy c = SeaJobs.convoyOf(s, Characters.get(p).characterId, id);
            ShipEntity ship = c == null ? null : SeaJobs.shipOf(s, c);
            JobBoard.StartGift g = JobBoard.gift(id);
            List<CompoundTag> port = marks(p, "convoy_port");
            SelfTest.check("sea polish: a convoy waits in port while the NPC ship cap is full (with a message)", started && c != null && ship == null && c.stage == 0
                            && ConvoyCap.held > heldBefore && got(p, "job.grandline.convoy.held") && SeaEncounters.countNpcShips(l) == cap,
                    id + ": started " + started + ", stage " + (c == null ? -1 : c.stage) + ", ship " + (ship == null ? "none" : ship.shipName()) + ", held " + (ConvoyCap.held - heldBefore)
                            + " times, " + SeaEncounters.countNpcShips(l) + "/" + cap + " NPC ships");
            SelfTest.check("sea polish: the convoy's port is on the job holder's chart", port.size() == 1 && port.get(0).getInt("x") == g.x() && port.get(0).getInt("z") == g.z()
                            && port.get(0).getString("island").equals(g.target()) && marks(p, "convoy_ship").isEmpty(),
                    "port marks " + port + ", merchantman marks before she sails " + marks(p, "convoy_ship").size());
            @SuppressWarnings("unchecked") List<ShipEntity> fillers = (List<ShipEntity>) b[3];
            scrap(l, fillers.remove(0)); // room for one
            SeaJobs.tickPlayer(p);
            ShipEntity now = c == null ? null : SeaJobs.shipOf(s, c);
            List<CompoundTag> at = marks(p, "convoy_ship");
            SelfTest.check("sea polish: with room under the cap she puts out", now != null && c.stage == 1,
                    now == null ? "still no ship" : "the " + now.shipName() + " at " + now.blockPosition().toShortString() + ", stage " + c.stage);
            SelfTest.check("sea polish: the convoy's merchantman is marked where she lies", now != null && at.size() == 1 && Math.abs(at.get(0).getInt("x") - now.getBlockX()) <= 1
                            && Math.abs(at.get(0).getInt("z") - now.getBlockZ()) <= 1,
                    "marks " + at + (now == null ? "" : ", ship at " + now.blockPosition().toShortString()));
            SeaJobs.abandon(p, id);
            for (ShipEntity f : fillers) scrap(l, f);
            if (now != null) scrap(l, now);
            clearNpcShips(l);
            return 0;
        });
    }

    private static Vec3 anchorage(Island i) {
        Settlement st = i.settlement();
        double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0, ul = Math.max(1e-6, Math.hypot(ux, uz));
        double out = i.radius * 1.25 + 40;
        return new Vec3(i.x + ux / ul * out, WorldPlan.SEA_LEVEL + 0.9, i.z + uz / ul * out);
    }

    // ---------- phase-2 fixture: a ship with hired gunners, a market short of plundered goods ----------
    private static void fixture(Deque<SelfTest.Step> steps) {
        final ShipEntity[] sh = new ShipEntity[1];
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan p = WorldPlan.of(s);
            double[] o = openSea(p, p.byId("shiokaze_isle"), 2300);
            loadNow(l, o[0], o[1] + 100, 2);
            return 1;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan p = WorldPlan.of(s);
            double[] o = openSea(p, p.byId("shiokaze_isle"), 2300);
            if (!ticking(l, o[0], o[1] + 100) && ++waited[0] < 2000) return -1;
            // a fixture ship left by an earlier run on the same world (quick runs reuse it) goes first
            for (ShipEntity old : l.getEntitiesOfClass(ShipEntity.class, new AABB(o[0] - 40, 0, o[1] + 60, o[0] + 40, 256, o[1] + 140))) {
                for (HumanoidNpc g : HiredGunners.gunnersOf(old)) g.discard();
                old.discard();
            }
            FakePlayer cap = character(l, "GunKeeper", Faction.CIVILIAN, 60_000);
            sh[0] = ownShip(l, cap, ShipClass.CARAVEL, o[0], o[1] + 100, "Selftest Gun Deck");
            onDeck(cap, sh[0]);
            HumanoidNpc wright = ModEntities.HUMANOID_NPC.get().create(l);
            wright.moveTo(cap.getX(), cap.getY(), cap.getZ() + 1, 0, 0);
            wright.configure("dockworker", 2, "shiokaze_isle", BlockPos.ZERO, "", l.random);
            wright.setNoAi(true);
            l.addFreshEntity(wright);
            int n = HiredGunners.hire(cap, wright, 2);
            wright.discard();
            List<Integer> seatList = new ArrayList<>();
            for (Entity g : sh[0].getPassengers()) if (HiredGunners.isHired(g)) seatList.add(sh[0].seatIndex(g));
            Collections.sort(seatList);
            String seats = seatList.isEmpty() ? "-" : seatList.toString().replace(" ", "");
            // the market of a town short of a plundered good (from the raid above, if any good is still missing)
            String market = "", goods = "";
            for (Island i : p.islands) {
                List<RaidPlunder.Taken> gone = RaidPlunder.missing(s, i.id);
                if (gone.isEmpty()) continue;
                market = i.id;
                StringBuilder g = new StringBuilder();
                for (RaidPlunder.Taken t : gone) g.append(t.item()).append(',');
                goods = g.toString();
                break;
            }
            try {
                Files.writeString(Path.of(FIXTURE), sh[0].getUUID() + " " + (int) Math.floor(sh[0].getX()) + " " + (int) Math.floor(sh[0].getZ()) + " " + n + " " + seats
                        + " " + (market.isEmpty() ? "-" : market) + " " + (goods.isEmpty() ? "-" : goods));
            } catch (Exception e) {
                SelfTest.check("sea polish: phase-2 fixture written", false, e.toString());
            }
            SelfTest.check("sea polish: phase-2 fixture (a caravel with two hired gunners)", n == 2 && seatList.size() == 2 && HiredGunners.gunnersOf(sh[0]).size() == 2, n +" gunners at seats " + seats + "; market short of goods: " + (market.isEmpty() ? "none" : market + " " + goods));
            return 0;
        });
    }

    // ------------------------------------------------------------------ phase 2

    static void phase2(Deque<SelfTest.Step> steps) {
        final String[] fx = new String[1];
        final int[] waited = {0};
        steps.add(s -> {
            Path f = Path.of(FIXTURE);
            if (!Files.exists(f)) {
                SelfTest.check("sea polish: hired gunners are back at their guns after the restart", false, "no fixture");
                return 0;
            }
            fx[0] = Files.readString(f).trim();
            String[] p = fx[0].split(" ");
            int x = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            ServerLevel l = s.overworld();
            ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
            l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 2, cp);
            l.getChunk(cp.x, cp.z);
            return 1;
        });
        steps.add(s -> {
            if (fx[0] == null) return 0;
            String[] p = fx[0].split(" ");
            ServerLevel l = s.overworld();
            Entity e = l.getEntity(UUID.fromString(p[0]));
            if (!(e instanceof ShipEntity ship) || ship.getPassengers().size() < Integer.parseInt(p[3])) {
                if (++waited[0] < 400) return -1;
            }
            Entity e2 = l.getEntity(UUID.fromString(p[0]));
            if (!(e2 instanceof ShipEntity ship)) {
                SelfTest.check("sea polish: hired gunners are back at their guns after the restart", false, "ship not found after " + waited[0] + " ticks");
                return 0;
            }
            List<Integer> seatList = new ArrayList<>();
            int atGuns = 0;
            for (Entity g : ship.getPassengers())
                if (HiredGunners.isHired(g)) {
                    int seat = ship.seatIndex(g);
                    seatList.add(seat);
                    if (seat >= 1 && seat <= ship.cls.cannons) atGuns++;
                }
            Collections.sort(seatList);
            String seats = seatList.isEmpty() ? "-" : seatList.toString().replace(" ", "");
            SelfTest.check("sea polish: hired gunners are back at their guns after the restart", atGuns == Integer.parseInt(p[3]) && seats.equals(p[4])
                            && HiredGunners.gunnersOf(ship).size() == atGuns,
                    atGuns + " hired gunners at seats " + seats + " (before the restart " + p[3] + " at " + p[4] + ")");
            if (!p[5].equals("-")) {
                List<String> still = new ArrayList<>();
                for (RaidPlunder.Taken t : RaidPlunder.missing(s, p[5])) still.add(t.item());
                boolean all = true;
                for (String it : p[6].split(",")) if (!it.isEmpty() && !still.contains(it)) all = false;
                SelfTest.check("sea polish: a market's plundered goods are still missing after the restart", all, p[5] + ": missing " + still + " (before the restart " + p[6] + ")");
            }
            return 0;
        });
    }

    private SeaPolishTests() {}
}
