package com.squidgame.game.dalgona;

import com.squidgame.SquidGameMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Networking of the honeycomb game beyond the generic screen / action payloads: the server pushes the
 * authoritative state of the contestant's cookie ({@link StatePayload}) to the open carving screen. The client
 * sends its strokes as {@code ClientActionPayload("dalgona.stroke", ...)} (see {@link DalgonaGame#onClientAction}).
 *
 * <p>A separate payload is used for the state (instead of an UPDATE of the screen) because the generic update re-opens
 * a screen the player has closed with Esc, which must stay closed.
 */
public final class DalgonaNet {
    private DalgonaNet() {
    }

    /** Called by {@code ModNetwork.init}. */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(StatePayload.TYPE, StatePayload.CODEC);
    }

    /** {@link StatePayload#flags}: how the cookie ended. */
    public static final int F_CRACKED = 1, F_DONE = 2, F_TIMEOUT = 4;
    /** {@link StatePayload#events}: things that happened since the previous state (feedback cues). */
    public static final int E_SPIKE = 1, E_LICK = 2, E_PENALTY = 4;

    /**
     * Server -> client: the cookie as the server sees it. {@code stress} 0..100, {@code carved} samples carved,
     * {@code carvedBits} the carved samples (bit i = outline sample i), {@code lickCooldown}/{@code lickLock} in
     * ticks, {@code timeLeft}/{@code timeTotal} the game clock in ticks.
     */
    public record StatePayload(float stress, int carved, int licks, int lickCooldown, int lickLock, int flags, int events,
                               int timeLeft, int timeTotal, long[] carvedBits) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(SquidGameMod.id("dalgona_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = new StreamCodec<>() {
            @Override
            public StatePayload decode(RegistryFriendlyByteBuf buf) {
                float stress = buf.readFloat();
                int carved = buf.readVarInt();
                int licks = buf.readVarInt();
                int cooldown = buf.readVarInt();
                int lock = buf.readVarInt();
                int flags = buf.readVarInt();
                int events = buf.readVarInt();
                int left = buf.readVarInt();
                int total = buf.readVarInt();
                long[] bits = buf.readLongArray();
                return new StatePayload(stress, carved, licks, cooldown, lock, flags, events, left, total, bits);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, StatePayload p) {
                buf.writeFloat(p.stress);
                buf.writeVarInt(p.carved);
                buf.writeVarInt(p.licks);
                buf.writeVarInt(p.lickCooldown);
                buf.writeVarInt(p.lickLock);
                buf.writeVarInt(p.flags);
                buf.writeVarInt(p.events);
                buf.writeVarInt(p.timeLeft);
                buf.writeVarInt(p.timeTotal);
                buf.writeLongArray(p.carvedBits);
            }
        };

        public boolean cracked() {
            return (flags & F_CRACKED) != 0;
        }

        public boolean done() {
            return (flags & F_DONE) != 0;
        }

        public boolean timedOut() {
            return (flags & F_TIMEOUT) != 0;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
