package com.squidgame.game.bridge;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Public knowledge about the bridge for the optional client overlay: what everybody has seen so far, never more.
 * {@code states} holds two entries per row (lane 0, lane 1) with the ordinal of
 * {@link com.squidgame.core.bridge.BridgeKnowledge.LaneState}; {@code rows == 0} clears the overlay.
 *
 * @param firstZ   world z of the near edge of row 0 (the client derives the player's row from its own position)
 * @param pitch    distance between two rows
 * @param leftLane the lane that is on the left of a contestant walking along the bridge
 */
public record BridgeMapPayload(int rows, int firstZ, int pitch, int leftLane, byte[] states) implements CustomPacketPayload {
    public static final Type<BridgeMapPayload> TYPE = new Type<>(SquidGameMod.id("bridge_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeMapPayload> CODEC = new StreamCodec<>() {
        @Override
        public BridgeMapPayload decode(RegistryFriendlyByteBuf buf) {
            int rows = buf.readVarInt();
            int firstZ = buf.readInt();
            int pitch = buf.readVarInt();
            int leftLane = buf.readVarInt();
            byte[] states = buf.readByteArray(256);
            return new BridgeMapPayload(rows, firstZ, pitch, leftLane, states);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BridgeMapPayload p) {
            buf.writeVarInt(p.rows);
            buf.writeInt(p.firstZ);
            buf.writeVarInt(p.pitch);
            buf.writeVarInt(p.leftLane);
            buf.writeByteArray(p.states);
        }
    };

    /** The cleared overlay. */
    public static BridgeMapPayload empty() {
        return new BridgeMapPayload(0, 0, 3, 1, new byte[0]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
