package com.grandline.journey;

import com.grandline.GrandLine;
import com.grandline.registry.ModCreativeTabs;
import com.grandline.registry.ModItems;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Registration of the journey: the {@code grandline:milestone} advancement trigger and the Den Den Mushi snails
 * (registered into the shared item register; the asset pipeline reads the ids from this file).
 */
public final class JourneyRegistry {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, GrandLine.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, MilestoneTrigger> MILESTONE = TRIGGERS.register("milestone", MilestoneTrigger::new);

    /** A personal snail: call any online player who carries one too. */
    public static final DeferredItem<Item> DEN_DEN_MUSHI = ModItems.ITEMS.register("den_den_mushi",
            () -> new DenDenMushiItem(DenDenMushiItem.Kind.PERSONAL, new Item.Properties().stacksTo(1)));
    /** Tuned to a crew: speak to every crewmate who carries a snail. */
    public static final DeferredItem<Item> CREW_DEN_DEN_MUSHI = ModItems.ITEMS.register("crew_den_den_mushi",
            () -> new DenDenMushiItem(DenDenMushiItem.Kind.CREW, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** A Baby Den Den: one message to anyone, then it wanders off (consumed). */
    public static final DeferredItem<Item> BABY_DEN_DEN_MUSHI = ModItems.ITEMS.register("baby_den_den_mushi",
            () -> new DenDenMushiItem(DenDenMushiItem.Kind.BABY, new Item.Properties().stacksTo(16)));
    /** A black snail taken from the Marines: it picks up their wavelength (eavesdropping). Not craftable. */
    public static final DeferredItem<Item> BLACK_DEN_DEN_MUSHI = ModItems.ITEMS.register("black_den_den_mushi",
            () -> new DenDenMushiItem(DenDenMushiItem.Kind.BLACK, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final List<DeferredItem<Item>> SNAILS = List.of(DEN_DEN_MUSHI, CREW_DEN_DEN_MUSHI, BABY_DEN_DEN_MUSHI, BLACK_DEN_DEN_MUSHI);

    /** Shop offers: general stores and merchants sell the craftable snails. */
    public record Offer(String item, int count, long price) {}

    public static List<Offer> shopExtras(String role) {
        return switch (role) {
            case "merchant", "shopkeeper", "sea_merchant" -> List.of(new Offer("grandline:den_den_mushi", 1, 2500),
                    new Offer("grandline:crew_den_den_mushi", 1, 6000), new Offer("grandline:baby_den_den_mushi", 2, 400));
            default -> List.of();
        };
    }

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
        modBus.addListener(JourneyRegistry::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        for (var it : SNAILS) e.accept(it.get());
    }

    private JourneyRegistry() {}
}
