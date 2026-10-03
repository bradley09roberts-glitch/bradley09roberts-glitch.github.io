package com.terracraft.registry;

import com.terracraft.TerraCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** One creative tab per {@link TabGroup}, filled from {@link ModItems#tabContents}. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TerraCraft.MODID);
    public static final Map<TabGroup, RegistryObject<CreativeModeTab>> BY_GROUP = new EnumMap<>(TabGroup.class);

    static {
        RegistryObject<CreativeModeTab> previous = null;
        for (TabGroup group : TabGroup.values()) {
            String name = group.name().toLowerCase(Locale.ROOT);
            RegistryObject<CreativeModeTab> before = previous;
            RegistryObject<CreativeModeTab> tab = TABS.register(name, () -> {
                CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.terracraft." + name))
                    .icon(() -> iconFor(group))
                    .displayItems((params, output) -> {
                        for (Supplier<? extends Item> item : ModItems.tabContents(group)) {
                            output.accept(item.get());
                        }
                    });
                if (before != null) {
                    builder.withTabsBefore(before.getId());
                }
                return builder.build();
            });
            BY_GROUP.put(group, tab);
            previous = tab;
        }
    }

    private ModCreativeTabs() {}

    private static ItemStack iconFor(TabGroup group) {
        var contents = ModItems.tabContents(group);
        if (group == TabGroup.DEV) {
            return new ItemStack(ModItems.DEV_TABLET.get());
        }
        return contents.isEmpty() ? new ItemStack(Items.BOOK) : new ItemStack(contents.get(0).get());
    }
}
