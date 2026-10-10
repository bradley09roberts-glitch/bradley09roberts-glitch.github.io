package io.github.bradley09roberts.hardcorefriends.life;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.people.Skins;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Music in the evenings: the village's musician (someone wearing a bard's clothes, else the innkeeper, else a
 * redstone tinkerer, else the chattiest) plays tunes on a note block, at every feast and on every other evening, in the
 * tavern if the village has one, otherwise at the square. They bring a note block from the supply chest (or make one
 * there from eight planks and a redstone dust at the crafting table), put it down beside them through the edit guard
 * (their own block, recorded), play a few tunes, and take it back up after. The notes are the note block's own sounds
 * (its instrument from the block it stands on), played straight to everyone nearby with the floating notes, without
 * tuning the block for every note. Everyone within earshot enjoys it (fun). A note block left behind (the musician
 * called away mid-tune, or lost) is taken down later by {@link TidyUpTask}. On the evening of a feast or a funeral there
 * is no ordinary evening music beforehand: the musician plays at the feast, and not at all at a funeral.
 */
final class MusicTask implements CompanionTask {
	static final String ID = "life.music";
	private static final double FEAST_SCORE = 85;
	/** Enough to take the musician from standing in the feast's ring ({@link GatherTask}) to playing for it. */
	private static final double FEAST_TAKE_OVER = GatherTask.FEAST + TaskScheduler.PREEMPT_MARGIN;
	private static final double EVENING_SCORE = 50;
	private static final long EVENING_FROM = 9000;
	private static final long EVENING_UNTIL = 12000;
	private static final int EVENING_TUNES = 3;
	private static final int FEAST_TUNES = 6;
	private static final int PAUSE = 40;
	private static final double EARSHOT = 16;
	/** How long a note block may stand before the tidying may take it down, if its musician has gone. */
	private static final long LEAVE_FOR = 20 * 60 * 5;

	/** Note blocks being played just now, and by whom (the tidying leaves these alone while their musician plays on). */
	private static final Map<BlockPos, UUID> IN_USE = new HashMap<>();
	/** The evening's musician, chosen now and then. */
	private static @Nullable UUID musician;
	private static long chosenAt = Long.MIN_VALUE;
	/** The day music was played (ordinary evenings: once), and the day no note block could be had. */
	private static long playedDay = -1;
	private static long noBlockDay = -1;
	/** Failed tries at putting the note block down today. */
	private static int placeFails;
	private static long placeFailDay = -1;

	private enum Step {
		FETCH,
		WALK,
		PLACE,
		PLAY,
		TAKE_DOWN
	}

	private Step step = Step.FETCH;
	private @Nullable BlockPos stand;
	private @Nullable BlockPos block;
	private boolean atFeast;
	private int ticks;
	private int stepTicks;
	private Tunes.@Nullable Tune tune;
	private int note;
	private long nextNoteAt;
	private int tunesPlayed;
	private NoteBlockInstrument instrument = NoteBlockInstrument.HARP;

	static void clear() {
		IN_USE.clear();
		musician = null;
		chosenAt = Long.MIN_VALUE;
		playedDay = -1;
		noBlockDay = -1;
		placeFails = 0;
		placeFailDay = -1;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Tunes.Tune t = tune;
		return step == Step.PLAY && t != null ? "playing " + t.name() : "playing music";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().villageLife || c.isChild() || !Places.free(c) || !(c.level() instanceof ServerLevel level)
			|| Camp.isNight(level) || c.getTarget() != null) {
			return 0;
		}
		long day = Calendar.today(level.getServer());
		if (noBlockDay == day || !c.getUUID().equals(musicianFor(level))) {
			return 0;
		}
		Gatherings.Gathering g = Gatherings.activeFor(c);
		if (g != null) {
			if (g.kind != Gatherings.Kind.FEAST) {
				return 0; // no music at a funeral
			}
			CompanionTask doing = c.scheduler().current();
			return doing != null && GatherTask.ID.equals(doing.id()) ? FEAST_TAKE_OVER : FEAST_SCORE;
		}
		long time = Calendar.time(level.getServer());
		if (playedDay == day || !Calendar.musicEvening(day) || time < EVENING_FROM || time >= EVENING_UNTIL
			|| gatheringAhead(level, day, time)) {
			return 0;
		}
		return EVENING_SCORE;
	}

	/**
	 * True if this evening has a feast still to begin (the music waits and is played at the feast) or a funeral to hold
	 * (no music that evening): ordinary evening music would only be broken off for it, its note block left behind.
	 */
	private static boolean gatheringAhead(ServerLevel level, long day, long time) {
		LifeData data = LifeData.get(level.getServer());
		if (Calendar.feastOn(day) != null && time < Gatherings.FEAST_LATEST_START && !data.isDone("feast:" + day)) {
			return true;
		}
		for (LifeData.Funeral f : data.funerals) {
			if (f.day <= day) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The village's musician tonight: a grown-up about the camp wearing a bard's clothes first, then the innkeeper,
	 * then a redstone tinkerer (note blocks are their kind of thing), then the chattiest. Chosen at most once a minute.
	 */
	static @Nullable UUID musicianFor(ServerLevel level) {
		long now = level.getGameTime();
		if (now - chosenAt < 1200 && now >= chosenAt && (musician == null || Places.loaded(level, musician) != null)) {
			return musician;
		}
		chosenAt = now;
		CompanionEntity best = null;
		double bestRank = Double.MAX_VALUE;
		for (CompanionEntity c : Places.freePeople(level)) {
			if (c.isChild()) {
				continue;
			}
			double rank;
			if (Skins.get(c.getSkinId()).map(s -> s.tags().contains("bard")).orElse(false)) {
				rank = 0;
			} else if (Professions.get().professionOf(c).map("innkeeper"::equals).orElse(false)) {
				rank = 1;
			} else if (c.friendId().role() == Role.INVENTOR) {
				rank = 2;
			} else {
				rank = 4 - c.friendId().chattiness();
			}
			if (rank < bestRank) {
				best = c;
				bestRank = rank;
			}
		}
		musician = best == null ? null : best.getUUID();
		return musician;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Gatherings.Gathering g = Gatherings.activeFor(c);
		atFeast = g != null && g.kind == Gatherings.Kind.FEAST;
		stand = null;
		if (atFeast) {
			stand = Places.ringSpot(level, g.centre, 0, 4, 3, false, 0, 0);
		} else {
			stand = Places.tavern(level);
			if (stand == null && Places.square(level) != null) {
				stand = Places.ringSpot(level, Places.square(level), c.getRandom().nextInt(8), 8, 4, false, 0, 0);
			}
		}
		if (stand == null) {
			return false;
		}
		block = null;
		ticks = 0;
		stepTicks = 0;
		tune = null;
		tunesPlayed = 0;
		step = c.backpack().has(s -> s.is(Items.NOTE_BLOCK)) ? Step.WALK : Step.FETCH;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ticks++;
		stepTicks++;
		if (ticks % 20 == 0 && step != Step.TAKE_DOWN
			&& (Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE) || Camp.isNightTime(level) || !Places.free(c))) {
			step = Step.TAKE_DOWN; // danger, nightfall or their watch: the music stops
			stepTicks = 0;
		}
		if (atFeast && Gatherings.activeFor(c) == null && step != Step.TAKE_DOWN) {
			step = Step.TAKE_DOWN; // the feast is over
			stepTicks = 0;
		}
		return switch (step) {
			case FETCH -> fetch(c, level);
			case WALK -> walk(c);
			case PLACE -> place(c, level);
			case PLAY -> play(c, level);
			case TAKE_DOWN -> takeDownOrLeave(c, level);
		};
	}

	/** A note block from the chest, or eight planks and a redstone dust to make one at the crafting table. */
	private TaskStatus fetch(CompanionEntity c, ServerLevel level) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			default -> {
			}
		}
		Container chest = SupplyChest.of(level).orElse(null);
		long day = Calendar.today(level.getServer());
		if (chest != null && SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.NOTE_BLOCK), 1) == 0
			&& Crafting.nearCraftingTable(c) && SupplyChest.count(chest, s -> s.is(ItemTags.PLANKS)) >= 32
			&& SupplyChest.count(chest, s -> s.is(Items.REDSTONE)) >= 1) {
			SupplyChest.withdraw(chest, c.backpack(), s -> s.is(ItemTags.PLANKS), 8);
			SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.REDSTONE), 1);
			if (!Crafting.ensure(c, Items.NOTE_BLOCK, 1)) {
				SupplyChest.deposit(c.backpack(), chest, s -> s.is(Items.REDSTONE) || s.is(ItemTags.PLANKS), 9);
			}
		}
		if (!c.backpack().has(s -> s.is(Items.NOTE_BLOCK))) {
			noBlockDay = day; // no note block to be had today
			return TaskStatus.FAILURE;
		}
		step = Step.WALK;
		stepTicks = 0;
		return TaskStatus.RUNNING;
	}

	private TaskStatus walk(CompanionEntity c) {
		BlockPos to = stand;
		if (to == null) {
			return TaskStatus.FAILURE;
		}
		if (c.actions().walkTo(to, 1.0)) {
			step = Step.PLACE;
			stepTicks = 0;
			return TaskStatus.RUNNING;
		}
		return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
	}

	/** The note block goes down on a free spot beside the musician, on firm ground. */
	private TaskStatus place(CompanionEntity c, ServerLevel level) {
		if (stepTicks % 10 != 0) {
			return TaskStatus.RUNNING;
		}
		if (stepTicks > 20 * 5) {
			long day = Calendar.today(level.getServer());
			if (placeFailDay != day) {
				placeFailDay = day;
				placeFails = 0;
			}
			if (++placeFails >= 3) {
				noBlockDay = day; // nowhere to put it down, three times: not tonight
			}
			return TaskStatus.FAILURE;
		}
		BlockPos here = c.blockPosition();
		CampData camp = Camp.data(level.getServer());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = here.relative(d);
			BlockState at = level.getBlockState(p);
			if (!at.isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
				|| level.getBlockState(p.below()).is(Blocks.CAMPFIRE) || !level.getBlockState(p.above()).isAir()
				|| WorldEditGuard.looksPlayerBuilt(level, p, 1, camp)) {
				continue;
			}
			NoteBlockInstrument below = level.getBlockState(p.below()).instrument();
			NoteBlockInstrument chosen = below.worksAboveNoteBlock() || !below.isTunable() ? NoteBlockInstrument.HARP : below;
			BlockState state = Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.INSTRUMENT, chosen);
			if (c.actions().place(p, state, s -> s.is(Items.NOTE_BLOCK), WorldEditGuard.Reason.BUILD)) {
				camp.keepPlaced(level, p, state); // even when the record of placed blocks is full: it must come down again
				block = p.immutable();
				instrument = chosen;
				IN_USE.put(block, c.getUUID());
				long clock = level.getServer().overworld().getOverworldClockTime();
				LifeData.get(level.getServer()).addTemp(Camp.dimensionId(level), block, Blocks.NOTE_BLOCK, clock + LEAVE_FOR);
				Speech.say(c, Line.MUSIC_PLAY);
				step = Step.PLAY;
				stepTicks = 0;
				nextNoteAt = level.getGameTime() + 20;
				return TaskStatus.RUNNING;
			}
		}
		return TaskStatus.RUNNING;
	}

	/** One note at a time, tune after tune with a short pause between, until the set (or the feast) is over. */
	private TaskStatus play(CompanionEntity c, ServerLevel level) {
		BlockPos at = block;
		if (at == null || !level.getBlockState(at).is(Blocks.NOTE_BLOCK)) {
			step = Step.TAKE_DOWN;
			return TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
		long now = level.getGameTime();
		if (stepTicks % 20 == 0) {
			cheer(c, level, at);
		}
		if (now < nextNoteAt) {
			return TaskStatus.RUNNING;
		}
		Tunes.Tune t = tune;
		if (t == null || note >= t.notes().length) {
			if (t != null) {
				tunesPlayed++;
			}
			if (tunesPlayed >= (atFeast ? FEAST_TUNES : EVENING_TUNES)) {
				step = Step.TAKE_DOWN;
				stepTicks = 0;
				return TaskStatus.RUNNING;
			}
			tune = Tunes.pick(c.getRandom());
			note = 0;
			nextNoteAt = now + (t == null ? 0 : PAUSE);
			return TaskStatus.RUNNING;
		}
		int[] n = t.notes()[note++];
		int pitchNote = Math.clamp(n[0], 0, 24);
		level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, instrument.getSoundEvent(), SoundSource.RECORDS, 3.0F,
			NoteBlock.getPitchFromNote(pitchNote));
		level.sendParticles(ParticleTypes.NOTE, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0, pitchNote / 24.0, 0.0, 0.0, 1.0);
		if (n[1] >= 2) {
			c.swingArm();
		}
		nextNoteAt = now + (long) Math.max(1, n[1]) * Tunes.HALF_BEAT;
		return TaskStatus.RUNNING;
	}

	/** Everyone within earshot enjoys the music. */
	private static void cheer(CompanionEntity c, ServerLevel level, BlockPos at) {
		for (CompanionEntity other : Companions.in(level)) {
			if (other.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= EARSHOT * EARSHOT && !other.isAsleep()) {
				other.needs().add(Needs.Need.FUN, other == c ? 0.3 : 0.2);
			}
		}
	}

	/** Picks the note block back up (it goes into the backpack), or leaves it for the tidying if that is refused. */
	private TaskStatus takeDownOrLeave(CompanionEntity c, ServerLevel level) {
		BlockPos at = block;
		if (at == null) {
			return finish(level);
		}
		if (!level.getBlockState(at).is(Blocks.NOTE_BLOCK) || !Camp.data(level.getServer()).isPlacedByFriends(level, at)) {
			forget(level, at); // already gone
			return finish(level);
		}
		if (stepTicks % 5 == 0 && c.actions().canReach(at) && WorldEditGuard.breakBlock(c, at, WorldEditGuard.Reason.BUILD)) {
			c.swingArm();
			forget(level, at);
			return finish(level);
		}
		if (stepTicks > 20 * 4 || !c.actions().canReach(at)) {
			IN_USE.remove(at); // left for the tidying
			block = null;
			return finish(level);
		}
		return TaskStatus.RUNNING;
	}

	/**
	 * True while the note block at {@code pos} is being played: its musician is about and still at their music. One
	 * whose musician died, or went out of reach of the world mid-tune, is let go here for the tidying.
	 */
	static boolean inUse(ServerLevel level, BlockPos pos) {
		UUID who = IN_USE.get(pos);
		if (who == null) {
			return false;
		}
		CompanionEntity c = Places.loaded(level, who);
		CompanionTask doing = c == null ? null : c.scheduler().current();
		if (doing != null && ID.equals(doing.id())) {
			return true;
		}
		IN_USE.remove(pos);
		return false;
	}

	private static void forget(ServerLevel level, BlockPos at) {
		IN_USE.remove(at);
		LifeData data = LifeData.get(level.getServer());
		String dim = Camp.dimensionId(level);
		data.temps.removeIf(t -> t.pos.equals(at) && t.dimension.equals(dim));
		data.setDirty();
	}

	private TaskStatus finish(ServerLevel level) {
		block = null;
		if (tunesPlayed > 0 && !atFeast) {
			playedDay = Calendar.today(level.getServer());
		}
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (block != null) {
			IN_USE.remove(block); // called away: the tidying takes it down later
		}
		block = null;
		stand = null;
		tune = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 60 * 3;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 5;
	}
}
