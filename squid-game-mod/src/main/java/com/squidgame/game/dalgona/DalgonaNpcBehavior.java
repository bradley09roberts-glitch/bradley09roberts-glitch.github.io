package com.squidgame.game.dalgona;

import com.squidgame.core.Personality;
import com.squidgame.core.dalgona.NpcCarver;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import com.squidgame.game.GameContext;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Contestant;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * How an NPC plays the honeycomb game: it sits at its desk, glances around while waiting and choosing a tin, then
 * carves with {@link NpcCarver} - the very same cookie physics a player is judged by. The carver only sees what a
 * contestant at that desk can see (its own groove, the carved parts, its stress meter, its licks, the clock); the body
 * language shows its decisions: head down while carving, a pause to let the stress drain, the lick animation, a flinch
 * when the sugar cracks (or when a neighbour's cookie shatters), relief when the shape comes free.
 */
final class DalgonaNpcBehavior implements NpcBehavior {
    private final DalgonaGame game;
    private final Contestant contestant;
    private int glanceTimer;
    private int scratchTimer;

    DalgonaNpcBehavior(DalgonaGame game, Contestant contestant) {
        this.game = game;
        this.contestant = contestant;
    }

    @Override
    public void start(ContestantEntity npc) {
        glanceTimer = npc.rng().rangeInt(10, 60);
    }

    @Override
    public void tick(ContestantEntity npc) {
        DalgonaGame.Slot s = game.slotOf(contestant);
        if (s == null) {
            return;
        }
        switch (s.stage) {
            case WAITING, SELECTING -> idle(npc, s);
            case CARVING -> carve(npc, s);
            default -> {
            }
        }
    }

    /** Seated and waiting: looks at the board, the tin, a neighbour; nervous contestants look around more often. */
    private void idle(ContestantEntity npc, DalgonaGame.Slot s) {
        if (npc.getActivity() != Activity.DALGONA_SIT) {
            npc.setActivity(Activity.DALGONA_SIT);
        }
        if (--glanceTimer > 0) {
            return;
        }
        Personality p = npc.personality();
        glanceTimer = (int) (npc.rng().rangeInt(30, 110) * (0.6 + 0.8 * p.caution()));
        double roll = npc.rng().nextDouble();
        Vec3 table = s.anchor.tableTop();
        if (roll < 0.5 || game.context() == null) {
            npc.lookAtPos(table.add(0, 0.3, 0));
        } else if (roll < 0.8) {
            var board = game.context().marker("dalgona.board");
            npc.lookAtPos(board != null ? new Vec3(board.x(), board.y(), board.z()) : table.add(0, 3, -8));
        } else {
            npc.lookAtPos(npc.getEyePosition().add((npc.rng().nextDouble() - 0.5) * 8, (npc.rng().nextDouble() - 0.5) * 1.5, (npc.rng().nextDouble() - 0.5) * 6));
        }
    }

    private void carve(ContestantEntity npc, DalgonaGame.Slot s) {
        if (s.carver == null) {
            return;
        }
        long now = game.clock();
        int ticks = (int) Math.max(1, Math.min(8, now - s.lastStep));
        s.lastStep = now;
        NpcCarver.Step step = s.carver.step(ticks, game.ticksLeft());
        npc.setHeldItem(1);
        Vec3 table = s.anchor.tableTop();
        // the head follows the work: the cookie lies on the table in front of the contestant
        npc.lookAtPos(table.add(0, 0.25, 0));
        boolean carving = step.state() == NpcCarver.State.CARVING;
        Activity want = carving ? Activity.DALGONA_CARVE : Activity.DALGONA_SIT;
        if (npc.getActivity() != want && step.state() != NpcCarver.State.DONE && step.state() != NpcCarver.State.CRACKED) {
            npc.setActivity(want);
        }
        GameContext ctx = game.context();
        if (step.lickStarted()) {
            npc.triggerAction("dalgona_lick");
            game.lickEffects(s);
        }
        if (step.spike() || (carving && step.stressJump() > 9)) {
            npc.triggerAction("dalgona_crack");
            if (ctx != null) {
                ctx.soundAt(table, ModSounds.DALGONA_CRACK, SoundSource.PLAYERS, 0.5f, 0.9f + (float) npc.rng().nextDouble() * 0.3f);
            }
        }
        if (carving && ctx != null && --scratchTimer <= 0) {
            scratchTimer = 12 + npc.rng().nextInt(10);
            // the hall is full of tiny scratching sounds, but only worth sending when somebody is close enough to hear
            if (npc.level().getNearestPlayer(npc, 14.0) != null) {
                ctx.soundAt(table, ModSounds.NEEDLE_SCRATCH, SoundSource.PLAYERS, 0.16f, 0.85f + (float) npc.rng().nextDouble() * 0.4f);
            }
        }
    }

    @Override
    public void onWitnessElimination(ContestantEntity npc, int otherNumber, double distance) {
        DalgonaGame.Slot s = game.slotOf(contestant);
        if (s == null || s.stage != DalgonaGame.Stage.CARVING || s.carver == null || distance > 12) {
            return;
        }
        Personality p = npc.personality();
        double fear = 1.0 - p.courage();
        if (npc.rng().chance(0.15 + 0.7 * fear)) {
            // the shot (or the broken cookie next door) startles the hand: a flinch, a pause, and a more careful pace
            s.carver.startle(6 + (int) (fear * 26), 40 + (int) (fear * 90));
            npc.triggerAction("dalgona_crack");
        }
    }
}
