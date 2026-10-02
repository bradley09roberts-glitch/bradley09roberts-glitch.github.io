package com.terracraft.client;

import com.terracraft.client.gui.TerrariaCraftingScreen;
import com.terracraft.item.weapon.UsableWeapon;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.DoubleJumpPacket;
import com.terracraft.network.packet.OpenAccessoriesPacket;
import com.terracraft.network.packet.UseWeaponPacket;
import com.terracraft.player.stats.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;

/**
 * Client input and movement: key bindings, Terraria-style left-click weapon use, double jumps and
 * water walking (movement is client-authoritative, the server validates through packets).
 */
public final class ClientEvents {
    private static boolean jumpWasDown;
    private static int jumpsUsed;
    private static long lastWeaponPacketTick;

    private ClientEvents() {}

    static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), ClientEvents.class);
    }

    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        while (KeyBindings.OPEN_EQUIPMENT.consumeClick()) {
            if (mc.gui.screen() == null) {
                TerraNetwork.sendToServer(OpenAccessoriesPacket.INSTANCE);
            }
        }
        while (KeyBindings.OPEN_CRAFTING.consumeClick()) {
            if (mc.gui.screen() == null) {
                mc.gui.setScreen(new TerrariaCraftingScreen());
            }
        }
        if (mc.gui.screen() == null) {
            tickWeaponUse(mc, player);
        }
        tickDoubleJump(mc, player);
        tickWaterWalking(player);
    }

    /** Holding attack with a ranged/magic/thrown weapon fires it continuously (Terraria auto-swing). */
    private static void tickWeaponUse(Minecraft mc, LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof UsableWeapon) || !mc.options.keyAttack.isDown()) {
            return;
        }
        long now = mc.level.getGameTime();
        if (player.getCooldowns().isOnCooldown(stack) || now - lastWeaponPacketTick < 2) {
            return;
        }
        lastWeaponPacketTick = now;
        TerraNetwork.sendToServer(UseWeaponPacket.INSTANCE);
    }

    /** Suppresses vanilla attacking/mining while a usable weapon fires on left click. */
    @SubscribeEvent
    static boolean onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && player.getMainHandItem().getItem() instanceof UsableWeapon) {
            event.setSwingHand(false);
            return true;
        }
        return false;
    }

    private static void tickDoubleJump(Minecraft mc, LocalPlayer player) {
        boolean jumpDown = mc.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            jumpsUsed = 0;
        } else if (jumpDown && !jumpWasDown && !player.getAbilities().flying && !player.isPassenger()
            && jumpsUsed < ClientState.stats().extraJumps()) {
            jumpsUsed++;
            Vec3 motion = player.getDeltaMovement();
            double power = player.getAttributeValue(Attributes.JUMP_STRENGTH) * 1.15;
            player.setDeltaMovement(motion.x, power, motion.z);
            player.resetFallDistance();
            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
                    (player.getRandom().nextDouble() - 0.5) * 0.2, 0.0, (player.getRandom().nextDouble() - 0.5) * 0.2);
            }
            TerraNetwork.sendToServer(DoubleJumpPacket.INSTANCE);
        }
        jumpWasDown = jumpDown;
    }

    /** Water Walking: liquid surfaces become walkable unless sneaking. */
    private static void tickWaterWalking(LocalPlayer player) {
        if (!ClientState.hasAbility(Ability.WATER_WALKING) || player.isShiftKeyDown() || player.getAbilities().flying) {
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        boolean waterBelow = player.level().getFluidState(below).is(FluidTags.WATER);
        boolean airAtFeet = player.level().getFluidState(player.blockPosition()).isEmpty();
        Vec3 motion = player.getDeltaMovement();
        if (player.isInWater() && !player.isUnderWater()) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, 0.12), motion.z);
        } else if (waterBelow && airAtFeet && motion.y < 0) {
            player.setDeltaMovement(motion.x, 0.0, motion.z);
            player.setOnGround(true);
            player.resetFallDistance();
        }
    }

    /** Adds an "Equipment" button to the vanilla inventory screen. */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            int x = screen.getGuiLeft() + 128;
            int y = screen.getGuiTop() - 20;
            event.addListener(Button.builder(Component.translatable("screen.terracraft.equipment.button"),
                b -> TerraNetwork.sendToServer(OpenAccessoriesPacket.INSTANCE)).bounds(x, y, 48, 18).build());
            event.addListener(Button.builder(Component.translatable("screen.terracraft.crafting.button"),
                b -> Minecraft.getInstance().gui.setScreen(new TerrariaCraftingScreen())).bounds(x - 52, y, 48, 18).build());
        }
    }
}
