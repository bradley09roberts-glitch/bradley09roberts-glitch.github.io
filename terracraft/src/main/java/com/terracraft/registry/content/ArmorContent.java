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

    /** Shadow armor (Demonite): +15% movement speed set bonus. */
    public static final ArmorPieces SHADOW = set("shadow", "_scalemail", 6, 7, 6,
        StatEffects.builder().add(Stat.MOVE_SPEED, 0.15F).build(), 3750);
    /** Crimson armor (Crimtane): greatly increased life regeneration. */
    public static final ArmorPieces CRIMSON = set("crimson", "_scalemail", 6, 7, 6,
        StatEffects.builder().add(Stat.LIFE_REGEN, 4).build(), 3750);

    /** Jungle armor (Jungle Spores + Stingers): mage set, +max mana and magic crit per piece, -16% mana cost bonus. */
    public static final ArmorPieces JUNGLE = named("jungle", new String[]{"jungle_hat", "jungle_shirt", "jungle_pants"}, new int[]{4, 5, 4},
        new StatEffects[]{
            StatEffects.builder().add(Stat.MAX_MANA, 40).add(Stat.MAGIC_CRIT, 4).build(),
            StatEffects.builder().add(Stat.MAX_MANA, 20).add(Stat.MAGIC_CRIT, 4).build(),
            StatEffects.builder().add(Stat.MAX_MANA, 20).add(Stat.MAGIC_CRIT, 4).build()},
        StatEffects.builder().add(Stat.MANA_COST, -0.16F).build(), TerraRarity.GREEN, 6000);

    /** Molten armor (Hellstone Bars): melee set, 7% melee crit per piece, +17% melee damage set bonus. */
    public static final ArmorPieces MOLTEN = named("molten", new String[]{"molten_helmet", "molten_breastplate", "molten_greaves"}, new int[]{8, 9, 8},
        new StatEffects[]{
            StatEffects.builder().add(Stat.MELEE_CRIT, 7).build(),
            StatEffects.builder().add(Stat.MELEE_CRIT, 7).build(),
            StatEffects.builder().add(Stat.MELEE_CRIT, 7).build()},
        StatEffects.builder().add(Stat.MELEE_DAMAGE, 0.17F).build(), TerraRarity.ORANGE, 30000);

    /** Meteor armor (Meteorite Bars): +7% magic damage per piece; set bonus makes the Space Gun free. */
    public static final ArmorPieces METEOR = named("meteor", new String[]{"meteor_helmet", "meteor_suit", "meteor_leggings"}, new int[]{3, 3, 3},
        new StatEffects[]{
            StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.07F).build(),
            StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.07F).build(),
            StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.07F).build()},
        StatEffects.builder().ability(com.terracraft.player.stats.Ability.FREE_SPACE_GUN).build(), TerraRarity.BLUE, 9000);


    // Hardmode ore armor (stage 5): helmet, chest, leggings; set bonuses after Terraria's melee/ranged/magic mix
    public static final ArmorPieces COBALT = named("cobalt", new String[]{"cobalt_helmet", "cobalt_breastplate", "cobalt_leggings"}, new int[]{11, 8, 7},
        new StatEffects[]{StatEffects.builder().add(Stat.DAMAGE, 0.03F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.03F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.03F).build()}, StatEffects.builder().add(Stat.MELEE_SPEED, 0.15F).add(Stat.AMMO_CONSERVATION, 0.2F).build(), TerraRarity.LIGHT_RED, 30000);
    public static final ArmorPieces PALLADIUM = named("palladium", new String[]{"palladium_helmet", "palladium_breastplate", "palladium_leggings"}, new int[]{14, 10, 8},
        new StatEffects[]{StatEffects.builder().add(Stat.DAMAGE, 0.03F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.03F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.03F).build()}, StatEffects.builder().add(Stat.LIFE_REGEN, 6).build(), TerraRarity.LIGHT_RED, 30000);
    public static final ArmorPieces MYTHRIL = named("mythril", new String[]{"mythril_helmet", "mythril_breastplate", "mythril_leggings"}, new int[]{16, 12, 10},
        new StatEffects[]{StatEffects.builder().add(Stat.CRIT, 3).build(), StatEffects.builder().add(Stat.CRIT, 3).build(), StatEffects.builder().add(Stat.CRIT, 3).build()}, StatEffects.builder().add(Stat.CRIT, 10).build(), TerraRarity.LIGHT_RED, 30000);
    public static final ArmorPieces ORICHALCUM = named("orichalcum", new String[]{"orichalcum_helmet", "orichalcum_breastplate", "orichalcum_leggings"}, new int[]{16, 14, 11},
        new StatEffects[]{StatEffects.builder().add(Stat.CRIT, 3).build(), StatEffects.builder().add(Stat.CRIT, 3).build(), StatEffects.builder().add(Stat.CRIT, 3).build()}, StatEffects.builder().add(Stat.DAMAGE, 0.08F).add(Stat.MOVE_SPEED, 0.1F).build(), TerraRarity.LIGHT_RED, 30000);
    public static final ArmorPieces ADAMANTITE = named("adamantite", new String[]{"adamantite_helmet", "adamantite_breastplate", "adamantite_leggings"}, new int[]{22, 14, 13},
        new StatEffects[]{StatEffects.builder().add(Stat.DAMAGE, 0.04F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.04F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.04F).build()}, StatEffects.builder().add(Stat.MELEE_SPEED, 0.18F).add(Stat.MOVE_SPEED, 0.18F).build(), TerraRarity.LIGHT_RED, 30000);
    public static final ArmorPieces TITANIUM = named("titanium", new String[]{"titanium_helmet", "titanium_breastplate", "titanium_leggings"}, new int[]{23, 16, 12},
        new StatEffects[]{StatEffects.builder().add(Stat.DAMAGE, 0.04F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.04F).build(), StatEffects.builder().add(Stat.DAMAGE, 0.04F).build()}, StatEffects.builder().add(Stat.ENDURANCE, 0.1F).add(Stat.DAMAGE, 0.05F).build(), TerraRarity.LIGHT_RED, 30000);

    private ArmorContent() {}

    /** A set whose pieces have their own Terraria names and per-piece bonuses. */
    private static ArmorPieces named(String set, String[] names, int[] defense, StatEffects[] pieceEffects, StatEffects bonus, TerraRarity rarity,
                                     int value) {
        ArmorSet armorSet = ArmorSet.of(set, bonus);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS};
        List<RegistryObject<TerrariaArmorItem>> pieces = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TerraItemStats stats = TerraItemStats.builder().defense(defense[i]).rarity(rarity).value(value).build();
            EquipmentSlot slot = slots[i];
            StatEffects effects = pieceEffects[i];
            pieces.add(ModItems.register(names[i], TabGroup.TOOLS_ARMOR, p -> new TerrariaArmorItem(p, armorSet, slot, effects),
                p -> TerrariaArmorItem.properties(p, armorSet, slot, stats)));
        }
        ArmorPieces result = new ArmorPieces(armorSet, pieces.get(0), pieces.get(1), pieces.get(2));
        SETS.add(result);
        return result;
    }

    public static void init() {}

    private static ArmorPieces set(String name, int helmet, int chest, int legs, StatEffects bonus, int value) {
        return set(name, name.equals("wood") ? "_breastplate" : "_chainmail", helmet, chest, legs, bonus, value);
    }

    private static ArmorPieces set(String name, String chestSuffix, int helmet, int chest, int legs, StatEffects bonus, int value) {
        ArmorSet set = ArmorSet.of(name, bonus);
        ArmorPieces pieces = new ArmorPieces(set,
            piece(name + "_helmet", set, EquipmentSlot.HEAD, helmet, value),
            piece(name + chestSuffix, set, EquipmentSlot.CHEST, chest, value),
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
