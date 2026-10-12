package com.longnight.game;

import com.longnight.sim.Commodity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** How real items map onto ledger commodities (and back). */
public final class TradeGoods {
    private TradeGoods() {}

    public record Units(Commodity commodity, double perItem) {}

    public static Units of(ItemStack s) {
        if (s.isEmpty()) return null;
        if (s.isDamageableItem() && s.getDamageValue() > 0) return null; // only sound goods are bought
        Item i = s.getItem();
        if (i == Items.WHEAT) return new Units(Commodity.GRAIN, 1);
        if (i == Items.HAY_BLOCK) return new Units(Commodity.GRAIN, 9);
        if (i == Items.BREAD) return new Units(Commodity.BREAD, 1);
        if (i == Items.CARROT || i == Items.POTATO || i == Items.BEETROOT || i == Items.BAKED_POTATO || i == Items.APPLE
                || i == Items.SWEET_BERRIES || i == Items.MELON_SLICE) return new Units(Commodity.VEGETABLES, 1);
        if (i == Items.BEEF || i == Items.PORKCHOP || i == Items.MUTTON || i == Items.CHICKEN || i == Items.RABBIT
                || i == Items.COOKED_BEEF || i == Items.COOKED_PORKCHOP || i == Items.COOKED_MUTTON || i == Items.COOKED_CHICKEN
                || i == Items.COOKED_RABBIT) return new Units(Commodity.MEAT, 1);
        if (i == Items.COD || i == Items.SALMON || i == Items.COOKED_COD || i == Items.COOKED_SALMON) return new Units(Commodity.FISH, 1);
        if (s.is(ItemTags.LOGS)) return new Units(Commodity.LOGS, 1);
        if (s.is(ItemTags.PLANKS)) return new Units(Commodity.PLANKS, 1);
        if (i == Items.COBBLESTONE || i == Items.STONE || i == Items.COBBLED_DEEPSLATE || i == Items.ANDESITE || i == Items.DIORITE
                || i == Items.GRANITE || i == Items.SANDSTONE || i == Items.STONE_BRICKS || i == Items.DEEPSLATE || i == Items.BLACKSTONE
                || i == Items.TUFF || i == Items.MUD_BRICKS || i == Items.BRICKS) return new Units(Commodity.STONE, 1);
        if (i == Items.GLASS) return new Units(Commodity.GLASS, 1);
        if (i == Items.RAW_IRON || i == Items.IRON_ORE || i == Items.DEEPSLATE_IRON_ORE) return new Units(Commodity.ORE, 1);
        if (i == Items.IRON_INGOT) return new Units(Commodity.IRON, 1);
        if (i == Items.COAL || i == Items.CHARCOAL) return new Units(Commodity.FUEL, 1);
        if (i == Items.IRON_PICKAXE || i == Items.IRON_AXE || i == Items.IRON_SHOVEL || i == Items.IRON_HOE) return new Units(Commodity.TOOLS, 1);
        if (i == Items.IRON_SWORD || i == Items.BOW || i == Items.CROSSBOW) return new Units(Commodity.WEAPONS, 1);
        if (i == Items.IRON_HELMET || i == Items.IRON_CHESTPLATE || i == Items.IRON_LEGGINGS || i == Items.IRON_BOOTS
                || i == Items.CHAINMAIL_CHESTPLATE) return new Units(Commodity.ARMOR, 1);
        if (s.is(ItemTags.WOOL)) return new Units(Commodity.WOOL, 1);
        if (i == Items.LEATHER) return new Units(Commodity.LEATHER, 1);
        return null;
    }

    /** The item a player receives when buying one unit; null if the good cannot be carried away. */
    public static Item itemFor(Commodity c) {
        return switch (c) {
            case GRAIN -> Items.WHEAT;
            case BREAD -> Items.BREAD;
            case MEAT -> Items.COOKED_MUTTON;
            case FISH -> Items.COOKED_COD;
            case VEGETABLES -> Items.CARROT;
            case LOGS -> Items.OAK_LOG;
            case PLANKS -> Items.OAK_PLANKS;
            case STONE -> Items.COBBLESTONE;
            case GLASS -> Items.GLASS;
            case ORE -> Items.RAW_IRON;
            case IRON -> Items.IRON_INGOT;
            case FUEL -> Items.CHARCOAL;
            case TOOLS -> Items.IRON_PICKAXE;
            case WEAPONS -> Items.IRON_SWORD;
            case ARMOR -> Items.IRON_CHESTPLATE;
            case WOOL -> Items.WHITE_WOOL;
            case LEATHER -> Items.LEATHER;
            default -> null; // flour, ale and cloth stay in the settlement for now
        };
    }
}
