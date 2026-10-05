package com.squidgame.client.game.finale;

import com.squidgame.game.finale.FightStatePayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side mirror of the fight the server runs: the latest {@link FightStatePayload}, the smoothed bar values and
 * the short-lived feedback (what was just hit or blocked) the overlay shows. The server decides everything; this only
 * remembers what it was told.
 */
final class FightClientState {
    /** A floating word or number the overlay shows for a moment. */
    record Popup(String text, int color, long bornNanos, boolean mine, boolean big) {
    }

    private static final long FRESH_NANOS = 800_000_000L;
    private static final long POPUP_NANOS = 1_100_000_000L;

    private static FightStatePayload state;
    private static long receivedAt;
    private static final List<Popup> popups = new ArrayList<>();
    private static int tookSeq = -1, dealtSeq = -1, deniedSeq = -1;
    /** Displayed (smoothed) health and stamina in tenths, indexed like the payload (0 attacker, 1 defender). */
    static final float[] shownHealth = {1000, 1000}, shownStamina = {1000, 1000};
    static long deniedAt, hitMarkerAt, tookAt;
    static int tookKind, hitMarkerKind;
    private static int lastDuel = -1;

    private FightClientState() {
    }

    static void accept(FightStatePayload s) {
        long now = System.nanoTime();
        if (state == null || s.duel()[0] != lastDuel) {
            // a new duel: the bars start full
            for (int i = 0; i < 2; i++) {
                shownHealth[i] = s.health()[i];
                shownStamina[i] = s.stamina()[i];
            }
            tookSeq = s.took()[0];
            dealtSeq = s.dealt()[0];
            deniedSeq = s.denied();
            lastDuel = s.duel()[0];
            popups.clear();
        }
        if (s.took()[0] != tookSeq) {
            tookSeq = s.took()[0];
            tookAt = now;
            tookKind = s.took()[1];
            pushPopup(s.took()[1], s.took()[2], true, now);
        }
        if (s.dealt()[0] != dealtSeq) {
            dealtSeq = s.dealt()[0];
            hitMarkerAt = now;
            hitMarkerKind = s.dealt()[1];
            pushPopup(s.dealt()[1], s.dealt()[2], false, now);
        }
        if (s.denied() != deniedSeq) {
            deniedSeq = s.denied();
            deniedAt = now;
        }
        state = s;
        receivedAt = now;
    }

    private static void pushPopup(int kind, int amount, boolean mine, long now) {
        String text;
        int color;
        boolean big = false;
        switch (kind) {
            case FightStatePayload.HIT_BLOCKED -> {
                text = mine ? "BLOCK" : "BLOCKED";
                color = mine ? 0xFF6EC8FF : 0xFFB0B0B0;
            }
            case FightStatePayload.HIT_PARRIED -> {
                text = mine ? "PARRY!" : "PARRIED";
                color = mine ? 0xFFFFD84A : 0xFFB0B0B0;
                big = mine;
            }
            case FightStatePayload.HIT_BREAK -> {
                text = mine ? "GUARD BREAK" : "GUARD BREAK!";
                color = mine ? 0xFFFF5050 : 0xFFFFD84A;
                big = true;
            }
            case FightStatePayload.HIT_DODGED -> {
                text = mine ? "DODGE" : "MISS";
                color = mine ? 0xFF60E090 : 0xFFB0B0B0;
            }
            default -> {
                text = (mine ? "-" : "") + amount;
                color = mine ? 0xFFFF5050 : 0xFFFFFFFF;
                big = kind == FightStatePayload.HIT_HEAVY;
            }
        }
        popups.add(new Popup(text, color, now, mine, big));
        while (popups.size() > 6) {
            popups.remove(0);
        }
    }

    /** Smooths the bars towards the server's values; call once per rendered frame. */
    static void animate(FightStatePayload s) {
        for (int i = 0; i < 2; i++) {
            shownHealth[i] += (s.health()[i] - shownHealth[i]) * 0.25f;
            shownStamina[i] += (s.stamina()[i] - shownStamina[i]) * 0.3f;
        }
    }

    /** The latest state if the server is still sending it, else null. */
    @Nullable
    static FightStatePayload fresh() {
        FightStatePayload s = state;
        return s != null && System.nanoTime() - receivedAt < FRESH_NANOS ? s : null;
    }

    /** True while this client is one of the two fighters of a duel that is being fought. */
    static boolean isLiveFighter() {
        FightStatePayload s = fresh();
        return s != null && s.stage() == FightStatePayload.FIGHT && s.myRole() != 0;
    }

    static List<Popup> popups() {
        long now = System.nanoTime();
        popups.removeIf(p -> now - p.bornNanos() > POPUP_NANOS);
        return popups;
    }

    static long popupLife() {
        return POPUP_NANOS;
    }

    static void reset() {
        state = null;
        popups.clear();
        tookSeq = dealtSeq = deniedSeq = -1;
        lastDuel = -1;
    }
}
