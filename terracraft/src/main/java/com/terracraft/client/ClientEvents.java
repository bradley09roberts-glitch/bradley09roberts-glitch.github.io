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
import com.terracraft.item.accessory.WingsItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;


/**
 * Client input and movement: key bindings, Terraria-style left-click weapon use, double jumps and
 * water walking (movement is client-authoritative, the server validates through packets).
 */
public final class ClientEvents {
    private static boolean jumpWasDown;
    private static int jumpsUsed;
    /** True once the jump key has been released while airborne; extra jumps need a fresh press after that. */
    private static boolean jumpReleasedInAir;
    private static long lastWeaponPacketTick;
    /** Wings: flight ticks left, whether the current jump-hold is driving the wings, and the jump key last tick. */
    private static int wingTime;
    private static boolean wingsEngaged;
    private static boolean wingJumpWasDown;

    private ClientEvents() {}

    static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ClientEvents.class);
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
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
        tickWings(mc, player);
        tickDoubleJump(mc, player);
        tickWaterWalking(player);
        tickWingAnimations(mc);
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
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (onInteractionKeyCancels(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean onInteractionKeyCancels(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && player.getMainHandItem().getItem() instanceof UsableWeapon) {
            event.setSwingHand(false);
            return true;
        }
        return false;
    }

    /**
     * Terraria wings: in the air, holding jump (a fresh press once double jumps are spent, or keeping it held
     * while falling) rises for the wings' flight time, then glides down slowly. Flight time refills on landing.
     */
    private static void tickWings(Minecraft mc, LocalPlayer player) {
        WingsItem.Flight wings = ClientState.wings(player.getId());
        boolean jumpDown = mc.options.keyJump.isDown();
        boolean newPress = jumpDown && !wingJumpWasDown;
        wingJumpWasDown = jumpDown;
        if (wings == null || player.getAbilities().flying || player.isPassenger()) {
            wingsEngaged = false;
            return;
        }
        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            wingTime = wings.flightTicks();
            wingsEngaged = false;
            return;
        }
        if (!jumpDown) {
            wingsEngaged = false;
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (!wingsEngaged && (newPress && jumpsUsed >= ClientState.stats().extraJumps() || motion.y < 0.0)) {
            wingsEngaged = true;
        }
        if (!wingsEngaged) {
            return;
        }
        if (wingTime > 0) {
            wingTime--;
            player.setDeltaMovement(motion.x, Math.min(motion.y + 0.16, wings.ascent()), motion.z);
        } else if (motion.y < -0.14) {
            player.setDeltaMovement(motion.x, -0.14, motion.z);
        }
        player.resetFallDistance();
        if (player.tickCount % 4 == 0) {
            TerraNetwork.sendToServer(com.terracraft.network.packet.WingFlightPacket.INSTANCE);
        }
    }

    /** Wing pose for every player wearing wings: folded on the ground, flapping while rising, spread while falling. */
    private static void tickWingAnimations(Minecraft mc) {
        for (var other : mc.level.players()) {
            if (ClientState.wings(other.getId()) == null) {
                continue;
            }
            double dy = other.getY() - other.yo;
            ClientState.WingAnim anim = other.onGround() || other.isInWater() ? ClientState.WingAnim.FOLDED
                : dy > -0.08 ? ClientState.WingAnim.FLAP : ClientState.WingAnim.GLIDE;
            ClientState.setWingAnim(other.getId(), anim);
        }
    }

    private static void tickDoubleJump(Minecraft mc, LocalPlayer player) {
        boolean jumpDown = mc.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            jumpsUsed = 0;
            jumpReleasedInAir = false;
        } else if (!jumpDown) {
            jumpReleasedInAir = true;
        } else if (!jumpWasDown && jumpReleasedInAir && !player.getAbilities().flying && !player.isPassenger()
            && jumpsUsed < ClientState.stats().extraJumps()) {
            jumpsUsed++;
            jumpReleasedInAir = false;
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
    public static void onScreenInit(ScreenEvent.Init.Post event) {
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
