package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.combat.CombatInput;
import com.grandline.net.GLNetwork;
import com.grandline.player.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client input: Combat Mode, ability slots, light/heavy attacks, guard, dodge, Haki, lock-on, gliding, sinking. */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static int attackHeld = 0;
    /** Outside Combat Mode a click on someone is still a combat strike (the same light/heavy blows), not a vanilla punch. */
    private static boolean strikeHeld = false;
    private static boolean wasBlocking = false;
    private static boolean gliding = false;

    private static void send(byte action, int a0, int a1, float x, float z) {
        PacketDistributor.sendToServer(new GLNetwork.CombatInput(action, (byte) a0, (byte) a1, x, z));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.reset();
        Guidance.reset();
        com.grandline.client.hud.CombatTips.reset();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientState.clientTicks++;
        if (p == null || mc.level == null) return;
        if (mc.screen != null) {
            attackHeld = 0;
            return;
        }
        var d = ClientState.LOCAL;

        while (Keys.COMBAT_MODE.consumeClick()) {
            ClientState.combatMode = !ClientState.combatMode;
            send(CombatInput.TOGGLE_COMBAT, ClientState.combatMode ? 1 : 0, 0, 0, 0);
            if (!ClientState.combatMode) ClientState.lockTarget = -1;
            else com.grandline.client.hud.CombatTips.onFirstFight();
        }
        while (Keys.OBSERVATION.consumeClick()) send(CombatInput.HAKI_TOGGLE, 0, 0, 0, 0);
        while (Keys.ARMAMENT.consumeClick()) send(CombatInput.HAKI_TOGGLE, 1, 0, 0, 0);
        while (Keys.CONQUEROR.consumeClick()) send(CombatInput.HAKI_TOGGLE, 2, 0, 0, 0);
        while (Keys.TRANSFORM.consumeClick()) send(CombatInput.TRANSFORM, 0, 0, 0, 0);
        while (Keys.CHART.consumeClick()) PacketDistributor.sendToServer(new GLNetwork.UiAction("open_chart", new CompoundTag()));
        while (Keys.CHARACTER.consumeClick()) mc.setScreen(new com.grandline.client.screen.CharacterScreen());
        while (Keys.QUEST_LOG.consumeClick()) PacketDistributor.sendToServer(new GLNetwork.UiAction("quest_log", new CompoundTag()));
        while (Keys.MINIMAP.consumeClick()) Minimap.toggle();
        while (Keys.NEXT_SET.consumeClick()) if (ClientState.combatMode) ClientState.abilitySet = (ClientState.abilitySet + 1) % com.grandline.player.CharacterData.LOADOUT_SETS;
        while (Keys.DODGE.consumeClick()) {
            float lx = p.input.leftImpulse, lz = p.input.forwardImpulse;
            send(CombatInput.DODGE, 0, 0, -lx, lz);
        }
        while (Keys.LOCK_ON.consumeClick()) toggleLock(mc, p);

        if (ClientState.combatMode) {
            // hotbar keys become ability slots
            for (int i = 0; i < 9; i++) {
                while (mc.options.keyHotbarSlots[i].consumeClick()) {
                    float target = ClientState.lockTarget >= 0 ? ClientState.lockTarget : crosshairTarget(mc);
                    send(CombatInput.ABILITY, ClientState.abilitySet, i, target, 0);
                    String id = d.loadoutSlot(ClientState.abilitySet, i);
                    if (id != null) {
                        var a = com.grandline.combat.Abilities.get(id);
                        if (a != null) ClientState.COOLDOWNS.put(id, ClientState.clientTicks + a.cooldown());
                    }
                }
            }
            // light (tap) / heavy (hold and release) attacks
            strikeInput(mc);
            // guard while holding use (unless holding something usable like food or a ranged weapon)
            var main = p.getMainHandItem();
            boolean usable = main.getUseAnimation() != net.minecraft.world.item.UseAnim.NONE || (main.getItem() instanceof com.grandline.item.GLWeaponItem w && w.weaponClass.ranged);
            boolean block = mc.options.keyUse.isDown() && !usable;
            if (block != wasBlocking) {
                send(CombatInput.BLOCK, block ? 1 : 0, 0, 0, 0);
                wasBlocking = block;
                ClientState.blocking = block;
            }
            softLock(mc, p);
        } else {
            if (strikeHeld) strikeInput(mc);
            else attackHeld = 0;
            if (wasBlocking) {
                send(CombatInput.BLOCK, 0, 0, 0, 0);
                wasBlocking = false;
                ClientState.blocking = false;
            }
        }

        // gliding races hold jump while falling
        boolean glider = d.race == Race.SKY_PEOPLE || d.race == Race.STORMWING;
        boolean wantGlide = glider && !p.onGround() && !p.isInWater() && mc.options.keyJump.isDown() && p.getDeltaMovement().y < 0;
        if (wantGlide != gliding) {
            gliding = wantGlide;
            send(CombatInput.GLIDE, gliding ? 1 : 0, 0, 0, 0);
        }
        if (gliding) p.setDeltaMovement(p.getDeltaMovement().multiply(1.02, 0.6, 1.02));

        // Devil Fruit users sink in the sea
        if (d.fruit != null && p.isInWater() && !p.isCreative()) {
            double depth = p.getFluidTypeHeight(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
            if (depth >= p.getBbHeight() * 0.45) {
                p.setSwimming(false);
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.6, Math.min(v.y, 0) - 0.03, v.z * 0.6);
            }
        }
    }

    /** Tap = light strike, hold and release = heavy blow (the longer the hold, the stronger, up to 1.5 s). */
    private static void strikeInput(Minecraft mc) {
        boolean atk = mc.options.keyAttack.isDown();
        if (atk) attackHeld++;
        else if (attackHeld > 0) {
            if (attackHeld >= 8) send(CombatInput.HEAVY, Math.min(10, (attackHeld - 8) / 3), 0, 0, 0);
            else send(CombatInput.LIGHT, 0, 0, 0, 0);
            attackHeld = 0;
            strikeHeld = false;
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientState.combatMode) {
            if (e.isAttack()) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
            if (e.isPickBlock()) e.setCanceled(true); // the middle button is lock-on in Combat Mode
            return;
        }
        // outside Combat Mode a click on a living target is the same combat strike, so there is only one way to fight
        if (e.isAttack() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity
                && !(eh.getEntity() instanceof net.minecraft.world.entity.decoration.ArmorStand) && ClientState.hasData && ClientState.LOCAL.created) {
            e.setCanceled(true);
            e.setSwingHand(false);
            strikeHeld = true;
            attackHeld = 0;
            com.grandline.client.hud.CombatTips.onFirstFight();
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.combatMode || mc.screen != null) return;
        int dir = e.getScrollDeltaY() > 0 ? -1 : 1;
        ClientState.abilitySet = Math.floorMod(ClientState.abilitySet + dir, com.grandline.player.CharacterData.LOADOUT_SETS);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHotbar(RenderGuiLayerEvent.Pre e) {
        if (ClientState.combatMode && e.getName().equals(VanillaGuiLayers.HOTBAR)) e.setCanceled(true);
    }

    /** The technique's target when not locked on: whoever the player looks at in plain sight, up to 32 blocks. */
    private static float crosshairTarget(Minecraft mc) {
        LivingEntity le = com.grandline.client.hud.TargetFrame.looked(mc, 32);
        return le == null ? -1 : le.getId();
    }

    private static void toggleLock(Minecraft mc, LocalPlayer p) {
        if (ClientState.lockTarget >= 0) {
            ClientState.lockTarget = -1;
            send(CombatInput.LOCK, 0, 0, -1, 0);
            return;
        }
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity en : mc.level.getEntities(p, new AABB(eye, eye).inflate(24))) {
            if (!(en instanceof LivingEntity le) || !le.isAlive()) continue;
            Vec3 to = le.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            double dot = to.normalize().dot(look);
            if (dot < 0.8 || dist > 24 || !com.grandline.combat.Cover.canSee(p, le)) continue; // never through walls
            double score = dist * (2 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        if (best != null) {
            ClientState.lockTarget = best.getId();
            send(CombatInput.LOCK, 0, 0, best.getId(), 0);
        }
    }

    private static int lockHidden;

    /** Soft lock-on: ease the camera toward the locked target; the lock drops a second after the target goes out of sight. */
    private static void softLock(Minecraft mc, LocalPlayer p) {
        if (ClientState.lockTarget < 0) return;
        Entity t = mc.level.getEntity(ClientState.lockTarget);
        if (t instanceof LivingEntity le0 && ClientState.clientTicks % 5 == 0) lockHidden = com.grandline.combat.Cover.canSee(p, le0) ? 0 : lockHidden + 5;
        if (!(t instanceof LivingEntity le) || !le.isAlive() || le.distanceTo(p) > 32 || lockHidden >= 20) {
            ClientState.lockTarget = -1;
            lockHidden = 0;
            send(CombatInput.LOCK, 0, 0, -1, 0);
            return;
        }
        Vec3 to = le.getBoundingBox().getCenter().subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(-to.x, to.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
        float dy = net.minecraft.util.Mth.wrapDegrees(yaw - p.getYRot());
        float dp = pitch - p.getXRot();
        p.setYRot(p.getYRot() + dy * 0.25f);
        p.setXRot(net.minecraft.util.Mth.clamp(p.getXRot() + dp * 0.15f, -80, 80));
    }

    /** /controls: every control with its current key, the combat loop and how to read a foe's wind-up. */
    @SubscribeEvent
    public static void onClientCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("controls").executes(c -> {
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.setScreen(new com.grandline.client.screen.ControlsScreen(null)));
            return 1;
        }));
    }

    private ClientEvents() {}
}
