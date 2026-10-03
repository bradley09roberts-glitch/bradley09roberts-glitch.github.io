package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.terracraft.network.PacketContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: open (or refresh) a town NPC's chat window.
 *
 * @param dialogueKey  lang key of the line to show; {@code dialogueArg} fills its {@code %s}
 * @param services     buttons to offer ("shop", "heal", "help")
 * @param offers       the NPC's current shop (already filtered by progression/time on the server)
 */
public record OpenNpcChatPacket(int entityId, String npcId, String dialogueKey, String dialogueArg, List<String> services,
                                List<Offer> offers) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<OpenNpcChatPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("open_npc_chat_packet"));

    @Override
    public Type<OpenNpcChatPacket> type() {
        return TYPE;
    }

    public record Offer(String item, int count, long price) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcChatPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.entityId);
            buf.writeUtf(p.npcId);
            buf.writeUtf(p.dialogueKey);
            buf.writeUtf(p.dialogueArg);
            buf.writeVarInt(p.services.size());
            p.services.forEach(buf::writeUtf);
            buf.writeVarInt(p.offers.size());
            for (Offer offer : p.offers) {
                buf.writeUtf(offer.item);
                buf.writeVarInt(offer.count);
                buf.writeVarLong(offer.price);
            }
        },
        buf -> {
            int entityId = buf.readVarInt();
            String npcId = buf.readUtf();
            String key = buf.readUtf();
            String arg = buf.readUtf();
            int serviceCount = buf.readVarInt();
            List<String> services = new ArrayList<>();
            for (int i = 0; i < serviceCount; i++) {
                services.add(buf.readUtf());
            }
            int offerCount = buf.readVarInt();
            List<Offer> offers = new ArrayList<>();
            for (int i = 0; i < offerCount; i++) {
                offers.add(new Offer(buf.readUtf(), buf.readVarInt(), buf.readVarLong()));
            }
            return new OpenNpcChatPacket(entityId, npcId, key, arg, services, offers);
        });

    public static void handle(OpenNpcChatPacket packet, PacketContext ctx) {
        ClientPacketHandlers.openNpcChat(packet);
    }
}
