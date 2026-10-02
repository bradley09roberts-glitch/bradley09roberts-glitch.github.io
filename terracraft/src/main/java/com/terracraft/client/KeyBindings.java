package com.terracraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.terracraft.TerraCraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** TerraCraft key bindings (rebindable in Controls). */
public final class KeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(TerraCraft.id("terracraft"));
    public static final KeyMapping OPEN_EQUIPMENT = new KeyMapping("key.terracraft.equipment", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping OPEN_CRAFTING = new KeyMapping("key.terracraft.crafting", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private KeyBindings() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EQUIPMENT);
        event.register(OPEN_CRAFTING);
    }
}
