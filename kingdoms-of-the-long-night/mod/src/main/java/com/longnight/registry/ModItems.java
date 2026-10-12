package com.longnight.registry;

import com.longnight.LongNight;
import com.longnight.item.CharterItem;
import com.longnight.item.LedgerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LongNight.MODID);

    // currency: 10 pennies = 1 mark, 10 marks = 1 crown
    public static final DeferredItem<Item> COPPER_PENNY = ITEMS.registerSimpleItem("copper_penny", new Item.Properties());
    public static final DeferredItem<Item> SILVER_MARK = ITEMS.registerSimpleItem("silver_mark", new Item.Properties());
    public static final DeferredItem<Item> GOLD_CROWN = ITEMS.registerSimpleItem("gold_crown", new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<CharterItem> FOUNDING_CHARTER = ITEMS.register("founding_charter",
            () -> new CharterItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<LedgerItem> SETTLEMENT_LEDGER = ITEMS.register("settlement_ledger",
            () -> new LedgerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LAND_DEED = ITEMS.registerSimpleItem("land_deed", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> WEDDING_TOKEN = ITEMS.registerSimpleItem("wedding_token", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> TRADE_SEAL = ITEMS.registerSimpleItem("trade_seal", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> LETTER = ITEMS.registerSimpleItem("letter", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> WARD_CHALK = ITEMS.registerSimpleItem("ward_chalk", new Item.Properties());
    public static final DeferredItem<Item> EMBER_RELIC = ITEMS.registerSimpleItem("ember_relic",
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> NIGHT_SHARD = ITEMS.registerSimpleItem("night_shard",
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> STAR_CHART = ITEMS.registerSimpleItem("star_chart", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BLIGHTED_WHEAT = ITEMS.registerSimpleItem("blighted_wheat", new Item.Properties());
}
