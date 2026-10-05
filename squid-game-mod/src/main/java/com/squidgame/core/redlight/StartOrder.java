package com.squidgame.core.redlight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which contestant takes which start slot of the Red Light field. The slots are numbered centre-out starting at the front
 * row, so handing them out in roster order puts whoever is first on the roster (the player who opened the tournament) at the
 * very front of an empty field with the whole crowd behind the camera. Humans are placed in the middle of the occupied block
 * of slots instead: runners ahead, beside and behind them, and the pack visible from the first second.
 */
public final class StartOrder {
    private StartOrder() {
    }

    /**
     * Returns the contestants in slot order (element i takes slot i). Machine contestants keep their relative order; every human
     * takes the free slot nearest to the middle of the used block, a little towards the back.
     *
     * @param slotX x of each start slot, ordered as the slots are numbered
     * @param slotZ z of each start slot (the field is entered towards +z, so a larger z is nearer the line)
     */
    public static <T> List<T> humansIntoCrowd(List<T> all, Predicate<T> isHuman, double[] slotX, double[] slotZ) {
        int n = all.size();
        List<T> humans = new ArrayList<>();
        List<T> others = new ArrayList<>();
        for (T c : all) {
            (isHuman.test(c) ? humans : others).add(c);
        }
        int used = Math.min(n, Math.min(slotX.length, slotZ.length));
        if (humans.isEmpty() || used < 2) {
            return new ArrayList<>(all);
        }
        double sx = 0, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < used; i++) {
            sx += slotX[i];
            minZ = Math.min(minZ, slotZ[i]);
            maxZ = Math.max(maxZ, slotZ[i]);
        }
        double tx = sx / used;
        double tz = (minZ + maxZ) / 2 - 0.12 * (maxZ - minZ);
        boolean[] taken = new boolean[used];
        Object[] bySlot = new Object[n];
        int placed = 0;
        for (T h : humans) {
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < used; i++) {
                if (taken[i]) {
                    continue;
                }
                double d = (slotX[i] - tx) * (slotX[i] - tx) + (slotZ[i] - tz) * (slotZ[i] - tz);
                if (d < bestD - 1e-9) {
                    bestD = d;
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            taken[best] = true;
            bySlot[best] = h;
            placed++;
        }
        int next = 0;
        for (T o : others) {
            while (next < n && bySlot[next] != null) {
                next++;
            }
            if (next < n) {
                bySlot[next] = o;
            }
        }
        // humans that did not fit a slot (more humans than slots) keep the tail positions
        for (int i = placed; i < humans.size(); i++) {
            while (next < n && bySlot[next] != null) {
                next++;
            }
            if (next < n) {
                bySlot[next] = humans.get(i);
            }
        }
        List<T> out = new ArrayList<>(n);
        @SuppressWarnings("unchecked")
        T[] cast = (T[]) bySlot;
        out.addAll(Arrays.asList(cast));
        return out;
    }
}
