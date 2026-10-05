package com.squidgame.game.bridge;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The packet that carries the public bridge knowledge to the client overlay must survive its own codec. */
class BridgeMapPayloadTest {
    private static BridgeMapPayload roundTrip(BridgeMapPayload p) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BridgeMapPayload.CODEC.encode(buf, p);
        BridgeMapPayload q = BridgeMapPayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the whole packet was read");
        return q;
    }

    @Test
    void aFullMapSurvivesTheCodec() {
        byte[] states = new byte[36];
        for (int i = 0; i < states.length; i++) {
            states[i] = (byte) (i % 4);
        }
        BridgeMapPayload q = roundTrip(new BridgeMapPayload(18, 5010, 3, 1, states));
        assertEquals(18, q.rows());
        assertEquals(5010, q.firstZ());
        assertEquals(3, q.pitch());
        assertEquals(1, q.leftLane());
        assertArrayEquals(states, q.states());
    }

    @Test
    void theClearingPacketIsEmptyAndRoundTrips() {
        BridgeMapPayload q = roundTrip(BridgeMapPayload.empty());
        assertEquals(0, q.rows());
        assertEquals(0, q.states().length);
    }

    @Test
    void aNegativeFirstRowSurvivesToo() {
        // an arena whose bridge lies at negative z (the client only needs the number back unchanged)
        BridgeMapPayload q = roundTrip(new BridgeMapPayload(18, -250, 3, 0, new byte[36]));
        assertEquals(-250, q.firstZ());
    }
}
