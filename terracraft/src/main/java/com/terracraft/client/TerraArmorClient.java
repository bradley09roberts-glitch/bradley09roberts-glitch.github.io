package com.terracraft.client;

import com.terracraft.client.model.ArmorModels;
import com.terracraft.item.armor.TerrariaArmorItem;
import net.minecraft.client.model.Model;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Swaps the vanilla armor model for TerraCraft's sculpted Terraria-style armor pieces. */
final class TerraArmorClient {
    private TerraArmorClient() {}

    static void register(RegisterClientExtensionsEvent event) {
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof TerrariaArmorItem armor) {
                event.registerItem(new IClientItemExtensions() {
                    @Override
                    public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) {
                        var model = ArmorModels.get(armor.set().asset(), armor.slot());
                        return model != null ? model : original;
                    }
                }, armor);
            }
        }
    }
}
