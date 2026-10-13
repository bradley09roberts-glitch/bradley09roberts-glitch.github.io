package com.longnight.entity;

import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Commodity;
import com.longnight.sim.Construction;
import com.longnight.sim.Economy;
import com.longnight.sim.Person;
import com.longnight.sim.Profession;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import com.longnight.world.Places;
import com.longnight.world.Placer;
import com.longnight.world.RaidManager;
import com.longnight.world.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Daily routine and practical decisions of an embodied person: sleep in their own bed, work at
 * their trade, gather in the evening, shelter from danger. Movement uses vanilla pathfinding
 * with stuck detection; when a task is impossible the person records why ("I can't reach the
 * site", "no stone in the stores") and turns to something else instead of walking into walls.
 */
public class CitizenBrain {
    public enum Mode { IDLE, SLEEP, WORK, SOCIAL, PLAY, SHELTER, PATROL, CAMP, RAID, FESTIVAL, FUNERAL }

    public static final int CYCLE_TICKS = 1250;

    Mode mode = Mode.IDLE;
    BlockPos target;
    int pause;
    int stuck;
    double lastDist = Double.MAX_VALUE;
    int waitTicks;
    int cycleTicks;
    int failures;
    BlockPos chopBase;
    final List<BlockPos> chopQueue = new ArrayList<>();
    BlockPos tendTarget;
    final Random rng = new Random();

    public void pause(int ticks) {
        pause = Math.max(pause, ticks);
    }

    public Mode mode() {
        return mode;
    }

    void tick(CitizenEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        if (pause > 0) {
            pause--;
            return;
        }
        WorldSim sim = WorldSim.get(level.getServer());
        Person p = sim.person(e.personId);
        if (p == null || !p.alive) {
            e.discard();
            return;
        }
        Settlement s = sim.settlement(e.raidTarget != null ? e.raidTarget : e.settlementId);
        if (s == null) return;
        long day = WorldSim.dayOf(level.getServer().overworld());
        long time = level.getDayTime() % 24000L;
        if (e.tickCount % 20 == 0) {
            Mode next = decide(e, sim, p, s, day, time);
            if (next != mode) {
                if (mode == Mode.SLEEP && e.isSleeping()) e.stopSleeping();
                mode = next;
                target = null;
                stuck = 0;
                waitTicks = 0;
                lastDist = Double.MAX_VALUE;
            }
        }
        switch (mode) {
            case SLEEP -> sleep(e, level, sim, p, s);
            case WORK -> work(e, level, sim, p, s, day);
            case SOCIAL -> social(e, level, sim, p, s);
            case PLAY -> wander(e, level, homeOrCentre(level, s, p), 12, 0.7);
            case SHELTER -> shelter(e, level, sim, p, s);
            case PATROL -> patrol(e, level, s, p);
            case CAMP -> wander(e, level, s.center, 10, 0.5);
            case RAID -> raid(e, level, sim, p, s);
            case FESTIVAL -> gather(e, level, p, s, holyPlace(level, s), "celebrating " + s.festival);
            case FUNERAL -> {
                Person dead = sim.person(s.funeralFor);
                gather(e, level, p, s, burialGround(level, s), "mourning " + (dead == null ? "the dead" : dead.given));
            }
            default -> wander(e, level, s.center, 8, 0.5);
        }
    }

    Mode decide(CitizenEntity e, WorldSim sim, Person p, Settlement s, long day, long time) {
        if (e.raidTarget != null) return Mode.RAID;
        if (p.profession == Profession.BANDIT) return Mode.CAMP;
        boolean night = time >= 12600 && time < 23400;
        boolean fighter = e.isFighter();
        if (!fighter && danger(e, s)) return Mode.SHELTER;
        if (night) return fighter && (p.id.getLeastSignificantBits() & 1) == 0 ? Mode.PATROL : Mode.SLEEP;
        if (time >= 5000 && time < 10000 && com.longnight.sim.Festivals.mourns(sim, p, s, day)) return Mode.FUNERAL;
        if (time >= 4000 && time < 11000 && s.festivalDay == day && !s.festival.isEmpty()) return Mode.FESTIVAL;
        if (!sim.isAdult(p, day)) return Mode.PLAY;
        if (time < 1000 || time >= 11000) return Mode.SOCIAL;
        return Mode.WORK;
    }

    boolean danger(CitizenEntity e, Settlement s) {
        if (s.raidWarning >= 3) return true;
        AABB box = e.getBoundingBox().inflate(10);
        for (LivingEntity x : e.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (x instanceof Monster && !(x instanceof net.minecraft.world.entity.monster.EnderMan)) return true;
            if (x instanceof CitizenEntity c && c.isHostile()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ movement

    /** Walk towards pos; true when within reach. Long trips go via waypoints; being stuck is detected and explained. */
    boolean goTo(CitizenEntity e, BlockPos pos, double speed, double reach, Person p, String what) {
        double d = Math.sqrt(e.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        if (d <= reach) {
            e.getNavigation().stop();
            stuck = 0;
            lastDist = Double.MAX_VALUE;
            return true;
        }
        if (e.tickCount % 40 == 0) {
            // progress check every two seconds
            if (d > lastDist - 0.5) stuck++;
            else {
                stuck = 0;
                failures = 0;
            }
            lastDist = d;
        }
        if (e.getNavigation().isDone() || e.tickCount % 60 == 0) {
            BlockPos step = pos;
            if (d > 24 && e.level() instanceof ServerLevel sl) {
                // pathfinding has a limited range: head for a waypoint on the way
                double f = 20 / d;
                int wx = (int) Math.round(e.getX() + (pos.getX() - e.getX()) * f + (stuck > 1 ? rng.nextInt(9) - 4 : 0));
                int wz = (int) Math.round(e.getZ() + (pos.getZ() - e.getZ()) * f + (stuck > 1 ? rng.nextInt(9) - 4 : 0));
                step = Places.ground(sl, new BlockPos(wx, pos.getY(), wz));
            }
            if (!e.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, speed) && e.tickCount % 40 == 0) stuck++;
        }
        if (stuck >= 8) {
            stuck = 0;
            failures++;
            p.blocked = "I can't find a way to " + what + ".";
            target = null;
            lastDist = Double.MAX_VALUE;
            if (failures >= 3 && e.level() instanceof ServerLevel sl && d < 64) {
                // truly boxed in (e.g. by a player's wall): step over the obstacle rather than starve
                BlockPos g = Places.ground(sl, pos);
                e.teleportTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
                failures = 0;
            } else pause(40 + rng.nextInt(60));
            return false;
        }
        if (stuck == 0 && p.blocked.startsWith("I can't find a way")) p.blocked = "";
        return false;
    }

    void wander(CitizenEntity e, ServerLevel level, BlockPos around, int radius, double speed) {
        if (around == null) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (target == null || e.getNavigation().isDone()) {
            target = Places.ground(level, around.offset(rng.nextInt(radius * 2 + 1) - radius, 0, rng.nextInt(radius * 2 + 1) - radius));
            e.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
            waitTicks = 40 + rng.nextInt(100);
        }
    }

    BlockPos homeOrCentre(ServerLevel level, Settlement s, Person p) {
        Building h = s.building(p.home);
        if (h != null && h.origin != null && h.placed > 0) return Places.mark(level, s, h, "door");
        return s.center;
    }

    // ------------------------------------------------------------------ routines

    void sleep(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s) {
        if (e.isSleeping()) return;
        Building h = s.building(p.home);
        if (h == null || h.origin == null || h.placed < h.total) {
            p.activity = "sleeping rough";
            wander(e, level, s.center, 4, 0.5);
            return;
        }
        BlockPos bed = Places.bedFor(level, s, h, p);
        if (bed == null) {
            p.activity = "resting at home";
            goTo(e, Places.mark(level, s, h, "work"), 0.55, 1.5, p, "my home");
            return;
        }
        if (goTo(e, bed, 0.55, 1.8, p, "my bed")) {
            BlockState st = level.getBlockState(bed);
            if (st.isBed(level, bed, e) && !st.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
                e.startSleeping(bed);
                p.activity = "asleep";
            }
        } else p.activity = "going home to bed";
    }

    void social(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s) {
        if (target == null) {
            List<Building> spots = new ArrayList<>();
            for (Building b : s.buildings) {
                if (!b.usable() || b.placed < b.total) continue;
                if (b.type == BuildingType.TAVERN || b.type == BuildingType.WELL || b.type == BuildingType.CAMPSITE
                        || b.type == BuildingType.MARKET || b.type == BuildingType.TEMPLE) spots.add(b);
            }
            if (spots.isEmpty()) target = s.center;
            else {
                Building b = spots.get(rng.nextInt(spots.size()));
                BlockPos m = Places.mark(level, s, b, "social");
                target = m != null ? m : Places.mark(level, s, b, "door");
                p.activity = b.type == BuildingType.TAVERN ? "sharing a drink at " + (b.name.isEmpty() ? "the tavern" : b.name)
                        : "gathering with neighbours";
            }
            target = Places.ground(level, target.offset(rng.nextInt(5) - 2, 0, rng.nextInt(5) - 2));
        }
        if (goTo(e, target, 0.55, 2.0, p, "the gathering place")) {
            if (e.tickCount % 60 == 0) {
                List<CitizenEntity> near = level.getEntitiesOfClass(CitizenEntity.class, e.getBoundingBox().inflate(4), c -> c != e);
                if (!near.isEmpty()) {
                    e.getLookControl().setLookAt(near.get(0));
                    if (rng.nextInt(4) == 0) e.swing(InteractionHand.MAIN_HAND);
                }
            }
            if (rng.nextInt(400) == 0) target = null;
        }
    }

    void shelter(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s) {
        Building h = s.building(p.home);
        BlockPos safe = h != null && h.origin != null && h.placed >= h.total ? Places.mark(level, s, h, "work") : s.center;
        p.activity = "sheltering from danger";
        goTo(e, safe, 0.8, 1.5, p, "shelter");
    }

    void patrol(CitizenEntity e, ServerLevel level, Settlement s, Person p) {
        p.activity = "keeping watch";
        if (target == null) {
            List<BlockPos> points = new ArrayList<>();
            points.add(s.center);
            for (Building b : s.buildings) {
                if (b.origin != null && b.placed > 0 && b.usable()) {
                    BlockPos d = Places.mark(level, s, b, b.type == BuildingType.WATCHTOWER ? "work" : "door");
                    if (d != null) points.add(d);
                }
            }
            target = points.get(rng.nextInt(points.size()));
        }
        if (goTo(e, target, 0.6, 2.0, p, "my post")) {
            if (waitTicks++ > 80 + rng.nextInt(120)) {
                target = null;
                waitTicks = 0;
            }
        }
    }

    void raid(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s) {
        p.activity = "raiding " + s.name;
        if (e.getTarget() != null) return;
        BlockPos goal = Places.storeOrCentre(level, s);
        if (goTo(e, goal, 0.85, 3.0, p, s.name)) {
            if (e.tickCount % 100 == 0) RaidManager.plunder(level, sim, s, e, p);
        }
    }

    // ------------------------------------------------------------------ work

    void work(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, long day) {
        Building w = s.building(p.workplace);
        switch (p.profession) {
            case BUILDER -> build(e, level, sim, p, s, day);
            case FARMER -> farm(e, level, sim, p, s, w, day);
            case LUMBERJACK -> chop(e, level, sim, p, s, w, day);
            case GUARD, SOLDIER -> {
                if (p.profession == Profession.SOLDIER && w != null && rng.nextBoolean()) station(e, level, sim, p, s, w, day, "drilling");
                else patrol(e, level, s, p);
            }
            case LABOURER -> haul(e, level, sim, p, s);
            case RULER -> {
                Building hall = first(s, BuildingType.TOWN_HALL);
                if (hall != null) station(e, level, sim, p, s, hall, day, "holding court");
                else wander(e, level, s.center, 6, 0.5);
                p.activity = "holding court";
            }
            default -> {
                if (w != null && w.usable() && w.placed >= w.total) station(e, level, sim, p, s, w, day, verb(p.profession));
                else if (p.profession == Profession.HUNTER) forage(e, level, sim, p, s, day);
                else {
                    p.activity = "looking for work";
                    if (w != null && w.underConstruction()) p.blocked = "My " + w.type.displayName.toLowerCase() + " is not built yet.";
                    wander(e, level, s.center, 10, 0.5);
                }
            }
        }
    }

    static String verb(Profession pr) {
        return switch (pr) {
            case QUARRIER -> "cutting stone";
            case MINER -> "working the mine";
            case FISHER -> "fishing";
            case HUNTER -> "dressing game";
            case SHEPHERD -> "tending the flock";
            case MILLER -> "grinding grain";
            case BAKER -> "baking bread";
            case BREWER -> "brewing ale";
            case CARPENTER -> "sawing timber";
            case BLACKSMITH -> "working the forge";
            case WEAVER -> "weaving cloth";
            case MERCHANT -> "minding the stall";
            case INNKEEPER -> "serving at the tavern";
            case PRIEST -> "tending the shrine";
            case HEALER -> "tending the sick";
            case TEACHER -> "teaching the children";
            case SCHOLAR -> "studying old books";
            case GRAVEDIGGER -> "tending the graves";
            case STEWARD -> "keeping the accounts";
            default -> "working";
        };
    }

    /** Stand at the workplace and work; each finished cycle produces through the shared recipe. */
    void station(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, Building w, long day, String doing) {
        BlockPos spot = Places.mark(level, s, w, "work");
        if (spot == null) spot = w.centre();
        if (goTo(e, spot, 0.55, 2.2, p, "the " + w.type.displayName.toLowerCase())) {
            p.activity = doing;
            if (e.tickCount % 40 == 0) e.swing(InteractionHand.MAIN_HAND);
            progressCycle(e, level, sim, p, s, day, 20);
        }
    }

    void progressCycle(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, long day, int ticks) {
        cycleTicks += 1;
        if (cycleTicks < CYCLE_TICKS) return;
        cycleTicks = 0;
        if (p.profession.producesGoods()) {
            Commodity c = Economy.work(sim, s, p, 1, day);
            if (c != null) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.8, e.getZ(), 4, 0.3, 0.2, 0.3, 0);
                p.activity = "finished a batch of " + c.displayName.toLowerCase();
            }
        } else {
            p.addSkill(p.profession, 0.05f);
        }
        sim.setDirty();
    }

    void build(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, long day) {
        Building b = null;
        for (Building x : s.buildings) {
            if (x.underConstruction() && x.origin != null && (x.problem.isEmpty() || x.placed < x.progress || rng.nextInt(20) == 0)) {
                b = x;
                break;
            }
        }
        if (b == null) {
            for (Building x : s.buildings) if (x.underConstruction() && x.origin != null) b = x;
        }
        if (b == null) {
            p.activity = "waiting for the next project";
            p.blocked = "";
            Building yard = first(s, BuildingType.BUILDERS_YARD);
            wander(e, level, yard != null && yard.origin != null ? yard.centre() : s.center, 6, 0.5);
            return;
        }
        BlockPos next = Placer.nextStepPos(level, s, b);
        BlockPos stand = next != null ? next : Places.mark(level, s, b, "door");
        if (stand == null) stand = b.centre();
        double d = Math.sqrt(e.distanceToSqr(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5));
        p.activity = "building the " + (b.name.isEmpty() ? b.type.displayName.toLowerCase() : b.name);
        if (d > 9) {
            BlockPos approach = Places.ground(level, stand);
            if (!goTo(e, approach, 0.6, 3.0, p, "the building site of the " + b.type.displayName.toLowerCase())) return;
        } else {
            e.getNavigation().stop();
        }
        if (e.tickCount % 14 != 0) return;
        if (next != null) e.getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
        if (b.placed < b.progress) {
            Placer.placeNext(level, sim, s, b);
            e.swing(InteractionHand.MAIN_HAND);
            return;
        }
        int paid = Construction.advance(sim, s, b, 1, day);
        if (paid == 1) {
            Placer.placeNext(level, sim, s, b);
            e.swing(InteractionHand.MAIN_HAND);
            p.blocked = "";
            p.addSkill(Profession.BUILDER, 0.01f);
        } else if (b.underConstruction()) {
            p.blocked = b.problem.isEmpty() ? "We lack materials." : b.problem;
            pause(100);
        }
    }

    void haul(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s) {
        Building site = null;
        for (Building b : s.buildings) if (b.underConstruction() && b.origin != null) site = b;
        BlockPos store = Places.storeOrCentre(level, s);
        if (site == null) {
            p.activity = "doing odd jobs";
            wander(e, level, s.center, 10, 0.5);
            return;
        }
        if (target == null) target = (rng.nextBoolean() ? store : Places.ground(level, site.centre()));
        p.activity = "hauling materials to the " + site.type.displayName.toLowerCase();
        if (goTo(e, target, 0.55, 2.5, p, "the stores")) {
            if (waitTicks++ > 40) {
                target = target.equals(store) ? Places.ground(level, site.centre()) : store;
                waitTicks = 0;
            }
        }
    }

    void farm(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, Building w, long day) {
        if (w == null || !w.usable() || w.placed < w.total) {
            p.activity = "waiting for the fields to be laid out";
            wander(e, level, s.center, 8, 0.5);
            return;
        }
        if (tendTarget == null || e.tickCount % 200 == 0) tendTarget = Places.findCrop(level, s, w, rng);
        if (tendTarget == null) {
            station(e, level, sim, p, s, w, day, "tending the fields");
            return;
        }
        p.activity = "working the fields";
        if (goTo(e, tendTarget.above(0), 0.55, 2.0, p, "the fields")) {
            BlockState st = level.getBlockState(tendTarget);
            if (e.tickCount % 30 == 0) {
                e.swing(InteractionHand.MAIN_HAND);
                if (st.getBlock() instanceof CropBlock crop) {
                    if (crop.isMaxAge(st)) {
                        level.destroyBlock(tendTarget, false, e);
                        level.setBlock(tendTarget, crop.getStateForAge(0), 3);
                        p.activity = "harvesting";
                    } else if (rng.nextInt(3) == 0) {
                        st.randomTick(level, tendTarget, level.random); // careful tending helps crops along
                    }
                }
                tendTarget = null;
            }
            progressCycle(e, level, sim, p, s, day, 30);
        }
    }

    void forage(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, long day) {
        p.activity = "hunting and gathering";
        wander(e, level, s.center, 24, 0.6);
        progressCycle(e, level, sim, p, s, day, 1);
    }

    void chop(CitizenEntity e, ServerLevel level, WorldSim sim, Person p, Settlement s, Building w, long day) {
        if (w == null || !w.usable() || w.placed < w.total) {
            p.activity = "waiting for the lumber camp";
            wander(e, level, s.center, 8, 0.5);
            return;
        }
        if (chopQueue.isEmpty()) {
            if (chopBase == null && e.tickCount % 40 == 0) {
                chopBase = Trees.findNaturalTree(level, sim, s, w.centre(), 28, rng);
                if (chopBase == null) {
                    p.blocked = "There are no more trees near the lumber camp that we may fell.";
                    station(e, level, sim, p, s, w, day, "splitting logs");
                    return;
                }
                p.blocked = "";
            }
            if (chopBase == null) {
                station(e, level, sim, p, s, w, day, "splitting logs");
                return;
            }
            if (goTo(e, chopBase, 0.55, 2.5, p, "the trees")) {
                chopQueue.addAll(Trees.trunk(level, chopBase));
                if (chopQueue.isEmpty()) chopBase = null;
            }
            return;
        }
        p.activity = "felling a tree";
        if (e.tickCount % 18 == 0) {
            BlockPos log = chopQueue.remove(chopQueue.size() - 1);
            e.getLookControl().setLookAt(log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5);
            e.swing(InteractionHand.MAIN_HAND);
            if (Trees.isLog(level.getBlockState(log))) level.destroyBlock(log, false, e);
            if (chopQueue.isEmpty()) {
                Trees.replant(level, s, chopBase);
                chopBase = null;
            }
        }
        progressCycle(e, level, sim, p, s, day, 18);
    }

    void gather(CitizenEntity e, ServerLevel level, Person p, Settlement s, BlockPos place, String doing) {
        p.activity = doing;
        if (place == null) place = s.center;
        if (target == null) target = Places.ground(level, place.offset(rng.nextInt(7) - 3, 0, rng.nextInt(7) - 3));
        if (goTo(e, target, 0.55, 2.0, p, "the gathering") && e.tickCount % 80 == 0) {
            e.getLookControl().setLookAt(place.getX() + 0.5, place.getY() + 1, place.getZ() + 0.5);
            if (doing.startsWith("celebrating") && rng.nextInt(3) == 0) {
                e.swing(InteractionHand.MAIN_HAND);
                level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + 2.2, e.getZ(), 1, 0.2, 0.1, 0.2, 0.5);
            }
        }
    }

    BlockPos holyPlace(ServerLevel level, Settlement s) {
        for (BuildingType t : new BuildingType[]{BuildingType.TEMPLE, BuildingType.SHRINE, BuildingType.WELL, BuildingType.CAMPSITE}) {
            Building b = first(s, t);
            if (b != null && b.placed >= b.total) {
                BlockPos m = Places.mark(level, s, b, t == BuildingType.TEMPLE ? "social" : "work");
                if (m != null) return m;
            }
        }
        return s.center;
    }

    BlockPos burialGround(ServerLevel level, Settlement s) {
        Building g = first(s, BuildingType.GRAVEYARD);
        return g != null && g.placed >= g.total ? Places.mark(level, s, g, "work") : s.center;
    }

    static Building first(Settlement s, BuildingType t) {
        for (Building b : s.buildings) if (b.type == t && b.usable()) return b;
        return null;
    }
}
