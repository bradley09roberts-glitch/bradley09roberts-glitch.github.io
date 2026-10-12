package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.registry.ModSounds;
import com.grandline.ship.ShipEntity;
import com.grandline.world.WorldPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Sea ambience (Ambient/Environment volume slider): a looping surf bed that swells the more open water is around
 * you, storm wind when it rains, gulls by day near shores, and creaking timbers aboard a moving ship. Only in the
 * ocean world, above water.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class SeaAmbience {
    private static Loop sea, wind;
    private static int scanTick, gullIn = 200, creakIn = 60;
    /** Share of open water round the player (0 inland .. 1 open sea), refreshed every second. */
    static float openness;

    /** A centred looping stereo bed whose volume glides toward a target. */
    static final class Loop extends AbstractTickableSoundInstance {
        float target;
        final long started;

        Loop(SoundEvent s, float target, long now) {
            super(s, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.target = target;
            this.started = now;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.04f;
            if (target <= 0.001f && volume < 0.01f) stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean canPlaySound() {
            return true;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel l = mc.level;
        if (p == null || l == null || mc.isPaused()) return;
        boolean ocean = l.dimension() == Level.OVERWORLD;
        if (++scanTick % 20 == 0) openness = ocean ? scan(l, p) : 0;
        double sea = WorldPlan.SEA_LEVEL;
        boolean above = !p.isUnderWater() && p.getY() > sea - 6;
        float height = (float) Math.max(0, Math.min(1, 1 - (p.getY() - sea - 12) / 40));
        float storm = Math.max(l.getRainLevel(1f), l.getThunderLevel(1f));
        float seaVol = ocean && above ? openness * 0.55f * height : 0;
        float windVol = ocean && above ? storm * (0.25f + 0.45f * openness) : 0;
        long now = l.getGameTime();
        SeaAmbience.sea = drive(mc, SeaAmbience.sea, ModSounds.AMBIENT_SEA.get(), seaVol, now);
        wind = drive(mc, wind, ModSounds.AMBIENT_WIND.get(), windVol, now);

        if (--gullIn <= 0) { // gulls along the shore by day, not in a storm
            gullIn = 160 + l.random.nextInt(260);
            if (ocean && above && openness > 0.15f && openness < 0.9f && l.isDay() && storm < 0.3f && height > 0.3f) {
                double a = l.random.nextDouble() * Math.PI * 2, r = 12 + l.random.nextDouble() * 18;
                l.playLocalSound(p.getX() + Math.cos(a) * r, p.getY() + 8 + l.random.nextDouble() * 10, p.getZ() + Math.sin(a) * r,
                        ModSounds.AMBIENT_GULL.get(), SoundSource.AMBIENT, 1.2f, 0.9f + l.random.nextFloat() * 0.25f, false);
            }
        }
        if (--creakIn <= 0) { // timbers creak aboard a ship under way
            creakIn = 40 + l.random.nextInt(90);
            for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, p.getBoundingBox().inflate(8))) {
                double speed = Math.hypot(s.getX() - s.xo, s.getZ() - s.zo);
                if (speed > 0.03 || l.random.nextFloat() < 0.25f) { // a moored ship creaks now and then too
                    l.playLocalSound(p.getX(), p.getY() - 0.5, p.getZ(), ModSounds.AMBIENT_CREAK.get(), SoundSource.AMBIENT,
                            0.5f + (float) Math.min(0.5, speed * 2), 0.85f + l.random.nextFloat() * 0.3f, false);
                }
                break;
            }
        }
    }

    /** For the client self-test. */
    public static String state() {
        Minecraft mc = Minecraft.getInstance();
        return String.format("openness %.2f, surf %s, wind %s", openness, sea == null ? "off" : String.format("%.2f%s", sea.target, mc.getSoundManager().isActive(sea) ? " playing" : ""),
                wind == null ? "off" : String.format("%.2f%s", wind.target, mc.getSoundManager().isActive(wind) ? " playing" : ""));
    }

    private static Loop drive(Minecraft mc, Loop loop, SoundEvent s, float target, long now) {
        if (loop != null) loop.target = target;
        boolean dead = loop == null || loop.isStopped() || (now - loop.started > 40 && !mc.getSoundManager().isActive(loop));
        if (dead && target > 0.02f) {
            loop = new Loop(s, target, now);
            mc.getSoundManager().play(loop);
        }
        return loop;
    }

    /** Share of sample points within 32 blocks whose surface is open sea. */
    private static float scan(ClientLevel l, LocalPlayer p) {
        int water = 0, n = 0;
        int y = WorldPlan.SEA_LEVEL; // the ocean world fills water up to and including sea level
        for (int ring = 1; ring <= 2; ring++) {
            int r = ring * 16;
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r);
                if (!l.hasChunkAt(at)) continue;
                n++;
                if (l.getFluidState(at).is(FluidTags.WATER) && l.getBlockState(at.above()).isAir()) water++;
            }
        }
        return n == 0 ? 0 : water / (float) n;
    }

    private SeaAmbience() {}
}
