package com.grandline.npc;

import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Faction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Bounties grow with notoriety; thresholds trigger world reactions (posters, Marine hunts). */
public final class Bounties {
    public static final long[] THRESHOLDS = {1_000_000, 10_000_000, 50_000_000, 100_000_000, 300_000_000, 500_000_000, 1_000_000_000L, 3_000_000_000L};

    public static void raise(ServerPlayer sp, CharacterData d, long amount, Component reason) {
        if (d.faction == Faction.MARINE && amount > 0) {
            // Marines lose standing instead and may be branded deserters
            d.addReputation(Faction.MARINE.id(), -(int) Math.min(200, amount / 2000));
            if (d.reputation(Faction.MARINE.id()) < -300) {
                d.faction = Faction.PIRATE;
                sp.sendSystemMessage(Component.translatable("grandline.faction.deserter").withStyle(ChatFormatting.DARK_RED));
            } else return;
        }
        long before = d.bounty;
        d.bounty += amount;
        for (long t : THRESHOLDS) {
            if (before < t && d.bounty >= t) {
                sp.server.getPlayerList().broadcastSystemMessage(Component.translatable("grandline.bounty.new_poster", d.name, String.format("%,d", d.bounty)).withStyle(ChatFormatting.YELLOW), false);
                ItemStack poster = poster(d);
                poster.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, c -> c.update(tag -> tag.putUUID("uuid", sp.getUUID())));
                sp.getInventory().add(poster);
            }
        }
        sp.displayClientMessage(Component.translatable("grandline.bounty.raised", String.format("%,d", d.bounty)).withStyle(ChatFormatting.YELLOW), true);
        CharacterManager.markVisualsDirty(sp);
        com.grandline.journey.Journey.onBounty(sp, d.bounty); // journey: poster, 10M, 100M, 1B
        com.grandline.ship.BusterCall.onCrime(sp); // a crime seen from a Marine base may bring a Buster Call
    }

    public static ItemStack poster(CharacterData d) {
        ItemStack s = new ItemStack(com.grandline.registry.ModItems.WANTED_POSTER.get());
        CompoundTag t = new CompoundTag();
        t.putString("name", d.name);
        t.putLong("bounty", d.bounty);
        t.putString("race", d.race.id());
        t.putString("faction", d.faction.id());
        t.putBoolean("alive", true);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    private Bounties() {}
}
