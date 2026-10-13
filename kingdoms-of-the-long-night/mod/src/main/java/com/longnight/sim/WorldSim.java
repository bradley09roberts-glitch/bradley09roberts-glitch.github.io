package com.longnight.sim;

import com.longnight.LongNight;
import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.content.NameSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The whole living world: every person, settlement, realm, caravan and piece of news. Saved with
 * the overworld. Runs a daily update for all settlements (cheap, chunk-independent); detailed
 * behaviour near players is handled by bodies (CitizenEntity) that read and write these records.
 */
public class WorldSim extends SavedData {
    public static final String DATA_NAME = "longnight_world";
    public static final int FORMAT = 1;

    public final Map<UUID, Person> people = new LinkedHashMap<>();
    public final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    public final Map<UUID, Faction> factions = new LinkedHashMap<>();
    public final List<NewsItem> news = new ArrayList<>();
    public final List<Caravan> caravans = new ArrayList<>();
    public final List<War> wars = new ArrayList<>();
    public final List<String> history = new ArrayList<>();
    public final Map<UUID, Long> playerLastSeenDay = new HashMap<>();
    public final List<Claim> claims = new ArrayList<>();
    /** active ward stones: position -> last day of protection */
    public final Map<Long, Long> wards = new HashMap<>();
    /** what each player has heard of: player -> settlements */
    public final Map<UUID, java.util.Set<UUID>> knownPlaces = new HashMap<>();
    /** good unused sites found when the world was generated, for future camps */
    public final List<BlockPos> spareSites = new ArrayList<>();
    public long nextNewsId = 1;
    public long lastDay = Long.MIN_VALUE;
    public boolean generated;
    /** The Long Night: 0 = dormant ... 5 = the Night has fallen */
    public int nightStage;
    public double nightPressure;
    public boolean dragonSlain;

    // transient
    public SimRules rules = new SimRules();
    public WorldHooks hooks = WorldHooks.NONE;
    public final Random rng = new Random();
    public final Map<UUID, Integer> embodiedTicksToday = new HashMap<>();

    public static final class Caravan {
        public UUID id = UUID.randomUUID();
        public UUID from, to;
        public long departDay, arriveDay;
        public final Map<Commodity, Double> goods = new java.util.EnumMap<>(Commodity.class);
        public int coins;
        public final List<Long> newsCarried = new ArrayList<>();
        public UUID leader;
        public boolean returning;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            Nbt.putUUID(t, "from", from);
            Nbt.putUUID(t, "to", to);
            t.putLong("dep", departDay);
            t.putLong("arr", arriveDay);
            CompoundTag g = new CompoundTag();
            goods.forEach((k, v) -> g.putDouble(k.id(), v));
            t.put("goods", g);
            t.putInt("coins", coins);
            t.putLongArray("news", newsCarried.stream().mapToLong(Long::longValue).toArray());
            Nbt.putUUID(t, "leader", leader);
            t.putBoolean("ret", returning);
            return t;
        }

        static Caravan load(CompoundTag t) {
            Caravan c = new Caravan();
            c.id = t.getUUID("id");
            c.from = Nbt.getUUID(t, "from");
            c.to = Nbt.getUUID(t, "to");
            c.departDay = t.getLong("dep");
            c.arriveDay = t.getLong("arr");
            CompoundTag g = t.getCompound("goods");
            for (String k : g.getAllKeys()) {
                Commodity cm = Commodity.byId(k);
                if (cm != null) c.goods.put(cm, g.getDouble(k));
            }
            c.coins = t.getInt("coins");
            for (long n : t.getLongArray("news")) c.newsCarried.add(n);
            c.leader = Nbt.getUUID(t, "leader");
            c.returning = t.getBoolean("ret");
            return c;
        }
    }

    /** A player's protected land. Settlements never build, fell trees or clear terrain inside a claim. */
    public record Claim(UUID owner, String ownerName, BlockPos center, int radius) {
        public boolean contains(BlockPos p, int margin) {
            long dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
            long r = radius + margin;
            return dx * dx + dz * dz <= r * r;
        }
    }

    public boolean claimed(BlockPos p, int margin) {
        for (Claim c : claims) if (c.contains(p, margin)) return true;
        return false;
    }

    /** A road between two settlements; {@code done} records the chunks where it has been laid. */
    public static final class Road {
        public final UUID a, b;
        public final java.util.Set<Long> done = new java.util.HashSet<>();

        public Road(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }
    }

    public final List<Road> roads = new ArrayList<>();

    public static final class War {
        public UUID attacker, defender;
        public UUID target;          // settlement the attacker wants
        public String cause = "";
        public long startDay;
        public int score;            // positive favours the attacker
        public int battles;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            Nbt.putUUID(t, "a", attacker);
            Nbt.putUUID(t, "d", defender);
            Nbt.putUUID(t, "t", target);
            t.putString("cause", cause);
            t.putLong("start", startDay);
            t.putInt("score", score);
            t.putInt("battles", battles);
            return t;
        }

        static War load(CompoundTag t) {
            War w = new War();
            w.attacker = Nbt.getUUID(t, "a");
            w.defender = Nbt.getUUID(t, "d");
            w.target = Nbt.getUUID(t, "t");
            w.cause = t.getString("cause");
            w.startDay = t.getLong("start");
            w.score = t.getInt("score");
            w.battles = t.getInt("battles");
            return w;
        }
    }

    // ------------------------------------------------------------------ access

    public static SavedData.Factory<WorldSim> factory() {
        return new SavedData.Factory<>(WorldSim::new, WorldSim::load);
    }

    public static WorldSim get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        WorldSim sim = overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
        return sim;
    }

    public static long dayOf(ServerLevel overworld) {
        return Math.floorDiv(overworld.getDayTime(), 24000L);
    }

    public Person person(UUID id) {
        return id == null ? null : people.get(id);
    }

    public Settlement settlement(UUID id) {
        return id == null ? null : settlements.get(id);
    }

    public Faction faction(UUID id) {
        return id == null ? null : factions.get(id);
    }

    public List<Person> residents(Settlement s) {
        List<Person> out = new ArrayList<>(s.residents.size());
        for (UUID id : s.residents) {
            Person p = people.get(id);
            if (p != null && p.alive) out.add(p);
        }
        return out;
    }

    public List<Settlement> settlementsOf(Faction f) {
        List<Settlement> out = new ArrayList<>();
        for (Settlement s : settlements.values()) if (f.id.equals(s.faction) && s.kind == Settlement.Kind.SETTLEMENT) out.add(s);
        return out;
    }

    public Collection<Settlement> allSettlements() {
        return settlements.values();
    }

    public Settlement settlementAt(BlockPos pos, int margin) {
        Settlement best = null;
        double bestD = Double.MAX_VALUE;
        for (Settlement s : settlements.values()) {
            if (s.center == null) continue;
            double d = Math.sqrt(s.center.distSqr(new BlockPos(pos.getX(), s.center.getY(), pos.getZ())));
            if (d <= s.radius + margin && d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    public Settlement nearestSettlement(BlockPos pos, Settlement.Kind kind) {
        Settlement best = null;
        double bestD = Double.MAX_VALUE;
        for (Settlement s : settlements.values()) {
            if (s.center == null || (kind != null && s.kind != kind)) continue;
            double d = s.center.distSqr(pos);
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    public double ageOf(Person p, long day) {
        return p.ageYears(day, rules.daysPerYear);
    }

    public boolean isAdult(Person p, long day) {
        return ageOf(p, day) >= rules.adultAge;
    }

    public void learnOf(UUID player, Settlement s) {
        if (s != null && knownPlaces.computeIfAbsent(player, k -> new java.util.HashSet<>()).add(s.id)) setDirty();
    }

    public void log(long day, String text) {
        history.add("Day " + day + ": " + text);
        if (history.size() > 400) history.remove(0);
        setDirty();
    }

    // ------------------------------------------------------------------ people

    /** Creates a founding-generation person of the given culture. */
    public Person createPerson(String cultureId, Person.Sex sex, long birthDay, Random r) {
        Culture c = Content.culture(cultureId);
        NameSet names = Content.names(c.id);
        Person p = new Person(UUID.randomUUID());
        p.culture = c.id;
        p.sex = sex != null ? sex : (r.nextBoolean() ? Person.Sex.MALE : Person.Sex.FEMALE);
        p.given = names.given(p.sex, r);
        p.family = names.rootFamily(p, r);
        p.birthDay = birthDay;
        p.faith = pickFaith(c, r);
        randomiseTemperament(p, c, r);
        int o = r.nextInt(100);
        p.orientation = o < 86 ? Person.Orientation.STRAIGHT : o < 92 ? Person.Orientation.GAY : Person.Orientation.BI;
        p.coins = 5 + r.nextInt(40);
        people.put(p.id, p);
        setDirty();
        return p;
    }

    public Person createChild(Person mother, Person father, long day, Random r) {
        Person base = mother != null ? mother : father;
        Culture c = Content.culture(base.culture);
        Person p = new Person(UUID.randomUUID());
        // mixed families: the child takes one parent's culture
        p.culture = (father != null && mother != null && !father.culture.equals(mother.culture) && r.nextBoolean())
                ? father.culture : base.culture;
        c = Content.culture(p.culture);
        NameSet names = Content.names(p.culture);
        p.sex = r.nextBoolean() ? Person.Sex.MALE : Person.Sex.FEMALE;
        p.given = names.given(p.sex, r);
        p.family = names.inheritFamily(p, father, mother, r);
        p.birthDay = day;
        p.faith = r.nextFloat() < 0.85f ? base.faith : pickFaith(c, r);
        randomiseTemperament(p, c, r);
        // children resemble their parents a little
        if (father != null && mother != null) {
            p.bravery = mix(p.bravery, (father.bravery + mother.bravery) / 2, r);
            p.diligence = mix(p.diligence, (father.diligence + mother.diligence) / 2, r);
            p.kindness = mix(p.kindness, (father.kindness + mother.kindness) / 2, r);
            p.piety = mix(p.piety, (father.piety + mother.piety) / 2, r);
        }
        int o = r.nextInt(100);
        p.orientation = o < 86 ? Person.Orientation.STRAIGHT : o < 92 ? Person.Orientation.GAY : Person.Orientation.BI;
        p.mother = mother != null ? mother.id : null;
        p.father = father != null ? father.id : null;
        if (mother != null) mother.children.add(p.id);
        if (father != null) father.children.add(p.id);
        p.settlement = base.settlement;
        p.home = base.home;
        p.food = 1f;
        people.put(p.id, p);
        setDirty();
        return p;
    }

    private static float mix(float a, float b, Random r) {
        float t = 0.3f + r.nextFloat() * 0.4f;
        return a * (1 - t) + b * t;
    }

    private static String pickFaith(Culture c, Random r) {
        double total = 0;
        for (double w : c.faiths.values()) total += w;
        double x = r.nextDouble() * total;
        for (Map.Entry<String, Double> e : c.faiths.entrySet()) {
            x -= e.getValue();
            if (x <= 0) return e.getKey();
        }
        return c.faiths.isEmpty() ? "hearth" : c.faiths.keySet().iterator().next();
    }

    private static void randomiseTemperament(Person p, Culture c, Random r) {
        p.bravery = trait(c.trait("bravery"), r);
        p.diligence = trait(c.trait("diligence"), r);
        p.sociability = trait(c.trait("sociability"), r);
        p.ambition = trait(c.trait("ambition"), r);
        p.piety = trait(c.trait("piety"), r);
        p.kindness = trait(c.trait("kindness"), r);
        p.greed = trait(c.trait("greed"), r);
    }

    private static float trait(float mean, Random r) {
        return (float) Math.max(0.02, Math.min(0.98, mean + r.nextGaussian() * 0.2));
    }

    public void join(Person p, Settlement s) {
        if (p.settlement != null) {
            Settlement old = settlement(p.settlement);
            if (old != null) old.residents.remove(p.id);
            leaveBuildings(p, old);
        }
        p.settlement = s == null ? null : s.id;
        p.home = null;
        p.workplace = null;
        if (s != null && !s.residents.contains(p.id)) s.residents.add(p.id);
        hooks.personMoved(this, p);
        setDirty();
    }

    private void leaveBuildings(Person p, Settlement s) {
        if (s == null) return;
        for (Building b : s.buildings) {
            b.workers.remove(p.id);
            b.residents.remove(p.id);
        }
    }

    /** The single, idempotent way anyone dies. */
    public void kill(Person p, long day, String cause) {
        if (p == null || !p.alive) return;
        p.alive = false;
        p.deathDay = day;
        p.deathCause = cause;
        p.activity = "";
        p.blocked = "";
        Settlement s = settlement(p.settlement);
        if (s != null) {
            s.residents.remove(p.id);
            leaveBuildings(p, s);
        }
        // the living remember
        Person spouse = person(p.spouse);
        if (spouse != null && spouse.alive) {
            spouse.spouse = null;
            spouse.remember(day, "bereaved", p.id, "lost my " + (p.sex == Person.Sex.FEMALE ? "wife" : "husband") + ", " + p.given, -60);
        }
        for (UUID cid : p.children) {
            Person c = person(cid);
            if (c != null && c.alive) c.remember(day, "bereaved", p.id, "lost my " + (p.sex == Person.Sex.FEMALE ? "mother" : "father"), -45);
        }
        for (UUID pid : new UUID[]{p.mother, p.father}) {
            Person parent = person(pid);
            if (parent != null && parent.alive) parent.remember(day, "bereaved", p.id, "lost my child " + p.given, -70);
        }
        if (s != null) {
            for (Person r : residents(s)) {
                int op = r.opinionOf(p.id);
                if (op >= 40 && !p.id.equals(r.spouse)) r.remember(day, "grief", p.id, "mourned my friend " + p.given, -25);
                else if (op <= -50) r.remember(day, "relief", p.id, "outlived " + p.given + ", whom I despised", 5);
            }
        }
        // inheritance: coins pass to spouse, else children, else the settlement
        int coins = p.coins;
        p.coins = 0;
        List<Person> heirs = new ArrayList<>();
        if (spouse != null && spouse.alive) heirs.add(spouse);
        else for (UUID cid : p.children) {
            Person c = person(cid);
            if (c != null && c.alive) heirs.add(c);
        }
        if (heirs.isEmpty()) {
            if (s != null) s.treasury += coins;
        } else {
            for (Person h : heirs) h.coins += coins / heirs.size();
        }
        // offices
        for (Faction f : new ArrayList<>(factions.values())) {
            if (p.id.equals(f.ruler)) Politics.succession(this, f, day);
            if (p.id.equals(f.heir)) f.heir = null;
        }
        hooks.personDied(this, p, s);
        setDirty();
    }

    // ------------------------------------------------------------------ news

    /** Records news at its origin. vars are key/value pairs: "person", "Aldric", "settlement", "Ashford", ... */
    public NewsItem addNews(long day, String type, String text, Settlement origin, int importance, String... vars) {
        Map<String, String> v = new HashMap<>();
        for (int i = 0; i + 1 < vars.length; i += 2) v.put(vars[i], vars[i + 1]);
        if (origin != null) v.putIfAbsent("settlement", origin.name);
        NewsItem n = new NewsItem(nextNewsId++, day, type, text, origin == null ? null : origin.id, importance, v);
        news.add(n);
        if (origin != null) origin.knownNews.put(n.id(), day);
        while (news.size() > 600) {
            NewsItem old = news.remove(0);
            for (Settlement s : settlements.values()) s.knownNews.remove(old.id());
        }
        if (importance >= 3) log(day, text);
        setDirty();
        return n;
    }

    public NewsItem newsById(long id) {
        for (int i = news.size() - 1; i >= 0; i--) if (news.get(i).id() == id) return news.get(i);
        return null;
    }

    // ------------------------------------------------------------------ time

    /** Called every server tick. Runs daily updates when the (overworld) day changes. */
    public void tick(MinecraftServer server) {
        long day = dayOf(server.overworld());
        if (lastDay == Long.MIN_VALUE) {
            lastDay = day;
            setDirty();
            return;
        }
        if (day > lastDay) {
            advanceTo(day);
        } else if (day < lastDay) {
            // time was set backwards with a command: just re-anchor
            lastDay = day;
        }
    }

    public void advanceTo(long day) {
        long from = Math.max(lastDay + 1, day - rules.maxCatchUpDays + 1);
        for (long d = from; d <= day; d++) {
            try {
                daily(d);
            } catch (Exception e) {
                LongNight.LOG.error("Simulation error on day {}", d, e);
            }
        }
        lastDay = day;
        embodiedTicksToday.clear();
        setDirty();
    }

    /** One day of the world. Order matters: people eat what was produced yesterday. */
    public void daily(long day) {
        rng.setSeed(day * 0x9E3779B97F4A7C15L ^ people.size());
        Life.daily(this, day);
        Economy.daily(this, day);
        Planner.daily(this, day);
        Threats.daily(this, day);
        Politics.daily(this, day);
        NewsSystem.daily(this, day);
        Growth.daily(this, day);
        Contracts.daily(this, day);
        LongNightArc.daily(this, day);
    }

    /** fraction (0..1) of the current day during which a settlement had bodies */
    public double embodiedFraction(Settlement s) {
        return Math.min(1.0, embodiedTicksToday.getOrDefault(s.id, 0) / 24000.0);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider registries) {
        t.putInt("format", FORMAT);
        ListTag pl = new ListTag();
        for (Person p : people.values()) pl.add(p.save());
        t.put("people", pl);
        ListTag sl = new ListTag();
        for (Settlement s : settlements.values()) sl.add(s.save());
        t.put("settlements", sl);
        ListTag fl = new ListTag();
        for (Faction f : factions.values()) fl.add(f.save());
        t.put("factions", fl);
        ListTag nl = new ListTag();
        for (NewsItem n : news) nl.add(n.save());
        t.put("news", nl);
        ListTag cl = new ListTag();
        for (Caravan c : caravans) cl.add(c.save());
        t.put("caravans", cl);
        ListTag wl = new ListTag();
        for (War w : wars) wl.add(w.save());
        t.put("wars", wl);
        t.put("history", Nbt.stringList(history));
        t.putLong("nextNews", nextNewsId);
        t.putLong("lastDay", lastDay);
        t.putBoolean("generated", generated);
        t.putInt("nightStage", nightStage);
        t.putDouble("nightPressure", nightPressure);
        t.putBoolean("dragonSlain", dragonSlain);
        ListTag seen = new ListTag();
        playerLastSeenDay.forEach((k, v) -> {
            CompoundTag x = new CompoundTag();
            x.putUUID("p", k);
            x.putLong("d", v);
            seen.add(x);
        });
        t.put("seen", seen);
        ListTag cls = new ListTag();
        for (Claim c : claims) {
            CompoundTag x = new CompoundTag();
            x.putUUID("o", c.owner());
            x.putString("n", c.ownerName());
            Nbt.putPos(x, "c", c.center());
            x.putInt("r", c.radius());
            cls.add(x);
        }
        t.put("claims", cls);
        t.putLongArray("spare", spareSites.stream().mapToLong(BlockPos::asLong).toArray());
        t.putLongArray("wardPos", wards.keySet().stream().mapToLong(Long::longValue).toArray());
        t.putLongArray("wardDay", wards.keySet().stream().mapToLong(k -> wards.get(k)).toArray());
        ListTag known = new ListTag();
        knownPlaces.forEach((k, v) -> {
            CompoundTag x = new CompoundTag();
            x.putUUID("p", k);
            x.put("s", Nbt.uuidList(v));
            known.add(x);
        });
        t.put("known", known);
        ListTag rl = new ListTag();
        for (Road r : roads) {
            CompoundTag x = new CompoundTag();
            x.putUUID("a", r.a);
            x.putUUID("b", r.b);
            x.putLongArray("done", r.done.stream().mapToLong(Long::longValue).toArray());
            rl.add(x);
        }
        t.put("roads", rl);
        return t;
    }

    public static WorldSim load(CompoundTag t, HolderLookup.Provider registries) {
        WorldSim w = new WorldSim();
        for (Tag x : t.getList("people", Tag.TAG_COMPOUND)) {
            Person p = Person.load((CompoundTag) x);
            w.people.put(p.id, p);
        }
        for (Tag x : t.getList("settlements", Tag.TAG_COMPOUND)) {
            Settlement s = Settlement.load((CompoundTag) x);
            w.settlements.put(s.id, s);
        }
        for (Tag x : t.getList("factions", Tag.TAG_COMPOUND)) {
            Faction f = Faction.load((CompoundTag) x);
            w.factions.put(f.id, f);
        }
        for (Tag x : t.getList("news", Tag.TAG_COMPOUND)) w.news.add(NewsItem.load((CompoundTag) x));
        for (Tag x : t.getList("caravans", Tag.TAG_COMPOUND)) w.caravans.add(Caravan.load((CompoundTag) x));
        for (Tag x : t.getList("wars", Tag.TAG_COMPOUND)) w.wars.add(War.load((CompoundTag) x));
        w.history.addAll(Nbt.readStringList(t, "history"));
        w.nextNewsId = t.getLong("nextNews");
        w.lastDay = t.contains("lastDay") ? t.getLong("lastDay") : Long.MIN_VALUE;
        w.generated = t.getBoolean("generated");
        w.nightStage = t.getInt("nightStage");
        w.nightPressure = t.getDouble("nightPressure");
        w.dragonSlain = t.getBoolean("dragonSlain");
        for (Tag x : t.getList("seen", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            w.playerLastSeenDay.put(c.getUUID("p"), c.getLong("d"));
        }
        for (Tag x : t.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            w.claims.add(new Claim(c.getUUID("o"), c.getString("n"), Nbt.getPos(c, "c"), c.getInt("r")));
        }
        for (long l : t.getLongArray("spare")) w.spareSites.add(BlockPos.of(l));
        for (Tag x : t.getList("roads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            Road r = new Road(c.getUUID("a"), c.getUUID("b"));
            for (long l : c.getLongArray("done")) r.done.add(l);
            w.roads.add(r);
        }
        long[] wp = t.getLongArray("wardPos"), wd = t.getLongArray("wardDay");
        for (int i = 0; i < Math.min(wp.length, wd.length); i++) w.wards.put(wp[i], wd[i]);
        for (Tag x : t.getList("known", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            w.knownPlaces.put(c.getUUID("p"), new java.util.HashSet<>(Nbt.readUuidList(c, "s")));
        }
        return w;
    }

    /** Full round-trip through NBT; used by tests to prove save data is consistent. */
    public WorldSim roundTrip() {
        CompoundTag t = save(new CompoundTag(), null);
        WorldSim w = load(t, null);
        w.rules = rules;
        w.hooks = hooks;
        return w;
    }
}
