package com.squidgame.game.finale;

import com.squidgame.SquidGameMod;
import com.squidgame.core.Difficulty;
import com.squidgame.core.finale.Act;
import com.squidgame.core.finale.CombatEvent;
import com.squidgame.core.finale.Duel;
import com.squidgame.core.finale.FighterView;
import com.squidgame.core.finale.FinaleRules;
import com.squidgame.core.finale.InputGate;
import com.squidgame.core.finale.NpcBrain;
import com.squidgame.core.finale.Role;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.GameContext;
import com.squidgame.net.ModNetwork;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * One live duel in the world: the rule engine ({@link Duel}), the two bodies and everything between them. Every tick
 * it reads the poses of the bodies, lets NPC brains press their buttons and applies the buttons players have sent,
 * steps the rules, shows the resulting events, moves the NPC bodies, slows players according to their state and sends
 * the overlay state to the clients.
 *
 * <p>The same fighter can be driven by a player or by an AI (a disconnected player is replaced by a stand-in with the
 * same number, and takes the controls back when they return): the body is resolved from the contestant every tick,
 * while health, stamina and the running action stay in the duel.
 */
final class FightSession {
    /** One fighter: who it is, its body and what its client has asked for. */
    static final class Slot {
        final int index;
        final Contestant contestant;
        FightBody body;
        FinalNpcBehavior npc;
        final InputGate gate = new InputGate();
        boolean attackHeld, guardHeld;
        long attackBeat, guardBeat;
        int missing;
        int tookSeq, tookKind, tookAmount, dealtSeq, dealtKind, dealtAmount, deniedSeq;

        Slot(int index, Contestant contestant) {
            this.index = index;
            this.contestant = contestant;
        }
    }

    /** Ticks without any body after which a fighter forfeits (an offline player without a stand-in). */
    private static final int MISSING_FORFEIT = 80;

    final Duel duel;
    final Slot[] slots = new Slot[2];
    private final GameContext ctx;
    private final FinaleArena arena;
    private final FightEffects fx;
    private final Difficulty difficulty;
    private final FightBody[] bodies = new FightBody[2];
    private final long seed;
    private int ticks;
    /** The duel numbering the overlay shows (sent with every state; 0 means "as before"). */
    private int lastDuelNo, lastDuelTotal;

    FightSession(GameContext ctx, FinaleArena arena, Duel duel, Contestant first, Contestant second) {
        this.ctx = ctx;
        this.arena = arena;
        this.duel = duel;
        this.fx = new FightEffects(ctx);
        this.difficulty = ctx.difficulty();
        this.seed = ctx.rng().seed() ^ (first.number * 31L + second.number);
        slots[0] = new Slot(0, first);
        slots[1] = new Slot(1, second);
        bind();
    }

    Slot slotOf(Contestant c) {
        for (Slot s : slots) {
            if (s.contestant == c) {
                return s;
            }
        }
        return null;
    }

    // ================================================================== bodies

    /**
     * Resolves the bodies; a fighter without any body for too long forfeits. Returns false if one is missing now.
     * An eliminated fighter keeps the body it had (the tournament shows it collapsing) and is not touched any more.
     */
    boolean bind() {
        boolean ok = true;
        for (Slot s : slots) {
            if (!s.contestant.isAlive()) {
                continue;
            }
            LivingEntity e = s.contestant.body(ctx.level);
            if (e == null) {
                s.missing++;
                ok = false;
                if (s.missing > MISSING_FORFEIT) {
                    duel.forfeit(s.index);
                }
                continue;
            }
            s.missing = 0;
            boolean player = e instanceof ServerPlayer;
            if (s.body != null && s.body.entity() == e && s.body.isPlayer() == player) {
                if (e instanceof ContestantEntity npc) {
                    attachBrain(s, npc);
                }
                bodies[s.index] = s.body;
                continue;
            }
            if (s.body != null) {
                s.body.release();
            }
            if (e instanceof ServerPlayer p) {
                if (s.npc != null) {
                    s.npc.leave();
                    s.npc = null;
                }
                s.body = new PlayerBody(p);
            } else if (e instanceof ContestantEntity npc) {
                s.body = new NpcBody(npc);
                attachBrain(s, npc);
            }
            bodies[s.index] = s.body;
            // a new body starts with clean button states
            s.attackHeld = false;
            s.guardHeld = false;
        }
        return ok;
    }

    /**
     * Makes sure the behaviour of an NPC fighter is the one that plays this duel: the game replaces the behaviours of
     * all NPCs when it starts, and a stand-in arrives with a fresh one. Nothing is attached once the duel is over.
     */
    private void attachBrain(Slot s, ContestantEntity npc) {
        if (duel.finished()) {
            return;
        }
        FinalNpcBehavior current = npc.behavior() instanceof FinalNpcBehavior b ? b : null;
        if (current != null && current == s.npc && current.fighting()) {
            return;
        }
        s.npc = current != null ? current : new FinalNpcBehavior(s.contestant);
        if (current == null) {
            npc.setBehavior(s.npc);
        }
        s.npc.enter(duel, s.index, difficulty, new Rng(seed ^ (s.index * 7919L)));
        s.npc.watch(arena.center);
    }

    boolean hasBodies() {
        return bodies[0] != null && bodies[1] != null;
    }

    /** Hands the bodies back (modifiers removed, behaviours released). Idempotent. */
    void release() {
        for (Slot s : slots) {
            if (s.body != null) {
                s.body.release();
                if (s.body.entity() instanceof ContestantEntity npc && s.contestant.isAlive()) {
                    npc.setActivity(Activity.NONE);
                }
            }
            if (s.npc != null) {
                s.npc.leave();
            }
            s.body = null;
            s.npc = null;
        }
        bodies[0] = null;
        bodies[1] = null;
    }

    // ================================================================== inputs from players

    /** A validated command from the player of {@code slot}. */
    void accept(Slot s, InputGate.Command cmd, ServerPlayer player) {
        long now = ctx.now();
        switch (cmd.kind()) {
            case ATTACK -> {
                if (cmd.down()) {
                    s.attackBeat = now;
                    if (!s.attackHeld) {
                        s.attackHeld = true;
                        duel.attackDown(s.index);
                    }
                } else if (s.attackHeld) {
                    s.attackHeld = false;
                    duel.attackUp(s.index);
                }
            }
            case GUARD -> {
                if (cmd.down()) {
                    s.guardBeat = now;
                    s.guardHeld = true;
                    duel.guard(s.index, true);
                    s.attackHeld = false;
                } else {
                    s.guardHeld = false;
                    duel.guard(s.index, false);
                }
            }
            case SHOVE -> duel.shove(s.index);
            case DODGE -> {
                double[] dir = InputGate.dodgeDirection(player.getYRot(), cmd.sector());
                duel.dodge(s.index, dir[0], dir[1]);
            }
        }
    }

    /** A left click on an entity that reached the server as a vanilla attack: treated as a light strike. */
    void tap(Slot s) {
        if (!s.attackHeld) {
            duel.attackDown(s.index);
            duel.attackUp(s.index);
        }
    }

    // ================================================================== frozen phases (intro, outro)

    /**
     * Holds the bodies still. Before the signal ({@code ready}) the fighters stand facing each other in their stance and
     * cannot move; after the duel the winner is only kept from running on (and keeps whatever the game made it do: cheer).
     */
    void freeze(boolean ready) {
        bind();
        for (Slot s : slots) {
            if (s.body == null || !s.contestant.isAlive()) {
                continue;
            }
            s.body.setSpeedFactor(ready ? 0.02 : 1.0);
            if (s.body instanceof NpcBody nb) {
                nb.move(0, 0, arena.court);
                if (ready) {
                    ContestantEntity npc = nb.npc();
                    Slot other = slots[1 - s.index];
                    if (other.body != null) {
                        npc.faceToward(other.body.entity().position());
                    }
                    npc.setActivity(Activity.FIGHT_STANCE);
                }
            }
        }
    }

    // ================================================================== the fight tick

    void tick() {
        ticks++;
        long now = ctx.now();
        bind();
        if (!hasBodies()) {
            return;
        }
        // 1. poses
        for (Slot s : slots) {
            LivingEntity e = s.body.entity();
            duel.setPose(s.index, e.getX(), e.getZ(), e.getYRot());
            if (s.body instanceof PlayerBody pb) {
                duel.setSprinting(s.index, pb.player().isSprinting());
            }
        }
        // 2. controls: NPC brains press buttons, held buttons of players expire when their client went quiet
        NpcBrain.Intent[] intents = new NpcBrain.Intent[2];
        for (Slot s : slots) {
            if (s.body instanceof NpcBody && s.npc != null && s.npc.fighting()) {
                intents[s.index] = s.npc.think(duel);
            } else {
                if (s.attackHeld && now - s.attackBeat > InputGate.HOLD_TIMEOUT) {
                    s.attackHeld = false;
                    duel.attackUp(s.index);
                }
                if (s.guardHeld && now - s.guardBeat > InputGate.HOLD_TIMEOUT) {
                    s.guardHeld = false;
                    duel.guard(s.index, false);
                }
            }
        }
        // 3. the rules
        List<CombatEvent> events = duel.step();
        for (CombatEvent e : events) {
            apply(e);
        }
        // 4. movement, slowdown, looks
        for (Slot s : slots) {
            NpcBrain.Intent in = intents[s.index];
            if (s.body instanceof NpcBody nb && in != null) {
                double[] wish = s.npc.wish(in, duel);
                nb.move(wish[0], wish[1], arena.court);
                nb.npc().faceYaw((float) in.yaw());
                syncActivity(nb.npc(), s.index, in);
            } else {
                s.body.move(0, 0, arena.court);
                s.body.setSpeedFactor(duel.speedFactor(s.index));
            }
        }
        telegraph();
        sendState(FightStatePayload.FIGHT, 0, 0, 0);
    }

    private void apply(CombatEvent e) {
        fx.play(e, bodies);
        switch (e.type()) {
            case HIT -> {
                slots[e.target()].body.cancelDash();
                took(e.target(), kindOf(e), e.amount());
                dealt(e.actor(), kindOf(e), e.amount());
            }
            case BLOCKED -> {
                took(e.target(), FightStatePayload.HIT_BLOCKED, e.amount());
                dealt(e.actor(), FightStatePayload.HIT_BLOCKED, e.amount());
            }
            case PARRIED -> {
                took(e.actor(), FightStatePayload.HIT_PARRIED, 0);
                dealt(e.target(), FightStatePayload.HIT_PARRIED, 0);
            }
            case GUARD_BREAK -> {
                slots[e.target()].body.cancelDash();
                took(e.target(), FightStatePayload.HIT_BREAK, e.amount());
                dealt(e.actor(), FightStatePayload.HIT_BREAK, e.amount());
            }
            case DODGED -> {
                took(e.target(), FightStatePayload.HIT_DODGED, 0);
                dealt(e.actor(), FightStatePayload.HIT_DODGED, 0);
            }
            case DENIED -> slots[e.actor()].deniedSeq++;
            default -> {
            }
        }
        if (e.type() == CombatEvent.Type.DODGE) {
            slots[e.actor()].body.dash(e.ix(), e.iz(), (int) e.amount());
        } else if (e.hasImpulse()) {
            slots[e.impulseSlot()].body.impulse(e.ix(), e.iz());
        }
    }

    private static int kindOf(CombatEvent e) {
        return switch (e.kind()) {
            case HEAVY -> FightStatePayload.HIT_HEAVY;
            case SHOVE -> FightStatePayload.HIT_SHOVE;
            default -> FightStatePayload.HIT_LIGHT;
        };
    }

    private void took(int slot, int kind, double amount) {
        Slot s = slots[slot];
        s.tookSeq++;
        s.tookKind = kind;
        s.tookAmount = (int) Math.round(amount);
    }

    private void dealt(int slot, int kind, double amount) {
        Slot s = slots[slot];
        s.dealtSeq++;
        s.dealtKind = kind;
        s.dealtAmount = (int) Math.round(amount);
    }

    /** What an NPC body shows: a run or a stance, the guard, the charge. */
    private void syncActivity(ContestantEntity npc, int slot, NpcBrain.Intent in) {
        FighterView v = duel.view(slot);
        FighterView foe = duel.view(1 - slot);
        Activity want;
        if (v.act() == Act.CHARGE) {
            want = Activity.MARBLE_WINDUP;
        } else if (v.guardUp()) {
            want = Activity.BLOCK;
        } else if (v.act() == Act.NONE && in.speed() > 1.05 && v.dist(foe) > 6) {
            want = Activity.SPRINT_ATTACK;
        } else if (v.dist(foe) > 8 && v.act() == Act.NONE) {
            want = Activity.NONE;
        } else {
            want = Activity.FIGHT_STANCE;
        }
        if (npc.getActivity() != want) {
            npc.setActivity(want);
        }
    }

    /** Visible telegraphs for bodies that have no animation for them (players): sparks while charging and guarding. */
    private void telegraph() {
        for (Slot s : slots) {
            FighterView v = duel.view(s.index);
            LivingEntity e = s.body.entity();
            double yaw = Math.toRadians(e.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            if (v.act() == Act.CHARGE && ticks % 2 == 0) {
                ctx.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX() + fx * 0.5, e.getY() + 1.2, e.getZ() + fz * 0.5, 2, 0.2, 0.2, 0.2, 0.3);
                if (v.charge() >= FinaleRules.HEAVY_MIN_HOLD && ticks % 4 == 0) {
                    ctx.level.sendParticles(ParticleTypes.CRIT, e.getX() + fx * 0.5, e.getY() + 1.2, e.getZ() + fz * 0.5, 3, 0.25, 0.25, 0.25, 0.1);
                }
            } else if (v.guardUp() && ticks % 6 == 0 && s.body.isPlayer()) {
                ctx.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX() + fx * 0.6, e.getY() + 1.1, e.getZ() + fz * 0.6, 2, 0.25, 0.3, 0.25, 0.01);
            }
        }
    }

    // ================================================================== state for the clients

    /** Sends the overlay state to everybody in the arena (every tick to fighters, every other tick to the rest). */
    void sendState(int stage, int coinTick, int duelNo, int duelTotal) {
        if (duelNo == 0) {
            duelNo = lastDuelNo;
            duelTotal = lastDuelTotal;
        } else {
            lastDuelNo = duelNo;
            lastDuelTotal = duelTotal;
        }
        int atk = duel.slotOf(Role.ATTACKER);
        int def = 1 - atk;
        FighterView va = duel.view(atk), vd = duel.view(def);
        int[] number = {slots[atk].contestant.number, slots[def].contestant.number};
        int[] health = {tenths(va.health()), tenths(vd.health())};
        int[] stamina = {tenths(va.stamina()), tenths(vd.stamina())};
        int[] flags = {flags(va), flags(vd)};
        int capture = (int) Math.round(duel.captureProgress() * 100);
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            Contestant me = ctx.of(p);
            Slot mine = me == null ? null : slotOf(me);
            if (mine == null && (ctx.gameTicks() & 1) == 1) {
                continue;
            }
            int role = mine == null ? 0 : (mine.index == atk ? 1 : 2);
            int edge = -1;
            int[] took = {0, 0, 0}, dealt = {0, 0, 0};
            int denied = 0;
            if (mine != null) {
                edge = (int) Math.round(Math.max(0, arena.court.edgeDistance(p.getX(), p.getZ())) * 10);
                took = new int[]{mine.tookSeq, mine.tookKind, mine.tookAmount};
                dealt = new int[]{mine.dealtSeq, mine.dealtKind, mine.dealtAmount};
                denied = mine.deniedSeq;
            }
            ModNetwork.send(p, new FightStatePayload(stage, role, number, health, stamina, flags, capture, duel.ticksLeft(),
                    new int[]{duelNo, duelTotal}, edge, took, dealt, coinTick, denied));
        }
    }

    private static int tenths(double v) {
        return (int) Math.max(0, Math.min(1000, Math.round(v * 10)));
    }

    private static int flags(FighterView v) {
        int f = 0;
        if (v.guardUp()) {
            f |= FightStatePayload.FLAG_GUARD;
        }
        if (v.exhausted()) {
            f |= FightStatePayload.FLAG_EXHAUSTED;
        }
        if (v.invulnerable()) {
            f |= FightStatePayload.FLAG_INVULNERABLE;
        }
        if (v.act() == Act.CHARGE) {
            f |= FightStatePayload.FLAG_CHARGING;
        }
        if (v.act().isStagger()) {
            f |= FightStatePayload.FLAG_STAGGERED;
        }
        return f;
    }

    /** Logs one line about the fight that has just ended: who won how, and what each fighter did. */
    void logOutcome() {
        Duel.Outcome o = duel.outcome();
        if (o != null) {
            SquidGameMod.LOGGER.info("Final duel: No. {} ({}) beat No. {} ({}) by {} after {} ticks | winner {} | loser {}",
                    slots[o.winner()].contestant.number, duel.role(o.winner()), slots[o.loser()].contestant.number,
                    duel.role(o.loser()), o.reason(), o.tick(), describe(duel.summary(o.winner())), describe(duel.summary(o.loser())));
        }
    }

    private static String describe(Duel.Summary s) {
        return String.format("hp %.0f lights %d heavies %d shoves %d dodges %d hits %d/%d blocked %d parries %d whiffs %d", s.health(),
                s.lights(), s.heavies(), s.shoves(), s.dodges(), s.hitsLanded(), s.hitsTaken(), s.blocked(), s.parries(), s.whiffs());
    }
}
