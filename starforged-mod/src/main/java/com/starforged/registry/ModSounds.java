package com.starforged.registry;

import com.starforged.Starforged;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Starforged.MODID);

    // World events
    public static final RegistryObject<SoundEvent> STARFALL_BEGIN = register("event.starfall_begin");
    public static final RegistryObject<SoundEvent> METEOR_WHOOSH = register("entity.meteor.whoosh");
    public static final RegistryObject<SoundEvent> METEOR_IMPACT = register("entity.meteor.impact");
    public static final RegistryObject<SoundEvent> VAULT_OPEN = register("block.vault_seal.open");
    public static final RegistryObject<SoundEvent> RUNE_TRIGGER = register("block.rune.trigger");
    public static final RegistryObject<SoundEvent> ALTAR_ACTIVATE = register("block.celestial_altar.activate");
    public static final RegistryObject<SoundEvent> CRYSTAL_CHIME = register("block.astral_crystal.chime");

    // Items
    public static final RegistryObject<SoundEvent> STAFF_CAST = register("item.starcaller_staff.cast");
    public static final RegistryObject<SoundEvent> HAMMER_LEAP = register("item.meteor_hammer.leap");
    public static final RegistryObject<SoundEvent> HAMMER_SLAM = register("item.meteor_hammer.slam");
    public static final RegistryObject<SoundEvent> SCYTHE_REAP = register("item.void_scythe.reap");
    public static final RegistryObject<SoundEvent> BOW_STARSHOT = register("item.constellation_bow.shoot");
    public static final RegistryObject<SoundEvent> BLADE_WAVE = register("item.eclipse_blade.wave");
    public static final RegistryObject<SoundEvent> BLADE_ECLIPSE = register("item.eclipse_blade.total_eclipse");
    public static final RegistryObject<SoundEvent> SINGULARITY_HUM = register("entity.singularity.hum");
    public static final RegistryObject<SoundEvent> SINGULARITY_COLLAPSE = register("entity.singularity.collapse");
    public static final RegistryObject<SoundEvent> RIFT_TELEPORT = register("item.rift_pearl.teleport");
    public static final RegistryObject<SoundEvent> GAUNTLET_GRAB = register("item.gravity_gauntlet.grab");
    public static final RegistryObject<SoundEvent> GAUNTLET_THROW = register("item.gravity_gauntlet.throw");
    public static final RegistryObject<SoundEvent> COMPASS_PING = register("item.astral_compass.ping");
    public static final RegistryObject<SoundEvent> COMET_JUMP = register("item.comet_boots.jump");
    public static final RegistryObject<SoundEvent> EGG_HATCH = register("item.astral_egg.hatch");
    public static final RegistryObject<SoundEvent> STARBURST = register("item.starmetal_armor.starburst");

    // Creatures
    public static final RegistryObject<SoundEvent> MITE_CHITTER = register("entity.star_mite.ambient");
    public static final RegistryObject<SoundEvent> MITE_DEATH = register("entity.star_mite.death");
    public static final RegistryObject<SoundEvent> STALKER_WHISPER = register("entity.void_stalker.ambient");
    public static final RegistryObject<SoundEvent> STALKER_TELEPORT = register("entity.void_stalker.teleport");
    public static final RegistryObject<SoundEvent> STALKER_HURT = register("entity.void_stalker.hurt");
    public static final RegistryObject<SoundEvent> STALKER_DEATH = register("entity.void_stalker.death");
    public static final RegistryObject<SoundEvent> GOLEM_SLAM = register("entity.astral_golem.slam");
    public static final RegistryObject<SoundEvent> GOLEM_HURT = register("entity.astral_golem.hurt");
    public static final RegistryObject<SoundEvent> GOLEM_DEATH = register("entity.astral_golem.death");
    public static final RegistryObject<SoundEvent> MIMIC_CHOMP = register("entity.mimic.chomp");
    public static final RegistryObject<SoundEvent> MIMIC_REVEAL = register("entity.mimic.reveal");
    public static final RegistryObject<SoundEvent> WRAITH_AMBIENT = register("entity.astral_wraith.ambient");
    public static final RegistryObject<SoundEvent> WRAITH_CAST = register("entity.astral_wraith.cast");
    public static final RegistryObject<SoundEvent> WRAITH_DEATH = register("entity.astral_wraith.death");
    public static final RegistryObject<SoundEvent> STARLING_CHIRP = register("entity.starling.ambient");
    public static final RegistryObject<SoundEvent> NEBULA_RAY_SONG = register("entity.nebula_ray.ambient");

    // Boss
    public static final RegistryObject<SoundEvent> BOSS_ROAR = register("entity.eclipse_sovereign.roar");
    public static final RegistryObject<SoundEvent> BOSS_HURT = register("entity.eclipse_sovereign.hurt");
    public static final RegistryObject<SoundEvent> BOSS_BEAM_CHARGE = register("entity.eclipse_sovereign.beam_charge");
    public static final RegistryObject<SoundEvent> BOSS_BEAM_FIRE = register("entity.eclipse_sovereign.beam_fire");
    public static final RegistryObject<SoundEvent> BOSS_SLAM = register("entity.eclipse_sovereign.slam");
    public static final RegistryObject<SoundEvent> BOSS_DEATH = register("entity.eclipse_sovereign.death");
    public static final RegistryObject<SoundEvent> BOSS_SUMMON = register("entity.eclipse_sovereign.summon");
    public static final RegistryObject<SoundEvent> CRYSTAL_SHATTER = register("entity.eclipse_crystal.shatter");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Starforged.id(name)));
    }

    private ModSounds() {
    }
}
