package com.grandline.quest.arc;

import com.grandline.npc.GLNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.player.Characters;
import com.grandline.quest.arc.Arc.Role;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Protection of the people a later scene needs. Allies, quest givers, the missing and future recruits never die to
 * stray blows (mobs, other NPCs, falls, fire) and always yield to players instead of dying, however often they are hit.
 * A chapter's villain cannot be killed before the player has reached the fight with them: stray blows do nothing, and
 * a player who is not there yet only beats them into yielding (which still counts as beating them). Story bosses with
 * a boss definition keep their own fight rules. Commands and the void ({@code bypasses_invulnerability}) still work.
 */
public final class ArcGuard {
    /** What to do with a blow against a notable. */
    public enum Verdict {
        /** The usual rules. */
        DEFAULT,
        /** Ignore the blow. */
        BLOCK,
        /** Let it land, but a lethal blow makes them yield instead (every time, never a "final" blow). */
        YIELD
    }

    private ArcGuard() {}

    /** The player a blow counts for: the attacker, or the captain of the crew member who struck. */
    @Nullable
    public static ServerPlayer creditedPlayer(@Nullable Entity attacker) {
        if (attacker instanceof ServerPlayer sp) return sp;
        if (attacker instanceof GLNpc g && CrewManager.isCrew(g)) return CrewManager.captainOf(g);
        return null;
    }

    public static Verdict verdict(GLNpc npc, DamageSource src, @Nullable Entity attacker) {
        if (npc.notableId().isEmpty() || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return Verdict.DEFAULT;
        if (CrewManager.isCrew(npc) || com.grandline.boss.BossManager.isBoss(npc)) return Verdict.DEFAULT;
        Role role = Arcs.roleOf(npc.notableId());
        if (role == null) return Verdict.DEFAULT;
        ServerPlayer by = creditedPlayer(attacker);
        boolean fromPlayer = by != null || attacker instanceof Player;
        if (role.protectedAlways()) return fromPlayer ? Verdict.YIELD : Verdict.BLOCK;
        if (role.foe() && Arcs.stagedFoe(npc.notableId())) {
            if (!fromPlayer) return Verdict.BLOCK;
            if (by != null && !Arcs.reachedFight(Characters.get(by), npc.notableId())) return Verdict.YIELD;
        }
        return Verdict.DEFAULT;
    }

    /**
     * Story override of whom a staged villain picks fights with: not a player who has not reached the fight yet
     * (unless that player just struck first). Null = the usual rules.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        if (!(e instanceof ServerPlayer sp) || npc.notableId().isEmpty() || !Arcs.stagedFoe(npc.notableId())) return null;
        if (com.grandline.boss.BossManager.isBoss(npc) || Arcs.reachedFight(Characters.get(sp), npc.notableId())) return null;
        boolean struckFirst = npc.getLastHurtByMob() == sp && npc.tickCount - npc.getLastHurtByMobTimestamp() < 200;
        return struckFirst ? null : Boolean.FALSE;
    }
}
