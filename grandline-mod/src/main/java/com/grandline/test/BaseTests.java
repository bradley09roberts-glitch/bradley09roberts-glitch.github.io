package com.grandline.test;

import com.grandline.activity.ActivityItems;
import com.grandline.activity.Meals;
import com.grandline.base.BaseHands;
import com.grandline.base.BaseRegistry;
import com.grandline.base.BaseUpgrades;
import com.grandline.base.CrewBases;
import com.grandline.base.CrewBases.Claim;
import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.navigation.Navigation;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEntities;
import com.grandline.ship.BaseRaids;
import com.grandline.ship.LivingSea;
import com.grandline.ship.SeaEncounters;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.IslandShape;
import com.grandline.world.SkyWorld;
import com.grandline.world.TownProtection;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.SettlementType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.grandline.test.SelfTest.check;

/**
 * Self-test steps for crew bases (added to phase 1 by {@link SelfTest}; quick run: GL_SELFTEST=bases).
 * <ul>
 *   <li>Claiming: refused on a canon island, a settled island, at a story place, on the shore, in the sky and with a
 *   hostile near; planting the (anvil-named) flag item at the heart of an uninhabited original island claims it,
 *   announced, on everyone's sea chart; one claim per crew (a crewmate, a transfer), an island held by one crew only.</li>
 *   <li>Building: the real block events: the crew may break and place, an outsider may not (nor open a container), the
 *   flag stands, outside the claim nothing changes, and town protection is unchanged (a base's crew still cannot
 *   break a town).</li>
 *   <li>The warehouse is shared by the crew and closed to outsiders; upgrades cost Beli and materials (pack and
 *   warehouse) and apply: dojo (+50% style/Haki training inside only), galley (+1 dish quality inside only), medical
 *   bay, slip (ship upgrade at 75%), quarters and hands (hire, cap, peaceful to the crew, a death costs a hand).</li>
 *   <li>The dock repairs the crew's ships for free (not an outsider's).</li>
 *   <li>Raids: none without room under the NPC ship cap; one ship within the cap; it sails in and lands at most six
 *   raiders who fight the crew and hands (and they them); beaten, the raid is repelled and the fighters paid; with a
 *   watchtower the crew is warned and raiders glow; undefended, the flag is taken (announced), retaken within the
 *   grace period, taken again, and when the grace runs out the claim is removed and the raiders carry off goods.</li>
 *   <li>Transfer and abandon; a claim with upgrades, goods and a hand is left for phase 2, which finds it after the
 *   restart.</li>
 * </ul>
 * Players are fake players outside the player list (counted as online through {@link CrewBases#testPlayers}).
 */
final class BaseTests {
    private static final Map<UUID, List<String>> INBOX = new HashMap<>();
    private static final List<String> NEWS = new ArrayList<>();
    private static final Set<Long> FORCED = new HashSet<>(), TICKETS = new HashSet<>();
    private static final String FIXTURE = "selftest-bases.txt";

    private static FakePlayer A, C, B, D;
    private static Island i1, i2;
    private static BlockPos heart1, heart2, chest;
    private static Claim c1;
    private static BaseRaids.Raid raid;
    private static ShipEntity shipA, shipB;
    private static final int[] waited = {0};
    private static final long[] num = new long[4];

    // ================================================================== helpers

    private static FakePlayer fp(ServerLevel l, String name) {
        return FakePlayerFactory.get(l, new GameProfile(UUID.nameUUIDFromBytes(("gl-bases-" + name).getBytes(StandardCharsets.UTF_8)), name));
    }

    private static void character(FakePlayer p, String name, Faction f) {
        CharacterData d = Characters.get(p);
        d.created = true;
        d.name = name;
        d.faction = f;
        d.beli = 100_000;
        d.bounty = 0;
        d.crewId = null;
        d.knockdownTicks = 0;
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : c.getString();
    }

    private static boolean got(FakePlayer p, String key) {
        return INBOX.getOrDefault(p.getUUID(), List.of()).contains(key);
    }

    private static void force(ServerLevel l, int cx, int cz) {
        if (FORCED.add(ChunkPos.asLong(cx, cz))) l.setChunkForced(cx, cz, true);
    }

    /** Keep chunks around a point entity-ticking and generate them now. */
    private static void loadNow(ServerLevel l, double x, double z, int r) {
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                force(l, cx + dx, cz + dz);
                l.getChunk(cx + dx, cz + dz);
            }
    }

    private static void keep(ServerLevel l, Entity e) {
        if (e == null || e.isRemoved()) return;
        ChunkPos cp = e.chunkPosition();
        if (TICKETS.add(cp.toLong())) l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
        force(l, cp.x, cp.z);
    }

    private static void release(ServerLevel l) {
        for (long c : FORCED) l.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        for (long c : TICKETS) {
            ChunkPos cp = new ChunkPos(c);
            l.getChunkSource().removeRegionTicket(TicketType.FORCED, cp, 3, cp);
        }
        FORCED.clear();
        TICKETS.clear();
    }

    private static int ground(ServerLevel l, int x, int z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Solid ground and three free blocks above (x, z); returns the free block. */
    private static BlockPos prepare(ServerLevel l, int x, int z) {
        int y = Math.max(WorldPlan.SEA_LEVEL + 2, ground(l, x, z));
        BlockPos at = new BlockPos(x, y, z);
        if (!l.getBlockState(at.below()).isSolid()) l.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 3);
        for (int k = 0; k < 4; k++) l.setBlock(at.above(k), Blocks.AIR.defaultBlockState(), 3);
        return at;
    }

    /** A spot at the heart of an island, well above the sea, away from its story places (null if none). */
    private static BlockPos heartSpot(MinecraftServer s, WorldPlan plan, Island isl) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        int span = (int) (isl.radius * 0.5);
        for (int dx = -span; dx <= span; dx += 6)
            for (int dz = -span; dz <= span; dz += 6) {
                int x = isl.x + dx, z = isl.z + dz;
                double h = isl.naturalHeight(x, z);
                if (Double.isNaN(h) || h < WorldPlan.SEA_LEVEL + 3 || isl.t(x, z) > 0.55 || plan.islandAt(x, z) != isl) continue;
                if (CrewBases.storyPlaceNear(s, plan, isl, x, z, 14) != null) continue;
                double slope = Math.abs(isl.naturalHeight(x + 4, z) - h) + Math.abs(isl.naturalHeight(x, z + 4) - h);
                if (slope > 4) continue;
                double d = dx * dx + dz * dz;
                if (d < bd) {
                    bd = d;
                    best = new BlockPos(x, 0, z);
                }
            }
        return best;
    }

    /** Hostiles near a spot are dealt with (the player "clears the threat"). */
    private static int clearThreats(ServerLevel l, ServerPlayer p, BlockPos at) {
        int n = 0;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(30, 16, 30), e -> e.isAlive()
                && ((e instanceof GLNpc g && g.isHostileTo(p)) || e instanceof Enemy))) {
            e.discard();
            n++;
        }
        return n;
    }

    private static boolean breakCancelled(ServerLevel l, BlockPos pos, ServerPlayer p) {
        BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(l, pos, l.getBlockState(pos), p);
        NeoForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }

    private static boolean placeCancelled(ServerLevel l, BlockPos pos, ServerPlayer p) {
        BlockEvent.EntityPlaceEvent e = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(l.dimension(), l, pos), Blocks.DIRT.defaultBlockState(), p);
        NeoForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }

    private static boolean useCancelled(BlockPos pos, ServerPlayer p) {
        PlayerInteractEvent.RightClickBlock e = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        NeoForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }

    private static boolean chartHas(ServerPlayer viewer, String island, String crew) {
        CompoundTag t = Navigation.chartData(viewer);
        if (t == null) return false;
        ListTag l = t.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            if (e.getString("island").equals(island) && (crew == null || e.getString("crew").equals(crew))) return true;
        }
        return false;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static List<GLNpc> raiders(ServerLevel l, BaseRaids.Raid r) {
        List<GLNpc> out = new ArrayList<>();
        for (UUID u : r.raiders) if (l.getEntity(u) instanceof GLNpc m && m.isAlive()) out.add(m);
        return out;
    }

    /** Keep the raiders at (or away from) the flag while a test waits on the capture rules. */
    private static void pin(ServerLevel l, BaseRaids.Raid r, BlockPos flag, int dist) {
        int k = 0;
        for (GLNpc m : raiders(l, r)) {
            double ang = (k++) * 1.3;
            BlockPos want = flag.offset((int) Math.round(Math.cos(ang) * dist), 0, (int) Math.round(Math.sin(ang) * dist));
            if (m.position().distanceTo(Vec3.atBottomCenterOf(want)) < 2.5) continue;
            BlockPos spot = NotableSpawner.safeSpotNear(l, new BlockPos(want.getX(), ground(l, want.getX(), want.getZ()), want.getZ()));
            if (spot == null) spot = new BlockPos(want.getX(), ground(l, want.getX(), want.getZ()), want.getZ());
            m.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            m.getNavigation().stop();
            m.setTarget(null);
        }
    }

    private static void far(FakePlayer p) {
        p.moveTo(heart1.getX() + 500, 120, heart1.getZ() + 500);
    }

    // ================================================================== phase 1

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- players, crews, islands ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            A = fp(l, "BaseCaptain");
            C = fp(l, "BaseMate");
            B = fp(l, "BaseRival");
            D = fp(l, "BaseHeir");
            character(A, "Rook", Faction.PIRATE);
            character(C, "Wren", Faction.PIRATE);
            character(B, "Maddox", Faction.PIRATE);
            character(D, "Ilse", Faction.CIVILIAN);
            Characters.get(A).crewId = Characters.get(A).characterId; // A founds a crew and C sails in it
            Characters.get(C).crewId = Characters.get(A).characterId;
            INBOX.clear();
            NEWS.clear();
            CrewBases.testPlayers = () -> List.of(A, C, B, D);
            CrewBases.tap = (p, m) -> INBOX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(key(m));
            CrewBases.announced = m -> NEWS.add(key(m));
            WorldPlan plan = WorldPlan.of(s);
            Island home = plan.byId("shiokaze_isle");
            List<Island> cands = new ArrayList<>();
            for (Island isl : plan.islands)
                if (CrewBases.claimable(isl) && LivingSea.openZone(isl.zone) && isl.radius >= 150 && isl.radius <= 600 && switch (isl.shape) {
                    case RING, ATOLL, ARCHIPELAGO, SEA, CRATER, SPIRES -> false;
                    default -> true;
                }) cands.add(isl);
            cands.sort(Comparator.comparingDouble(i -> i.distance(home.x, home.z)));
            for (Island isl : cands) {
                BlockPos h = heartSpot(s, plan, isl);
                if (h == null) continue;
                if (i1 == null) {
                    i1 = isl;
                    heart1 = h;
                } else {
                    i2 = isl;
                    heart2 = h;
                    break;
                }
            }
            long eligible = plan.islands.stream().filter(CrewBases::claimable).count();
            check("bases: uninhabited original islands to claim", i1 != null && i2 != null,
                    eligible + " claimable islands of " + plan.islands.size() + "; picked " + (i1 == null ? "-" : i1.id + " (" + i1.shape + ", r" + i1.radius + ")")
                            + " and " + (i2 == null ? "-" : i2.id));
            if (i1 == null || i2 == null) {
                steps.clear();
                return 0;
            }
            loadNow(l, heart1.getX(), heart1.getZ(), 5);
            loadNow(l, heart2.getX(), heart2.getZ(), 2);
            heart1 = prepare(l, heart1.getX(), heart1.getZ());
            heart2 = prepare(l, heart2.getX(), heart2.getZ());
            return 1;
        });

        // ---------- where a flag may not go ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan plan = WorldPlan.of(s);
            Island dawn = plan.byId("dawn_island");
            CrewBases.Refusal canon = CrewBases.check(A, new BlockPos(dawn.x, 80, dawn.z));
            Island settled = null;
            for (Island isl : plan.islands)
                if (!isl.canon && isl.settlementType == SettlementType.VILLAGE && plan.islandAt(isl.x, isl.z) == isl && !Double.isNaN(isl.naturalHeight(isl.x, isl.z))
                        && isl.naturalHeight(isl.x, isl.z) > WorldPlan.SEA_LEVEL + 1) {
                    settled = isl;
                    break;
                }
            CrewBases.Refusal town = settled == null ? null : CrewBases.check(A, new BlockPos(settled.x, 80, settled.z));
            // a story place at the heart of a claimable island
            String storyAt = "none found";
            CrewBases.Refusal story = null;
            search:
            for (Island isl : plan.islands) {
                if (!CrewBases.claimable(isl)) continue;
                for (int[] p : CrewBases.storyPlaces(s, isl)) {
                    if (!CrewBases.heart(isl, p[0], p[1]) || plan.islandAt(p[0], p[1]) != isl || isl.naturalHeight(p[0], p[1]) < WorldPlan.SEA_LEVEL + 1) continue;
                    story = CrewBases.check(A, new BlockPos(p[0], 80, p[1]));
                    storyAt = isl.id + " @" + p[0] + "," + p[1];
                    break search;
                }
            }
            // the shore of the first island
            CrewBases.Refusal shore = null;
            for (int d = (int) (i1.radius * 0.7); d < i1.radius * 1.3 && shore == null; d += 3) {
                int x = i1.x + d, z = i1.z;
                if (plan.islandAt(x, z) == i1 && i1.t(x, z) > 0.8 && i1.t(x, z) < 0.98) shore = CrewBases.check(A, new BlockPos(x, 80, z));
            }
            // the sky
            FakePlayer sky = FakePlayerFactory.get(s.getLevel(SkyWorld.LEVEL), new GameProfile(UUID.nameUUIDFromBytes("gl-bases-sky".getBytes(StandardCharsets.UTF_8)), "BaseSky"));
            character(sky, "Aloft", Faction.PIRATE);
            CrewBases.Refusal skyR = CrewBases.check(sky, new BlockPos(0, 120, 0));
            check("bases: no flag on a canon island, a settled island, a story place, the shore or the sky",
                    canon != null && canon.key().endsWith(".canon") && town != null && town.key().endsWith(".settled") && story != null && story.key().endsWith(".story")
                            && shore != null && shore.key().endsWith(".not_heart") && skyR != null && skyR.key().endsWith(".wrong_world"),
                    "canon " + (canon == null ? "-" : canon.key()) + ", " + (settled == null ? "no village" : settled.id) + " " + (town == null ? "-" : town.key())
                            + ", story (" + storyAt + ") " + (story == null ? "-" : story.key()) + ", shore " + (shore == null ? "-" : shore.key()) + ", sky " + (skyR == null ? "-" : skyR.key()));
            // a threat at the heart: refused until it is dealt with
            clearThreats(l, A, heart1);
            HumanoidNpc bandit = SelfTest.npc(l, "bandit", 1, heart1.offset(5, 0, 0));
            CrewBases.Refusal threat = CrewBases.check(A, heart1);
            bandit.discard();
            CrewBases.Refusal clear = CrewBases.check(A, heart1);
            check("bases: a hostile near the heart must be dealt with first", threat != null && threat.key().endsWith(".threat") && clear == null,
                    "with a bandit " + (threat == null ? "allowed" : threat.key()) + ", without " + (clear == null ? "allowed" : clear.key()));
            return 0;
        });

        // ---------- planting the flag claims the island ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            ItemStack flag = new ItemStack(BaseRegistry.CREW_FLAG_ITEM.get());
            flag.set(DataComponents.CUSTOM_NAME, Component.literal("Test Crew"));
            A.setItemInHand(InteractionHand.MAIN_HAND, flag);
            A.moveTo(heart1.getX() + 3.5, heart1.getY(), heart1.getZ() + 0.5);
            BlockPos below = heart1.below();
            InteractionResult res = flag.useOn(new UseOnContext(A, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
            c1 = CrewBases.claimOn(s, i1.id);
            boolean block = l.getBlockState(heart1).is(BaseRegistry.CREW_FLAG.get());
            check("bases: planting the crew flag at the heart claims the island for the crew",
                    res.consumesAction() && c1 != null && block && c1.crew.equals(Characters.get(A).characterId) && c1.flag.equals(heart1) && c1.crewName.equals("Test Crew")
                            && A.getMainHandItem().isEmpty(),
                    "use " + res + ", claim " + (c1 == null ? "none" : c1.crewName + " flag " + c1.flag.toShortString() + (c1.hasDock ? " dock " + c1.dockX + "," + c1.dockZ : " no dock"))
                            + ", flag block " + block + ", flag item left " + !A.getMainHandItem().isEmpty());
            if (c1 == null) {
                steps.clear();
                return 0;
            }
            check("bases: the claim is announced and on everyone's sea chart under the crew's name",
                    NEWS.contains("grandline.base.claimed_announce") && got(A, "grandline.base.claimed") && chartHas(B, i1.id, "Test Crew") && chartHas(A, i1.id, "Test Crew"),
                    "news " + NEWS + ", chart (rival) " + chartHas(B, i1.id, "Test Crew"));
            check("bases: the claim finds a dock in open water off the island", c1.hasDock, c1.hasDock ? "dock " + c1.dockX + "," + c1.dockZ + ", "
                    + (int) Math.hypot(c1.dockX - heart1.getX(), c1.dockZ - heart1.getZ()) + " blocks from the flag" : "none");
            return 0;
        });

        // ---------- one claim per crew, one crew per island ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            CrewBases.Refusal mate = CrewBases.check(C, heart2);
            BlockPos other = prepare(l, heart1.getX() + 8, heart1.getZ() + 8);
            clearThreats(l, B, other);
            CrewBases.Refusal rival = CrewBases.check(B, other);
            clearThreats(l, B, heart2);
            CrewBases.Refusal ok = CrewBases.check(B, heart2);
            Claim c2 = ok == null ? CrewBases.claim(B, heart2, "") : null;
            boolean transferRefused = c2 != null && !CrewBases.transfer(B, c2, A) && c2.crew.equals(Characters.get(B).characterId);
            check("bases: one claim per crew, and an island already claimed is taken",
                    mate != null && mate.key().endsWith(".limit") && rival != null && rival.key().endsWith(".taken") && c2 != null && transferRefused
                            && CrewBases.claimsOf(s, Characters.get(A).characterId).size() == 1,
                    "crewmate elsewhere " + (mate == null ? "allowed" : mate.key()) + ", rival on the claimed island " + (rival == null ? "allowed" : rival.key())
                            + ", rival elsewhere " + (ok == null ? "claimed " + i2.id : ok.key()) + ", transfer to a crew at its limit refused " + transferRefused);
            return 0;
        });

        // ---------- building in the base ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan plan = WorldPlan.of(s);
            BlockPos inside = null;
            for (int r = 4; r < 30 && inside == null; r += 2)
                for (int a = 0; a < 8 && inside == null; a++) {
                    int x = heart1.getX() + (int) Math.round(Math.cos(a * Math.PI / 4) * r), z = heart1.getZ() + (int) Math.round(Math.sin(a * Math.PI / 4) * r);
                    if (CrewBases.claimAt(l, x, z) == c1 && !TownProtection.protectedAt(plan, x, z)) inside = new BlockPos(x, ground(l, x, z) - 1, z);
                }
            BlockPos outside = new BlockPos(heart1.getX() + CrewBases.radius() + 30, 70, heart1.getZ());
            Plot plot = plan.byId("orange_town").settlement().plots.get(0);
            BlockPos town = new BlockPos(plot.x() + plot.sizeX() / 2, plot.y(), plot.z() + plot.sizeZ() / 2);
            if (inside == null) {
                check("bases: only the crew builds inside the base", false, "no unprotected spot inside the claim");
                return 0;
            }
            boolean aBreak = breakCancelled(l, inside, A), cBreak = breakCancelled(l, inside, C), bBreak = breakCancelled(l, inside, B);
            boolean aPlace = placeCancelled(l, inside.above(), A), bPlace = placeCancelled(l, inside.above(), B);
            check("bases: the crew may break and place inside the base, an outsider may not",
                    !aBreak && !cBreak && bBreak && !aPlace && bPlace,
                    "at " + inside.toShortString() + ": break captain/mate/rival cancelled " + aBreak + "/" + cBreak + "/" + bBreak + ", place captain/rival cancelled " + aPlace + "/" + bPlace);
            l.setBlock(inside.above(), Blocks.CHEST.defaultBlockState(), 3);
            boolean bUse = useCancelled(inside.above(), B), aUse = useCancelled(inside.above(), A);
            l.setBlock(inside.above(), Blocks.AIR.defaultBlockState(), 3);
            boolean flagA = breakCancelled(l, heart1, A), flagB = breakCancelled(l, heart1, B);
            check("bases: outsiders cannot open containers in the base, and nobody breaks the flag by hand",
                    bUse && !aUse && flagA && flagB && CrewBases.claimOn(s, i1.id) == c1,
                    "chest opened by rival cancelled " + bUse + ", by the crew " + aUse + "; flag break cancelled (crew/rival) " + flagA + "/" + flagB);
            boolean outB = breakCancelled(l, outside, B);
            boolean townA = breakCancelled(l, town, A), townB = breakCancelled(l, town, B);
            check("bases: outside the claim nothing changes and town protection is unchanged",
                    CrewBases.claimAt(l, outside.getX(), outside.getZ()) == null && !outB && TownProtection.protectedAt(plan, town.getX(), town.getZ()) && townA && townB
                            && !TownProtection.protectedAt(plan, inside.getX(), inside.getZ()),
                    "wild " + outside.toShortString() + " rival cancelled " + outB + "; Orange Town plot " + town.toShortString() + " cancelled for the base's captain " + townA
                            + " and the rival " + townB + "; the base is not town protection " + !TownProtection.protectedAt(plan, inside.getX(), inside.getZ()));
            return 0;
        });

        // ---------- the shared warehouse ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            chest = null;
            for (int r = 3; r < 12 && chest == null; r++) {
                BlockPos p = new BlockPos(heart1.getX() + r, ground(l, heart1.getX() + r, heart1.getZ()), heart1.getZ());
                if (CrewBases.claimAt(l, p.getX(), p.getZ()) == c1) chest = p;
            }
            if (chest == null) chest = heart1.offset(3, 0, 0);
            l.setBlock(chest, BaseRegistry.BASE_CHEST.get().defaultBlockState(), 3);
            A.moveTo(chest.getX() + 1.5, chest.getY(), chest.getZ() + 0.5);
            C.moveTo(chest.getX() - 0.5, chest.getY(), chest.getZ() + 1.5);
            B.moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() - 1.5);
            Container va = CrewBases.warehouse(A, chest), vc = CrewBases.warehouse(C, chest), vb = CrewBases.warehouse(B, chest);
            boolean ok = va != null && vc != null && vb == null;
            if (ok) {
                va.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
                ChestMenu menu = ChestMenu.sixRows(7, C.getInventory(), vc);
                ok = menu.getSlot(0).getItem().is(Items.GOLD_INGOT) && menu.getSlot(0).getItem().getCount() == 5 && c1.warehouse.getItem(0).getCount() == 5
                        && va.stillValid(A) && !va.stillValid(B);
            }
            check("bases: the base chest opens one warehouse shared by the crew, closed to outsiders", ok && got(B, "grandline.base.chest_locked"),
                    "captain " + (va != null) + ", mate " + (vc != null) + ", rival " + (vb != null) + ", mate sees the captain's gold " + (vc != null && vc.getItem(0).is(Items.GOLD_INGOT)));
            return 0;
        });

        // ---------- upgrades ----------
        steps.add(s -> {
            CharacterData d = Characters.get(A);
            d.beli = 0;
            boolean poor = !BaseUpgrades.build(A, c1, BaseUpgrades.DOJO) && got(A, "grandline.base.upgrade_poor");
            d.beli = 100_000;
            boolean shortOf = !BaseUpgrades.build(A, c1, BaseUpgrades.DOJO) && got(A, "grandline.base.upgrade_short");
            A.getInventory().add(new ItemStack(Items.IRON_INGOT, 12));
            c1.warehouse.setItem(5, new ItemStack(Items.OAK_PLANKS, 32));
            boolean built = BaseUpgrades.build(A, c1, BaseUpgrades.DOJO);
            check("bases: upgrades cost Beli and materials from the pack and the warehouse",
                    poor && shortOf && built && c1.has(BaseUpgrades.DOJO) && d.beli == 65_000 && count(A, Items.IRON_INGOT) == 0 && c1.warehouse.getItem(5).isEmpty()
                            && got(C, "grandline.base.upgrade_built"),
                    "refused poor " + poor + ", short " + shortOf + ", built " + built + ", Beli " + d.beli + ", iron left " + count(A, Items.IRON_INGOT) + ", planks left "
                            + c1.warehouse.getItem(5).getCount() + ", mate told " + got(C, "grandline.base.upgrade_built"));
            for (String u : List.of(BaseUpgrades.GALLEY, BaseUpgrades.MEDBAY, BaseUpgrades.SLIP, BaseUpgrades.QUARTERS)) c1.upgrades.add(u);
            // the dojo
            A.moveTo(heart1.getX() + 2.5, heart1.getY(), heart1.getZ() + 2.5);
            int s0 = d.masteryXp.getOrDefault("style/brawler", 0);
            Hits.train(A, "style/brawler", 10);
            int inStyle = d.masteryXp.getOrDefault("style/brawler", 0) - s0;
            int h0 = d.masteryXp.getOrDefault("haki/observation", 0);
            Hits.train(A, "haki/observation", 10);
            int inHaki = d.masteryXp.getOrDefault("haki/observation", 0) - h0;
            int w0 = d.masteryXp.getOrDefault("weapon/sword", 0);
            Hits.train(A, "weapon/sword", 10);
            int inWeapon = d.masteryXp.getOrDefault("weapon/sword", 0) - w0;
            // the galley and the medical bay, inside
            ItemStack dishIn = new ItemStack(ActivityItems.GRILLED_FISH.get());
            Meals.onCooked(dishIn, A);
            A.setHealth(8f);
            int healed = CrewBases.medicalBay(c1, List.of(A));
            float hpIn = A.getHealth();
            int fed = CrewBases.galleyMeal(c1, List.of(A));
            boolean buff = CombatState.of(A).hasBuff(Meals.STAMINA);
            far(A);
            s0 = d.masteryXp.getOrDefault("style/brawler", 0);
            Hits.train(A, "style/brawler", 10);
            int outStyle = d.masteryXp.getOrDefault("style/brawler", 0) - s0;
            ItemStack dishOut = new ItemStack(ActivityItems.GRILLED_FISH.get());
            Meals.onCooked(dishOut, A);
            A.setHealth(8f);
            int healedOut = CrewBases.medicalBay(c1, List.of(A));
            A.setHealth(A.getMaxHealth());
            check("bases: the dojo trains style and Haki half again, inside the base only", inStyle == 15 && inHaki == 15 && inWeapon == 10 && outStyle == 10,
                    "style +" + inStyle + " inside / +" + outStyle + " outside, Haki +" + inHaki + ", weapon +" + inWeapon);
            check("bases: the galley cooks a step better inside, the medical bay heals, a hot meal is served",
                    Meals.quality(dishIn) == 1 && Meals.quality(dishOut) == 0 && healed == 1 && hpIn > 8f && healedOut == 0 && fed == 1 && buff,
                    "dish quality inside " + Meals.quality(dishIn) + " / outside " + Meals.quality(dishOut) + ", healed " + healed + " (hp 8 -> " + hpIn + "), outside " + healedOut
                            + ", meal " + fed + " buff " + buff);
            // the waters off the base, loaded now for the dock and the raids (entities only show up a tick or two later)
            if (c1.hasDock) loadNow(s.overworld(), c1.dockX, c1.dockZ, 6);
            return 20;
        });

        // ---------- the dock: free repairs, the slip ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            if (!c1.hasDock) {
                check("bases: crew ships at the base dock are repaired for free", false, "no dock");
                return 0;
            }
            loadNow(l, c1.dockX, c1.dockZ, 2);
            shipA = ModEntities.SHIPS.get(ShipClass.SLOOP).get().create(l);
            shipB = ModEntities.SHIPS.get(ShipClass.SLOOP).get().create(l);
            Vec3 wa = LivingSea.openWater(l, c1.dockX, c1.dockZ, 4);
            if (wa == null) wa = com.grandline.ship.Shipyard.findWaterPublic(l, new BlockPos(c1.dockX, WorldPlan.SEA_LEVEL, c1.dockZ));
            if (wa == null || Math.hypot(wa.x - c1.dockX, wa.z - c1.dockZ) > 30) wa = new Vec3(c1.dockX + 0.5, WorldPlan.SEA_LEVEL + 0.9, c1.dockZ + 0.5);
            shipA.moveTo(wa.x, wa.y, wa.z, 0, 0);
            shipA.owner = A.getUUID();
            shipA.ownerName = "Rook";
            shipA.setHull(ShipClass.SLOOP.hull);
            l.addFreshEntity(shipA);
            Vec3 wb = LivingSea.openWater(l, wa.x + 14, wa.z, 4);
            if (wb == null) wb = wa.add(12, 0, 0);
            shipB.moveTo(wb.x, wb.y, wb.z, 0, 0);
            shipB.owner = B.getUUID();
            shipB.ownerName = "Maddox";
            shipB.setHull(ShipClass.SLOOP.hull);
            l.addFreshEntity(shipB);
            float half = shipA.maxHull() * 0.5f;
            shipA.setHull(half);
            shipB.setHull(half);
            shipA.setRigging(0.5f);
            long beli = Characters.get(A).beli;
            int moored = CrewBases.mooredShips(l, c1, CrewBases.online(s)).size();
            int worked = CrewBases.dockService(l, c1, CrewBases.online(s));
            float a1 = shipA.hull(), b1 = shipB.hull();
            for (int i = 0; i < 30; i++) CrewBases.dockService(l, c1, CrewBases.online(s));
            check("bases: crew ships at the base dock are repaired for free (an outsider's are not)",
                    worked == 1 && a1 > half && b1 == half && shipA.hull() >= shipA.maxHull() && shipA.rigging() >= 1f && shipB.hull() == half && Characters.get(A).beli == beli,
                    String.format("hull %.0f -> %.0f -> %.0f of %.0f, rigging %.2f, rival's %.0f, Beli %d -> %d; ship at %s (%s, in level %s), dock %d,%d, %d crew ship(s) moored, %d ships near",
                            half, a1, shipA.hull(), shipA.maxHull(), shipA.rigging(), shipB.hull(), beli, Characters.get(A).beli, shipA.blockPosition().toShortString(),
                            shipA.isRemoved() ? "removed" : "present", l.getEntity(shipA.getUUID()) != null, c1.dockX, c1.dockZ, moored,
                            l.getEntitiesOfClass(ShipEntity.class, shipA.getBoundingBox().inflate(50)).size()));
            A.moveTo(wa.x + 2, wa.y + 1, wa.z);
            Long price = BaseUpgrades.slipPrice("fine_sails");
            long before = Characters.get(A).beli;
            boolean fitted = BaseUpgrades.shipUpgrade(A, c1, "fine_sails");
            check("bases: the slip fits ship upgrades at the base dock for three quarters of the price",
                    price != null && price == 9000 && fitted && shipA.hasUpgrade("fine_sails") && before - Characters.get(A).beli == 9000 && BaseUpgrades.slipPrice("coating") == null,
                    "price " + price + ", fitted " + fitted + ", paid " + (before - Characters.get(A).beli));
            shipA.discard();
            shipB.discard();
            return 0;
        });

        // ---------- hired hands ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            A.moveTo(heart1.getX() + 2.5, heart1.getY(), heart1.getZ() + 2.5);
            Characters.get(A).beli = 200_000;
            boolean guard = BaseHands.hire(A, c1, "guard");
            boolean more = BaseHands.hire(A, c1, "sailor") && BaseHands.hire(A, c1, "sailor") && BaseHands.hire(A, c1, "sailor");
            boolean full = !BaseHands.hire(A, c1, "sailor") && got(A, "grandline.base.hire_full");
            boolean rival = !BaseHands.hire(B, c1, "guard");
            BaseHands.sync(l, c1, l.getGameTime());
            BaseHands.sync(l, c1, l.getGameTime());
            List<HumanoidNpc> hands = BaseHands.present(l, c1);
            boolean tagged = !hands.isEmpty() && hands.stream().allMatch(h -> h.getTags().contains(CrewBases.HAND) && h.getTags().contains(CrewBases.OF + c1.island));
            boolean friendly = !hands.isEmpty() && hands.stream().noneMatch(h -> h.isHostileTo(A) || h.isHostileTo(C));
            check("bases: crew quarters take four hired hands who live at the base and never turn on the crew",
                    guard && more && full && rival && c1.hands.size() == 4 && hands.size() == 4 && tagged && friendly,
                    "hired " + c1.hands + ", fifth refused " + full + ", rival refused " + rival + ", on the ground " + hands.size() + ", tagged " + tagged + ", friendly " + friendly);
            HumanoidNpc sailor = hands.stream().filter(h -> h.role().equals("sailor")).findFirst().orElse(null);
            if (sailor != null) sailor.kill();
            check("bases: a hand who dies is gone for good", sailor != null && c1.hands.size() == 3 && got(A, "grandline.base.hand_fell"),
                    "hands " + c1.hands + ", crew told " + got(A, "grandline.base.hand_fell"));
            return 0;
        });

        // ---------- raids: the cap ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            C.moveTo(heart1.getX() - 2.5, heart1.getY(), heart1.getZ() + 2.5);
            loadNow(l, c1.hasDock ? c1.dockX : heart1.getX(), c1.hasDock ? c1.dockZ : heart1.getZ(), 6);
            loadNow(l, heart1.getX(), heart1.getZ(), 4); // the landing party comes up at the base's edge, near the flag: keep it loaded
            BaseRaids.Rules r = new BaseRaids.Rules();
            r.force = true;
            r.faction = LivingSea.PIRATE;
            r.spawnDist = 200;
            int ships = SeaEncounters.countNpcShips(l);
            r.cap = ships;
            BaseRaids.Raid none = BaseRaids.decide(l, c1, List.<ServerPlayer>of(A, C), 4, r);
            r.cap = ships + 1;
            raid = BaseRaids.decide(l, c1, List.<ServerPlayer>of(A, C), 4, r);
            int after = SeaEncounters.countNpcShips(l);
            check("bases: a raid on a base waits for room under the NPC ship cap, then one ship sets out",
                    none == null && raid != null && after == ships + 1 && raid.ship.getTags().contains(BaseRaids.TAG) && raid.phase == BaseRaids.APPROACH && !raid.warned,
                    "no room: " + (none == null ? "no raid" : "raid!") + "; room for one: " + (raid == null ? "no raid" : raid.ship.shipName() + " (" + raid.faction + ") at "
                            + raid.ship.blockPosition().toShortString() + ", " + (int) raid.ship.position().distanceTo(raid.dock) + " blocks from the dock") + ", NPC ships " + ships + " -> " + after);
            if (raid == null) return 0;
            raid.ship.testSpeedMult = 2f;
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (raid == null) return 0;
            ServerLevel l = s.overworld();
            keep(l, raid.ship);
            if (raid.phase == BaseRaids.APPROACH && !raid.ship.isRemoved() && ++waited[0] < 1200) return -1;
            boolean sailedIn = raid.phase == BaseRaids.ASHORE;
            String how0 = sailedIn ? "sailed in and landed after " + waited[0] + " ticks" : "phase " + raid.phase + " after " + waited[0] + " ticks (" + raid.outcome + "), landed by hand";
            String how = how0;
            if (raid.phase == BaseRaids.APPROACH && !raid.ship.isRemoved()) {
                how += " [" + BaseRaids.landingReport(l, c1, raid) + "]";
                BaseRaids.land(raid, l, c1, l.getGameTime());
            }
            List<GLNpc> rs = raiders(l, raid);
            boolean ashore = !rs.isEmpty() && rs.stream().allMatch(m -> m.getY() > WorldPlan.SEA_LEVEL && CrewBases.within(c1, m.getX(), m.getZ()) || m.position().distanceTo(Vec3.atCenterOf(heart1)) < CrewBases.radius() * 1.5);
            check("bases: the raid ship lands at most six raiders at the base",
                    raid.phase == BaseRaids.ASHORE && rs.size() >= 1 && rs.size() <= BaseRaids.MAX_RAIDERS && ashore && got(A, "grandline.base.raid.landed"),
                    how + ": " + rs.size() + " raiders ashore " + ashore + ", ship " + (int) raid.ship.position().distanceTo(raid.dock) + " blocks from the dock");
            List<HumanoidNpc> hands = BaseHands.present(l, c1);
            GLNpc m = rs.isEmpty() ? null : rs.get(0);
            boolean fights = m != null && m.isHostileTo(A) && m.isHostileTo(C) && !hands.isEmpty() && m.isHostileTo(hands.get(0)) && hands.get(0).isHostileTo(m)
                    && (rs.size() < 2 || !m.isHostileTo(rs.get(1)));
            check("bases: raiders fight the crew and the hands, the hands fight the raiders", fights,
                    m == null ? "no raider" : "raider vs captain " + m.isHostileTo(A) + ", vs hand " + (!hands.isEmpty() && m.isHostileTo(hands.get(0))) + ", hand vs raider "
                            + (!hands.isEmpty() && hands.get(0).isHostileTo(m)));
            // the defenders cut them down
            for (GLNpc r : rs) {
                r.hurt(A.damageSources().playerAttack(A), 1f);
                r.kill();
            }
            num[0] = Characters.get(A).beli;
            num[1] = Characters.get(C).beli;
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (raid == null) return 0;
            ServerLevel l = s.overworld();
            if (raid.outcome.isEmpty() && ++waited[0] < 100) return -1;
            check("bases: beaten raiders mean the raid is repelled and those who fought are paid",
                    raid.outcome.equals("repelled") && raid.phase == BaseRaids.LEAVING && c1.raidsRepelled == 1 && Characters.get(A).beli > num[0] && Characters.get(C).beli == num[1]
                            && CrewBases.claimOn(s, i1.id) == c1 && got(A, "grandline.base.raid.repelled_paid"),
                    "outcome " + raid.outcome + " after " + waited[0] + " ticks, repelled " + c1.raidsRepelled + ", captain's Beli " + num[0] + " -> " + Characters.get(A).beli
                            + ", mate (did not fight) " + num[1] + " -> " + Characters.get(C).beli);
            BaseRaids.dismiss(raid, l);
            check("bases: the raid ship is gone once dismissed", raid.ship.isRemoved() && BaseRaids.raidOn(i1.id) == null, "");
            return 0;
        });

        // ---------- raids: the watchtower, the flag taken, retaken, lost ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            c1.upgrades.add(BaseUpgrades.WATCHTOWER);
            INBOX.clear();
            NEWS.clear();
            BaseRaids.Rules r = new BaseRaids.Rules();
            r.force = true;
            r.faction = LivingSea.MARINE;
            r.spawnDist = 200;
            r.cap = SeaEncounters.countNpcShips(l) + 1;
            raid = BaseRaids.decide(l, c1, List.<ServerPlayer>of(A, C), 4, r);
            check("bases: a watchtower sights the raid far out and warns the whole crew",
                    raid != null && raid.warned && got(A, "grandline.base.raid.watchtower") && got(C, "grandline.base.raid.watchtower") && !got(B, "grandline.base.raid.watchtower"),
                    raid == null ? "no raid" : raid.ship.shipName() + " " + (int) raid.ship.position().distanceTo(Vec3.atCenterOf(heart1)) + " blocks from the flag, warned " + raid.warned);
            if (raid == null) return 0;
            boolean landed = BaseRaids.land(raid, l, c1, l.getGameTime());
            List<GLNpc> rs = raiders(l, raid);
            check("bases: with a watchtower the raiders glow", landed && !rs.isEmpty() && rs.stream().allMatch(m -> m.hasEffect(MobEffects.GLOWING)), rs.size() + " raiders");
            // nobody defends: the crew is away and the hands are gone
            far(A);
            far(C);
            far(B);
            c1.hands.clear();
            BaseHands.despawn(l, c1);
            pin(l, raid, heart1, 2);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (raid == null) return 0;
            ServerLevel l = s.overworld();
            keep(l, raid.ship);
            pin(l, raid, heart1, 2);
            if (raid.phase == BaseRaids.ASHORE && ++waited[0] < 1000) return -1;
            check("bases: raiders holding an undefended flag take it (announced, grace period)",
                    raid.phase == BaseRaids.FLAG_TAKEN && NEWS.contains("grandline.base.raid.flag_taken_marines") && got(A, "grandline.base.raid.at_flag") && CrewBases.claimOn(s, i1.id) == c1,
                    "phase " + raid.phase + " after " + waited[0] + " ticks, news " + NEWS);
            // the captain comes back to the flag, the raiders are driven off it
            A.moveTo(heart1.getX() + 1.5, heart1.getY(), heart1.getZ() + 0.5);
            pin(l, raid, heart1, 20);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (raid == null) return 0;
            ServerLevel l = s.overworld();
            pin(l, raid, heart1, 20);
            if (raid.phase == BaseRaids.FLAG_TAKEN && ++waited[0] < 100) return -1;
            check("bases: a taken flag can be retaken within the grace period", raid.phase == BaseRaids.ASHORE && NEWS.contains("grandline.base.raid.retaken"),
                    "phase " + raid.phase + " after " + waited[0] + " ticks");
            far(A);
            pin(l, raid, heart1, 2);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (raid == null) return 0;
            ServerLevel l = s.overworld();
            keep(l, raid.ship);
            pin(l, raid, heart1, 2);
            if (raid.phase == BaseRaids.ASHORE && ++waited[0] < 1000) return -1;
            raid.lostAt -= 20L * 3600; // the grace period runs out
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (raid == null) return 0;
            ServerLevel l = s.overworld();
            if (CrewBases.claimOn(s, i1.id) != null && ++waited[0] < 100) return -1;
            boolean gone = CrewBases.claimOn(s, i1.id) == null;
            boolean plunder = raid.plunder.stream().anyMatch(st -> st.is(Items.GOLD_INGOT));
            check("bases: when the grace period runs out the flag is captured: the claim is removed and the raiders carry off goods",
                    gone && !l.getBlockState(heart1).is(BaseRegistry.CREW_FLAG.get()) && NEWS.contains("grandline.base.removed.captured") && plunder && raid.phase == BaseRaids.LEAVING
                            && !chartHas(B, i1.id, null) && BaseHands.present(l, c1).isEmpty() && CrewBases.claimsOf(s, Characters.get(A).characterId).isEmpty(),
                    "claim gone " + gone + ", flag block " + l.getBlockState(heart1).getBlock() + ", plunder " + raid.plunder + ", on the chart " + chartHas(B, i1.id, null));
            BaseRaids.dismiss(raid, l);
            return 0;
        });

        // ---------- transfer and abandon; the claim left for phase 2 ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            Claim c2 = CrewBases.claimOn(s, i2.id);
            if (c2 == null) {
                check("bases: a claim can be handed to another crew and abandoned", false, "the rival's claim is missing");
                return 0;
            }
            NEWS.clear();
            boolean mateRefused = !CrewBases.abandon(C, c2) && got(C, "grandline.base.not_leader");
            boolean moved = CrewBases.transfer(B, c2, D);
            BlockPos in = new BlockPos(heart2.getX() + 3, ground(l, heart2.getX() + 3, heart2.getZ()) - 1, heart2.getZ());
            boolean dMay = CrewBases.mayModify(D, l, in), bMay = CrewBases.mayModify(B, l, in);
            boolean abandoned = CrewBases.abandon(D, c2);
            check("bases: a claim can be handed to another crew and abandoned by its leader",
                    mateRefused && moved && dMay && !bMay && abandoned && CrewBases.claimOn(s, i2.id) == null && !l.getBlockState(heart2).is(BaseRegistry.CREW_FLAG.get())
                            && NEWS.contains("grandline.base.transferred") && NEWS.contains("grandline.base.removed.abandoned"),
                    "outsider refused " + mateRefused + ", transferred " + moved + ", new crew builds " + dMay + ", old crew builds " + bMay + ", abandoned " + abandoned + ", news " + NEWS);
            // the persistence fixture: the rival claims it again, with upgrades, goods and a hand
            clearThreats(l, B, heart2);
            CrewBases.Refusal again = CrewBases.check(B, heart2);
            Claim keep = again == null ? CrewBases.claim(B, heart2, "Persist Crew") : null;
            if (keep != null) {
                keep.upgrades.add(BaseUpgrades.DOJO);
                keep.upgrades.add(BaseUpgrades.QUARTERS);
                keep.warehouse.setItem(0, new ItemStack(Items.DIAMOND, 7));
                keep.hands.add("sailor");
                keep.raidReadyAt = Long.MAX_VALUE / 4; // no raid on the fixture
                keep.dirty();
                try {
                    Files.writeString(Path.of(FIXTURE), keep.island + " " + keep.crew + " " + keep.flag.asLong());
                } catch (Exception ex) {
                    check("bases: fixture written", false, ex.toString());
                }
            }
            check("bases: a claim is kept for the restart", keep != null, again == null ? keep.island + " flag " + keep.flag.toShortString() : again.key());
            return 0;
        });
        steps.add(s -> {
            CrewBases.testPlayers = null;
            CrewBases.tap = null;
            CrewBases.announced = null;
            release(s.overworld());
            return 0;
        });
    }

    // ================================================================== phase 2

    static void phase2(Deque<SelfTest.Step> steps) {
        final String[] fx = new String[1];
        steps.add(s -> {
            try {
                fx[0] = Files.readString(Path.of(FIXTURE)).trim();
            } catch (Exception e) {
                check("bases: claims persist across a restart", false, "no fixture: " + e);
                return 0;
            }
            BlockPos flag = BlockPos.of(Long.parseLong(fx[0].split(" ")[2]));
            s.overworld().getChunk(flag.getX() >> 4, flag.getZ() >> 4);
            return 1;
        });
        steps.add(s -> {
            if (fx[0] == null) return 0;
            String[] p = fx[0].split(" ");
            Claim c = CrewBases.claimOn(s, p[0]);
            BlockPos flag = BlockPos.of(Long.parseLong(p[2]));
            BlockState st = s.overworld().getBlockState(flag);
            boolean ok = c != null && c.crew.toString().equals(p[1]) && c.flag.equals(flag) && c.crewName.equals("Persist Crew") && c.has(BaseUpgrades.DOJO)
                    && c.has(BaseUpgrades.QUARTERS) && c.warehouse.getItem(0).is(Items.DIAMOND) && c.warehouse.getItem(0).getCount() == 7 && c.hands.equals(List.of("sailor"));
            check("bases: claims persist across a restart (crew, flag, upgrades, warehouse, hands)", ok && st.is(BaseRegistry.CREW_FLAG.get()),
                    c == null ? "claim missing" : c.crewName + " " + c.upgrades + " warehouse " + c.warehouse.getItem(0) + " hands " + c.hands + ", flag block " + st.getBlock());
            FakePlayer viewer = fp(s.overworld(), "BaseViewer");
            check("bases: the claim is still on the sea chart after the restart", chartHas(viewer, p[0], "Persist Crew"), "");
            return 0;
        });
    }

    private BaseTests() {}
}
