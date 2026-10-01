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

    private ModCreativeTabs() {
    }
}
