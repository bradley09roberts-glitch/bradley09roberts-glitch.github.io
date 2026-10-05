package com.squidgame.client.mixin;

import com.squidgame.client.ExperimentalWorldWarning;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft flags every world that has more than the three vanilla dimensions as "experimental" and asks for a confirmation
 * ("Worlds using Experimental Settings are not supported") each time the world is opened. The tournament complex lives in a
 * fourth dimension, so a single-player world would show that scary prompt at every load. When the arena dimension is the only
 * reason for the flag (no experimental feature packs), the prompt is skipped. Optional: if this mixin ever fails to apply the
 * vanilla prompt simply appears again.
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    @Shadow
    protected abstract void openWorldLoadBundledResourcePack(LevelStorageSource.LevelStorageAccess levelStorageAccess,
                                                             WorldStem worldStem, PackRepository packRepository, Runnable runnable);

    @Inject(method = "openWorldCheckWorldStemCompatibility", at = @At("HEAD"), cancellable = true, require = 0)
    private void squidgame$skipArenaDimensionWarning(LevelStorageSource.LevelStorageAccess levelStorageAccess, WorldStem worldStem,
                                                     PackRepository packRepository, Runnable runnable, CallbackInfo ci) {
        if (ExperimentalWorldWarning.onlyBecauseOfArenaDimension(worldStem)) {
            this.openWorldLoadBundledResourcePack(levelStorageAccess, worldStem, packRepository, runnable);
            ci.cancel();
        }
    }
}
