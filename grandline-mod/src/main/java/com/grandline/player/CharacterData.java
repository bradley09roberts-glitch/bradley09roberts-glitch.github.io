package com.grandline.player;

import com.grandline.core.GLConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Everything persistent about a player's character. Attached to the player entity (saved with player data)
 * and synchronised to the owning client. Server is the only writer.
 */
public final class CharacterData implements INBTSerializable<CompoundTag> {
    // ---- identity ----
    public boolean created = false;
    public UUID characterId = UUID.randomUUID();
    public String name = "";
    public Race race = Race.HUMAN;
    public Appearance appearance = new Appearance();
    public Blue origin = Blue.EAST;
    public Faction faction = Faction.PIRATE;
    public int factionRank = 0;
    public int generation = 0;              // how many characters this player has had (permadeath legacy)
    public final List<String> fallenCharacters = new ArrayList<>();

    // ---- progression ----
    public int level = 1;
    public long xp = 0;
    public int statPoints = 0;
    public final EnumMap<Stat, Integer> stats = new EnumMap<>(Stat.class);
    public final Map<String, Integer> masteryXp = new HashMap<>();
    public long beli = 500;

    // ---- combat ----
    public float stamina = 100f;
    public float hakiGauge = 100f;
    public final Set<String> knownStyles = new LinkedHashSet<>();
    public String activeStyle = "brawler";
    public final String[][] loadout = new String[3][9];

    // ---- devil fruit ----
    @Nullable public String fruit = null;
    public boolean fruitAwakened = false;
    public String zoanForm = "";   // "", "hybrid", "full", "awakened"

    // ---- haki ----
    public boolean observationUnlocked = false;
    public boolean armamentUnlocked = false;
    public boolean conquerorTrait = false;   // hidden birth trait
    public boolean conquerorAwakened = false;
    public boolean futureSight = false;
    public boolean emission = false;
    public boolean internalDestruction = false;
    public boolean conquerorInfusion = false;

    // ---- world / social ----
    public long bounty = 0;
    public final Map<String, Integer> reputation = new HashMap<>();
    public final Set<String> discoveredIslands = new LinkedHashSet<>();
    public final Map<String, CompoundTag> questProgress = new HashMap<>();
    public final Set<String> completedQuests = new LinkedHashSet<>();
    public final Set<String> flags = new HashSet<>();
    @Nullable public UUID crewId = null;
    public boolean pvpFlag = false;
    public String logPoseTarget = "";

    // ---- knockdown (persisted so logging out doesn't escape it) ----
    public int knockdownTicks = 0;
    @Nullable public UUID knockedBy = null;

    public CharacterData() {
        for (Stat s : Stat.values()) stats.put(s, 0);
        knownStyles.add("brawler");
    }

    // ------------------------------------------------------------------ progression helpers

    public int stat(Stat s) {
        return stats.getOrDefault(s, 0);
    }

    public static long xpForLevel(int level) {
        return (long) (120 * Math.pow(level, 1.55));
    }

    /** @return number of levels gained */
    public int addXp(long amount) {
        if (amount <= 0) return 0;
        xp += Math.round(amount * GLConfig.XP_MULTIPLIER.get());
        int gained = 0;
        int max = GLConfig.MAX_LEVEL.get();
        while (level < max && xp >= xpForLevel(level)) {
            xp -= xpForLevel(level);
            level++;
            statPoints += 3;
            gained++;
        }
        if (level >= max) xp = Math.min(xp, xpForLevel(level));
        return gained;
    }

    public static int masteryLevelFor(int xpTotal) {
        int lvl = 0;
        int need = 60;
        int remaining = xpTotal;
        while (remaining >= need && lvl < 100) {
            remaining -= need;
            lvl++;
            need = 60 + lvl * 30;
        }
        return lvl;
    }

    public int mastery(String key) {
        return masteryLevelFor(masteryXp.getOrDefault(key, 0));
    }

    /** @return true when the mastery level increased. */
    public boolean addMastery(String key, int amount) {
        int before = mastery(key);
        double mult = GLConfig.MASTERY_MULTIPLIER.get() * (1.0 + stat(Stat.TECHNIQUE) * 0.01);
        masteryXp.merge(key, Math.max(1, (int) Math.round(amount * mult)), Integer::sum);
        return mastery(key) > before;
    }

    public float maxStamina() {
        return 100f + stat(Stat.ENDURANCE) * 4f + level * 0.5f;
    }

    public float maxHakiGauge() {
        return 100f + stat(Stat.WILLPOWER) * 5f;
    }

    public int reputation(String key) {
        return reputation.getOrDefault(key, 0);
    }

    public void addReputation(String key, int delta) {
        reputation.put(key, Mth.clamp(reputation(key) + delta, -1000, 1000));
    }

    public boolean isKnockedDown() {
        return knockdownTicks > 0;
    }

    @Nullable
    public String loadoutSlot(int set, int slot) {
        if (set < 0 || set >= loadout.length || slot < 0 || slot >= 9) return null;
        return loadout[set][slot];
    }

    // ------------------------------------------------------------------ permadeath

    /** Archive this character and reset to a blank, uncreated character. */
    public void archiveAndReset() {
        List<String> fallen = new ArrayList<>(fallenCharacters);
        if (created) fallen.add(name + " (" + race.id() + ", Lv " + level + ", bounty " + bounty + ")");
        int gen = generation + 1;
        CharacterData fresh = new CharacterData();
        this.deserializeNBT(null, fresh.serializeNBT(null));
        this.generation = gen;
        this.fallenCharacters.clear();
        this.fallenCharacters.addAll(fallen);
        this.characterId = UUID.randomUUID();
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public CompoundTag serializeNBT(@Nullable HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("created", created);
        t.putUUID("characterId", characterId);
        t.putString("name", name);
        t.putString("race", race.id());
        t.put("appearance", appearance.save());
        t.putString("origin", origin.id());
        t.putString("faction", faction.id());
        t.putInt("factionRank", factionRank);
        t.putInt("generation", generation);
        t.put("fallen", strings(fallenCharacters));

        t.putInt("level", level);
        t.putLong("xp", xp);
        t.putInt("statPoints", statPoints);
        CompoundTag st = new CompoundTag();
        stats.forEach((k, v) -> st.putInt(k.id(), v));
        t.put("stats", st);
        CompoundTag mx = new CompoundTag();
        masteryXp.forEach(mx::putInt);
        t.put("mastery", mx);
        t.putLong("beli", beli);

        t.putFloat("stamina", stamina);
        t.putFloat("haki", hakiGauge);
        t.put("styles", strings(knownStyles));
        t.putString("activeStyle", activeStyle);
        ListTag lo = new ListTag();
        for (String[] set : loadout) {
            ListTag l = new ListTag();
            for (String s : set) l.add(StringTag.valueOf(s == null ? "" : s));
            lo.add(l);
        }
        t.put("loadout", lo);

        if (fruit != null) t.putString("fruit", fruit);
        t.putBoolean("awakened", fruitAwakened);
        t.putString("zoanForm", zoanForm);

        t.putBoolean("obs", observationUnlocked);
        t.putBoolean("arm", armamentUnlocked);
        t.putBoolean("coqTrait", conquerorTrait);
        t.putBoolean("coq", conquerorAwakened);
        t.putBoolean("futureSight", futureSight);
        t.putBoolean("emission", emission);
        t.putBoolean("internal", internalDestruction);
        t.putBoolean("infusion", conquerorInfusion);

        t.putLong("bounty", bounty);
        CompoundTag rep = new CompoundTag();
        reputation.forEach(rep::putInt);
        t.put("rep", rep);
        t.put("discovered", strings(discoveredIslands));
        CompoundTag qp = new CompoundTag();
        questProgress.forEach(qp::put);
        t.put("quests", qp);
        t.put("completed", strings(completedQuests));
        t.put("flags", strings(flags));
        if (crewId != null) t.putUUID("crew", crewId);
        t.putBoolean("pvp", pvpFlag);
        t.putString("logPose", logPoseTarget);
        t.putInt("knockdown", knockdownTicks);
        if (knockedBy != null) t.putUUID("knockedBy", knockedBy);
        return t;
    }

    @Override
    public void deserializeNBT(@Nullable HolderLookup.Provider provider, CompoundTag t) {
        created = t.getBoolean("created");
        if (t.hasUUID("characterId")) characterId = t.getUUID("characterId");
        name = t.getString("name");
        race = Race.byId(t.getString("race"));
        appearance = Appearance.load(t.getCompound("appearance"));
        origin = Blue.byId(t.getString("origin"));
        faction = Faction.byId(t.getString("faction"));
        factionRank = t.getInt("factionRank");
        generation = t.getInt("generation");
        fallenCharacters.clear();
        readStrings(t, "fallen", fallenCharacters);

        level = Math.max(1, t.getInt("level"));
        xp = t.getLong("xp");
        statPoints = t.getInt("statPoints");
        CompoundTag st = t.getCompound("stats");
        for (Stat s : Stat.values()) stats.put(s, st.getInt(s.id()));
        masteryXp.clear();
        CompoundTag mx = t.getCompound("mastery");
        for (String k : mx.getAllKeys()) masteryXp.put(k, mx.getInt(k));
        beli = t.contains("beli") ? t.getLong("beli") : 500;

        stamina = t.contains("stamina") ? t.getFloat("stamina") : 100f;
        hakiGauge = t.contains("haki") ? t.getFloat("haki") : 100f;
        knownStyles.clear();
        readStrings(t, "styles", knownStyles);
        if (knownStyles.isEmpty()) knownStyles.add("brawler");
        activeStyle = t.contains("activeStyle") ? t.getString("activeStyle") : "brawler";
        ListTag lo = t.getList("loadout", Tag.TAG_LIST);
        for (int i = 0; i < loadout.length; i++) {
            Arrays.fill(loadout[i], null);
            if (i < lo.size()) {
                ListTag l = lo.getList(i);
                for (int j = 0; j < 9 && j < l.size(); j++) {
                    String s = l.getString(j);
                    loadout[i][j] = s.isEmpty() ? null : s;
                }
            }
        }

        fruit = t.contains("fruit") ? t.getString("fruit") : null;
        fruitAwakened = t.getBoolean("awakened");
        zoanForm = t.getString("zoanForm");

        observationUnlocked = t.getBoolean("obs");
        armamentUnlocked = t.getBoolean("arm");
        conquerorTrait = t.getBoolean("coqTrait");
        conquerorAwakened = t.getBoolean("coq");
        futureSight = t.getBoolean("futureSight");
        emission = t.getBoolean("emission");
        internalDestruction = t.getBoolean("internal");
        conquerorInfusion = t.getBoolean("infusion");

        bounty = t.getLong("bounty");
        reputation.clear();
        CompoundTag rep = t.getCompound("rep");
        for (String k : rep.getAllKeys()) reputation.put(k, rep.getInt(k));
        discoveredIslands.clear();
        readStrings(t, "discovered", discoveredIslands);
        questProgress.clear();
        CompoundTag qp = t.getCompound("quests");
        for (String k : qp.getAllKeys()) questProgress.put(k, qp.getCompound(k));
        completedQuests.clear();
        readStrings(t, "completed", completedQuests);
        flags.clear();
        readStrings(t, "flags", flags);
        crewId = t.hasUUID("crew") ? t.getUUID("crew") : null;
        pvpFlag = t.getBoolean("pvp");
        logPoseTarget = t.getString("logPose");
        knockdownTicks = t.getInt("knockdown");
        knockedBy = t.hasUUID("knockedBy") ? t.getUUID("knockedBy") : null;
    }

    private static ListTag strings(Collection<String> c) {
        ListTag l = new ListTag();
        for (String s : c) l.add(StringTag.valueOf(s));
        return l;
    }

    private static void readStrings(CompoundTag t, String key, Collection<String> out) {
        ListTag l = t.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
    }
}
