package com.terracraft.core;

import com.terracraft.TerraCraft;
import com.terracraft.player.stats.BuffStatSource;
import com.terracraft.player.stats.EquipmentStatSources;
import com.terracraft.player.stats.StatCalculator;
import com.terracraft.player.stats.WellFedSource;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Common (both sides) setup after registries are populated. */
public final class CommonSetup {
    private CommonSetup() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StatCalculator.addSource(EquipmentStatSources::armor);
            StatCalculator.addSource(EquipmentStatSources::accessories);
            StatCalculator.addSource(new BuffStatSource());
            StatCalculator.addSource(new WellFedSource());
            TerraCraft.LOGGER.info("TerraCraft common setup complete");
        });
    }
}
