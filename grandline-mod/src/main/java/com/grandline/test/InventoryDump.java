package com.grandline.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grandline.GrandLine;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.item.GLWeaponItem;
import com.grandline.npc.Archetypes;
import com.grandline.player.Race;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.ship.ShipClass;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.Builder;
import com.grandline.world.landmark.Landmarks;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Buildings;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Writes inventory.json (islands, races, fruits, weapons, styles, ships, NPC roles, quests) from the live registries
 * and the actual world plan, so docs/INVENTORY.md (tools/gen_inventory.py) reflects the code instead of intentions.
 * Settlement and landmark contents are counted by running the real builders into a counting sink.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = GrandLine.MODID)
public final class InventoryDump {

    @net.neoforged.bus.api.SubscribeEvent
    public static void onStart(net.neoforged.neoforge.event.server.ServerStartedEvent e) {
        if (!"1".equals(System.getenv("GL_SELFTEST"))) return;
        try {
            write(e.getServer());
        } catch (Exception ex) {
            GrandLine.LOGGER.error("Inventory dump failed", ex);
        }
    }

    /** Counts blocks, NPC markers, chests and secrets written by a builder. */
    static final class CountingSink implements BlockSink {
        int blocks, chests, npcs, secrets, boards;
        final Map<String, Integer> roles = new TreeMap<>();

        @Override public void set(int x, int y, int z, BlockState state) {
            if (!state.isAir()) blocks++;
        }

        @Override public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
            chests++;
            set(x, y, z, chest);
        }

        @Override public void marker(String kind, BlockPos pos, CompoundTag data) {
            switch (kind) {
                case "npc" -> {
                    npcs++;
                    roles.merge(data.getString("role"), 1, Integer::sum);
                }
                case "treasure", "secret", "poneglyph" -> secrets++;
                case "bounty_board", "quest_board" -> boards++;
                default -> {}
            }
        }
    }

    public static Path write(MinecraftServer server) throws Exception {
        JsonObject root = new JsonObject();
        WorldPlan plan = WorldPlan.of(server);
        Map<String, Integer> questsByIsland = new HashMap<>();
        for (QuestDef q : QuestRegistry.all()) // story quests per island (notice-board jobs are counted separately)
            if (q.island() != null && !q.island().isEmpty() && !q.giverRole().equals(com.grandline.quest.story.JobBoard.GIVER)) questsByIsland.merge(q.island(), 1, Integer::sum);

        JsonArray islands = new JsonArray();
        java.util.List<WorldPlan> plans = new java.util.ArrayList<>();
        for (var level : server.getAllLevels()) {
            WorldPlan lp = WorldPlan.forLevel(level);
            if (lp != null && !plans.contains(lp)) plans.add(lp);
        }
        for (WorldPlan pl : plans) {
            for (Island isl : pl.islands) {
                JsonObject o = new JsonObject();
                o.addProperty("id", isl.id);
                o.addProperty("plan", pl.kind.name().toLowerCase(Locale.ROOT));
                o.addProperty("name", isl.name);
                o.addProperty("canon", isl.canon);
                o.addProperty("zone", isl.zone.name());
                o.addProperty("x", isl.x);
                o.addProperty("z", isl.z);
                o.addProperty("radius", isl.radius);
                o.addProperty("shape", isl.shape.name());
                o.addProperty("theme", isl.theme.name() + (isl.altTheme != null ? "/" + isl.altTheme.name() : ""));
                o.addProperty("tier", isl.tier);
                o.addProperty("settlement", isl.settlementType.name());
                o.addProperty("faction", isl.faction);
                o.addProperty("starter", isl.starterFor == null ? "" : isl.starterFor.name());
                CountingSink sink = new CountingSink();
                int buildings = 0;
                JsonArray lms = new JsonArray();
                try {
                    Settlement st = isl.settlement();
                    o.addProperty("dock", st.hasDock);
                    for (Plot p : st.plots) {
                        if (p.type() == BuildingType.LANDMARK) {
                            Landmarks.Spec spec = Landmarks.spec(p.landmark());
                            CountingSink ls = new CountingSink();
                            Landmarks.build(p, isl, new Builder(ls, p.x(), p.y(), p.z(), spec.width(), spec.depth(), p.rotation(), p.seed()));
                            JsonObject lm = new JsonObject();
                            lm.addProperty("id", p.landmark());
                            lm.addProperty("implemented", Landmarks.implemented(p.landmark()));
                            lm.addProperty("blocks", ls.blocks);
                            lm.addProperty("npcs", ls.npcs);
                            lm.addProperty("chests", ls.chests);
                            lms.add(lm);
                            sink.npcs += ls.npcs;
                            sink.chests += ls.chests;
                            sink.secrets += ls.secrets;
                            sink.boards += ls.boards;
                            ls.roles.forEach((k, v) -> sink.roles.merge(k, v, Integer::sum));
                        } else {
                            buildings++;
                            Buildings.build(p, isl.style, new Builder(sink, p.x(), p.y(), p.z(), p.type().w, p.type().d, p.rotation(), p.seed()));
                        }
                    }
                    JsonArray secrets = new JsonArray();
                    for (Plot p : st.secrets) {
                        CountingSink ss = new CountingSink();
                        Buildings.build(p, isl.style, new Builder(ss, p.x(), p.y(), p.z(), p.type().w, p.type().d, p.rotation(), p.seed()));
                        secrets.add(p.type().name().toLowerCase(Locale.ROOT));
                        sink.chests += ss.chests;
                        sink.secrets += Math.max(1, ss.secrets);
                    }
                    o.add("secretList", secrets);
                } catch (Exception ex) {
                    o.addProperty("error", ex.toString());
                }
                o.addProperty("buildings", buildings);
                o.addProperty("npcs", sink.npcs);
                o.addProperty("chests", sink.chests);
                o.addProperty("secrets", sink.secrets);
                o.addProperty("boards", sink.boards);
                o.add("landmarks", lms);
                JsonObject roles = new JsonObject();
                sink.roles.forEach(roles::addProperty);
                o.add("roles", roles);
                o.addProperty("quests", questsByIsland.getOrDefault(isl.id, 0));
                var story = com.grandline.quest.story.IslandStories.storyOf(isl.id); // generated local story (original islands)
                o.addProperty("story", story == null ? "" : story.template().id);
                o.addProperty("storyPeople", story == null ? 0 : story.people().size());
                int jobs = 0;
                for (QuestDef q : QuestRegistry.offeredBy(com.grandline.quest.story.JobBoard.GIVER, isl.id, false))
                    if (q.island().equals(isl.id) && com.grandline.quest.QuestRegistry.variantFaction(q.id()) == null) jobs++;
                o.addProperty("boardJobs", jobs);
                islands.add(o);
            }
        }
        root.add("islands", islands);

        JsonArray races = new JsonArray();
        for (Race r : Race.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("playable", r.playable);
            o.addProperty("canon", r.canon);
            o.addProperty("minScale", r.minScale);
            o.addProperty("maxScale", r.maxScale);
            o.addProperty("parts", r.parts);
            o.addProperty("abilities", count(Ability.Source.RACE, r.id()));
            races.add(o);
        }
        root.add("races", races);

        JsonArray fruits = new JsonArray();
        for (DevilFruit f : DevilFruits.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id);
            o.addProperty("type", f.type.name());
            o.addProperty("tier", f.tier);
            o.addProperty("canon", f.canon);
            o.addProperty("element", f.element.name());
            JsonArray ab = new JsonArray();
            for (Ability a : f.abilities()) ab.add(a.id().substring(a.id().lastIndexOf('/') + 1) + (a.awakened() ? "*" : ""));
            o.add("abilities", ab);
            fruits.add(o);
        }
        root.add("fruits", fruits);

        JsonArray weapons = new JsonArray();
        for (WeaponClass w : WeaponClass.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.name().toLowerCase(Locale.ROOT));
            o.addProperty("ranged", w.ranged);
            o.addProperty("twoHanded", w.twoHanded);
            o.addProperty("chain", w.light.size());
            JsonArray items = new JsonArray();
            for (var it : BuiltInRegistries.ITEM) if (it instanceof GLWeaponItem gw && gw.weaponClass == w) items.add(BuiltInRegistries.ITEM.getKey(it).getPath() + ":" + gw.grade);
            o.add("items", items);
            weapons.add(o);
        }
        root.add("weapons", weapons);
        JsonObject arts = new JsonObject();
        for (Ability a : Abilities.all()) if (a.source() == Ability.Source.WEAPON) arts.addProperty(a.owner(), (arts.has(a.owner()) ? arts.get(a.owner()).getAsInt() : 0) + 1);
        root.add("weaponArts", arts);

        JsonArray styles = new JsonArray();
        for (FightingStyle s : FightingStyles.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("canon", s.canon);
            o.addProperty("race", s.race == null ? "" : s.race.id());
            o.addProperty("chain", s.light.size());
            JsonArray ab = new JsonArray();
            for (Ability a : s.abilities) ab.add(a.id().substring(a.id().lastIndexOf('/') + 1));
            o.add("techniques", ab);
            styles.add(o);
        }
        root.add("styles", styles);

        JsonArray ships = new JsonArray();
        for (ShipClass c : ShipClass.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id());
            o.addProperty("length", c.length);
            o.addProperty("beam", c.beam);
            o.addProperty("hull", c.hull);
            o.addProperty("speed", c.speed);
            o.addProperty("cannons", c.cannons);
            o.addProperty("masts", c.masts);
            o.addProperty("seats", c.seats);
            o.addProperty("price", c.price);
            o.addProperty("interior", c.interior);
            ships.add(o);
        }
        root.add("ships", ships);

        JsonArray roles = new JsonArray();
        for (String r : new TreeSet<>(Archetypes.roles())) {
            var a = Archetypes.get(r);
            JsonObject o = new JsonObject();
            o.addProperty("id", r);
            o.addProperty("faction", a.faction().name());
            o.addProperty("tier", a.baseTier());
            o.addProperty("style", a.style());
            o.addProperty("race", a.race().id());
            o.addProperty("hostility", a.hostility().name());
            o.addProperty("fruitChance", a.fruitChance());
            o.addProperty("hakiChance", a.hakiChance());
            roles.add(o);
        }
        root.add("roles", roles);

        JsonArray quests = new JsonArray();
        for (QuestDef q : QuestRegistry.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", q.id());
            o.addProperty("arc", q.arc());
            o.addProperty("island", q.island());
            o.addProperty("giver", q.giverRole());
            o.addProperty("objectives", q.objectives().size());
            o.addProperty("repeatable", q.repeatable());
            o.addProperty("generated", QuestRegistry.isGenerated(q.id()));
            quests.add(o);
        }
        root.add("quests", quests);

        JsonArray notables = new JsonArray();
        for (var n : com.grandline.npc.notable.NotableCharacters.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", n.id());
            o.addProperty("nameKey", n.nameKey());
            o.addProperty("titleKey", n.titleKey());
            o.addProperty("group", n.group().name().toLowerCase(Locale.ROOT));
            o.addProperty("faction", n.faction().name().toLowerCase(Locale.ROOT));
            o.addProperty("island", n.island());
            o.addProperty("tier", n.tier());
            o.addProperty("fruit", n.fruit() == null ? "" : n.fruit());
            o.addProperty("style", n.style());
            o.addProperty("race", n.race());
            o.addProperty("recruitable", n.recruitable());
            o.addProperty("crewRole", n.crewRole() == null ? "" : n.crewRole());
            o.addProperty("generated", com.grandline.npc.notable.NotableCharacters.isGenerated(n.id()));
            notables.add(o);
        }
        root.add("notables", notables);
        root.addProperty("abilities", Abilities.all().size());

        Path out = server.getServerDirectory().resolve("inventory.json");
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        GrandLine.LOGGER.info("Inventory written to {}", out.toAbsolutePath());
        return out;
    }

    private static int count(Ability.Source src, String owner) {
        int n = 0;
        for (Ability a : Abilities.all()) if (a.source() == src && a.owner().equals(owner)) n++;
        return n;
    }

    private InventoryDump() {}
}
