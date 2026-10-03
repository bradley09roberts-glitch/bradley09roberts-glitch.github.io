package com.terracraft.network.packet;

import com.terracraft.menu.AccessoryMenu;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import com.terracraft.network.PacketContext;

/** Client -> server: open the equipment / accessory screen. */
public record OpenAccessoriesPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<OpenAccessoriesPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("open_accessories_packet"));

    @Override
    public Type<OpenAccessoriesPacket> type() {
        return TYPE;
    }

    public static final OpenAccessoriesPacket INSTANCE = new OpenAccessoriesPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAccessoriesPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenAccessoriesPacket packet, PacketContext ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || player.isSpectator()) {
            return;
        }
        TerraPlayerData data = TerraPlayerData.get(player);
        // Vanilla opening (not Forge's extra-data payload): the open-screen and content packets then share the
        // client's packet queue, so the accessory contents can never arrive before the menu exists.
        // The client knows its slot count from the synced player stats.
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AccessoryMenu(id, inventory, data),
            Component.translatable("screen.terracraft.equipment")));
    }
}
