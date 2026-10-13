package com.longnight.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A community of people with a ledger, buildings, and plans. Also used for bandit camps and ruins. */
public class Settlement {
    /** BAND: a player's company travelling with them before they settle (no land, no buildings; its centre follows its leader). */
    public enum Kind { SETTLEMENT, BANDIT_CAMP, RUIN, BAND }

    public enum Tier {
        CAMP("Camp", 0), HAMLET("Hamlet", 5), VILLAGE("Village", 14), TOWN("Town", 36), CITY("City", 90);

        public final String displayName;
        public final int minPopulation;

        Tier(String displayName, int minPopulation) {
            this.displayName = displayName;
            this.minPopulation = minPopulation;
        }
    }

    public UUID id;
    public String name = "";
    public Kind kind = Kind.SETTLEMENT;
    public String culture = "valemen";
    public UUID faction;
    public BlockPos center;
    public int radius = 40;
    public long foundedDay;
    public Tier tier = Tier.CAMP;
    public boolean materialized;   // physical presence has been created in the world
    public boolean playerFounded;
    public UUID founderPlayer;
    public int treasury;           // copper pennies
    public Stockpile stock = new Stockpile();
    public final List<Building> buildings = new ArrayList<>();
    public final List<UUID> residents = new ArrayList<>();
    public final Map<UUID, Integer> reputation = new HashMap<>();   // player uuid -> -100..100
    public final Set<UUID> citizenPlayers = new HashSet<>();
    public final Set<UUID> visitors = new HashSet<>();
    public final List<Contracts.Contract> contracts = new ArrayList<>();
    public String festival = "";
    public long festivalDay = -1;
    public UUID funeralFor;
    public long funeralDay = -1;
    public final LinkedHashMap<Long, Long> knownNews = new LinkedHashMap<>();  // news id -> day learned
    public final List<String> chronicle = new ArrayList<>();
    public final Set<Long> badCells = new HashSet<>();
    /** cells whose terrain already passed the survey (not saved: only spares re-sampling terrain noise this session) */
    public final transient Set<Long> goodCells = new HashSet<>();
    public double morale = 0.6;
    public int raidsSurvived;

    // threats: an approaching raid
    public UUID raidSource;      // bandit camp (or enemy settlement) id
    public long raidDay = -1;    // day the raid strikes (at dusk)
    public int raidWarning;      // 0 none, 1 rumours, 2 scouts seen, 3 imminent
    public int raidStrength;

    // bandit camp state
    public int hunger;           // for bandit camps: pressure to raid

    public Settlement(UUID id) {
        this.id = id;
    }

    // ------------------------------------------------------------------ queries

    public List<Building> byType(BuildingType t) {
        List<Building> out = new ArrayList<>();
        for (Building b : buildings) if (b.type == t) out.add(b);
        return out;
    }

    public boolean hasUsable(BuildingType t) {
        for (Building b : buildings) if (b.type == t && b.usable()) return true;
        return false;
    }

    public boolean hasAny(BuildingType t) {
        for (Building b : buildings) if (b.type == t && b.state != Building.State.RUINED) return true;
        return false;
    }

    public Building building(UUID id) {
        if (id == null) return null;
        for (Building b : buildings) if (b.id.equals(id)) return b;
        return null;
    }

    public int housingCapacity() {
        int cap = 0;
        for (Building b : buildings) if (b.usable()) cap += b.type.housing + (b.type.housing > 0 ? (b.level - 1) * 2 : 0);
        return cap;
    }

    public int storageCapacity() {
        int cap = 150; // the hearth itself keeps a modest store
        for (Building b : buildings) if (b.usable()) cap += b.type.storage;
        return cap;
    }

    public int activeProjects() {
        int n = 0;
        for (Building b : buildings) if (b.underConstruction()) n++;
        return n;
    }

    public int reputationOf(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public void changeReputation(UUID player, int delta) {
        reputation.put(player, Math.max(-100, Math.min(100, reputationOf(player) + delta)));
    }

    public void chronicle(long day, String text) {
        chronicle.add("Day " + day + ": " + text);
        if (chronicle.size() > 60) chronicle.remove(0);
    }

    public String displayName() {
        return switch (kind) {
            case SETTLEMENT -> name;
            case BANDIT_CAMP -> name + " (outlaws)";
            case RUIN -> "Ruins of " + name;
            case BAND -> name;
        };
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("kind", kind.name());
        t.putString("culture", culture);
        Nbt.putUUID(t, "faction", faction);
        Nbt.putPos(t, "center", center);
        t.putInt("radius", radius);
        t.putLong("founded", foundedDay);
        t.putString("tier", tier.name());
        t.putBoolean("materialized", materialized);
        t.putBoolean("playerFounded", playerFounded);
        Nbt.putUUID(t, "founder", founderPlayer);
        t.putInt("treasury", treasury);
        t.put("stock", stock.save());
        ListTag bl = new ListTag();
        for (Building b : buildings) bl.add(b.save());
        t.put("buildings", bl);
        t.put("residents", Nbt.uuidList(residents));
        ListTag rep = new ListTag();
        reputation.forEach((k, v) -> {
            CompoundTag r = new CompoundTag();
            r.putUUID("p", k);
            r.putInt("v", v);
            rep.add(r);
        });
        t.put("rep", rep);
        t.put("citizens", Nbt.uuidList(citizenPlayers));
        t.put("visitors", Nbt.uuidList(visitors));
        t.put("contracts", Contracts.save(contracts));
        t.putString("festival", festival);
        t.putLong("festivalDay", festivalDay);
        Nbt.putUUID(t, "funeralFor", funeralFor);
        t.putLong("funeralDay", funeralDay);
        long[] newsIds = new long[knownNews.size()];
        long[] newsDays = new long[knownNews.size()];
        int i = 0;
        for (Map.Entry<Long, Long> e : knownNews.entrySet()) {
            newsIds[i] = e.getKey();
            newsDays[i++] = e.getValue();
        }
        t.putLongArray("newsIds", newsIds);
        t.putLongArray("newsDays", newsDays);
        t.put("chronicle", Nbt.stringList(chronicle));
        t.putLongArray("badCells", badCells.stream().mapToLong(Long::longValue).toArray());
        t.putDouble("morale", morale);
        t.putInt("raidsSurvived", raidsSurvived);
        Nbt.putUUID(t, "raidSource", raidSource);
        t.putLong("raidDay", raidDay);
        t.putInt("raidWarning", raidWarning);
        t.putInt("raidStrength", raidStrength);
        t.putInt("hunger", hunger);
        return t;
    }

    public static Settlement load(CompoundTag t) {
        Settlement s = new Settlement(t.getUUID("id"));
        s.name = t.getString("name");
        s.kind = Kind.valueOf(t.getString("kind"));
        s.culture = t.getString("culture");
        s.faction = Nbt.getUUID(t, "faction");
        s.center = Nbt.getPos(t, "center");
        s.radius = t.getInt("radius");
        s.foundedDay = t.getLong("founded");
        s.tier = Tier.valueOf(t.getString("tier"));
        s.materialized = t.getBoolean("materialized");
        s.playerFounded = t.getBoolean("playerFounded");
        s.founderPlayer = Nbt.getUUID(t, "founder");
        s.treasury = t.getInt("treasury");
        s.stock = Stockpile.load(t.getCompound("stock"));
        for (Tag x : t.getList("buildings", Tag.TAG_COMPOUND)) s.buildings.add(Building.load((CompoundTag) x));
        s.residents.addAll(Nbt.readUuidList(t, "residents"));
        for (Tag x : t.getList("rep", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) x;
            s.reputation.put(r.getUUID("p"), r.getInt("v"));
        }
        s.citizenPlayers.addAll(Nbt.readUuidList(t, "citizens"));
        s.visitors.addAll(Nbt.readUuidList(t, "visitors"));
        s.contracts.addAll(Contracts.load(t.getList("contracts", Tag.TAG_COMPOUND)));
        s.festival = t.getString("festival");
        s.festivalDay = t.contains("festivalDay") ? t.getLong("festivalDay") : -1;
        s.funeralFor = Nbt.getUUID(t, "funeralFor");
        s.funeralDay = t.contains("funeralDay") ? t.getLong("funeralDay") : -1;
        long[] ids = t.getLongArray("newsIds");
        long[] days = t.getLongArray("newsDays");
        for (int i = 0; i < Math.min(ids.length, days.length); i++) s.knownNews.put(ids[i], days[i]);
        s.chronicle.addAll(Nbt.readStringList(t, "chronicle"));
        for (long c : t.getLongArray("badCells")) s.badCells.add(c);
        s.morale = t.getDouble("morale");
        s.raidsSurvived = t.getInt("raidsSurvived");
        s.raidSource = Nbt.getUUID(t, "raidSource");
        s.raidDay = t.getLong("raidDay");
        s.raidWarning = t.getInt("raidWarning");
        s.raidStrength = t.getInt("raidStrength");
        s.hunger = t.getInt("hunger");
        return s;
    }
}
