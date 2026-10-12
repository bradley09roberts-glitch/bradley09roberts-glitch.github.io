package com.longnight.registry;

import com.longnight.LongNight;
import com.longnight.entity.CitizenEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, LongNight.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LongNight.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CitizenEntity>> CITIZEN = ENTITIES.register("citizen",
            () -> EntityType.Builder.of(CitizenEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10)
                    .eyeHeight(1.62f).build("citizen"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.longnight"))
            .icon(() -> new ItemStack(ModBlocks.HEARTHSTONE.get()))
            .displayItems((params, out) -> ModItems.ITEMS.getEntries().forEach(i -> out.accept(i.get())))
            .build());
}
