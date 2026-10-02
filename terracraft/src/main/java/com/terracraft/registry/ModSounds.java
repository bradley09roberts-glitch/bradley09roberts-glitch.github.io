package com.terracraft.registry;

import com.terracraft.TerraCraft;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** ModSounds registry. */
public final class ModSounds {
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TerraCraft.MODID);

    private ModSounds() {}
}
