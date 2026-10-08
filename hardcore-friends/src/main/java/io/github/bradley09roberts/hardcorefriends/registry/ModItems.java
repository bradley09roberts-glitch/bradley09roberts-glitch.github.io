package io.github.bradley09roberts.hardcorefriends.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.item.BackpackItem;

public final class ModItems {
	public static final ResourceKey<Item> BACKPACK_KEY =
		ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "backpack"));
	public static final Item BACKPACK = Registry.register(BuiltInRegistries.ITEM, BACKPACK_KEY,
		new BackpackItem(new Item.Properties().setId(BACKPACK_KEY).stacksTo(1).fireResistant()));

	private ModItems() {
	}

	public static void init() {
		// Class loading registers the items.
	}
}
