package com.grandline.client;

import com.grandline.GrandLine;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Create World screen opens on "Grand Line Ocean World" instead of Minecraft's Default, so a new world is the mod's
 * ocean world unless the player picks another type. It is applied once per screen and only over the vanilla default,
 * so a type the player chooses (or a world being re-created with another type) is left alone.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class DefaultWorldType {
    private static final ResourceKey<WorldPreset> OCEAN_WORLD = ResourceKey.create(Registries.WORLD_PRESET, GrandLine.id("ocean_world"));
    private static final Set<Screen> DONE = Collections.newSetFromMap(new WeakHashMap<>());

    private DefaultWorldType() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen) || !DONE.add(screen)) return;
        WorldCreationUiState ui = screen.getUiState();
        Holder<WorldPreset> current = ui.getWorldType().preset();
        if (current == null || !current.is(WorldPresets.NORMAL)) return;
        for (WorldCreationUiState.WorldTypeEntry type : ui.getNormalPresetList()) {
            if (type.preset() != null && type.preset().is(OCEAN_WORLD)) {
                ui.setWorldType(type);
                return;
            }
        }
    }
}
