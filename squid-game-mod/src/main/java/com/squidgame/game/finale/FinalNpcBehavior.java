package com.squidgame.game.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.finale.Duel;
import com.squidgame.core.finale.FinaleRules;
import com.squidgame.core.finale.NpcBrain;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import com.squidgame.tournament.Contestant;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * How an NPC plays the final in the world. While it fights, the decisions come from the pure {@link NpcBrain}
 * (attacker and defender policies, personality driven, reaction delay, only the public view of the opponent) and are
 * turned into exactly the controls a player has: the buttons of the {@link Duel}, plus walking and looking, which the
 * game applies to the body. The game calls {@link #think} once per tick for the fighters, so that the reduced tick
 * rate of far-away NPCs can never change the outcome of a duel; the entity's own AI step only drives the audience.
 */
final class FinalNpcBehavior implements NpcBehavior {
    private final Contestant contestant;
    @Nullable
    private NpcBrain brain;
    private int slot = -1;
    private boolean attackDown;
    private Vec3 watch = Vec3.ZERO;

    FinalNpcBehavior(Contestant contestant) {
        this.contestant = contestant;
    }

    /** The NPC now fights in {@code duel} as {@code slot}. */
    void enter(Duel duel, int slot, Difficulty difficulty, Rng rng) {
        this.slot = slot;
        this.attackDown = false;
        this.brain = new NpcBrain(slot, contestant.personality, difficulty, duel.params(), duel.court(), rng);
    }

    /** The duel is over (or the NPC is no longer in it): back to the audience. */
    void leave() {
        brain = null;
        slot = -1;
        attackDown = false;
    }

    boolean fighting() {
        return brain != null;
    }

    void watch(Vec3 courtCenter) {
        this.watch = courtCenter;
    }

    /** Decides this tick, presses the buttons on the duel and returns the intent (walking and looking). */
    NpcBrain.Intent think(Duel duel) {
        NpcBrain.Intent in = brain.think(duel);
        duel.guard(slot, in.guard());
        if (in.attackHeld() && !attackDown) {
            duel.attackDown(slot);
        } else if (!in.attackHeld() && attackDown) {
            duel.attackUp(slot);
        }
        attackDown = in.attackHeld() && !in.guard();
        if (in.shove()) {
            duel.shove(slot);
        }
        if (in.dodge()) {
            duel.dodge(slot, in.dodgeX(), in.dodgeZ());
        }
        duel.setSprinting(slot, in.speed() > 1.05 && duel.canSprint(slot));
        return in;
    }

    /** The velocity (blocks per tick) the NPC wants this tick: its walking speed scaled by what it is doing. */
    double[] wish(NpcBrain.Intent in, Duel duel) {
        double speed = in.speed();
        if (speed > 1.05 && !duel.canSprint(slot)) {
            speed = 1.0;
        }
        double mag = FinaleRules.WALK * speed * duel.speedFactor(slot);
        return new double[]{in.dirX() * mag, in.dirZ() * mag};
    }

    @Override
    public void start(ContestantEntity npc) {
        npc.setActivity(Activity.NONE);
    }

    /** The audience: stand, watch the court. */
    @Override
    public void tick(ContestantEntity npc) {
        if (brain != null) {
            return;     // fighters are driven by the game every tick
        }
        if (!watch.equals(Vec3.ZERO) && npc.tickCount % 20 == 0) {
            npc.faceToward(watch);
        }
    }

    @Override
    public void stop(ContestantEntity npc) {
        leave();
    }

    @Override
    public void onStuck(ContestantEntity npc) {
        // nothing: fighters are not path-finding and the audience does not move
    }
}
