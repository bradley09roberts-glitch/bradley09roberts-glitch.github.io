package com.grandline.quest.story;

import com.grandline.GrandLine;
import com.grandline.creature.WildBeast;
import com.grandline.net.GLNetwork;
import com.grandline.npc.MarkerCollector;
import com.grandline.npc.notable.NotableCharacters.Disposition;
import com.grandline.npc.notable.NotableCharacters.Group;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.StoryContext.Spot;
import com.grandline.quest.story.StoryModel.*;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * Notice boards: right-click a town's bounty board to read its jobs. Besides the five classic board jobs, every
 * settled island's board has jobs generated from the seed and scaled to its sea: deliver a letter to someone on a
 * nearby island, escort a merchant to another island's dock, hunt three named outlaws in turn (each with a wanted
 * poster saying where they were last seen; they turn up only for whoever hunts them), clear a beast nest or a bandit
 * camp, salvage a wreck, and bring in fish, hot meals or timber.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class JobBoard {
    /** Dialogue "npc" id that means "the notice board you are reading". */
    public static final int BOARD_ID = -1;
    public static final String GIVER = "bounty_board";
    public static final String[] TYPES = {"deliver", "escort", "hunt", "nest", "camp", "wreck", "fish", "cook", "supply", "sink", "convoy"};

    /** What a job hands over when it starts: a wanted poster (hunts) or a sealed letter (deliveries). */
    public record StartGift(String kind, String name, Component where, int x, int z, long bounty, String target) {}

    private static final Map<String, StartGift> GIFTS = new HashMap<>();
    /** Board you last read, per player (choices must come from near it). */
    private static final Map<UUID, BlockPos> READING = new HashMap<>();

    // ================================================================== generation (pure)

    /** Whether an island's town has a notice board (the settlement types that build one, plus canon towns). */
    static boolean hasBoard(Island i) {
        return switch (i.settlementType) {
            case VILLAGE, TOWN, PORT, CITY, MARINE_BASE -> true;
            case NONE, RUINS, HAMLET, FORTRESS -> i.canon && i.settlementType != SettlementType.NONE && i.settlementType != SettlementType.RUINS;
        };
    }

    static void generate(WorldPlan plan, List<IslandStory> stories, List<QuestDef> quests, List<Person> people, Map<String, Component> titles,
                         Map<String, Component> descs, Map<String, List<SiteRef>> qs, Map<String, Site> sites, Map<String, StartGift> gifts) {
        Map<String, Person> giverOf = new HashMap<>();
        for (IslandStory s : stories)
            for (Person p : s.people()) if (p.id().equals(s.giver())) giverOf.put(s.island(), p);
        for (Island isl : plan.islands) {
            if (plan.kind != WorldPlan.Kind.OCEAN || isl.radius < 60 || isl.zone == SeaZone.RED_LINE || !hasBoard(isl)) continue;
            new Gen(plan, isl, giverOf, quests, people, titles, descs, qs, sites, gifts).all();
        }
    }

    static final class Gen {
        final WorldPlan plan;
        final Island isl;
        final StoryContext c;
        final Map<String, Person> giverOf;
        final List<QuestDef> quests;
        final List<Person> people;
        final Map<String, Component> titles, descs;
        final Map<String, List<SiteRef>> qs;
        final Map<String, Site> sites;
        final Map<String, StartGift> gifts;
        final int t;

        Gen(WorldPlan plan, Island isl, Map<String, Person> giverOf, List<QuestDef> quests, List<Person> people, Map<String, Component> titles,
            Map<String, Component> descs, Map<String, List<SiteRef>> qs, Map<String, Site> sites, Map<String, StartGift> gifts) {
            this.plan = plan;
            this.isl = isl;
            this.c = new StoryContext(plan, isl, "", 0x10B5L);
            this.giverOf = giverOf;
            this.quests = quests;
            this.people = people;
            this.titles = titles;
            this.descs = descs;
            this.qs = qs;
            this.sites = sites;
            this.gifts = gifts;
            this.t = c.tier();
        }

        String id(String type, int k) {
            return "job_" + type + "_" + isl.id + (k >= 0 ? "_" + k : "");
        }

        void quest(String id, String type, List<QuestDef.Objective> obj, double weight, Map<String, Integer> rep, List<Supplier<ItemStack>> items,
                   List<String> req, boolean repeat, Object[] args, @Nullable String descVariant) {
            int xp = (int) Math.round(StoryBuilder.XP[t] * weight * 0.6);
            long beli = Math.round(StoryBuilder.BELI[t] * weight);
            quests.add(new QuestDef(id, GIVER, isl.id, List.copyOf(obj), xp, beli, 0, Map.copyOf(rep), List.copyOf(items), List.copyOf(req), null, null, repeat, "job_board"));
            titles.put(id, Component.translatable("job.grandline." + type, args));
            descs.put(id, Component.translatable("job.grandline." + type + ".desc" + (descVariant == null ? "" : "." + descVariant), args));
        }

        Site site(String tag, Island where, Spot at, SiteKind kind, String what, @Nullable Component name) {
            String key = "job_" + isl.id + "#" + tag;
            Site s = new Site(key, where.id, at.x(), at.z(), at.y(), kind, what, Math.max(1, Math.min(10, where.tier)), name, Component.translatable("job.grandline.site." + tag.replaceAll("\\d+$", "")));
            sites.put(key, s);
            return s;
        }

        Person person(String slot, String role, Faction f, Disposition d, int tier, Island where, Spot at, String name, boolean conditional, Component title) {
            String[] kit = d == Disposition.VILLAIN ? StoryBuilder.kit(c.r, role) : new String[]{"brawler", ""};
            StoryContext wc = where == isl ? c : new StoryContext(plan, where, "", 0x10B5L);
            Person p = new Person("job_" + isl.id + "@" + slot, slot, name, title, role, Math.max(1, Math.min(12, tier)), f, d, kit[0], kit[1],
                    StoryBuilder.race(wc, role), where.id, at.x(), at.z(), conditional, d == Disposition.VILLAIN ? Group.VILLAIN : Group.PATRON);
            people.add(p);
            return p;
        }

        void all() {
            List<Island> near = c.neighbours(4, false);
            if (near.isEmpty()) return;
            // ---- deliveries: a sealed letter for the story-giver of a nearby original island
            int k = 0;
            for (Island y : c.neighbours(8, true)) {
                Person to = giverOf.get(y.id);
                if (to == null) continue;
                String id = id("deliver", k);
                double dist = y.distance(isl.x, isl.z);
                quest(id, "deliver", List.of(new QuestDef.Objective("talk", to.id(), 1)), 0.6 + Math.min(1.5, dist / 6000), Map.of("civilian", 5), List.of(),
                        List.of(), true, new Object[]{to.name(), StoryContext.islandName(y)}, null);
                gifts.put(id, new StartGift("letter", to.name(), StoryContext.islandName(y), y.x, y.z, 0, to.id()));
                if (++k >= 2) break;
            }
            // ---- escort: a merchant travels with you (a real follower, SeaJobs) to another island's dock; pirates wait there
            Island dest = null;
            for (Island y : near) if (y.settlement().hasDock) { dest = y; break; }
            if (dest != null) {
                StoryContext dc = new StoryContext(plan, dest, "", 0x10B5L);
                Spot dock = dc.dock();
                String merchant = PeopleNames.person(c.culture, c.r);
                Site ambush = site("ambush", dest, dock, SiteKind.ROLE, "pirate", null);
                String id = id("escort", -1);
                double w = 1.4 + Math.min(1.0, dest.distance(isl.x, isl.z) / 5000);
                quest(id, "escort", List.of(new QuestDef.Objective("kill", "pirate", 3), new QuestDef.Objective("escort", id, 1)), w, Map.of("civilian", 10),
                        List.of(), List.of(), true, new Object[]{merchant, StoryContext.islandName(dest)}, "follow");
                qs.put(id, List.of(new SiteRef(0, ambush.key(), 3)));
                gifts.put(id, new StartGift(SeaJobs.ESCORT, merchant, StoryContext.islandName(dest), dock.x(), dock.z(), 0, dest.id));
            }
            // ---- three named outlaws, one after another, each with a wanted poster
            for (int g = 0; g < 3; g++) {
                Island y = g == 0 || near.size() < g ? isl : near.get(g - 1);
                StoryContext yc = y == isl ? c : new StoryContext(plan, y, "", 0x10B5L);
                Spot hide = yc.wild("outlaw" + g + isl.id);
                boolean bandit = g == 0 && isl.zone.isBlue();
                String role = bandit ? "bandit" : "pirate_captain";
                int tier = Math.min(12, Math.max(2, y.tier + 1 + g));
                String name = PeopleNames.outlaw(PeopleNames.culture(y.zone), c.r);
                Person o = person("outlaw" + g, role, bandit ? Faction.WILD : Faction.PIRATE, Disposition.VILLAIN, tier, y, hide, name, true,
                        Component.translatable("job.grandline.who.outlaw", StoryContext.islandName(y)));
                Site guards = site("hideout" + g, y, hide, SiteKind.ROLE, bandit ? "bandit" : "pirate", null);
                String id = id("hunt", g);
                double w = 1.5 + g * 0.7;
                long bounty = Math.round(StoryBuilder.BELI[t] * w); // the poster's bounty is what the board pays
                Object[] args = {o.name(), StoryContext.islandName(y), String.format("%,d", bounty), hide.x(), hide.z()};
                List<String> req = g == 0 ? List.of() : List.of(id("hunt", g - 1));
                quest(id, "hunt", List.of(new QuestDef.Objective("defeat", o.id(), 1)), w, Map.of("civilian", 15, "bounty_hunter", 20), List.of(), req, false, args, null);
                quest(id + ".marine", "hunt", List.of(new QuestDef.Objective("arrest", o.id(), 1)), w * 1.3, Map.of("marine", 40, "civilian", 15), List.of(), req, false, args, "marine");
                quest(id + ".bounty_hunter", "hunt", List.of(new QuestDef.Objective("arrest", o.id(), 1)), w * 1.6, Map.of("bounty_hunter", 50), List.of(), req, false, args, "bounty_hunter");
                for (String v : List.of(id, id + ".marine", id + ".bounty_hunter")) {
                    qs.put(v, List.of(new SiteRef(-1, guards.key(), 2)));
                    gifts.put(v, new StartGift("poster", o.name(), StoryContext.islandName(y), hide.x(), hide.z(), bounty, o.id()));
                }
            }
            // ---- a beast nest on this island, or a bandit camp where no beasts live
            WildBeast.Kind kind = c.beast();
            if (kind != null) {
                Site nest = site("nest", isl, c.wild("job_nest"), SiteKind.BEAST, kind.id(), null);
                String id = id("nest", -1);
                int n = 3 + t / 2;
                quest(id, "nest", List.of(new QuestDef.Objective("slay", kind.id(), n)), 1.0, Map.of("island/" + isl.id, 15), List.of(StoryBuilder.mod("meat_on_bone", 4)),
                        List.of(), true, new Object[]{Component.translatable("entity.grandline." + kind.id()), c.islandName(), n}, null);
                qs.put(id, List.of(new SiteRef(0, nest.key(), n)));
            } else {
                Site camp = site("camp", isl, c.wild("job_camp"), SiteKind.ROLE, "bandit", null);
                String id = id("camp", -1);
                int n = 4 + t / 2;
                quest(id, "camp", List.of(new QuestDef.Objective("kill", "bandit", n)), 1.0, Map.of("island/" + isl.id, 15, "civilian", 5), List.of(), List.of(), true,
                        new Object[]{c.islandName(), n}, null);
                qs.put(id, List.of(new SiteRef(0, camp.key(), n)));
            }
            // ---- salvage: a wreck off this island or a neighbour
            Plot wreck = c.secret(BuildingType.SHIPWRECK);
            Island wy = isl;
            if (wreck == null)
                for (Island y : near) {
                    Plot p = y.settlement().secrets.stream().filter(s -> s.type() == BuildingType.SHIPWRECK).findFirst().orElse(null);
                    if (p != null) {
                        wreck = p;
                        wy = y;
                        break;
                    }
                }
            if (wreck != null) {
                Site ws = site("wreck", wy, new Spot(wreck.centerX(), wreck.centerZ(), wreck.y()), SiteKind.REACH, "", null);
                String id = id("wreck", -1);
                quest(id, "wreck", List.of(new QuestDef.Objective("reach", ws.key(), 1)), 1.1, Map.of(), List.of(StoryBuilder.vanilla("gold_nugget", 4 + t)), List.of(), true,
                        new Object[]{PeopleNames.ship(c.r), StoryContext.islandName(wy)}, null);
                qs.put(id, List.of(new SiteRef(0, ws.key(), 1)));
            }
            // ---- odd jobs for the town: fish, hot meals, timber
            int fish = 3 + t;
            quest(id("fish", -1), "fish", List.of(new QuestDef.Objective("collect", "#minecraft:fishes", fish)), 0.6, Map.of("island/" + isl.id, 5), List.of(), List.of(), true,
                    new Object[]{c.islandName(), fish}, null);
            String meal = switch (c.r.nextInt(3)) {
                case 0 -> "minecraft:cooked_cod";
                case 1 -> "minecraft:cooked_salmon";
                default -> "minecraft:bread";
            };
            int meals = 4 + t / 2;
            quest(id("cook", -1), "cook", List.of(new QuestDef.Objective("collect", meal, meals)), 0.6, Map.of("island/" + isl.id, 5), List.of(), List.of(), true,
                    new Object[]{c.islandName(), meals, StoryTemplate.itemName(meal)}, null);
            int logs = 8 + 2 * t;
            quest(id("supply", -1), "supply", List.of(new QuestDef.Objective("collect", "#minecraft:logs", logs)), 0.7, Map.of("island/" + isl.id, 5), List.of(), List.of(), true,
                    new Object[]{c.islandName(), logs}, null);
            SeaJobs.generate(this, near); // sea battles: a named pirate ship to sink, a merchantman to convoy
        }
    }

    // ================================================================== install

    static void installed(Generated g) {
        GIFTS.clear();
        GIFTS.putAll(g.gifts());
    }

    static void uninstalled() {
        GIFTS.clear();
        READING.clear();
    }

    /** What job-board people say. */
    static Component greeting(Person p) {
        String kind = p.slot().startsWith("outlaw") ? "outlaw" : p.slot();
        return Component.translatable("job.grandline.greet." + kind, p.name());
    }

    // ================================================================== reading a board

    /** Jobs this board offers to the character now: the classic board jobs and this island's generated ones. */
    public static List<QuestDef> offers(Island isl, CharacterData d) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef q : QuestRegistry.offeredBy(GIVER, isl.id, isl.starterFor != null)) if (QuestRegistry.canOffer(d, q)) out.add(q);
        return out;
    }

    /** The bounty-board marker within reach of a clicked block on this island, or null. */
    @Nullable
    public static BlockPos boardAt(Island isl, BlockPos clicked) {
        for (Plot p : isl.settlement().plots) {
            if (!p.contains(clicked.getX(), clicked.getZ(), 3)) continue;
            for (MarkerCollector.Marker m : MarkerCollector.markers(isl, p))
                if (m.kind().equals("bounty_board") && m.pos().distSqr(clicked) <= 9) return m.pos();
        }
        return null;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getHand() != InteractionHand.MAIN_HAND || sp.isShiftKeyDown()) return;
        if (!(sp.level() instanceof ServerLevel l)) return;
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null) return;
        BlockPos pos = e.getPos();
        BlockState st = l.getBlockState(pos);
        Block b = st.getBlock();
        if (st.hasBlockEntity() || b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock
                || b instanceof LeverBlock || b instanceof BedBlock || b instanceof CraftingTableBlock) return;
        for (Island isl : plan.around(pos.getX(), pos.getZ())) {
            if (!isl.settlement().bounds(pos.getX(), pos.getZ())) continue;
            BlockPos board = boardAt(isl, pos);
            if (board == null) continue;
            open(sp, isl, board);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
    }

    /** Show a board's notices (the dialogue screen, with the board instead of a person). */
    public static void open(ServerPlayer sp, Island isl, BlockPos board) {
        IslandStories.ensure(sp.server);
        CharacterData d = Characters.get(sp);
        CompoundTag t = new CompoundTag();
        t.putInt("npc", BOARD_ID);
        t.putString("name", Component.translatable("grandline.board.name", StoryContext.islandName(isl)).getString());
        t.putString("role", GIVER);
        t.putString("greeting", "dialogue.grandline.board");
        ListTag opts = new ListTag();
        for (QuestDef q : offers(isl, d)) opts.add(StringTag.valueOf("quest:" + q.id()));
        if (opts.isEmpty()) t.putString("greeting", "dialogue.grandline.board.empty");
        opts.add(StringTag.valueOf("leave"));
        t.put("options", opts);
        com.grandline.dialogue.Dialogues.labelGenerated(sp, t, opts);
        READING.put(sp.getUUID(), board);
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("dialogue", t));
    }

    /** A notice was picked: only from the board the player is reading, and only jobs that board offers. */
    public static void choose(ServerPlayer sp, String option) {
        BlockPos board = READING.get(sp.getUUID());
        if (board == null || !option.startsWith("quest:") || sp.blockPosition().distSqr(board) > 10 * 10) return;
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        Island isl = plan == null ? null : plan.islandAt(board.getX(), board.getZ());
        if (isl == null) return;
        String id = option.substring(6);
        for (QuestDef q : offers(isl, Characters.get(sp)))
            if (q.id().equals(id)) {
                Quests.start(sp, id);
                return;
            }
    }

    // ================================================================== starting a job

    static void onStarted(ServerPlayer sp, QuestDef q) {
        StartGift g = GIFTS.get(q.id());
        if (g == null) return;
        if (SeaJobs.onStarted(sp, q, g)) return; // escorts, named pirate ships, convoys
        ItemStack stack;
        if (g.kind().equals("poster")) {
            stack = new ItemStack(com.grandline.registry.ModItems.WANTED_POSTER.get());
            CompoundTag t = new CompoundTag();
            t.putString("name", g.name());
            t.putLong("bounty", g.bounty());
            t.putString("faction", "pirate");
            t.putBoolean("alive", true);
            t.putString("where", g.where().getString());
            t.putInt("x", g.x());
            t.putInt("z", g.z());
            t.putString("target", g.target());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        } else {
            stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("job.grandline.letter_for", g.name()).withStyle(ChatFormatting.GOLD));
        }
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        sp.sendSystemMessage(Component.translatable("job.grandline.where", g.where(), g.x(), g.z()).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    public static StartGift gift(String questId) {
        return GIFTS.get(questId);
    }

    private JobBoard() {}
}
