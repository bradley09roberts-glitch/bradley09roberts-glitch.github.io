package com.squidgame.game.finale;

import com.squidgame.SquidGameMod;
import com.squidgame.core.GameKind;
import com.squidgame.core.finale.CourtGeometry;
import com.squidgame.core.finale.Duel;
import com.squidgame.core.finale.DuelSimulator;
import com.squidgame.core.finale.FinaleRules;
import com.squidgame.core.finale.InputGate;
import com.squidgame.core.finale.Ladder;
import com.squidgame.core.finale.Replay;
import com.squidgame.core.finale.Role;
import com.squidgame.core.finale.SquidShape;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.EliminationCause;
import com.squidgame.game.GameContext;
import com.squidgame.game.GameResult;
import com.squidgame.game.MiniGame;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Game 6: the Final Squid Game. Two finalists duel on the squid-shaped court: the attacker starts in the square and
 * must stand inside the golden circle of the head for a while (or knock the defender out), the defender starts in the
 * triangle and must stop them (or push them over the white line, or simply outlast the clock). A coin toss decides
 * the roles. Touching or crossing the line, or health 0, loses.
 *
 * <h2>More than two survivors: the knockout ladder</h2>
 * The planner can send any number of survivors. They are drawn into a {@link Ladder}: a knockout bracket of duels on
 * the same court, the loser of every duel is eliminated, the winners fight on (an odd one out advances), the last duel
 * is the final and its winner is the only survivor. A duel is fought live when a human takes part or when four or
 * fewer finalists are left; the others are settled by the headless {@link DuelSimulator} behind a short, visible
 * ceremony (the two bodies replay the swings and hits of the simulation on the spot). Contestants who wait for their
 * turn watch from the gallery.
 *
 * <p>Everything is server-authoritative: the rules run in {@link Duel}, the players only send the buttons they press
 * ({@link InputGate}), and NPCs press the same buttons ({@link FinalNpcBehavior}).
 */
public final class FinalSquidGame implements MiniGame {
    private enum Stage {PREP, INTRO, FIGHT, OUTRO, CEREMONY, DONE}

    /** Length of the coin flip animation (the first duel starts when the countdown ends, whenever that is). */
    private static final int COIN_TICKS = 64;
    private static final int FIRST_INTRO = Integer.MAX_VALUE;

    @Nullable
    private FinaleArena arena;
    private CourtGeometry court;
    private FinaleRules.Params params;
    private Ladder ladder;
    private Stage stage = Stage.PREP;
    private int stageTick;
    private int stageLength;
    @Nullable
    private FightSession session;
    @Nullable
    private Ladder.Pairing pairing;
    private Role roleOfFirst = Role.ATTACKER;
    private int duelNo, savedPlayed;
    /** The loser of the duel that has just been settled (its body is removed when the next fighters are placed). */
    @Nullable
    private Contestant lastLoser;
    private int forfeitBy;
    private boolean settling;
    /** The tournament has called {@code begin}: the countdown is over and the game is ticked. */
    private boolean started;
    private boolean revealed;
    private final Set<Integer> seated = new HashSet<>();
    private int nextSeat;

    // the headless duel of the ceremony
    @Nullable
    private DuelSimulator.Result simResult;
    private Contestant[] simFighters;
    private NpcBody[] simBodies;
    private FightEffects simEffects;
    private Replay simReplay;
    private int simReplayed;
    /** Stage ticks of the ceremony at which the coin has fallen and at which the replayed duel ends. */
    private int ceremonyCoinEnd, ceremonyShowEnd;

    @Override
    public GameKind type() {
        return GameKind.FINAL;
    }

    // ================================================================== texts

    @Override
    public List<Component> instructions(GameContext ctx) {
        FinaleRules.Params p = FinaleRules.params(ctx.difficulty());
        String capture = String.format("%.1f", p.captureTicks() / 20.0);
        int minutes = p.duelTicks() / 20 / 60;
        String time = (p.duelTicks() / 20) % 60 == 0 ? minutes + " min" : p.duelTicks() / 20 + " s";
        return List.of(
                Component.translatable("squidgame.game.final.instruction.1"),
                Component.translatable("squidgame.game.final.instruction.2", capture),
                Component.translatable("squidgame.game.final.instruction.3", time),
                Component.translatable("squidgame.game.final.instruction.4"),
                Component.translatable("squidgame.game.final.instruction.5",
                        Component.keybind("key.squidgame.dash"), Component.keybind("key.squidgame.shove")),
                Component.translatable("squidgame.game.final.instruction.6", ctx.difficulty().id),
                Component.translatable("squidgame.game.final.instruction.7"));
    }

    @Override
    public Component objective(GameContext ctx, @Nullable Contestant viewer) {
        if (viewer == null || !viewer.isAlive()) {
            return Component.translatable("squidgame.game.final.objective.spectator");
        }
        Role role = roleOf(viewer);
        if (role == Role.ATTACKER) {
            return Component.translatable("squidgame.game.final.objective.attacker");
        }
        if (role == Role.DEFENDER) {
            return Component.translatable("squidgame.game.final.objective.defender");
        }
        return Component.translatable("squidgame.game.final.objective.waiting");
    }

    /** The role of a contestant in the duel in progress, or null when it is not one of the two fighters. */
    @Nullable
    private Role roleOf(Contestant c) {
        if (pairing == null || !pairing.contains(c.number)) {
            return null;
        }
        return pairing.a() == c.number ? roleOfFirst : roleOfFirst.other();
    }

    @Override
    public int timeLimitTicks(GameContext ctx) {
        FinaleRules.Params p = FinaleRules.params(ctx.difficulty());
        return Ladder.budgetTicks(ctx.alive().size(), ctx.aliveHumans().size(), p.duelTicks()) + 100;
    }

    // ================================================================== lifecycle

    @Override
    public void prepare(GameContext ctx) {
        arena = FinaleArena.read(ctx);
        if (arena == null) {
            SquidGameMod.LOGGER.error("The final arena lacks its markers; every duel is settled off the court");
            court = SquidShape.court();
        } else {
            court = arena.court;
        }
        FinaleRules.Params base = FinaleRules.params(ctx.difficulty());
        params = new FinaleRules.Params(scaled(ctx, base.duelTicks()), base.staminaMax(), base.staminaRegen(), base.regenDelay(),
                base.damageScale(), base.dodgeIframes(), base.parryWindow(), base.captureTicks());
        List<Integer> numbers = new ArrayList<>();
        for (Contestant c : ctx.alive()) {
            numbers.add(c.number);
        }
        ladder = new Ladder(numbers, ctx.rng().fork(6));
        stage = Stage.PREP;
        session = null;
        pairing = null;
        duelNo = 0;
        forfeitBy = 0;
        started = false;
        revealed = false;
        settling = false;
        lastLoser = null;
        seated.clear();
        nextSeat = 0;
        ctx.spawnGuards();
    }

    @Override
    public void placeContestants(GameContext ctx) {
        for (Contestant c : ctx.alive()) {
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setActivity(Activity.NONE);
            }
        }
        started = false;
        startDuel(ctx, true);
        ctx.schedule(1, () -> countdownPulse(ctx));
    }

    /**
     * The tournament's countdown does not tick the game, so the coin toss of the first duel (and the fighters standing
     * ready, unable to move) runs from here until the signal. A first duel that is settled by a ceremony only shows its
     * coin toss now; the replay starts with the game.
     */
    private void countdownPulse(GameContext ctx) {
        if (started || stage == Stage.PREP || stage == Stage.DONE) {
            return;
        }
        if (stage == Stage.INTRO) {
            stageTick++;
            tickIntro(ctx);
        } else if (stage == Stage.CEREMONY && stageTick < ceremonyCoinEnd) {
            stageTick++;
            tickCeremony(ctx);
        }
        ctx.schedule(1, () -> countdownPulse(ctx));
    }

    @Override
    public void begin(GameContext ctx) {
        started = true;
        ctx.assignBehaviors(c -> {
            FinalNpcBehavior b = new FinalNpcBehavior(c);
            if (arena != null) {
                b.watch(arena.center);
            }
            return b;
        });
        if (session != null) {
            session.bind();
        }
        if (stage == Stage.INTRO) {
            revealRoles(ctx);
            enterFight(ctx, false);
        }
    }

    @Override
    public void onControllerChanged(GameContext ctx, Contestant c) {
        ContestantEntity e = ctx.npc(c);
        if (e != null && c.isAlive() && stage != Stage.PREP) {
            FinalNpcBehavior b = new FinalNpcBehavior(c);
            if (arena != null) {
                b.watch(arena.center);
            }
            e.setBehavior(b);
        }
        if (session != null) {
            session.bind();
        }
    }

    // ================================================================== tick

    @Override
    public void tick(GameContext ctx) {
        stageTick++;
        switch (stage) {
            case INTRO -> tickIntro(ctx);
            case FIGHT -> tickFight(ctx);
            case OUTRO -> tickOutro(ctx);
            case CEREMONY -> tickCeremony(ctx);
            default -> {
            }
        }
    }

    /**
     * Over when the ladder has no duel left. The last duel gets its outro (the fall, the cheer) before the tournament
     * moves on: the game does not end the very tick the last loser is eliminated.
     */
    @Override
    public boolean isFinished(GameContext ctx) {
        return stage == Stage.DONE;
    }

    @Override
    public void onTimeout(GameContext ctx) {
        ctx.title(Component.translatable("squidgame.game.final.time_up"), Component.empty(), 0, 30, 10);
    }

    // ------------------------------------------------------------------ the duels

    /** Takes the next pairing from the ladder and starts it live or as a ceremony. */
    private void startDuel(GameContext ctx, boolean first) {
        Ladder.Pairing pr;
        while ((pr = ladder.next()) != null) {
            Contestant a = ctx.byNumber(pr.a()), b = ctx.byNumber(pr.b());
            boolean aOk = a != null && a.isAlive(), bOk = b != null && b.isAlive();
            if (aOk && bOk) {
                break;
            }
            ladder.withdraw(aOk ? pr.b() : pr.a());
        }
        if (pr == null) {
            stage = Stage.DONE;
            return;
        }
        pairing = pr;
        Contestant a = ctx.byNumber(pr.a()), b = ctx.byNumber(pr.b());
        roleOfFirst = ctx.rng().fork(duelNo * 977L + a.number).nextBoolean() ? Role.ATTACKER : Role.DEFENDER;
        duelNo++;
        revealed = false;
        forfeitBy = 0;
        boolean live = arena != null && Ladder.live(ladder.remaining(), a.isHuman() || b.isHuman());
        seatAudience(ctx, a, b);
        int total = Math.max(duelNo, savedPlayed + ladder.duelsPlayed() + ladder.duelsLeft());
        ctx.broadcast(Component.translatable("squidgame.game.final.chat.duel", duelNo, total, a.displayNumber(), b.displayNumber())
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        if (live) {
            Duel duel = new Duel(params, court, roleOfFirst, ctx.rng().fork(duelNo * 131L + b.number));
            session = new FightSession(ctx, arena, duel, a, b);
            stage = Stage.INTRO;
            stageTick = 0;
            stageLength = first ? FIRST_INTRO : scaled(ctx, FinaleRules.INTRO_TICKS);
            if (first) {
                placeFighters(ctx, a, b);
            } else {
                for (ServerPlayer p : Announcer.audience(ctx.server())) {
                    ctx.fade(p, 6, 10, 10, 0xFF000000);
                }
                ctx.schedule(7, () -> placeFighters(ctx, a, b));
            }
        } else {
            startCeremony(ctx, a, b);
        }
    }

    private void placeFighters(GameContext ctx, Contestant a, Contestant b) {
        if (arena == null) {
            return;
        }
        clearCorpse(ctx);
        Contestant atk = roleOfFirst == Role.ATTACKER ? a : b;
        Contestant def = atk == a ? b : a;
        ctx.teleport(atk, arena.attackerSpawn, arena.attackerYaw);
        ctx.teleport(def, arena.defenderSpawn, arena.defenderYaw);
        seated.remove(a.number);
        seated.remove(b.number);
        for (Contestant c : new Contestant[]{a, b}) {
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setActivity(Activity.FIGHT_STANCE);
            }
        }
        if (session != null) {
            session.bind();
        }
    }

    /**
     * The body of the previous duel's loser lies on the spot where the next fighters are about to stand (the screens of
     * the humans are black at this moment): it is taken away with a puff instead of letting two bodies overlap.
     */
    private void clearCorpse(GameContext ctx) {
        if (lastLoser == null) {
            return;
        }
        ContestantEntity e = ctx.npc(lastLoser);
        if (e != null && !lastLoser.isAlive()) {
            ctx.level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            e.discard();
        }
        lastLoser = null;
    }

    /** Everybody who is alive but not fighting watches from the gallery. */
    private void seatAudience(GameContext ctx, Contestant a, Contestant b) {
        if (arena == null) {
            return;
        }
        for (Contestant c : ctx.alive()) {
            if (c == a || c == b || seated.contains(c.number)) {
                continue;
            }
            int i = nextSeat++;
            ctx.teleport(c, arena.audienceSpot(i), arena.audienceYaw(i));
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setActivity(Activity.NONE);
            }
            seated.add(c.number);
        }
    }

    // ------------------------------------------------------------------ intro

    private void tickIntro(GameContext ctx) {
        if (session == null) {
            return;
        }
        if (forfeitBy != 0) {
            resolveForfeit(ctx);
            return;
        }
        session.freeze(true);
        int coinEnd = stageLength == FIRST_INTRO ? COIN_TICKS : Math.max(6, (int) (stageLength * 0.5));
        if (stageTick == 1) {
            ctx.sound(ModSounds.ANNOUNCE_CHIME, 0.8f, 1.3f);
        }
        if (stageTick < coinEnd && stageTick % 8 == 0) {
            ctx.sound(ModSounds.COUNTDOWN_TICK, 0.5f, 1.0f + 0.04f * (stageTick / 8));
        }
        if (stageTick == coinEnd) {
            revealRoles(ctx);
        }
        if (stageLength != FIRST_INTRO && stageTick == stageLength - scaled(ctx, 28)) {
            ctx.title(Component.translatable("squidgame.game.final.ready"), Component.empty(), 0, 18, 4);
            ctx.sound(ModSounds.COUNTDOWN_BEEP, 0.9f, 1f);
        }
        session.sendState(stageTick < coinEnd ? FightStatePayload.COIN : FightStatePayload.READY, stageTick, duelNo, duelTotal());
        if (stageLength != FIRST_INTRO && stageTick >= stageLength) {
            enterFight(ctx, true);
        }
    }

    /** The coin has landed: roles, stakes, who is who. */
    private void revealRoles(GameContext ctx) {
        if (revealed || pairing == null) {
            return;
        }
        revealed = true;
        Contestant a = ctx.byNumber(pairing.a()), b = ctx.byNumber(pairing.b());
        Contestant atk = roleOfFirst == Role.ATTACKER ? a : b;
        Contestant def = atk == a ? b : a;
        ctx.sound(ModSounds.UI_NUMBER_CALL, 1f, 1f);
        ctx.broadcast(Component.translatable("squidgame.game.final.chat.roles", atk.displayNumber(), def.displayNumber()));
        for (Contestant c : new Contestant[]{atk, def}) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null && c.isHumanControlled()) {
                boolean attacker = c == atk;
                Announcer.title(p, Component.translatable(attacker ? "squidgame.game.final.role.attacker" : "squidgame.game.final.role.defender"),
                        Component.translatable(attacker ? "squidgame.game.final.role.attacker.sub" : "squidgame.game.final.role.defender.sub"),
                        4, 50, 10);
                p.sendSystemMessage(Component.translatable("squidgame.game.final.chat.stakes"));
            }
        }
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            Contestant me = ctx.of(p);
            if (me != atk && me != def) {
                Announcer.title(p, Component.translatable("squidgame.game.final.role.spectator", atk.displayNumber()),
                        Component.translatable("squidgame.game.final.role.spectator.sub", def.displayNumber()), 4, 50, 10);
            }
        }
    }

    private void enterFight(GameContext ctx, boolean announce) {
        stage = Stage.FIGHT;
        stageTick = 0;
        if (announce) {
            ctx.title(Component.translatable("squidgame.game.final.fight"), Component.empty(), 0, 24, 8);
            ctx.sound(ModSounds.GAME_START_HORN, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ the fight

    private void tickFight(GameContext ctx) {
        if (session == null) {
            return;
        }
        if (forfeitBy != 0) {
            resolveForfeit(ctx);
            return;
        }
        session.tick();
        if (session.duel.finished()) {
            finishLive(ctx);
        }
    }

    private void finishLive(GameContext ctx) {
        Duel.Outcome o = session.duel.outcome();
        Contestant winner = session.slots[o.winner()].contestant;
        Contestant loser = session.slots[o.loser()].contestant;
        session.logOutcome();
        announceResult(ctx, winner, loser, o.reason(), o.tick());
        settle(ctx, winner, loser, o.reason(), session.duel.role(o.loser()));
        session.sendState(FightStatePayload.OUTRO, 0, duelNo, duelTotal());
        stage = Stage.OUTRO;
        stageTick = 0;
        stageLength = scaled(ctx, FinaleRules.OUTRO_TICKS);
    }

    private void tickOutro(GameContext ctx) {
        if (session != null) {
            session.freeze(false);
            session.sendState(FightStatePayload.OUTRO, 0, duelNo, duelTotal());
        }
        if (stageTick >= stageLength) {
            if (session != null) {
                session.release();
                session = null;
            }
            startDuel(ctx, false);
        }
    }

    // ------------------------------------------------------------------ settled behind a ceremony

    private void startCeremony(GameContext ctx, Contestant a, Contestant b) {
        long seed = ctx.rng().fork(duelNo * 1009L + a.number).seed();
        simResult = new DuelSimulator(ctx.difficulty(), court, a.personality, b.personality, roleOfFirst, seed).run();
        simFighters = new Contestant[]{a, b};
        simReplayed = 0;
        simReplay = new Replay(simResult, scaled(ctx, FinaleRules.CEREMONY_SHOW_TICKS));
        stage = Stage.CEREMONY;
        stageTick = 0;
        ceremonyCoinEnd = scaled(ctx, FinaleRules.CEREMONY_COIN_TICKS);
        ceremonyShowEnd = ceremonyCoinEnd + Math.max(1, simReplay.lengthTicks());
        stageLength = ceremonyShowEnd + scaled(ctx, FinaleRules.CEREMONY_AFTER_TICKS);
        simBodies = new NpcBody[2];
        simEffects = null;
        if (arena != null) {
            simEffects = new FightEffects(ctx);
            for (ServerPlayer p : Announcer.audience(ctx.server())) {
                ctx.fade(p, 6, 10, 10, 0xFF000000);
            }
            ctx.schedule(7, () -> placeFighters(ctx, a, b));
        }
    }

    private void tickCeremony(GameContext ctx) {
        if (simResult == null) {
            return;
        }
        if (forfeitBy != 0) {
            resolveForfeit(ctx);
            return;
        }
        int coinEnd = ceremonyCoinEnd, settleAt = ceremonyShowEnd;
        Contestant a = simFighters[0], b = simFighters[1];
        if (stageTick == coinEnd && !revealed) {
            revealed = true;
            Contestant atk = roleOfFirst == Role.ATTACKER ? a : b;
            Contestant def = atk == a ? b : a;
            ctx.sound(ModSounds.UI_NUMBER_CALL, 0.8f, 1f);
            ctx.broadcast(Component.translatable("squidgame.game.final.chat.roles", atk.displayNumber(), def.displayNumber()));
            ctx.title(Component.translatable("squidgame.game.final.ceremony.title"),
                    Component.translatable("squidgame.game.final.ceremony.sub", atk.displayNumber(), def.displayNumber()), 4, 40, 8);
        }
        if (simEffects != null && stageTick > coinEnd && stageTick <= settleAt) {
            if (simBodies[0] == null && simBodies[1] == null) {
                for (int i = 0; i < 2; i++) {
                    ContestantEntity e = ctx.npc(simFighters[i]);
                    simBodies[i] = e == null ? null : new NpcBody(e);
                    if (e != null) {
                        e.setActivity(Activity.FIGHT_STANCE);
                    }
                }
            }
            replayFrame(stageTick - coinEnd);
        }
        sendCeremonyState(ctx, stageTick < coinEnd ? FightStatePayload.COIN : FightStatePayload.CEREMONY);
        if (stageTick == settleAt) {
            DuelSimulator.Result r = simResult;
            Contestant winner = simFighters[r.outcome().winner()];
            Contestant loser = simFighters[r.outcome().loser()];
            SquidGameMod.LOGGER.info("Final duel (ceremony): No. {} ({}) beat No. {} ({}) by {} after {} ticks", winner.number,
                    r.roles()[r.outcome().winner()], loser.number, r.roles()[r.outcome().loser()], r.outcome().reason(), r.outcome().tick());
            announceResult(ctx, winner, loser, r.outcome().reason(), r.outcome().tick());
            settle(ctx, winner, loser, r.outcome().reason(), r.roles()[r.outcome().loser()]);
        }
        if (stageTick >= stageLength) {
            simResult = null;
            startDuel(ctx, false);
        }
    }

    /**
     * One frame of the replayed duel: the bodies take the poses the simulation recorded for this moment of the time warp
     * (quick where nothing happens, close to natural speed around the blows) and the swings, hits, blocks and dodges
     * up to it are played with their sounds and animations.
     */
    private void replayFrame(int showTick) {
        double st = simReplay.simTime(showTick);
        List<DuelSimulator.Timed> log = simResult.log();
        while (simReplayed < log.size() && log.get(simReplayed).tick() <= st) {
            if (simBodies[0] != null && simBodies[1] != null) {
                simEffects.play(log.get(simReplayed).event(), simBodies);
            }
            simReplayed++;
        }
        int i0 = (int) Math.floor(st), i1 = Math.min(i0 + 1, simResult.ticks());
        double f = st - i0;
        double[] x = new double[2], z = new double[2];
        for (int s = 0; s < 2; s++) {
            x[s] = simResult.x(s, i0) + (simResult.x(s, i1) - simResult.x(s, i0)) * f;
            z[s] = simResult.z(s, i0) + (simResult.z(s, i1) - simResult.z(s, i0)) * f;
        }
        double apart = Math.hypot(x[0] - x[1], z[0] - z[1]);
        for (int s = 0; s < 2; s++) {
            if (simBodies[s] == null) {
                continue;
            }
            double yaw0 = simResult.yaw(s, i0), yaw1 = simResult.yaw(s, i1);
            double yaw = yaw0 + FinaleRules.angleDiff(yaw0, yaw1) * f;
            simBodies[s].place(x[s], arena.floorY, z[s], yaw);
            // running in between, the stance when they are in each other's face (blows and guards set their own poses)
            Activity now = simBodies[s].npc().getActivity();
            if (now == Activity.FIGHT_STANCE || now == Activity.NONE) {
                simBodies[s].npc().setActivity(apart > 6 ? Activity.NONE : Activity.FIGHT_STANCE);
            }
        }
    }

    private void sendCeremonyState(GameContext ctx, int stagePhase) {
        if (stageTick % 2 != 0) {
            return;
        }
        int atkSlot = roleOfFirst == Role.ATTACKER ? 0 : 1;
        int[] number = {simFighters[atkSlot].number, simFighters[1 - atkSlot].number};
        FightStatePayload payload = new FightStatePayload(stagePhase, 0, number, new int[]{1000, 1000}, new int[]{1000, 1000},
                new int[]{0, 0}, 0, 0, new int[]{duelNo, duelTotal()}, -1, new int[]{0, 0, 0}, new int[]{0, 0, 0}, stageTick, 0);
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            ModNetwork.send(p, payload);
        }
    }

    // ------------------------------------------------------------------ results

    private void announceResult(GameContext ctx, Contestant winner, Contestant loser, Duel.Reason reason, int ticks) {
        Component why = Component.translatable("squidgame.game.final.reason." + reason.name().toLowerCase(java.util.Locale.ROOT));
        ctx.title(Component.translatable("squidgame.game.final.result.title", winner.displayNumber()), why, 0, 50, 12);
        ctx.broadcast(Component.translatable("squidgame.game.final.chat.result", winner.displayNumber(), loser.displayNumber(), why,
                String.format("%d:%02d", ticks / 20 / 60, ticks / 20 % 60)).withStyle(net.minecraft.ChatFormatting.YELLOW));
        ctx.sound(ModSounds.GAME_END_BUZZER, 0.6f, 1.2f);
    }

    /** Applies a decided duel: the loser is out, the winner advances (and cheers), the audience reacts. */
    private void settle(GameContext ctx, Contestant winner, Contestant loser, Duel.Reason reason, Role loserRole) {
        if (loser.isAlive()) {
            settling = true;
            try {
                ctx.eliminate(loser, causeOf(reason, loserRole));
            } finally {
                settling = false;
            }
        }
        if (ladder.current() != null && ladder.current().contains(winner.number)) {
            ladder.report(winner.number);
        }
        lastLoser = loser;
        pairing = null;
        ContestantEntity w = ctx.npc(winner);
        if (w != null && winner.isAiControlled()) {
            w.setBehavior(null);
            w.setActivity(Activity.CELEBRATE_FIST);
        }
        for (Contestant c : ctx.alive()) {
            ContestantEntity e = ctx.npc(c);
            if (e != null && c != winner && seated.contains(c.number) && ctx.rng().chance(0.45)) {
                e.triggerAction(ctx.rng().pick(List.of("shocked", "nod", "wave", "shake_head")));
            }
        }
    }

    private static EliminationCause causeOf(Duel.Reason reason, Role loserRole) {
        return switch (reason) {
            case KNOCKOUT -> EliminationCause.KNOCKED_OUT;
            case OUT_OF_BOUNDS -> EliminationCause.OUT_OF_BOUNDS;
            case TIMEOUT -> EliminationCause.TIMEOUT;
            case CAPTURE -> EliminationCause.LOST_MATCH;
            case FORFEIT -> EliminationCause.DISCONNECTED;
        };
    }

    // ------------------------------------------------------------------ somebody leaves

    @Override
    public void onContestantEliminated(GameContext ctx, Contestant c, EliminationCause cause) {
        seated.remove(c.number);
        if (settling || ladder == null) {
            return;
        }
        if (pairing != null && pairing.contains(c.number)) {
            forfeitBy = c.number;      // resolved in the next tick: we are inside the tournament's elimination
        } else {
            ladder.withdraw(c.number);
        }
    }

    /** A fighter was eliminated by the tournament (disconnect, administrator): the opponent wins. */
    private void resolveForfeit(GameContext ctx) {
        int gone = forfeitBy;
        forfeitBy = 0;
        if (pairing == null || !pairing.contains(gone)) {
            return;
        }
        Contestant winner = ctx.byNumber(pairing.other(gone));
        Contestant loser = ctx.byNumber(gone);
        if (winner == null || loser == null) {
            return;
        }
        if (stage == Stage.FIGHT && session != null && !session.duel.finished()) {
            session.duel.forfeit(session.slotOf(loser).index);
            finishLive(ctx);
            return;
        }
        announceResult(ctx, winner, loser, Duel.Reason.FORFEIT, 0);
        settle(ctx, winner, loser, Duel.Reason.FORFEIT, Role.DEFENDER);
        if (session != null) {
            session.release();
            session = null;
        }
        simResult = null;
        stage = Stage.OUTRO;
        stageTick = 0;
        stageLength = scaled(ctx, FinaleRules.OUTRO_TICKS / 2);
    }

    // ================================================================== the end

    @Override
    public GameResult conclude(GameContext ctx) {
        settleAll(ctx);
        List<Contestant> survivors = new ArrayList<>();
        Contestant champion = null;
        List<Contestant> out = new ArrayList<>();
        for (Contestant c : ctx.tournament.roster.all()) {
            if (c.isAlive()) {
                survivors.add(c);
                champion = champion == null ? c : champion;
            } else if (c.eliminatedInGame() == ctx.tournament.gameNumber) {
                out.add(c);
            }
        }
        if (session != null) {
            session.release();
            session = null;
        }
        victory(ctx, champion);
        Component headline = champion == null ? Component.translatable("squidgame.results.none_survived")
                : Component.translatable("squidgame.game.final.headline", champion.displayNumber());
        Component detail = Component.translatable("squidgame.game.final.detail", Math.max(1, duelNo));
        return new GameResult(survivors, out, headline, detail);
    }

    /** The game is over (or out of time): every duel that is still open is settled by the simulator. */
    private void settleAll(GameContext ctx) {
        if (ladder == null) {
            return;
        }
        if (session != null && stage == Stage.FIGHT && !session.duel.finished()) {
            session.duel.timeUp();
            finishLive(ctx);
        }
        // the duel that had not started or was in its ceremony
        if (pairing != null && ladder.current() != null) {
            Contestant a = ctx.byNumber(pairing.a()), b = ctx.byNumber(pairing.b());
            if (a != null && b != null && a.isAlive() && b.isAlive()) {
                resolveBySimulation(ctx, a, b);
            }
            pairing = null;
        }
        Ladder.Pairing pr;
        while ((pr = ladder.next()) != null) {
            Contestant a = ctx.byNumber(pr.a()), b = ctx.byNumber(pr.b());
            boolean aOk = a != null && a.isAlive(), bOk = b != null && b.isAlive();
            if (aOk && bOk) {
                resolveBySimulation(ctx, a, b);
            } else {
                ladder.withdraw(aOk ? pr.b() : pr.a());
            }
        }
        simResult = null;
    }

    /** Settles a duel on the spot, without a ceremony: the tournament reads the survivors right after {@link #conclude}. */
    private void resolveBySimulation(GameContext ctx, Contestant a, Contestant b) {
        Role first = ctx.rng().nextBoolean() ? Role.ATTACKER : Role.DEFENDER;
        DuelSimulator.Result r = new DuelSimulator(ctx.difficulty(), court, a.personality, b.personality, first,
                ctx.rng().fork(a.number * 31L + b.number).seed()).run();
        Contestant winner = r.outcome().winner() == 0 ? a : b;
        Contestant loser = winner == a ? b : a;
        ladder.report(winner.number);
        settling = true;
        try {
            ctx.eliminate(loser, causeOf(r.outcome().reason(), r.roles()[r.outcome().loser()]));
        } finally {
            settling = false;
        }
        duelNo++;
    }

    /** The champion takes the podium: fanfare, cheering, fireworks over the court. */
    private void victory(GameContext ctx, @Nullable Contestant champion) {
        if (champion == null) {
            return;
        }
        ctx.sound(ModSounds.GAME_WIN_FANFARE, 1f, 1f);
        Vec3 spot = null;
        if (arena != null) {
            spot = arena.podium != null ? new Vec3(arena.podium.x(), arena.podium.y(), arena.podium.z()) : arena.center;
            if (arena.podium != null && champion.position(ctx.level) != null) {
                ctx.teleport(champion, spot, arena.podium.yaw());
            }
        }
        ContestantEntity e = ctx.npc(champion);
        if (e != null) {
            e.setBehavior(null);
            e.stopMoving();
            e.setActivity(Activity.CELEBRATE_FIST);
        }
        for (Contestant c : ctx.alive()) {
            ContestantEntity audience = ctx.npc(c);
            if (audience != null && c != champion) {
                audience.setActivity(Activity.CELEBRATE);
            }
        }
        Vec3 where = champion.position(ctx.level);
        if (where == null) {
            where = spot;
        }
        if (where != null) {
            Vec3 at = where;
            for (int i = 0; i < 14; i++) {
                final int k = i;
                ctx.schedule(10 + i * 7, () -> fireworks(ctx, at, k));
            }
        }
    }

    private void fireworks(GameContext ctx, Vec3 c, int k) {
        int[][] palettes = {{0xED1B76, 0xFFFFFF}, {0x0FA89C, 0xFFFFFF}, {0xFFD23F, 0xED1B76}};
        double ang = k * 2.4;
        double rad = 3 + k % 4;
        int[] pal = palettes[k % palettes.length];
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion ex = new FireworkExplosion(k % 3 == 0 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(pal), IntList.of(0xFFFFFF), true, k % 2 == 0);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(ex)));
        Entity e = new FireworkRocketEntity(ctx.level, c.x + Math.cos(ang) * rad, c.y + 1, c.z + Math.sin(ang) * rad, rocket);
        e.addTag("squidgame_temp");
        ctx.level.addFreshEntity(e);
    }

    @Override
    public void cleanup(GameContext ctx) {
        if (session != null) {
            session.release();
            session = null;
        }
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            PlayerBody.clearModifiers(p);
        }
        ctx.cleanupGuards();
        simResult = null;
        simReplay = null;
        simBodies = null;
        simEffects = null;
        pairing = null;
        stage = Stage.PREP;
    }

    // ================================================================== input

    @Override
    public void onClientAction(GameContext ctx, Contestant c, ServerPlayer player, String id, CompoundTag data) {
        if (session == null || stage != Stage.FIGHT || !c.isAlive()) {
            return;
        }
        FightSession.Slot slot = session.slotOf(c);
        if (slot == null || !(slot.body instanceof PlayerBody)) {
            return;
        }
        InputGate.Command cmd = InputGate.parse(id, data.getBoolean("d"), data.getInt("k"));
        if (cmd == null || !slot.gate.allow(ctx.now())) {
            return;
        }
        session.accept(slot, cmd, player);
    }

    @Override
    public boolean onPlayerAttack(GameContext ctx, Contestant attacker, ServerPlayer player, net.minecraft.world.entity.Entity target) {
        if (session != null && stage == Stage.FIGHT && attacker.isAlive()) {
            FightSession.Slot slot = session.slotOf(attacker);
            if (slot != null && slot.body instanceof PlayerBody && slot.gate.allow(ctx.now())) {
                session.tap(slot);
            }
        }
        return true;
    }

    // ================================================================== HUD

    @Override
    public void hudWidgets(GameContext ctx, @Nullable Contestant viewer, List<HudPayload.Widget> out) {
        if (stage == Stage.PREP || stage == Stage.DONE || pairing == null && session == null && simResult == null) {
            return;
        }
        if (session != null) {
            int atk = session.duel.slotOf(Role.ATTACKER);
            out.add(HudPayload.Widget.line("duel", Component.translatable("squidgame.game.final.hud.duel", duelNo, duelTotal(),
                    session.slots[atk].contestant.displayNumber(), session.slots[1 - atk].contestant.displayNumber())));
            if (stage == Stage.FIGHT) {
                out.add(HudPayload.Widget.bar("time", Component.translatable("squidgame.game.final.hud.time"),
                        session.duel.ticksLeft(), params.duelTicks(), 0xFFD84A));
            }
        } else {
            out.add(HudPayload.Widget.line("duel", Component.translatable("squidgame.game.final.hud.ceremony", duelNo, duelTotal())));
        }
        out.add(HudPayload.Widget.line("left", Component.translatable("squidgame.game.final.hud.left", ctx.alive().size())));
    }

    // ================================================================== persistence

    @Override
    public void saveState(CompoundTag tag) {
        tag.putInt("played", ladder == null ? savedPlayed : savedPlayed + ladder.duelsPlayed());
    }

    @Override
    public void loadState(CompoundTag tag) {
        savedPlayed = tag.getInt("played");
        duelNo = savedPlayed;
    }

    // ================================================================== helpers

    private int duelTotal() {
        return Math.max(duelNo, savedPlayed + (ladder == null ? 0 : ladder.duelsPlayed() + ladder.duelsLeft()));
    }

    private static int scaled(GameContext ctx, int ticks) {
        return Math.max(2, (int) Math.round(ticks * ctx.config().timeScale));
    }

}
