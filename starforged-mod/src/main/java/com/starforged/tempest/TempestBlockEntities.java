package com.starforged.tempest;

import com.starforged.Starforged;
import com.starforged.tempest.block.StormTickerBlockEntity;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block entities of the Tempestforged expansion. */
public final class TempestBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,
        Starforged.MODID);

    /** One ticking entity shared by every "live" storm block: dynamos, beacons, vents, lifts and cyclone emitters. */
    public static final RegistryObject<BlockEntityType<StormTickerBlockEntity>> STORM_TICKER = BLOCK_ENTITIES.register("storm_ticker",
        () -> new BlockEntityType<>(StormTickerBlockEntity::new, Set.of(
            TempestBlocks.STORM_DYNAMO.get(), TempestBlocks.LIGHTNING_BEACON.get(), TempestBlocks.WIND_VENT.get(), TempestBlocks.GALE_VENT.get(),
            TempestBlocks.STORM_LIFT.get(), TempestBlocks.CYCLONE_EMITTER.get())));

    private TempestBlockEntities() {
    }
}
