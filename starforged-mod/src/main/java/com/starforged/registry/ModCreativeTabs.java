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

    private static final List<Supplier<? extends Item>> MOON_ORDER = List.of(
        com.starforged.moon.MoonItems.LUNAR_KEY,
        com.starforged.moon.MoonItems.TIDECALLER_GLAIVE,
        com.starforged.moon.MoonItems.CRESCENT_GLAIVE,
        com.starforged.moon.MoonItems.ORRERY_STAFF,
        com.starforged.moon.MoonItems.PHASE_DAGGERS,
        com.starforged.moon.MoonItems.MOONSHOT_CROSSBOW,
        com.starforged.moon.MoonItems.STASIS_BELL,
        com.starforged.moon.MoonItems.TETHER_HOOK,
        com.starforged.moon.MoonItems.CROWN_OF_TIDES,
        com.starforged.moon.MoonItems.MOONSILVER_HELMET,
        com.starforged.moon.MoonItems.MOONSILVER_CHESTPLATE,
        com.starforged.moon.MoonItems.MOONSILVER_LEGGINGS,
        com.starforged.moon.MoonItems.MOONSILVER_BOOTS,
        com.starforged.moon.MoonItems.MOONSILVER_SWORD,
        com.starforged.moon.MoonItems.MOONSILVER_PICKAXE,
        com.starforged.moon.MoonItems.MOONSILVER_AXE,
        com.starforged.moon.MoonItems.MOONSILVER_SHOVEL,
        com.starforged.moon.MoonItems.MOONSILVER_HOE,
        com.starforged.moon.MoonItems.RAW_MOONSILVER,
        com.starforged.moon.MoonItems.MOONSILVER_INGOT,
        com.starforged.moon.MoonItems.SELENITE_SHARD,
        com.starforged.moon.MoonItems.LUNAR_DUST,
        com.starforged.moon.MoonItems.LUNAR_PEARL,
        com.starforged.moon.MoonItems.TIDAL_SIGIL,
        com.starforged.moon.MoonItems.MOON_HEART,
        com.starforged.moon.MoonItems.MOONSTONE,
        com.starforged.moon.MoonItems.REGOLITH,
        com.starforged.moon.MoonItems.UMBRAL_REGOLITH,
        com.starforged.moon.MoonItems.SILVER_SAND,
        com.starforged.moon.MoonItems.MOONSILVER_ORE,
        com.starforged.moon.MoonItems.SELENITE_CLUSTER,
        com.starforged.moon.MoonItems.MOONPETAL,
        com.starforged.moon.MoonItems.TIDAL_CLAM,
        com.starforged.moon.MoonItems.MOONSILVER_BLOCK,
        com.starforged.moon.MoonItems.LUNAR_BRICKS,
        com.starforged.moon.MoonItems.CRACKED_LUNAR_BRICKS,
        com.starforged.moon.MoonItems.CHISELED_LUNAR_BRICKS,
        com.starforged.moon.MoonItems.LUNAR_BRICK_STAIRS,
        com.starforged.moon.MoonItems.LUNAR_BRICK_SLAB,
        com.starforged.moon.MoonItems.MOON_GLASS,
        com.starforged.moon.MoonItems.MOON_LANTERN,
        com.starforged.moon.MoonItems.GRAVITY_PLATE,
        com.starforged.moon.MoonItems.ORRERY_RING,
        com.starforged.moon.MoonItems.LUNAR_MURAL,
        com.starforged.moon.MoonItems.ORRERY_CONSOLE,
        com.starforged.moon.MoonItems.MOON_SEAL,
        com.starforged.moon.MoonItems.MOON_ALTAR,
        com.starforged.moon.MoonItems.REGOLITH_SKIMMER_SPAWN_EGG,
        com.starforged.moon.MoonItems.LUNAR_MOTH_SPAWN_EGG,
        com.starforged.moon.MoonItems.SELENITE_SENTINEL_SPAWN_EGG,
        com.starforged.moon.MoonItems.UMBRAL_LURKER_SPAWN_EGG,
        com.starforged.moon.MoonItems.MOONKIT_SPAWN_EGG,
        com.starforged.moon.MoonItems.MOONLEAPER_SPAWN_EGG,
        com.starforged.moon.MoonItems.PALE_MATRIARCH_SPAWN_EGG
    );

    public static final RegistryObject<CreativeModeTab> MOONFORGED = TABS.register("moonforged", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.starforged.moonforged"))
        .withTabsBefore(SUNFORGED.getId())
        .icon(() -> new ItemStack(com.starforged.moon.MoonItems.TIDECALLER_GLAIVE.get()))
        .displayItems((params, output) -> {
            for (Supplier<? extends Item> item : MOON_ORDER) {
                output.accept(item.get());
            }
        })
        .build());

    private static final List<Supplier<? extends Item>> TEMPEST_ORDER = List.of(
        com.starforged.tempest.TempestItems.SKYBREAKER_CORE,
        com.starforged.tempest.TempestItems.SKYBREAKER_HALBERD,
        com.starforged.tempest.TempestItems.TEMPEST_JAVELIN,
        com.starforged.tempest.TempestItems.GALE_BLADES,
        com.starforged.tempest.TempestItems.STORMHOOK,
        com.starforged.tempest.TempestItems.ARC_CANNON,
        com.starforged.tempest.TempestItems.SKYCLEAVER,
        com.starforged.tempest.TempestItems.TEMPEST_CROWN,
        com.starforged.tempest.TempestItems.AETHERIUM_HELMET,
        com.starforged.tempest.TempestItems.AETHERIUM_CHESTPLATE,
        com.starforged.tempest.TempestItems.AETHERIUM_LEGGINGS,
        com.starforged.tempest.TempestItems.AETHERIUM_BOOTS,
        com.starforged.tempest.TempestItems.AETHERIUM_SWORD,
        com.starforged.tempest.TempestItems.AETHERIUM_PICKAXE,
        com.starforged.tempest.TempestItems.AETHERIUM_AXE,
        com.starforged.tempest.TempestItems.AETHERIUM_SHOVEL,
        com.starforged.tempest.TempestItems.AETHERIUM_HOE,
        com.starforged.tempest.TempestItems.RAW_AETHERIUM,
        com.starforged.tempest.TempestItems.AETHERIUM_INGOT,
        com.starforged.tempest.TempestItems.CHARGED_AETHERIUM_INGOT,
        com.starforged.tempest.TempestItems.THUNDER_SHARD,
        com.starforged.tempest.TempestItems.STATIC_MOTE,
        com.starforged.tempest.TempestItems.CHARGED_AETHER_DUST,
        com.starforged.tempest.TempestItems.SHARDWING_CRYSTAL,
        com.starforged.tempest.TempestItems.STORM_FEATHER,
        com.starforged.tempest.TempestItems.STORMBOUND_PLATE,
        com.starforged.tempest.TempestItems.CHARGED_SCRAP,
        com.starforged.tempest.TempestItems.THUNDERJAW_HORN,
        com.starforged.tempest.TempestItems.STORMHIDE,
        com.starforged.tempest.TempestItems.BREEZE_SHARD,
        com.starforged.tempest.TempestItems.ALPHA_CONDUCTOR_HORN,
        com.starforged.tempest.TempestItems.STORMHEART,
        com.starforged.tempest.TempestItems.TEMPEST_SIGIL,
        com.starforged.tempest.TempestItems.STORMSTONE,
        com.starforged.tempest.TempestItems.SKYROCK,
        com.starforged.tempest.TempestItems.SKYSOIL,
        com.starforged.tempest.TempestItems.STORMGRASS,
        com.starforged.tempest.TempestItems.STORMWOOD_LOG,
        com.starforged.tempest.TempestItems.STORMWOOD_PLANKS,
        com.starforged.tempest.TempestItems.STORMLEAVES,
        com.starforged.tempest.TempestItems.GALE_SEED,
        com.starforged.tempest.TempestItems.AETHERIUM_ORE,
        com.starforged.tempest.TempestItems.AETHERIUM_BLOCK,
        com.starforged.tempest.TempestItems.CHARGED_AETHERIUM_BLOCK,
        com.starforged.tempest.TempestItems.THUNDER_CRYSTAL_CLUSTER,
        com.starforged.tempest.TempestItems.TEMPEST_BRICKS,
        com.starforged.tempest.TempestItems.CHISELED_TEMPEST_BRICKS,
        com.starforged.tempest.TempestItems.TEMPEST_BRICK_STAIRS,
        com.starforged.tempest.TempestItems.TEMPEST_BRICK_SLAB,
        com.starforged.tempest.TempestItems.AETHERGLASS,
        com.starforged.tempest.TempestItems.STORM_LANTERN,
        com.starforged.tempest.TempestItems.WIND_CHIME,
        com.starforged.tempest.TempestItems.STORM_DYNAMO,
        com.starforged.tempest.TempestItems.AETHERIUM_CONDUCTOR,
        com.starforged.tempest.TempestItems.ROTATING_CONDUCTOR,
        com.starforged.tempest.TempestItems.SPLITTER_RELAY,
        com.starforged.tempest.TempestItems.STORM_RELAY,
        com.starforged.tempest.TempestItems.OVERLOAD_RELAY,
        com.starforged.tempest.TempestItems.STORM_CAPACITOR,
        com.starforged.tempest.TempestItems.CITADEL_CORE,
        com.starforged.tempest.TempestItems.LIGHTNING_BEACON,
        com.starforged.tempest.TempestItems.WEATHER_ENGINE,
        com.starforged.tempest.TempestItems.WIND_VENT,
        com.starforged.tempest.TempestItems.GALE_VENT,
        com.starforged.tempest.TempestItems.STORM_LIFT,
        com.starforged.tempest.TempestItems.SHOCK_PLATE,
        com.starforged.tempest.TempestItems.SKY_ANCHOR,
        com.starforged.tempest.TempestItems.THUNDER_RUNE,
        com.starforged.tempest.TempestItems.GALE_RUNE,
        com.starforged.tempest.TempestItems.CYCLONE_EMITTER,
        com.starforged.tempest.TempestItems.TEMPEST_SEAL,
        com.starforged.tempest.TempestItems.TEMPEST_ALTAR,
        com.starforged.tempest.TempestItems.STATIC_WISP_SPAWN_EGG,
        com.starforged.tempest.TempestItems.SHARDWING_SPAWN_EGG,
        com.starforged.tempest.TempestItems.STORMBOUND_SPAWN_EGG,
        com.starforged.tempest.TempestItems.THUNDERJAW_SPAWN_EGG,
        com.starforged.tempest.TempestItems.ZEPHYR_SPRITE_SPAWN_EGG,
        com.starforged.tempest.TempestItems.STORM_ROC_SPAWN_EGG,
        com.starforged.tempest.TempestItems.THUNDERJAW_ALPHA_SPAWN_EGG,
        com.starforged.tempest.TempestItems.VEYR_SPAWN_EGG
    );

    public static final RegistryObject<CreativeModeTab> TEMPESTFORGED = TABS.register("tempestforged", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.starforged.tempestforged"))
        .withTabsBefore(MOONFORGED.getId())
        .icon(() -> new ItemStack(com.starforged.tempest.TempestItems.SKYBREAKER_HALBERD.get()))
        .displayItems((params, output) -> {
            for (Supplier<? extends Item> item : TEMPEST_ORDER) {
                output.accept(item.get());
            }
        })
        .build());

    private ModCreativeTabs() {
    }
}
