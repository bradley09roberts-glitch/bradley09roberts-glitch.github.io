package com.terracraft.network.packet;

import com.terracraft.item.weapon.UsableWeapon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.terracraft.network.PacketContext;

/**
 * Client -> server: Terraria-style left-click use of the held ranged / magic / thrown weapon.
 * Cooldowns, ammo and mana are all checked on the server.
 */
public record UseWeaponPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<UseWeaponPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("use_weapon_packet"));

    @Override
    public Type<UseWeaponPacket> type() {
        return TYPE;
    }

    public static final UseWeaponPacket INSTANCE = new UseWeaponPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, UseWeaponPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(UseWeaponPacket packet, PacketContext ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof UsableWeapon weapon) {
            UsableWeapon.tryFire(weapon, player, stack);
        }
    }
}
