package io.github.bradley09roberts.hardcorefriends.life;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Writing up the Village Chronicle in a real book. The keeper (Sage, or a strategist among the newcomers, otherwise
 * whoever has been with the camp longest) goes now and then by day, when there is news, to where the book is kept and
 * brings it up to date: on the lectern in the town hall once the village has one (a lectern from the stores is put on
 * the plan's spot if the builders left it out), until then in the camp's supply chest. The first copy is written in a
 * blank book and quill from the chest (or one made there from a book, an ink sac and a feather): a real item, used up.
 * A book and quill with anything written in it, or a name of its own, is someone's and is never taken. When a volume's
 * hundred pages are full, its last lines are written in, the next volume is begun in a new book and the full one goes
 * to the chest. A book a player takes away is theirs to keep; the keeper writes the volume out again in a new book.
 *
 * <p>Every change to the lectern goes through the edit guard: the lectern is the friends' own block, and showing or
 * clearing its book is changing that block in place (the INVENT rule: the friends' own block, the same block).
 */
final class ChronicleTask implements CompanionTask {
	static final String ID = "life.chronicle";
	/** The custom data key on a Chronicle book: its volume number. */
	static final String MARKER = "hardcorefriends_chronicle";
	private static final double SCORE = 34;
	private static final long FROM = 2000;
	private static final long UNTIL = 11000;

	private static @Nullable UUID keeper;
	private static long keeperAt = Long.MIN_VALUE;
	/** The day no book and quill could be had (tried again the next day). */
	private static long noBookDay = -1;
	/** The day the town hall's lectern spot stood empty with no lectern to be had (the move is tried the next day). */
	private static long noLecternDay = -1;
	/** The town hall's lectern spot, looked up at most every half minute. */
	private static @Nullable BlockPos spot;
	private static long spotAt = Long.MIN_VALUE;

	private enum Step {
		CHEST,
		WALK,
		LECTERN
	}

	private Step step = Step.CHEST;
	private @Nullable BlockPos lectern;
	private @Nullable BlockPos stand;
	private int ticks;

	static void clear() {
		keeper = null;
		keeperAt = Long.MIN_VALUE;
		noBookDay = -1;
		noLecternDay = -1;
		spot = null;
		spotAt = Long.MIN_VALUE;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "writing in the Village Chronicle";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().chronicleBook || c.isChild() || !Places.free(c) || !(c.level() instanceof ServerLevel level)
			|| Camp.isNight(level)) {
			return 0;
		}
		long time = Calendar.time(level.getServer());
		long day = Calendar.today(level.getServer());
		LifeData data = LifeData.get(level.getServer());
		if (time < FROM || time >= UNTIL || data.entries.isEmpty() || !c.getUUID().equals(keeperFor(level))) {
			return 0;
		}
		boolean news = data.bookWritten < data.entries.size();
		boolean noBook = data.bookPlace.isEmpty();
		boolean move = data.bookPlace.equals("chest") && noLecternDay != day && lecternSpot(level) != null;
		if (noBook && noBookDay == day) {
			return 0;
		}
		return news || noBook || move ? SCORE : 0;
	}

	/** The Chronicle's keeper: Sage, else a strategist, else whoever has been with the camp longest. */
	static @Nullable UUID keeperFor(ServerLevel level) {
		long now = level.getGameTime();
		if (now - keeperAt < 1200 && now >= keeperAt && keeper != null && Places.loaded(level, keeper) != null) {
			return keeper;
		}
		keeperAt = now;
		LifeData data = LifeData.get(level.getServer());
		CompanionEntity best = null;
		long bestRank = Long.MAX_VALUE;
		for (CompanionEntity c : Places.freePeople(level)) {
			if (c.isChild()) {
				continue;
			}
			long joined = data.person(c.getUUID()).map(p -> p.joined).orElse(Long.MAX_VALUE / 4);
			long rank = c.friendId() == FriendId.SAGE && !c.isSettler() ? Long.MIN_VALUE
				: c.friendId().role() == Role.STRATEGIST ? Long.MIN_VALUE / 2 + joined : joined;
			if (rank < bestRank) {
				best = c;
				bestRank = rank;
			}
		}
		keeper = best == null ? null : best.getUUID();
		return keeper;
	}

	/**
	 * The town hall's lectern spot, if a town hall stands: the plan's lectern marker, holding the friends' own lectern or
	 * still empty (for a lectern from the stores). A lectern with someone else's book on it is not one: the Chronicle
	 * stays where it is until the book is taken off again.
	 */
	static @Nullable BlockPos lecternSpot(ServerLevel level) {
		long now = level.getGameTime();
		if (now - spotAt < 600 && now >= spotAt) {
			return spot;
		}
		spotAt = now;
		spot = findLecternSpot(level);
		return spot;
	}

	private static @Nullable BlockPos findLecternSpot(ServerLevel level) {
		CampData camp = Camp.data(level.getServer());
		for (VillagePlan.Building hall : VillagePlan.buildingsOfKind(level.getServer(), "civic:town_hall")) {
			for (BlockPos p : hall.marker("lectern")) {
				if (!level.isLoaded(p)) {
					continue;
				}
				BlockState s = level.getBlockState(p);
				if (s.is(Blocks.LECTERN) && camp.isPlacedByFriends(level, p)) {
					if (s.getValue(LecternBlock.HAS_BOOK) && level.getBlockEntity(p) instanceof LecternBlockEntity desk
						&& volumeOf(desk.getBook()) <= 0) {
						continue; // a player's own book lies there
					}
					return p;
				}
				if (s.isAir()) {
					return p;
				}
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		lectern = lecternSpot(level);
		LifeData data = LifeData.get(level.getServer());
		BlockPos kept = data.bookPlace.equals("lectern") && data.bookDimension.equals(Camp.dimensionId(level)) ? data.bookPos
			: null;
		if (lectern != null && !level.isLoaded(lectern) || kept != null && !level.isLoaded(kept)) {
			// The town hall is out of the loaded world just now: another time, rather than loading it (or writing a
			// second copy of a book that is still lying there).
			return false;
		}
		ticks = 0;
		stand = null;
		step = needsChest(c, level) ? Step.CHEST : Step.WALK;
		if (step == Step.WALK && lectern == null) {
			step = Step.CHEST;
		}
		return true;
	}

	/**
	 * True when the keeper must go to the chest first: no lectern, no book yet (or it was taken from the lectern), the
	 * book is in the chest, or a new volume needs a new book.
	 */
	private boolean needsChest(CompanionEntity c, ServerLevel level) {
		LifeData data = LifeData.get(level.getServer());
		BlockPos at = lectern;
		if (at == null || !data.bookPlace.equals("lectern") || full(data)) {
			return true;
		}
		BlockState state = level.getBlockState(at);
		boolean ours = state.is(Blocks.LECTERN) && state.getValue(LecternBlock.HAS_BOOK)
			&& level.getBlockEntity(at) instanceof LecternBlockEntity desk && volumeOf(desk.getBook()) == data.volume;
		return !ours && !hasOwnBook(c, data.volume);
	}

	/** True when lines belong to a volume after the one being kept: the book is full, and the next one is begun. */
	private static boolean full(LifeData data) {
		return !data.entries.isEmpty() && data.entries.getLast().volume() > data.volume;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (++ticks % 20 == 0 && Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE)) {
			return TaskStatus.FAILURE;
		}
		return switch (step) {
			case CHEST -> atChest(c, level);
			case WALK -> walk(c, level);
			case LECTERN -> atLectern(c, level);
		};
	}

	/**
	 * At the supply chest: a full volume stays there (and the next is begun), the current volume is taken out to go to
	 * the lectern, a book and quill (or what makes one) is taken for a new copy, and a lectern for an empty spot. With no
	 * lectern to go to, the book is written and kept right here.
	 */
	private TaskStatus atChest(CompanionEntity c, ServerLevel level) {
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
		if (chest == null || lectern != null && !level.isLoaded(lectern)) {
			return TaskStatus.FAILURE; // (the town hall went out of the loaded world meanwhile: another time)
		}
		LifeData data = LifeData.get(level.getServer());
		if (full(data) && data.bookPlace.equals("chest")) {
			int old = slotIn(chest, data.volume);
			if (old >= 0) {
				write(chest.getItem(old), data, c); // its last lines go in first
				chest.setChanged();
			}
			nextVolume(data); // the full one stays in the chest, among the camp's keepsakes
		}
		int slot = slotIn(chest, data.volume);
		boolean toLectern = lectern != null && (level.getBlockState(lectern).is(Blocks.LECTERN) || haveLectern(c, chest));
		if (lectern != null && !toLectern) {
			noLecternDay = Calendar.today(level.getServer()); // the spot stays empty for today: no lectern in the stores
		}
		if (slot >= 0 && toLectern && !full(data)) {
			ItemStack book = chest.getItem(slot).copy();
			if (!c.backpack().canFit(book)) {
				return TaskStatus.FAILURE;
			}
			chest.setItem(slot, ItemStack.EMPTY);
			chest.setChanged();
			c.backpack().insert(book);
			data.bookPlace = "";
			data.setDirty();
		} else if (slot < 0 && !hasOwnBook(c, data.volume) && !getBookAndQuill(c, chest, level)) {
			return TaskStatus.FAILURE;
		}
		if (!toLectern) {
			return writeInChest(c, level, chest, data);
		}
		step = Step.WALK;
		return TaskStatus.RUNNING;
	}

	/** A lectern for the town hall's empty spot: one from the chest. */
	private boolean haveLectern(CompanionEntity c, Container chest) {
		if (c.backpack().has(s -> s.is(Items.LECTERN))) {
			return true;
		}
		return SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.LECTERN), 1) > 0;
	}

	/**
	 * A blank book and quill: from the chest, or made from a book (or paper and leather), an ink sac and a feather.
	 */
	private static boolean getBookAndQuill(CompanionEntity c, Container chest, ServerLevel level) {
		if (c.backpack().has(ChronicleTask::blank) || SupplyChest.withdraw(chest, c.backpack(), ChronicleTask::blank, 1) > 0) {
			return true;
		}
		boolean book = SupplyChest.count(chest, s -> s.is(Items.BOOK)) > 0
			|| SupplyChest.count(chest, s -> s.is(Items.PAPER)) >= 3 && SupplyChest.count(chest, s -> s.is(Items.LEATHER)) >= 1;
		if (!book || SupplyChest.count(chest, s -> s.is(Items.INK_SAC)) < 1 || SupplyChest.count(chest, s -> s.is(Items.FEATHER)) < 1) {
			noBookDay = Calendar.today(level.getServer());
			return false;
		}
		if (SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.BOOK), 1) == 0) {
			SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.PAPER), 3);
			SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.LEATHER), 1);
		}
		SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.INK_SAC), 1);
		SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.FEATHER), 1);
		// One more than they carry: a written-in book and quill on them does not count.
		if (Crafting.ensure(c, Items.WRITABLE_BOOK, c.backpack().count(Items.WRITABLE_BOOK) + 1)) {
			return true;
		}
		// Could not be made after all: everything goes back.
		SupplyChest.deposit(c.backpack(), chest, s -> s.is(Items.BOOK) || s.is(Items.PAPER) || s.is(Items.LEATHER) || s.is(Items.INK_SAC)
			|| s.is(Items.FEATHER), 8);
		noBookDay = Calendar.today(level.getServer());
		return false;
	}

	/** No town hall lectern: the current volume is written (or brought up to date) and kept in the supply chest. */
	private TaskStatus writeInChest(CompanionEntity c, ServerLevel level, Container chest, LifeData data) {
		int slot = slotIn(chest, data.volume);
		if (slot >= 0) {
			write(chest.getItem(slot), data, c);
			chest.setChanged();
		} else {
			ItemStack book = takeOwnBook(c, data.volume);
			if (book.isEmpty()) {
				book = newBook(c, data);
			}
			if (book.isEmpty()) {
				return TaskStatus.FAILURE;
			}
			write(book, data, c);
			ItemStack left = SupplyChest.insert(chest, book);
			if (!left.isEmpty()) {
				c.backpack().insert(left); // the chest is full: kept on them for now, written up
				return TaskStatus.FAILURE;
			}
		}
		kept(data, "chest", Camp.data(level.getServer()).chestPos().orElse(null), level);
		Speech.say(c, Line.CHRONICLE_WRITING);
		return TaskStatus.SUCCESS;
	}

	private TaskStatus walk(CompanionEntity c, ServerLevel level) {
		BlockPos at = lectern;
		if (at == null) {
			return TaskStatus.FAILURE;
		}
		if (stand == null) {
			stand = Places.standableNear(level, at, 2);
			if (stand == null) {
				return TaskStatus.FAILURE;
			}
		}
		if (c.actions().canReach(at) && c.position().distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 9) {
			c.actions().stopWalking();
			step = Step.LECTERN;
			ticks = 0;
			return TaskStatus.RUNNING;
		}
		if (c.actions().walkTo(stand, 1.0)) {
			step = Step.LECTERN;
			ticks = 0;
			return TaskStatus.RUNNING;
		}
		return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
	}

	/**
	 * At the lectern, a step every half second: put the lectern up if the spot is empty; take a full volume off (it
	 * goes to the chest with the next tidying); put the current volume on, written up; or bring the one there up to date.
	 */
	private TaskStatus atLectern(CompanionEntity c, ServerLevel level) {
		BlockPos at = lectern;
		if (at == null || !c.actions().canReach(at)) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5);
		if (ticks % 10 != 0) {
			return TaskStatus.RUNNING;
		}
		if (ticks > 20 * 10) {
			return TaskStatus.FAILURE; // the guard kept refusing (someone standing at it, most likely)
		}
		LifeData data = LifeData.get(level.getServer());
		BlockState state = level.getBlockState(at);
		if (state.isAir()) {
			BlockState lecternState = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing(level, at));
			if (c.actions().place(at, lecternState, s -> s.is(Items.LECTERN), WorldEditGuard.Reason.BUILD)) {
				// Recorded even when the record of placed blocks is full: the keeper must know it as the friends' own.
				Camp.data(level.getServer()).keepPlaced(level, at, lecternState);
			}
			return TaskStatus.RUNNING;
		}
		if (!state.is(Blocks.LECTERN) || !Camp.data(level.getServer()).isPlacedByFriends(level, at)
			|| !(level.getBlockEntity(at) instanceof LecternBlockEntity desk)) {
			return TaskStatus.FAILURE;
		}
		if (state.getValue(LecternBlock.HAS_BOOK)) {
			ItemStack there = desk.getBook();
			int vol = volumeOf(there);
			if (vol <= 0) {
				return TaskStatus.FAILURE; // someone else's book lies there: leave it be
			}
			if (vol == data.volume && !full(data)) {
				write(there, data, c);
				desk.setBook(there);
				kept(data, "lectern", at, level);
				Speech.say(c, Line.CHRONICLE_WRITING);
				return TaskStatus.SUCCESS;
			}
			// A full (or an old) volume: off the lectern into the backpack, from where it goes to the chest.
			if (!c.backpack().canFit(there) || !WorldEditGuard.transformBlock(c, at, state.setValue(LecternBlock.HAS_BOOK, false),
				WorldEditGuard.Reason.INVENT)) {
				return TaskStatus.RUNNING;
			}
			if (vol == data.volume) {
				write(there, data, c); // the full volume's last lines go in before it is put away
			}
			c.backpack().insert(there.copy());
			desk.setBook(ItemStack.EMPTY);
			if (vol == data.volume) {
				nextVolume(data);
			}
			data.bookPlace = "";
			data.setDirty();
			return TaskStatus.RUNNING;
		}
		ItemStack book = takeOwnBook(c, data.volume);
		if (book.isEmpty()) {
			book = newBook(c, data);
		}
		if (book.isEmpty()) {
			return TaskStatus.FAILURE; // nothing to write in: back to the chest another time
		}
		write(book, data, c);
		if (!WorldEditGuard.transformBlock(c, at, state.setValue(LecternBlock.HAS_BOOK, true), WorldEditGuard.Reason.INVENT)) {
			c.backpack().insert(book);
			return TaskStatus.RUNNING;
		}
		if (level.getBlockEntity(at) instanceof LecternBlockEntity placed) {
			placed.setBook(book);
		}
		kept(data, "lectern", at, level);
		Speech.say(c, Line.CHRONICLE_WRITING);
		return TaskStatus.SUCCESS;
	}

	/** The way a new lectern faces: into the hall, towards its middle (the plan's {@code inside} or {@code table} spot). */
	private static Direction facing(ServerLevel level, BlockPos at) {
		for (VillagePlan.Building hall : VillagePlan.buildingsOfKind(level.getServer(), "civic:town_hall")) {
			if (hall.marker("lectern").contains(at)) {
				BlockPos towards = hall.first("table").or(() -> hall.first("inside")).orElse(null);
				if (towards != null && !towards.equals(at)) {
					return SiteFinder.directionTo(at, towards);
				}
			}
		}
		return Direction.NORTH;
	}

	// ----------------------------------------------------------------- books

	/** Begins the next volume: its lines are those written since the last one filled. */
	private static void nextVolume(LifeData data) {
		data.volume++;
		data.bookPlace = "";
		data.bookPos = null;
		data.setDirty();
	}

	/** Where the current volume is now kept, and that it is written up (to the end of its volume). */
	private static void kept(LifeData data, String place, @Nullable BlockPos pos, ServerLevel level) {
		data.bookPlace = place;
		data.bookPos = pos;
		data.bookDimension = Camp.dimensionId(level);
		int written = 0;
		for (LifeData.Entry e : data.entries) {
			if (e.volume() <= data.volume) {
				written++;
			}
		}
		data.bookWritten = written;
		data.setDirty();
	}

	/** A new written book for the current volume, made from a blank book and quill out of the backpack (used up); or empty. */
	private static ItemStack newBook(CompanionEntity c, LifeData data) {
		if (c.backpack().remove(ChronicleTask::blank, 1) != 1) {
			return ItemStack.EMPTY;
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		CompoundTag tag = new CompoundTag();
		tag.putInt(MARKER, data.volume);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	/** Writes the current volume's pages into a Chronicle book (its author stays whoever first wrote it). */
	private static void write(ItemStack book, LifeData data, CompanionEntity c) {
		int volume = volumeOf(book) > 0 ? volumeOf(book) : data.volume;
		List<LifeData.Entry> lines = Chronicle.volume(data, volume);
		WrittenBookContent old = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String author = old != null && !old.author().isBlank() ? old.author() : c.displayName();
		List<Filterable<Component>> pages = Chronicle.pages(lines, volume);
		String title = "Village Chronicle " + Chronicle.roman(volume);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, pages, true));
	}

	/** The volume number of a Chronicle book, or 0 for any other item. */
	static int volumeOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK)) {
			return 0;
		}
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(MARKER, 0);
	}

	/** The slot of a volume in a container, or -1. */
	private static int slotIn(Container container, int volume) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (volumeOf(container.getItem(i)) == volume) {
				return i;
			}
		}
		return -1;
	}

	private static boolean hasOwnBook(CompanionEntity c, int volume) {
		return c.backpack().has(s -> volumeOf(s) == volume) || c.backpack().has(ChronicleTask::blank);
	}

	/**
	 * A book and quill nobody has written in and nobody has named: the only kind the Chronicle is written in. Players
	 * keep their notes in book and quills in the chest, and those are never used up.
	 */
	private static boolean blank(ItemStack s) {
		return s.is(Items.WRITABLE_BOOK) && !s.has(DataComponents.CUSTOM_NAME)
			&& s.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).pages().stream()
				.allMatch(page -> page.raw().isBlank());
	}

	/** Takes the current volume out of the backpack, if carried. */
	private static ItemStack takeOwnBook(CompanionEntity c, int volume) {
		return c.backpack().take(s -> volumeOf(s) == volume, 1);
	}

	@Override
	public void stop(CompanionEntity c) {
		lectern = null;
		stand = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60 * 5;
	}

	@Override
	public int successCooldown() {
		return 20 * 60 * 5;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
