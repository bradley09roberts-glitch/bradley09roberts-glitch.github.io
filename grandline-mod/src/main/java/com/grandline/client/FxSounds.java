package com.grandline.client;

import com.grandline.registry.ModSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The mod's own sounds layered onto visual effects: when a Devil Fruit, Haki or boss effect plays on the client
 * (ClientFx), its signature sound plays at the same spot. Many effects are sent again and again while they last
 * (pulses, trails, zones), so each kind sounds at most once per 1.5 s, and lingering zones once per 5 s (a Devil
 * Fruit exists once per world, so per kind is per user).
 */
public final class FxSounds {
    private record S(DeferredHolder<SoundEvent, SoundEvent> sound, float volume, float pitch, int gap) {}

    private static final Map<String, S> MAP = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();

    private static void put(DeferredHolder<SoundEvent, SoundEvent> s, float vol, float pitch, String... kinds) {
        for (String k : kinds) MAP.put(k, new S(s, vol, pitch, 30));
    }

    /** Zones that pulse for many seconds: their sound comes back at most every 5 s. */
    private static void lingering(String... kinds) {
        for (String k : kinds) {
            S s = MAP.get(k);
            if (s != null) MAP.put(k, new S(s.sound(), s.volume(), s.pitch(), 100));
        }
    }

    static {
        // Haki
        put(ModSounds.HAKI_CONQUEROR, 2.5f, 1.0f, "conqueror_burst", "boss_clash", "ryuo_burst");
        put(ModSounds.HAKI_OBSERVATION, 0.9f, 1.0f, "observation_dodge", "future_sight", "boss_foresight_break");
        put(ModSounds.HAKI_ARMAMENT, 1.0f, 1.0f, "steel_body");
        // Devil Fruits, by element
        put(ModSounds.FRUIT_RUBBER, 1.0f, 1.0f, "rubber_arm", "giant_fist", "fusen_inflate", "long_arm");
        put(ModSounds.FRUIT_DRUMS, 1.6f, 1.0f, "nika");
        put(ModSounds.FRUIT_FLAME, 1.2f, 1.0f, "fire_fist", "flame_emperor", "fire_bird", "fire_ring", "blue_flame", "abura_fire",
                "flame_jet", "phoenix_wings", "prometheus_sun", "abura_flare", "kai_breath", "overheat");
        put(ModSounds.FRUIT_FLAME, 1.4f, 0.7f, "magma_fist", "magma_burst", "meteor", "eruption", "meteor_sky", "magma_floor");
        put(ModSounds.FRUIT_THUNDER, 1.4f, 1.0f, "lightning_bolt", "discharge", "black_lightning", "raiju_bolt", "raiju_arc", "tenki_bolt",
                "thunder", "electro_hit", "zeus_cloud", "raiju_zap", "storm_cloud");
        put(ModSounds.FRUIT_ICE, 1.1f, 1.0f, "ice_spear", "ice_wave", "ice_encase", "kiri_freeze", "genbu_freeze", "blizzard", "snow_blade",
                "kamakura", "whiteout", "hai_winter");
        put(ModSounds.FRUIT_DARK, 1.3f, 1.0f, "black_hole", "black_world", "dark_pull", "dark_grasp", "darkness_burst", "shinku_implode");
        put(ModSounds.FRUIT_QUAKE, 1.8f, 1.0f, "quake_cone", "air_crack", "tsunami", "island_tilt", "kaba_quake", "tsuchi_quake");
        put(ModSounds.FRUIT_LIGHT, 1.0f, 1.0f, "light_beam", "light_burst", "light_pillar", "light_sword", "prism_beam", "mirror_beam",
                "yako_flash", "kai_flash");
        put(ModSounds.FRUIT_GAS, 1.0f, 1.0f, "sandstorm", "sand_wave", "smoke_screen", "gas_cloud", "gas_jet", "shinokuni", "kabi_cloud",
                "kiri_fog", "hai_cloud", "smoke_cage", "sand_drain", "gas_explosion");
        put(ModSounds.FRUIT_ROOM, 1.0f, 1.0f, "room");
        put(ModSounds.FRUIT_SHAMBLES, 1.0f, 1.0f, "shambles", "time_vanish", "time_return", "mirror_step");
        put(ModSounds.FRUIT_TRANSFORM, 1.2f, 1.0f, "transform");
        put(ModSounds.FRUIT_SLASH, 1.0f, 1.0f, "wind_blade", "air_blade", "air_blade_big", "crescent", "blade_sweep", "blade_storm", "steel_crescent",
                "wing_crescent", "light_sword", "snow_blade", "room_slash", "chop_festival", "sasori_pincer", "raiju_claw", "claw");
        put(ModSounds.FRUIT_STRING, 1.0f, 1.0f, "string_lines", "birdcage", "string_bullet", "kusari_chain", "kusari_bind", "wana_wire", "hari_pin");
        put(ModSounds.FRUIT_BUBBLE, 1.0f, 1.0f, "bubbles", "paw_bubble", "pain_bubble", "fusen_pop", "kaeru_splash", "mitsu_pool", "shinku_pop");
        put(ModSounds.FRUIT_SQUISH, 1.0f, 1.0f, "mochi_fist", "mochi_bind", "mochi_donut", "mud_grab", "swamp_gulp", "mud_ball", "wax_bind", "nendo_fist",
                "kabi_rot", "abura_slick");
        put(ModSounds.FRUIT_BARRIER, 1.0f, 1.0f, "barrier", "barrier_ball", "mirror_shield", "salt_wall", "mirror_dome", "wax_wall", "shinju_layer",
                "kai_shell", "genbu_shell", "bell_dome");
        // movement arts
        put(ModSounds.COMBAT_SORU, 0.8f, 1.0f, "dash_line", "blur", "vanish");
        put(ModSounds.COMBAT_GEPPO, 0.8f, 1.0f, "sky_walk");
        // bosses
        put(ModSounds.BOSS_ENRAGE, 2.5f, 1.0f, "boss_enrage");
        lingering("birdcage", "mirror_dome", "black_world", "blizzard", "sandstorm", "smoke_screen", "smoke_cage", "gas_cloud", "shinokuni",
                "magma_floor", "whiteout", "mitsu_pool", "eruption", "island_tilt", "black_hole", "storm_cloud", "kiri_fog", "hai_cloud", "kabi_cloud");
    }

    /** Called by ClientFx for every effect; plays the matching sound, if any. */
    public static void play(ClientLevel l, String kind, double x, double y, double z) {
        S s = MAP.get(kind);
        if (s == null) return;
        long now = l.getGameTime();
        Long last = LAST.get(kind);
        if (last != null && now - last < s.gap() && now >= last) return;
        LAST.put(kind, now);
        float pitch = s.pitch() * (0.95f + l.random.nextFloat() * 0.1f);
        l.playLocalSound(x, y, z, s.sound().get(), SoundSource.PLAYERS, s.volume(), pitch, false);
    }

    /** Every effect kind that has a sound (client self-test). */
    public static Map<String, String> table() {
        Map<String, String> out = new HashMap<>();
        MAP.forEach((k, v) -> out.put(k, v.sound().getId().getPath()));
        return out;
    }

    private FxSounds() {}
}
