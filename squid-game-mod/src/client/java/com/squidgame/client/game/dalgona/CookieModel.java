package com.squidgame.client.game.dalgona;

import com.squidgame.core.dalgona.CookieSim;
import com.squidgame.core.dalgona.CrackPattern;
import com.squidgame.core.dalgona.DalgonaRules;
import com.squidgame.core.dalgona.DalgonaShape;
import com.squidgame.game.dalgona.DalgonaNet;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's picture of one cookie: the static facts (shape, rules, crack pattern) and the dynamic state, which is
 * the last state the server sent plus a little local prediction (carved samples show up instantly where the needle
 * passes; the server's bitset is OR-ed in, so a sample never un-carves). Everything that matters (stress, licks,
 * success, cracking) is only ever taken from the server.
 */
final class CookieModel {
    /** A crack decal where the cookie took a hit. */
    static final class Decal {
        final float x;
        final float y;
        final int severity;
        final float rotation;
        final long bornMs;

        Decal(float x, float y, int severity, float rotation, long bornMs) {
            this.x = x;
            this.y = y;
            this.severity = severity;
            this.rotation = rotation;
            this.bornMs = bornMs;
        }
    }

    final DalgonaShape shape;
    final DalgonaRules.Params params;
    final long seed;
    final int n;
    final List<CrackPattern.Branch> cracks;
    /** Unit normal of every outline sample and which side of it is the figure (+1 along the normal, -1 against it). */
    final float[] normalX;
    final float[] normalY;
    final byte[] insideSign;

    final long[] carved;
    /** Wall-clock time (ms) at which a sample was last carved, for the fresh-cut glint. */
    final long[] carvedAt;
    int carvedCount;

    double stressServer;
    double stressShown;
    int licks;
    int lickCooldown;
    int lickLock;
    boolean cracked;
    boolean done;
    boolean timedOut;
    int timeLeft;
    int timeTotal;
    long timeStampMs;
    boolean gotState;
    final List<Decal> decals = new ArrayList<>();

    CookieModel(DalgonaShape shape, DalgonaRules.Params params, long seed) {
        this.shape = shape;
        this.params = params;
        this.seed = seed;
        this.n = shape.sampleCount();
        this.cracks = CrackPattern.generate(seed);
        this.carved = new long[(n + 63) / 64];
        this.carvedAt = new long[n];
        this.normalX = new float[n];
        this.normalY = new float[n];
        this.insideSign = new byte[n];
        for (int i = 0; i < n; i++) {
            int a = ((i - 1) % n + n) % n;
            int b = (i + 1) % n;
            double tx = shape.sampleX(b) - shape.sampleX(a);
            double ty = shape.sampleY(b) - shape.sampleY(a);
            double len = Math.max(1e-6, Math.hypot(tx, ty));
            normalX[i] = (float) (-ty / len);
            normalY[i] = (float) (tx / len);
            insideSign[i] = (byte) (shape.contains(shape.sampleX(i) + normalX[i] * 6, shape.sampleY(i) + normalY[i] * 6) ? 1 : -1);
        }
        this.licks = params.licks();
        this.timeLeft = DalgonaRules.timeLimitTicks(com.squidgame.core.Difficulty.NORMAL);
        this.timeTotal = this.timeLeft;
        this.timeStampMs = System.currentTimeMillis();
    }

    boolean carvedAt(int i) {
        return (carved[i >> 6] & (1L << (i & 63))) != 0;
    }

    /** Marks the samples around a needle position as carved right away (the server will confirm). */
    void carveLocal(double x, double y, long nowMs) {
        if (cracked || done || lickLock > 0) {
            return;
        }
        double tol = params.tolerance();
        if (shape.distanceTo(x, y) > tol) {
            return;
        }
        long[] before = carved.clone();
        CookieSim.carve(shape, carved, x, y, tol, 0, Integer.MAX_VALUE, null);
        stamp(before, nowMs);
    }

    private void stamp(long[] before, long nowMs) {
        int count = 0;
        for (int w = 0; w < carved.length; w++) {
            long fresh = carved[w] & ~before[w];
            while (fresh != 0) {
                int bit = Long.numberOfTrailingZeros(fresh);
                fresh &= fresh - 1;
                carvedAt[(w << 6) + bit] = nowMs;
            }
            count += Long.bitCount(carved[w]);
        }
        carvedCount = count;
    }

    /**
     * Takes the server's state; returns the stress it had before (to detect jumps). While strokes are in flight the
     * server's carved set is only merged in (our prediction is ahead of it); once nothing is in flight ({@code settled})
     * it replaces the prediction, which drops anything the server did not accept.
     */
    double apply(DalgonaNet.StatePayload p, long nowMs, boolean settled) {
        double before = stressServer;
        stressServer = p.stress();
        if (!gotState) {
            stressShown = stressServer;
            gotState = true;
        }
        licks = p.licks();
        lickCooldown = p.lickCooldown();
        lickLock = p.lickLock();
        cracked = p.cracked();
        done = p.done();
        timedOut = p.timedOut();
        timeLeft = p.timeLeft();
        timeTotal = Math.max(1, p.timeTotal());
        timeStampMs = nowMs;
        long[] before2 = carved.clone();
        long[] bits = p.carvedBits();
        for (int i = 0; i < carved.length; i++) {
            long server = i < bits.length ? bits[i] : 0L;
            carved[i] = settled ? server : carved[i] | server;
        }
        stamp(before2, nowMs);
        return before;
    }

    /** Ticks left on the game clock, counting down between the server's updates. */
    double ticksLeftNow(long nowMs) {
        return Math.max(0, timeLeft - (nowMs - timeStampMs) / 50.0);
    }

    boolean canCarve() {
        return !cracked && !done && !timedOut;
    }

    boolean canLickLocal() {
        return canCarve() && licks > 0 && lickCooldown <= 0 && lickLock <= 0;
    }

    double progress() {
        return carvedCount / (double) n;
    }

    /** Smooths the displayed stress towards the server's value. */
    void tick() {
        stressShown += (stressServer - stressShown) * 0.3;
        if (Math.abs(stressServer - stressShown) < 0.05) {
            stressShown = stressServer;
        }
        if (lickCooldown > 0) {
            lickCooldown--;
        }
        if (lickLock > 0) {
            lickLock--;
        }
    }

    void addDecal(float x, float y, int severity, long nowMs, java.util.Random rnd) {
        decals.add(new Decal(x, y, Math.max(0, Math.min(3, severity)), rnd.nextFloat() * 360f, nowMs));
        if (decals.size() > 22) {
            decals.remove(0);
        }
    }
}
