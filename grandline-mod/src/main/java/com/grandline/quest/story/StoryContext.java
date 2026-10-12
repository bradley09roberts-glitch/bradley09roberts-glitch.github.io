package com.grandline.quest.story;

import com.grandline.creature.WildBeast;
import com.grandline.world.Island;
import com.grandline.world.IslandShape;
import com.grandline.world.IslandTheme;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a story can be built from on one island: its sea, theme, town, dock, secrets and landmarks, and deterministic
 * places on it (a spot in town, at the dock, out in the wilds, on the beach, at sea). Everything is a pure function of
 * the world plan, so the same seed always yields the same places.
 */
public final class StoryContext {
    /** A place on (or off) an island. {@code y} is a known floor height (wrecks) or {@link #SURFACE}. */
    public record Spot(int x, int z, int y) {
        public static final int SURFACE = Integer.MIN_VALUE;

        Spot(int x, int z) {
            this(x, z, SURFACE);
        }
    }

    public final WorldPlan plan;
    public final Island isl;
    public final RandomSource r;
    public final Settlement st;
    public final PeopleNames.Culture culture;
    /** Id prefix for everything generated here ("" for the world's own stories; tests use their own prefix). */
    public final String prefix;

    StoryContext(WorldPlan plan, Island isl, String prefix, long salt) {
        this.plan = plan;
        this.isl = isl;
        this.prefix = prefix;
        this.r = new LegacyRandomSource(isl.seed ^ 0x57A1E5L ^ salt);
        this.st = isl.settlement();
        this.culture = PeopleNames.culture(isl.zone);
    }

    // ------------------------------------------------------------------ facts

    public SettlementType settlement() {
        return isl.settlementType;
    }

    public boolean settled() {
        return st.hasCenter && isl.settlementType != SettlementType.NONE;
    }

    /** Villages, towns and cities where ordinary people live (not bases, forts or ruins). */
    public boolean town() {
        return switch (isl.settlementType) {
            case HAMLET, VILLAGE, TOWN, PORT, CITY -> true;
            default -> false;
        };
    }

    public boolean bigTown() {
        return switch (isl.settlementType) {
            case TOWN, PORT, CITY -> true;
            default -> false;
        };
    }

    public boolean hasDock() {
        return st.hasDock;
    }

    public boolean grandLine() {
        return isl.zone.isGrandLine() || isl.zone == SeaZone.CALM_BELT;
    }

    public int tier() {
        return Math.max(1, Math.min(10, isl.tier));
    }

    @Nullable
    public Plot secret(BuildingType type) {
        for (Plot p : st.secrets) if (p.type() == type) return p;
        return null;
    }

    public boolean hasLandmark(String id) {
        for (Island.LandmarkPlacement lp : isl.landmarks) if (lp.id().equals(id)) return true;
        return false;
    }

    /** The beast that lives here (for nests and beast troubles), or null. */
    @Nullable
    public WildBeast.Kind beast() {
        return beastFor(isl.theme);
    }

    @Nullable
    public static WildBeast.Kind beastFor(IslandTheme theme) {
        return switch (theme) {
            case SNOW, ICE, TAIGA -> WildBeast.Kind.LAPAHN;
            case SWAMP, TROPICAL -> WildBeast.Kind.BANANA_GATOR;
            case DESERT, BADLANDS, SAVANNA, TEMPERATE, MEADOW, FLOWER, ROCKY -> WildBeast.Kind.SEA_CAT;
            default -> null;
        };
    }

    public Component islandName() {
        return islandName(isl);
    }

    public static Component islandName(Island i) {
        return i.canon && i.name == null ? Component.translatable(i.translationKey()) : Component.literal(i.name);
    }

    // ------------------------------------------------------------------ places

    /** A spot in town, ring {@code k} around the square (the beach when there is no town). */
    public Spot townSpot(int k) {
        if (!st.hasCenter) return beach(k);
        double a = k * 2.1 + 0.4;
        int d = 7 + 5 * (k % 3);
        return new Spot(st.centerX + (int) Math.round(Math.cos(a) * d), st.centerZ + (int) Math.round(Math.sin(a) * d));
    }

    /** By the dock root (or in town when there is no dock). */
    public Spot dock() {
        if (!st.hasDock) return townSpot(5);
        return new Spot(st.dockX - st.dockDirX * 5 / 1000, st.dockZ - st.dockDirZ * 5 / 1000);
    }

    /** Somewhere out in the wilds: dry land, away from the town and its buildings. {@code salt} picks which place. */
    public Spot wild(String salt) {
        RandomSource rr = new LegacyRandomSource(isl.seed ^ salt.hashCode() * 0x9E3779B97F4A7C15L);
        for (int pass = 0; pass < 3; pass++) {
            int minTown = pass == 0 ? 50 : pass == 1 ? 25 : 0;
            for (int i = 0; i < 48; i++) {
                double a = rr.nextDouble() * Math.PI * 2;
                double dist = isl.radius * (0.25 + 0.55 * rr.nextDouble());
                int x = isl.x + (int) (Math.cos(a) * dist), z = isl.z + (int) (Math.sin(a) * dist);
                if (!dry(x, z, 2)) continue;
                if (st.hasCenter && Math.hypot(x - st.centerX, z - st.centerZ) < minTown) continue;
                if (inPlot(x, z, 4)) continue;
                return new Spot(x, z);
            }
        }
        return beach(salt.length());
    }

    /** On the beach or the coastal strip (still dry land). */
    public Spot beach(int k) {
        RandomSource rr = new LegacyRandomSource(isl.seed ^ 0xBEAC4L ^ k * 31L);
        for (int i = 0; i < 64; i++) {
            double a = rr.nextDouble() * Math.PI * 2;
            for (double f = 0.92; f >= 0.4; f -= 0.06) {
                int x = isl.x + (int) (Math.cos(a) * isl.radius * f), z = isl.z + (int) (Math.sin(a) * isl.radius * f);
                if (!dry(x, z, 1) || inPlot(x, z, 3)) continue;
                return new Spot(x, z);
            }
        }
        return new Spot(isl.x, isl.z);
    }

    /** Open water off the coast, {@code out} blocks or more beyond the shore, deep enough for a Sea King. */
    public Spot sea(int out, int minDepth) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        RandomSource rr = new LegacyRandomSource(isl.seed ^ 0x5EA5L);
        double a0 = rr.nextDouble() * Math.PI * 2;
        for (int d = isl.radius + out; d < isl.radius + out + 160; d += 20)
            for (int i = 0; i < 16; i++) {
                double a = a0 + i * Math.PI / 8;
                int x = isl.x + (int) (Math.cos(a) * d), z = isl.z + (int) (Math.sin(a) * d);
                plan.sample(x, z, smp);
                if (smp.height <= WorldPlan.SEA_LEVEL - minDepth && !smp.canal && !smp.redLine) return new Spot(x, z, WorldPlan.SEA_LEVEL);
            }
        return new Spot(isl.x + isl.radius + out, isl.z, WorldPlan.SEA_LEVEL);
    }

    /** Centre of a secret plot, on its floor (a wreck lies under water). */
    public Spot at(Plot p) {
        return new Spot(p.centerX(), p.centerZ(), p.type() == BuildingType.SHIPWRECK ? p.y() : Spot.SURFACE);
    }

    /** Centre of a landmark on this island, or null. */
    @Nullable
    public Spot landmark(String id) {
        for (Plot p : st.plots) if (p.type() == BuildingType.LANDMARK && p.landmark().equals(id)) return new Spot(p.centerX(), p.centerZ());
        return null;
    }

    private boolean dry(int x, int z, int above) {
        double h = isl.naturalHeight(x, z);
        return !Double.isNaN(h) && h >= WorldPlan.SEA_LEVEL + above && isl.t(x, z) < 0.9;
    }

    private boolean inPlot(int x, int z, int margin) {
        for (Plot p : st.plots) if (p.contains(x, z, margin)) return true;
        for (Plot p : st.secrets) if (p.contains(x, z, margin)) return true;
        return false;
    }

    // ------------------------------------------------------------------ neighbours

    /** Other islands of this plan nearest first (same sea first), excluding points and the Red Line. */
    public List<Island> neighbours(int max, boolean originalsOnly) {
        List<Island> l = new ArrayList<>();
        for (Island i : plan.islands) {
            if (i == isl || i.radius < 60 || i.zone == SeaZone.RED_LINE || i.shape == IslandShape.SEA) continue;
            if (originalsOnly && i.canon) continue;
            l.add(i);
        }
        l.sort(Comparator.comparingDouble((Island i) -> (sameSea(i) ? 0 : 1_000_000) + i.distance(isl.x, isl.z)));
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    public boolean sameSea(Island i) {
        if (i.zone == isl.zone) return true;
        return isl.zone.isGrandLine() && i.zone == SeaZone.CALM_BELT || isl.zone == SeaZone.CALM_BELT && i.zone.isGrandLine();
    }
}
