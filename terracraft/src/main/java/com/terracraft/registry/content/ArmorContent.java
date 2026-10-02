package com.terracraft.registry.content;

import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.armor.ArmorSet;
import com.terracraft.item.armor.TerrariaArmorItem;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-hardmode ore armor sets. Terraria sets are three pieces (helmet, chest, leggings); the set bonus is
 * granted while all three are worn.
 */
public final class ArmorContent {
    public static final List<ArmorPieces> SETS = new ArrayList<>();

    public static final ArmorPieces WOOD = set("wood", 1, 1, 0, StatEffects.builder().add(Stat.DEFENSE, 1).build(), 0);
    public static final ArmorPieces COPPER = set("copper", 1, 2, 1, StatEffects.builder().add(Stat.DEFENSE, 2).build(), 100);
    public static final ArmorPieces TIN = set("tin", 2, 2, 1, StatEffects.builder().add(Stat.DEFENSE, 2).build(), 150);
    public static final ArmorPieces IRON = set("iron", 2, 3, 2, StatEffects.builder().add(Stat.DEFENSE, 2).build(), 300);
    public static final ArmorPieces LEAD = set("lead", 3, 3, 2, StatEffects.builder().add(Stat.DEFENSE, 3).build(), 450);
    public static final ArmorPieces SILVER = set("silver", 3, 4, 3, StatEffects.builder().add(Stat.DEFENSE, 3).build(), 600);
    public static final ArmorPieces TUNGSTEN = set("tungsten", 4, 5, 3, StatEffects.builder().add(Stat.DEFENSE, 3).build(), 900);
    public static final ArmorPieces GOLD = set("gold", 4, 5, 4, StatEffects.builder().add(Stat.DEFENSE, 3).build(), 1200);
    public static final ArmorPieces PLATINUM = set("platinum", 5, 6, 5, StatEffects.builder().add(Stat.DEFENSE, 4).build(), 1800);

    public record ArmorPieces(ArmorSet set, RegistryObject<TerrariaArmorItem> helmet, RegistryObject<TerrariaArmorItem> chest,
                              RegistryObject<TerrariaArmorItem> legs) {}

    private ArmorContent() {}

    public static void init() {}

    private static ArmorPieces set(String name, int helmet, int chest, int legs, StatEffects bonus, int value) {
        ArmorSet set = ArmorSet.of(name, bonus);
        ArmorPieces pieces = new ArmorPieces(set,
            piece(name + "_helmet", set, EquipmentSlot.HEAD, helmet, value),
            piece(name + (name.equals("wood") ? "_breastplate" : "_chainmail"), set, EquipmentSlot.CHEST, chest, value),
            piece(name + "_greaves", set, EquipmentSlot.LEGS, legs, value));
        SETS.add(pieces);
        return pieces;
    }

    private static RegistryObject<TerrariaArmorItem> piece(String name, ArmorSet set, EquipmentSlot slot, int defense, int value) {
        TerraItemStats stats = TerraItemStats.builder().defense(defense).rarity(TerraRarity.WHITE).value(value).build();
        return ModItems.register(name, TabGroup.TOOLS_ARMOR, p -> new TerrariaArmorItem(p, set, slot, StatEffects.NONE),
            p -> TerrariaArmorItem.properties(p, set, slot, stats));
    }
}
