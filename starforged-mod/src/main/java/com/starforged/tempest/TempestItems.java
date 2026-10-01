package com.starforged.tempest;

import com.starforged.Starforged;
import com.starforged.item.LoreItem;
import com.starforged.item.StarforgedArmorItem;
import com.starforged.registry.ModTags;
import com.starforged.tempest.item.ArcCannonItem;
import com.starforged.tempest.item.GaleBladesItem;
import com.starforged.tempest.item.SkybreakerCoreItem;
import com.starforged.tempest.item.SkybreakerHalberdItem;
import com.starforged.tempest.item.SkycleaverItem;
import com.starforged.tempest.item.StormhookItem;
import com.starforged.tempest.item.TempestJavelinItem;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items of the Tempestforged expansion. */
public final class TempestItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Starforged.MODID);

    // --- Block items ------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> STORMSTONE = block(TempestBlocks.STORMSTONE);
    public static final RegistryObject<Item> SKYROCK = block(TempestBlocks.SKYROCK);
    public static final RegistryObject<Item> SKYSOIL = block(TempestBlocks.SKYSOIL);
    public static final RegistryObject<Item> STORMGRASS = block(TempestBlocks.STORMGRASS);
    public static final RegistryObject<Item> STORMWOOD_LOG = block(TempestBlocks.STORMWOOD_LOG);
    public static final RegistryObject<Item> STORMWOOD_PLANKS = block(TempestBlocks.STORMWOOD_PLANKS);
    public static final RegistryObject<Item> STORMLEAVES = block(TempestBlocks.STORMLEAVES);
    public static final RegistryObject<Item> GALE_SEED = block(TempestBlocks.GALE_SEED);
    public static final RegistryObject<Item> AETHERIUM_ORE = block(TempestBlocks.AETHERIUM_ORE);
    public static final RegistryObject<Item> AETHERIUM_BLOCK = block(TempestBlocks.AETHERIUM_BLOCK);
    public static final RegistryObject<Item> CHARGED_AETHERIUM_BLOCK = block(TempestBlocks.CHARGED_AETHERIUM_BLOCK);
    public static final RegistryObject<Item> THUNDER_CRYSTAL_CLUSTER = block(TempestBlocks.THUNDER_CRYSTAL_CLUSTER);
    public static final RegistryObject<Item> TEMPEST_BRICKS = block(TempestBlocks.TEMPEST_BRICKS);
    public static final RegistryObject<Item> CHISELED_TEMPEST_BRICKS = block(TempestBlocks.CHISELED_TEMPEST_BRICKS);
    public static final RegistryObject<Item> TEMPEST_BRICK_STAIRS = block(TempestBlocks.TEMPEST_BRICK_STAIRS);
    public static final RegistryObject<Item> TEMPEST_BRICK_SLAB = block(TempestBlocks.TEMPEST_BRICK_SLAB);
    public static final RegistryObject<Item> AETHERGLASS = block(TempestBlocks.AETHERGLASS);
    public static final RegistryObject<Item> STORM_LANTERN = block(TempestBlocks.STORM_LANTERN);
    public static final RegistryObject<Item> WIND_CHIME = block(TempestBlocks.WIND_CHIME);
    public static final RegistryObject<Item> STORM_DYNAMO = block(TempestBlocks.STORM_DYNAMO);
    public static final RegistryObject<Item> AETHERIUM_CONDUCTOR = block(TempestBlocks.AETHERIUM_CONDUCTOR);
    public static final RegistryObject<Item> ROTATING_CONDUCTOR = block(TempestBlocks.ROTATING_CONDUCTOR);
    public static final RegistryObject<Item> SPLITTER_RELAY = block(TempestBlocks.SPLITTER_RELAY);
    public static final RegistryObject<Item> STORM_RELAY = block(TempestBlocks.STORM_RELAY);
    public static final RegistryObject<Item> OVERLOAD_RELAY = block(TempestBlocks.OVERLOAD_RELAY);
    public static final RegistryObject<Item> STORM_CAPACITOR = block(TempestBlocks.STORM_CAPACITOR);
    public static final RegistryObject<Item> CITADEL_CORE = block(TempestBlocks.CITADEL_CORE);
    public static final RegistryObject<Item> LIGHTNING_BEACON = block(TempestBlocks.LIGHTNING_BEACON);
    public static final RegistryObject<Item> WEATHER_ENGINE = block(TempestBlocks.WEATHER_ENGINE);
    public static final RegistryObject<Item> WIND_VENT = block(TempestBlocks.WIND_VENT);
    public static final RegistryObject<Item> GALE_VENT = block(TempestBlocks.GALE_VENT);
    public static final RegistryObject<Item> STORM_LIFT = block(TempestBlocks.STORM_LIFT);
    public static final RegistryObject<Item> SHOCK_PLATE = block(TempestBlocks.SHOCK_PLATE);
    public static final RegistryObject<Item> SKY_ANCHOR = block(TempestBlocks.SKY_ANCHOR);
    public static final RegistryObject<Item> THUNDER_RUNE = block(TempestBlocks.THUNDER_RUNE);
    public static final RegistryObject<Item> GALE_RUNE = block(TempestBlocks.GALE_RUNE);
    public static final RegistryObject<Item> CYCLONE_EMITTER = block(TempestBlocks.CYCLONE_EMITTER);
    public static final RegistryObject<Item> TEMPEST_SEAL = block(TempestBlocks.TEMPEST_SEAL);
    public static final RegistryObject<Item> TEMPEST_ALTAR = register("tempest_altar",
        p -> new BlockItem(TempestBlocks.TEMPEST_ALTAR.get(), p.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));

    // --- Materials --------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> RAW_AETHERIUM = register("raw_aetherium", Item::new);
    public static final RegistryObject<Item> AETHERIUM_INGOT = register("aetherium_ingot", p -> new LoreItem(p.fireResistant(), 2));
    public static final RegistryObject<Item> CHARGED_AETHERIUM_INGOT = register("charged_aetherium_ingot",
        p -> new LoreItem(p.fireResistant().rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 1));
    public static final RegistryObject<Item> THUNDER_SHARD = register("thunder_shard", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> STATIC_MOTE = register("static_mote", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> CHARGED_AETHER_DUST = register("charged_aether_dust", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> SHARDWING_CRYSTAL = register("shardwing_crystal", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> STORM_FEATHER = register("storm_feather", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> STORMBOUND_PLATE = register("stormbound_plate", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> CHARGED_SCRAP = register("charged_scrap", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> THUNDERJAW_HORN = register("thunderjaw_horn", p -> new LoreItem(p.rarity(Rarity.UNCOMMON), 1));
    public static final RegistryObject<Item> STORMHIDE = register("stormhide", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> BREEZE_SHARD = register("breeze_shard", p -> new LoreItem(p, 1));
    public static final RegistryObject<Item> ALPHA_CONDUCTOR_HORN = register("alpha_conductor_horn",
        p -> new LoreItem(p.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 2));
    public static final RegistryObject<Item> STORMHEART = register("stormheart",
        p -> new LoreItem(p.rarity(Rarity.EPIC).fireResistant().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 2));
    public static final RegistryObject<Item> SKYBREAKER_CORE = register("skybreaker_core",
        p -> new SkybreakerCoreItem(p.stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final RegistryObject<Item> TEMPEST_SIGIL = register("tempest_sigil",
        p -> new LoreItem(p.stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 3));

    // --- Aetherium tools --------------------------------------------------------------------------------------
    public static final RegistryObject<Item> AETHERIUM_SWORD = register("aetherium_sword",
        p -> new Item(p.sword(TempestMaterials.AETHERIUM, 3.0F, -2.3F)));
    public static final RegistryObject<Item> AETHERIUM_PICKAXE = register("aetherium_pickaxe",
        p -> new Item(p.pickaxe(TempestMaterials.AETHERIUM, 1.0F, -2.7F)));
    public static final RegistryObject<Item> AETHERIUM_AXE = register("aetherium_axe",
        p -> new AxeItem(TempestMaterials.AETHERIUM, 5.0F, -2.9F, p));
    public static final RegistryObject<Item> AETHERIUM_SHOVEL = register("aetherium_shovel",
        p -> new ShovelItem(TempestMaterials.AETHERIUM, 1.5F, -2.9F, p));
    public static final RegistryObject<Item> AETHERIUM_HOE = register("aetherium_hoe",
        p -> new HoeItem(TempestMaterials.AETHERIUM, -5.5F, 0.0F, p));

    // --- Armor ------------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> AETHERIUM_HELMET = armor("aetherium_helmet", ArmorType.HELMET);
    public static final RegistryObject<Item> AETHERIUM_CHESTPLATE = armor("aetherium_chestplate", ArmorType.CHESTPLATE);
    public static final RegistryObject<Item> AETHERIUM_LEGGINGS = armor("aetherium_leggings", ArmorType.LEGGINGS);
    public static final RegistryObject<Item> AETHERIUM_BOOTS = armor("aetherium_boots", ArmorType.BOOTS);
    public static final RegistryObject<Item> TEMPEST_CROWN = register("tempest_crown",
        p -> new StarforgedArmorItem(p.humanoidArmor(TempestMaterials.TEMPEST_ARMOR, ArmorType.HELMET).rarity(Rarity.EPIC),
            StarforgedArmorItem.Ability.TEMPEST_CROWN));

    // --- Legendary weapons ------------------------------------------------------------------------------------
    public static final RegistryObject<Item> SKYBREAKER_HALBERD = register("skybreaker_halberd",
        p -> new SkybreakerHalberdItem(polearm(p, TempestMaterials.AETHERIUM, 7.0F, -3.0F, 1.5).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TEMPEST_JAVELIN = register("tempest_javelin",
        p -> new TempestJavelinItem(p.stacksTo(1).durability(1600).rarity(Rarity.EPIC).repairable(ModTags.AETHERIUM_REPAIR).enchantable(15)));
    public static final RegistryObject<Item> GALE_BLADES = register("gale_blades",
        p -> new GaleBladesItem(p.sword(TempestMaterials.AETHERIUM, 2.5F, -1.2F).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> STORMHOOK = register("stormhook",
        p -> new StormhookItem(p.stacksTo(1).durability(900).rarity(Rarity.RARE).repairable(ModTags.AETHERIUM_REPAIR).enchantable(10)));
    public static final RegistryObject<Item> ARC_CANNON = register("arc_cannon",
        p -> new ArcCannonItem(p.stacksTo(1).durability(1200).rarity(Rarity.EPIC).repairable(ModTags.AETHERIUM_REPAIR).enchantable(10)));
    public static final RegistryObject<Item> SKYCLEAVER = register("skycleaver",
        p -> new SkycleaverItem(polearm(p, TempestMaterials.TEMPEST, 11.0F, -3.2F, 1.0).rarity(Rarity.EPIC)));

    // --- Spawn eggs -------------------------------------------------------------------------------------------
    public static final RegistryObject<Item> STATIC_WISP_SPAWN_EGG = egg("static_wisp_spawn_egg", TempestEntities.STATIC_WISP);
    public static final RegistryObject<Item> SHARDWING_SPAWN_EGG = egg("shardwing_spawn_egg", TempestEntities.SHARDWING);
    public static final RegistryObject<Item> STORMBOUND_SPAWN_EGG = egg("stormbound_spawn_egg", TempestEntities.STORMBOUND);
    public static final RegistryObject<Item> THUNDERJAW_SPAWN_EGG = egg("thunderjaw_spawn_egg", TempestEntities.THUNDERJAW);
    public static final RegistryObject<Item> ZEPHYR_SPRITE_SPAWN_EGG = egg("zephyr_sprite_spawn_egg", TempestEntities.ZEPHYR_SPRITE);
    public static final RegistryObject<Item> STORM_ROC_SPAWN_EGG = egg("storm_roc_spawn_egg", TempestEntities.STORM_ROC);
    public static final RegistryObject<Item> THUNDERJAW_ALPHA_SPAWN_EGG = egg("thunderjaw_alpha_spawn_egg", TempestEntities.THUNDERJAW_ALPHA);
    public static final RegistryObject<Item> VEYR_SPAWN_EGG = egg("veyr_spawn_egg", TempestEntities.VEYR);

    /** A sword with extra reach (halberds and greatswords). */
    private static Item.Properties polearm(Item.Properties p, ToolMaterial material, float damage, float speed, double reach) {
        return p.sword(material, damage, speed).attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage + material.attackDamageBonus(),
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(Starforged.id("polearm_reach"), reach, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build());
    }

    private static RegistryObject<Item> armor(String name, ArmorType type) {
        return register(name, p -> new StarforgedArmorItem(p.humanoidArmor(TempestMaterials.AETHERIUM_ARMOR, type),
            StarforgedArmorItem.Ability.AETHERIUM_SET));
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

    private TempestItems() {
    }
}
