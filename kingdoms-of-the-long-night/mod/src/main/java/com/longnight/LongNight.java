package com.longnight;

import com.longnight.entity.CitizenEntity;
import com.longnight.event.ServerEvents;
import com.longnight.registry.ModBlockEntities;
import com.longnight.registry.ModBlocks;
import com.longnight.registry.ModEntities;
import com.longnight.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(LongNight.MODID)
public class LongNight {
    public static final String MODID = "longnight";
    public static final Logger LOG = LogUtils.getLogger();

    public LongNight(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.registerItems();
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModEntities.TABS.register(modBus);
        modBus.addListener(this::attributes);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(new ServerEvents());
        LOG.info("Kingdoms of the Long Night initialising");
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.CITIZEN.get(), CitizenEntity.attributes().build());
    }
}
