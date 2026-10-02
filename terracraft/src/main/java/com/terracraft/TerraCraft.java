package com.terracraft;

import com.mojang.logging.LogUtils;
import com.terracraft.config.TerraConfig;
import com.terracraft.core.CommonSetup;
import com.terracraft.core.GameEventHandlers;
import com.terracraft.network.TerraNetwork;
import com.terracraft.registry.ModRegistries;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * TerraCraft: a Terraria total conversion for Minecraft.
 * <p>
 * This class only wires the mod together. Every subsystem owns its own registration method so the
 * entry point stays small as the project grows:
 * <ul>
 *     <li>{@link TerraConfig} - gameplay and client configuration</li>
 *     <li>{@link ModRegistries} - every DeferredRegister (blocks, items, entities, menus...)</li>
 *     <li>{@link TerraNetwork} - the single play-phase network channel</li>
 *     <li>{@link GameEventHandlers} - registration of all game-bus listener classes</li>
 *     <li>{@code com.terracraft.client.TerraClient} - client only wiring (loaded only on the client)</li>
 * </ul>
 */
@Mod(TerraCraft.MODID)
public final class TerraCraft {
    public static final String MODID = "terracraft";
    public static final String NAME = "TerraCraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TerraCraft(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();

        TerraConfig.register(context);
        ModRegistries.register(modBus);
        TerraNetwork.init();
        GameEventHandlers.register();

        FMLCommonSetupEvent.getBus(modBus).addListener(CommonSetup::onCommonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.terracraft.client.TerraClient.init(modBus);
        }
        LOGGER.info("{} constructed - Terraria progression is coming to this world.", NAME);
    }

    /** Creates an identifier in the TerraCraft namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
