package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.net.GLNetwork;
import com.grandline.ship.NavalCombat;
import com.grandline.ship.ShipEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Captain's gun orders from the helm: V toggles fire at will / hold fire, N cycles the shot the gun crews load.
 * Also the naval lines of the ship HUD (orders, ammo, rigging, grapple/tow lines).
 */
public final class NavalClient {
    public static final KeyMapping GUN_ORDERS = new KeyMapping("key.grandline.gun_orders", HelmKeyContext.AT_HELM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, Keys.CAT);
    public static final KeyMapping AMMO = new KeyMapping("key.grandline.gun_ammo", HelmKeyContext.AT_HELM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, Keys.CAT);

    @EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(GUN_ORDERS);
            e.register(AMMO);
        }
    }

    @EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            boolean aboard = mc.player.getVehicle() instanceof ShipEntity;
            while (GUN_ORDERS.consumeClick()) if (aboard) order("toggle_fire");
            while (AMMO.consumeClick()) if (aboard) order("ammo");
        }
    }

    static void order(String what) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "order");
        t.putString("order", what);
        PacketDistributor.sendToServer(new GLNetwork.UiAction("ship", t));
    }

    /** Extra HUD lines below the ship panel. */
    static void hudLines(GuiGraphics g, Minecraft mc, ShipEntity ship, int x, int y) {
        boolean fire = ship.flag(ShipEntity.F_FIRE_AT_WILL);
        Component order = Component.translatable(fire ? "hud.grandline.fire_at_will" : "hud.grandline.hold_fire");
        g.drawString(mc.font, Component.translatable("hud.grandline.guns", order, Component.translatable("item.grandline." + NavalCombat.ammo(ship))), x, y, fire ? 0xFFFF7060 : 0xFFB0E0FF, true);
        int rig = (int) (ship.rigging() * 100);
        Component line;
        if (ship.linkedId() >= 0 && mc.level.getEntity(ship.linkedId()) instanceof ShipEntity o)
            line = Component.translatable(ship.towing() ? "hud.grandline.towing" : "hud.grandline.grappled", o.shipName());
        else line = Component.translatable("hud.grandline.rigging", rig);
        g.drawString(mc.font, line, x, y + 11, rig < 60 ? 0xFFFFA040 : 0xFFDDDDDD, true);
    }

    private NavalClient() {}
}
