package com.starforged.sun;

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
 * Sunforged gear sits above Starmetal: Sunsteel beats netherite, and the Sun Warden's relics top everything.
 */
public final class SunMaterials {
    public static final ToolMaterial SUNSTEEL = new ToolMaterial(
        ModTags.INCORRECT_FOR_SUNSTEEL, 2600, 10.5F, 4.5F, 20, ModTags.SUNSTEEL_REPAIR
    );
    public static final ToolMaterial SOLAR = new ToolMaterial(
        ModTags.INCORRECT_FOR_SUNSTEEL, 3800, 12.0F, 6.0F, 24, ModTags.SOLAR_REPAIR
    );

    public static final ResourceKey<EquipmentAsset> SUNSTEEL_ASSET = asset("sunsteel");
    public static final ResourceKey<EquipmentAsset> PHOENIX_ASSET = asset("phoenix");
    public static final ResourceKey<EquipmentAsset> MAGMA_ASSET = asset("magma");
    public static final ResourceKey<EquipmentAsset> SOLAR_ASSET = asset("solar");

    public static final ArmorMaterial SUNSTEEL_ARMOR = new ArmorMaterial(
        40, defense(3, 6, 8, 3, 11), 20, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.1F, ModTags.SUNSTEEL_REPAIR, SUNSTEEL_ASSET
    );
    public static final ArmorMaterial PHOENIX_ARMOR = new ArmorMaterial(
        38, defense(3, 6, 8, 3, 11), 22, SoundEvents.ARMOR_EQUIP_ELYTRA, 3.0F, 0.0F, ModTags.SUNSTEEL_REPAIR, PHOENIX_ASSET
    );
    public static final ArmorMaterial MAGMA_ARMOR = new ArmorMaterial(
        38, defense(4, 6, 8, 3, 11), 20, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, ModTags.SUNSTEEL_REPAIR, MAGMA_ASSET
    );
    public static final ArmorMaterial SOLAR_ARMOR = new ArmorMaterial(
        50, defense(4, 7, 9, 5, 14), 24, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.5F, 0.2F, ModTags.SOLAR_REPAIR, SOLAR_ASSET
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

    private SunMaterials() {
    }
}
