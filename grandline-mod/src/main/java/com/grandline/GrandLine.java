package com.grandline;

import com.grandline.core.GLConfig;
import com.grandline.net.GLNetwork;
import com.grandline.registry.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(GrandLine.MODID)
public final class GrandLine {
    public static final String MODID = "grandline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GrandLine(IEventBus modBus, ModContainer container) {
        LOGGER.info("Grand Line Odyssey loading");
        container.registerConfig(ModConfig.Type.SERVER, GLConfig.SPEC);
        ModAttachments.register(modBus);
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModEffects.register(modBus);
        ModWorldgen.register(modBus);
        ModCreativeTabs.register(modBus);
        com.grandline.activity.ActivityRegistry.register(modBus); // sea activities: treasure maps, fishing, cooking
        com.grandline.journey.JourneyRegistry.register(modBus); // journey advancements (milestone trigger), Den Den Mushi
        com.grandline.base.BaseRegistry.register(modBus); // crew bases: the crew flag and the base chest
        GLNetwork.register(modBus);
        modBus.addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            com.grandline.combat.Abilities.bootstrap();
            LOGGER.info("Registered {} abilities, {} fruits ({} implemented), {} styles",
                    com.grandline.combat.Abilities.all().size(), com.grandline.fruit.DevilFruits.all().size(),
                    com.grandline.fruit.DevilFruits.available().size(), com.grandline.combat.style.FightingStyles.all().size());
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
