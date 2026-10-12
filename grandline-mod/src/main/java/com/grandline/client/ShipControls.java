package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.net.GLNetwork;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.ShipNet;
import com.grandline.ship.Wind;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Helm controls: W/S raise/lower sail (0-3), A/D rudder, X anchor, B Coup de Burst. Cannon seats: attack to fire.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class ShipControls {
    private static boolean wasFwd, wasBack;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !(p.getVehicle() instanceof ShipEntity ship) || mc.screen != null) return;
        byte flags = 0;
        boolean fwd = mc.options.keyUp.isDown(), back = mc.options.keyDown.isDown();
        if (fwd && !wasFwd) flags |= ShipNet.SAIL_UP;
        if (back && !wasBack) flags |= ShipNet.SAIL_DOWN;
        wasFwd = fwd;
        wasBack = back;
        while (Keys.ANCHOR.consumeClick()) flags |= ShipNet.ANCHOR;
        while (Keys.BURST.consumeClick()) flags |= ShipNet.BURST;
        float rudder = 0;
        if (mc.options.keyLeft.isDown()) rudder -= 1;
        if (mc.options.keyRight.isDown()) rudder += 1;
        PacketDistributor.sendToServer(new GLNetwork.ShipControl(ship.getId(), 0, rudder, flags));
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof ShipEntity ship)) return;
        if (e.isAttack() && ship.seatIndex(mc.player) != ship.helmSeat) {
            PacketDistributor.sendToServer(new GLNetwork.ShipControl(ship.getId(), 0, 0, ShipNet.FIRE));
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    /** HUD while aboard: ship name, hull, sail, speed (knots), wind. */
    public static void hud(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ShipEntity ship = mc.player.getVehicle() instanceof ShipEntity s ? s : null;
        if (ship == null) return;
        int x = g.guiWidth() - 150, y = 8;
        g.fill(x - 4, y - 4, x + 146, y + 54, 0x88000000);
        g.drawString(mc.font, ship.shipName(), x, y, 0xFFFFD34A, true);
        g.drawString(mc.font, Component.translatable("hud.grandline.hull", (int) ship.hull(), (int) ship.maxHull()), x, y + 11, 0xFFFFFFFF, true);
        g.drawString(mc.font, Component.translatable("hud.grandline.sail", ship.sail(), ship.anchored() ? Component.translatable("hud.grandline.anchored") : Component.empty()), x, y + 22, 0xFFFFFFFF, true);
        g.drawString(mc.font, Component.translatable("hud.grandline.speed", String.format("%.1f", ship.speed() * 20)), x, y + 33, 0xFFFFFFFF, true);
        Vec3 w = Wind.at(mc.level, ship.getX(), ship.getZ());
        String dir = com.grandline.navigation.Navigation.compass(w.x, w.z);
        g.drawString(mc.font, Component.translatable("hud.grandline.wind", dir, String.format("%.1f", w.length())), x, y + 44, 0xFF9AD8FF, true);
    }

    private ShipControls() {}
}
