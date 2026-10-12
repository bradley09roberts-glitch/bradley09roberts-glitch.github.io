package com.grandline.dialogue;

import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Beli shops by NPC role. Prices scale with the island's tier; reputation gives discounts. */
public final class Shops {
    record Offer(String item, int count, long price) {}

    static List<Offer> offers(String role) {
        return switch (role) {
            case "blacksmith" -> List.of(new Offer("grandline:katana", 1, 1200), new Offer("grandline:cutlass", 1, 900), new Offer("grandline:spear", 1, 900),
                    new Offer("grandline:dual_blades", 1, 1100), new Offer("grandline:brass_knuckles", 1, 700), new Offer("grandline:kanabo", 1, 1500),
                    new Offer("grandline:flintlock", 1, 1500), new Offer("grandline:musket", 1, 2200), new Offer("grandline:slingshot", 1, 400),
                    new Offer("grandline:bullet", 16, 300), new Offer("grandline:pellet", 32, 100), new Offer("grandline:cannonball", 4, 500),
                    new Offer("minecraft:iron_chestplate", 1, 1800), new Offer("grandline:fine_katana", 1, 9000));
            case "shipwright" -> List.of(new Offer("minecraft:oak_planks", 32, 120), new Offer("grandline:cannonball", 8, 900));
            default -> List.of(new Offer("minecraft:bread", 8, 60), new Offer("grandline:meat_on_bone", 4, 200), new Offer("minecraft:cooked_cod", 8, 80),
                    new Offer("grandline:log_pose", 1, 3000), new Offer("grandline:sea_chart", 1, 800), new Offer("grandline:bullet", 16, 320),
                    new Offer("minecraft:torch", 16, 40), new Offer("minecraft:lead", 2, 60), new Offer("minecraft:potion", 1, 300),
                    new Offer("grandline:seastone_cuffs", 1, 4000));
        };
    }

    public static void open(ServerPlayer sp, GLNpc npc) {
        CharacterData d = Characters.get(sp);
        CompoundTag t = new CompoundTag();
        t.putInt("npc", npc.getId());
        t.putLong("beli", d.beli);
        ListTag l = new ListTag();
        int i = 0;
        for (Offer o : offers(npc.role())) {
            CompoundTag e = new CompoundTag();
            e.putInt("i", i++);
            e.putString("item", o.item);
            e.putInt("count", o.count);
            e.putLong("price", price(o, npc, d));
            l.add(e);
        }
        t.put("offers", l);
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("shop", t));
    }

    static long price(Offer o, GLNpc npc, CharacterData d) {
        double mult = 1 + (npc.tier() - 1) * 0.15;
        int rep = d.reputation("island/" + npc.islandId()) + d.reputation(npc.faction().id());
        mult *= Math.max(0.6, 1 - rep / 2000.0);
        return Math.max(1, Math.round(o.price * mult));
    }

    public static void buy(ServerPlayer sp, int npcId, int index) {
        Entity e = sp.level().getEntity(npcId);
        if (!(e instanceof GLNpc npc) || npc.distanceTo(sp) > 8) return;
        List<Offer> list = offers(npc.role());
        if (index < 0 || index >= list.size()) return;
        Offer o = list.get(index);
        CharacterData d = Characters.get(sp);
        long price = price(o, npc, d);
        if (d.beli < price) {
            sp.sendSystemMessage(Component.translatable("grandline.shop.poor").withStyle(ChatFormatting.RED));
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.item));
        d.beli -= price;
        ItemStack stack = new ItemStack(item, o.count);
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        CharacterManager.markDirty(sp);
        open(sp, npc);
    }

    private Shops() {}
}
