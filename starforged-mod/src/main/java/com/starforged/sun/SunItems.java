package com.starforged.sun;

import com.starforged.Starforged;
import com.starforged.item.LoreItem;
import com.starforged.item.StarforgedArmorItem;
import com.starforged.registry.ModTags;
import com.starforged.sun.item.CinderChakramItem;
import com.starforged.sun.item.FlareGreatswordItem;
import com.starforged.sun.item.HeliosScepterItem;
import com.starforged.sun.item.PhoenixBowItem;
import com.starforged.sun.item.PhoenixEggItem;
import com.starforged.sun.item.SolarKeyItem;
import com.starforged.sun.item.SolarLanceItem;
import com.starforged.sun.item.SunburstFlaskItem;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
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

/** Items of the Sunforged expansion. */
public final class SunItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Starforged.MODID);

    // --- Block items ------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> SCORCHSTONE = block(SunBlocks.SCORCHSTONE);
    public static final RegistryObject<Item> SUNSTONE_ORE = block(SunBlocks.SUNSTONE_ORE);
    public static final RegistryObject<Item> SUNSAND = block(SunBlocks.SUNSAND);
    public static final RegistryObject<Item> ASHEN_SOIL = block(SunBlocks.ASHEN_SOIL);
    public static final RegistryObject<Item> EMBER_CRYSTAL_CLUSTER = block(SunBlocks.EMBER_CRYSTAL_CLUSTER);
    public static final RegistryObject<Item> SUNBLOOM = block(SunBlocks.SUNBLOOM);
    public static final RegistryObject<Item> SUNSTEEL_BLOCK = block(SunBlocks.SUNSTEEL_BLOCK);
    public static final RegistryObject<Item> SUNBAKED_BRICKS = block(SunBlocks.SUNBAKED_BRICKS);
    public static final RegistryObject<Item> CRACKED_SUNBAKED_BRICKS = block(SunBlocks.CRACKED_SUNBAKED_BRICKS);
    public static final RegistryObject<Item> CHISELED_SUNBAKED_BRICKS = block(SunBlocks.CHISELED_SUNBAKED_BRICKS);
    public static final RegistryObject<Item> SUNBAKED_BRICK_STAIRS = block(SunBlocks.SUNBAKED_BRICK_STAIRS);
    public static final RegistryObject<Item> SUNBAKED_BRICK_SLAB = block(SunBlocks.SUNBAKED_BRICK_SLAB);
    public static final RegistryObject<Item> SOLAR_GLASS = block(SunBlocks.SOLAR_GLASS);
    public static final RegistryObject<Item> SUN_LANTERN = block(SunBlocks.SUN_LANTERN);
    public static final RegistryObject<Item> SOLAR_BRAZIER = block(SunBlocks.SOLAR_BRAZIER);
    public static final RegistryObject<Item> SUNFIRE_VENT = block(SunBlocks.SUNFIRE_VENT);
    public static final RegistryObject<Item> SUN_SEAL = block(SunBlocks.SUN_SEAL);
    public static final RegistryObject<Item> SUN_ALTAR = register("sun_altar",
        p -> new BlockItem(SunBlocks.SUN_ALTAR.get(), p.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));

    // --- Materials --------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> RAW_SUNSTEEL = register("raw_sunsteel", p -> new Item(p.fireResistant()));
    public static final RegistryObject<Item> SUNSTEEL_INGOT = register("sunsteel_ingot", p -> new Item(p.fireResistant().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> EMBER_SHARD = register("ember_shard", p -> new LoreItem(p.fireResistant(), 1));
    public static final RegistryObject<Item> SOLAR_ESSENCE = register("solar_essence", p -> new LoreItem(p.fireResistant().rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> PHOENIX_FEATHER = register("phoenix_feather", p -> new LoreItem(p.fireResistant().rarity(Rarity.RARE), 1));
    public static final RegistryObject<Item> SUN_HEART = register("sun_heart",
        p -> new LoreItem(p.fireResistant().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 2));
    public static final RegistryObject<Item> SOLAR_KEY = register("solar_key",
        p -> new SolarKeyItem(p.stacksTo(1).fireResistant().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final RegistryObject<Item> SUNFIRE_SIGIL = register("sunfire_sigil",
        p -> new LoreItem(p.stacksTo(1).fireResistant().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 3));
    public static final RegistryObject<Item> PHOENIX_EGG = register("phoenix_egg", p -> new PhoenixEggItem(p.stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    // --- Sunsteel tools ---------------------------------------------------------------------------------------
    public static final RegistryObject<Item> SUNSTEEL_SWORD = register("sunsteel_sword",
        p -> new Item(p.sword(SunMaterials.SUNSTEEL, 3.0F, -2.4F).fireResistant()));
    public static final RegistryObject<Item> SUNSTEEL_PICKAXE = register("sunsteel_pickaxe",
        p -> new Item(p.pickaxe(SunMaterials.SUNSTEEL, 1.0F, -2.8F).fireResistant()));
    public static final RegistryObject<Item> SUNSTEEL_AXE = register("sunsteel_axe",
        p -> new AxeItem(SunMaterials.SUNSTEEL, 5.0F, -3.0F, p.fireResistant()));
    public static final RegistryObject<Item> SUNSTEEL_SHOVEL = register("sunsteel_shovel",
        p -> new ShovelItem(SunMaterials.SUNSTEEL, 1.5F, -3.0F, p.fireResistant()));
    public static final RegistryObject<Item> SUNSTEEL_HOE = register("sunsteel_hoe",
        p -> new HoeItem(SunMaterials.SUNSTEEL, -4.0F, 0.0F, p.fireResistant()));

    // --- Armor ------------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> SUNSTEEL_HELMET = armor("sunsteel_helmet", ArmorType.HELMET);
    public static final RegistryObject<Item> SUNSTEEL_CHESTPLATE = armor("sunsteel_chestplate", ArmorType.CHESTPLATE);
    public static final RegistryObject<Item> SUNSTEEL_LEGGINGS = armor("sunsteel_leggings", ArmorType.LEGGINGS);
    public static final RegistryObject<Item> SUNSTEEL_BOOTS = armor("sunsteel_boots", ArmorType.BOOTS);
    public static final RegistryObject<Item> PHOENIX_MANTLE = register("phoenix_mantle",
        p -> new StarforgedArmorItem(p.humanoidArmor(SunMaterials.PHOENIX_ARMOR, ArmorType.CHESTPLATE).rarity(Rarity.EPIC).fireResistant()
            .component(DataComponents.GLIDER, Unit.INSTANCE), StarforgedArmorItem.Ability.PHOENIX_MANTLE));
    public static final RegistryObject<Item> MAGMA_TREADS = register("magma_treads",
        p -> new StarforgedArmorItem(p.humanoidArmor(SunMaterials.MAGMA_ARMOR, ArmorType.BOOTS).rarity(Rarity.RARE).fireResistant(),
            StarforgedArmorItem.Ability.MAGMA_TREADS));
    public static final RegistryObject<Item> SOLAR_CROWN = register("solar_crown",
        p -> new StarforgedArmorItem(p.humanoidArmor(SunMaterials.SOLAR_ARMOR, ArmorType.HELMET).rarity(Rarity.EPIC).fireResistant(),
            StarforgedArmorItem.Ability.SOLAR_CROWN));

    // --- Legendary weapons & gadgets --------------------------------------------------------------------------
    public static final RegistryObject<Item> SOLAR_LANCE = register("solar_lance",
        p -> new SolarLanceItem(p.sword(SunMaterials.SUNSTEEL, 5.0F, -2.9F).rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> FLARE_GREATSWORD = register("flare_greatsword",
        p -> new FlareGreatswordItem(p.sword(SunMaterials.SOLAR, 9.0F, -3.0F).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> PHOENIX_BOW = register("phoenix_bow",
        p -> new PhoenixBowItem(p.durability(1200).enchantable(1).rarity(Rarity.EPIC).fireResistant().repairable(ModTags.SUNSTEEL_REPAIR)));
    public static final RegistryObject<Item> HELIOS_SCEPTER = register("helios_scepter",
        p -> new HeliosScepterItem(p.stacksTo(1).durability(300).rarity(Rarity.EPIC).fireResistant().repairable(ModTags.SUNSTEEL_REPAIR).enchantable(15)));
    public static final RegistryObject<Item> CINDER_CHAKRAM = register("cinder_chakram",
        p -> new CinderChakramItem(p.stacksTo(1).durability(700).rarity(Rarity.RARE).fireResistant().repairable(ModTags.SUNSTEEL_REPAIR)));
    public static final RegistryObject<Item> SUNBURST_FLASK = register("sunburst_flask", p -> new SunburstFlaskItem(p.stacksTo(16).rarity(Rarity.UNCOMMON)));

    // --- Projectile visuals (not obtainable) ------------------------------------------------------------------
    public static final RegistryObject<Item> SOLAR_FLARE = register("solar_flare", Item::new);

    // --- Spawn eggs -------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> CINDER_IMP_SPAWN_EGG = egg("cinder_imp_spawn_egg", SunEntities.CINDER_IMP);
    public static final RegistryObject<Item> MAGMA_CRAWLER_SPAWN_EGG = egg("magma_crawler_spawn_egg", SunEntities.MAGMA_CRAWLER);
    public static final RegistryObject<Item> EMBER_HOUND_SPAWN_EGG = egg("ember_hound_spawn_egg", SunEntities.EMBER_HOUND);
    public static final RegistryObject<Item> ASHEN_KNIGHT_SPAWN_EGG = egg("ashen_knight_spawn_egg", SunEntities.ASHEN_KNIGHT);
    public static final RegistryObject<Item> SOLAR_PHOENIX_SPAWN_EGG = egg("solar_phoenix_spawn_egg", SunEntities.SOLAR_PHOENIX);
    public static final RegistryObject<Item> SUN_WARDEN_SPAWN_EGG = egg("sun_warden_spawn_egg", SunEntities.SUN_WARDEN);

    private static RegistryObject<Item> armor(String name, ArmorType type) {
        return register(name, p -> new StarforgedArmorItem(p.humanoidArmor(SunMaterials.SUNSTEEL_ARMOR, type).fireResistant(),
            StarforgedArmorItem.Ability.SUNSTEEL_SET));
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

    private SunItems() {
    }
}
