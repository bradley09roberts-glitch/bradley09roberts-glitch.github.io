package com.starforged.tempest;

import com.starforged.Starforged;
import com.starforged.registry.ModTags;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Tempestforged gear sits above Moonsilver. Aetherium is light and fast: it hits harder and mines faster than anything
 * before it, and its armour shrugs off knockback.
 */
public final class TempestMaterials {
    public static final ToolMaterial AETHERIUM = new ToolMaterial(
        ModTags.INCORRECT_FOR_AETHERIUM, 3800, 12.5F, 5.5F, 24, ModTags.AETHERIUM_REPAIR
    );
    public static final ToolMaterial TEMPEST = new ToolMaterial(
        ModTags.INCORRECT_FOR_AETHERIUM, 5200, 13.0F, 7.5F, 28, ModTags.TEMPEST_REPAIR
    );

    public static final ResourceKey<EquipmentAsset> AETHERIUM_ASSET = asset("aetherium");
    public static final ResourceKey<EquipmentAsset> TEMPEST_ASSET = asset("tempest");

    public static final ArmorMaterial AETHERIUM_ARMOR = new ArmorMaterial(
        50, defense(4, 7, 9, 4, 13), 24, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.5F, 0.3F, ModTags.AETHERIUM_REPAIR, AETHERIUM_ASSET
    );
    public static final ArmorMaterial TEMPEST_ARMOR = new ArmorMaterial(
        60, defense(5, 8, 10, 5, 16), 28, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.5F, 0.4F, ModTags.TEMPEST_REPAIR, TEMPEST_ASSET
    );

    private static ResourceKey<EquipmentAsset> asset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Starforged.id(name));
    }

    private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helm, int body) {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.BOOTS, boots);
        map.put(ArmorType.LEGGINGS, legs);
        map.put(ArmorType.CHESTPLATE, chest);
        map.put(ArmorType.HELMET, helm);
        map.put(ArmorType.BODY, body);
        return map;
    }

    private TempestMaterials() {
    }
}
