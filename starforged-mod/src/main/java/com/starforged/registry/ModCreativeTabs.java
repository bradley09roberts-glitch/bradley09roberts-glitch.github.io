package com.starforged.registry;

import com.starforged.Starforged;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Starforged.MODID);

    private static final List<Supplier<? extends Item>> ORDER = List.of(
        // Legendary gear first - the showcase.
        ModItems.STARCALLER_STAFF, ModItems.METEOR_HAMMER, ModItems.VOID_SCYTHE, ModItems.CONSTELLATION_BOW, ModItems.ECLIPSE_BLADE,
        ModItems.GRAVITY_GAUNTLET, ModItems.RIFT_PEARL, ModItems.SINGULARITY_GRENADE, ModItems.ASTRAL_COMPASS,
        ModItems.COMET_BOOTS, ModItems.NEBULA_CLOAK, ModItems.ECLIPSE_CROWN,
        ModItems.STARMETAL_HELMET, ModItems.STARMETAL_CHESTPLATE, ModItems.STARMETAL_LEGGINGS, ModItems.STARMETAL_BOOTS,
        ModItems.STARMETAL_SWORD, ModItems.STARMETAL_PICKAXE, ModItems.STARMETAL_AXE, ModItems.STARMETAL_SHOVEL, ModItems.STARMETAL_HOE,
        // Materials & progression.
        ModItems.STARDUST, ModItems.RAW_STARMETAL, ModItems.STARMETAL_INGOT, ModItems.ASTRAL_SHARD, ModItems.VOID_ESSENCE,
        ModItems.CELESTIAL_CORE, ModItems.ECLIPSE_SIGIL, ModItems.SOVEREIGN_HEART, ModItems.ASTRAL_EGG, ModItems.STAR_CHART,
        // Blocks.
        ModItems.METEORITE_ROCK, ModItems.STARMETAL_ORE, ModItems.ASTRAL_CRYSTAL_CLUSTER, ModItems.STARMETAL_BLOCK,
        ModItems.ASTRAL_BRICKS, ModItems.CRACKED_ASTRAL_BRICKS, ModItems.CHISELED_ASTRAL_BRICKS, ModItems.ASTRAL_BRICK_STAIRS,
        ModItems.ASTRAL_BRICK_SLAB, ModItems.STARGLASS, ModItems.STAR_LANTERN, ModItems.GRAVITY_RUNE, ModItems.STARFIRE_RUNE,
        ModItems.VAULT_SEAL, ModItems.CELESTIAL_ALTAR,
        // Creatures.
        ModItems.STAR_MITE_SPAWN_EGG, ModItems.VOID_STALKER_SPAWN_EGG, ModItems.ASTRAL_WRAITH_SPAWN_EGG, ModItems.MIMIC_SPAWN_EGG,
        ModItems.ASTRAL_GOLEM_SPAWN_EGG, ModItems.STARLING_SPAWN_EGG, ModItems.NEBULA_RAY_SPAWN_EGG, ModItems.ECLIPSE_SOVEREIGN_SPAWN_EGG
    );

    public static final RegistryObject<CreativeModeTab> STARFORGED = TABS.register("starforged", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.starforged"))
        .withTabsBefore(CreativeModeTabs.SPAWN_EGGS.identifier())
        .icon(() -> new ItemStack(ModItems.STARCALLER_STAFF.get()))
        .displayItems((params, output) -> {
            for (Supplier<? extends Item> item : ORDER) {
                output.accept(item.get());
            }
        })
        .build());

    private static final List<Supplier<? extends Item>> SUN_ORDER = List.of(
        com.starforged.sun.SunItems.SOLAR_KEY, com.starforged.sun.SunItems.FLARE_GREATSWORD, com.starforged.sun.SunItems.SOLAR_LANCE,
        com.starforged.sun.SunItems.PHOENIX_BOW, com.starforged.sun.SunItems.HELIOS_SCEPTER, com.starforged.sun.SunItems.CINDER_CHAKRAM,
        com.starforged.sun.SunItems.SUNBURST_FLASK, com.starforged.sun.SunItems.PHOENIX_MANTLE, com.starforged.sun.SunItems.MAGMA_TREADS,
        com.starforged.sun.SunItems.SOLAR_CROWN,
        com.starforged.sun.SunItems.SUNSTEEL_HELMET, com.starforged.sun.SunItems.SUNSTEEL_CHESTPLATE, com.starforged.sun.SunItems.SUNSTEEL_LEGGINGS,
        com.starforged.sun.SunItems.SUNSTEEL_BOOTS, com.starforged.sun.SunItems.SUNSTEEL_SWORD, com.starforged.sun.SunItems.SUNSTEEL_PICKAXE,
        com.starforged.sun.SunItems.SUNSTEEL_AXE, com.starforged.sun.SunItems.SUNSTEEL_SHOVEL, com.starforged.sun.SunItems.SUNSTEEL_HOE,
        com.starforged.sun.SunItems.RAW_SUNSTEEL, com.starforged.sun.SunItems.SUNSTEEL_INGOT, com.starforged.sun.SunItems.EMBER_SHARD,
        com.starforged.sun.SunItems.SOLAR_ESSENCE, com.starforged.sun.SunItems.PHOENIX_FEATHER, com.starforged.sun.SunItems.SUNFIRE_SIGIL,
        com.starforged.sun.SunItems.SUN_HEART, com.starforged.sun.SunItems.PHOENIX_EGG,
        com.starforged.sun.SunItems.SCORCHSTONE, com.starforged.sun.SunItems.SUNSTONE_ORE, com.starforged.sun.SunItems.SUNSAND,
        com.starforged.sun.SunItems.ASHEN_SOIL, com.starforged.sun.SunItems.EMBER_CRYSTAL_CLUSTER, com.starforged.sun.SunItems.SUNBLOOM,
        com.starforged.sun.SunItems.SUNSTEEL_BLOCK, com.starforged.sun.SunItems.SUNBAKED_BRICKS, com.starforged.sun.SunItems.CRACKED_SUNBAKED_BRICKS,
        com.starforged.sun.SunItems.CHISELED_SUNBAKED_BRICKS, com.starforged.sun.SunItems.SUNBAKED_BRICK_STAIRS,
        com.starforged.sun.SunItems.SUNBAKED_BRICK_SLAB, com.starforged.sun.SunItems.SOLAR_GLASS, com.starforged.sun.SunItems.SUN_LANTERN,
        com.starforged.sun.SunItems.SOLAR_BRAZIER, com.starforged.sun.SunItems.SUNFIRE_VENT, com.starforged.sun.SunItems.SUN_SEAL,
        com.starforged.sun.SunItems.SUN_ALTAR,
        com.starforged.sun.SunItems.CINDER_IMP_SPAWN_EGG, com.starforged.sun.SunItems.MAGMA_CRAWLER_SPAWN_EGG,
        com.starforged.sun.SunItems.EMBER_HOUND_SPAWN_EGG, com.starforged.sun.SunItems.ASHEN_KNIGHT_SPAWN_EGG,
        com.starforged.sun.SunItems.SOLAR_PHOENIX_SPAWN_EGG, com.starforged.sun.SunItems.SUN_WARDEN_SPAWN_EGG
    );

    public static final RegistryObject<CreativeModeTab> SUNFORGED = TABS.register("sunforged", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.starforged.sunforged"))
        .withTabsBefore(STARFORGED.getId())
        .icon(() -> new ItemStack(com.starforged.sun.SunItems.FLARE_GREATSWORD.get()))
        .displayItems((params, output) -> {
            for (Supplier<? extends Item> item : SUN_ORDER) {
                output.accept(item.get());
            }
        })
        .build());

    private ModCreativeTabs() {
    }
}
