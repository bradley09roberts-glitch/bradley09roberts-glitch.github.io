package com.longnight.registry;

import com.longnight.LongNight;
import com.longnight.block.GravestoneBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LongNight.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravestoneBlockEntity>> GRAVESTONE = BLOCK_ENTITIES.register("gravestone",
            () -> BlockEntityType.Builder.of(GravestoneBlockEntity::new, ModBlocks.GRAVESTONE.get()).build(null));
}
