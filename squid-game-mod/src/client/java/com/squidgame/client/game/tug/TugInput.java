package com.squidgame.client.game.tug;

import com.mojang.blaze3d.platform.InputConstants;
import com.squidgame.game.tug.TugNet;
import com.squidgame.net.ClientActionPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

/**
 * The player's controls in the Tug of War: hold Pull (R), hold Brace (Left Shift) and tap Heave (F) on the beat. The held keys
 * are reported to the server whenever they change and refreshed every half second (a refresh that stops counts as a release);
 * a heave tap is sent at once, polled every rendered frame so the press is timed to a frame, not to a tick. The server
 * validates everything; the client only says what the player does.
 *
 * <p>Minecraft hands a key to <em>one</em> binding when several share it, and the defaults do share: F is "swap hands" and Left
 * Shift is "sneak". So a binding counts as held when any binding on the same key is held, and during a match a tap that went to
 * another binding on the heave key counts as a heave (and the other action, a swap of the hands, does not happen).
 */
final class TugInput {
    private TugInput() {
    }

    static final String CATEGORY = "key.categories.squidgame";
    static final KeyMapping PULL = new KeyMapping("key.squidgame.tug.pull", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    static final KeyMapping HEAVE = new KeyMapping("key.squidgame.tug.heave", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
    static final KeyMapping BRACE = new KeyMapping("key.squidgame.tug.brace", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);

    private static boolean lastPull;
    private static boolean lastBrace;
    private static int sinceSend;

    static void registerKeys() {
        KeyBindingHelper.registerKeyBinding(PULL);
        KeyBindingHelper.registerKeyBinding(HEAVE);
        KeyBindingHelper.registerKeyBinding(BRACE);
    }

    /** True while the player is pulling in a running match and nothing else (chat, a menu) has the keyboard. */
    private static boolean active(Minecraft mc) {
        TugNet.StatePayload s = TugClientState.state();
        return s != null && mc.player != null && mc.screen == null && !mc.isPaused()
                && (s.role() == TugNet.ROLE_TEAM_A || s.role() == TugNet.ROLE_TEAM_B)
                && (s.stage() == TugNet.STAGE_MATCH || s.stage() == TugNet.STAGE_SUDDEN);
    }

    /** True while the key of {@code m} is down, also when Minecraft gave the key to another binding on it (sneak on Left Shift). */
    static boolean held(KeyMapping m) {
        if (m.isDown()) {
            return true;
        }
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != m && other.same(m) && other.isDown()) {
                return true;
            }
        }
        return false;
    }

    /** Called at the start of every client tick, before Minecraft acts on its own bindings: taps that went to another binding. */
    static void startTick(Minecraft mc) {
        if (!active(mc)) {
            return;
        }
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != HEAVE && other != PULL && other != BRACE && other.same(HEAVE)) {
                while (other.consumeClick()) {
                    sendHeave();
                }
            }
        }
    }

    /** Called at the end of every client tick: the held keys. */
    static void tick(Minecraft mc) {
        if (!active(mc)) {
            while (HEAVE.consumeClick()) {
                // taps outside a match are not sent
            }
            if (lastPull || lastBrace) {
                send(false, false);
            }
            lastPull = false;
            lastBrace = false;
            sinceSend = 0;
            return;
        }
        boolean pull = held(PULL);
        boolean brace = held(BRACE);
        if (pull != lastPull || brace != lastBrace || ++sinceSend >= 10) {
            send(pull, brace);
            lastPull = pull;
            lastBrace = brace;
            sinceSend = 0;
        }
    }

    /** Called every rendered frame: the heave taps. */
    static void frame(Minecraft mc) {
        if (!active(mc)) {
            return;
        }
        while (HEAVE.consumeClick()) {
            sendHeave();
        }
    }

    private static void sendHeave() {
        ClientPlayNetworking.send(new ClientActionPayload("tug.heave", new CompoundTag()));
    }

    private static void send(boolean pull, boolean brace) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("p", pull);
        tag.putBoolean("b", brace);
        ClientPlayNetworking.send(new ClientActionPayload("tug.input", tag));
    }
}
