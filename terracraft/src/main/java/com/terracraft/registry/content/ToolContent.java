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

    public static final RegistryObject<TerrariaToolItem> MOLTEN_PICKAXE = ModItems.register("molten_pickaxe", TabGroup.TOOLS_ARMOR, TerrariaToolItem::new,
        p -> TerrariaToolItem.properties(p, TerraItemStats.builder().melee(12).useTime(18).knockback(2.0F).pickaxe(100)
            .rarity(TerraRarity.ORANGE).value(54000).build(), 9.5F));
    /** Molten Hamaxe: 150% axe and 70% hammer (still short of the Pwnhammer's 80% needed for altars). */
    public static final RegistryObject<TerrariaToolItem> MOLTEN_HAMAXE = ModItems.register("molten_hamaxe", TabGroup.TOOLS_ARMOR, TerrariaToolItem::new,
        p -> TerrariaToolItem.properties(p, TerraItemStats.builder().melee(20).useTime(25).knockback(7.0F).axe(150).hammer(70)
            .rarity(TerraRarity.ORANGE).value(54000).build(), 9.5F));
    /** Pwnhammer (Wall of Flesh): 80% hammer power breaks Demon and Crimson Altars in Hardmode. */
    public static final RegistryObject<TerrariaToolItem> PWNHAMMER = ModItems.register("pwnhammer", TabGroup.TOOLS_ARMOR, TerrariaToolItem::new,
        p -> TerrariaToolItem.properties(p, TerraItemStats.builder().melee(26).useTime(27).knockback(7.5F).hammer(80)
            .rarity(TerraRarity.LIGHT_RED).value(78000).build(), 9.0F));


    // Hardmode pickaxes (stage 5): mine the next ore tier
    public static final RegistryObject<TerrariaToolItem> COBALT_PICKAXE = hardmodePickaxe("cobalt_pickaxe", 110, 15, TerraRarity.LIGHT_RED, 13500);
    public static final RegistryObject<TerrariaToolItem> PALLADIUM_PICKAXE = hardmodePickaxe("palladium_pickaxe", 130, 18, TerraRarity.LIGHT_RED, 18400);
    public static final RegistryObject<TerrariaToolItem> MYTHRIL_PICKAXE = hardmodePickaxe("mythril_pickaxe", 150, 21, TerraRarity.LIGHT_RED, 22000);
    public static final RegistryObject<TerrariaToolItem> ORICHALCUM_PICKAXE = hardmodePickaxe("orichalcum_pickaxe", 165, 23, TerraRarity.LIGHT_RED, 26000);
    public static final RegistryObject<TerrariaToolItem> ADAMANTITE_PICKAXE = hardmodePickaxe("adamantite_pickaxe", 180, 25, TerraRarity.LIGHT_RED, 29000);
    public static final RegistryObject<TerrariaToolItem> TITANIUM_PICKAXE = hardmodePickaxe("titanium_pickaxe", 190, 27, TerraRarity.LIGHT_RED, 32000);

    private ToolContent() {}

    private static RegistryObject<TerrariaToolItem> hardmodePickaxe(String name, int power, int damage, TerraRarity rarity, int value) {
        return ModItems.register(name, TabGroup.TOOLS_ARMOR, TerrariaToolItem::new,
            p -> TerrariaToolItem.properties(p, TerraItemStats.builder().melee(damage).useTime(16).knockback(3.0F).pickaxe(power)
                .rarity(rarity).value(value).build(), 10.0F + power / 50.0F));
    }

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
