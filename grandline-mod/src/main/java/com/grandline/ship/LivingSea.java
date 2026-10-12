package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.core.Chunks;
import com.grandline.core.GLConfig;
import com.grandline.npc.Bounties;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEffects;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The living sea around players: NPC ships go about their business whether or not a player joins in.
 * <ul>
 *   <li>Sea battles: Marine ships engage the pirates they sight, pirates take on Marines and bounty hunters only when
 *   strong enough (otherwise they run), bounty hunters chase pirate ships, pirates fall on merchantmen. They fight
 *   with the gun crews, rams and boarding parties of {@link NavalCombat}. A beaten hulk founders after a while and
 *   every NPC ship that goes down leaves a salvage boat (a chest boat with a loot table) and floating wreckage.</li>
 *   <li>Merchant traffic ({@link MerchantShips}): merchantmen, and in the Grand Line Marine-escorted convoys, sail
 *   between settled islands of one sea; hail one to trade with her captain, or attack her (piracy).</li>
 *   <li>Harbour raids ({@link HarbourRaids}): pirates sail into a settled harbour near a player and land raiders.</li>
 * </ul>
 * Players who help a side earn reputation (or a bounty) through the existing faction rules. Spawn decisions run
 * every 5 s, only around players on the overworld ocean, never in the Calm Belt, on the Red Line or in a harbour
 * (raids excepted), and every NPC ship counts against {@link SeaEncounters#cap}. NPC ships and their crews are
 * transient: they despawn with nobody near and are refused when a chunk reloads them from disk.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class LivingSea {
    public static final String MARINE = "marine", PIRATE = "pirate", HUNTER = "bounty_hunter", MERCHANT = "merchant";
    /** Entity tags: dropped when reloaded from disk; crew of an NPC ship (+ "gl_crew_of:&lt;ship uuid&gt;"); ashore on a raid. */
    public static final String TRANSIENT = "gl_sea_transient", CREW = "gl_sea_crew", CREW_OF = "gl_crew_of:", RAIDER = "gl_raider",
            RESPONDER = "gl_raid_responder", SALVAGE = "gl_salvage", YIELDED = "gl_yielded";
    /** Bounty that bounty hunters go after. */
    public static final long HUNTED = 3_000_000L;
    static final int PERIOD = 100;
    /** Quiet time around a player after a living-sea event spawned near them. */
    static final long PLAYER_GAP = 20 * 90;
    static final double SEA_CHANCE = 0.07, RAID_CHANCE = 0.015;
    static final long RAID_COOLDOWN = 24000 * 2;
    /** A beaten hulk founders this long after its crew fell (if nobody is near to claim it). */
    static final long HULK_TICKS = 300;
    static final long SALVAGE_LIFE = 24000;
    static final String SALVAGE_UNTIL = "GLSalvageUntil";

    /** Knobs for one round of spawn decisions (tests and the /ship sea command force them). */
    public static final class Rules {
        public boolean force, ignoreGap, ignoreCrowd, seaOnly, raidOnly;
        public int spawnDist = -1;
        @Nullable public String kind;
    }

    private static final Rules DEFAULT = new Rules();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    static final Map<String, Long> RAIDED = new HashMap<>();
    private static final Map<UUID, Long> SALVAGED = new LinkedHashMap<>();

    // ================================================================== spawn decisions

    public static void tick(MinecraftServer server) {
        ServerLevel l = server.overworld();
        long now = l.getGameTime();
        if (now % PERIOD != 0) return;
        expireSalvage(l, now);
        if (!GLConfig.LIVING_SEA.get() || WorldPlan.of(server) == null) return;
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer sp : l.players()) if (!sp.isSpectator() && !sp.isCreative()) ps.add(sp);
        if (!ps.isEmpty()) decide(l, ps, l.players().size(), null);
    }

    /**
     * One round of spawn decisions for these players (each may get one event); {@code playerCount} sets the
     * server-wide cap. Returns the ships that were spawned.
     */
    public static List<ShipEntity> decide(ServerLevel l, List<? extends ServerPlayer> players, int playerCount, @Nullable Rules rules) {
        Rules r = rules == null ? DEFAULT : rules;
        List<ShipEntity> out = new ArrayList<>();
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null || plan.kind != WorldPlan.Kind.OCEAN || l.dimension() != Level.OVERWORLD) return out; // not in the sky or the deep sea
        long now = l.getGameTime();
        int count = -1;
        for (ServerPlayer sp : players) {
            if (sp.level() != l) continue;
            if (!r.ignoreGap && NEXT.getOrDefault(sp.getUUID(), 0L) > now) continue;
            double roll = r.force ? 0 : l.random.nextDouble(); // rolled first: most rounds end here, before any world query
            if (roll > Math.max(SEA_CHANCE, RAID_CHANCE)) continue;
            int x = sp.getBlockX(), z = sp.getBlockZ();
            SeaZone zone = WorldLayout.zoneAt(x, z);
            if (!openZone(zone) || nearReverseMountain(x, z)) continue;
            boolean atSea = atSea(sp);
            Island town = atSea ? null : raidTown(plan, x, z);
            if (!atSea && town == null) continue;
            if (count < 0) count = SeaEncounters.countNpcShips(l);
            int room = SeaEncounters.cap(playerCount) - count;
            if (room <= 0) break;
            int dist = r.spawnDist > 0 ? r.spawnDist : spawnDist(l);
            List<ShipEntity> got = List.of();
            if (atSea && !r.raidOnly) {
                if (inHarbour(plan, x, z)) continue; // the open sea, not a town's harbour
                if (!r.ignoreCrowd && crowded(l, sp)) continue;
                if (roll > SEA_CHANCE) continue;
                got = seaEvent(l, sp.position(), zone, room, dist, r.kind, l.random);
            } else if (town != null && !r.seaOnly) {
                if (!r.force && (RAIDED.getOrDefault(town.id, 0L) > now || roll > RAID_CHANCE || Characters.get(sp).level < 3)) continue;
                ShipEntity raid = HarbourRaids.start(l, town, sp.position(), dist);
                if (raid != null) {
                    got = List.of(raid);
                    RAIDED.put(town.id, now + RAID_COOLDOWN);
                }
            }
            if (got.isEmpty()) continue;
            count += got.size();
            out.addAll(got);
            NEXT.put(sp.getUUID(), now + PLAYER_GAP);
            for (ServerPlayer o : l.players()) if (o.distanceToSqr(sp) < 300 * 300) NEXT.put(o.getUUID(), now + PLAYER_GAP);
        }
        return out;
    }

    /** Seas where ships of the living sea sail: not the Calm Belt (Sea Kings), the Red Line or the world's edge. */
    public static boolean openZone(SeaZone z) {
        return z != SeaZone.CALM_BELT && z != SeaZone.RED_LINE && z != SeaZone.WORLD_EDGE && z != SeaZone.SKY_SEA && z != SeaZone.DEEP_SEA;
    }

    static boolean nearReverseMountain(int x, int z) {
        return Math.abs(x) < 1400 && Math.abs(z) < 1400;
    }

    /** Distance from a player at which ships appear: inside the simulation distance, so they sail and fight in view. */
    static int spawnDist(ServerLevel l) {
        int sim = l.getServer().getPlayerList().getSimulationDistance();
        return Mth.clamp((sim - 1) * 16 - 30, 48, 140);
    }

    /** Aboard a ship (seat or deck) or swimming on the open sea. */
    static boolean atSea(ServerPlayer sp) {
        return sp.getVehicle() instanceof ShipEntity || SeaEncounters.isAtSea(sp) || NavalCombat.shipOf(sp) != null;
    }

    /** Standing on an island (not on a ship, not in the sea): ships do not bombard people ashore. */
    static boolean ashore(Player p) {
        WorldPlan plan = WorldPlan.forLevel(p.level());
        if (plan == null || p.isInWater() || p.getVehicle() instanceof ShipEntity) return false;
        return plan.islandAt(p.getBlockX(), p.getBlockZ()) != null && NavalCombat.shipOf(p) == null;
    }

    /** Within a settled island's harbour waters (as {@link SeaEncounters} judges it). */
    public static boolean inHarbour(WorldPlan plan, int x, int z) {
        for (Island isl : plan.candidates(x, z))
            if (isl.settlementType != com.grandline.world.settlement.SettlementType.NONE && isl.t(x, z) < 1.35) return true;
        return false;
    }

    private static boolean crowded(ServerLevel l, ServerPlayer sp) {
        return l.getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(220, 40, 220), s -> s.flag(ShipEntity.F_NPC)).size() >= 2;
    }

    /** A town a pirate crew might raid with a player in it: settled, with a dock, not a Marine/pirate stronghold or a starter island. */
    @Nullable
    static Island raidTown(WorldPlan plan, int x, int z) {
        for (Island isl : plan.around(x, z)) {
            if (!raidable(isl)) continue;
            if (isl.settlement().bounds(x, z) || isl.distance(x, z) < isl.radius * 1.15) return isl;
        }
        return null;
    }

    public static boolean raidable(Island isl) {
        return switch (isl.settlementType) {
            case HAMLET, VILLAGE, TOWN, CITY, PORT -> isl.settlement().hasDock && openZone(isl.zone) && isl.starterFor == null
                    && (isl.faction.equals("civilian") || isl.faction.equals("revolutionary"));
            default -> false;
        };
    }

    /** One event near {@code near}: a merchantman, a convoy, or a fight between NPC ships. */
    static List<ShipEntity> seaEvent(ServerLevel l, Vec3 near, SeaZone zone, int room, int dist, @Nullable String kind, RandomSource r) {
        if (kind == null) {
            int roll = r.nextInt(100);
            boolean gl = zone.isGrandLine();
            kind = roll < 40 ? "merchant" : roll < 65 ? "battle" : roll < 80 ? "plunder" : roll < 92 ? "hunt" : gl ? "convoy" : "merchant";
            if (room < 2) kind = "merchant";
        }
        return switch (kind) {
            case "battle" -> battle(l, near, zone, dist, MARINE, r);
            case "hunt" -> battle(l, near, zone, dist, HUNTER, r);
            case "plunder" -> battle(l, near, zone, dist, MERCHANT, r);
            case "convoy" -> MerchantShips.spawnNear(l, near, zone, dist, true, Math.min(room, 3), r);
            default -> MerchantShips.spawnNear(l, near, zone, dist, false, 1, r);
        };
    }

    static ShipClass classFor(SeaZone zone, RandomSource r, int bias) {
        int base = zone.isBlue() ? 1 : zone == SeaZone.NEW_WORLD ? 3 : 2; // sloop/caravel, caravel/brigantine, brigantine/galleon
        int o = Mth.clamp(base + r.nextInt(2) + bias, 1, 4);
        return ShipClass.values()[o];
    }

    private static final String[] PIRATE_NAMES = {"Black Gull", "Rusty Anchor", "Red Tide", "Sea Wolf", "Grinning Skull", "Iron Maw", "Bloody Mary Ann", "Salt Viper"};
    private static final String[] HUNTER_NAMES = {"Headhunter", "Silver Noose", "Bounty Hawk", "Iron Warrant", "Last Reward"};

    static String name(String faction, RandomSource r) {
        return switch (faction) {
            case PIRATE -> PIRATE_NAMES[r.nextInt(PIRATE_NAMES.length)];
            case HUNTER -> HUNTER_NAMES[r.nextInt(HUNTER_NAMES.length)];
            case MARINE -> "Marine Patrol";
            default -> MerchantShips.NAMES[r.nextInt(MerchantShips.NAMES.length)];
        };
    }

    /**
     * A fight already joined some way off: {@code side} (Marines, bounty hunters, or a merchantman for pirates to
     * plunder) and a pirate ship 60–75 blocks apart. Hunted pirates and plundered merchants are running.
     */
    static List<ShipEntity> battle(ServerLevel l, Vec3 near, SeaZone zone, int dist, String side, RandomSource r) {
        List<ShipEntity> out = new ArrayList<>();
        for (int tries = 0; tries < 6 && out.isEmpty(); tries++) {
            double ang = r.nextDouble() * Math.PI * 2, axis = r.nextDouble() * Math.PI * 2;
            double sep = 60 + r.nextInt(16);
            double cx = near.x + Math.cos(ang) * dist, cz = near.z + Math.sin(ang) * dist;
            int tier = Math.max(1, zone.dangerTier);
            ShipClass pc = classFor(zone, r, 0);
            ShipClass sc = side.equals(MARINE) ? classFor(zone, r, r.nextInt(3) == 0 ? 1 : 0) : side.equals(HUNTER) ? classFor(zone, r, -1) : classFor(zone, r, 0);
            Vec3 a = openWater(l, cx + Math.cos(axis) * sep / 2, cz + Math.sin(axis) * sep / 2, sc.length / 2 + 4);
            Vec3 b = a == null ? null : openWater(l, cx - Math.cos(axis) * sep / 2, cz - Math.sin(axis) * sep / 2, pc.length / 2 + 4);
            if (b == null) continue;
            float toB = yawTo(a, b), toA = yawTo(b, a);
            boolean runs = !side.equals(MARINE); // hunted pirates / plundered merchants are running, pursuers close in
            ShipEntity s, p;
            if (side.equals(MERCHANT)) {
                s = MerchantShips.spawn(l, a, toB + 180, sc, zone, null, r); // a merchant running from pirates
                p = s == null ? null : SeaEncounters.spawnShip(l, b, toA, pc, PIRATE, tier, name(PIRATE, r));
            } else {
                s = SeaEncounters.spawnShip(l, a, toB, sc, side, tier, name(side, r));
                p = s == null ? null : SeaEncounters.spawnShip(l, b, runs ? toB : toA, pc, PIRATE, tier, name(PIRATE, r));
            }
            if (s == null || p == null) {
                if (s != null) NpcShipAi.despawn(s, l);
                continue;
            }
            out.add(s);
            out.add(p);
            String key = side.equals(MARINE) ? "grandline.living_sea.battle" : side.equals(HUNTER) ? "grandline.living_sea.hunt" : "grandline.living_sea.plunder";
            announce(l, new Vec3(cx, near.y, cz), Component.translatable(key, s.shipName(), p.shipName()).withStyle(ChatFormatting.YELLOW));
        }
        return out;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    static void announce(ServerLevel l, Vec3 at, Component msg) {
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(at.x, p.getY(), at.z) < 260 * 260) p.sendSystemMessage(msg);
    }

    /**
     * Open water for a hull at (x, z): the world plan shows no land within {@code clear} blocks, the chunks there are
     * fully loaded (never waits for one) and hold open water, and no other ship lies there. Null otherwise.
     */
    @Nullable
    public static Vec3 openWater(ServerLevel l, double x, double z, int clear) {
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null) return null;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!openZone(WorldLayout.zoneAt(bx, bz))) return null;
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int dx = -clear; dx <= clear; dx += 3)
            for (int dz = -clear; dz <= clear; dz += 3) {
                plan.sample(bx + dx, bz + dz, smp);
                if (smp.height > WorldPlan.SEA_LEVEL - 3 || smp.redLine || smp.canal) return null;
            }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -clear; dx <= clear; dx += 3)
            for (int dz = -clear; dz <= clear; dz += 3) {
                if (!Chunks.ready(l, bx + dx, bz + dz)) return null;
                p.set(bx + dx, WorldPlan.SEA_LEVEL, bz + dz);
                if (!l.getBlockState(p).is(Blocks.WATER)) return null;
                p.move(0, 1, 0);
                if (!l.getBlockState(p).isAir()) return null;
            }
        if (!l.getEntitiesOfClass(ShipEntity.class, new AABB(bx - clear - 6, 40, bz - clear - 6, bx + clear + 6, 100, bz + clear + 6)).isEmpty()) return null;
        return new Vec3(bx + 0.5, WorldPlan.SEA_LEVEL + 0.9, bz + 0.5);
    }

    // ================================================================== crews

    /** Make an NPC one of a ship's crew (tags only: the ship's crew list is kept by the caller). */
    public static void enlist(ShipEntity ship, GLNpc npc) {
        npc.addTag(TRANSIENT);
        npc.addTag(CREW);
        npc.addTag(CREW_OF + ship.getUUID());
    }

    @Nullable
    static ShipEntity shipOfCrew(GLNpc npc) {
        if (!(npc.level() instanceof ServerLevel sl)) return null;
        for (String t : npc.getTags()) {
            if (!t.startsWith(CREW_OF)) continue;
            try {
                return sl.getEntity(UUID.fromString(t.substring(CREW_OF.length()))) instanceof ShipEntity s ? s : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    static boolean sameCrew(Entity a, Entity b) {
        for (String t : a.getTags()) if (t.startsWith(CREW_OF)) return b.getTags().contains(t);
        return false;
    }

    private static final Map<Integer, ShipEntity> DECK = new HashMap<>();
    private static long deckTime = -1;

    /** The ship whose deck an entity stands on (or rides), cached for the tick. */
    @Nullable
    static ShipEntity deckOf(Entity e) {
        if (e.getVehicle() instanceof ShipEntity s) return s;
        long t = e.level().getGameTime();
        if (t != deckTime) {
            DECK.clear();
            deckTime = t;
        }
        if (DECK.containsKey(e.getId())) return DECK.get(e.getId());
        ShipEntity s = NavalCombat.shipOf(e);
        DECK.put(e.getId(), s);
        return s;
    }

    /**
     * Who an NPC fights, beyond its archetype: ship crews only pick fights on the deck they stand on (they would
     * otherwise walk off into the sea after a foe on another ship) and never with their shipmates; raiders spare the
     * players who joined their raid; town guards, Marines and responders go after raiders. Null: no opinion.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        Set<String> tags = npc.getTags(), et = e.getTags();
        if (tags.isEmpty() && et.isEmpty()) return null;
        if (tags.contains(RAIDER)) {
            if (e instanceof Player p) {
                ShipEntity s = shipOfCrew(npc);
                if (s != null && s.life.raidAlly(p)) return false;
            }
            if (e instanceof GLNpc o && (o.getTags().contains(RAIDER) || sameCrew(npc, o))) return false;
            return null;
        }
        if (et.contains(RAIDER) && !tags.contains(CREW) && (Faction.isLaw(npc.faction()) || npc.role().equals("guard") || tags.contains(RESPONDER))) return true;
        if (tags.contains(CREW)) {
            if (e instanceof GLNpc o && sameCrew(npc, o)) return false;
            ShipEntity deck = deckOf(npc);
            if (deck == null) return null; // overboard / ship gone: an ordinary NPC again
            if (!(deck.carries(e) || e.getVehicle() == deck)) return false;
        }
        return null;
    }

    /** Sea folk never turn on their own side (alerts from a shipmate hit by friendly fire, raiders alerted by townsfolk). */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide || !(e.getNewAboutToBeSetTarget() instanceof GLNpc t)) return;
        Set<String> tags = npc.getTags();
        if (!tags.contains(CREW) && !tags.contains(RAIDER)) return;
        if (sameCrew(npc, t) || (t.faction() == npc.faction() && (t.getTags().contains(CREW) || t.getTags().contains(RAIDER)))) e.setCanceled(true);
    }

    /** Players striking an NPC ship's crew (or a raider) count as attacking that ship. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (!npc.getTags().contains(CREW)) return;
        ShipEntity ship = shipOfCrew(npc);
        if (ship == null) return;
        if (npc.getTags().contains(RAIDER)) HarbourRaids.foughtBy(ship, sp);
        else attackedByPlayer(ship, sp, sp);
    }

    /** Ships, crews and raiders of the living sea are not restored from disk; neither is salvage that has rotted. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.loadedFromDisk() || e.getLevel().isClientSide) return;
        Entity en = e.getEntity();
        Set<String> tags = en.getTags();
        if (tags.isEmpty()) return;
        if (tags.contains(TRANSIENT)) e.setCanceled(true);
        else if (tags.contains(SALVAGE) && en.getPersistentData().getLong(SALVAGE_UNTIL) < e.getLevel().getGameTime()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        NEXT.clear();
        RAIDED.clear();
        SALVAGED.clear();
    }

    /**
     * Keep a ship's crew aboard: hands who fell or walked overboard climb back on deck, targets off the deck are
     * dropped (gun crews fight ship to ship), boarders whose prize is beaten or sinking leap home.
     */
    static void deckDiscipline(ShipEntity ship, ServerLevel sl) {
        NavalCombat.State n = ship.naval;
        if (n.crew.isEmpty() || ship.isSinking()) return;
        for (UUID u : n.crew) {
            if (!(sl.getEntity(u) instanceof GLNpc m) || !m.isAlive() || m.isPassenger()) continue;
            if (m.getTags().contains(RAIDER) || leaping(n, m)) continue;
            Integer boarded = n.boarders.get(m.getId());
            if (boarded != null) {
                Entity b = sl.getEntity(boarded);
                boolean fight = b instanceof ShipEntity bs && bs.isAlive() && !bs.isSinking() && (!bs.naval.crewGone || (m.getTarget() != null && m.getTarget().isAlive()));
                if (fight) continue;
                n.boarders.remove(m.getId());
                m.removeTag("gl_boarder");
                m.setTarget(null);
                leapHome(ship, m);
                continue;
            }
            if (ship.carries(m)) {
                LivingEntity t = m.getTarget();
                if (t != null && !(ship.carries(t) || t.getVehicle() == ship) && m.distanceToSqr(t) > 9) {
                    m.setTarget(null);
                    m.getNavigation().stop();
                }
                continue;
            }
            ShipEntity other = NavalCombat.shipOf(m);
            if (other != null && other != ship) {
                boolean enemy = !other.flag(ShipEntity.F_NPC) || !other.npcFaction.equals(ship.npcFaction);
                if (enemy && !other.naval.crewGone && !other.isSinking()) {
                    n.boarders.put(m.getId(), other.getId()); // fighting on the enemy's deck
                    m.addTag("gl_boarder");
                } else leapHome(ship, m);
                continue;
            }
            if (m.distanceToSqr(ship) < 48 * 48 && !m.hasEffect(ModEffects.KNOCKED_OUT)) {
                Vec3 deck = deckSpot(ship, m.getRandom());
                m.stopRiding();
                m.teleportTo(deck.x, deck.y, deck.z);
                m.setDeltaMovement(Vec3.ZERO);
                m.fallDistance = 0;
                m.getNavigation().stop();
            }
        }
    }

    private static boolean leaping(NavalCombat.State n, GLNpc m) {
        for (NavalCombat.Leap l : n.leaps) if (l.npc() == m.getId()) return true;
        return false;
    }

    static void leapHome(ShipEntity ship, GLNpc m) {
        if (m.distanceToSqr(ship) > 60 * 60) return;
        Vec3 deck = deckSpot(ship, m.getRandom());
        ship.naval.leaps.add(new NavalCombat.Leap(m.getId(), ship.getId(), m.position(), ship.toLocal(deck), new int[]{0}, false));
        m.getNavigation().stop();
    }

    /** A random spot on the main deck (world position). */
    static Vec3 deckSpot(ShipEntity ship, RandomSource r) {
        double hb = Math.max(0.3, ship.cls.halfBeamAt(0.5f) - 1.2);
        double lz = (r.nextDouble() * 0.4 - 0.25) * ship.cls.length;
        return ship.toWorld((r.nextDouble() * 2 - 1) * hb, ship.cls.freeboard + 0.05, lz);
    }

    // ================================================================== behaviour (called from NpcShipAi)

    /** Behaviour that replaces the plain chase: merchants, raids, running from a stronger ship. True when handled. */
    static boolean override(ShipEntity ship, ServerLevel sl, @Nullable Entity target, long now) {
        if (ship.npcFaction.equals(MERCHANT)) return MerchantShips.tick(ship, sl, target, now);
        if (!ship.life.raidIsland.isEmpty() && HarbourRaids.tickShip(ship, sl, target, now)) return true;
        if (target == null && ship.life.fleeFrom >= 0 && sl.getEntity(ship.life.fleeFrom) instanceof ShipEntity from) {
            runFrom(ship, sl, from.position());
            return true;
        }
        return false;
    }

    /** Crowd on sail away from a point, still rounding islands on the way. */
    static void runFrom(ShipEntity ship, ServerLevel sl, Vec3 from) {
        Vec3 away = ship.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1) away = ship.heading();
        Vec3 goal = ship.position().add(away.normalize().scale(240));
        ship.setAnchored(false);
        ship.setSail(3);
        com.grandline.navigation.Ferries.helm(ship, sl, goal, "");
    }

    static int merchantTarget(ShipEntity ship, ServerLevel sl) {
        return MerchantShips.pickTarget(ship, sl);
    }

    /**
     * How NPC ship {@code ship} regards NPC ship {@code s}: 1 engage, 0 leave alone, -1 run from it. Marines and
     * bounty hunters engage pirates; pirates plunder merchants and take on Marines or hunters only when their side
     * is at least 80% as strong; anyone fights back against a ship that attacked it (or its convoy).
     */
    static int stance(ShipEntity ship, ShipEntity s, @Nullable ShipEntity lead) {
        if (NavalCombat.attackedBy(ship, s) || (lead != null && escortsDefend(lead, s))) return 1;
        String b = s.npcFaction;
        return switch (ship.npcFaction) {
            case MARINE, HUNTER -> b.equals(PIRATE) ? 1 : 0;
            case PIRATE -> b.equals(MERCHANT) ? (s.life.yielded ? 0 : 1)
                    : (b.equals(MARINE) || b.equals(HUNTER)) ? (groupStrength(ship) >= groupStrength(s) * 0.8f ? 1 : -1) : 0;
            default -> 0;
        };
    }

    /** Two NPC ships of one side: the same faction, or a convoy's merchantman and her escorts. */
    static boolean allied(ShipEntity a, ShipEntity b) {
        if (!a.flag(ShipEntity.F_NPC) || !b.flag(ShipEntity.F_NPC)) return false;
        return a.npcFaction.equals(b.npcFaction) || a.shipId.equals(b.naval.leader) || b.shipId.equals(a.naval.leader);
    }

    /** Escorts take on whoever attacked the merchantman they guard (her aggressor ships, or ships of players who wronged her). */
    static boolean escortsDefend(ShipEntity lead, ShipEntity s) {
        if (!lead.npcFaction.equals(MERCHANT)) return false;
        if (NavalCombat.attackedBy(lead, s) || lead.naval.targetId == s.getId() || lead.life.threatId == s.getId()) return true;
        return s.owner != null && lead.life.wronged.contains(s.owner);
    }

    /** Fighting strength: hull and guns, scaled by crew still standing and the hull's condition. */
    static float strength(ShipEntity s) {
        int crew = s.flag(ShipEntity.F_NPC) ? aliveCrew(s) : 4;
        return (s.cls.hull / 100f + s.cls.cannons) * (0.6f + 0.08f * crew) * Math.max(0.2f, s.hull() / Math.max(1f, s.maxHull()));
    }

    /** A ship and the NPC ships of its faction within 120 blocks. */
    static float groupStrength(ShipEntity s) {
        float sum = strength(s);
        if (!s.flag(ShipEntity.F_NPC)) return sum;
        for (ShipEntity o : s.level().getEntitiesOfClass(ShipEntity.class, s.getBoundingBox().inflate(120, 30, 120),
                o -> o != s && o.flag(ShipEntity.F_NPC) && !o.isSinking() && !o.naval.crewGone && o.npcFaction.equals(s.npcFaction))) sum += strength(o);
        return sum;
    }

    static int aliveCrew(ShipEntity s) {
        if (!(s.level() instanceof ServerLevel sl)) return 0;
        int n = 0;
        for (UUID u : s.naval.crew)
            if (sl.getEntity(u) instanceof GLNpc m && m.isAlive() && !m.flag(GLNpc.FLAG_SURRENDER) && !m.hasEffect(ModEffects.KNOCKED_OUT)) n++;
        return n;
    }

    /**
     * A beaten NPC ship drifts. A merchant that struck her colours to a player recovers and sails on once left
     * alone; any other hulk founders after {@link #HULK_TICKS} unless a player is close enough to claim her.
     */
    static void hulk(ShipEntity ship, ServerLevel sl, long now) {
        SeaLife life = ship.life;
        if (!life.raidIsland.isEmpty() && life.raidPhase == HarbourRaids.ASHORE) { // her raiders are still ashore
            HarbourRaids.tickShip(ship, sl, null, now);
            return;
        }
        if (life.beatenAt == 0) life.beatenAt = now;
        if (now - life.beatenAt < HULK_TICKS || now % 20 != Math.floorMod(ship.getId(), 20)) return;
        for (Player p : sl.players()) if (p.distanceToSqr(ship) < 48 * 48) return;
        if (life.yielded && MerchantShips.spare(ship, sl, now)) return;
        ship.hurtHull(ship.hull() + 1, null); // scuttled by the victors / founders
    }

    // ================================================================== consequences

    /** A blow on an NPC ship's hull from {@code by} (a player, a gunner, a shot's owner). */
    static void onHullHit(ShipEntity ship, Entity by) {
        if (by instanceof ServerPlayer sp) attackedByPlayer(ship, sp, sp);
        else if (by instanceof GLNpc g && g.getVehicle() instanceof ShipEntity gs && gs != ship && gs.owner != null && ship.level() instanceof ServerLevel sl
                && sl.getServer().getPlayerList().getPlayer(gs.owner) instanceof ServerPlayer owner) attackedByPlayer(ship, owner, gs); // a player's gun crew
    }

    /** {@code by} fired on, rammed or hooked {@code victim}: a player's ship attacking an NPC ship is its captain's deed. */
    static void onAggression(ShipEntity victim, ShipEntity by) {
        if (!victim.flag(ShipEntity.F_NPC) || by.owner == null || by.flag(ShipEntity.F_NPC) || !(victim.level() instanceof ServerLevel sl)) return;
        if (sl.getServer().getPlayerList().getPlayer(by.owner) instanceof ServerPlayer owner) attackedByPlayer(victim, owner, by);
    }

    /** A player (or their ship {@code via}) attacked an NPC ship or its crew: they fought it; the first time, it is a crime. */
    public static void attackedByPlayer(ShipEntity ship, ServerPlayer sp, Entity via) {
        if (!ship.flag(ShipEntity.F_NPC) || !(ship.level() instanceof ServerLevel sl)) return;
        ship.life.fought.put(sp.getUUID(), sl.getGameTime());
        ship.life.who.put(sp.getUUID(), sp);
        if (ship.life.raidAlly(sp)) return;
        if (!ship.life.wronged.add(sp.getUUID())) {
            if (ship.npcFaction.equals(MERCHANT)) MerchantShips.attacked(ship, sl, sp, via);
            return;
        }
        CharacterData d = Characters.get(sp);
        int size = ship.cls.ordinal() + 1;
        switch (ship.npcFaction) {
            case MERCHANT -> {
                SeaZone z = WorldLayout.zoneAt(ship.getBlockX(), ship.getBlockZ());
                Bounties.raise(sp, d, 1_000_000L * size * (z.isGrandLine() ? 2 : 1), Component.translatable("grandline.living_sea.crime.piracy"));
                d.addReputation(Faction.CIVILIAN.id(), -25);
                d.addReputation(Faction.MARINE.id(), -10);
                sp.sendSystemMessage(Component.translatable("grandline.living_sea.piracy", ship.shipName()).withStyle(ChatFormatting.RED));
                for (ShipEntity e : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(200, 30, 200), e -> ship.shipId.equals(e.naval.leader)))
                    e.life.wronged.add(sp.getUUID()); // the convoy's escorts turn on the pirate
                MerchantShips.attacked(ship, sl, sp, via);
            }
            case MARINE -> {
                Bounties.raise(sp, d, 2_000_000L * size, Component.translatable("grandline.living_sea.crime.marines"));
                d.addReputation(Faction.MARINE.id(), -30);
                sp.sendSystemMessage(Component.translatable("grandline.living_sea.attacked_marines", ship.shipName()).withStyle(ChatFormatting.RED));
            }
            case HUNTER -> d.addReputation(Faction.BOUNTY_HUNTER.id(), -20);
            default -> {}
        }
        CharacterManager.markDirty(sp);
    }

    /** The crew of an NPC ship has been beaten (captureCheck): reward those who fought it. */
    static void onCrewBeaten(ShipEntity ship) {
        if (ship.life.beatenAt == 0) ship.life.beatenAt = ship.level().getGameTime();
        defeated(ship);
    }

    /** An NPC ship has started to go down: rewards, then her cargo floats free. */
    static void onSinking(ShipEntity ship) {
        if (!(ship.level() instanceof ServerLevel sl)) return;
        com.grandline.quest.story.SeaJobs.onShipSinking(sl, ship); // sea jobs: a named pirate ship sunk, a convoy lost
        defeated(ship);
        // survivors and boarders are ordinary folk again (they despawn by the usual rules once nobody is near)
        for (UUID u : ship.naval.crew) if (sl.getEntity(u) instanceof GLNpc m) m.removeTag(CREW);
        if (ship.life.salvaged) return;
        ship.life.salvaged = true;
        String table = switch (ship.npcFaction) {
            case MARINE -> "chests/marine";
            case MERCHANT -> "chests/warehouse";
            default -> "chests/wreck";
        };
        ChestBoat cb = salvage(sl, ship, table);
        if (cb != null) announce(sl, ship.position(), Component.translatable("grandline.living_sea.salvage", ship.shipName()).withStyle(ChatFormatting.AQUA));
    }

    /** Players who fought a beaten ship in the last two minutes earn the standing that goes with the side they took. */
    static void defeated(ShipEntity ship) {
        if (ship.life.defeated || !(ship.level() instanceof ServerLevel sl)) return;
        ship.life.defeated = true;
        long now = sl.getGameTime();
        int size = ship.cls.ordinal() + 1;
        for (var en : ship.life.fought.entrySet()) {
            if (now - en.getValue() > 2400) continue;
            ServerPlayer sp = resolve(sl, en.getKey(), ship.life);
            if (sp == null) continue;
            if (!ship.life.raidAlly(sp)) com.grandline.quest.QuestEvents.onSeaBattleWon(sp, ship.npcFaction); // "sea_battle" quest objectives
            CharacterData d = Characters.get(sp);
            switch (ship.npcFaction) {
                case PIRATE -> {
                    if (ship.life.raidAlly(sp)) break;
                    d.addReputation(Faction.MARINE.id(), 10 + 5 * size);
                    d.addReputation(Faction.CIVILIAN.id(), 5);
                    long reward = 1500L * size * Math.max(1, WorldLayout.zoneAt(ship.getBlockX(), ship.getBlockZ()).dangerTier);
                    d.beli += reward;
                    sp.sendSystemMessage(Component.translatable("grandline.living_sea.pirates_beaten", ship.shipName(), reward).withStyle(ChatFormatting.GOLD));
                }
                case MARINE -> {
                    d.addReputation(Faction.PIRATE.id(), 10 + 5 * size);
                    sp.sendSystemMessage(Component.translatable("grandline.living_sea.marines_beaten", ship.shipName()).withStyle(ChatFormatting.GOLD));
                }
                case HUNTER -> d.addReputation(Faction.PIRATE.id(), 5 + 2 * size);
                default -> {}
            }
            com.grandline.career.Careers.onShipDefeated(sp, ship.npcFaction, size); // careers: sea battles won
            CharacterManager.markDirty(sp);
        }
    }

    /** The player behind a uuid that fought a ship: online by uuid, else the one remembered (if still in the world). */
    @Nullable
    static ServerPlayer resolve(ServerLevel sl, UUID u, SeaLife life) {
        ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(u);
        if (sp != null) return sp;
        ServerPlayer ref = life.who.get(u);
        return ref != null && !ref.isRemoved() ? ref : null;
    }

    /** A salvage boat beside a wreck (sneak-use to open; its loot is rolled when first opened) and floating wreckage. */
    @Nullable
    public static ChestBoat salvage(ServerLevel l, ShipEntity ship, String table) {
        ChestBoat cb = EntityType.CHEST_BOAT.create(l);
        if (cb == null) return null;
        Vec3 p = ship.toWorld(ship.cls.halfBeamAt(0.5f) + 3, 0, 0);
        cb.setVariant(Boat.Type.DARK_OAK);
        cb.moveTo(p.x, WorldPlan.SEA_LEVEL + 1.0, p.z, l.random.nextFloat() * 360, 0);
        cb.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, table)));
        cb.setLootTableSeed(l.random.nextLong());
        cb.setCustomName(Component.translatable("grandline.living_sea.salvage_name", ship.shipName()));
        cb.addTag(SALVAGE);
        cb.getPersistentData().putLong(SALVAGE_UNTIL, l.getGameTime() + SALVAGE_LIFE);
        l.addFreshEntity(cb);
        SALVAGED.put(cb.getUUID(), l.getGameTime() + SALVAGE_LIFE);
        for (int i = 0; i < 3; i++) {
            Vec3 w = ship.toWorld((l.random.nextDouble() - 0.5) * ship.cls.beam * 2, 0.5, (l.random.nextDouble() - 0.5) * ship.cls.length);
            ItemEntity it = new ItemEntity(l, w.x, WorldPlan.SEA_LEVEL + 1.2, w.z, new ItemStack(i == 0 ? Items.BARREL : Items.OAK_PLANKS, 1 + l.random.nextInt(4)));
            it.setDeltaMovement((l.random.nextDouble() - 0.5) * 0.1, 0.1, (l.random.nextDouble() - 0.5) * 0.1);
            l.addFreshEntity(it);
        }
        return cb;
    }

    /** Salvage nobody took rots away after a day (when nobody is right beside it). */
    private static void expireSalvage(ServerLevel l, long now) {
        if (SALVAGED.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = SALVAGED.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            if (en.getValue() > now) continue;
            Entity e = l.getEntity(en.getKey());
            if (e == null) { // unloaded (refused on reload once rotten) or already gone
                it.remove();
                continue;
            }
            if (l.getNearestPlayer(e, 24) != null) continue;
            e.discard();
            it.remove();
        }
    }

    // ================================================================== read-only views (self-tests, /ship sea status)

    /** Entity id of the ship's current target (-1 none, -2 to be chosen). */
    public static int targetOf(ShipEntity ship) {
        return ship.naval.targetId;
    }

    /** True when the NPC ship's crew is beaten. */
    public static boolean beaten(ShipEntity ship) {
        return ship.naval.crewGone;
    }

    /** The ship id of the flagship (or convoy merchant) this ship keeps station on, or null. */
    @Nullable
    public static UUID leaderOf(ShipEntity ship) {
        return ship.naval.leader;
    }

    // ================================================================== dialogue (harbour raids)

    public static void addOptions(ServerPlayer sp, GLNpc npc, ListTag opts) {
        if (HarbourRaids.mayJoin(sp, npc)) opts.add(StringTag.valueOf("join_raid"));
    }

    public static boolean choose(ServerPlayer sp, GLNpc npc, String option) {
        if (!option.equals("join_raid")) return false;
        HarbourRaids.join(sp, npc);
        return true;
    }

    // ================================================================== /ship sea (operators)

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("ship").then(Commands.literal("sea").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(LivingSea::status))
                .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("merchant", "battle", "hunt", "plunder", "convoy", "raid"), b))
                        .executes(c -> spawnCmd(c, StringArgumentType.getString(c, "event"))))));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        ServerLevel l = c.getSource().getServer().overworld();
        int n = SeaEncounters.countNpcShips(l);
        StringBuilder sb = new StringBuilder("NPC ships " + n + "/" + SeaEncounters.cap(l.players().size()));
        Vec3 at = c.getSource().getPosition();
        for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, new AABB(at, at).inflate(320), s -> s.flag(ShipEntity.F_NPC)))
            sb.append("\n ").append(s.shipName()).append(" (").append(s.npcFaction).append(", ").append(s.cls.id()).append(") ")
                    .append((int) Math.sqrt(s.distanceToSqr(at))).append("m hull ").append((int) s.hull())
                    .append(s.naval.crewGone ? " beaten" : "").append(s.life.route.isEmpty() ? "" : " -> " + s.life.route)
                    .append(s.life.raidIsland.isEmpty() ? "" : " raiding " + s.life.raidIsland + " phase " + s.life.raidPhase);
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return n;
    }

    private static int spawnCmd(CommandContext<CommandSourceStack> c, String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel l = sp.serverLevel();
        List<ShipEntity> got;
        if (kind.equals("raid")) {
            WorldPlan plan = WorldPlan.forLevel(l);
            Island isl = plan == null ? null : plan.nearest(sp.getBlockX(), sp.getBlockZ(), 600);
            ShipEntity s = isl == null || !isl.settlement().hasDock ? null : HarbourRaids.start(l, isl, sp.position(), spawnDist(l));
            got = s == null ? List.of() : List.of(s);
        } else got = seaEvent(l, sp.position(), WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ()), 3, spawnDist(l), kind, l.random);
        if (got.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Nothing spawned (no open water in loaded chunks, or no harbour near)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + got.stream().map(s -> s.shipName() + " (" + s.npcFaction + ")").toList()), true);
        return got.size();
    }

    private LivingSea() {}
}
