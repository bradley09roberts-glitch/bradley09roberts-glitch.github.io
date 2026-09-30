package net.emberveil.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.emberveil.core.Emberveil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * Client-only entry point: the "Open Wayfarer's Almanac" keybind and the {@code /almanac} client command.
 * Both open the book GUI directly, so the Almanac stays readable even if the item is lost.
 */
@Mod(value = Emberveil.MODID, dist = Dist.CLIENT)
public final class EmberveilClient {
    public static final String KEY_CATEGORY = "key.categories.emberveil";
    public static final KeyMapping OPEN_ALMANAC = new KeyMapping("key.emberveil.open_almanac",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KEY_CATEGORY);

    public EmberveilClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(EmberveilClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(EmberveilClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EmberveilClient::registerClientCommands);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ALMANAC);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_ALMANAC.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                openAlmanac();
            }
        }
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        // Client commands need no operator permission and work in any world.
        event.getDispatcher().register(Commands.literal("almanac").executes(ctx -> {
            // Defer one tick: the chat screen is still closing while the command runs.
            Minecraft.getInstance().tell(EmberveilClient::openAlmanac);
            return 1;
        }));
    }

    public static void openAlmanac() {
        PatchouliAPI.get().openBookGUI(Emberveil.ALMANAC_ID);
    }
}
