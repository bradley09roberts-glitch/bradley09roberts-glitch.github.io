package com.longnight.gametest;

import com.longnight.LongNight;
import com.longnight.game.Founding;
import com.longnight.registry.ModBlocks;
import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Faction;
import com.longnight.sim.Person;
import com.longnight.sim.Politics;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import com.longnight.world.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A player founds a settlement: settlers arrive, the realm is the player's, and it is never handed to an NPC ruler. */
@GameTestHolder(LongNight.MODID)
@PrefixGameTestTemplate(false)
public class FoundingGameTests {

    @GameTest(template = "empty", timeoutTicks = 100)
    @SuppressWarnings("removal")
    public static void playerFoundsASettlement(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        p.getInventory().add(Founding.charter());
        BlockPos pos = h.absolutePos(new BlockPos(12, 1, 12));
        // keep clear of any generated settlement: GameTests run far out
        level.setBlock(pos, ModBlocks.HEARTHSTONE.get().defaultBlockState(), 3);
        Placer.testGroundY = h.absolutePos(BlockPos.ZERO).getY();
        Founding.found(p, level, pos);
        Settlement s = sim.settlementAt(pos, 2);
        h.assertTrue(s != null && s.playerFounded, "settlement founded");
        h.assertTrue(p.getUUID().equals(s.founderPlayer), "founder recorded");
        h.assertTrue(sim.residents(s).size() == 3, "three settlers answered the charter: " + sim.residents(s).size());
        h.assertFalse(p.getInventory().contains(Founding.charter()), "charter consumed");
        Faction f = sim.faction(s.faction);
        h.assertTrue(f != null && f.playerRealm && p.getUUID().equals(f.playerRuler), "player realm");
        // the realm must never be handed to an NPC by succession
        Politics.succession(sim, f, 1);
        h.assertTrue(f.ruler == null, "no NPC ruler for a player realm");
        for (Person x : sim.residents(s)) h.assertTrue(x.profession != com.longnight.sim.Profession.RULER, "no settler crowned");
        // the well cannot sit on the player's hearthstone: it is planned on another plot instead
        Building well = com.longnight.sim.Planner.plan(sim, s, BuildingType.WELL, 1);
        h.assertTrue(well != null, "a well is planned");
        h.assertFalse(well.contains(pos, 0), "the well's plot avoids the hearthstone");
        Placer.testGroundY = null;
        sim.settlements.remove(s.id);
        sim.factions.remove(f.id);
        p.discard();
        h.succeed();
    }

    /** A new player's company: real people who are not a place, appear beside their leader, and settle where the leader chooses. */
    @GameTest(template = "empty", timeoutTicks = 200)
    @SuppressWarnings("removal")
    public static void companyFollowsAndSettles(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WorldSim sim = WorldSim.get(level.getServer());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        BlockPos start = h.absolutePos(new BlockPos(6, 1, 6));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        Placer.testGroundY = h.absolutePos(BlockPos.ZERO).getY();
        Founding.startBand(p, sim, 4);
        Settlement band = com.longnight.sim.Band.of(sim, p.getUUID());
        h.assertTrue(band != null && band.kind == Settlement.Kind.BAND, "a company sets out with the player");
        h.assertTrue(sim.residents(band).size() == 4, "four companions: " + sim.residents(band).size());
        h.assertTrue(p.getInventory().contains(Founding.charter()), "the leader carries a Founding Charter");
        h.assertTrue(band.buildings.isEmpty(), "a company on the road has no buildings");
        h.assertTrue(sim.settlementAt(start, 16) == null, "a company is not a place (no spawn suppression, no market) ");
        h.onEachTick(() -> {
            if (level.getGameTime() % 20 == 0) com.longnight.world.Embodiment.tick(level, sim);
        });
        h.runAfterDelay(80, () -> {
            int near = level.getEntitiesOfClass(com.longnight.entity.CitizenEntity.class, p.getBoundingBox().inflate(12),
                    c -> band.id.equals(c.settlementId)).size();
            h.assertTrue(near >= 1, "companions appear beside their leader: " + near);
            for (Person x : sim.residents(band)) h.assertTrue(x.alive, "all alive");
            BlockPos ground = h.absolutePos(new BlockPos(12, 1, 12)); // the arena floor's top block
            h.assertTrue(Founding.settleBand(p, level, ground, true), "they settle where the leader stands");
            h.assertTrue(band.kind == Settlement.Kind.SETTLEMENT, "the company is now a settlement");
            h.assertTrue(sim.residents(band).size() == 4, "the same four people live there");
            h.assertTrue(sim.settlementAt(ground, 2) == band, "and it is a place");
            h.assertFalse(p.getInventory().contains(Founding.charter()), "charter used");
            Faction f = sim.faction(band.faction);
            h.assertTrue(f != null && f.playerRealm && p.getUUID().equals(f.playerRuler) && f.ruler == null, "the player's lordship");
            h.assertTrue(band.buildings.stream().anyMatch(b -> b.type == BuildingType.CAMPSITE && b.usable()), "tents pitched");
            BlockPos hearth = ground.offset(0, 1, -4);
            h.assertTrue(level.getBlockState(hearth).is(ModBlocks.HEARTHSTONE.get()), "hearthstone set down: found " + level.getBlockState(hearth)
                    + " ready=" + com.longnight.world.Places.ready(level, hearth) + " at " + hearth + " centre " + band.center);
            for (Person x : sim.residents(band)) com.longnight.world.Embodiment.discardBody(level, x.id);
            Placer.testGroundY = null;
            sim.settlements.remove(band.id);
            sim.factions.remove(f.id);
            p.discard();
            h.succeed();
        });
    }
}
