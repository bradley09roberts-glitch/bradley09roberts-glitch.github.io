package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import com.terracraft.progression.WorldVariants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import com.terracraft.network.PacketContext;

import java.util.List;
import java.util.Map;

/** Server -> client: full world progression state (small; sent on login and on every change). */
public record SyncProgressionPacket(List<Identifier> flags, Map<Identifier, Integer> counters, WorldVariants variants) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<SyncProgressionPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("sync_progression_packet"));

    @Override
    public Type<SyncProgressionPacket> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProgressionPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeCollection(p.flags, FriendlyByteBuf::writeIdentifier);
            buf.writeMap(p.counters, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeVarInt);
            WorldVariants.STREAM_CODEC.encode(buf, p.variants);
        },
        buf -> new SyncProgressionPacket(
            buf.readList(FriendlyByteBuf::readIdentifier),
            buf.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readVarInt),
            WorldVariants.STREAM_CODEC.decode(buf)
        )
    );

    public static void handle(SyncProgressionPacket packet, PacketContext ctx) {
        ClientPacketHandlers.handleProgression(packet);
    }
}
