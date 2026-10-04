package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The results board shown during the RESULTS and FINAL_WINNER phases. {@code survivors} / {@code eliminated} are
 * contestant numbers (eliminated is capped to a readable length by the sender); {@code headline} is the big text.
 */
public record ResultsPayload(Component headline, Component subline, List<Integer> survivors, List<Integer> eliminated,
                             int myNumber, int myOutcome, int showTicks, long prizeWon) implements CustomPacketPayload {
    public static final Type<ResultsPayload> TYPE = new Type<>(SquidGameMod.id("results"));
    /** myOutcome values */
    public static final int OUTCOME_NONE = 0, OUTCOME_SURVIVED = 1, OUTCOME_ELIMINATED = 2, OUTCOME_WINNER = 3;

    public static final StreamCodec<RegistryFriendlyByteBuf, ResultsPayload> CODEC = new StreamCodec<>() {
        @Override
        public ResultsPayload decode(RegistryFriendlyByteBuf buf) {
            Component h = ComponentSerialization.STREAM_CODEC.decode(buf);
            Component s = ComponentSerialization.STREAM_CODEC.decode(buf);
            int n = buf.readVarInt();
            List<Integer> sv = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                sv.add(buf.readVarInt());
            }
            int m = buf.readVarInt();
            List<Integer> el = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                el.add(buf.readVarInt());
            }
            return new ResultsPayload(h, s, sv, el, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ResultsPayload p) {
            ComponentSerialization.STREAM_CODEC.encode(buf, p.headline);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.subline);
            buf.writeVarInt(p.survivors.size());
            p.survivors.forEach(buf::writeVarInt);
            buf.writeVarInt(p.eliminated.size());
            p.eliminated.forEach(buf::writeVarInt);
            buf.writeVarInt(p.myNumber);
            buf.writeVarInt(p.myOutcome);
            buf.writeVarInt(p.showTicks);
            buf.writeLong(p.prizeWon);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
