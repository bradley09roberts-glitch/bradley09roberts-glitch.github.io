package com.grandline.career;

import com.grandline.GrandLine;
import com.grandline.registry.ModCreativeTabs;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * Registration for careers: the Marine coat (a light chest piece worn over the shoulders, its own armour material and
 * layer texture) and the Warlord's letter of marque (a keepsake naming its holder). The items go into the shared item
 * register; the asset pipeline reads their ids from this file (tools/assetgen/source.py).
 */
public final class CareerRegistry {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, GrandLine.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MARINE_COAT_MATERIAL = MATERIALS.register("marine_coat", () -> {
        EnumMap<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) def.put(t, t == ArmorItem.Type.CHESTPLATE ? 3 : 1);
        return new ArmorMaterial(def, 12, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.WHITE_WOOL),
                List.of(new ArmorMaterial.Layer(GrandLine.id("marine_coat"))), 0f, 0f);
    });

    /** The white "Justice" coat of the Marines: sold by Marine quartermasters, issued on enlistment. */
    public static final DeferredItem<Item> MARINE_COAT = ModItems.ITEMS.register("marine_coat",
            () -> new ArmorItem(MARINE_COAT_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)
                    .durability(ArmorItem.Type.CHESTPLATE.getDurability(18))));

    /** The World Government's letter of marque, given to a pirate who accepts the Warlord charter. */
    public static final DeferredItem<Item> LETTER_OF_MARQUE = ModItems.ITEMS.register("letter_of_marque",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
                    CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
                    if (cd == null) return;
                    tip.add(Component.translatable("item.grandline.letter_of_marque.holder", cd.copyTag().getString("holder")).withStyle(ChatFormatting.GRAY));
                }
            });

    public static final List<DeferredItem<Item>> ITEMS = List.of(MARINE_COAT, LETTER_OF_MARQUE);

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        modBus.addListener(CareerRegistry::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        for (var it : ITEMS) e.accept(it.get());
    }

    private CareerRegistry() {}
}
