package com.squidgame.client.game.marbles;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.game.marbles.MarblesNet;
import com.squidgame.game.marbles.ThrowStatePayload;
import com.squidgame.net.ClientActionPayload;
import com.squidgame.registry.ModEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the marbles game, registered by name from {@code ScreenRegistry.discoverGameClients}: the odd-or-even
 * table and the partnership offer (server-opened screens), the charge bar of the target throw, a key to bring the table
 * back after hiding it, and a smaller, glowing renderer for the thrown marble.
 */
@Environment(EnvType.CLIENT)
public final class MarblesClient {
    /** Key of the panel the player dismissed with Esc (it stays hidden until the next decision or reveal). */
    static volatile String dismissed;
    private static KeyMapping panelKey;

    private MarblesClient() {
    }

    public static void register() {
        ScreenRegistry.register(MarblesNet.SCREEN_ODD_EVEN, new ScreenRegistry.Handler() {
            @Override
            public Screen open(Minecraft mc, CompoundTag data) {
                MarblesScreen.State s = MarblesScreen.State.parse(data);
                return s.key().equals(dismissed) ? null : new MarblesScreen(s);
            }

            @Override
            public boolean update(Minecraft mc, Screen current, CompoundTag data) {
                if (current instanceof MarblesScreen table) {
                    table.apply(MarblesScreen.State.parse(data));
                }
                // any other screen (pause menu, chat...) is left alone
                return true;
            }
        });
        ScreenRegistry.register(MarblesNet.SCREEN_PAIR_REQUEST, PairRequestScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(ThrowStatePayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ThrowOverlay.accept(payload)));
        HudRenderCallback.EVENT.register(ThrowOverlay::render);
        EntityRendererRegistry.register(ModEntities.MARBLE, ctx -> new ThrownItemRenderer<>(ctx, 0.75f, true));

        panelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.squidgame.marbles_panel", GLFW.GLFW_KEY_M, "key.categories.squidgame"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (panelKey.consumeClick()) {
                dismissed = null;
                if (mc.player != null && ClientPlayNetworking.canSend(ClientActionPayload.TYPE)) {
                    ClientPlayNetworking.send(new ClientActionPayload(MarblesNet.ACTION_REOPEN, new CompoundTag()));
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dismissed = null;
            ThrowOverlay.reset();
        });
    }

    /** The key that brings the panel back, for the hint in the panel. */
    static Component panelKeyName() {
        return panelKey == null ? Component.literal("M") : panelKey.getTranslatedKeyMessage();
    }
}
