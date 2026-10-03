package com.terracraft.item.armor;

import com.terracraft.TerraCraft;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * A Terraria armor piece. Defense lives in the item's {@link TerraItemStats}; pieces can carry their own
 * bonuses and belong to an {@link ArmorSet}. Vanilla armor attributes and durability are not used.
 * The worn look is a 3D model ({@code ArmorModels}) painted with the equipment asset {@code terracraft:<set>}.
 */
public class TerrariaArmorItem extends TerraItem {
    private final ArmorSet set;
    private final EquipmentSlot slot;
    private final StatEffects pieceEffects;

    public TerrariaArmorItem(Properties properties, ArmorSet set, EquipmentSlot slot, StatEffects pieceEffects) {
        super(properties);
        this.set = set;
        this.slot = slot;
        this.pieceEffects = pieceEffects;
    }

    public static Item.Properties properties(Item.Properties properties, ArmorSet set, EquipmentSlot slot, TerraItemStats stats) {
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, TerraCraft.id(set.asset()));
        return properties.stacksTo(1)
            .component(ModDataComponents.STATS, stats)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).setAsset(asset).build());
    }

    public ArmorSet set() {
        return set;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    public StatEffects pieceEffects() {
        return pieceEffects;
    }
}
