package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;

/**
 * A delivery to a player's mailbox ({@link Mail}): by day, a friend packs the delivery at the supply chest, walks to
 * the mailbox (the land around them kept running by survival's roaming loader when it lies beyond the camp's area),
 * puts the things into that container and nothing else, and comes home, bringing back whatever did not fit. Anyone may
 * go; Rowan (and newcomer foragers) are keenest. One delivery at a time. Hurt, hungry, tired, a storm or the evening
 * drawing in send them home early, with the delivery; a mailbox that is gone or out of reach is let go for the day.
 */
final class MailTripTask implements CompanionTask {
	static final String ID = "town.mail";
	/** The delivery in progress is kept with the friend, under this key of their extra. */
	private static final String MANIFEST = "town.mail";
	/** Time set aside at the mailbox when working out whether there is daylight enough, in ticks. */
	private static final long DROP_TICKS = 600;
	/** The longest spent trying to reach the mailbox once there, in ticks. */
	private static final int MAX_DROP_TICKS = 400;
	private static final String PACK = "pack";
	private static final String OUT = "out";
	private static final String DROP = "drop";
	private static final String HOME = "home";
	private static final String UNPACK = "unpack";

	/** One kind of item in a delivery: how many were wanted, packed and left in the mailbox. */
	private static final class Entry {
		final Identifier item;
		final int wanted;
		int packed;
		int delivered;

		Entry(Identifier item, int wanted, int packed, int delivered) {
			this.item = item;
			this.wanted = wanted;
			this.packed = packed;
			this.delivered = delivered;
		}
	}

	/** What is being delivered, to whom, and the request it fills (if any), saved with the friend. */
	private static final class Manifest {
		UUID player;
		String name;
		boolean requested;
		long askedAt;
		final List<Entry> entries = new ArrayList<>();

		Manifest(UUID player, String name, boolean requested, long askedAt) {
			this.player = player;
			this.name = name;
			this.requested = requested;
			this.askedAt = askedAt;
		}
	}

	private final Trips.Walker walker = new Trips.Walker();
	private Trips.@Nullable State trip;
	private @Nullable Manifest manifest;
	private int phaseTicks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Trips.State t = trip;
		Manifest m = manifest;
		String whose = m == null ? "a player" : m.name;
		if (t == null) {
			return "taking a delivery to " + whose;
		}
		return switch (t.phase) {
			case PACK -> "packing a delivery for " + whose;
			case DROP -> "leaving a delivery in " + whose + "'s mailbox";
			case HOME -> "heading home from a delivery";
			case UNPACK -> "putting back what was not delivered";
			default -> "on the way to " + whose + "'s mailbox at " + Compass.coords(t.destination);
		};
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		Trips.State saved = Trips.state(c);
		if (saved != null) {
			if (!saved.job.equals(ID)) {
				return 0;
			}
			if (Trips.expired(c, saved)) {
				Trips.release(ID, c);
				c.extra().remove(MANIFEST);
				return 0;
			}
			return Camp.isNight(level) ? 0 : 60;
		}
		if (!Trips.allowed() || Camp.isNight(level) || Camp.isDusk(level) || Trips.heldByOther(ID, c)
			|| !Trips.campSafe(level, data) || !Trips.fitToGo(c)) {
			return 0;
		}
		Mail.Delivery d = Mail.next(level);
		if (d == null) {
			return 0;
		}
		if (!ChunkLoader.inCampArea(level, d.mailbox()) && !ChunkLoader.canRoam(level.getServer())) {
			return 0;
		}
		double distance = Math.sqrt(Camp.horizontalDistSqr(d.mailbox(), c.homePos()));
		if (!Trips.daylightFor(level, 2 * distance, DROP_TICKS)) {
			return 0;
		}
		return (c.friendId() == FriendId.ROWAN ? 50 : 36) + (d.request() != null ? 6 : 0);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		walker.reset();
		phaseTicks = 0;
		Trips.State saved = Trips.state(c);
		if (saved != null) {
			Manifest m = load(c);
			if (!saved.job.equals(ID) || m == null) {
				if (saved.job.equals(ID)) {
					Trips.clear(c);
				}
				return false;
			}
			trip = saved;
			manifest = m;
			if (!Trips.home(c) && !ChunkLoader.inCampArea(level, saved.destination) && !ChunkLoader.startRoaming(c, "a delivery")) {
				return false;
			}
			Trips.claim(ID, c);
			return true;
		}
		Mail.Delivery d = Mail.plan(level);
		if (d == null) {
			return false;
		}
		if (!ChunkLoader.inCampArea(level, d.mailbox()) && !ChunkLoader.startRoaming(c, "a delivery")) {
			return false;
		}
		Manifest m = new Manifest(d.player(), d.name(), d.request() != null, d.request() != null ? d.request().askedAt() : 0);
		for (Mail.Want w : d.wants()) {
			m.entries.add(new Entry(w.item(), w.count(), 0, 0));
		}
		TownData data = TownData.get(level.getServer());
		if (d.request() != null) {
			data.dequeue(d.request()); // on its way: if the chest has none, the player is told and may ask again
		} else {
			data.setLastSurplus(d.player(), Camp.day(level));
		}
		Mail.invalidate();
		manifest = m;
		save(c, m);
		trip = new Trips.State(ID, PACK, d.mailbox(), level.getGameTime());
		Trips.save(c, trip);
		Trips.claim(ID, c);
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Trips.State t = trip;
		Manifest m = manifest;
		if (t == null || m == null) {
			return TaskStatus.FAILURE;
		}
		phaseTicks++;
		if (phaseTicks % 100 == 0) {
			Trips.snack(c);
		}
		return switch (t.phase) {
			case PACK -> pack(c, t, m);
			case OUT -> out(c, t);
			case DROP -> drop(c, t, m);
			case HOME -> home(c, t);
			case UNPACK -> unpack(c, m);
			default -> {
				finish(c);
				yield TaskStatus.FAILURE;
			}
		};
	}

	private void setPhase(CompanionEntity c, Trips.State t, String phase) {
		t.phase = phase;
		phaseTicks = 0;
		walker.reset();
		c.actions().reset();
		Trips.save(c, t);
	}

	/** At the chest: takes what is to be delivered (and a shelter kit for the road), then sets off. */
	private TaskStatus pack(CompanionEntity c, Trips.State t, Manifest m) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				finish(c);
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			finish(c);
			return TaskStatus.FAILURE;
		}
		int packed = 0;
		List<String> parts = new ArrayList<>();
		for (Entry entry : m.entries) {
			Optional<Item> item = Mail.sendable(entry.item);
			if (item.isEmpty()) {
				continue;
			}
			Item it = item.get();
			entry.packed = SupplyChest.withdraw(chest.get(), c.backpack(), s -> Mail.matches(s, it), entry.wanted);
			if (entry.packed > 0) {
				packed += entry.packed;
				parts.add(entry.packed + " " + new ItemStack(it).getHoverName().getString().toLowerCase(java.util.Locale.ROOT));
			}
		}
		save(c, m);
		if (packed == 0) {
			tell(c, m, "The supply chest had none of what was to go to your mailbox, so nothing was sent.");
			finish(c);
			return TaskStatus.FAILURE;
		}
		if (Trips.needsKit(c)) {
			Trips.packKit(c, chest.get());
		}
		Speech.say(c, Line.TRIP_START, m.name + "'s mailbox at " + Compass.coords(t.destination));
		tell(c, m, "On the way to your mailbox with " + Trips.joinList(parts) + ".");
		setPhase(c, t, OUT);
		return TaskStatus.RUNNING;
	}

	private TaskStatus out(CompanionEntity c, Trips.State t) {
		if (Trips.turnBackReason(c) != null) {
			Speech.say(c, Line.TRIP_TURN_BACK);
			setPhase(c, t, HOME);
			return TaskStatus.RUNNING;
		}
		switch (walker.walk(c, t.destination, 4)) {
			case ARRIVED -> setPhase(c, t, DROP);
			case BLOCKED -> {
				Manifest m = manifest;
				if (m != null) {
					Mail.gaveUp((ServerLevel) c.level(), m.player);
				}
				setPhase(c, t, HOME);
			}
			case WALKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** At the mailbox: puts the delivery into it, and only into it. Whatever does not fit comes home again. */
	private TaskStatus drop(CompanionEntity c, Trips.State t, Manifest m) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos box = t.destination;
		if (!c.actions().walkTo(box, 2.0)) {
			if (c.actions().isStuck() || phaseTicks > MAX_DROP_TICKS) {
				Mail.gaveUp(level, m.player);
				tell(c, m, "I couldn't get to your mailbox at " + Compass.coords(box) + ", so I've brought your delivery home.");
				setPhase(c, t, HOME);
			}
			return TaskStatus.RUNNING;
		}
		TownData data = TownData.get(level.getServer());
		Optional<TownData.Mailbox> registered = data.mailbox(m.player);
		Optional<Container> container = registered.filter(r -> r.pos().equals(box) && r.dimension().equals(Camp.dimensionId(level)))
			.flatMap(r -> SupplyChest.at(level, box));
		if (container.isEmpty()) {
			if (registered.isPresent() && registered.get().pos().equals(box) && SupplyChest.at(level, box).isEmpty() && level.isLoaded(box)) {
				data.setMailbox(m.player, null); // the chest is gone: no more deliveries there
			}
			tell(c, m, "Your mailbox at " + Compass.coords(box) + " wasn't there any more, so I've brought your delivery home.");
			setPhase(c, t, HOME);
			return TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(box.getX() + 0.5, box.getY() + 0.5, box.getZ() + 0.5);
		List<String> parts = new ArrayList<>();
		for (Entry entry : m.entries) {
			Optional<Item> item = Mail.sendable(entry.item);
			int left = entry.packed - entry.delivered;
			if (item.isEmpty() || left <= 0) {
				continue;
			}
			Item it = item.get();
			int put = SupplyChest.deposit(c.backpack(), container.get(), s -> Mail.matches(s, it), left);
			entry.delivered += put;
			if (put > 0) {
				parts.add(put + " " + new ItemStack(it).getHoverName().getString().toLowerCase(java.util.Locale.ROOT));
			}
		}
		save(c, m);
		c.swingArm();
		if (parts.isEmpty()) {
			tell(c, m, "Your mailbox at " + Compass.coords(box) + " was full, so I've brought your delivery home.");
		} else {
			Trips.announce(c, Line.MAIL_DELIVERED, m.name);
			tell(c, m, "I left " + Trips.joinList(parts) + " in your mailbox at " + Compass.coords(box) + ".");
			Camp.data(level.getServer()).addStat("mail_delivered", 1);
		}
		setPhase(c, t, HOME);
		return TaskStatus.RUNNING;
	}

	private TaskStatus home(CompanionEntity c, Trips.State t) {
		if (Trips.home(c)) {
			setPhase(c, t, UNPACK);
			return TaskStatus.RUNNING;
		}
		switch (walker.walk(c, c.homePos(), 6)) {
			case ARRIVED -> setPhase(c, t, UNPACK);
			case BLOCKED -> {
				return TaskStatus.FAILURE; // tried again later; the trip is remembered
			}
			case WALKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Back at the chest: whatever was not delivered goes back in. */
	private TaskStatus unpack(CompanionEntity c, Manifest m) {
		ChunkLoader.stopRoaming(c);
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				finish(c);
				return TaskStatus.SUCCESS; // the everyday deposit job puts it away later
			}
			case ARRIVED -> {
			}
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isPresent()) {
			for (Entry entry : m.entries) {
				Optional<Item> item = Mail.sendable(entry.item);
				int back = entry.packed - entry.delivered;
				if (item.isPresent() && back > 0) {
					Item it = item.get();
					SupplyChest.deposit(c.backpack(), chest.get(), s -> Mail.matches(s, it), back);
				}
			}
		}
		finish(c);
		return TaskStatus.SUCCESS;
	}

	/** Tells the player the delivery is for, if they are online. */
	private static void tell(CompanionEntity c, Manifest m, String text) {
		if (c.level() instanceof ServerLevel level) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
			if (player != null) {
				player.sendSystemMessage(Speech.prefix(c).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
			}
		}
	}

	private void finish(CompanionEntity c) {
		Trips.clear(c);
		Trips.release(ID, c);
		ChunkLoader.stopRoaming(c);
		c.extra().remove(MANIFEST);
		trip = null;
		manifest = null;
	}

	@Override
	public void stop(CompanionEntity c) {
		walker.reset();
		c.actions().reset();
		Trips.State t = trip;
		Manifest m = manifest;
		if (t != null && Trips.state(c) != null) {
			Trips.save(c, t);
			if (m != null) {
				save(c, m);
			}
		}
		if (Trips.home(c)) {
			ChunkLoader.stopRoaming(c); // asked for again when the trip goes on; away, it lasts until they are back
		}
		trip = null;
		manifest = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 12;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int successCooldown() {
		return 2400;
	}

	// ------------------------------------------------------------- manifest

	private static void save(CompanionEntity c, Manifest m) {
		CompoundTag tag = new CompoundTag();
		tag.putString("player", m.player.toString());
		tag.putString("name", m.name);
		tag.putBoolean("requested", m.requested);
		tag.putLong("askedAt", m.askedAt);
		ListTag lines = new ListTag();
		for (Entry entry : m.entries) {
			CompoundTag l = new CompoundTag();
			l.putString("item", entry.item.toString());
			l.putInt("wanted", entry.wanted);
			l.putInt("packed", entry.packed);
			l.putInt("delivered", entry.delivered);
			lines.add(l);
		}
		tag.put("lines", lines);
		c.extra().put(MANIFEST, tag);
	}

	private static @Nullable Manifest load(CompanionEntity c) {
		CompoundTag tag = c.extra().getCompoundOrEmpty(MANIFEST);
		if (tag.isEmpty()) {
			return null;
		}
		UUID player;
		try {
			player = UUID.fromString(tag.getStringOr("player", ""));
		} catch (IllegalArgumentException e) {
			return null;
		}
		Manifest m = new Manifest(player, tag.getStringOr("name", "someone"), tag.getBooleanOr("requested", false),
			tag.getLongOr("askedAt", 0L));
		for (Tag t : tag.getListOrEmpty("lines")) {
			if (t instanceof CompoundTag l) {
				Identifier item = Identifier.tryParse(l.getStringOr("item", ""));
				if (item != null && BuiltInRegistries.ITEM.containsKey(item)) {
					m.entries.add(new Entry(item, Math.max(0, l.getIntOr("wanted", 0)), Math.max(0, l.getIntOr("packed", 0)),
						Math.max(0, l.getIntOr("delivered", 0))));
				}
			}
		}
		return m;
	}
}
