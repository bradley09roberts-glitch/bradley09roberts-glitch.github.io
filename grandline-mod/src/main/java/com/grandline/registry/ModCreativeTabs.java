package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.item.DevilFruitItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GrandLine.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grandline"))
            .icon(() -> new ItemStack(ModItems.LOG_POSE.get()))
            .displayItems((params, out) -> {
                for (var w : ModItems.WEAPONS) out.accept(w.get());
                out.accept(ModItems.LOG_POSE.get());
                out.accept(ModItems.ETERNAL_POSE.get());
                out.accept(ModItems.VIVRE_CARD.get());
                out.accept(ModItems.SEA_CHART.get());
                out.accept(ModItems.SEASTONE_CUFFS.get());
                out.accept(ModItems.BULLET.get());
                out.accept(ModItems.CANNONBALL.get());
                out.accept(ModItems.PELLET.get());
                out.accept(ModItems.POP_GREEN.get());
                out.accept(ModItems.SEASTONE_SHARD.get());
                out.accept(ModItems.MEAT_ON_BONE.get());
                out.accept(ModItems.SEA_KING_STEAK.get());
                out.accept(ModBlocks.SEASTONE.get());
                out.accept(ModBlocks.SEASTONE_BRICKS.get());
                out.accept(ModBlocks.SEASTONE_ORE.get());
                out.accept(ModBlocks.FRUIT_BLOOM.get());
            }).build());

    /** The architectural building kit (ArchBlocks): plasters, timber, roof tiles, stone, shutters, windows, props, lanterns. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARCHITECTURE = TABS.register("architecture", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grandline.architecture"))
            .icon(() -> new ItemStack(ArchBlocks.TERRACOTTA_ROOF_TILES.get()))
            .displayItems((params, out) -> {
                for (var b : ArchBlocks.ALL) out.accept(b.get());
            }).build());

    /** Creative-only fruit tab (bypasses the world ledger; for testing and creative building). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FRUITS = TABS.register("fruits", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grandline.fruits"))
            .icon(() -> DevilFruitItem.of("mera"))
            .displayItems((params, out) -> {
                for (DevilFruit f : DevilFruits.available()) out.accept(DevilFruitItem.of(f.id));
            }).build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private ModCreativeTabs() {}
}
