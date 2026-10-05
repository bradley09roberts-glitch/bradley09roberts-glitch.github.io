package com.squidgame.game.marbles;

import com.squidgame.SquidGameMod;
import com.squidgame.core.marbles.PairingPlanner;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.GameContext;
import com.squidgame.net.OpenScreenPayload;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Contestant;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1: the partnership market in the square. Anyone (human or NPC) can propose a partnership to any unpaired
 * contestant; an NPC answers after a personality-dependent think time ({@link PairingPlanner#acceptChance}), a human
 * gets the modal request screen. Agreements are final. Spam is limited: one outstanding proposal per proposer, one
 * pending offer per target, a pause between proposals and a cooldown after a refusal.
 */
final class PairingManager {
    static final String SCREEN = MarblesNet.SCREEN_PAIR_REQUEST;
    private static final int HUMAN_ANSWER_SECONDS = 12;

    enum Result {
        OK, CLOSED, SELF, INVALID, YOU_PAIRED, TARGET_PAIRED, TARGET_BUSY, YOU_BUSY, COOLDOWN, DECLINED_RECENTLY
    }

    /** A pending offer; {@code answerAt} is when an NPC target decides (humans answer on their own). */
    private record Proposal(int from, int to, long answerAt, long expiresAt) {
    }

    private final MarblesGame game;
    private final GameContext ctx;
    private final Rng rng;
    private final Map<Integer, Integer> partner = new LinkedHashMap<>();
    private final Map<Integer, Proposal> incoming = new LinkedHashMap<>();
    private final Map<Integer, Proposal> outgoing = new HashMap<>();
    private final Map<Long, Long> blockedUntil = new HashMap<>();
    private final Map<Integer, Long> lastAsk = new HashMap<>();
    private int pairsFormed;

    PairingManager(MarblesGame game, GameContext ctx, Rng rng) {
        this.game = game;
        this.ctx = ctx;
        this.rng = rng;
    }

    private static long key(int from, int to) {
        return from * 100_000L + to;
    }

    int pairsFormed() {
        return pairsFormed;
    }

    /** Agreed partnerships (both directions) for the final pairing. */
    Map<Integer, Integer> locked() {
        return partner;
    }

    boolean isPaired(int number) {
        return partner.containsKey(number);
    }

    int partnerOf(int number) {
        return partner.getOrDefault(number, -1);
    }

    boolean hasOutstanding(int number) {
        return outgoing.containsKey(number);
    }

    /** The contestant an NPC is waiting for an answer from, or -1. */
    int outstandingTarget(int number) {
        Proposal p = outgoing.get(number);
        return p == null ? -1 : p.to;
    }

    boolean hasIncomingHuman(int number) {
        return incoming.containsKey(number);
    }

    /** True when everybody who can be paired is (an odd one out has no partner) and no offer is pending. */
    boolean everyonePaired() {
        int alive = ctx.alive().size();
        return incoming.isEmpty() && alive - partner.size() <= (alive & 1);
    }

    // ------------------------------------------------------------------ proposals

    Result propose(Contestant from, Contestant to) {
        long now = game.clock();
        if (!game.pairingOpen()) {
            return Result.CLOSED;
        }
        if (from.number == to.number) {
            return Result.SELF;
        }
        if (!from.isAlive() || !to.isAlive()) {
            return Result.INVALID;
        }
        if (partner.containsKey(from.number)) {
            return Result.YOU_PAIRED;
        }
        if (partner.containsKey(to.number)) {
            return Result.TARGET_PAIRED;
        }
        if (outgoing.containsKey(from.number)) {
            return Result.YOU_BUSY;
        }
        Long block = blockedUntil.get(key(from.number, to.number));
        if (block != null && block > now) {
            return Result.DECLINED_RECENTLY;
        }
        Long last = lastAsk.get(from.number);
        if (last != null && now - last < game.ticks(1.5)) {
            return Result.COOLDOWN;
        }
        Proposal reverse = outgoing.get(to.number);
        if (reverse != null && reverse.to == from.number) {
            // both asked each other: that is an agreement
            cancel(reverse);
            lock(from, to);
            return Result.OK;
        }
        if (incoming.containsKey(to.number)) {
            return Result.TARGET_BUSY;
        }
        lastAsk.put(from.number, now);
        boolean toHuman = to.isHumanControlled();
        long answerAt = toHuman ? Long.MAX_VALUE : now + game.ticks(PairingPlanner.answerDelayTicks(to.personality, rng) / 20.0);
        long expiresAt = toHuman ? now + game.ticks(HUMAN_ANSWER_SECONDS) : answerAt + 1;
        Proposal p = new Proposal(from.number, to.number, answerAt, expiresAt);
        incoming.put(to.number, p);
        outgoing.put(from.number, p);
        ContestantEntity asker = ctx.npc(from);
        if (asker != null) {
            asker.triggerAction("wave");
            Vec3 tp = to.position(ctx.level);
            if (tp != null) {
                asker.lookAtPos(tp.add(0, 1.5, 0));
            }
        }
        ContestantEntity askedBody = ctx.npc(to);
        if (askedBody != null && to.isAiControlled()) {
            askedBody.setActivity(Activity.THINK);
        }
        if (from.isHumanControlled()) {
            game.tell(from, Component.translatable("squidgame.game.marbles.pair.asked", to.displayNumber()));
        }
        ServerPlayer human = to.isHumanControlled() ? to.player(ctx.server()) : null;
        if (human != null) {
            CompoundTag n = new CompoundTag();
            n.putInt("from", from.number);
            n.putString("name", from.name);
            n.putInt("total", (int) (expiresAt - now));
            n.putInt("remaining", (int) (expiresAt - now));
            ctx.screen(human, SCREEN, OpenScreenPayload.OPEN, n);
            human.playNotifySound(ModSounds.UI_NUMBER_CALL, SoundSource.MASTER, 1.0f, 1.2f);
        }
        return Result.OK;
    }

    /** A human answered the modal. */
    void answer(Contestant target, int fromNumber, boolean accept) {
        Proposal p = incoming.get(target.number);
        if (p == null || p.from != fromNumber) {
            return;
        }
        Contestant from = ctx.byNumber(p.from);
        if (from == null || !from.isAlive()) {
            cancel(p);
            return;
        }
        if (accept) {
            cancel(p);
            lock(from, target);
        } else {
            decline(p, from, target);
        }
    }

    void tick(long now) {
        if (incoming.isEmpty()) {
            return;
        }
        for (Proposal p : new ArrayList<>(incoming.values())) {
            Contestant from = ctx.byNumber(p.from);
            Contestant to = ctx.byNumber(p.to);
            if (from == null || to == null || !from.isAlive() || !to.isAlive()) {
                cancel(p);
                continue;
            }
            if (to.isAiControlled() && now >= p.answerAt) {
                double dist = distance(from, to);
                boolean busy = outgoing.containsKey(to.number) && outgoing.get(to.number) != p;
                if (rng.chance(PairingPlanner.acceptChance(to.personality, dist, busy))) {
                    cancel(p);
                    lock(from, to);
                } else {
                    decline(p, from, to);
                }
            } else if (now >= p.expiresAt) {
                decline(p, from, to);
            }
        }
    }

    private void decline(Proposal p, Contestant from, Contestant to) {
        cancel(p);
        blockedUntil.put(key(p.from, p.to), game.clock() + game.ticks(12));
        ContestantEntity body = ctx.npc(to);
        if (body != null && to.isAiControlled()) {
            body.setActivity(Activity.NONE);
            body.triggerAction("shake_head");
        }
        if (from.isHumanControlled()) {
            game.tell(from, Component.translatable("squidgame.game.marbles.pair.declined", to.displayNumber()));
            ServerPlayer hp = from.player(ctx.server());
            if (hp != null) {
                hp.playNotifySound(ModSounds.UI_DENY, SoundSource.MASTER, 0.8f, 1.0f);
            }
        }
        closeModal(to);
    }

    /** Removes a proposal from both indexes and closes the target's modal. */
    private void cancel(Proposal p) {
        incoming.remove(p.to, p);
        outgoing.remove(p.from, p);
        Contestant to = ctx.byNumber(p.to);
        if (to != null) {
            closeModal(to);
            ContestantEntity body = ctx.npc(to);
            if (body != null && to.isAiControlled() && body.getActivity() == Activity.THINK) {
                body.setActivity(Activity.NONE);
            }
        }
    }

    private void closeModal(Contestant c) {
        if (c.isHumanControlled()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null) {
                ctx.screen(p, SCREEN, OpenScreenPayload.CLOSE, new CompoundTag());
            }
        }
    }

    private void lock(Contestant a, Contestant b) {
        partner.put(a.number, b.number);
        partner.put(b.number, a.number);
        pairsFormed++;
        for (Contestant c : new Contestant[]{a, b}) {
            Proposal in = incoming.get(c.number);
            if (in != null) {
                Contestant other = ctx.byNumber(in.from);
                cancel(in);
                if (other != null && other.isHumanControlled()) {
                    game.tell(other, Component.translatable("squidgame.game.marbles.pair.taken", c.displayNumber()));
                }
            }
            Proposal out = outgoing.get(c.number);
            if (out != null) {
                cancel(out);
            }
        }
        for (Contestant c : new Contestant[]{a, b}) {
            Contestant other = c == a ? b : a;
            ContestantEntity body = ctx.npc(c);
            if (body != null && c.isAiControlled()) {
                body.triggerAction("nod");
            }
            Vec3 pos = c.position(ctx.level);
            if (pos != null) {
                ctx.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 2.1, pos.z, 6, 0.3, 0.2, 0.3, 0.0);
            }
            if (c.isHumanControlled()) {
                ServerPlayer p = c.player(ctx.server());
                game.tell(c, Component.translatable("squidgame.game.marbles.pair.locked", other.displayNumber(), other.name));
                if (p != null) {
                    p.playNotifySound(ModSounds.UI_CONFIRM, SoundSource.MASTER, 1.0f, 1.0f);
                    p.sendSystemMessage(Component.translatable("squidgame.game.marbles.pair.locked", other.displayNumber(), other.name));
                }
            }
        }
        SquidGameMod.LOGGER.debug("Marbles: No. {} and No. {} agreed to partner up", a.displayNumber(), b.displayNumber());
    }

    /** A contestant left the game (eliminated by the tournament or gone): breaks their agreement, cancels their offers. */
    void remove(int number) {
        Proposal in = incoming.get(number);
        if (in != null) {
            cancel(in);
        }
        Proposal out = outgoing.get(number);
        if (out != null) {
            cancel(out);
        }
        Integer other = partner.remove(number);
        if (other != null) {
            partner.remove(other);
            pairsFormed = Math.max(0, pairsFormed - 1);
            Contestant o = ctx.byNumber(other);
            if (o != null && o.isHumanControlled()) {
                game.tell(o, Component.translatable("squidgame.game.marbles.pair.partner_left", String.format("%03d", number)));
            }
        }
    }

    /** A human went offline (their stand-in decides from now on): their modal is gone with the connection. */
    void onControllerChanged(Contestant c) {
        Proposal in = incoming.get(c.number);
        if (in != null && c.isAiControlled()) {
            // the stand-in answers like any NPC
            Proposal replaced = new Proposal(in.from, in.to, game.clock() + game.ticks(1.5), game.clock() + game.ticks(1.5) + 1);
            incoming.put(c.number, replaced);
            outgoing.put(in.from, replaced);
        }
    }

    // ------------------------------------------------------------------ helpers for NPC behaviour

    /** Unpaired contestants an NPC may ask, with their distance and whether they refused it recently. */
    List<PairingPlanner.Candidate> candidatesFor(Contestant me) {
        long now = game.clock();
        Vec3 mine = me.position(ctx.level);
        List<PairingPlanner.Candidate> out = new ArrayList<>();
        if (mine == null) {
            return out;
        }
        for (Contestant c : ctx.alive()) {
            if (c.number == me.number || partner.containsKey(c.number) || incoming.containsKey(c.number)) {
                continue;
            }
            Vec3 p = c.position(ctx.level);
            if (p == null) {
                continue;
            }
            Long block = blockedUntil.get(key(me.number, c.number));
            out.add(new PairingPlanner.Candidate(c.number, p.distanceTo(mine), block != null && block > now));
        }
        return out;
    }

    private double distance(Contestant a, Contestant b) {
        Vec3 pa = a.position(ctx.level), pb = b.position(ctx.level);
        return pa == null || pb == null ? 10.0 : pa.distanceTo(pb);
    }

    /** Messages for the result of a human proposal. */
    void report(Contestant from, Contestant to, Result r) {
        if (r == Result.OK || !from.isHumanControlled()) {
            return;
        }
        String key = switch (r) {
            case CLOSED -> "closed";
            case SELF, INVALID -> "invalid";
            case YOU_PAIRED -> "you_paired";
            case TARGET_PAIRED -> "target_paired";
            case TARGET_BUSY -> "busy";
            case YOU_BUSY -> "you_busy";
            case COOLDOWN -> "cooldown";
            case DECLINED_RECENTLY -> "declined_recently";
            default -> "invalid";
        };
        Component msg = Component.translatable("squidgame.game.marbles.pair." + key,
                to.displayNumber(), partnerOf(from.number) >= 0 ? String.format("%03d", partnerOf(from.number)) : "-");
        game.tell(from, msg);
        ServerPlayer p = from.player(ctx.server());
        if (p != null) {
            p.playNotifySound(ModSounds.UI_DENY, SoundSource.MASTER, 0.6f, 1.0f);
        }
    }
}
