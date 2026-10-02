package com.terracraft.network.packet;

import com.terracraft.mining.MiningPower;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Map;

/** Server -> client: resolved block -> required pickaxe power table. */
public record SyncMiningPowerPacket(Map<Identifier, Integer> table) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMiningPowerPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeMap(p.table, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeVarInt),
        buf -> new SyncMiningPowerPacket(buf.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readVarInt))
    );

    public static void handle(SyncMiningPowerPacket packet, CustomPayloadEvent.Context ctx) {
        // In single player client and server share the table; only replace it on a remote client.
        if (FMLEnvironment.dist == Dist.CLIENT && !ctx.getConnection().isMemoryConnection()) {
            MiningPower.applySnapshot(packet.table);
        }
    }
}
