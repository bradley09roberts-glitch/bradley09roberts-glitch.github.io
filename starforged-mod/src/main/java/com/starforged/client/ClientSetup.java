package com.starforged.client;

import com.starforged.client.model.ModLayers;
import com.starforged.client.particle.StarforgedParticle;
import com.starforged.client.render.StarforgedRenderers;
import com.starforged.network.DoubleJumpPacket;
import com.starforged.network.ModNetwork;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * Client bootstrap: renderers, model layers, particles and client-side effects.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSetup {
    private static boolean jumpWasDown;
    private static int airTicks;
    private static int airJumps;

    private ClientSetup() {
    }

    public static void init(BusGroup modBus) {
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(ModLayers::register);
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(StarforgedRenderers::register);
        RegisterParticleProvidersEvent.BUS.addListener(StarforgedParticle::register);
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> onClientTick());
        ViewportEvent.ComputeCameraAngles.BUS.addListener(ClientSetup::onCameraAngles);
        ViewportEvent.ComputeFogColor.BUS.addListener(ClientSetup::onFogColor);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> ClientHooks.reset());
    }

    private static void onClientTick() {
        ClientHooks.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        tickDoubleJump(mc, player);
        tickNebulaCloak(player);
        tickHammerDive(player);
        tickShootingStars(mc, player);
    }

    /** Comet Boots: press jump in mid-air for a second, star-powered jump. */
    private static void tickDoubleJump(Minecraft mc, LocalPlayer player) {
        boolean jumpDown = mc.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            airTicks = 0;
            airJumps = 1;
        } else {
            airTicks++;
        }
        boolean boots = player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.COMET_BOOTS.get());
        if (boots && jumpDown && !jumpWasDown && airTicks > 2 && airJumps > 0 && !player.isPassenger() && !player.getAbilities().flying
            && !player.isFallFlying()) {
            airJumps--;
            Vec3 look = player.getLookAngle().multiply(1, 0, 1);
            Vec3 boost = look.lengthSqr() > 1.0E-4 ? look.normalize().scale(0.35) : Vec3.ZERO;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 0.6 + boost.x, 0.68, motion.z * 0.6 + boost.z);
            player.resetFallDistance();
            ModNetwork.sendToServer(DoubleJumpPacket.INSTANCE);
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2;
                player.level().addParticle(ModParticles.ASTRAL_GLINT.get(), player.getX() + Math.cos(a) * 0.5, player.getY(), player.getZ() + Math.sin(a) * 0.5,
                    Math.cos(a) * 0.15, -0.05, Math.sin(a) * 0.15);
            }
            player.playSound(ModSounds.COMET_JUMP.get(), 0.7F, 1.1F);
        }
        jumpWasDown = jumpDown;
    }

    /** Nebula Cloak: gliding is powered by a gentle starwind - no rockets needed. */
    private static void tickNebulaCloak(LocalPlayer player) {
        if (!player.isFallFlying() || !player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.NEBULA_CLOAK.get())) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        if (motion.length() < 1.7) {
            player.setDeltaMovement(motion.add(look.scale(0.045)));
        }
        if (player.tickCount % 2 == 0) {
            RandomSource random = player.getRandom();
            player.level().addParticle(ModParticles.STAR_SPARKLE.get(), player.getX() + (random.nextDouble() - 0.5), player.getY() + 0.8,
                player.getZ() + (random.nextDouble() - 0.5), -motion.x * 0.2, -motion.y * 0.2, -motion.z * 0.2);
        }
    }

    /** Meteor Hammer: after the apex of a leap the wielder plunges toward the ground. */
    private static void tickHammerDive(LocalPlayer player) {
        if (!ClientHooks.isHammerDiving()) {
            return;
        }
        if (player.onGround() || player.isInWater()) {
            ClientHooks.endHammerDive();
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0.15) {
            player.setDeltaMovement(motion.x, Math.max(-2.8, motion.y - 0.16), motion.z);
            player.level().addParticle(ModParticles.METEOR_EMBER.get(), player.getX(), player.getY() + 0.5, player.getZ(), 0, 0.1, 0);
            player.level().addParticle(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 0, 0.05, 0);
        }
    }

    /** During a Starfall the night sky is streaked with shooting stars. */
    private static void tickShootingStars(Minecraft mc, LocalPlayer player) {
        if (!ClientHooks.isStarfall() || !mc.level.isDarkOutside()) {
            return;
        }
        RandomSource random = player.getRandom();
        if (random.nextInt(4) != 0) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 40 + random.nextDouble() * 60;
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        double y = Math.max(player.getY(), mc.level.getSeaLevel()) + 55 + random.nextDouble() * 40;
        double dir = random.nextDouble() * Math.PI * 2;
        double speed = 2.0 + random.nextDouble() * 1.5;
        mc.level.addAlwaysVisibleParticle(ModParticles.SHOOTING_STAR.get(), x, y, z, Math.cos(dir) * speed, -0.6 - random.nextDouble() * 0.6,
            Math.sin(dir) * speed);
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float shake = ClientHooks.currentShake();
        if (shake <= 0.0F) {
            return;
        }
        float t = (float) (System.nanoTime() / 1.0E9 * 40.0);
        event.setYaw(event.getYaw() + Mth.sin(t * 1.7F) * shake * 1.4F);
        event.setPitch(event.getPitch() + Mth.sin(t * 2.3F + 1.0F) * shake * 1.4F);
        event.setRoll(event.getRoll() + Mth.sin(t * 1.1F + 2.0F) * shake * 2.0F);
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float eclipse = ClientHooks.eclipse();
        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();
        Minecraft mc = Minecraft.getInstance();
        if (ClientHooks.isStarfall() && mc.level != null && mc.level.isDarkOutside()) {
            r = Mth.lerp(0.35F, r, 0.16F);
            g = Mth.lerp(0.35F, g, 0.07F);
            b = Mth.lerp(0.35F, b, 0.3F);
        }
        if (eclipse > 0.0F) {
            r = Mth.lerp(eclipse * 0.85F, r, 0.1F);
            g = Mth.lerp(eclipse * 0.85F, g, 0.02F);
            b = Mth.lerp(eclipse * 0.85F, b, 0.16F);
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }
}
