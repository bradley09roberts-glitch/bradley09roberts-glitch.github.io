package com.squidgame.client.game.finale;

import com.mojang.blaze3d.platform.InputConstants;
import com.squidgame.core.finale.InputGate;
import com.squidgame.net.ClientActionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

/**
 * Turns the player's controls into fight inputs, while this client is one of the two fighters of a live duel:
 * <ul>
 *   <li>left mouse button: a tap is a light strike, holding charges a heavy strike (released with the button);</li>
 *   <li>right mouse button, held: guard;</li>
 *   <li>the Dash key (V) or a double tap of A, D or S: dodge in the direction of the keys held (backwards when none);</li>
 *   <li>the Shove key (R): shove.</li>
 * </ul>
 * The vanilla attack is cancelled for the duration (see {@code FinaleClient}), so strikes also work in the air:
 * the server decides what they hit. Held buttons send a heartbeat so a lost message cannot leave a guard up. Clicks are
 * counted, not only sampled: a press that is over before the next client tick (a quick click, a slow frame) still
 * counts as a tap.
 */
@Environment(EnvType.CLIENT)
final class FinaleInput {
    static final KeyMapping DASH = new KeyMapping("key.squidgame.dash", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.squidgame");
    static final KeyMapping SHOVE = new KeyMapping("key.squidgame.shove", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.squidgame");

    /** Ticks between the heartbeats of a held button, and the window in which a second tap makes a double tap. */
    private static final int HEARTBEAT = 8, DOUBLE_TAP = 7;
    /** The dodge direction (eighths of a turn clockwise from straight ahead) of a double tap of A, D and S. */
    private static final int[] TAP_SECTORS = {6, 2, 4};

    private static boolean attackHeld, guardHeld;
    private static int tick, attackBeat, guardBeat;
    /** Presses of the attack key since the last tick, as the vanilla attack handling counts them (fed by the pre-attack event). */
    private static int attackPresses;
    private static final int[] keyTap = {-100, -100, -100};

    private FinaleInput() {
    }

    static void registerKeys() {
        KeyBindingHelper.registerKeyBinding(DASH);
        KeyBindingHelper.registerKeyBinding(SHOVE);
    }

    /** Called by the pre-attack event, every tick in which the attack key is down or was clicked. */
    static void noteAttackKey(int presses) {
        attackPresses += presses;
    }

    static void tick(Minecraft mc) {
        tick++;
        // the presses are drained every tick, in a fight or not, so that none is left over for the next duel
        int[] taps = {drain(mc.options.keyLeft), drain(mc.options.keyRight), drain(mc.options.keyDown)};
        int dash = drain(DASH), shove = drain(SHOVE);
        if (mc.player == null || mc.screen != null || !FightClientState.isLiveFighter()) {
            releaseAll();
            attackPresses = 0;
            return;
        }
        pollAttack(mc);
        pollGuard(mc);
        for (int i = 0; i < shove; i++) {
            send(InputGate.ID_SHOVE, true, 0);
        }
        for (int i = 0; i < dash; i++) {
            send(InputGate.ID_DODGE, true, sectorOfHeldKeys(mc, 4));
        }
        countDoubleTaps(taps);
    }

    private static int drain(KeyMapping key) {
        int n = 0;
        while (key.consumeClick()) {
            n++;
        }
        return n;
    }

    private static void pollAttack(Minecraft mc) {
        int presses = attackPresses;
        attackPresses = 0;
        boolean down = mc.options.keyAttack.isDown();
        if (down) {
            if (!attackHeld || tick - attackBeat >= HEARTBEAT) {
                send(InputGate.ID_ATTACK, true, 0);
                attackHeld = true;
                attackBeat = tick;
            }
        } else if (attackHeld) {
            send(InputGate.ID_ATTACK, false, 0);
            attackHeld = false;
        } else if (presses > 0) {
            // pressed and released again before this tick looked: a quick click is a light strike all the same
            send(InputGate.ID_ATTACK, true, 0);
            send(InputGate.ID_ATTACK, false, 0);
        }
    }

    private static void pollGuard(Minecraft mc) {
        boolean down = mc.options.keyUse.isDown();
        if (down && (!guardHeld || tick - guardBeat >= HEARTBEAT)) {
            send(InputGate.ID_GUARD, true, 0);
            guardHeld = true;
            guardBeat = tick;
        } else if (!down && guardHeld) {
            send(InputGate.ID_GUARD, false, 0);
            guardHeld = false;
        }
    }

    /** A second press of A, D or S within a few ticks is a dodge to that side (W is vanilla's sprint). */
    private static void countDoubleTaps(int[] presses) {
        for (int i = 0; i < presses.length; i++) {
            for (int k = 0; k < presses[i]; k++) {
                if (tick - keyTap[i] <= DOUBLE_TAP) {
                    send(InputGate.ID_DODGE, true, TAP_SECTORS[i]);
                    keyTap[i] = -100;
                } else {
                    keyTap[i] = tick;
                }
            }
        }
    }

    /** The direction of the movement keys held, in eighths of a turn clockwise from straight ahead. */
    private static int sectorOfHeldKeys(Minecraft mc, int fallback) {
        int forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int right = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
        if (forward == 0 && right == 0) {
            return fallback;
        }
        if (forward == 1) {
            return right == 0 ? 0 : right == 1 ? 1 : 7;
        }
        if (forward == -1) {
            return right == 0 ? 4 : right == 1 ? 3 : 5;
        }
        return right == 1 ? 2 : 6;
    }

    private static void releaseAll() {
        if (attackHeld || guardHeld) {
            if (attackHeld) {
                send(InputGate.ID_ATTACK, false, 0);
            }
            if (guardHeld) {
                send(InputGate.ID_GUARD, false, 0);
            }
            attackHeld = false;
            guardHeld = false;
        }
        keyTap[0] = keyTap[1] = keyTap[2] = -100;
    }

    private static void send(String id, boolean down, int sector) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("d", down);
        tag.putInt("k", sector);
        if (ClientPlayNetworking.canSend(ClientActionPayload.TYPE)) {
            ClientPlayNetworking.send(new ClientActionPayload(id, tag));
        }
    }
}
