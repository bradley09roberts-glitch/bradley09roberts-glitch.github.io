package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.combat.*;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.fruit.*;
import com.grandline.item.GLWeaponItem;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.MarkerCollector;
import com.grandline.player.*;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.SettlementType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * Automated verification on a real dedicated server. Enabled with environment variable GL_SELFTEST=1 (phase 1:
 * gameplay + writes persistence fixtures) or GL_SELFTEST=2 (phase 2: verifies state after a full restart).
 * Results are written to selftest-phase<N>.txt in the server directory, then the server stops.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class SelfTest {
    private static final String PHASE = System.getenv("GL_SELFTEST");
    private static final List<String> REPORT = new ArrayList<>();
    private static int passed, failed;
    private static Deque<Step> steps;
    private static int wait;
    private static FakePlayer fake;
    private static final UUID FAKE_ID = UUID.fromString("6c1a4e2b-0b9e-4f43-9d61-5e7e4e1a2b3c");

    interface Step {
        /** @return ticks to wait before the next step (0 = continue immediately), or -1 to repeat this step next tick. */
        int run(MinecraftServer s) throws Exception;
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        String line = (ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " — " + detail);
        REPORT.add(line);
        GrandLine.LOGGER.info("[SELFTEST] {}", line);
    }

    @SubscribeEvent
    public static void onStart(ServerStartedEvent e) {
        if (PHASE == null || PHASE.equals("client") || PHASE.equals("load")) return; // "load" is LoadTest
        steps = new ArrayDeque<>();
        REPORT.add("Grand Line Odyssey self-test, phase " + PHASE + ", " + new Date());
        if (PHASE.equals("1")) phase1();
        else if (PHASE.equals("dims")) {
            steps.add(s -> LandmarkCheck.run(s, SelfTest::check));
            DimensionTests.ferry(steps);
            DimensionTests.holyLand(steps);
            DimensionTests.settledIslands(steps);
            DimensionTests.awakening(steps);
            DimensionTests.shipClasses(steps);
            DimensionTests.phase1(steps);
        }
        else if (PHASE.equals("naval")) NavalTests.phase1(steps);
        else if (PHASE.equals("livingsea")) LivingSeaTests.phase1(steps); // quick run of the living-sea steps only
        else if (PHASE.equals("creatures")) CreatureSelfTest.phase1(steps); // quick run of the creature steps only
        else if (PHASE.equals("stories")) { // quick run: the existing story checks, then island stories and jobs
            IslandStoryTests.prelude(steps);
            storySteps();
            StoryTestsExtra.phase1(steps);
            IslandStoryTests.phase1(steps);
        }
        else if (PHASE.equals("activities")) ActivityTests.phase1(steps); // quick run: treasure maps, fishing, cooking
        else if (PHASE.equals("journey")) JourneyTests.phase1(steps); // quick run: advancements, Den Den Mushi, Buster Call
        else if (PHASE.equals("bosses")) BossTests.phase1(steps); // quick run: boss fights
        else if (PHASE.equals("structures")) StructureDump.steps(steps); // dev aid: every structure to structdump/*.json
        else if (PHASE.equals("quests")) steps.add(SelfTest::questTextCheck); // quick run: quest descriptions and objective text
        else if (PHASE.equals("landmarks")) { // quick run: every landmark builder and every settlement building
            steps.add(s -> LandmarkCheck.run(s, SelfTest::check));
            steps.add(s -> BuildingCheck.run(SelfTest::check));
        }
        else if (PHASE.equals("bases")) BaseTests.phase1(steps); // quick run: crew bases on claimed islands
        else if (PHASE.equals("careers")) CareerTests.phase1(steps); // quick run: faction careers
        else if (PHASE.equals("crew")) CrewTests.phase1(steps); // quick run: crew companions (roles, orders, swaps, downed, credit, saves)
        else if (PHASE.equals("seapolish")) SeaPolishTests.phase1(steps); // quick run: hired gunners, raid plunder, sea-job chart marks, convoy cap
        else if (PHASE.equals("seapolish2")) SeaPolishTests.phase2(steps); // its restart check, on the world the quick run left
        else if (PHASE.equals("seajobs")) { // quick run: escorts, named pirate ships, convoys
            SeaJobTests.phase1(steps);
            SeaJobTests.fixture(steps);
        }
        else phase2();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (steps == null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        MinecraftServer s = e.getServer();
        Step step = steps.peekFirst();
        if (step == null) {
            finish(s);
            return;
        }
        try {
            int w = step.run(s);
            if (w >= 0) {
                steps.pollFirst();
                wait = w;
            }
        } catch (Throwable t) {
            check("step crashed", false, t + " @ " + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : ""));
            GrandLine.LOGGER.error("[SELFTEST] step crashed", t);
            steps.pollFirst();
        }
    }

    private static void finish(MinecraftServer s) {
        REPORT.add("TOTAL: " + passed + " passed, " + failed + " failed");
        try {
            Files.write(Path.of("selftest-phase" + PHASE + ".txt"), REPORT);
        } catch (IOException ex) {
            GrandLine.LOGGER.error("could not write report", ex);
        }
        GrandLine.LOGGER.info("[SELFTEST] DONE {} passed {} failed", passed, failed);
        steps = null;
        s.saveEverything(true, true, true);
        s.halt(false);
    }

    /** Every quest has a description and every objective reads as words (shown when it starts and by /quests). */
    static int questTextCheck(MinecraftServer s) {
        List<String> noDesc = new ArrayList<>(), rawObj = new ArrayList<>();
        int quests = 0;
        for (com.grandline.quest.QuestDef q : com.grandline.quest.QuestRegistry.all()) {
            quests++;
            if (com.grandline.quest.QuestText.description(q) == null) noDesc.add(q.id());
            for (com.grandline.quest.QuestDef.Objective o : q.objectives()) {
                String text = com.grandline.quest.QuestText.objective(s, o).getString();
                if (text.startsWith("grandline.quest.obj") || !(com.grandline.quest.QuestText.objective(s, o).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents))
                    rawObj.add(q.id() + ":" + o.type() + "=" + text);
            }
        }
        check("every quest has a description and readable objectives", quests > 100 && noDesc.isEmpty() && rawObj.isEmpty(),
                quests + " quests" + (noDesc.isEmpty() ? "" : "; no description: " + noDesc.subList(0, Math.min(15, noDesc.size())) + (noDesc.size() > 15 ? " +" + (noDesc.size() - 15) : ""))
                        + (rawObj.isEmpty() ? "" : "; untranslated objectives: " + rawObj.subList(0, Math.min(10, rawObj.size()))));
        net.minecraft.network.chat.Component desc = com.grandline.quest.QuestText.description(com.grandline.quest.QuestRegistry.get("ember_east_1"));
        String line = com.grandline.quest.QuestText.objective(s, com.grandline.quest.QuestRegistry.get("ember_east_1").objectives().get(0)).getString();
        { // the quest guide: a route from Shiokaze Isle's town to Shells Town runs to this pier, across the sea, to that pier
            com.grandline.world.WorldPlan plan = com.grandline.world.WorldPlan.of(s);
            com.grandline.world.Island from = plan.byId("shiokaze_isle"), to = plan.byId("shells_town");
            FakePlayer gp = fake(s);
            var fs = from.settlement();
            gp.moveTo(fs.centerX + 0.5, 90, fs.centerZ + 0.5);
            var tg = com.grandline.quest.QuestGuide.island(gp, plan, Characters.get(gp), "shells_town");
            int[] r = tg == null ? new int[0] : com.grandline.quest.QuestGuide.route(gp, tg);
            boolean viaPiers = r.length >= 8 && Math.abs(r[r.length - 2] - tg.x()) <= 1 && Math.abs(r[r.length - 1] - tg.z()) <= 1;
            boolean throughFromPier = false;
            for (int i = 0; i + 1 < r.length; i += 2) if (r[i] == fs.dockTipX() && r[i + 1] == fs.dockTipZ()) throughFromPier = true;
            check("quest guide: route to another island runs through both piers", tg != null && viaPiers && throughFromPier,
                    (tg == null ? "no target" : "target " + tg.x() + "," + tg.z()) + ", " + r.length / 2 + " points");
            var tgt = com.grandline.quest.QuestGuide.person(gp, plan, from, "shipwright", false);
            int[] town = tgt == null ? new int[0] : com.grandline.quest.QuestGuide.route(gp, tgt);
            check("quest guide: the shipwright is found on the island and the route follows the streets", tgt != null && town.length >= 6,
                    (tgt == null ? "no shipwright marker" : "at " + tgt.x() + "," + tgt.z() + ", " + town.length / 2 + " points")
                            + "; plots " + fs.plots.size() + ", shipwright plots " + fs.plots.stream().filter(p -> p.type() == com.grandline.world.settlement.BuildingType.SHIPWRIGHT).count()
                            + ", dock " + fs.hasDock + ", pier shipwright " + fs.hasDockShipwright);
            java.util.List<String> noWright = plan.islands.stream().filter(i -> i.settlement().hasDock && i.settlementType != com.grandline.world.settlement.SettlementType.HAMLET
                    && !i.settlement().hasDockShipwright && i.settlement().plots.stream().noneMatch(p -> p.type() == com.grandline.world.settlement.BuildingType.SHIPWRIGHT)).map(i -> i.id).toList();
            check("every town with a pier has a shipwright (a yard, or one at the pier's landing)", noWright.isEmpty(), noWright.isEmpty() ? "" : noWright.toString());
        }
        check("the Rigger's Lesson reads: description and 'Defeat Bandit x3'", desc != null && desc.getString().contains("bandits") && line.equals("Defeat Bandit \u00d73"), (desc == null ? "no description" : desc.getString()) + " | " + line);
        return 0;
    }

    static ServerLevel ow(MinecraftServer s) {
        return s.overworld();
    }

    private static int surface(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    static FakePlayer fake(MinecraftServer s) {
        if (fake == null) {
            fake = FakePlayerFactory.get(ow(s), new GameProfile(FAKE_ID, "SelfTestPirate"));
        }
        return fake;
    }

    // ============================================================== PHASE 1

    private static void phase1() {
        WorldPolishTests.phase1(steps); // island terrain polish, new canon islands, generation speed (test/WorldPolishTests.java)
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            check("ocean world active", p != null, "");
            if (p == null) return 0;
            long canon = p.islands.stream().filter(i -> i.canon).count();
            check("island count >= 150", p.islands.size() >= 150, p.islands.size() + " islands, " + canon + " canon/starter");
            for (String id : List.of("dawn_island", "loguetown", "alabasta", "water_7", "sabaody_archipelago", "wano_country", "elbaph", "laugh_tale", "shiokaze_isle", "hinata_isle"))
                check("canon island present: " + id, p.byId(id) != null, "");
            return 0;
        });
        // terrain at island centres
        for (String id : List.of("shiokaze_isle", "dawn_island", "loguetown", "drum_island", "alabasta", "wano_country", "fuyugane_isle")) {
            steps.add(s -> {
                WorldPlan p = WorldPlan.of(s);
                Island i = p.byId(id);
                ServerLevel l = ow(s);
                int y = surface(l, i.x, i.z);
                check("terrain is land at " + id, y > WorldPlan.SEA_LEVEL, "surface y=" + y + " block=" + l.getBlockState(new BlockPos(i.x, y - 1, i.z)).getBlock().getName().getString());
                return 0;
            });
        }
        // a generated original island
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            // a solid-centred shape (ring/atoll/crater/archipelago centres are lagoons by design)
            Island orig = p.islands.stream().filter(i -> !i.canon && i.radius > 300 && switch (i.shape) {
                case RING, ATOLL, CRATER, ARCHIPELAGO, SEA -> false;
                default -> true;
            }).findFirst().orElse(null);
            check("original island exists", orig != null, orig == null ? "" : orig.id + " '" + orig.name + "' " + orig.zone.id());
            if (orig != null) {
                int y = surface(ow(s), orig.x, orig.z);
                check("original island terrain", y > WorldPlan.SEA_LEVEL - 8, "y=" + y);
            }
            return 0;
        });
        // ocean, Red Line, Reverse Mountain canal
        steps.add(s -> {
            ServerLevel l = ow(s);
            int sea = surface(l, 20000, 14000 + 3000);
            check("open ocean is water", l.getBlockState(new BlockPos(20000, WorldPlan.SEA_LEVEL, 17000)).is(Blocks.WATER) || sea < WorldPlan.SEA_LEVEL, "floor y=" + sea);
            int red = surface(l, 0, 12000);
            check("Red Line wall is tall", red > 250, "y=" + red);
            int redB = surface(l, 60000, -9000);
            check("Red Line B (Mary Geoise side) is tall", redB > 250, "y=" + redB);
            // canal halfway up the North Blue canal
            int cx = 390, cz = -390;
            l.getChunk(cx >> 4, cz >> 4);
            boolean water = false;
            for (int y = 150; y < 240; y++) if (l.getBlockState(new BlockPos(cx, y, cz)).is(Blocks.WATER)) water = true;
            check("Reverse Mountain canal holds water high up", water, "");
            return 0;
        });
        // settlement buildings exist (floor of a plot)
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            ServerLevel l = ow(s);
            int towns = 0, built = 0;
            for (String id : List.of("shiokaze_isle", "loguetown", "shells_town", "cocoyasi")) {
                Island i = p.byId(id);
                for (Plot pl : i.settlement().plots) {
                    if (!pl.landmark().isEmpty()) continue;
                    towns++;
                    l.getChunk(pl.x() >> 4, pl.z() >> 4);
                    l.getChunk((pl.x() + pl.sizeX()) >> 4, (pl.z() + pl.sizeZ()) >> 4);
                    var st = l.getBlockState(new BlockPos(pl.x() + pl.sizeX() / 2, pl.y(), pl.z() + pl.sizeZ() / 2));
                    if (!st.isAir() && !st.liquid()) built++;
                    break;
                }
            }
            check("settlement buildings generated", built == towns && towns > 0, built + "/" + towns + " sampled plots have floors");
            Island lt = p.byId("loguetown");
            check("Loguetown is a city with many plots", lt.settlement().plots.size() >= 15, lt.settlement().plots.size() + " plots, dock=" + lt.settlement().hasDock);
            return 0;
        });
        // landmark: Baratie floats on the sea
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            Island b = p.byId("baratie");
            ServerLevel l = ow(s);
            l.getChunk(b.x >> 4, b.z >> 4);
            int planks = 0;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -12; dz <= 12; dz++) if (l.getBlockState(new BlockPos(b.x + dx, WorldPlan.SEA_LEVEL + 1, b.z + dz)).is(Blocks.OAK_PLANKS)) planks++;
            check("Baratie restaurant ship built on the water", planks > 20, planks + " deck planks");
            return 0;
        });
        // island secrets and chest loot
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            int total = 0, with = 0;
            for (Island isl : p.islands) {
                int n = isl.settlement().secrets.size();
                total += n;
                if (n > 0) with++;
            }
            check("islands carry secrets (treasure, caches, shrines, wrecks)", with >= p.islands.size() * 0.8, total + " secrets on " + with + "/" + p.islands.size() + " islands");
            ServerLevel l = ow(s);
            Island isl = p.byId("dawn_island");
            Plot t = isl.settlement().secrets.stream().filter(pl -> pl.type() == com.grandline.world.settlement.BuildingType.BURIED_TREASURE).findFirst().orElse(null);
            if (t != null) {
                BlockPos chest = new com.grandline.world.build.Builder(new com.grandline.world.build.BlockSink() {
                    @Override public void set(int x, int y, int z, net.minecraft.world.level.block.state.BlockState st) {}
                }, t.x(), t.y(), t.z(), t.type().w, t.type().d, t.rotation(), t.seed()).world(2, -3, 2);
                l.getChunk(chest.getX() >> 4, chest.getZ() >> 4);
                var be = l.getBlockEntity(chest);
                check("buried treasure chest generated under its X", be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "at " + chest.toShortString());
            } else check("buried treasure chest generated under its X", false, "dawn_island has no buried treasure");
            int ok = 0;
            String sample = "";
            for (String n : List.of("house", "tavern", "shop", "blacksmith", "marine", "warehouse", "ruin", "starter", "treasure", "wreck", "shrine", "cache")) {
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, GrandLine.id("chests/" + n));
                var table = s.reloadableRegistries().getLootTable(key);
                var items = table.getRandomItems(new net.minecraft.world.level.storage.loot.LootParams.Builder(l)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST));
                if (table != net.minecraft.world.level.storage.loot.LootTable.EMPTY && !items.isEmpty()) ok++;
                if (n.equals("treasure")) sample = items.toString();
            }
            check("all 12 chest loot tables load and roll items", ok == 12, ok + "/12; treasure roll " + sample);
            // the painted sea scenes: registered painting variants, placeable, sold by merchants, in treasure loot
            var variants = s.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT);
            int reg = 0, placeable = 0;
            for (String v : com.grandline.dialogue.Shops.PAINTINGS) {
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PAINTING_VARIANT, GrandLine.id(v));
                var h = variants.getHolder(key);
                if (h.isPresent()) {
                    reg++;
                    if (h.get().is(net.minecraft.tags.PaintingVariantTags.PLACEABLE)) placeable++;
                }
            }
            var stack = com.grandline.dialogue.Shops.painting("red_line");
            var ed = stack.get(net.minecraft.core.component.DataComponents.ENTITY_DATA);
            check("ten sea paintings are registered, placeable and sold as their scene", reg == 10 && placeable == 10 && ed != null && ed.copyTag().getString("variant").equals("grandline:red_line"),
                    reg + " registered, " + placeable + " placeable, shop item " + (ed == null ? "-" : ed.copyTag()));
            return 0;
        });
        // landmarks: every registered set piece builds deterministically inside its footprint (see LandmarkCheck)
        steps.add(s -> LandmarkCheck.run(s, SelfTest::check));
        // settlement buildings: every type in every style and rotation (see BuildingCheck)
        steps.add(s -> BuildingCheck.run(SelfTest::check));
        // NPC markers & spawning
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            Island i = p.byId("shells_town");
            int markers = 0;
            String sample = "";
            for (Plot pl : i.settlement().plots) for (var m : MarkerCollector.markers(i, pl)) if (m.kind().equals("npc")) {
                markers++;
                sample = m.data().getString("role");
            }
            check("Shells Town has NPC spawn markers", markers > 5, markers + " markers, e.g. " + sample);
            return 0;
        });
        // ---------- combat arena ----------
        final HumanoidNpc[] duel = new HumanoidNpc[2];
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            duel[0] = npc(l, "marine", 3, c.offset(-2, 0, 0));
            duel[1] = npc(l, "pirate", 3, c.offset(2, 0, 0));
            duel[0].setTarget(duel[1]);
            duel[1].setTarget(duel[0]);
            check("spawned Marine vs pirate duel", duel[0] != null && duel[1] != null, "");
            return 200;
        });
        steps.add(s -> {
            float a = duel[0].getHealth() / duel[0].getMaxHealth(), b = duel[1].getHealth() / duel[1].getMaxHealth();
            boolean fought = a < 1f || b < 1f || !duel[0].isAlive() || !duel[1].isAlive();
            check("NPCs fight each other with the combat system", fought, String.format("marine hp %.0f%%, pirate hp %.0f%%", a * 100, b * 100));
            duel[0].discard();
            duel[1].discard();
            return 0;
        });
        // ---------- character creation with a fake player ----------
        steps.add(s -> {
            FakePlayer fp = fake(s);
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            CharacterData d = Characters.get(fp);
            if (d.created) d.archiveAndReset();
            CompoundTag req = new CompoundTag();
            req.putString("name", "Kaito the Tester");
            req.putString("race", "giant");
            req.putString("faction", "pirate");
            req.putString("origin", "south");
            Appearance a = new Appearance();
            a.scale = 9f; // out of range on purpose: server must clamp
            req.put("appearance", a.save());
            var err = CharacterManager.create(fp, req);
            check("character creation accepted", err == null, err == null ? "" : err.getString());
            check("race size clamped to giant max", Math.abs(d.appearance.scale - Race.GIANT.maxScale) < 1e-3, "scale=" + d.appearance.scale);
            check("scale attribute applied (hitbox)", Math.abs(fp.getScale() - Race.GIANT.maxScale) < 0.01, "getScale=" + fp.getScale() + " height=" + fp.getBbHeight());
            check("giant reach bonus applied", fp.entityInteractionRange() > 4.5, "reach=" + fp.entityInteractionRange());
            int bookPages = -1;
            for (var st : fp.getInventory().items) {
                var wb = st.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
                if (wb != null) bookPages = wb.pages().size();
            }
            check("a new character starts with the Captain's Handbook", bookPages == com.grandline.player.Handbook.PAGES, bookPages + " pages");
            fp.setGameMode(GameType.CREATIVE);
            return 0;
        });
        // ---------- data round trip ----------
        steps.add(s -> {
            CharacterData d = Characters.get(fake(s));
            d.addXp(5000);
            d.addMastery("style/black_leg", 500);
            d.bounty = 12_345_678;
            d.discoveredIslands.add("loguetown");
            CompoundTag t = d.serializeNBT(s.registryAccess());
            CharacterData copy = new CharacterData();
            copy.deserializeNBT(s.registryAccess(), t);
            check("character NBT round trip", copy.level == d.level && copy.bounty == d.bounty && copy.race == d.race && copy.mastery("style/black_leg") == d.mastery("style/black_leg")
                    && copy.discoveredIslands.contains("loguetown") && copy.name.equals(d.name), "level " + copy.level + ", bounty " + copy.bounty);
            return 0;
        });
        // ---------- fruit ledger ----------
        steps.add(s -> {
            FruitWorldData led = FruitWorldData.get(s);
            boolean first = led.claimSpecific("goro");
            boolean second = led.claimSpecific("goro");
            check("fruit uniqueness: second copy refused", first && !second, "first=" + first + " second=" + second);
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            d.fruit = null;
            FruitManager.eat(fp, DevilFruits.get("goro"));
            check("eating a fruit grants it and marks it OWNED", "goro".equals(d.fruit) && led.state("goro") == FruitWorldData.State.OWNED, "state=" + led.state("goro"));
            return 0;
        });
        // ---------- every ability executes without error ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            BlockPos c = arena(l);
            HumanoidNpc dummy = npc(l, "bandit", 1, c.offset(0, 0, 2));
            dummy.setNoAi(true);
            dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);
            dummy.setHealth(100000);
            d.observationUnlocked = d.armamentUnlocked = d.futureSight = d.emission = d.internalDestruction = true;
            d.conquerorTrait = d.conquerorAwakened = d.conquerorInfusion = true;
            d.fruitAwakened = true;
            for (FightingStyle st : FightingStyles.all()) d.knownStyles.add(st.id);
            Race raceBefore = d.race;
            int ok = 0, total = 0;
            float dealt = 0;
            Map<String, Float> perFruit = new TreeMap<>();
            List<String> errors = new ArrayList<>();
            for (Ability a : Abilities.all()) {
                total++;
                // MAX_HEALTH is capped at 1024, so the dummy is topped up before every ability (otherwise it dies
                // part-way through the list and every later targeted ability is refused for lack of a target)
                dummy.setHealth(dummy.getMaxHealth());
                // set up prerequisites for this ability
                fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
                dummy.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                dummy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                fp.setYRot(0);
                fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
                CombatState.of(fp).cooldowns.clear();
                CombatState.of(fp).buffs.put("room", 200);
                fp.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                switch (a.source()) {
                    case FRUIT -> {
                        d.fruit = a.owner();
                        d.zoanForm = "hybrid";
                    }
                    case STYLE -> {
                        d.activeStyle = a.owner();
                        FightingStyle st = FightingStyles.get(a.owner());
                        if (st != null && !st.unarmed()) fp.setItemSlot(EquipmentSlot.MAINHAND, weaponFor(st.weapons.iterator().next()));
                    }
                    case WEAPON -> fp.setItemSlot(EquipmentSlot.MAINHAND, weaponFor(WeaponClass.valueOf(a.owner().toUpperCase(Locale.ROOT))));
                    case RACE -> d.race = Race.byId(a.owner());
                    default -> {}
                }
                try {
                    boolean used = CombatManager.useAbility(fp, a, dummy);
                    // run scheduled follow-ups (multi-hit, delayed strikes)
                    for (int t = 0; t < 60; t++) CombatState.of(fp).tick(l.getGameTime() + t);
                    if (used || a.id().contains("shambles") || a.id().contains("sulong") || a.id().endsWith("/toggle") || a.id().contains("pounce") || a.id().contains("rending")) ok++;
                    else errors.add(a.id() + "(refused)");
                } catch (Throwable t) {
                    errors.add(a.id() + " threw " + t);
                    GrandLine.LOGGER.error("[SELFTEST] ability {} failed", a.id(), t);
                }
                float took = dummy.getMaxHealth() - dummy.getHealth();
                dealt += took;
                if (a.source() == Ability.Source.FRUIT) perFruit.merge(a.owner(), took, Float::sum);
            }
            d.race = raceBefore;
            for (var p : l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(c).inflate(80))) p.discard();
            check("all " + total + " abilities execute", errors.isEmpty(), ok + "/" + total + (errors.isEmpty() ? "" : " problems: " + String.join(", ", errors.subList(0, Math.min(12, errors.size())))));
            check("ability dummy took damage", dealt > 0, String.format("%.0f damage dealt in total", dealt));
            List<String> harmless = new ArrayList<>();
            for (var e : perFruit.entrySet()) if (e.getValue() <= 0) harmless.add(e.getKey());
            check("every implemented fruit damages the dummy", harmless.isEmpty(), perFruit.size() + " fruits" + (harmless.isEmpty() ? "" : ", no damage from: " + String.join(", ", harmless)));
            dummy.discard();
            d.zoanForm = "";
            return 0;
        });
        // ---------- Logia intangibility vs Haki ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            HumanoidNpc logia = npc(l, "pirate", 3, c.offset(0, 0, 3));
            HumanoidNpc attacker = npc(l, "marine", 3, c.offset(0, 0, 1));
            logia.setNoAi(true);
            attacker.setNoAi(true);
            logia.forceFruitForTest("mera");
            Hits.Result plain = Hits.hit(attacker, logia, 5, Hits.Info.physical(0.1f));
            Hits.Result haki = Hits.hit(attacker, logia, 5, Hits.Info.physical(0.1f).imbue(true));
            check("Logia ignores un-imbued physical hits", plain == Hits.Result.INTANGIBLE, "result=" + plain);
            check("Armament Haki hits a Logia", haki == Hits.Result.HIT, "result=" + haki);
            // water weakness
            l.setBlock(logia.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
            l.setBlock(logia.blockPosition().above(), Blocks.WATER.defaultBlockState(), 3);
            return 5;
        });
        steps.add(s -> {
            ServerLevel l = ow(s);
            var list = l.getEntitiesOfClass(HumanoidNpc.class, new AABB(arena(l)).inflate(10), n -> "mera".equals(n.fruitId()));
            if (list.isEmpty()) {
                check("Logia in water found", false, "");
                return 0;
            }
            HumanoidNpc logia = list.get(0);
            boolean weak = FruitRules.weakened(logia);
            HumanoidNpc attacker = l.getEntitiesOfClass(HumanoidNpc.class, new AABB(arena(l)).inflate(10), n -> n.fruitId() == null).stream().findFirst().orElse(null);
            Hits.Result wet = attacker == null ? null : Hits.hit(attacker, logia, 5, Hits.Info.physical(0.1f));
            check("Devil Fruit user is weakened in the sea", weak, "inWater=" + logia.isInWater());
            check("Logia submerged in sea water can be hit", wet == Hits.Result.HIT, "result=" + wet);
            for (var n : l.getEntitiesOfClass(HumanoidNpc.class, new AABB(arena(l)).inflate(12))) n.discard();
            l.setBlock(logia.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(logia.blockPosition().above(), Blocks.AIR.defaultBlockState(), 3);
            return 0;
        });
        // ---------- second-wave fruit mechanics: passives and defences ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            TerrainChanges.revertAll(s); // clean the arena after the ability sweep
            HumanoidNpc att = npc(l, "marine", 3, c.offset(-6, 0, -8));
            HumanoidNpc vic = npc(l, "pirate", 3, c.offset(-6, 0, -1)); // 7 blocks apart: a ranged attacker
            att.setNoAi(true);
            vic.setNoAi(true);
            att.forceFruitForTest("");
            Hits.Info punch = Hits.Info.physical(0.1f);
            // Yami: the darkness can never let an attack pass through
            vic.forceFruitForTest("yami");
            float h0 = vic.getHealth();
            Hits.Result r = Hits.hit(att, vic, 5, punch);
            check("Yami user cannot turn intangible", r == Hits.Result.HIT && vic.getHealth() < h0, "result=" + r);
            // Suna: intangible while dry, solid once wet
            vic.forceFruitForTest("suna");
            Hits.Result dry = Hits.hit(att, vic, 5, punch);
            CombatState.of(vic).buffs.put("suna_wet", 100);
            Hits.Result wet = Hits.hit(att, vic, 5, punch);
            check("Suna is intangible when dry, solid when wet", dry == Hits.Result.INTANGIBLE && wet == Hits.Result.HIT, "dry=" + dry + " wet=" + wet);
            // Bari: the barrier ball absorbs a hit completely
            vic.forceFruitForTest("bari");
            boolean ball = CombatManager.useAbility(vic, Abilities.get("fruit/bari/barrier_ball"), null);
            h0 = vic.getHealth();
            r = Hits.hit(att, vic, 5, punch);
            check("Barrier Ball absorbs damage", ball && vic.getHealth() >= h0, "used=" + ball + " result=" + r + " hp " + h0 + " -> " + vic.getHealth());
            // Kagami: a ranged hit is turned back at the attacker as a mirror beam
            vic.forceFruitForTest("kagami");
            boolean mirror = CombatManager.useAbility(vic, Abilities.get("fruit/kagami/reflect"), null);
            h0 = vic.getHealth();
            r = Hits.hit(att, vic, 5, punch);
            int beams = l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(vic.blockPosition()).inflate(4),
                    p -> p.kind().equals("mirror_beam") && p.getOwner() == vic).size();
            check("Kagami mirror reflects a ranged hit", mirror && vic.getHealth() >= h0 && beams > 0, "used=" + mirror + " result=" + r + " beams=" + beams);
            // Shio: salt does +50% to creatures of the sea, normal damage to others
            att.forceFruitForTest("shio");
            var cod = net.minecraft.world.entity.EntityType.COD.create(l);
            var pig = net.minecraft.world.entity.EntityType.PIG.create(l);
            float vsFish = cod == null ? 0 : FruitRules.modifyOutgoing(att, cod, 10, punch);
            float vsLand = pig == null ? 0 : FruitRules.modifyOutgoing(att, pig, 10, punch);
            check("Shio salt hurts sea creatures more", Math.abs(vsFish - 15) < 0.01 && Math.abs(vsLand - 10) < 0.01, "vs cod " + vsFish + ", vs pig " + vsLand);
            // Yami: the darkness grasp shuts off the target's own Devil Fruit
            att.forceFruitForTest("yami");
            vic.forceFruitForTest("mera");
            vic.teleportTo(att.getX(), att.getY(), att.getZ() + 2);
            boolean grasp = CombatManager.useAbility(att, Abilities.get("fruit/yami/dark_grasp"), vic);
            check("Darkness grasp nullifies the target's Devil Fruit", grasp && FruitRules.weakened(vic), "used=" + grasp + " weakened=" + FruitRules.weakened(vic));
            att.discard();
            vic.discard();
            if (cod != null) cod.discard();
            if (pig != null) pig.discard();
            return 0;
        });
        // ---------- overlapping temporary terrain still reverts to the true original ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos at = arena(l).offset(7, 0, 7);
            HumanoidNpc user = npc(l, "pirate", 3, at);
            user.setNoAi(true);
            BlockPos under = at.below();
            var original = l.getBlockState(under);
            user.forceFruitForTest("shio");
            boolean salt = CombatManager.useAbility(user, Abilities.get("fruit/shio/salt_flats"), null);
            boolean saltPlaced = l.getBlockState(under).is(Blocks.CALCITE);
            user.forceFruitForTest("magu");
            boolean magma = CombatManager.useAbility(user, Abilities.get("fruit/magu/magma_floor"), null);
            boolean magmaPlaced = l.getBlockState(under).is(Blocks.MAGMA_BLOCK);
            TerrainChanges.revertAll(s);
            boolean restored = l.getBlockState(under).equals(original);
            check("fruit terrain (salt, then magma on top) reverts to the original block", salt && magma && saltPlaced && magmaPlaced && restored,
                    "salt=" + saltPlaced + " magma=" + magmaPlaced + " restored=" + restored + " (" + l.getBlockState(under).getBlock().getName().getString() + ")");
            user.discard();
            return 0;
        });
        // ---------- third-wave (canon) fruit mechanics ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            TerrainChanges.revertAll(s);
            HumanoidNpc att = npc(l, "marine", 3, c.offset(6, 0, -8));
            HumanoidNpc vic = npc(l, "pirate", 3, c.offset(6, 0, -3));
            att.setNoAi(true);
            vic.setNoAi(true);
            att.forceFruitForTest("");
            Hits.Info punch = Hits.Info.physical(0.1f);
            // Mochi: the body only opens around attacks the user sees coming (Observation Haki); Haki still connects
            vic.forceFruitForTest("mochi");
            CombatState vs = CombatState.of(vic);
            Hits.Result blind = Hits.hit(att, vic, 5, punch);
            vs.observation = true;
            boolean opens = FruitRules.intangibleTo(vic, att, punch, false), hakiLands = !FruitRules.intangibleTo(vic, att, punch, true);
            vs.observation = false;
            check("Mochi passes attacks only with Observation Haki", blind == Hits.Result.HIT && opens && hakiLands, "noObs=" + blind + " obs=" + opens + " hakiLands=" + hakiLands);
            // Numa: a projectile reaching the swamp body is swallowed (and stored for Swamp Spit)
            vic.forceFruitForTest("numa");
            vic.setTarget(att);
            int before = com.grandline.fruit.impl.NumaFruit.stored(vic);
            var shot = com.grandline.entity.AbilityProjectile.shoot(att, "bullet", 0.25f, vic.getBoundingBox().getCenter().add(0, 0, -1.2), new net.minecraft.world.phys.Vec3(0, 0, 1), 0.5, 4, punch);
            FruitRules.tick(vic);
            check("Numa swamp body swallows a projectile", shot.isRemoved() && com.grandline.fruit.impl.NumaFruit.stored(vic) > before, "removed=" + shot.isRemoved() + " stored=" + com.grandline.fruit.impl.NumaFruit.stored(vic));
            // Bomu: explosions cannot hurt a bomb body (control: the same blast hurts once the fruit is gone)
            vic.forceFruitForTest("bomu");
            vic.invulnerableTime = 0;
            float h0 = vic.getHealth();
            vic.hurt(l.damageSources().explosion(null, null), 6);
            float bomb = h0 - vic.getHealth();
            vic.forceFruitForTest("");
            vic.invulnerableTime = 0;
            h0 = vic.getHealth();
            vic.hurt(l.damageSources().explosion(null, null), 6);
            check("Bomu body is immune to explosions", bomb == 0 && vic.getHealth() < h0, "bomu took " + bomb + ", plain took " + (h0 - vic.getHealth()));
            // Toki: no second time leap in a row while fighting; another technique in between allows it again
            vic.forceFruitForTest("toki");
            Ability leap = Abilities.get("fruit/toki/time_leap");
            boolean first = CombatManager.useAbility(vic, leap, att);
            vs.cooldowns.clear();
            com.grandline.fruit.impl.CanonKit.enable(vic); // back from the future
            boolean second = CombatManager.useAbility(vic, leap, att);
            boolean other = CombatManager.useAbility(vic, Abilities.get("fruit/toki/hourglass"), att);
            vs.cooldowns.clear();
            boolean third = CombatManager.useAbility(vic, leap, att);
            check("Toki cannot time-leap twice in a row in combat", first && !second && other && third, "first=" + first + " second=" + second + " between=" + other + " after=" + third);
            // Seiryu: Cloud Ride gives a transformed (survival) player real flight, and reverting the form takes it away
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
            fp.setGameMode(GameType.SURVIVAL);
            d.fruit = "uo_seiryu";
            d.zoanForm = "hybrid";
            d.stamina = 100;
            CombatState.of(fp).toggles.remove(com.grandline.fruit.impl.SeiryuFruit.FLIGHT);
            CombatState.of(fp).cooldowns.clear();
            boolean grounded = !fp.getAbilities().mayfly;
            boolean flies = CombatManager.useAbility(fp, Abilities.get("fruit/uo_seiryu/cloud_ride"), null) && fp.getAbilities().mayfly;
            d.zoanForm = "";
            com.grandline.fruit.impl.SeiryuFruit.updateFlight(fp);
            boolean landed = !fp.getAbilities().mayfly;
            fp.setGameMode(GameType.CREATIVE);
            check("Seiryu Cloud Ride flight in form, revoked on reverting", grounded && flies && landed, "before=" + !grounded + " on=" + flies + " afterRevert=" + !landed);
            att.discard();
            vic.discard();
            return 0;
        });
        SelfTestOriginals.phase1(steps); // Devil Fruits wave 3 — ORIGINAL fruit mechanics (test/SelfTestOriginals.java)
        SelfTestWave4.phase1(steps); // Devil Fruits wave 4 — ORIGINAL fruits (test/SelfTestWave4.java)
        RaceTests.phase1(steps); // racial powers
        // ---------- knockdown/permadeath data rules ----------
        steps.add(s -> {
            CharacterData d = new CharacterData();
            d.created = true;
            d.name = "Doomed";
            d.level = 30;
            d.fruit = "mera";
            d.archiveAndReset();
            check("permadeath archives the character", !d.created && d.generation == 1 && d.fruit == null && d.level == 1 && d.fallenCharacters.size() == 1, d.fallenCharacters.toString());
            return 0;
        });
        // ---------- ships ----------
        final com.grandline.ship.ShipEntity[] voyage = new com.grandline.ship.ShipEntity[1];
        final double[] start = new double[2];
        final HumanoidNpc[] deckHand = new HumanoidNpc[1];
        steps.add(s -> {
            FakePlayer fp = fake(s);
            ServerLevel l = ow(s);
            Island home = WorldPlan.of(s).byId("shiokaze_isle");
            var st = home.settlement();
            fp.moveTo(st.dockX + 0.5, WorldPlan.SEA_LEVEL + 2, st.dockZ + 0.5);
            for (int cx = -3; cx <= 3; cx++) // game code only reads fully loaded chunks: generate the harbour first
                for (int cz = -3; cz <= 3; cz++) l.getChunk((st.dockTipX() >> 4) + cx, (st.dockTipZ() >> 4) + cz);
            var ship = com.grandline.ship.Shipyard.spawnAtDock(fp, fp, com.grandline.ship.ShipClass.SLOOP);
            check("shipyard launches a sloop at the dock", ship != null, ship == null ? "" : "at " + ship.blockPosition() + " dock tip " + st.dockTipX() + "," + st.dockTipZ());
            if (ship == null) return 0;
            var rec = com.grandline.ship.ShipRegistry.get(s).get(ship.shipId);
            check("ship registered with owner and interior", rec != null && FAKE_ID.equals(rec.owner) && rec.interior >= 0, rec == null ? "" : "interior #" + rec.interior);
            check("ship has a walkable deck (collidable parts)", ship.getParts().length > 4 && ship.getParts()[0].canBeCollidedWith(), ship.getParts().length + " hull parts");
            // (a FakePlayer cannot change dimension, so the interior is checked structurally)
            BlockPos io = com.grandline.ship.ShipInteriors.prepare(s, ship);
            ServerLevel dim = s.getLevel(com.grandline.ship.ShipInteriors.DIM);
            boolean hatch = dim != null && io != null && dim.getBlockState(io.offset(2, 3, 1)).is(com.grandline.registry.ModBlocks.SHIP_HATCH.get());
            check("below-deck interior built in the pocket dimension", hatch, io == null ? "no interior" : "origin " + io);
            var exit = com.grandline.ship.ShipInteriors.deckExit(ship);
            check("hatch exit lands on the ship's deck", ship.carries(fp) || exit.distanceTo(ship.position()) < ship.cls.length, "exit " + exit + " ship " + ship.position());
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = ow(s);
            WorldPlan p = WorldPlan.of(s);
            Island home = p.byId("shiokaze_isle");
            // find a heading with 1800 blocks of open sea
            int bestDir = -1;
            int sx = home.x, sz = home.z + home.radius + 400;
            for (int dir = 0; dir < 16 && bestDir < 0; dir++) {
                double a = dir * Math.PI / 8;
                boolean open = true;
                for (int d = 0; d <= 1900 && open; d += 40) {
                    WorldPlan.Sample smp = new WorldPlan.Sample();
                    p.sample(sx + (int) (Math.cos(a) * d), sz + (int) (Math.sin(a) * d), smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 6) open = false;
                }
                if (open) bestDir = dir;
            }
            check("found open sea for a voyage", bestDir >= 0, "dir " + bestDir);
            if (bestDir < 0) return 0;
            double a = bestDir * Math.PI / 8;
            l.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(sx >> 4, sz >> 4), 3, new ChunkPos(sx >> 4, sz >> 4));
            l.getChunk(sx >> 4, sz >> 4);
            var ship = com.grandline.registry.ModEntities.SHIPS.get(com.grandline.ship.ShipClass.GALLEON).get().create(l);
            float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
            ship.moveTo(sx + 0.5, WorldPlan.SEA_LEVEL + 0.9, sz + 0.5, yaw, 0);
            ship.setShipName("Selftest Voyager");
            ship.setHull(ship.cls.hull);
            l.addFreshEntity(ship);
            ship.autopilot = true;
            ship.testSpeedMult = 5f;
            ship.setAnchored(false);
            ship.setSail(3);
            voyage[0] = ship;
            start[0] = ship.getX();
            start[1] = ship.getZ();
            var deck = ship.toWorld(0, ship.cls.freeboard + 0.05, 0);
            deckHand[0] = npc(l, "villager", 1, BlockPos.containing(deck));
            deckHand[0].moveTo(deck.x, deck.y, deck.z);
            deckHand[0].setNoAi(true);
            return 2;
        });
        final int[] voyageTicks = {0};
        steps.add(s -> {
            var ship = voyage[0];
            if (ship == null || !ship.isAlive()) {
                check("voyage ship stayed afloat", false, "ship removed");
                return 0;
            }
            ServerLevel l = ow(s);
            ChunkPos cp = new ChunkPos(ship.blockPosition());
            l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
            ChunkPos ahead = new ChunkPos(BlockPos.containing(ship.position().add(ship.heading().scale(40))));
            l.getChunkSource().addRegionTicket(TicketType.FORCED, ahead, 2, ahead);
            double dist = Math.hypot(ship.getX() - start[0], ship.getZ() - start[1]);
            if (dist < 1600 && ++voyageTicks[0] < 3000) return -1;
            check("ship sails into newly generated chunks", dist >= 1600, String.format("travelled %.0f blocks in %d ticks, speed %.2f b/t, now at %s", dist, voyageTicks[0], ship.speed(), ship.blockPosition()));
            check("chunk under the ship is generated ocean", l.getBlockState(ship.blockPosition().below()).is(Blocks.WATER) || l.getBlockState(ship.blockPosition()).is(Blocks.WATER), l.getBlockState(ship.blockPosition().below()).toString());
            boolean onDeck = deckHand[0] != null && deckHand[0].isAlive() && ship.carries(deckHand[0]) && deckHand[0].distanceTo(ship) < ship.cls.length;
            check("crew standing on deck is carried along", onDeck, deckHand[0] == null ? "" : "npc " + (int) deckHand[0].distanceTo(ship) + " blocks from ship centre");
            ship.autopilot = false;
            ship.setAnchored(true);
            ship.setSail(0);
            try {
                Files.writeString(Path.of("selftest-ship.txt"), ship.getUUID() + " " + ship.blockPosition().getX() + " " + ship.blockPosition().getZ() + " " + ship.shipName());
            } catch (IOException ex) {
                check("write ship fixture", false, ex.toString());
            }
            return 40;
        });
        NavalTests.phase1(steps); // naval combat (test/NavalTests.java)
        LivingSeaTests.phase1(steps); // sea battles, merchant traffic, harbour raids (test/LivingSeaTests.java)
        DimensionTests.awakening(steps);
        DimensionTests.shipClasses(steps);
        DimensionTests.ferry(steps);
        DimensionTests.holyLand(steps);
        DimensionTests.settledIslands(steps);
        DimensionTests.phase1(steps);
        CreatureSelfTest.phase1(steps); // Sea Kings, Calm Belt, Zoan beast forms
        ActivityTests.phase1(steps); // sea activities: treasure maps, fishing, cooking (test/ActivityTests.java)
        // ---------- persistence fixtures for phase 2 ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            WorldPlan p = WorldPlan.of(s);
            Island isl = p.byId("orange_town");
            int x = isl.x + 3, z = isl.z + 3;
            int y = surface(l, x, z);
            HumanoidNpc keeper = npc(l, "elder", 1, new BlockPos(x, y, z));
            keeper.setCustomName(net.minecraft.network.chat.Component.literal("Persistence Keeper"));
            keeper.setPersistenceRequired();
            CompoundTag fx = new CompoundTag();
            fx.putUUID("npc", keeper.getUUID());
            fx.putInt("x", x);
            fx.putInt("z", z);
            fx.putString("fruitOwner", FAKE_ID.toString());
            Files.writeString(Path.of("selftest-fixtures.txt"), keeper.getUUID() + " " + x + " " + z);
            check("persistence fixture written", true, "NPC " + keeper.getUUID() + " at " + x + "," + z);
            return 20;
        });
        storySteps();
        StoryTestsExtra.phase1(steps); // Skypiea, Fish-Man Island, local stories, sky/deep notables
        IslandStoryTests.phase1(steps); // procedural stories on original islands, the richer job board (test/IslandStoryTests.java)
        BossTests.phase1(steps); // boss fights: bars, phases, telegraphs, Haki rules, scaling, fair knockdowns, rewards (test/BossTests.java)
        steps.add(s -> { // the mod's own sounds and music discs (registry/ModSounds, registry/MusicDiscs)
            int missing = 0;
            for (String id : com.grandline.registry.ModSounds.IDS)
                if (!net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(com.grandline.GrandLine.id(id))) missing++;
            check("sound events registered", missing == 0, (com.grandline.registry.ModSounds.IDS.length - missing) + "/" + com.grandline.registry.ModSounds.IDS.length);
            int discs = 0;
            for (var d : List.of(com.grandline.registry.MusicDiscs.DISC_EAST_BLUE, com.grandline.registry.MusicDiscs.DISC_GRAND_LINE,
                    com.grandline.registry.MusicDiscs.DISC_WANO, com.grandline.registry.MusicDiscs.DISC_BOSS)) {
                var jp = new net.minecraft.world.item.ItemStack(d.get()).get(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
                if (jp != null && jp.song().unwrap(s.registryAccess()).filter(h -> h.value().lengthInSeconds() > 60).isPresent()) discs++;
            }
            check("music discs play their jukebox songs", discs == 4, discs + "/4 discs resolve to a jukebox song over a minute long");
            return 0;
        });
        BaseTests.phase1(steps); // crew bases: claims, building rights, warehouse, upgrades, dock, hands, raids (test/BaseTests.java)
        CareerTests.phase1(steps); // faction careers: ranks, perks, titles, the Warlord letter (test/CareerTests.java)
        SeaJobTests.phase1(steps); // sea jobs: escort follower, named pirate ships, convoys, sea-battle objectives (test/SeaJobTests.java)
        SeaPolishTests.phase1(steps); // hired gunners, raid plunder, sea-job chart marks, convoy cap (test/SeaPolishTests.java)
        JourneyTests.phase1(steps); // journey advancements, Den Den Mushi, Buster Call (test/JourneyTests.java; runs last)
        SeaJobTests.fixture(steps); // an escort stowed for phase 2 (after the last test, so its away time stays short)
    }

    // ============================================================== story cast, crews and quests (Saltfire Saga)

    private static void storySteps() {
        // ---------- cast data ----------
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            var all = com.grandline.npc.notable.NotableCharacters.all();
            check("story cast registered", all.size() >= 100, all.size() + " notables");
            java.util.function.ToIntFunction<com.grandline.npc.notable.NotableCharacters.Group> c = com.grandline.npc.notable.NotableCharacters::count;
            check("cast groups", c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.EMPEROR) == 4 && c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.STEWARD) == 5
                            && c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.WARLORD) == 7 && c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.SUPERNOVA) >= 9
                            && com.grandline.npc.notable.NotableCharacters.recruitable().size() >= 14,
                    "emperors " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.EMPEROR) + ", stewards " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.STEWARD)
                            + ", warlords " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.WARLORD) + ", supernovas " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.SUPERNOVA)
                            + ", crew " + com.grandline.npc.notable.NotableCharacters.recruitable().size());
            List<String> bad = new ArrayList<>();
            Set<String> fruits = new HashSet<>();
            for (var n : all) {
                if (StoryTestsExtra.planOf(s, n.island()) == null) bad.add(n.id() + ": island " + n.island()); // ocean, sky or deep sea
                if (n.fruit() != null && (DevilFruits.get(n.fruit()) == null || !fruits.add(n.fruit()))) bad.add(n.id() + ": fruit " + n.fruit());
                if (FightingStyles.get(n.style()) == null) bad.add(n.id() + ": style " + n.style());
                if (!com.grandline.npc.Archetypes.has(n.role())) bad.add(n.id() + ": role " + n.role());
                if (!n.raceEnum().id().equals(n.race())) bad.add(n.id() + ": race " + n.race());
                if (!n.weapon().isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.get(GrandLine.id(n.weapon())) == net.minecraft.world.item.Items.AIR) bad.add(n.id() + ": weapon " + n.weapon());
            }
            check("every notable's island, fruit (unique), style, role, race and weapon exist", bad.isEmpty(), bad.isEmpty() ? fruits.size() + " fruits held by the cast" : String.join("; ", bad.subList(0, Math.min(10, bad.size()))));
            return 0;
        });
        // ---------- quest graph ----------
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            List<String> bad = new ArrayList<>();
            Set<String> story = com.grandline.quest.QuestRegistry.storyQuestIds();
            for (var q : com.grandline.quest.QuestRegistry.all()) {
                if (q.next() != null && com.grandline.quest.QuestRegistry.get(com.grandline.quest.QuestRegistry.baseId(q.next())) == null) bad.add(q.id() + " next " + q.next());
                for (String r : q.requires()) if (com.grandline.quest.QuestRegistry.get(com.grandline.quest.QuestRegistry.baseId(r)) == null) bad.add(q.id() + " requires " + r);
                if (!story.contains(q.id())) continue;
                if (q.giverRole().startsWith("notable:") && com.grandline.npc.notable.NotableCharacters.get(q.giverRole().substring(8)) == null) bad.add(q.id() + " giver " + q.giverRole());
                if (!q.giverRole().startsWith("notable:") && !com.grandline.npc.Archetypes.has(q.giverRole())) bad.add(q.id() + " giver " + q.giverRole());
                if (!q.island().equals("*") && StoryTestsExtra.planOf(s, q.island()) == null) bad.add(q.id() + " island " + q.island());
                if (q.objectives().isEmpty()) bad.add(q.id() + " has no objectives");
                for (var o : q.objectives()) {
                    boolean ok = switch (o.type()) {
                        case "defeat" -> com.grandline.npc.notable.NotableCharacters.get(o.target()) != null;
                        case "talk" -> com.grandline.npc.notable.NotableCharacters.get(o.target()) != null || com.grandline.npc.Archetypes.has(o.target());
                        case "kill" -> com.grandline.npc.Archetypes.has(o.target());
                        case "discover" -> StoryTestsExtra.planOf(s, o.target()) != null || o.target().equals("any") || o.target().equals("any_canon");
                        case "poneglyph" -> true;
                        case "arrest", "collect" -> StoryTestsExtra.progresses(s, o);
                        default -> false;
                    };
                    if (!ok) bad.add(q.id() + " objective " + o.type() + ":" + o.target());
                }
                String vf = com.grandline.quest.QuestRegistry.variantFaction(q.id());
                if (q.bounty() > 0 && (vf == null ? com.grandline.quest.QuestRegistry.get(q.id() + ".marine") == null : vf.equals("marine"))) bad.add(q.id() + " grants a bounty without a Marine variant");
            }
            for (var n : com.grandline.npc.notable.NotableCharacters.recruitable()) {
                if (com.grandline.quest.QuestRegistry.get(n.recruitQuest()) == null) bad.add(n.id() + " has no " + n.recruitQuest());
                if (com.grandline.quest.QuestRegistry.get(n.personalQuest()) == null) bad.add(n.id() + " has no " + n.personalQuest());
            }
            Set<String> islands = new HashSet<>();
            for (String id : story) {
                var q = com.grandline.quest.QuestRegistry.get(id);
                if (q != null && !q.island().equals("*")) islands.add(q.island());
            }
            check("story quest graph is consistent", bad.isEmpty(), bad.isEmpty() ? story.size() + " story quests across " + islands.size() + " islands" : String.join("; ", bad.subList(0, Math.min(10, bad.size()))));
            return 0;
        });
        // ---------- faction variants ----------
        steps.add(s -> {
            CharacterData m = new CharacterData();
            m.faction = Faction.MARINE;
            CharacterData pi = new CharacterData();
            pi.faction = Faction.PIRATE;
            var base = com.grandline.quest.QuestRegistry.get("shells_2");
            var marine = com.grandline.quest.QuestRegistry.get("shells_2.marine");
            m.completedQuests.add("shells_1.marine");
            pi.completedQuests.add("shells_1");
            boolean ok = com.grandline.quest.QuestRegistry.resolve("shells_2", m).equals("shells_2.marine") && com.grandline.quest.QuestRegistry.resolve("shells_2", pi).equals("shells_2")
                    && !com.grandline.quest.QuestRegistry.requirementsMet(m, base) && com.grandline.quest.QuestRegistry.requirementsMet(m, marine)
                    && com.grandline.quest.QuestRegistry.requirementsMet(pi, base) && !com.grandline.quest.QuestRegistry.requirementsMet(pi, marine);
            m.completedQuests.add("shells_2.marine");
            ok &= com.grandline.quest.QuestRegistry.isDone(m, "shells_2") && com.grandline.quest.QuestRegistry.requirementsMet(m, com.grandline.quest.QuestRegistry.get("recruit_petra"));
            check("faction variants resolve and gate correctly", ok, "");
            return 0;
        });
        // ---------- spawning a notable, yielding, permanent death ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            WorldPlan p = WorldPlan.of(s);
            var n = com.grandline.npc.notable.NotableCharacters.get("fumio");
            Island isl = p.byId(n.island());
            l.getChunk((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            HumanoidNpc npc = com.grandline.npc.notable.NotableSpawner.spawnNow(l, "fumio");
            var st = com.grandline.npc.notable.NotableState.get(s);
            check("notable spawns on its home island", npc != null && npc.isAlive() && "fumio".equals(npc.notableId()) && npc.flag(com.grandline.npc.GLNpc.FLAG_NOTABLE)
                            && npc.isPersistenceRequired() && npc.tier() == n.tier() && "black_leg".equals(npc.styleId()) && st.status("fumio") == com.grandline.npc.notable.NotableState.Status.ALIVE,
                    npc == null ? "no stand spot / not spawned" : npc.getName().getString() + " at " + npc.blockPosition() + ", hp " + (int) npc.getMaxHealth());
            if (npc == null) return 0;
            FakePlayer fp = fake(s);
            check("friendly notable does not attack players", !npc.isHostileTo(fp), "");
            CompoundTag t = new CompoundTag();
            t.putString("greeting", "dialogue.grandline.greet.elder");
            net.minecraft.nbt.ListTag opts = new net.minecraft.nbt.ListTag();
            CharacterData d = Characters.get(fp);
            com.grandline.npc.notable.NotableHooks.decorate(fp, npc, d, t, opts, true);
            check("notable dialogue: own greeting and quest offer", t.getString("greeting").equals("dialogue.grandline.notable.fumio") && opts.toString().contains("quest:ember_east_1"), opts.toString());
            questTextCheck(s); // every quest tells the player what it is about and what to do
            npc.hurt(fp.damageSources().playerAttack(fp), 100000);
            check("beaten friendly notable yields instead of dying", npc.isAlive() && npc.getHealth() > 0 && npc.flag(com.grandline.npc.GLNpc.FLAG_SURRENDER), "hp " + npc.getHealth());
            npc.kill();
            check("killed notable is gone for good", st.status("fumio") == com.grandline.npc.notable.NotableState.Status.DEFEATED
                    && com.grandline.npc.notable.NotableSpawner.spawnNow(l, "fumio") == null, "status " + st.status("fumio"));
            return 0;
        });
        // ---------- recruiting a crew member ----------
        final int[] crewWait = {0};
        steps.add(s -> {
            // keep the crew candidate's home loaded and ticking, as it would be with a player nearby; wait until it has
            // actually finished loading (a fixed wait was too short on a busy machine and the NPC was not yet tracked)
            var n = com.grandline.npc.notable.NotableCharacters.get("petra");
            Island isl = WorldPlan.of(s).byId(n.island());
            ChunkPos cp = new ChunkPos((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            if (crewWait[0]++ == 0) ow(s).getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
            boolean ready = true;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) if (ow(s).getChunkSource().getChunkNow(cp.x + dx, cp.z + dz) == null) ready = false;
            return ready && crewWait[0] > 40 || crewWait[0] > 600 ? 20 : -1;
        });
        steps.add(s -> {
            ServerLevel l = ow(s);
            HumanoidNpc petra = com.grandline.npc.notable.NotableSpawner.spawnNow(l, "petra");
            if (petra == null) {
                check("crew candidate spawned", false, "petra not spawned");
                return 0;
            }
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            CompoundTag t = new CompoundTag();
            net.minecraft.nbt.ListTag opts = new net.minecraft.nbt.ListTag();
            com.grandline.npc.notable.NotableHooks.decorate(fp, petra, d, t, opts, false);
            boolean noRecruitYet = !opts.toString().contains("recruit");
            d.completedQuests.add("recruit_petra");
            opts = new net.minecraft.nbt.ListTag();
            com.grandline.npc.notable.NotableHooks.decorate(fp, petra, d, t, opts, false);
            boolean recruitOffered = opts.toString().contains("\"recruit\"");
            boolean joined = com.grandline.npc.crew.CrewManager.recruit(fp, petra);
            check("recruit option appears only after the recruitment quest", noRecruitYet && recruitOffered && joined, opts.toString());
            float hp = petra.getHealth();
            petra.hurt(fp.damageSources().playerAttack(fp), 5);
            boolean crewOk = com.grandline.npc.crew.CrewManager.isCrewOf(petra, fp) && petra.isAlliedTo(fp) && !petra.isHostileTo(fp) && petra.getHealth() == hp
                    && com.grandline.npc.crew.CrewManager.members(s, d.characterId).contains("petra")
                    && com.grandline.npc.notable.NotableState.get(s).status("petra") == com.grandline.npc.notable.NotableState.Status.RECRUITED;
            check("crew member is allied, protected from friendly fire and saved per character", crewOk, "members " + com.grandline.npc.crew.CrewManager.members(s, d.characterId));
            CompoundTag saved = com.grandline.npc.crew.CrewManager.data(s).save(new CompoundTag(), s.registryAccess());
            check("crew data round trip", saved.toString().contains("petra") && saved.getList("crews", 10).size() >= 1, "");
            com.grandline.npc.crew.CrewManager.disband(s, d.characterId);
            check("crew disbands (captain's permadeath)", com.grandline.npc.crew.CrewManager.members(s, d.characterId).isEmpty() && !com.grandline.npc.crew.CrewManager.isCrew(petra)
                    && petra.faction() == Faction.MARINE, "status " + com.grandline.npc.notable.NotableState.get(s).status("petra") + ", faction " + petra.faction()
                    + ", tracked " + (l.getEntity(petra.getUUID()) != null));
            d.completedQuests.remove("recruit_petra");
            return 0;
        });
        // ---------- quest chain with retroactive discovery and auto-advance ----------
        steps.add(s -> {
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            Faction before = d.faction;
            d.faction = Faction.PIRATE;
            d.discoveredIslands.add("shells_town");
            boolean started = com.grandline.quest.Quests.start(fp, "ember_east_1");
            com.grandline.quest.Quests.progress(fp, "kill", "bandit", 3);
            boolean ok = started && d.completedQuests.contains("ember_east_1") && d.completedQuests.contains("ember_east_2") && d.questProgress.containsKey("shells_1");
            check("story chain advances (pre-discovered island credited, next arc auto-started)", ok, "active " + d.questProgress.keySet());
            for (String id : List.of("ember_east_1", "ember_east_2")) d.completedQuests.remove(id);
            d.questProgress.remove("shells_1");
            d.faction = before;
            return 0;
        });
    }

    // ============================================================== PHASE 2

    private static void phase2() {
        SeaJobTests.phase2(steps); // a stowed escort comes back after the restart (first: the job lapses after ten minutes away)
        DimensionTests.phase2(steps);
        NavalTests.phase2(steps);
        LivingSeaTests.phase2(steps);
        IslandStoryTests.phase2(steps); // generated stories regenerate identically; generated people's fates persist
        ActivityTests.phase2(steps); // looted island secrets persist
        JourneyTests.phase2(steps); // journey advancements belong to the account and are saved with it
        BossTests.phase2(steps); // boss relic records and fates persist
        BaseTests.phase2(steps); // a claimed island with its upgrades, warehouse and hands survives the restart
        SeaPolishTests.phase2(steps); // hired gunners back at their guns, a market still short of plundered goods
        CareerTests.phase2(steps); // careers are saved with the character
        final String[] fx = new String[1];
        steps.add(s -> {
            fx[0] = Files.readString(Path.of("selftest-fixtures.txt")).trim();
            String[] parts = fx[0].split(" ");
            int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            ow(s).getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(x >> 4, z >> 4), 2, new ChunkPos(x >> 4, z >> 4));
            ow(s).getChunk(x >> 4, z >> 4);
            check("fruit ledger survived restart", FruitWorldData.get(s).state("goro") == FruitWorldData.State.OWNED, "goro=" + FruitWorldData.get(s).state("goro") + " owner=" + FruitWorldData.get(s).ownerName("goro"));
            return 60; // let entities load
        });
        final int[] npcWait = {0};
        steps.add(s -> {
            String[] parts = fx[0].split(" ");
            UUID id = UUID.fromString(parts[0]);
            var e = ow(s).getEntity(id);
            if (e == null && ++npcWait[0] < 400) return -1; // entities load asynchronously (slow on a busy machine)
            check("NPC entity persisted across restart", e instanceof HumanoidNpc, e == null ? "missing" : e.getName().getString() + " at " + e.blockPosition());
            return 0;
        });
        final String[] sf = new String[1];
        steps.add(s -> {
            sf[0] = Files.readString(Path.of("selftest-ship.txt")).trim();
            String[] parts = sf[0].split(" ", 4);
            int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            ow(s).getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(x >> 4, z >> 4), 3, new ChunkPos(x >> 4, z >> 4));
            ow(s).getChunk(x >> 4, z >> 4);
            return 80;
        });
        steps.add(s -> {
            String[] parts = sf[0].split(" ", 4);
            var e = ow(s).getEntity(UUID.fromString(parts[0]));
            boolean ok = e instanceof com.grandline.ship.ShipEntity ship && ship.shipName().equals(parts[3]) && ship.cls == com.grandline.ship.ShipClass.GALLEON && ship.hull() > 0;
            check("ship persisted across restart where it was left", ok, e == null ? "missing" : e.blockPosition() + " name=" + ((com.grandline.ship.ShipEntity) e).shipName());
            var rec = com.grandline.ship.ShipRegistry.get(s).all().stream().filter(r -> FAKE_ID.equals(r.owner)).findFirst().orElse(null);
            check("ship registry persisted", rec != null && rec.interior >= 0, rec == null ? "" : rec.name);
            return 0;
        });
    }

    static void phase2Ships(Deque<Step> steps) {}

    // ============================================================== helpers

    private static BlockPos arenaPos;

    /** A flat stone platform in the sky above Shiokaze Isle for controlled tests. */
    static BlockPos arena(ServerLevel l) {
        if (arenaPos == null) {
            WorldPlan p = WorldPlan.of(l.getServer());
            Island i = p.byId("shiokaze_isle");
            BlockPos c = new BlockPos(i.x + 60, 200, i.z + 60);
            for (int dx = -12; dx <= 12; dx++)
                for (int dz = -12; dz <= 12; dz++) {
                    l.setBlock(c.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 0; dy < 6; dy++) l.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            arenaPos = c;
            // keep the arena entity-ticking without a real player nearby
            l.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(c), 3, new ChunkPos(c));
            l.setChunkForced(c.getX() >> 4, c.getZ() >> 4, true);
        }
        return arenaPos;
    }

    static HumanoidNpc npc(ServerLevel l, String role, int tier, BlockPos pos) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        n.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        n.configure(role, tier, "", pos, "", l.random);
        l.addFreshEntity(n);
        return n;
    }

    private static ItemStack weaponFor(WeaponClass wc) {
        for (var w : ModItems.WEAPONS) if (w.get().weaponClass == wc) return new ItemStack(w.get());
        return ItemStack.EMPTY;
    }

    private SelfTest() {}
}
