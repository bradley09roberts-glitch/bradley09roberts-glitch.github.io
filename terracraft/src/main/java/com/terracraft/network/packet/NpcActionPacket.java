package com.terracraft.network.packet;

import com.terracraft.npc.NpcManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: an action in a town NPC's chat/shop window ("close", "buy" + index, "sell", "heal", "help", "shop"). */
public record NpcActionPacket(int entityId, String action, int index) {
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcActionPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, NpcActionPacket::entityId,
        ByteBufCodecs.STRING_UTF8, NpcActionPacket::action,
        ByteBufCodecs.VAR_INT, NpcActionPacket::index,
        NpcActionPacket::new);

    public static void handle(NpcActionPacket packet, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            NpcManager.handleAction(player, packet.entityId(), packet.action(), packet.index());
        }
    }
}
