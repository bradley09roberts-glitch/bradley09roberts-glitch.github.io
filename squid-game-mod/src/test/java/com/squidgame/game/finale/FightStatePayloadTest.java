package com.squidgame.game.finale;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The state packet of the overlay survives the wire and stays tiny. */
class FightStatePayloadTest {
    private static FightStatePayload roundTrip(FightStatePayload p) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FightStatePayload.CODEC.encode(buf, p);
        FightStatePayload back = FightStatePayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the whole packet is consumed");
        return back;
    }

    private static void assertSame(FightStatePayload a, FightStatePayload b) {
        assertEquals(a.stage(), b.stage());
        assertEquals(a.myRole(), b.myRole());
        assertArrayEquals(a.number(), b.number());
        assertArrayEquals(a.health(), b.health());
        assertArrayEquals(a.stamina(), b.stamina());
        assertArrayEquals(a.flags(), b.flags());
        assertEquals(a.capture(), b.capture());
        assertEquals(a.ticksLeft(), b.ticksLeft());
        assertArrayEquals(a.duel(), b.duel());
        assertEquals(a.edge(), b.edge());
        assertArrayEquals(a.took(), b.took());
        assertArrayEquals(a.dealt(), b.dealt());
        assertEquals(a.coinTick(), b.coinTick());
        assertEquals(a.denied(), b.denied());
    }

    @Test
    void aFullStateSurvivesTheWire() {
        FightStatePayload p = new FightStatePayload(FightStatePayload.FIGHT, 2, new int[]{440, 151}, new int[]{1000, 372},
                new int[]{250, 1000}, new int[]{FightStatePayload.FLAG_GUARD | FightStatePayload.FLAG_STAGGERED, FightStatePayload.FLAG_CHARGING},
                63, 3412, new int[]{3, 11}, 42, new int[]{17, FightStatePayload.HIT_HEAVY, 22}, new int[]{9, FightStatePayload.HIT_BLOCKED, 2}, 0, 5);
        assertSame(p, roundTrip(p));
    }

    @Test
    void theUnknownEdgeOfASpectatorKeepsItsMinusOne() {
        FightStatePayload p = new FightStatePayload(FightStatePayload.COIN, 0, new int[]{1, 456}, new int[]{1000, 1000}, new int[]{1000, 1000},
                new int[]{0, 0}, 0, 0, new int[]{1, 1}, -1, new int[]{0, 0, 0}, new int[]{0, 0, 0}, 33, 0);
        FightStatePayload back = roundTrip(p);
        assertSame(p, back);
        assertEquals(-1, back.edge());
    }

    @Test
    void everyStageAndExtremeValuesRoundTrip() {
        for (int stage = FightStatePayload.NONE; stage <= FightStatePayload.CEREMONY; stage++) {
            FightStatePayload p = new FightStatePayload(stage, stage % 3, new int[]{456, 1}, new int[]{0, 1000}, new int[]{0, 1000},
                    new int[]{31, 0}, 100, 180 * 20, new int[]{99, 99}, 1200, new int[]{Integer.MAX_VALUE / 2, 7, 999}, new int[]{1, 1, 1}, 100000, 123456);
            assertSame(p, roundTrip(p));
        }
    }

    @Test
    void thePacketIsSmall() {
        FightStatePayload p = new FightStatePayload(FightStatePayload.FIGHT, 1, new int[]{440, 151}, new int[]{1000, 1000},
                new int[]{1000, 1000}, new int[]{0, 0}, 0, 3600, new int[]{1, 1}, 55, new int[]{0, 0, 0}, new int[]{0, 0, 0}, 0, 0);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FightStatePayload.CODEC.encode(buf, p);
        assertTrue(buf.readableBytes() <= 48, "sent every tick to every fighter: " + buf.readableBytes() + " bytes");
    }

    @Test
    void theTypeHasTheModNamespace() {
        assertEquals("squidgame", FightStatePayload.TYPE.id().getNamespace());
        assertEquals("fight_state", FightStatePayload.TYPE.id().getPath());
    }
}
