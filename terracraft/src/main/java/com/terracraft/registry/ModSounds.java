package com.terracraft.registry;

import com.terracraft.TerraCraft;

/** ModSounds registry. */
public final class ModSounds {
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, TerraCraft.MODID);

    private ModSounds() {}
}
