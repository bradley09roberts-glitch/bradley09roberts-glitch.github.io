package com.grandline.registry;

import com.grandline.GrandLine;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own sound effects and music. Every file is original and synthesised by tools/gen_sounds.py (sounds.json
 * and the .ogg files under assets/grandline/sounds are generated; re-run the script after changing tools/audio).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, GrandLine.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HAKI_CONQUEROR = sfx("haki.conqueror");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAKI_ARMAMENT = sfx("haki.armament");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAKI_OBSERVATION = sfx("haki.observation");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_RUBBER = sfx("fruit.rubber");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_FLAME = sfx("fruit.flame");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_THUNDER = sfx("fruit.thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_ICE = sfx("fruit.ice");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_DARK = sfx("fruit.dark");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_QUAKE = sfx("fruit.quake");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_LIGHT = sfx("fruit.light");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_GAS = sfx("fruit.gas");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_ROOM = sfx("fruit.room");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_SHAMBLES = sfx("fruit.shambles");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_TRANSFORM = sfx("fruit.transform");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_DRUMS = sfx("fruit.drums");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_SLASH = sfx("fruit.slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_STRING = sfx("fruit.string");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_BUBBLE = sfx("fruit.bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_SQUISH = sfx("fruit.squish");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_BARRIER = sfx("fruit.barrier");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_ROAR = sfx("boss.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PHASE = sfx("boss.phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_TELEGRAPH = sfx("boss.telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_ENRAGE = sfx("boss.enrage");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIP_CANNON = sfx("ship.cannon");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIP_BELL = sfx("ship.bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIP_HORN = sfx("ship.horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEA_KING_CALL = sfx("sea_king.call");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEN_DEN_RING = sfx("den_den.ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEN_DEN_PICKUP = sfx("den_den.pickup");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_BOUNTY = sfx("ui.bounty");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_QUEST = sfx("ui.quest");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_DISCOVER = sfx("ui.discover");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_KNOCKDOWN = sfx("player.knockdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_SORU = sfx("combat.soru");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_GEPPO = sfx("combat.geppo");
    // ambience (client/SeaAmbience): looping surf and storm wind, gulls near shore, creaking timbers aboard
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_SEA = sfx("ambient.sea");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_WIND = sfx("ambient.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_GULL = sfx("ambient.gull");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_CREAK = sfx("ambient.creak");

    // music (streamed; picked by client/GLMusic)
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EAST_BLUE = sfx("music.east_blue");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_GRAND_LINE = sfx("music.grand_line");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_NEW_WORLD = sfx("music.new_world");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ISLAND = sfx("music.island");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BATTLE = sfx("music.battle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BOSS = sfx("music.boss");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SKY = sfx("music.sky");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DEEP_SEA = sfx("music.deep_sea");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WANO = sfx("music.wano");
    // mono copies for the music discs (a jukebox is a positional source)
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_EAST_BLUE = sfx("disc.east_blue");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_GRAND_LINE = sfx("disc.grand_line");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_WANO = sfx("disc.wano");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_BOSS = sfx("disc.boss");

    /** Every event, for the self-test and tools/check_assets.py. */
    public static final String[] IDS = {"haki.conqueror", "haki.armament", "haki.observation", "fruit.rubber", "fruit.flame",
            "fruit.thunder", "fruit.ice", "fruit.dark", "fruit.quake", "fruit.light", "fruit.gas", "fruit.room", "fruit.shambles",
            "fruit.transform", "fruit.drums", "boss.roar", "boss.phase", "boss.telegraph", "boss.enrage", "ship.cannon", "ship.bell",
            "ship.horn", "sea_king.call", "den_den.ring", "den_den.pickup", "ui.bounty", "ui.quest", "ui.discover", "player.knockdown",
            "combat.soru", "combat.geppo", "music.east_blue", "music.grand_line", "music.new_world", "music.island", "music.battle",
            "music.boss", "music.sky", "music.deep_sea", "music.wano", "disc.east_blue", "disc.grand_line", "disc.wano", "disc.boss",
            "ambient.sea", "ambient.wind", "ambient.gull", "ambient.creak", "fruit.slash", "fruit.string", "fruit.bubble", "fruit.squish",
            "fruit.barrier"};

    private static DeferredHolder<SoundEvent, SoundEvent> sfx(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(GrandLine.id(name)));
    }

    /** Situational music: replace=true cuts the current track (battles, bosses); delays are in ticks. */
    public static Music music(Holder<SoundEvent> track, int minDelay, int maxDelay, boolean replace) {
        return new Music(track, minDelay, maxDelay, replace);
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }

    private ModSounds() {}
}
