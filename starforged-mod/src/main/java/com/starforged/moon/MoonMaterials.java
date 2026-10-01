package com.starforged.moon;

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
 * Moonforged gear sits above Sunsteel. Moonsilver is tougher rather than much harder-hitting: its strength is control
 * (gravity, tides and the Lunar Ward), not raw damage.
 */
public final class MoonMaterials {
    public static final ToolMaterial MOONSILVER = new ToolMaterial(
        ModTags.INCORRECT_FOR_MOONSILVER, 3200, 11.0F, 5.0F, 22, ModTags.MOONSILVER_REPAIR
    );
    public static final ToolMaterial TIDAL = new ToolMaterial(
        ModTags.INCORRECT_FOR_MOONSILVER, 4400, 12.0F, 6.5F, 26, ModTags.TIDAL_REPAIR
    );

    public static final ResourceKey<EquipmentAsset> MOONSILVER_ASSET = asset("moonsilver");
    public static final ResourceKey<EquipmentAsset> TIDES_ASSET = asset("tides");

    public static final ArmorMaterial MOONSILVER_ARMOR = new ArmorMaterial(
        44, defense(3, 7, 9, 3, 12), 22, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F, ModTags.MOONSILVER_REPAIR, MOONSILVER_ASSET
    );
    public static final ArmorMaterial TIDES_ARMOR = new ArmorMaterial(
        56, defense(4, 8, 10, 5, 15), 26, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.0F, 0.25F, ModTags.TIDAL_REPAIR, TIDES_ASSET
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

    private MoonMaterials() {
    }
}
