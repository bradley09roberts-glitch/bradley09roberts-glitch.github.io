package com.squidgame.game.tug;

import com.squidgame.SquidGameMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Networking of the Tug of War beyond the generic HUD: the server sends every viewer a compact snapshot of the match a few
 * times per second ({@link StatePayload}); the client's input goes the other way through the generic
 * {@code ClientActionPayload} ({@code tug.input} for the held keys, {@code tug.heave} for a tap) and is validated by the game.
 * Registered by name from {@code ModNetwork.init} (this class is {@code com.squidgame.game.tug.TugNet}).
 */
public final class TugNet {
    private TugNet() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StatePayload.TYPE, StatePayload.CODEC);
    }

    // stage of the game, as the client needs to know it
    public static final int STAGE_NONE = 0, STAGE_WALK = 1, STAGE_COUNT_IN = 2, STAGE_MATCH = 3, STAGE_SUDDEN = 4,
            STAGE_FALL = 5, STAGE_BETWEEN = 6;
    // what the viewer is doing in the current heat
    public static final int ROLE_WATCH = 0, ROLE_TEAM_A = 1, ROLE_TEAM_B = 2, ROLE_WAIT = 3;
    // verdict of the viewer's last heave
    public static final int HEAVE_NONE = 0, HEAVE_HIT = 1, HEAVE_MISTIMED = 2, HEAVE_EXHAUSTED = 3, HEAVE_TOO_SOON = 4;

    /**
     * Snapshot of the match for one viewer.
     *
     * @param tick          server tick the snapshot was taken at (the client maps ticks to its own clock with it)
     * @param beatEpoch     tick of beat 0 (the GO); beats fall on {@code beatEpoch + k * beatPeriod}, also before the GO (count-in)
     * @param window        half width of the heave window in ticks
     * @param offset        rope offset -1 (team A's edge) .. +1 (team B's edge)
     * @param stance        the viewer's own stance: 0 rest, 1 pull, 2 brace, 3 spent
     * @param sync          share of the viewer's team (or the stronger team for spectators) that is in a heave burst
     * @param timeLeft      ticks left of the heat
     * @param heaveSeq      counts the viewer's judged heaves; the client shows the verdict when it changes
     * @param heaveError    ticks away from the beat of the last heave (negative = early)
     * @param danger        0 safe, 1 losing, 2 about to fall
     */
    public record StatePayload(int stage, int role, long tick, long beatEpoch, int beatPeriod, float window,
                               float offset, float velocity, float strain, float stamina, int stance, boolean exhausted,
                               float staminaA, float staminaB, float sync, int presentA, int presentB, int timeLeft,
                               int heaveSeq, int heaveResult, int heaveError, float heaveQuality, int danger)
            implements CustomPacketPayload {

        public static final Type<StatePayload> TYPE = new Type<>(SquidGameMod.id("tug_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = new StreamCodec<>() {
            @Override
            public StatePayload decode(RegistryFriendlyByteBuf buf) {
                int stage = buf.readVarInt();
                int role = buf.readVarInt();
                long tick = buf.readVarLong();
                long epoch = buf.readLong();
                int period = buf.readVarInt();
                float window = buf.readFloat();
                float offset = buf.readFloat();
                float velocity = buf.readFloat();
                float strain = buf.readFloat();
                float stamina = buf.readFloat();
                int stance = buf.readVarInt();
                boolean exhausted = buf.readBoolean();
                float staminaA = buf.readFloat();
                float staminaB = buf.readFloat();
                float sync = buf.readFloat();
                int presentA = buf.readVarInt();
                int presentB = buf.readVarInt();
                int timeLeft = buf.readVarInt();
                int heaveSeq = buf.readVarInt();
                int heaveResult = buf.readVarInt();
                int heaveError = buf.readByte();
                float heaveQuality = buf.readFloat();
                int danger = buf.readVarInt();
                return new StatePayload(stage, role, tick, epoch, period, window, offset, velocity, strain, stamina, stance, exhausted,
                        staminaA, staminaB, sync, presentA, presentB, timeLeft, heaveSeq, heaveResult, heaveError, heaveQuality, danger);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, StatePayload p) {
                buf.writeVarInt(p.stage);
                buf.writeVarInt(p.role);
                buf.writeVarLong(p.tick);
                buf.writeLong(p.beatEpoch);
                buf.writeVarInt(p.beatPeriod);
                buf.writeFloat(p.window);
                buf.writeFloat(p.offset);
                buf.writeFloat(p.velocity);
                buf.writeFloat(p.strain);
                buf.writeFloat(p.stamina);
                buf.writeVarInt(p.stance);
                buf.writeBoolean(p.exhausted);
                buf.writeFloat(p.staminaA);
                buf.writeFloat(p.staminaB);
                buf.writeFloat(p.sync);
                buf.writeVarInt(p.presentA);
                buf.writeVarInt(p.presentB);
                buf.writeVarInt(p.timeLeft);
                buf.writeVarInt(p.heaveSeq);
                buf.writeVarInt(p.heaveResult);
                buf.writeByte(p.heaveError);
                buf.writeFloat(p.heaveQuality);
                buf.writeVarInt(p.danger);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
