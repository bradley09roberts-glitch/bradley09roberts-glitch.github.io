package com.grandline.activity;

import com.grandline.registry.ModItems;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Items of the sea activities, registered into the shared item register (the asset pipeline reads the ids from this
 * file: {@code ITEMS.register("…")}, {@code fish("…")} and {@code dish("…")}).
 */
public final class ActivityItems {
    public static final List<DeferredItem<Item>> ALL = new ArrayList<>();
    public static final List<DeferredItem<Item>> FISH = new ArrayList<>();
    public static final List<DeferredItem<Item>> DISHES = new ArrayList<>();

    // ---------------------------------------------------------------- treasure
    public static final DeferredItem<Item> TREASURE_MAP = add(ModItems.ITEMS.register("treasure_map",
            () -> new TreasureMapItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
    public static final DeferredItem<Item> TORN_MAP = add(ModItems.ITEMS.register("torn_map", () -> new TornMapItem(new Item.Properties().stacksTo(1))));

    // ---------------------------------------------------------------- sea catches (raw; see SeaFishing for where they bite)
    /** The Blues: small fry. */
    public static final DeferredItem<Item> BLUE_SARDINE = fish("blue_sardine", 2, 0.2f);
    public static final DeferredItem<Item> HARBOR_BREAM = fish("harbor_bream", 3, 0.3f);
    /** Paradise: exotic fish. */
    public static final DeferredItem<Item> RAINBOW_TUNA = fish("rainbow_tuna", 4, 0.4f);
    public static final DeferredItem<Item> NEEDLENOSE_GAR = fish("needlenose_gar", 3, 0.4f);
    /** Calm Belt and New World: giants. */
    public static final DeferredItem<Item> BEHEMOTH_CARP = fish("behemoth_carp", 6, 0.5f);
    public static final DeferredItem<Item> CRIMSON_MARLIN = fish("crimson_marlin", 6, 0.6f);
    public static final DeferredItem<Item> THUNDER_EEL = fish("thunder_eel", 4, 0.5f);
    /** The deep sea (Fish-Man Island's dimension). */
    public static final DeferredItem<Item> ABYSS_LANTERNFISH = fish("abyss_lanternfish", 3, 0.4f);
    public static final DeferredItem<Item> GHOST_RAY = fish("ghost_ray", 4, 0.4f);
    /** The White Sea above the clouds. */
    public static final DeferredItem<Item> CLOUD_SKIPPER = fish("cloud_skipper", 3, 0.3f);
    /** Rare anywhere on the Grand Line and in the Calm Belt: a hatchling Sea King. */
    public static final DeferredItem<Item> SEA_KING_FRY = fish("sea_king_fry", 8, 0.8f);

    // ---------------------------------------------------------------- dishes (buffs in Meals)
    public static final DeferredItem<Item> GRILLED_FISH = dish("grilled_fish", 6, 0.7f, false);
    public static final DeferredItem<Item> SEA_KING_ROAST = dish("sea_king_roast", 14, 1.1f, false);
    public static final DeferredItem<Item> FISHERMAN_STEW = dish("fisherman_stew", 10, 0.9f, true);
    public static final DeferredItem<Item> ABYSS_CHOWDER = dish("abyss_chowder", 10, 0.9f, true);
    /** Signature dishes. */
    public static final DeferredItem<Item> SALTFIRE_CURRY = dish("saltfire_curry", 12, 1.0f, true);
    public static final DeferredItem<Item> TIDEWATER_SASHIMI = dish("tidewater_sashimi", 8, 0.8f, false);
    public static final DeferredItem<Item> SEA_KING_HOTPOT = dish("sea_king_hotpot", 16, 1.1f, true);
    public static final DeferredItem<Item> PIRATES_FEAST = dish("pirates_feast", 20, 1.2f, false);

    private static DeferredItem<Item> add(DeferredItem<Item> it) {
        ALL.add(it);
        return it;
    }

    private static DeferredItem<Item> fish(String id, int nutrition, float saturation) {
        DeferredItem<Item> it = add(ModItems.ITEMS.register(id, () -> new SeaFishItem(new Item.Properties()
                .rarity(id.equals("sea_king_fry") ? Rarity.RARE : Rarity.COMMON)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()))));
        FISH.add(it);
        return it;
    }

    private static DeferredItem<Item> dish(String id, int nutrition, float saturation, boolean bowl) {
        DeferredItem<Item> it = add(ModItems.ITEMS.register(id, () -> {
            FoodProperties.Builder f = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible();
            if (bowl) f.usingConvertsTo(Items.BOWL);
            return new DishItem(new Item.Properties().stacksTo(bowl ? 16 : 64).food(f.build()));
        }));
        DISHES.add(it);
        return it;
    }

    /** Loads the class (and so registers the items) during mod construction. */
    static void init() {}

    private ActivityItems() {}
}
