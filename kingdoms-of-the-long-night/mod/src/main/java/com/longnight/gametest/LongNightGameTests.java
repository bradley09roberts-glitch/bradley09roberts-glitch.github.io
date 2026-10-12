package com.longnight.gametest;

import com.longnight.LongNight;
import com.longnight.block.GravestoneBlockEntity;
import com.longnight.content.Content;
import com.longnight.entity.CitizenEntity;
import com.longnight.game.Coins;
import com.longnight.game.Trade;
import com.longnight.game.Wards;
import com.longnight.registry.ModBlocks;
import com.longnight.registry.ModEntities;
import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Commodity;
import com.longnight.sim.Construction;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.Stockpile;
import com.longnight.sim.WorldBuilder;
import com.longnight.sim.WorldSim;
import com.longnight.world.Blueprint;
import com.longnight.world.BlueprintGen;
import com.longnight.world.Embodiment;
import com.longnight.world.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * In-world tests (run with ./gradlew runGameTestServer). Each builds a tiny settlement on the
 * test platform and checks a rule that must never break: construction is paid exactly once and
 * never overwrites player blocks, trading conserves goods and money, the dead stay dead and are
 * buried by name, wards stop night-spawn.
 */
@GameTestHolder(LongNight.MODID)
@PrefixGameTestTemplate(false)
public class LongNightGameTests {

    static Settlement settlement(GameTestHelper h, String culture) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        if (Content.CULTURES.isEmpty()) Content.loadBundled();
        BlockPos centre = h.absolutePos(new BlockPos(12, 0, 12));
        Settlement s = WorldBuilder.newSettlement(sim, culture, centre, 0, "Testholm-" + UUID.randomUUID().toString().substring(0, 4));
        s.radius = 10;
        s.materialized = true;
        Placer.testGroundY = centre.getY();
        return s;
    }

    static Building plan(Settlement s, BuildingType t, BlockPos origin) {
        Building b = new Building(UUID.randomUUID(), t);
        b.seed = 12345;
        b.origin = origin;
        b.faith = "hearth";
        Construction.initPlan(s, b);
        s.buildings.add(b);
        return b;
    }

    static void cleanup(GameTestHelper h, Settlement s) {
        WorldSim sim = WorldSim.get(h.getLevel().getServer());
        for (UUID id : s.residents) {
            Embodiment.discardBody(h.getLevel(), id);
        }
        sim.settlements.remove(s.id);
        Placer.testGroundY = null;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void builderPaysOncePerBlock(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = settlement(h, "valemen");
        Building b = plan(s, BuildingType.HOUSE, h.absolutePos(new BlockPos(3, 0, 3)));
        Map<Commodity, Double> bill = Construction.blueprint(s, b).totalCost();
        s.stock = new Stockpile();
        bill.forEach(s.stock::add);
        int guard = 0;
        while (b.underConstruction() && guard++ < 5000) {
            if (Construction.advance(sim, s, b, 1, 0) != 1) break;
            Placer.placeNext(level, sim, s, b);
        }
        h.assertTrue(b.state == Building.State.COMPLETE, "house complete, state=" + b.state + " problem=" + b.problem);
        h.assertTrue(b.placed == b.total, "all blocks placed " + b.placed + "/" + b.total);
        h.assertTrue(s.stock.total() < 1e-6, "exactly the bill consumed, left=" + s.stock.total());
        h.assertFalse(Placer.placeNext(level, sim, s, b), "nothing more to place");
        h.assertTrue(Construction.advance(sim, s, b, 10, 0) == 0, "no further charge");
        // the blocks really exist: walls on the perimeter at y+1
        Blueprint bp = Construction.blueprint(s, b);
        int solid = 0;
        for (Blueprint.Step st : bp.steps) {
            BlockPos p = Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z());
            if (!level.getBlockState(p).isAir()) solid++;
        }
        h.assertTrue(solid > bp.steps.size() * 0.9, "blocks present in world: " + solid + "/" + bp.steps.size());
        cleanup(h, s);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void neverBuildsOverPlayerBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = settlement(h, "dunmar");
        BlockPos origin = h.absolutePos(new BlockPos(3, 0, 3));
        Building b = plan(s, BuildingType.HOUSE, origin);
        b.progress = b.total; // fully paid, as if built in the distance
        BlockPos chest = origin.offset(3, 1, 3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        boolean placed = Placer.placeNext(level, sim, s, b);
        h.assertFalse(placed, "building must not start on a plot with a player's chest (origin " + origin + " -> " + b.origin
                + ", problem='" + b.problem + "', chest=" + chest + ", ground=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chest.getX(), chest.getZ()) + ")");
        h.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the chest is untouched");
        h.assertTrue(!b.origin.equals(origin) || !b.problem.isEmpty(), "building moved or reported a problem");
        cleanup(h, s);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    @SuppressWarnings("removal")
    public static void tradeConservesGoodsAndCoin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = settlement(h, "tidefolk");
        s.treasury = 1000;
        s.stock = new Stockpile();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 10));
        int coinsBefore = Coins.purse(p) + s.treasury;
        Trade.handIn(p, sim, s, InteractionHand.MAIN_HAND, false);
        int left = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(Items.WHEAT)) left += p.getInventory().getItem(i).getCount();
        }
        h.assertTrue(left == 0, "wheat handed over, left=" + left);
        h.assertTrue(Math.abs(s.stock.get(Commodity.GRAIN) - 10) < 1e-6, "ledger has exactly 10 grain: " + s.stock.get(Commodity.GRAIN));
        h.assertTrue(Coins.purse(p) + s.treasury == coinsBefore, "money conserved on sale");
        int purse = Coins.purse(p);
        h.assertTrue(purse > 0, "player was paid");
        // buy back as much as affordable; goods and money still conserved
        int unit = Trade.buyPrice(sim, s, Commodity.GRAIN);
        int n = Math.min(10, purse / unit);
        boolean ok = Trade.buy(p, sim, s, Commodity.GRAIN, n);
        h.assertTrue(n == 0 || ok, "purchase succeeded");
        int wheat = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(Items.WHEAT)) wheat += p.getInventory().getItem(i).getCount();
        }
        h.assertTrue(Math.abs(wheat + s.stock.get(Commodity.GRAIN) - 10) < 1e-6, "grain conserved: player " + wheat + " + ledger " + s.stock.get(Commodity.GRAIN));
        h.assertTrue(Coins.purse(p) + s.treasury == coinsBefore, "money conserved after purchase");
        h.assertFalse(Trade.buy(p, sim, s, Commodity.GRAIN, 1000), "cannot buy more than exists");
        p.discard();
        cleanup(h, s);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void deadStayDeadAndAreBuried(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = settlement(h, "valemen");
        Building yard = plan(s, BuildingType.GRAVEYARD, h.absolutePos(new BlockPos(2, 0, 2)));
        yard.progress = yard.total;
        while (yard.placed < yard.progress && Placer.placeNext(level, sim, s, yard)) {
            // raise the burial ground
        }
        yard.state = Building.State.COMPLETE;
        Person p = sim.createPerson("valemen", Person.Sex.FEMALE, -200, sim.rng);
        sim.join(p, s);
        CitizenEntity body = ModEntities.CITIZEN.get().create(level);
        BlockPos at = h.absolutePos(new BlockPos(18, 1, 18));
        body.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        body.bind(p, s, 0, sim);
        level.addFreshEntity(body);
        body.kill();
        h.assertFalse(p.alive, "the person died with their body");
        h.assertFalse(s.residents.contains(p.id), "removed from the rolls");
        boolean buried = p.buried || Placer.bury(level, sim, s, p);
        h.assertTrue(buried, "a grave was dug");
        boolean found = false;
        for (BlockPos q : BlockPos.betweenClosed(yard.origin, yard.origin.offset(yard.width(), 2, yard.depth()))) {
            if (level.getBlockEntity(q) instanceof GravestoneBlockEntity g && p.id.equals(g.person)) {
                found = g.name.equals(p.fullName());
            }
        }
        h.assertTrue(found, "gravestone carries their name");
        // the embodiment manager must never give the dead a body again
        Embodiment.observe(level, s, true);
        h.runAfterDelay(60, () -> {
            for (var e : level.getEntitiesOfClass(CitizenEntity.class, new net.minecraft.world.phys.AABB(s.center).inflate(40))) {
                h.assertFalse(p.id.equals(e.personId) && e.isAlive(), "dead person has no body");
            }
            Embodiment.observe(level, s, false);
            cleanup(h, s);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wardStopsNightSpawn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos ward = h.absolutePos(new BlockPos(12, 1, 12));
        level.setBlock(ward, ModBlocks.WARD_STONE.get().defaultBlockState(), 3);
        Wards.kindle(level, ward);
        Zombie z = EntityType.ZOMBIE.create(level);
        z.moveTo(ward.getX() + 5, ward.getY(), ward.getZ() + 5);
        h.assertTrue(Wards.blocks(level, z, MobSpawnType.NATURAL), "natural spawn near a kindled ward is blocked");
        h.assertFalse(Wards.blocks(level, z, MobSpawnType.SPAWNER), "spawners are not affected");
        Wards.remove(level, ward);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyBlueprintGenerates(GameTestHelper h) {
        for (String c : Content.CULTURE_IDS) {
            for (BuildingType t : BuildingType.values()) {
                Blueprint bp = BlueprintGen.get(t, c, 1, 99, "hearth");
                h.assertTrue(!bp.steps.isEmpty(), t + "/" + c + " has steps");
                for (Blueprint.Step st : bp.steps) {
                    h.assertTrue(st.x() >= 0 && st.x() < bp.width && st.z() >= 0 && st.z() < bp.depth, t + "/" + c + " step inside footprint " + st);
                }
            }
        }
        h.succeed();
    }
}
