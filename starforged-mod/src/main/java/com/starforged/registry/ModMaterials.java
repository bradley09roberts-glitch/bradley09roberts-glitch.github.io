package com.starforged.registry;

import com.starforged.Starforged;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class ModMaterials {
    /** Forged from fallen stars: a hair above diamond, below netherite in toughness but faster and more enchantable. */
    public static final ToolMaterial STARMETAL = new ToolMaterial(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1850, 9.5F, 3.5F, 18, ModTags.STARMETAL_REPAIR
    );

    /** Tier of the Eclipse Sovereign's relics. */
    public static final ToolMaterial ECLIPSE = new ToolMaterial(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3200, 11.0F, 5.0F, 22, ModTags.ECLIPSE_REPAIR
    );

    public static final ResourceKey<EquipmentAsset> STARMETAL_ASSET = asset("starmetal");
    public static final ResourceKey<EquipmentAsset> COMET_ASSET = asset("comet");
    public static final ResourceKey<EquipmentAsset> NEBULA_ASSET = asset("nebula");
    public static final ResourceKey<EquipmentAsset> ECLIPSE_ASSET = asset("eclipse");

    public static final ArmorMaterial STARMETAL_ARMOR = new ArmorMaterial(
        35, defense(3, 6, 8, 3, 11), 18, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5F, 0.05F, ModTags.STARMETAL_REPAIR, STARMETAL_ASSET
    );
    public static final ArmorMaterial COMET_ARMOR = new ArmorMaterial(
        30, defense(3, 6, 8, 3, 11), 20, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.0F, 0.0F, ModTags.STARMETAL_REPAIR, COMET_ASSET
    );
    public static final ArmorMaterial NEBULA_ARMOR = new ArmorMaterial(
        32, defense(3, 6, 7, 3, 11), 22, SoundEvents.ARMOR_EQUIP_ELYTRA, 2.0F, 0.0F, ModTags.STARMETAL_REPAIR, NEBULA_ASSET
    );
    public static final ArmorMaterial ECLIPSE_ARMOR = new ArmorMaterial(
        45, defense(4, 7, 9, 4, 13), 22, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.15F, ModTags.ECLIPSE_REPAIR, ECLIPSE_ASSET
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

    private ModMaterials() {
    }
}
