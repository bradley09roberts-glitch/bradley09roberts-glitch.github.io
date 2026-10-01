package com.starforged.moon;

import com.starforged.Starforged;
import com.starforged.item.LoreItem;
import com.starforged.item.StarforgedArmorItem;
import com.starforged.moon.item.CrescentGlaiveItem;
import com.starforged.moon.item.LunarKeyItem;
import com.starforged.moon.item.MoonshotCrossbowItem;
import com.starforged.moon.item.OrreryStaffItem;
import com.starforged.moon.item.PhaseDaggersItem;
import com.starforged.moon.item.StasisBellItem;
import com.starforged.moon.item.TetherHookItem;
import com.starforged.moon.item.TidecallerGlaiveItem;
import com.starforged.registry.ModTags;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items of the Moonforged expansion. */
public final class MoonItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Starforged.MODID);

    // --- Block items ------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> MOONSTONE = block(MoonBlocks.MOONSTONE);
    public static final RegistryObject<Item> REGOLITH = block(MoonBlocks.REGOLITH);
    public static final RegistryObject<Item> UMBRAL_REGOLITH = block(MoonBlocks.UMBRAL_REGOLITH);
    public static final RegistryObject<Item> SILVER_SAND = block(MoonBlocks.SILVER_SAND);
    public static final RegistryObject<Item> MOONSILVER_ORE = block(MoonBlocks.MOONSILVER_ORE);
    public static final RegistryObject<Item> SELENITE_CLUSTER = block(MoonBlocks.SELENITE_CLUSTER);
    public static final RegistryObject<Item> MOONPETAL = block(MoonBlocks.MOONPETAL);
    public static final RegistryObject<Item> TIDAL_CLAM = block(MoonBlocks.TIDAL_CLAM);
    public static final RegistryObject<Item> MOONSILVER_BLOCK = block(MoonBlocks.MOONSILVER_BLOCK);
    public static final RegistryObject<Item> LUNAR_BRICKS = block(MoonBlocks.LUNAR_BRICKS);
    public static final RegistryObject<Item> CRACKED_LUNAR_BRICKS = block(MoonBlocks.CRACKED_LUNAR_BRICKS);
    public static final RegistryObject<Item> CHISELED_LUNAR_BRICKS = block(MoonBlocks.CHISELED_LUNAR_BRICKS);
    public static final RegistryObject<Item> LUNAR_BRICK_STAIRS = block(MoonBlocks.LUNAR_BRICK_STAIRS);
    public static final RegistryObject<Item> LUNAR_BRICK_SLAB = block(MoonBlocks.LUNAR_BRICK_SLAB);
    public static final RegistryObject<Item> MOON_GLASS = block(MoonBlocks.MOON_GLASS);
    public static final RegistryObject<Item> MOON_LANTERN = block(MoonBlocks.MOON_LANTERN);
    public static final RegistryObject<Item> GRAVITY_PLATE = block(MoonBlocks.GRAVITY_PLATE);
    public static final RegistryObject<Item> ORRERY_RING = block(MoonBlocks.ORRERY_RING);
    public static final RegistryObject<Item> LUNAR_MURAL = block(MoonBlocks.LUNAR_MURAL);
    public static final RegistryObject<Item> ORRERY_CONSOLE = block(MoonBlocks.ORRERY_CONSOLE);
    public static final RegistryObject<Item> MOON_SEAL = block(MoonBlocks.MOON_SEAL);
    public static final RegistryObject<Item> MOON_ALTAR = register("moon_altar",
        p -> new BlockItem(MoonBlocks.MOON_ALTAR.get(), p.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));

    // --- Materials --------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> RAW_MOONSILVER = register("raw_moonsilver", Item::new);
    public static final RegistryObject<Item> MOONSILVER_INGOT = register("moonsilver_ingot", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SELENITE_SHARD = register("selenite_shard", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> LUNAR_DUST = register("lunar_dust", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> LUNAR_PEARL = register("lunar_pearl", p -> new LoreItem(p.rarity(Rarity.RARE), 1));
    public static final RegistryObject<Item> MOON_HEART = register("moon_heart",
        p -> new LoreItem(p.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 2));
    public static final RegistryObject<Item> LUNAR_KEY = register("lunar_key",
        p -> new LunarKeyItem(p.stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final RegistryObject<Item> TIDAL_SIGIL = register("tidal_sigil",
        p -> new LoreItem(p.stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 3));

    // --- Moonsilver tools -------------------------------------------------------------------------------------
    public static final RegistryObject<Item> MOONSILVER_SWORD = register("moonsilver_sword",
        p -> new Item(p.sword(MoonMaterials.MOONSILVER, 3.0F, -2.4F)));
    public static final RegistryObject<Item> MOONSILVER_PICKAXE = register("moonsilver_pickaxe",
        p -> new Item(p.pickaxe(MoonMaterials.MOONSILVER, 1.0F, -2.8F)));
    public static final RegistryObject<Item> MOONSILVER_AXE = register("moonsilver_axe",
        p -> new AxeItem(MoonMaterials.MOONSILVER, 5.0F, -3.0F, p));
    public static final RegistryObject<Item> MOONSILVER_SHOVEL = register("moonsilver_shovel",
        p -> new ShovelItem(MoonMaterials.MOONSILVER, 1.5F, -3.0F, p));
    public static final RegistryObject<Item> MOONSILVER_HOE = register("moonsilver_hoe",
        p -> new HoeItem(MoonMaterials.MOONSILVER, -5.0F, 0.0F, p));

    // --- Armor ------------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> MOONSILVER_HELMET = armor("moonsilver_helmet", ArmorType.HELMET);
    public static final RegistryObject<Item> MOONSILVER_CHESTPLATE = armor("moonsilver_chestplate", ArmorType.CHESTPLATE);
    public static final RegistryObject<Item> MOONSILVER_LEGGINGS = armor("moonsilver_leggings", ArmorType.LEGGINGS);
    public static final RegistryObject<Item> MOONSILVER_BOOTS = armor("moonsilver_boots", ArmorType.BOOTS);
    public static final RegistryObject<Item> CROWN_OF_TIDES = register("crown_of_tides",
        p -> new StarforgedArmorItem(p.humanoidArmor(MoonMaterials.TIDES_ARMOR, ArmorType.HELMET).rarity(Rarity.EPIC),
            StarforgedArmorItem.Ability.TIDE_CROWN));

    // --- Legendary weapons & gadgets --------------------------------------------------------------------------
    public static final RegistryObject<Item> CRESCENT_GLAIVE = register("crescent_glaive",
        p -> new CrescentGlaiveItem(p.sword(MoonMaterials.MOONSILVER, 6.0F, -2.8F).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ORRERY_STAFF = register("orrery_staff",
        p -> new OrreryStaffItem(p.stacksTo(1).durability(500).rarity(Rarity.EPIC).repairable(ModTags.MOONSILVER_REPAIR).enchantable(15)));
    public static final RegistryObject<Item> PHASE_DAGGERS = register("phase_daggers",
        p -> new PhaseDaggersItem(p.sword(MoonMaterials.MOONSILVER, 2.0F, -1.6F).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> MOONSHOT_CROSSBOW = register("moonshot_crossbow",
        p -> new MoonshotCrossbowItem(p.stacksTo(1).durability(1400).rarity(Rarity.EPIC).repairable(ModTags.MOONSILVER_REPAIR).enchantable(1)));
    public static final RegistryObject<Item> STASIS_BELL = register("stasis_bell",
        p -> new StasisBellItem(p.stacksTo(1).durability(60).rarity(Rarity.EPIC).repairable(ModTags.MOONSILVER_REPAIR)));
    public static final RegistryObject<Item> TETHER_HOOK = register("tether_hook",
        p -> new TetherHookItem(p.stacksTo(1).durability(800).rarity(Rarity.RARE).repairable(ModTags.MOONSILVER_REPAIR)));
    public static final RegistryObject<Item> TIDECALLER_GLAIVE = register("tidecaller_glaive",
        p -> new TidecallerGlaiveItem(p.sword(MoonMaterials.TIDAL, 9.0F, -3.0F).rarity(Rarity.EPIC)));

    // --- Projectile visuals (not obtainable) ------------------------------------------------------------------
    public static final RegistryObject<Item> MOONSHOT_BOLT = register("moonshot_bolt", Item::new);

    // --- Spawn eggs -------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> REGOLITH_SKIMMER_SPAWN_EGG = egg("regolith_skimmer_spawn_egg", MoonEntities.REGOLITH_SKIMMER);
    public static final RegistryObject<Item> LUNAR_MOTH_SPAWN_EGG = egg("lunar_moth_spawn_egg", MoonEntities.LUNAR_MOTH);
    public static final RegistryObject<Item> SELENITE_SENTINEL_SPAWN_EGG = egg("selenite_sentinel_spawn_egg", MoonEntities.SELENITE_SENTINEL);
    public static final RegistryObject<Item> UMBRAL_LURKER_SPAWN_EGG = egg("umbral_lurker_spawn_egg", MoonEntities.UMBRAL_LURKER);
    public static final RegistryObject<Item> MOONKIT_SPAWN_EGG = egg("moonkit_spawn_egg", MoonEntities.MOONKIT);
    public static final RegistryObject<Item> MOONLEAPER_SPAWN_EGG = egg("moonleaper_spawn_egg", MoonEntities.MOONLEAPER);
    public static final RegistryObject<Item> PALE_MATRIARCH_SPAWN_EGG = egg("pale_matriarch_spawn_egg", MoonEntities.PALE_MATRIARCH);

    private static RegistryObject<Item> armor(String name, ArmorType type) {
        return register(name, p -> new StarforgedArmorItem(p.humanoidArmor(MoonMaterials.MOONSILVER_ARMOR, type),
            StarforgedArmorItem.Ability.MOONSILVER_SET));
    }

    private static RegistryObject<Item> block(RegistryObject<Block> block) {
        String name = block.getId().getPath();
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().setId(ITEMS.key(name)).useBlockDescriptionPrefix()));
    }

    private static RegistryObject<Item> egg(String name, Supplier<? extends EntityType<?>> type) {
        return ITEMS.register(name, () -> new SpawnEggItem(new Item.Properties().setId(ITEMS.key(name)).spawnEgg(type.get())));
    }

    private static RegistryObject<Item> register(String name, Function<Item.Properties, Item> factory) {
        return ITEMS.register(name, () -> factory.apply(new Item.Properties().setId(ITEMS.key(name))));
    }

    private MoonItems() {
    }
}
