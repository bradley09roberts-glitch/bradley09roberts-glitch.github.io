package com.grandline.mixin;

import com.grandline.core.ServerEvents;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Where vanilla hostiles are replaced (the ocean overworld, see {@link ServerEvents#replacesVanillaHostiles}), every
 * natural monster spawn is cancelled when it is finalised, so the monster cap is never reached and the spawner kept
 * picking positions, building zombies/skeletons/creepers and throwing them away in every chunk on every tick. The
 * outcome is the same without those attempts, so the monster category is simply not tried there. Other categories
 * and the custom spawners (patrols, phantoms, sieges) are untouched.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheSpawnMixin {
    @Shadow
    @Final
    public ServerLevel level;

    @ModifyArg(method = "tickChunks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"),
            index = 4)
    private boolean grandline$skipReplacedMonsters(boolean spawnMonsters) {
        return spawnMonsters && !ServerEvents.replacesVanillaHostiles(level);
    }
}
