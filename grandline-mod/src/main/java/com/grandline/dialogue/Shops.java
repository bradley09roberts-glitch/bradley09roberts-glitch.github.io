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
    /** An offer; {@code painting} names a painting variant when the item is a painting of a particular scene. */
    record Offer(String item, int count, long price, @org.jetbrains.annotations.Nullable String painting) {
        Offer(String item, int count, long price) {
            this(item, count, price, null);
        }
    }

    /** The painted scenes of the seas (Higgsfield art, see docs/ART.md); each island's merchant stocks three. */
    public static final String[] PAINTINGS = {"red_line", "reverse_mountain", "knock_up_stream", "white_sea", "bubble_city", "sabaody_grove",
            "calm_belt", "last_island", "sea_train", "cyclone"};

    /** What this NPC sells this player: a career shop (Marine supply, a pirate port's fence, revolutionary contacts) or the usual. */
    static List<Offer> offers(ServerPlayer sp, GLNpc npc) {
        var career = com.grandline.career.CareerPerks.shop(sp, npc);
        if (career == null) return offers(npc);
        List<Offer> l = new java.util.ArrayList<>();
        for (var o : career) l.add(new Offer(o.item(), o.count(), o.price()));
        return l;
    }

    static List<Offer> offers(GLNpc npc) {
        List<Offer> l = new java.util.ArrayList<>(offers(npc.role()));
        // sea activities: treasure maps from shady dealers, catches and dishes from cooks, fishing rods
        for (var x : com.grandline.activity.ActivityShops.extras(npc)) l.add(new Offer(x.item(), x.count(), x.price()));
        for (var x : com.grandline.journey.JourneyRegistry.shopExtras(npc.role())) l.add(new Offer(x.item(), x.count(), x.price())); // Den Den Mushi
        if (!npc.role().equals("merchant") && !npc.role().equals("shopkeeper")) return l;
        int h = Math.floorMod(npc.islandId().hashCode(), PAINTINGS.length);
        for (int k = 0; k < 3; k++) l.add(new Offer("minecraft:painting", 1, 2500 + k * 1500L, PAINTINGS[(h + k * 3) % PAINTINGS.length]));
        return l;
    }

    /** A painting item that hangs the given scene. */
    public static ItemStack painting(String variant) {
        ItemStack st = new ItemStack(net.minecraft.world.item.Items.PAINTING);
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:painting");
        t.putString("variant", "grandline:" + variant);
        st.set(net.minecraft.core.component.DataComponents.ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(t));
        return st;
    }

    static List<Offer> offers(String role) {
        return switch (role) {
            // a merchant ship's cargo, sold over the rail at sea (hailed: see ship/LivingSea)
            case "sea_merchant" -> List.of(new Offer("grandline:sea_king_steak", 2, 900), new Offer("grandline:meat_on_bone", 6, 280),
                    new Offer("minecraft:cooked_cod", 12, 110), new Offer("minecraft:oak_planks", 32, 150), new Offer("minecraft:white_wool", 8, 200),
                    new Offer("grandline:cannonball", 8, 1000), new Offer("grandline:chain_shot", 4, 1400), new Offer("grandline:grappling_hook", 1, 2900),
                    new Offer("grandline:pop_green", 4, 1200), new Offer("grandline:sea_chart", 1, 900), new Offer("grandline:log_pose", 1, 3400),
                    new Offer("grandline:bullet", 24, 450), new Offer("minecraft:gold_ingot", 4, 2400));
            case "blacksmith" -> List.of(new Offer("grandline:katana", 1, 1200), new Offer("grandline:cutlass", 1, 900), new Offer("grandline:spear", 1, 900),
                    new Offer("grandline:dual_blades", 1, 1100), new Offer("grandline:brass_knuckles", 1, 700), new Offer("grandline:kanabo", 1, 1500),
                    new Offer("grandline:flintlock", 1, 1500), new Offer("grandline:musket", 1, 2200), new Offer("grandline:slingshot", 1, 400),
                    new Offer("grandline:bullet", 16, 300), new Offer("grandline:pellet", 32, 100), new Offer("grandline:cannonball", 4, 500),
                    new Offer("minecraft:iron_chestplate", 1, 1800), new Offer("grandline:fine_katana", 1, 9000));
            case "shipwright" -> List.of(new Offer("minecraft:oak_planks", 32, 120), new Offer("grandline:cannonball", 8, 900),
                    new Offer("grandline:chain_shot", 4, 1200), new Offer("grandline:grape_shot", 4, 1000), new Offer("grandline:grappling_hook", 1, 2500),
                    new Offer("minecraft:white_wool", 8, 160));
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
        for (Offer o : offers(sp, npc)) {
            CompoundTag e = new CompoundTag();
            e.putInt("i", i++);
            e.putString("item", o.item);
            if (o.painting != null) e.putString("painting", o.painting);
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
        mult *= com.grandline.career.CareerPerks.priceFactor(d); // careers: a merchant's discount
        return Math.max(1, Math.round(o.price * mult));
    }

    public static void buy(ServerPlayer sp, int npcId, int index) {
        Entity e = sp.level().getEntity(npcId);
        if (!(e instanceof GLNpc npc) || npc.distanceTo(sp) > 8) return;
        List<Offer> list = offers(sp, npc);
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
        com.grandline.career.Careers.onTrade(sp, price); // careers: a merchant's trade
        ItemStack stack = o.painting != null ? painting(o.painting) : new ItemStack(item, o.count);
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        CharacterManager.markDirty(sp);
        open(sp, npc);
    }

    private Shops() {}
}
