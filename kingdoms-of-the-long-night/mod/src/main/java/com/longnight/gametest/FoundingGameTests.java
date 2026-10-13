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
}
