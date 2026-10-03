package com.terracraft;

import com.mojang.logging.LogUtils;
import com.terracraft.config.TerraConfig;
import com.terracraft.core.CommonSetup;
import com.terracraft.core.GameEventHandlers;
import com.terracraft.network.TerraNetwork;
import com.terracraft.registry.ModRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * TerraCraft: a Terraria total conversion for Minecraft.
 * <p>
 * This class only wires the mod together. Every subsystem owns its own registration method so the
 * entry point stays small as the project grows:
 * <ul>
 *     <li>{@link TerraConfig} - gameplay and client configuration</li>
 *     <li>{@link ModRegistries} - every DeferredRegister (blocks, items, entities, menus...)</li>
 *     <li>{@link TerraNetwork} - the play-phase network payloads</li>
 *     <li>{@link GameEventHandlers} - registration of all game-bus listener classes</li>
 *     <li>{@code com.terracraft.client.TerraClient} - client only wiring (loaded only on the client)</li>
 * </ul>
 */
@Mod(TerraCraft.MODID)
public final class TerraCraft {
    public static final String MODID = "terracraft";
    public static final String NAME = "TerraCraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static IEventBus modBus;

    public TerraCraft(IEventBus modBus, ModContainer container) {
        TerraCraft.modBus = modBus;

        TerraConfig.register(container);
        ModRegistries.register(modBus);
        modBus.addListener(TerraNetwork::register);
        GameEventHandlers.register();

        modBus.addListener(CommonSetup::onCommonSetup);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            com.terracraft.client.TerraClient.init(modBus);
        }
        LOGGER.info("{} constructed - Terraria progression is coming to this world.", NAME);
    }

    /** The mod event bus (registration and lifecycle events). */
    public static IEventBus modBus() {
        return modBus;
    }

    /** Creates an identifier in the TerraCraft namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
