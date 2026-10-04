package com.squidgame.registry;

import com.squidgame.SquidGameMod;
import com.squidgame.item.MarbleItem;
import com.squidgame.item.RecruiterCardItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModItems {
    private static final List<Item> TAB_ITEMS = new ArrayList<>();

    public static final Item MARBLE = item("marble", new MarbleItem(new Item.Properties().stacksTo(64)));
    public static final Item RECRUITER_CARD = item("recruiter_card", new RecruiterCardItem(new Item.Properties().stacksTo(1)));
    public static final Item CONTESTANT_SPAWN_EGG = item("contestant_spawn_egg",
            new SpawnEggItem(ModEntities.CONTESTANT, 0x2E7D5B, 0xFFFFFF, new Item.Properties()));
    public static final Item GUARD_SPAWN_EGG = item("guard_spawn_egg",
            new SpawnEggItem(ModEntities.GUARD, 0xE0457B, 0x111111, new Item.Properties()));
    public static final Item DOLL_SPAWN_EGG = item("doll_spawn_egg",
            new SpawnEggItem(ModEntities.DOLL, 0xF28C28, 0xF5E6A8, new Item.Properties()));

    static {
        for (Map.Entry<String, Block> e : ModBlocks.ALL.entrySet()) {
            item(e.getKey(), new BlockItem(e.getValue(), new Item.Properties()));
        }
    }

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            SquidGameMod.id("squidgame"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.squidgame"))
                    .icon(() -> new ItemStack(RECRUITER_CARD))
                    .displayItems((params, output) -> TAB_ITEMS.forEach(output::accept))
                    .build());

    private ModItems() {
    }

    private static Item item(String id, Item item) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SquidGameMod.id(id));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        TAB_ITEMS.add(item);
        return item;
    }

    public static void init() {
    }
}
