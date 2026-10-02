package com.terracraft.registry.content;

import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.tool.TerrariaToolItem;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraftforge.registries.RegistryObject;

/** Pickaxes, axes and hammers with Terraria powers. */
public final class ToolContent {
    public static final RegistryObject<TerrariaToolItem> COPPER_PICKAXE = pickaxe("copper_pickaxe", 35, 4, 4.0F, 50);
    public static final RegistryObject<TerrariaToolItem> TIN_PICKAXE = pickaxe("tin_pickaxe", 35, 5, 4.3F, 75);
    public static final RegistryObject<TerrariaToolItem> IRON_PICKAXE = pickaxe("iron_pickaxe", 40, 5, 5.0F, 200);
    public static final RegistryObject<TerrariaToolItem> LEAD_PICKAXE = pickaxe("lead_pickaxe", 43, 6, 5.3F, 300);
    public static final RegistryObject<TerrariaToolItem> SILVER_PICKAXE = pickaxe("silver_pickaxe", 45, 6, 5.8F, 400);
    public static final RegistryObject<TerrariaToolItem> TUNGSTEN_PICKAXE = pickaxe("tungsten_pickaxe", 50, 7, 6.2F, 600);
    public static final RegistryObject<TerrariaToolItem> GOLD_PICKAXE = pickaxe("gold_pickaxe", 55, 6, 6.8F, 800);
    public static final RegistryObject<TerrariaToolItem> PLATINUM_PICKAXE = pickaxe("platinum_pickaxe", 59, 7, 7.2F, 1200);

    public static final RegistryObject<TerrariaToolItem> COPPER_AXE = axe("copper_axe", 35, 3, 4.0F, 40);
    public static final RegistryObject<TerrariaToolItem> TIN_AXE = axe("tin_axe", 40, 4, 4.3F, 60);
    public static final RegistryObject<TerrariaToolItem> IRON_AXE = axe("iron_axe", 45, 5, 5.0F, 160);
    public static final RegistryObject<TerrariaToolItem> LEAD_AXE = axe("lead_axe", 50, 5, 5.3F, 240);
    public static final RegistryObject<TerrariaToolItem> SILVER_AXE = axe("silver_axe", 55, 6, 5.8F, 320);
    public static final RegistryObject<TerrariaToolItem> TUNGSTEN_AXE = axe("tungsten_axe", 60, 6, 6.2F, 480);
    public static final RegistryObject<TerrariaToolItem> GOLD_AXE = axe("gold_axe", 65, 6, 6.8F, 640);
    public static final RegistryObject<TerrariaToolItem> PLATINUM_AXE = axe("platinum_axe", 70, 7, 7.2F, 960);

    public static final RegistryObject<TerrariaToolItem> COPPER_HAMMER = hammer("copper_hammer", 35, 4, 4.0F, 40);
    public static final RegistryObject<TerrariaToolItem> IRON_HAMMER = hammer("iron_hammer", 45, 7, 5.0F, 160);

    public static final RegistryObject<TerrariaToolItem> NIGHTMARE_PICKAXE = pickaxe("nightmare_pickaxe", 65, 9, 7.8F, 3600);
    public static final RegistryObject<TerrariaToolItem> DEATHBRINGER_PICKAXE = pickaxe("deathbringer_pickaxe", 70, 12, 8.0F, 3600);
    public static final RegistryObject<TerrariaToolItem> WAR_AXE_OF_THE_NIGHT = axe("war_axe_of_the_night", 100, 21, 7.8F, 2700);
    public static final RegistryObject<TerrariaToolItem> BLOOD_LUST_CLUSTER = axe("blood_lust_cluster", 100, 24, 8.0F, 2700);
    public static final RegistryObject<TerrariaToolItem> THE_BREAKER = hammer("the_breaker", 70, 24, 7.0F, 2700);
    public static final RegistryObject<TerrariaToolItem> FLESH_GRINDER = hammer("flesh_grinder", 70, 26, 7.2F, 2700);

    private ToolContent() {}

    public static void init() {}

    private static RegistryObject<TerrariaToolItem> pickaxe(String name, int power, int damage, float speed, int value) {
        TerraItemStats stats = TerraItemStats.builder().melee(damage).useTime(20).knockback(2.0F).pickaxe(power).rarity(TerraRarity.WHITE).value(value).build();
        return ModItems.register(name, TabGroup.TOOLS_ARMOR, TerrariaToolItem::new, p -> TerrariaToolItem.properties(p, stats, speed));
    }

    private static RegistryObject<TerrariaToolItem> axe(String name, int power, int damage, float speed, int value) {
        TerraItemStats stats = TerraItemStats.builder().melee(damage).useTime(25).knockback(4.5F).axe(power).rarity(TerraRarity.WHITE).value(value).build();
        return ModItems.register(name, TabGroup.TOOLS_ARMOR, TerrariaToolItem::new, p -> TerrariaToolItem.properties(p, stats, speed));
    }

    private static RegistryObject<TerrariaToolItem> hammer(String name, int power, int damage, float speed, int value) {
        TerraItemStats stats = TerraItemStats.builder().melee(damage).useTime(30).knockback(5.5F).hammer(power).rarity(TerraRarity.WHITE).value(value).build();
        return ModItems.register(name, TabGroup.TOOLS_ARMOR, TerrariaToolItem::new, p -> TerrariaToolItem.properties(p, stats, speed));
    }
}
