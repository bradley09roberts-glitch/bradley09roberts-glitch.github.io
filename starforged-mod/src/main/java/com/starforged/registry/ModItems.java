package com.starforged.registry;

import com.starforged.Starforged;
import com.starforged.item.AstralCompassItem;
import com.starforged.item.AstralEggItem;
import com.starforged.item.ConstellationBowItem;
import com.starforged.item.EclipseBladeItem;
import com.starforged.item.EclipseSigilItem;
import com.starforged.item.GravityGauntletItem;
import com.starforged.item.LoreItem;
import com.starforged.item.MeteorHammerItem;
import com.starforged.item.RiftPearlItem;
import com.starforged.item.SingularityGrenadeItem;
import com.starforged.item.StarcallerStaffItem;
import com.starforged.item.StarforgedArmorItem;
import com.starforged.item.VoidScytheItem;
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

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Starforged.MODID);

    // --- Block items ------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> METEORITE_ROCK = block(ModBlocks.METEORITE_ROCK);
    public static final RegistryObject<Item> STARMETAL_ORE = block(ModBlocks.STARMETAL_ORE);
    public static final RegistryObject<Item> ASTRAL_CRYSTAL_CLUSTER = block(ModBlocks.ASTRAL_CRYSTAL_CLUSTER);
    public static final RegistryObject<Item> STARMETAL_BLOCK = block(ModBlocks.STARMETAL_BLOCK);
    public static final RegistryObject<Item> ASTRAL_BRICKS = block(ModBlocks.ASTRAL_BRICKS);
    public static final RegistryObject<Item> CRACKED_ASTRAL_BRICKS = block(ModBlocks.CRACKED_ASTRAL_BRICKS);
    public static final RegistryObject<Item> CHISELED_ASTRAL_BRICKS = block(ModBlocks.CHISELED_ASTRAL_BRICKS);
    public static final RegistryObject<Item> ASTRAL_BRICK_STAIRS = block(ModBlocks.ASTRAL_BRICK_STAIRS);
    public static final RegistryObject<Item> ASTRAL_BRICK_SLAB = block(ModBlocks.ASTRAL_BRICK_SLAB);
    public static final RegistryObject<Item> STARGLASS = block(ModBlocks.STARGLASS);
    public static final RegistryObject<Item> STAR_LANTERN = block(ModBlocks.STAR_LANTERN);
    public static final RegistryObject<Item> CELESTIAL_ALTAR = register("celestial_altar",
        p -> new BlockItem(ModBlocks.CELESTIAL_ALTAR.get(), p.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> GRAVITY_RUNE = block(ModBlocks.GRAVITY_RUNE);
    public static final RegistryObject<Item> STARFIRE_RUNE = block(ModBlocks.STARFIRE_RUNE);
    public static final RegistryObject<Item> VAULT_SEAL = block(ModBlocks.VAULT_SEAL);

    // --- Materials --------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> STARDUST = register("stardust", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> RAW_STARMETAL = register("raw_starmetal", Item::new);
    public static final RegistryObject<Item> STARMETAL_INGOT = register("starmetal_ingot", Item::new);
    public static final RegistryObject<Item> ASTRAL_SHARD = register("astral_shard", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> VOID_ESSENCE = register("void_essence", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> CELESTIAL_CORE = register("celestial_core",
        p -> new LoreItem(p.rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 1));
    public static final RegistryObject<Item> SOVEREIGN_HEART = register("sovereign_heart",
        p -> new LoreItem(p.rarity(Rarity.EPIC).fireResistant().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 2));
    public static final RegistryObject<Item> ECLIPSE_SIGIL = register("eclipse_sigil",
        p -> new EclipseSigilItem(p.rarity(Rarity.EPIC).stacksTo(1).fireResistant().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final RegistryObject<Item> ASTRAL_EGG = register("astral_egg", p -> new AstralEggItem(p.rarity(Rarity.RARE).stacksTo(1)));
    public static final RegistryObject<Item> STAR_CHART = register("star_chart", p -> new LoreItem(p.rarity(Rarity.UNCOMMON).stacksTo(1), 3));

    // --- Starmetal tools --------------------------------------------------------------------------------------
    public static final RegistryObject<Item> STARMETAL_SWORD = register("starmetal_sword",
        p -> new Item(p.sword(ModMaterials.STARMETAL, 3.0F, -2.4F)));
    public static final RegistryObject<Item> STARMETAL_PICKAXE = register("starmetal_pickaxe",
        p -> new Item(p.pickaxe(ModMaterials.STARMETAL, 1.0F, -2.8F)));
    public static final RegistryObject<Item> STARMETAL_AXE = register("starmetal_axe",
        p -> new AxeItem(ModMaterials.STARMETAL, 5.0F, -3.0F, p));
    public static final RegistryObject<Item> STARMETAL_SHOVEL = register("starmetal_shovel",
        p -> new ShovelItem(ModMaterials.STARMETAL, 1.5F, -3.0F, p));
    public static final RegistryObject<Item> STARMETAL_HOE = register("starmetal_hoe",
        p -> new HoeItem(ModMaterials.STARMETAL, -3.5F, 0.0F, p));

    // --- Armor ------------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> STARMETAL_HELMET = register("starmetal_helmet",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.STARMETAL_ARMOR, ArmorType.HELMET), StarforgedArmorItem.Ability.STARMETAL_SET));
    public static final RegistryObject<Item> STARMETAL_CHESTPLATE = register("starmetal_chestplate",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.STARMETAL_ARMOR, ArmorType.CHESTPLATE), StarforgedArmorItem.Ability.STARMETAL_SET));
    public static final RegistryObject<Item> STARMETAL_LEGGINGS = register("starmetal_leggings",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.STARMETAL_ARMOR, ArmorType.LEGGINGS), StarforgedArmorItem.Ability.STARMETAL_SET));
    public static final RegistryObject<Item> STARMETAL_BOOTS = register("starmetal_boots",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.STARMETAL_ARMOR, ArmorType.BOOTS), StarforgedArmorItem.Ability.STARMETAL_SET));
    public static final RegistryObject<Item> COMET_BOOTS = register("comet_boots",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.COMET_ARMOR, ArmorType.BOOTS).rarity(Rarity.RARE), StarforgedArmorItem.Ability.COMET_BOOTS));
    public static final RegistryObject<Item> NEBULA_CLOAK = register("nebula_cloak",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.NEBULA_ARMOR, ArmorType.CHESTPLATE).rarity(Rarity.RARE)
            .component(DataComponents.GLIDER, Unit.INSTANCE), StarforgedArmorItem.Ability.NEBULA_CLOAK));
    public static final RegistryObject<Item> ECLIPSE_CROWN = register("eclipse_crown",
        p -> new StarforgedArmorItem(p.humanoidArmor(ModMaterials.ECLIPSE_ARMOR, ArmorType.HELMET).rarity(Rarity.EPIC).fireResistant(),
            StarforgedArmorItem.Ability.ECLIPSE_CROWN));

    // --- Legendary weapons & gadgets --------------------------------------------------------------------------
    public static final RegistryObject<Item> STARCALLER_STAFF = register("starcaller_staff",
        p -> new StarcallerStaffItem(p.stacksTo(1).durability(450).rarity(Rarity.RARE).repairable(ModTags.STARMETAL_REPAIR).enchantable(15)));
    public static final RegistryObject<Item> METEOR_HAMMER = register("meteor_hammer",
        p -> new MeteorHammerItem(p.pickaxe(ModMaterials.STARMETAL, 8.0F, -3.3F).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> VOID_SCYTHE = register("void_scythe",
        p -> new VoidScytheItem(p.sword(ModMaterials.STARMETAL, 5.0F, -2.8F).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CONSTELLATION_BOW = register("constellation_bow",
        p -> new ConstellationBowItem(p.durability(900).enchantable(1).rarity(Rarity.RARE).repairable(ModTags.STARMETAL_REPAIR)));
    public static final RegistryObject<Item> ECLIPSE_BLADE = register("eclipse_blade",
        p -> new EclipseBladeItem(p.sword(ModMaterials.ECLIPSE, 8.0F, -2.2F).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> SINGULARITY_GRENADE = register("singularity_grenade",
        p -> new SingularityGrenadeItem(p.stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> RIFT_PEARL = register("rift_pearl",
        p -> new RiftPearlItem(p.durability(128).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ASTRAL_COMPASS = register("astral_compass",
        p -> new AstralCompassItem(p.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GRAVITY_GAUNTLET = register("gravity_gauntlet",
        p -> new GravityGauntletItem(p.stacksTo(1).durability(600).rarity(Rarity.RARE).repairable(ModTags.STARMETAL_REPAIR)));

    // --- Projectile visuals (not obtainable) ------------------------------------------------------------------
    public static final RegistryObject<Item> STARBOLT = register("starbolt", Item::new);
    public static final RegistryObject<Item> VOID_BOLT = register("void_bolt", Item::new);
    public static final RegistryObject<Item> CRYSTAL_SHARD_BOLT = register("crystal_shard_bolt", Item::new);

    // --- Spawn eggs -------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> STAR_MITE_SPAWN_EGG = egg("star_mite_spawn_egg", ModEntities.STAR_MITE);
    public static final RegistryObject<Item> VOID_STALKER_SPAWN_EGG = egg("void_stalker_spawn_egg", ModEntities.VOID_STALKER);
    public static final RegistryObject<Item> ASTRAL_GOLEM_SPAWN_EGG = egg("astral_golem_spawn_egg", ModEntities.ASTRAL_GOLEM);
    public static final RegistryObject<Item> MIMIC_SPAWN_EGG = egg("mimic_spawn_egg", ModEntities.MIMIC);
    public static final RegistryObject<Item> ASTRAL_WRAITH_SPAWN_EGG = egg("astral_wraith_spawn_egg", ModEntities.ASTRAL_WRAITH);
    public static final RegistryObject<Item> STARLING_SPAWN_EGG = egg("starling_spawn_egg", ModEntities.STARLING);
    public static final RegistryObject<Item> NEBULA_RAY_SPAWN_EGG = egg("nebula_ray_spawn_egg", ModEntities.NEBULA_RAY);
    public static final RegistryObject<Item> ECLIPSE_SOVEREIGN_SPAWN_EGG = egg("eclipse_sovereign_spawn_egg", ModEntities.ECLIPSE_SOVEREIGN);

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

    private ModItems() {
    }
}
