package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.PlayerStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server -> owning client: the player's Terraria resources and the subset of computed stats the client
 * needs (HUD numbers, movement abilities). Sent when anything changes, throttled for mana regeneration.
 */
public record SyncPlayerStatsPacket(
    int lifeCrystals,
    int lifeFruit,
    int manaCrystals,
    int maxLife,
    int mana,
    int maxMana,
    int defense,
    int extraJumps,
    int accessorySlots,
    int abilityBits,
    String setBonus
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerStatsPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.lifeCrystals);
            buf.writeVarInt(p.lifeFruit);
            buf.writeVarInt(p.manaCrystals);
            buf.writeVarInt(p.maxLife);
            buf.writeVarInt(p.mana);
            buf.writeVarInt(p.maxMana);
            buf.writeVarInt(p.defense);
            buf.writeVarInt(p.extraJumps);
            buf.writeVarInt(p.accessorySlots);
            buf.writeInt(p.abilityBits);
            buf.writeUtf(p.setBonus, 128);
        },
        buf -> new SyncPlayerStatsPacket(
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readUtf(128)
        )
    );

    public static SyncPlayerStatsPacket of(TerraPlayerData data) {
        PlayerStats stats = data.stats();
        return new SyncPlayerStatsPacket(
            data.lifeCrystals(), data.lifeFruit(), data.manaCrystals(),
            stats.maxLife, (int) Math.floor(data.mana()), stats.maxMana,
            stats.defense(), stats.extraJumps(), data.usableAccessorySlots(), stats.abilityBits(), stats.activeSetBonus
        );
    }

    public static void handle(SyncPlayerStatsPacket packet, CustomPayloadEvent.Context ctx) {
        ClientPacketHandlers.handlePlayerStats(packet);
    }
}
