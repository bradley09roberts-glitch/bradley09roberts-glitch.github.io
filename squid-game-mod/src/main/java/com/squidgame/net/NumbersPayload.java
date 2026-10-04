package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps player UUIDs to contestant numbers so every client can draw the tracksuit and number bib on the players
 * who are in the tournament (empty map = nobody). Resent whenever the roster changes.
 */
public record NumbersPayload(Map<UUID, Integer> numbers) implements CustomPacketPayload {
    public static final Type<NumbersPayload> TYPE = new Type<>(SquidGameMod.id("numbers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NumbersPayload> CODEC = new StreamCodec<>() {
        @Override
        public NumbersPayload decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            Map<UUID, Integer> map = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                map.put(buf.readUUID(), buf.readVarInt());
            }
            return new NumbersPayload(map);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, NumbersPayload p) {
            buf.writeVarInt(p.numbers.size());
            p.numbers.forEach((id, num) -> {
                buf.writeUUID(id);
                buf.writeVarInt(num);
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
