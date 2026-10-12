package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.activity.*;
import com.grandline.combat.CombatState;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.Appearance;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.registry.ModDamageTypes;
import com.grandline.registry.ModItems;
import com.grandline.world.DeepWorld;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.SkyWorld;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.grandline.test.SelfTest.check;

/**
 * Self-test steps for the sea activities (added to phase 1 by {@link SelfTest}; quick run: GL_SELFTEST=activities).
 * Treasure maps must point at a real secret whose chest exists in the generated world, prefer unlooted secrets, come
 * out of the chest loot tables, solve when the target chest is opened and piece together from torn halves; fishing at
 * sea in each sea (driven through the real fishing loot table and its loot modifier) must yield that sea's catches,
 * island ponds the vanilla catch, and the skill must raise quality; dishes must cook in the smoker, campfire, furnace
 * and crafting grid, eating one must apply its buffs, the buffs must change damage and regeneration, and a recruited
 * crew cook must make better dishes and run the galley.
 */
final class ActivityTests {
    private static final String[] MAP_TABLES = {"house", "tavern", "ruin", "wreck", "cache", "shrine", "treasure"};

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- items: models, textures and names ----------
        steps.add(s -> {
            List<String> missing = new ArrayList<>();
            String lang = resource("/assets/grandline/lang/en_us.json");
            for (var it : ActivityItems.ALL) {
                String id = it.getId().getPath();
                if (resource("/assets/grandline/models/item/" + id + ".json") == null) missing.add(id + " model");
                if (ActivityTests.class.getResource("/assets/grandline/textures/item/" + id + ".png") == null) missing.add(id + " texture");
                if (lang == null || !lang.contains("\"item.grandline." + id + "\"")) missing.add(id + " name");
            }
            check("every sea activity item has a model, a texture and a name", missing.isEmpty() && ActivityItems.ALL.size() >= 21,
                    ActivityItems.ALL.size() + " items" + (missing.isEmpty() ? "" : ", missing: " + missing));
            ensureCharacter(s);
            return 0;
        });

        // ---------- treasure maps: a real secret, its chest in the generated world ----------
        final TreasureTarget[] first = new TreasureTarget[1];
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            ServerLevel l = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            Island dawn = p.byId("dawn_island");
            BlockPos origin = new BlockPos(dawn.x, 70, dawn.z);
            TreasureTarget a = TreasureMaps.pick(s, origin, 42L, fp, false), b = TreasureMaps.pick(s, origin, 42L, fp, false);
            first[0] = a;
            Island isl = a == null ? null : p.byId(a.island());
            Plot plot = isl == null || a.index() >= isl.settlement().secrets.size() ? null : isl.settlement().secrets.get(a.index());
            BlockPos cp = plot == null ? null : TreasureMaps.chestPos(plot);
            check("a treasure map points at a real island secret", cp != null && cp.equals(new BlockPos(a.x(), a.y(), a.z()))
                            && a.kind().equals(TreasureMaps.kind(plot.type())),
                    a == null ? "no target" : a.key() + " " + a.kind() + " at " + a.x() + "," + a.y() + "," + a.z() + " (" + (isl == null ? "?" : isl.zone.id()) + ")");
            check("the map's target is deterministic for the same place and salt", a != null && a.equals(b), a + " / " + b);
            // the target chests exist in the generated world (maps found in four different seas)
            int ok = 0, n = 0;
            StringBuilder det = new StringBuilder();
            Set<String> kinds = new TreeSet<>();
            for (String from : List.of("dawn_island", "loguetown", "alabasta", "wano_country", "drum_island")) {
                Island o = p.byId(from);
                for (long salt = 1; salt <= 2; salt++) {
                    TreasureTarget t = TreasureMaps.pick(s, new BlockPos(o.x, 70, o.z), salt * 7919, null, false);
                    if (t == null) continue;
                    n++;
                    BlockPos pos = new BlockPos(t.x(), t.y(), t.z());
                    l.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    boolean chest = l.getBlockEntity(pos) instanceof ChestBlockEntity;
                    if (chest) ok++;
                    kinds.add(t.kind());
                    det.append(t.key()).append(chest ? " ok; " : " NO CHEST at " + pos.toShortString() + "; ");
                }
            }
            check("map targets have their chest in the generated world", n >= 8 && ok == n, ok + "/" + n + " " + kinds + " — " + det);
            // every kind of secret, on the island nearest Dawn Island that holds one
            List<Island> byDistance = new ArrayList<>(p.islands);
            byDistance.sort(Comparator.comparingDouble(i -> i.distance(dawn.x, dawn.z)));
            int kindsOk = 0;
            StringBuilder kdet = new StringBuilder();
            for (BuildingType kind : List.of(BuildingType.BURIED_TREASURE, BuildingType.HIDDEN_CACHE, BuildingType.LOST_SHRINE, BuildingType.SHIPWRECK)) {
                TreasureTarget t = null;
                for (Island i : byDistance) {
                    if (i.radius < 60) continue;
                    List<Plot> sec = i.settlement().secrets;
                    for (int k = 0; k < sec.size() && t == null; k++) if (sec.get(k).type() == kind) t = TreasureMaps.target(i, k);
                    if (t != null) break;
                }
                if (t == null) {
                    kdet.append(kind).append(": none; ");
                    continue;
                }
                BlockPos pos = new BlockPos(t.x(), t.y(), t.z());
                l.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                boolean chest = l.getBlockEntity(pos) instanceof ChestBlockEntity;
                if (chest) kindsOk++;
                kdet.append(t.kind()).append(' ').append(t.key()).append(chest ? " ok; " : " NO CHEST at " + pos.toShortString() + "; ");
            }
            check("every kind of secret (buried treasure, cache, shrine, wreck) has its chest where the map says", kindsOk == 4, kdet.toString());
            // prefer secrets nobody has looted, then ones this character has not
            if (a != null) {
                TreasureMaps.Data data = TreasureMaps.data(s);
                data.looted.add(a.key());
                TreasureTarget c = TreasureMaps.pick(s, origin, 42L, fp, false);
                data.looted.remove(a.key());
                check("maps prefer secrets nobody has looted", c != null && !c.key().equals(a.key()), a.key() + " looted -> " + (c == null ? "-" : c.key()));
            }
            return 0;
        });
        // ---------- treasure maps in chest loot ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            int ok = 0;
            StringBuilder det = new StringBuilder();
            for (String n : MAP_TABLES) {
                LootTable table = s.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, GrandLine.id("chests/" + n)));
                int maps = 0, torn = 0;
                for (int i = 0; i < 400; i++)
                    for (ItemStack st : table.getRandomItems(new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST))) {
                        if (st.is(ActivityItems.TREASURE_MAP.get())) maps++;
                        if (st.is(ActivityItems.TORN_MAP.get())) torn++;
                    }
                if (table != LootTable.EMPTY && maps + torn > 0) ok++;
                det.append(n).append(' ').append(maps).append('/').append(torn).append("; ");
            }
            check("chest loot tables with treasure maps load and roll them", ok == MAP_TABLES.length, ok + "/" + MAP_TABLES.length + " (maps/torn halves in 400 rolls: " + det + ")");
            return 0;
        });
        // ---------- a blank map is drawn for its holder; the chart marks it; opening the chest solves it ----------
        final ItemStack[] held = new ItemStack[1];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            Island dawn = WorldPlan.of(s).byId("dawn_island");
            fp.moveTo(dawn.x + 0.5, 90, dawn.z + 0.5);
            ItemStack map = new ItemStack(ActivityItems.TREASURE_MAP.get());
            fp.getInventory().setItem(20, map);
            fp.tickCount = 0;
            map.inventoryTick(l, fp, 20, false);
            TreasureTarget t = map.get(ActivityRegistry.TREASURE.get());
            held[0] = map;
            check("a blank treasure map is drawn for the player carrying it", t != null && WorldPlan.of(s).byId(t.island()) != null, String.valueOf(t));
            if (t == null) return 0;
            CompoundTag sd = TreasureMaps.screenData(fp, t);
            int land = 0;
            if (sd != null) for (byte b : sd.getByteArray("mask")) if (b >= 2) land++;
            boolean inside = sd != null && t.x() >= sd.getInt("x0") && t.x() < sd.getInt("x0") + sd.getInt("span") && t.z() >= sd.getInt("z0") && t.z() < sd.getInt("z0") + sd.getInt("span");
            check("the painted map shows the island and the X", sd != null && land > 40 && inside, sd == null ? "no data" : land + " land cells of " + sd.getByteArray("mask").length + ", X inside=" + inside);
            TreasureMaps.show(fp, t); // the reader remembers it
            boolean marked = false;
            for (var e : TreasureMaps.chartMarks(fp)) {
                CompoundTag c = (CompoundTag) e;
                if (c.getInt("x") == t.x() && c.getInt("z") == t.z()) marked = true;
            }
            fp.getInventory().setItem(20, ItemStack.EMPTY);
            boolean remembered = false;
            for (var e : TreasureMaps.chartMarks(fp)) if (((CompoundTag) e).getInt("x") == t.x()) remembered = true;
            fp.getInventory().setItem(20, map);
            check("the sea chart marks the map's X (carried, and remembered once read)", marked && remembered, "carried=" + marked + " read=" + remembered);
            BlockPos chest = new BlockPos(t.x(), t.y(), t.z());
            l.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(chest), 1, new ChunkPos(chest));
            l.getChunk(chest.getX() >> 4, chest.getZ() >> 4);
            return 2;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            ItemStack map = held[0];
            TreasureTarget t = map == null ? null : map.get(ActivityRegistry.TREASURE.get());
            if (t == null) {
                check("opening the target chest solves the map", false, "no map");
                return 0;
            }
            BlockPos chest = new BlockPos(t.x(), t.y(), t.z());
            fp.moveTo(chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5);
            CharacterData d = Characters.get(fp);
            int lvl = d.level;
            long xp = d.xp;
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // a real right click on the chest (the same path a player's click takes)
            fp.gameMode.useItemOn(fp, l, ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
            TreasureTarget after = map.get(ActivityRegistry.TREASURE.get());
            boolean looted = TreasureMaps.data(s).looted.contains(t.key()) && d.flags.contains(TreasureMaps.LOOTED_FLAG + t.key());
            check("opening the target chest solves the map", after != null && after.solved() && looted && (d.level > lvl || d.xp > xp),
                    "solved=" + (after != null && after.solved()) + " looted=" + looted + " xp " + xp + " -> " + d.xp + " (level " + lvl + " -> " + d.level + ")");
            // the next map drawn at the same spot leads elsewhere
            TreasureTarget next = TreasureMaps.pick(s, chest, 42L, fp, false);
            check("a found secret is not handed out again while others remain", next != null && !next.key().equals(t.key()), String.valueOf(next));
            fp.getInventory().setItem(20, ItemStack.EMPTY);
            l.getChunkSource().removeRegionTicket(TicketType.FORCED, new ChunkPos(chest), 1, new ChunkPos(chest));
            // torn halves
            fp.getInventory().setItem(21, TornMapItem.of(0));
            fp.getInventory().setItem(22, TornMapItem.of(1));
            ItemStack grand = TornMapItem.combine(fp, fp.getInventory().getItem(21));
            TreasureTarget g = grand.get(ActivityRegistry.TREASURE.get());
            check("two torn halves piece together into a grand treasure map", g != null && g.grand() && fp.getInventory().getItem(21).isEmpty()
                    && fp.getInventory().getItem(22).isEmpty(), String.valueOf(g));
            clearItem(fp, ActivityItems.TREASURE_MAP.get());
            return 0;
        });
        // ---------- shops and quests ----------
        steps.add(s -> {
            boolean shady = ActivityShops.extras("bartender", null).stream().anyMatch(x -> x.item().equals("grandline:treasure_map"));
            Island pirateIsle = WorldPlan.of(s).islands.stream().filter(i -> i.faction.equals("pirate")).findFirst().orElse(null);
            boolean pirateMerchant = pirateIsle != null && ActivityShops.extras("merchant", pirateIsle).stream().anyMatch(x -> x.item().equals("grandline:treasure_map"));
            Island civil = WorldPlan.of(s).byId("shiokaze_isle");
            boolean honest = ActivityShops.extras("merchant", civil).stream().noneMatch(x -> x.item().equals("grandline:treasure_map"));
            boolean cook = ActivityShops.extras("cook", null).stream().anyMatch(x -> x.item().equals("grandline:saltfire_curry"));
            check("shady dealers sell treasure maps, cooks sell dishes", shady && pirateMerchant && honest && cook,
                    "bartender=" + shady + " pirate merchant=" + pirateMerchant + " honest merchant clean=" + honest + " cook=" + cook);
            QuestDef rumour = QuestRegistry.get("tavern_rumour"), board = QuestRegistry.get("board_treasure");
            boolean rewardMap = rumour != null && rumour.rewardItems().stream().anyMatch(sup -> sup.get().is(ActivityItems.TREASURE_MAP.get()));
            check("quests give treasure maps and ask for treasure", rewardMap && board != null && board.objectives().get(0).type().equals("treasure"), "");
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            d.completedQuests.remove("cooks_catch");
            d.questProgress.remove("cooks_catch");
            boolean started = Quests.start(fp, "cooks_catch");
            List<ItemStack> catches = new ArrayList<>();
            for (int i = 0; i < 5; i++) catches.add(new ItemStack(ActivityItems.HARBOR_BREAM.get()));
            for (ItemStack c : catches) SeaFishing.onCatch(fp, List.of(c));
            boolean paid = count(fp, ActivityItems.SALTFIRE_CURRY.get()) >= 2;
            check("a fishing errand counts sea catches and pays in dishes", started && d.completedQuests.contains("cooks_catch") && paid,
                    "started=" + started + " done=" + d.completedQuests.contains("cooks_catch") + " curry=" + count(fp, ActivityItems.SALTFIRE_CURRY.get()));
            clearItem(fp, ActivityItems.SALTFIRE_CURRY.get());
            return 0;
        });

        // ---------- fishing at sea, through the real fishing loot table ----------
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            Characters.get(fp).masteryXp.remove(SeaFishing.MASTERY);
            Map<SeaZone, ServerLevel> levels = new LinkedHashMap<>();
            Map<SeaZone, int[]> spots = new LinkedHashMap<>();
            spots.put(SeaZone.EAST_BLUE, new int[]{20000, 17000});
            spots.put(SeaZone.NORTH_BLUE, new int[]{20000, -17000});
            spots.put(SeaZone.PARADISE, new int[]{20000, 0});
            spots.put(SeaZone.CALM_BELT, new int[]{20000, 5300});
            spots.put(SeaZone.NEW_WORLD, new int[]{-20000, 0});
            spots.put(SeaZone.DEEP_SEA, new int[]{0, 900});
            spots.put(SeaZone.SKY_SEA, new int[]{0, 1500});
            Set<Item> all = new HashSet<>();
            for (var f : ActivityItems.FISH) all.add(f.get());
            List<String> details = new ArrayList<>();
            boolean allOk = true;
            for (var e : spots.entrySet()) {
                SeaZone zone = e.getKey();
                ServerLevel l = zone == SeaZone.DEEP_SEA ? s.getLevel(DeepWorld.LEVEL) : zone == SeaZone.SKY_SEA ? s.getLevel(SkyWorld.LEVEL) : ow;
                if (l == null) {
                    allOk = false;
                    details.add(zone.id() + ": no level");
                    continue;
                }
                Vec3 at = findSea(l, e.getValue()[0], e.getValue()[1], zone);
                if (at == null) {
                    allOk = false;
                    details.add(zone.id() + ": no open sea found");
                    continue;
                }
                Set<Item> own = new HashSet<>();
                for (var c : SeaFishing.table(zone)) own.add(c.item().get());
                Map<Item, Integer> got = fish(l, fp, at, 300).counts();
                int mine = 0, foreign = 0;
                for (var g : got.entrySet()) {
                    if (own.contains(g.getKey())) mine += g.getValue();
                    else if (all.contains(g.getKey()) && g.getKey() != ActivityItems.SEA_KING_FRY.get()) foreign += g.getValue();
                }
                boolean ok = mine > 30 && foreign == 0;
                allOk &= ok;
                check("fishing at sea in " + zone.id() + " yields its own catches", ok, mine + " own, " + foreign + " foreign in 300 rolls at "
                        + (int) at.x + "," + (int) at.z + ": " + names(got));
            }
            // an island pond keeps the vanilla catch
            Island dawn = WorldPlan.of(s).byId("dawn_island");
            Map<Item, Integer> pond = fish(ow, fp, new Vec3(dawn.x + 0.5, 64, dawn.z + 0.5), 150).counts();
            int seaFish = 0;
            for (var g : pond.entrySet()) if (all.contains(g.getKey())) seaFish += g.getValue();
            check("an island pond keeps the vanilla catch", seaFish == 0 && SeaFishing.seaAt(ow, dawn.x, dawn.z) == null, names(pond));
            return 0;
        });
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            Vec3 at = findSea(ow, 20000, 0, SeaZone.PARADISE);
            if (at == null) {
                check("fishing skill raises catch quality", false, "no Paradise sea spot");
                return 0;
            }
            d.masteryXp.remove(SeaFishing.MASTERY);
            double q0 = fish(ow, fp, at, 400).avgQuality();
            d.masteryXp.put(SeaFishing.MASTERY, 20000);
            int lvl = d.mastery(SeaFishing.MASTERY);
            double q1 = fish(ow, fp, at, 400).avgQuality();
            check("fishing skill raises catch quality", q1 > q0 + 0.2, String.format("avg quality %.2f at skill 0, %.2f at skill %d", q0, q1, lvl));
            d.masteryXp.remove(SeaFishing.MASTERY);
            int before = d.masteryXp.getOrDefault(SeaFishing.MASTERY, 0);
            int xp = SeaFishing.onCatch(fp, List.of(new ItemStack(ActivityItems.RAINBOW_TUNA.get())));
            check("landing a sea catch trains the fishing skill", d.masteryXp.getOrDefault(SeaFishing.MASTERY, 0) > before && xp > 2,
                    before + " -> " + d.masteryXp.getOrDefault(SeaFishing.MASTERY, 0));
            var clear = SeaFishing.odds(SeaZone.NEW_WORLD, 0, false, false, com.grandline.world.weather.SeaWeather.Type.CLEAR, 0);
            var storm = SeaFishing.odds(SeaZone.NEW_WORLD, 0, false, false, com.grandline.world.weather.SeaWeather.Type.STORM, 0.9f);
            var nav = SeaFishing.odds(SeaZone.NEW_WORLD, 0, true, false, com.grandline.world.weather.SeaWeather.Type.CLEAR, 0);
            var cook = SeaFishing.odds(SeaZone.NEW_WORLD, 0, false, true, com.grandline.world.weather.SeaWeather.Type.CLEAR, 0);
            check("storms and a crew navigator bring up giants and Sea King fry; a crew cook raises quality",
                    storm.fryChance() > clear.fryChance() && storm.giantBoost() > clear.giantBoost() && nav.fryChance() > clear.fryChance()
                            && nav.giantBoost() > clear.giantBoost() && cook.fineChance() > clear.fineChance()
                            && SeaFishing.odds(SeaZone.EAST_BLUE, 0, true, false, com.grandline.world.weather.SeaWeather.Type.STORM, 1f).fryChance() == 0,
                    String.format("fry clear %.3f storm %.3f navigator %.3f; fine clear %.2f cook %.2f", clear.fryChance(), storm.fryChance(), nav.fryChance(),
                            clear.fineChance(), cook.fineChance()));
            return 0;
        });

        // ---------- cooking ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            var rm = s.getRecipeManager();
            List<String> bad = new ArrayList<>();
            cook(rm, l, RecipeType.SMOKING, ActivityItems.RAINBOW_TUNA.get(), ActivityItems.GRILLED_FISH.get(), bad);
            cook(rm, l, RecipeType.SMELTING, ActivityItems.BLUE_SARDINE.get(), ActivityItems.GRILLED_FISH.get(), bad);
            cook(rm, l, RecipeType.CAMPFIRE_COOKING, ActivityItems.GHOST_RAY.get(), ActivityItems.GRILLED_FISH.get(), bad);
            cook(rm, l, RecipeType.CAMPFIRE_COOKING, ModItems.SEA_KING_STEAK.get(), ActivityItems.SEA_KING_ROAST.get(), bad);
            cook(rm, l, RecipeType.SMOKING, ModItems.SEA_KING_STEAK.get(), ActivityItems.SEA_KING_ROAST.get(), bad);
            craft(rm, l, List.of(Items.BOWL, ActivityItems.BLUE_SARDINE.get(), ModItems.MEAT_ON_BONE.get(), Items.POTATO, Items.CARROT, Items.RED_MUSHROOM), ActivityItems.SALTFIRE_CURRY.get(), bad);
            craft(rm, l, List.of(Items.BOWL, ActivityItems.HARBOR_BREAM.get(), ActivityItems.RAINBOW_TUNA.get(), Items.POTATO, Items.CARROT), ActivityItems.FISHERMAN_STEW.get(), bad);
            craft(rm, l, List.of(ActivityItems.CRIMSON_MARLIN.get(), ActivityItems.RAINBOW_TUNA.get(), Items.KELP), ActivityItems.TIDEWATER_SASHIMI.get(), bad);
            craft(rm, l, List.of(Items.BOWL, ActivityItems.SEA_KING_FRY.get(), Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.POTATO, Items.CARROT), ActivityItems.SEA_KING_HOTPOT.get(), bad);
            craft(rm, l, List.of(Items.BOWL, ActivityItems.ABYSS_LANTERNFISH.get(), ActivityItems.GHOST_RAY.get(), Items.KELP, Items.POTATO), ActivityItems.ABYSS_CHOWDER.get(), bad);
            craft(rm, l, List.of(ModItems.MEAT_ON_BONE.get(), ModItems.MEAT_ON_BONE.get(), ActivityItems.SEA_KING_ROAST.get(), ActivityItems.GRILLED_FISH.get(), Items.BREAD, Items.APPLE),
                    ActivityItems.PIRATES_FEAST.get(), bad);
            check("dishes cook in the smoker, furnace and campfire and come together in the crafting grid", bad.isEmpty(), bad.isEmpty() ? "11 recipes" : String.join("; ", bad));
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            CombatState cs = CombatState.of(fp);
            for (String b : List.of(Meals.STAMINA, Meals.STRENGTH, Meals.HAKI, Meals.SWIM, Meals.WARM, Meals.COOL, Meals.PRIME)) cs.buffs.remove(b);
            ItemStack curry = new ItemStack(ActivityItems.SALTFIRE_CURRY.get());
            ItemStack left = curry.finishUsingItem(l, fp);
            int str = cs.buffs.getOrDefault(Meals.STRENGTH, 0), warm = cs.buffs.getOrDefault(Meals.WARM, 0);
            check("eating a dish applies its buffs", str == 150 * 20 && warm == 240 * 20 && !cs.hasBuff(Meals.PRIME),
                    "strength " + str + " ticks, cold resistance " + warm + " ticks, left " + left);
            ItemStack prime = new ItemStack(ActivityItems.TIDEWATER_SASHIMI.get());
            prime.set(ActivityRegistry.QUALITY.get(), 2);
            prime.finishUsingItem(l, fp);
            int cool = cs.buffs.getOrDefault(Meals.COOL, 0);
            check("a prime dish lasts twice as long and strengthens every meal effect", cool == 480 * 20 && cs.hasBuff(Meals.PRIME) && Meals.potency(fp) > 1.2f,
                    "heat resistance " + cool + " ticks, prime=" + cs.hasBuff(Meals.PRIME));
            cs.buffs.remove(Meals.PRIME);
            // regeneration on the player
            CharacterData d = Characters.get(fp);
            d.stamina = 10;
            d.hakiGauge = 10;
            cs.buffs.put(Meals.STAMINA, 100);
            cs.buffs.put(Meals.HAKI, 100);
            for (int i = 0; i < 20; i++) Meals.tick(fp);
            check("stamina and Haki meals regenerate the gauges", d.stamina > 10 + 6 && d.hakiGauge > 10 + 5, String.format("stamina 10 -> %.1f, haki 10 -> %.1f in 20 ticks", d.stamina, d.hakiGauge));
            // damage: strength dealt, cold and heat taken (cows in the test arena)
            BlockPos c = SelfTest.arena(l).offset(-8, 0, 8);
            Cow attacker = EntityType.COW.create(l), victim = EntityType.COW.create(l);
            attacker.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            victim.moveTo(c.getX() + 2.5, c.getY(), c.getZ() + 0.5);
            attacker.setNoAi(true);
            victim.setNoAi(true);
            l.addFreshEntity(attacker);
            l.addFreshEntity(victim);
            float plain = hurt(victim, ModDamageTypes.source(l, ModDamageTypes.STRIKE, attacker), 4);
            CombatState.of(attacker).buffs.put(Meals.STRENGTH, 200);
            float strong = hurt(victim, ModDamageTypes.source(l, ModDamageTypes.STRIKE, attacker), 4);
            float cold0 = hurt(victim, ModDamageTypes.source(l, ModDamageTypes.ICE, null), 4);
            float heat0 = hurt(victim, ModDamageTypes.source(l, ModDamageTypes.FIRE, null), 4);
            CombatState.of(victim).buffs.put(Meals.WARM, 200);
            CombatState.of(victim).buffs.put(Meals.COOL, 200);
            float cold1 = hurt(victim, ModDamageTypes.source(l, ModDamageTypes.ICE, null), 4);
            float heat1 = hurt(victim, l.damageSources().inFire(), 4);
            check("meal strength adds damage; cold and heat resistance halve ice and fire damage",
                    Math.abs(strong - plain * 1.15f) < 0.05f && plain > 0 && Math.abs(cold1 - cold0 * 0.5f) < 0.05f && heat1 < heat0 * 0.6f && heat1 > 0,
                    String.format("strike %.2f -> %.2f, ice %.2f -> %.2f, fire %.2f -> %.2f", plain, strong, cold0, cold1, heat0, heat1));
            attacker.discard();
            victim.discard();
            return 0;
        });
        // ---------- the crew cook ----------
        steps.add(s -> {
            var n = NotableCharacters.get("hilde");
            Island isl = WorldPlan.of(s).byId(n.island());
            ChunkPos cp = new ChunkPos((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            s.overworld().getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
            // without a cook: a plain dish
            FakePlayer fp = SelfTest.fake(s);
            fp.containerMenu = fp.inventoryMenu;
            ItemStack stew = new ItemStack(ActivityItems.FISHERMAN_STEW.get());
            stew.onCraftedBy(s.overworld(), fp, 1);
            noCook[0] = Meals.quality(stew);
            return 40;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            HumanoidNpc hilde = NotableSpawner.spawnNow(l, "hilde");
            if (hilde == null) {
                UUID id = NotableState.get(s).entry("hilde").entity;
                if (id != null && l.getEntity(id) instanceof HumanoidNpc h) hilde = h;
            }
            if (hilde == null) {
                check("a crew cook makes better dishes", false, "Hilde (crew cook) could not be spawned");
                return 0;
            }
            boolean joined = CrewManager.recruit(fp, hilde);
            boolean hasCook = Meals.hasCrewCook(fp);
            fp.containerMenu = fp.inventoryMenu;
            ItemStack stew = new ItemStack(ActivityItems.FISHERMAN_STEW.get());
            stew.onCraftedBy(l, fp, 1);
            int withCook = Meals.quality(stew);
            check("a crew cook makes better dishes", joined && hasCook && withCook == noCook[0] + 1,
                    "recruited=" + joined + " cook in crew=" + hasCook + " quality " + noCook[0] + " -> " + withCook);
            // the galley: a raw catch from the hold becomes a fine (or prime) dish, and everyone gets a hot meal
            ItemStack raw = new ItemStack(ActivityItems.RAINBOW_TUNA.get());
            raw.set(ActivityRegistry.QUALITY.get(), 1);
            fp.getInventory().setItem(23, raw);
            CombatState.of(fp).buffs.remove(Meals.STAMINA);
            ItemStack dish = Meals.galley(fp);
            check("the crew cook's galley cooks the catch and feeds the crew", dish.is(ActivityItems.GRILLED_FISH.get()) && Meals.quality(dish) == 2
                            && fp.getInventory().getItem(23).isEmpty() && CombatState.of(fp).hasBuff(Meals.STAMINA),
                    "cooked " + dish + " q" + Meals.quality(dish) + ", stamina meal=" + CombatState.of(fp).hasBuff(Meals.STAMINA));
            clearItem(fp, ActivityItems.GRILLED_FISH.get());
            CrewManager.disband(s, d.characterId);
            var n = NotableCharacters.get("hilde");
            Island isl = WorldPlan.of(s).byId(n.island());
            ChunkPos cp = new ChunkPos((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            l.getChunkSource().removeRegionTicket(TicketType.FORCED, cp, 3, cp);
            return 0;
        });
    }

    /** Phase 2 (after a restart): the secrets looted in phase 1 are still remembered by the world. */
    static void phase2(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            Set<String> looted = TreasureMaps.data(s).looted;
            check("looted island secrets persist across a restart", !looted.isEmpty(), looted.size() + " looted: " + looted);
            return 0;
        });
    }

    private static final int[] noCook = new int[1];

    // ================================================================== helpers

    /** The fake player needs a character (the quick phase runs without the character-creation step). */
    private static void ensureCharacter(MinecraftServer s) {
        FakePlayer fp = SelfTest.fake(s);
        CharacterData d = Characters.get(fp);
        if (d.created) return;
        CompoundTag req = new CompoundTag();
        req.putString("name", "Activity Tester");
        req.putString("race", "human");
        req.putString("faction", "pirate");
        req.putString("origin", "east");
        req.put("appearance", new Appearance().save());
        CharacterManager.create(fp, req);
    }

    private static String resource(String path) {
        try (InputStream in = ActivityTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Open sea of the wanted zone near (x, z) in the level's plan, or null. */
    private static Vec3 findSea(ServerLevel l, int x, int z, SeaZone zone) {
        for (int r = 0; r <= 3000; r += 150)
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                int px = x + (int) (Math.cos(a) * r), pz = z + (int) (Math.sin(a) * r);
                if (zone.isBlue() || zone.isGrandLine() || zone == SeaZone.CALM_BELT) {
                    if (WorldLayout.zoneAt(px, pz) != zone) continue;
                }
                if (SeaFishing.seaAt(l, px, pz) != zone) continue;
                WorldPlan plan = WorldPlan.forLevel(l);
                if (plan != null && plan.islandAt(px, pz) != null) continue;
                return new Vec3(px + 0.5, 62, pz + 0.5);
            }
        return null;
    }

    /** What came up in a series of rolls: item counts, and the summed quality of the sea catches. */
    private record Haul(Map<Item, Integer> counts, int seaFish, int qualitySum) {
        double avgQuality() {
            return seaFish == 0 ? 0 : (double) qualitySum / seaFish;
        }
    }

    /** Rolls the vanilla fishing table (with the loot modifier) as a bobber at {@code at} would. */
    private static Haul fish(ServerLevel l, FakePlayer fp, Vec3 at, int rolls) {
        LootTable table = l.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        FishingHook hook = new FishingHook(fp, l, 0, 0);
        hook.setPos(at);
        Map<Item, Integer> got = new LinkedHashMap<>();
        int sea = 0, quality = 0;
        try {
            for (int i = 0; i < rolls; i++) {
                LootParams params = new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN, at)
                        .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).withParameter(LootContextParams.THIS_ENTITY, hook)
                        .withParameter(LootContextParams.ATTACKING_ENTITY, fp).create(LootContextParamSets.FISHING);
                for (ItemStack st : table.getRandomItems(params)) {
                    got.merge(st.getItem(), st.getCount(), Integer::sum);
                    if (st.getItem() instanceof SeaFishItem) {
                        sea++;
                        quality += Meals.quality(st);
                    }
                }
            }
        } finally {
            fp.fishing = null;
            hook.discard();
        }
        return new Haul(got, sea, quality);
    }

    private static String names(Map<Item, Integer> got) {
        StringBuilder b = new StringBuilder();
        for (var e : got.entrySet()) b.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).append('=').append(e.getValue()).append(' ');
        return b.toString().trim();
    }

    private static <T extends net.minecraft.world.item.crafting.AbstractCookingRecipe> void cook(net.minecraft.world.item.crafting.RecipeManager rm, ServerLevel l,
                                                                                                 RecipeType<T> type, Item in, Item want, List<String> bad) {
        var r = rm.getRecipeFor(type, new SingleRecipeInput(new ItemStack(in)), l);
        ItemStack out = r.map(h -> h.value().getResultItem(l.registryAccess())).orElse(ItemStack.EMPTY);
        if (!out.is(want)) bad.add(type + " " + in + " -> " + out);
    }

    private static void craft(net.minecraft.world.item.crafting.RecipeManager rm, ServerLevel l, List<Item> in, Item want, List<String> bad) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(i < in.size() ? new ItemStack(in.get(i)) : ItemStack.EMPTY);
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var r = rm.getRecipeFor(RecipeType.CRAFTING, input, l);
        ItemStack out = r.map(h -> h.value().assemble(input, l.registryAccess())).orElse(ItemStack.EMPTY);
        if (!out.is(want)) bad.add("crafting " + in + " -> " + out);
    }

    private static float hurt(net.minecraft.world.entity.LivingEntity e, net.minecraft.world.damagesource.DamageSource src, float amount) {
        e.setHealth(e.getMaxHealth());
        e.invulnerableTime = 0;
        e.clearFire();
        float before = e.getHealth();
        e.hurt(src, amount);
        float took = before - e.getHealth();
        e.setHealth(e.getMaxHealth());
        e.invulnerableTime = 0;
        e.clearFire();
        return took;
    }

    private static int count(FakePlayer fp, Item item) {
        int n = 0;
        for (int i = 0; i < fp.getInventory().getContainerSize(); i++) if (fp.getInventory().getItem(i).is(item)) n += fp.getInventory().getItem(i).getCount();
        return n;
    }

    private static void clearItem(FakePlayer fp, Item item) {
        for (int i = 0; i < fp.getInventory().getContainerSize(); i++) if (fp.getInventory().getItem(i).is(item)) fp.getInventory().setItem(i, ItemStack.EMPTY);
    }

    private ActivityTests() {}
}
