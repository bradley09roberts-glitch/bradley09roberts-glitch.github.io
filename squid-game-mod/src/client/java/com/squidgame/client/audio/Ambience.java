package com.squidgame.client.audio;

import com.squidgame.client.state.ClientState;
import com.squidgame.core.Phase;
import com.squidgame.net.HudPayload;
import com.squidgame.registry.ModSounds;
import com.squidgame.world.ArenaWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Looping room tone and music of the tournament complex. The arena a player is in follows from the 1000-block spacing of the
 * arenas ({@code ArenaId}: x = 0 hub, 1000 red light, 2000 dalgona, 3000 tug of war, 4000 marbles, 5000 glass bridge, 6000 final), so
 * the client needs no extra packets. Sounds fade in and out and stop when the player leaves the dimension.
 */
public final class Ambience {
    private static Loop ambient;
    private static Loop music;

    private Ambience() {
    }

    public static void tick(Minecraft mc) {
        SoundEvent wantAmbient = null;
        SoundEvent wantMusic = null;
        if (mc.player != null && mc.level != null && mc.level.dimension().equals(ArenaWorld.DIMENSION)) {
            int arena = Mth.clamp(Math.round((float) (mc.player.getX() / 1000.0)), 0, 6);
            wantAmbient = switch (arena) {
                case 0, 2 -> ModSounds.AMBIENT_DORM;
                case 1, 6 -> ModSounds.AMBIENT_PLAYGROUND;
                case 3, 5 -> ModSounds.AMBIENT_INDUSTRIAL;
                case 4 -> ModSounds.AMBIENT_ALLEY;
                default -> null;
            };
            HudPayload hud = ClientState.hud;
            boolean playing = hud != null && Phase.byOrdinal(hud.phase()) == Phase.GAME;
            if (arena == 0 && !playing) {
                wantMusic = ModSounds.MUSIC_LOBBY;
            } else if (playing && arena == 6) {
                wantMusic = ModSounds.MUSIC_FINAL;
            } else if (playing && (arena == 2 || arena == 5)) {
                wantMusic = ModSounds.MUSIC_TENSION;
            }
        }
        ambient = follow(mc, ambient, wantAmbient, 0.45f);
        music = follow(mc, music, wantMusic, 0.30f);
    }

    private static Loop follow(Minecraft mc, @Nullable Loop current, @Nullable SoundEvent want, float volume) {
        if (current != null && current.isStopped()) {
            current = null;
        }
        if (current != null && current.event != want) {
            current.fadeOut();
            if (want == null) {
                return current.isStopped() ? null : current;
            }
            current = null;
        }
        if (current == null && want != null) {
            current = new Loop(want, volume);
            mc.getSoundManager().play(current);
        }
        return current;
    }

    /** A looping, non-positional sound that fades in on start and out on request. */
    private static final class Loop extends AbstractSoundInstance implements TickableSoundInstance {
        final SoundEvent event;
        private final float full;
        private boolean fading;
        private boolean stopped;

        Loop(SoundEvent event, float volume) {
            super(event, SoundSource.AMBIENT, net.minecraft.util.RandomSource.create());
            this.event = event;
            this.full = volume;
            this.volume = 0.01f;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
            if (fading) {
                volume -= 0.02f;
                if (volume <= 0.0f) {
                    stopped = true;
                }
            } else if (volume < full) {
                volume = Math.min(full, volume + 0.01f);
            }
        }
    }
}
