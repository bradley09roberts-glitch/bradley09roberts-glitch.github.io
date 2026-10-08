package io.github.bradley09roberts.hardcorefriends.town;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Notes left for the camp with {@code /friends note <text>}: anyone may leave one, spectators too (a fallen player in
 * Hardcore can still say goodbye). The next time a friend is at camp, awake and not fighting, with a player within
 * {@value #LISTENER_RANGE} blocks, they read it out ("A note from Bob! It says: ..."). The last
 * {@value TownData#MAX_NOTES} notes are kept and listed with {@code /friends notes}.
 */
public final class Notes {
	/** The longest note, in characters. */
	public static final int MAX_LENGTH = 200;
	/** One note per player this often at most, in ticks (half a minute). */
	public static final int NOTE_COOLDOWN = 600;
	/** A player this close to the friend hears the note. */
	private static final double LISTENER_RANGE = 16;
	/** How often the camp looks for someone to read a note to, in ticks. */
	private static final int INTERVAL = 100;

	/** When each player last left a note (game time). Not saved: a restart only forgets the half-minute wait. */
	private static final Map<UUID, Long> LAST_NOTE = new HashMap<>();

	private Notes() {
	}

	/** Leaves a note; returns why not, or null when it was left. */
	static @Nullable String leave(ServerPlayer player, String raw) {
		String text = raw.strip().replaceAll("\\s+", " ");
		if (text.isEmpty()) {
			return "Write something after /friends note.";
		}
		if (text.length() > MAX_LENGTH) {
			return "That note is too long (" + text.length() + " characters). Keep it under " + MAX_LENGTH + ".";
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST_NOTE.get(player.getUUID());
		if (last != null && now - last < NOTE_COOLDOWN && now >= last) {
			return "You left a note just now. Give it a moment before leaving another.";
		}
		LAST_NOTE.put(player.getUUID(), now);
		TownData data = TownData.get(level.getServer());
		data.remember(player);
		data.addNote(new TownData.Note(player.getUUID(), player.getName().getString(), text, Camp.day(level), false));
		return null;
	}

	/** Every few seconds: a friend at camp with a player close by reads out the oldest unread note. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 37) {
			return;
		}
		TownData data = TownData.get(server);
		Optional<TownData.Note> unread = data.firstUnread();
		if (unread.isEmpty()) {
			return;
		}
		CampData camp = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (!Camp.isCampLevel(level, camp)) {
				continue;
			}
			BlockPos centre = camp.campPos().orElseThrow();
			int r = Camp.radius(camp);
			for (CompanionEntity c : Companions.in(level)) {
				if (!readsNow(c, centre, r)) {
					continue;
				}
				for (ServerPlayer player : level.players()) {
					if (player.distanceToSqr(c) <= LISTENER_RANGE * LISTENER_RANGE) {
						TownData.Note note = unread.get();
						note.read = true;
						data.setDirty();
						Speech.say(c, Line.NOTE_FOUND, note.authorName);
						Speech.sayText(c, "\"" + note.text + "\"");
						return;
					}
				}
			}
		}
	}

	/** At camp, on their own, awake, not fighting and not running from anything. */
	private static boolean readsNow(CompanionEntity c, BlockPos centre, int radius) {
		return c.mode() == CompanionMode.WORK && !c.isAsleep() && c.getTarget() == null && !c.isRetreating()
			&& Camp.horizontalDistSqr(c.blockPosition(), centre) <= (double) radius * radius;
	}

	static void clear() {
		LAST_NOTE.clear();
	}
}
