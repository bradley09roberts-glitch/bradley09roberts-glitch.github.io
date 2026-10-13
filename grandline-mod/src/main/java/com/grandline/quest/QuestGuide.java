package com.grandline.quest;

import com.grandline.GrandLine;
import com.grandline.navigation.Ferries;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.npc.MarkerCollector;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.story.IslandStories;
import com.grandline.quest.story.JobBoard;
import com.grandline.quest.story.StoryModel;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Where to go next: the tracked quest's current objective resolved to a place in the world, and a route there that
 * the minimap and the sea chart draw as a trail (GTA style). On an island the route follows the town's streets; to
 * another island it runs to this island's pier, straight across the sea to the destination's pier and up its streets.
 * Sent to the player every two seconds and whenever a quest starts or the tracked quest changes. Also builds the
 * quest log screen's data.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class QuestGuide {
    /** A place to go: block x/z, the dimension it is in, what it is, and the island it belongs to (if any). */
    public record Target(int x, int z, String dim, Component label, @Nullable Island island, boolean area) {
        public Target(int x, int z, String dim, Component label, @Nullable Island island) {
            this(x, z, dim, label, island, false);
        }
    }

    private static final String OVERWORLD = "minecraft:overworld";

    private QuestGuide() {}

    // ------------------------------------------------------------------ sending

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 40 != 13) return;
        for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) send(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) send(sp);
    }

    public static void send(ServerPlayer sp) {
        if (sp instanceof FakePlayer || sp.connection == null) return;
        try {
            GLNetwork.toPlayer(sp, new GLNetwork.Guidance(guidance(sp)));
        } catch (RuntimeException ex) {
            GrandLine.LOGGER.warn("Quest guide for {} failed", sp.getName().getString(), ex);
        }
    }

    /** Tracks a quest (a newly started one, or the player's pick in the quest log) and sends the new guidance. */
    public static void track(ServerPlayer sp, String questId) {
        CharacterData d = Characters.get(sp);
        if (!d.questProgress.containsKey(questId)) return;
        d.trackedQuest = questId;
        CharacterManager.markDirty(sp);
        send(sp);
    }

    /** The tracked quest if it is still active; otherwise another active quest (which then becomes tracked), or null. */
    @Nullable
    public static String tracked(CharacterData d) {
        if (!d.trackedQuest.isEmpty() && d.questProgress.containsKey(d.trackedQuest)) return d.trackedQuest;
        if (d.questProgress.isEmpty()) return null;
        String pick = new TreeSet<>(d.questProgress.keySet()).first();
        d.trackedQuest = pick;
        return pick;
    }

    // ------------------------------------------------------------------ guidance

    /** What the HUD needs: the tracked quest, its current objective, the target and the route ({} = nothing tracked). */
    public static CompoundTag guidance(ServerPlayer sp) {
        CompoundTag t = new CompoundTag();
        CharacterData d = Characters.get(sp);
        String id = tracked(d);
        QuestDef q = id == null ? null : QuestRegistry.get(id);
        if (q == null) return t;
        CompoundTag prog = d.questProgress.get(id);
        int oi = currentObjective(q, prog);
        t.putString("quest", id);
        t.putString("title", json(sp, QuestRegistry.title(q)));
        if (oi >= 0) {
            QuestDef.Objective o = q.objectives().get(oi);
            MutableComponent line = QuestText.objective(sp.server, o);
            if (o.count() > 1) line.append(" (" + Math.min(o.count(), prog.getInt("o" + oi)) + "/" + o.count() + ")");
            t.putString("obj", json(sp, line));
            Target tg = target(sp, d, q, oi);
            if (tg != null) {
                t.putBoolean("has", true);
                t.putInt("tx", tg.x());
                t.putInt("tz", tg.z());
                t.putString("dim", tg.dim());
                t.putString("label", json(sp, tg.label()));
                if (tg.dim().equals(sp.level().dimension().location().toString())) t.putIntArray("route", route(sp, tg));
            } else {
                t.putString("hint", json(sp, hint(o)));
            }
        }
        return t;
    }

    static int currentObjective(QuestDef q, CompoundTag prog) {
        // a lesson or a decision comes before the rest of its step (learn to fight, then fight; decide, then act)
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            if ((o.type().equals("lesson") || o.type().equals("choice")) && (prog == null || prog.getInt("o" + i) < o.count())) return i;
        }
        for (int i = 0; i < q.objectives().size(); i++)
            if (prog == null || prog.getInt("o" + i) < q.objectives().get(i).count()) return i;
        return q.objectives().isEmpty() ? -1 : q.objectives().size() - 1;
    }

    /** For objectives with no single place: what to do instead. */
    static Component hint(QuestDef.Objective o) {
        return Component.translatable("grandline.guide.hint." + switch (o.type()) {
            case "collect", "fish" -> "gather";
            case "sea_battle", "capture", "sink", "convoy", "escort" -> "sea";
            case "fruit_kill" -> "fight";
            default -> "explore";
        });
    }

    // ------------------------------------------------------------------ targets

    /** Where the objective is done: a story place, a job's destination, a person, an island, a building or the wilds. */
    @Nullable
    public static Target target(ServerPlayer sp, CharacterData d, QuestDef q, int oi) {
        MinecraftServer server = sp.server;
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return null;
        QuestDef.Objective o = q.objectives().get(oi);
        // a place the quest's story binds to this objective (camps, nests, wrecks, meeting points)
        for (StoryModel.SiteRef ref : IslandStories.sitesOf(q.id())) {
            if (ref.objective() != oi) continue;
            StoryModel.Site site = IslandStories.site(ref.site());
            if (site != null) return new Target(site.x(), site.z(), OVERWORLD, site.label(), plan.islandAt(site.x(), site.z()));
        }
        // a job's destination (deliveries, escorts, convoys, a pirate ship's last-seen point)
        JobBoard.StartGift gift = JobBoard.gift(q.id());
        if (gift != null && (gift.x() != 0 || gift.z() != 0) && !o.type().equals("kill"))
            return new Target(gift.x(), gift.z(), OVERWORLD, gift.where() != null ? gift.where() : Component.literal(gift.name()), plan.islandAt(gift.x(), gift.z()));
        Island home = questIsland(sp, plan, q);
        return switch (o.type()) {
            case "talk", "defeat", "arrest", "kill" -> person(sp, plan, home, o.target(), o.type().equals("kill"));
            // the combat lesson: the teacher who gave it (elders and masters offer a sparring bout)
            case "lesson" -> q.giverRole().startsWith("notable:") ? person(sp, plan, home, q.giverRole().substring(8), false) : null;
            // a decision: whoever puts it to the player
            case "choice" -> {
                var dec = com.grandline.quest.arc.Arcs.decision(o.target());
                yield dec == null ? null : person(sp, plan, home, dec.presenter(), false);
            }
            case "discover" -> island(sp, plan, d, o.target());
            case "ship" -> {
                Target yard = building(sp, plan, home, BuildingType.SHIPWRIGHT, Component.translatable("grandline.guide.shipwright"));
                yield yard != null ? yard : person(sp, plan, home, "shipwright", false);
            }
            default -> null;
        };
    }

    /** The island a quest belongs to: its own if it names one, else where the player is (or the nearest island). */
    @Nullable
    static Island questIsland(ServerPlayer sp, WorldPlan plan, QuestDef q) {
        Island i = q.island() == null || q.island().isEmpty() || q.island().startsWith("*") ? null : plan.byId(q.island());
        if (i != null) return i;
        i = plan.islandAt(sp.getBlockX(), sp.getBlockZ());
        if (i != null) return i;
        Island best = null;
        double bd = Double.MAX_VALUE;
        for (Island c : plan.islands) {
            double dd = c.distance(sp.getBlockX(), sp.getBlockZ());
            if (dd < bd) {
                bd = dd;
                best = c;
            }
        }
        return best;
    }

    /** A notable or a role: the live NPC if one is near, else where they live or work, else (for foes) the island's wilds. */
    @Nullable
    public static Target person(ServerPlayer sp, WorldPlan plan, @Nullable Island home, String who, boolean foe) {
        String dim = sp.level().dimension().location().toString();
        var notable = NotableCharacters.get(who);
        Component name = notable != null ? Component.translatable(notable.nameKey()) : Component.translatable("role.grandline." + who);
        // the live one nearest the player
        GLNpc best = null;
        double bd = Double.MAX_VALUE;
        for (GLNpc n : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(foe ? 160 : 112),
                n -> n.isAlive() && !n.flag(GLNpc.FLAG_ARRESTED) && (who.equals(n.notableId()) || notable == null && who.equals(n.role())))) {
            double dd = n.distanceToSqr(sp);
            if (dd < bd) {
                bd = dd;
                best = n;
            }
        }
        if (best != null) return new Target(best.getBlockX(), best.getBlockZ(), dim, name, plan.islandAt(best.getBlockX(), best.getBlockZ()));
        if (notable != null) {
            BlockPos h = NotableSpawner.homeOf(sp.server, notable);
            return h == null ? null : new Target(h.getX(), h.getZ(), OVERWORLD, name, plan.byId(notable.island()));
        }
        if (home == null) return null;
        // where the role lives or works on the island (from the buildings' own NPC markers)
        BlockPos at = null;
        double ad = Double.MAX_VALUE;
        for (Plot p : home.settlement().plots)
            for (MarkerCollector.Marker m : MarkerCollector.markers(home, p)) {
                if (!m.kind().equals("npc") || !who.equals(m.data().getString("role"))) continue;
                double dd = m.pos().distSqr(sp.blockPosition());
                if (dd < ad) {
                    ad = dd;
                    at = m.pos();
                }
            }
        if (at == null && who.equals("shipwright") && home.settlement().hasDockShipwright)
            at = new BlockPos(home.settlement().dockShipwrightX, 64, home.settlement().dockShipwrightZ);
        if (at != null) return new Target(at.getX(), at.getZ(), OVERWORLD, name, home);
        if (!foe) return null;
        int[] w = wilds(home);
        return new Target(w[0], w[1], OVERWORLD, Component.translatable("grandline.guide.wilds", name), home, true);
    }

    /** A spot in the island's wilds, away from the town (where wild encounters come from). */
    public static int[] wilds(Island isl) {
        Settlement s = isl.settlement();
        double best = -1;
        int[] out = {isl.x, isl.z};
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            for (double f : new double[]{0.6, 0.45, 0.3}) {
                int x = (int) (isl.x + Math.cos(a) * isl.radius * f), z = (int) (isl.z + Math.sin(a) * isl.radius * f);
                if (isl.t(x, z) > 0.8 || s.bounds(x, z)) continue;
                double away = s.hasCenter ? Math.hypot(x - s.centerX, z - s.centerZ) : isl.radius * f;
                if (away > best) {
                    best = away;
                    out = new int[]{x, z};
                }
                break;
            }
        }
        return out;
    }

    /** An island to sail to: by id, or the nearest undiscovered one ("any", "any_canon", "any_new_world"). */
    @Nullable
    public static Target island(ServerPlayer sp, WorldPlan plan, CharacterData d, String id) {
        Island isl = id.startsWith("any") ? null : plan.byId(id);
        if (id.startsWith("any")) {
            double bd = Double.MAX_VALUE;
            for (Island c : plan.islands) {
                if (d.discoveredIslands.contains(c.id) || c.shape == com.grandline.world.IslandShape.SEA) continue;
                if (id.equals("any_canon") && !c.canon || id.equals("any_new_world") && c.zone != SeaZone.NEW_WORLD) continue;
                double dd = c.distance(sp.getBlockX(), sp.getBlockZ());
                if (dd < bd) {
                    bd = dd;
                    isl = c;
                }
            }
        }
        if (isl == null) return null;
        Component name = isl.name == null || isl.name.isEmpty() ? Component.translatable(isl.translationKey()) : Component.literal(isl.name);
        Settlement s = isl.settlement();
        int x = s.hasCenter ? s.centerX : isl.x, z = s.hasCenter ? s.centerZ : isl.z;
        return new Target(x, z, OVERWORLD, name, isl);
    }

    /** The nearest building of a type on the island (its middle). */
    @Nullable
    static Target building(ServerPlayer sp, WorldPlan plan, @Nullable Island home, BuildingType type, Component label) {
        if (home == null) return null;
        Plot best = null;
        double bd = Double.MAX_VALUE;
        for (Plot p : home.settlement().plots) {
            if (p.type() != type) continue;
            double dd = Math.hypot(p.x() + p.sizeX() / 2.0 - sp.getX(), p.z() + p.sizeZ() / 2.0 - sp.getZ());
            if (dd < bd) {
                bd = dd;
                best = p;
            }
        }
        return best == null ? null : new Target(best.x() + best.sizeX() / 2, best.z() + best.sizeZ() / 2, OVERWORLD, label, home);
    }

    // ------------------------------------------------------------------ routes

    /** Route from the player to the target as x,z pairs: streets on islands, pier to pier across the sea. */
    public static int[] route(ServerPlayer sp, Target tg) {
        WorldPlan plan = WorldPlan.of(sp.server);
        int px = sp.getBlockX(), pz = sp.getBlockZ();
        List<int[]> pts = new ArrayList<>();
        Island here = plan == null || !sp.level().dimension().location().toString().equals(OVERWORLD) ? null : plan.islandAt(px, pz);
        Island there = tg.island() != null ? tg.island() : plan == null ? null : plan.islandAt(tg.x(), tg.z());
        if (here != null && here == there) {
            pts.addAll(streetPath(here.settlement(), px, pz, tg.x(), tg.z()));
        } else {
            if (here != null && here.settlement().hasDock) {
                Settlement s = here.settlement();
                pts.addAll(streetPath(s, px, pz, s.dockX, s.dockZ));
                pts.add(new int[]{s.dockTipX(), s.dockTipZ()});
            } else {
                pts.add(new int[]{px, pz});
            }
            if (there != null && there.settlement().hasDock) {
                Settlement s = there.settlement();
                Vec3 berth = Ferries.dockOf(there);
                pts.add(new int[]{(int) berth.x, (int) berth.z});
                pts.add(new int[]{s.dockTipX(), s.dockTipZ()});
                pts.addAll(streetPath(s, s.dockX, s.dockZ, tg.x(), tg.z()));
            } else {
                pts.add(new int[]{tg.x(), tg.z()});
            }
        }
        // flatten, dropping repeats; at most 300 points
        int[] out = new int[Math.min(pts.size(), 300) * 2];
        int n = 0, lx = Integer.MIN_VALUE, lz = Integer.MIN_VALUE;
        for (int[] p : pts) {
            if (n >= out.length) break;
            if (p[0] == lx && p[1] == lz) continue;
            out[n++] = p[0];
            out[n++] = p[1];
            lx = p[0];
            lz = p[1];
        }
        return Arrays.copyOf(out, n);
    }

    /** Shortest way along a town's streets (from, the street points it uses, to); a straight line when there are none. */
    static List<int[]> streetPath(Settlement s, int fx, int fz, int tx, int tz) {
        List<int[]> roads = s.roads;
        List<int[]> straight = List.of(new int[]{fx, fz}, new int[]{tx, tz});
        if (roads.isEmpty()) return straight;
        Graph g = GRAPHS.computeIfAbsent(s, QuestGuide::graph);
        Snap a = snap(roads, fx, fz), b = snap(roads, tx, tz);
        if (a == null || b == null || a.dist > 60 || b.dist > 60) return straight;
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{fx, fz});
        out.add(new int[]{a.x, a.z});
        if (a.seg != b.seg) {
            // Dijkstra from both ends of a's segment to both ends of b's segment
            int[] ra = roads.get(a.seg), rb = roads.get(b.seg);
            int a1 = g.node(ra[0], ra[1]), a2 = g.node(ra[2], ra[3]), b1 = g.node(rb[0], rb[1]), b2 = g.node(rb[2], rb[3]);
            double[] dist = new double[g.nodes.size()];
            int[] prev = new int[g.nodes.size()];
            Arrays.fill(dist, Double.MAX_VALUE);
            Arrays.fill(prev, -1);
            PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e[1]));
            dist[a1] = Math.abs(a.x - ra[0]) + Math.abs(a.z - ra[1]);
            dist[a2] = Math.min(dist[a2], Math.abs(a.x - ra[2]) + Math.abs(a.z - ra[3]));
            pq.add(new double[]{a1, dist[a1]});
            pq.add(new double[]{a2, dist[a2]});
            while (!pq.isEmpty()) {
                double[] e = pq.poll();
                int u = (int) e[0];
                if (e[1] > dist[u]) continue;
                for (int[] edge : g.adj.get(u)) {
                    double nd = dist[u] + edge[1];
                    if (nd < dist[edge[0]]) {
                        dist[edge[0]] = nd;
                        prev[edge[0]] = u;
                        pq.add(new double[]{edge[0], nd});
                    }
                }
            }
            double e1 = dist[b1] + Math.abs(b.x - rb[0]) + Math.abs(b.z - rb[1]);
            double e2 = dist[b2] + Math.abs(b.x - rb[2]) + Math.abs(b.z - rb[3]);
            int end = e1 <= e2 ? b1 : b2;
            if (dist[end] < Double.MAX_VALUE) {
                Deque<int[]> path = new ArrayDeque<>();
                for (int u = end; u >= 0; u = prev[u]) path.addFirst(g.nodes.get(u));
                out.addAll(path);
            }
        }
        out.add(new int[]{b.x, b.z});
        out.add(new int[]{tx, tz});
        return out;
    }

    private record Snap(int seg, int x, int z, double dist) {}

    @Nullable
    private static Snap snap(List<int[]> roads, int x, int z) {
        Snap best = null;
        for (int i = 0; i < roads.size(); i++) {
            int[] r = roads.get(i);
            int cx = Math.max(Math.min(r[0], r[2]), Math.min(Math.max(r[0], r[2]), x));
            int cz = Math.max(Math.min(r[1], r[3]), Math.min(Math.max(r[1], r[3]), z));
            double dd = Math.hypot(x - cx, z - cz);
            if (best == null || dd < best.dist) best = new Snap(i, cx, cz, dd);
        }
        return best;
    }

    private static final Map<Settlement, Graph> GRAPHS = Collections.synchronizedMap(new WeakHashMap<>());

    private record Graph(List<int[]> nodes, Map<Long, Integer> index, List<List<int[]>> adj) {
        int node(int x, int z) {
            return index.get(((long) x << 32) ^ (z & 0xffffffffL));
        }
    }

    /** Street graph: segment ends are nodes, segments are edges (Manhattan length). */
    private static Graph graph(Settlement s) {
        Graph g = new Graph(new ArrayList<>(), new HashMap<>(), new ArrayList<>());
        for (int[] r : s.roads) {
            int a = add(g, r[0], r[1]), b = add(g, r[2], r[3]);
            int len = Math.abs(r[0] - r[2]) + Math.abs(r[1] - r[3]);
            g.adj.get(a).add(new int[]{b, len});
            g.adj.get(b).add(new int[]{a, len});
        }
        return g;
    }

    private static int add(Graph g, int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xffffffffL);
        Integer i = g.index.get(k);
        if (i != null) return i;
        g.nodes.add(new int[]{x, z});
        g.adj.add(new ArrayList<>());
        g.index.put(k, g.nodes.size() - 1);
        return g.nodes.size() - 1;
    }

    // ------------------------------------------------------------------ quest log

    /**
     * Data for the quest log (the journal): every active quest with its text, objectives, rewards and where to go,
     * grouped as the story (the saga's chapters, recruitment and crew quests), side stories, and jobs (generated island
     * stories, notice-board and sea jobs, anything repeatable). Story steps also carry their chapter, who is who, why it
     * matters, the next step, how dangerous its foes are for this player and a recap of the chapter so far
     * ({@link com.grandline.quest.arc.ArcJournal}).
     */
    public static CompoundTag log(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        CompoundTag t = new CompoundTag();
        String tracked = tracked(d);
        t.putString("tracked", tracked == null ? "" : tracked);
        t.putInt("completed", d.completedQuests.size());
        ListTag list = new ListTag();
        List<String> ids = new ArrayList<>(new TreeSet<>(d.questProgress.keySet()));
        Map<String, Integer> rank = new HashMap<>();
        for (String id : ids) {
            QuestDef q = QuestRegistry.get(id);
            rank.put(id, q == null ? 3 : switch (com.grandline.quest.arc.ArcJournal.group(q)) {
                case "story" -> 0;
                case "side" -> 1;
                default -> 2;
            });
        }
        ids.sort(Comparator.comparingInt(rank::get)); // stable: by id within each group
        for (String id : ids) {
            QuestDef q = QuestRegistry.get(id);
            if (q == null) continue;
            CompoundTag prog = d.questProgress.get(id);
            CompoundTag e = new CompoundTag();
            e.putString("id", id);
            e.putString("group", com.grandline.quest.arc.ArcJournal.group(q));
            e.putString("title", json(sp, QuestRegistry.title(q)));
            Component desc = QuestText.description(q);
            if (desc != null) e.putString("desc", json(sp, desc));
            ListTag objs = new ListTag();
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestDef.Objective o = q.objectives().get(i);
                CompoundTag ot = new CompoundTag();
                ot.putString("text", json(sp, QuestText.objective(sp.server, o)));
                ot.putInt("have", Math.min(o.count(), prog.getInt("o" + i)));
                ot.putInt("count", o.count());
                objs.add(ot);
            }
            e.put("objectives", objs);
            e.putInt("xp", q.xp());
            e.putLong("beli", q.beli());
            ListTag items = new ListTag();
            for (var item : q.rewardItems()) {
                var st = item.get();
                items.add(StringTag.valueOf(json(sp, Component.literal(st.getCount() + "× ").append(st.getHoverName()))));
            }
            e.put("items", items);
            int oi = currentObjective(q, prog);
            if (oi >= 0) {
                QuestDef.Objective o = q.objectives().get(oi);
                MutableComponent next = QuestText.objective(sp.server, o);
                if (o.count() > 1) next.append(" (" + Math.min(o.count(), prog.getInt("o" + oi)) + "/" + o.count() + ")");
                e.putString("next", json(sp, next));
                Target tg = target(sp, d, q, oi);
                if (tg != null) {
                    e.putString("where", json(sp, tg.label()));
                    e.putBoolean("area", tg.area());
                    if (tg.dim().equals(sp.level().dimension().location().toString()))
                        e.putInt("dist", (int) Math.hypot(tg.x() - sp.getX(), tg.z() - sp.getZ()));
                } else {
                    e.putString("where", json(sp, hint(o)));
                    e.putBoolean("area", true);
                }
            }
            com.grandline.quest.arc.ArcJournal.fill(sp, d, q, prog, e, c -> json(sp, c));
            list.add(e);
        }
        t.put("quests", list);
        return t;
    }

    public static void openLog(ServerPlayer sp) {
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("quests", log(sp)));
    }

    static String json(ServerPlayer sp, Component c) {
        return Component.Serializer.toJson(c, sp.registryAccess());
    }

    @SuppressWarnings("unused")
    private static ResourceLocation itemId(String id) {
        return BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }
}
