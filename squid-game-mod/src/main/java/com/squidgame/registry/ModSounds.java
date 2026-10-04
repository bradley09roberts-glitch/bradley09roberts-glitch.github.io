package com.squidgame.registry;

import com.squidgame.SquidGameMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** All sound events (ids match docs/ASSET_CONTRACT.md and sounds.json). */
public final class ModSounds {
    private static final Map<String, SoundEvent> ALL = new LinkedHashMap<>();

    private ModSounds() {
    }

    private static SoundEvent reg(String id) {
        ResourceLocation rl = SquidGameMod.id(id);
        SoundEvent e = Registry.register(BuiltInRegistries.SOUND_EVENT, rl, SoundEvent.createVariableRangeEvent(rl));
        ALL.put(id, e);
        return e;
    }

    /** The ten syllables of the doll's chant, in order. */
    public static final SoundEvent[] DOLL_SYLLABLES = new SoundEvent[10];
    static {
        for (int i = 0; i < 10; i++) {
            DOLL_SYLLABLES[i] = reg("doll.syllable_" + (i + 1));
        }
    }

    public static final SoundEvent DOLL_TURN_SERVO = reg("doll.turn_servo");
    public static final SoundEvent DOLL_LOCK_ON = reg("doll.lock_on");
    public static final SoundEvent DOLL_EYES_ON = reg("doll.eyes_on");
    public static final SoundEvent DOLL_EYES_OFF = reg("doll.eyes_off");
    public static final SoundEvent DOLL_SCAN_BEEP = reg("doll.scan_beep");
    public static final SoundEvent ANNOUNCE_CHIME = reg("announce.chime");
    public static final SoundEvent ANNOUNCE_CHIME_ALERT = reg("announce.chime_alert");
    public static final SoundEvent GAME_START_HORN = reg("game.start_horn");
    public static final SoundEvent GAME_END_BUZZER = reg("game.end_buzzer");
    public static final SoundEvent GAME_WIN_FANFARE = reg("game.win_fanfare");
    public static final SoundEvent GAME_RESULTS_STING = reg("game.results_sting");
    public static final SoundEvent COUNTDOWN_TICK = reg("countdown.tick");
    public static final SoundEvent COUNTDOWN_BEEP = reg("countdown.beep");
    public static final SoundEvent COUNTDOWN_FINAL = reg("countdown.final");
    public static final SoundEvent ELIMINATION_CRACK = reg("elimination.crack");
    public static final SoundEvent ELIMINATION_BUZZER = reg("elimination.buzzer");
    public static final SoundEvent ELIMINATION_BODY_FALL = reg("elimination.body_fall");
    public static final SoundEvent DOOR_SLIDE_OPEN = reg("door.slide_open");
    public static final SoundEvent DOOR_SLIDE_CLOSE = reg("door.slide_close");
    public static final SoundEvent DOOR_LOCK = reg("door.lock");
    public static final SoundEvent GLASS_CRACK = reg("glass.crack");
    public static final SoundEvent GLASS_SHATTER = reg("glass.shatter");
    public static final SoundEvent ROPE_CREAK = reg("rope.creak");
    public static final SoundEvent ROPE_STRAIN = reg("rope.strain");
    public static final SoundEvent MARBLE_CLICK = reg("marble.click");
    public static final SoundEvent MARBLE_DROP = reg("marble.drop");
    public static final SoundEvent MARBLE_ROLL = reg("marble.roll");
    public static final SoundEvent NEEDLE_SCRATCH = reg("needle.scratch");
    public static final SoundEvent DALGONA_CRACK = reg("dalgona.crack");
    public static final SoundEvent DALGONA_SNAP = reg("dalgona.snap");
    public static final SoundEvent DANGER_HEARTBEAT = reg("danger.heartbeat");
    public static final SoundEvent DANGER_STING = reg("danger.sting");
    public static final SoundEvent UI_SELECT = reg("ui.select");
    public static final SoundEvent UI_CONFIRM = reg("ui.confirm");
    public static final SoundEvent UI_DENY = reg("ui.deny");
    public static final SoundEvent UI_NUMBER_CALL = reg("ui.number_call");
    public static final SoundEvent AMBIENT_DORM = reg("ambient.dorm");
    public static final SoundEvent AMBIENT_PLAYGROUND = reg("ambient.playground");
    public static final SoundEvent AMBIENT_INDUSTRIAL = reg("ambient.industrial");
    public static final SoundEvent AMBIENT_ALLEY = reg("ambient.alley");
    public static final SoundEvent MUSIC_LOBBY = reg("music.lobby");
    public static final SoundEvent MUSIC_TENSION = reg("music.tension");
    public static final SoundEvent MUSIC_FINAL = reg("music.final");

    public static SoundEvent byId(String id) {
        return ALL.get(id);
    }

    /** Forces class loading (and therefore registration). */
    public static void init() {
    }
}
