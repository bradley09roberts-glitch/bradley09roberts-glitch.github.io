package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * Birthdays. Everyone has one: the day of the year they were born in the camp, or (for everyone else) the day they
 * joined it. On the day, once they are up and about in the camp, everyone is told, up to three friends nearby wish
 * them a happy birthday (the birthday friend thanks them), and if the camp has plenty there is a small present from
 * the stores: a cake shared with whoever is near (from the supply chest or the bakery's shelves), otherwise a sweet
 * treat for their backpack. Real food only, moved or eaten, never made from nothing.
 */
final class Birthdays {
	private static final long FROM = 1000;
	private static final long UNTIL = 11000;
	private static final double WISH_RANGE = 24;
	private static final double CAKE_RANGE = 12;
	private static final String UNITY = "birthday";

	private Birthdays() {
	}

	/** Every few seconds: anyone whose birthday it is and who is about the camp is celebrated, once. */
	static void check(MinecraftServer server) {
		if (!FriendsConfig.get().villageLife) {
			return;
		}
		ServerLevel level = Places.campLevel(server);
		long time = Calendar.time(server);
		if (level == null || time < FROM || time >= UNTIL) {
			return;
		}
		LifeData data = LifeData.get(server);
		long day = Calendar.today(server);
		for (CompanionEntity c : Places.freePeople(level)) {
			LifeData.Person p = data.person(c.getUUID()).orElse(null);
			if (p == null || p.lastBirthday == day || !Calendar.birthday(p.joined, day) || c.getTarget() != null) {
				continue;
			}
			p.lastBirthday = day;
			data.setDirty();
			celebrate(server, level, data, c, p, day);
			return; // one at a time: the next one a few seconds later
		}
	}

	private static void celebrate(MinecraftServer server, ServerLevel level, LifeData data, CompanionEntity c, LifeData.Person p, long day) {
		long years = Calendar.yearsSince(p.joined, day);
		String name = c.displayName();
		String text = p.born
			? "It's " + name + "'s birthday: " + years + (years == 1 ? " year old" : " years old") + " today! Happy birthday, " + name + "!"
			: "It's " + name + "'s birthday in the camp: " + years + (years == 1 ? " year" : " years") + " since they joined. Happy birthday!";
		Speech.announce(server, Component.literal(text).withStyle(ChatFormatting.GOLD));
		if (p.born) {
			Chronicle.write(server, Chronicle.fullName(server, c.getUUID(), name) + " turned " + years + ".");
		}
		c.needs().add(Needs.Need.FUN, 25);
		c.needs().add(Needs.Need.SOCIAL, 15);
		level.sendParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getBbHeight() + 0.3, c.getZ(), 3, 0.4, 0.2, 0.4, 0.0);
		List<CompanionEntity> wishers = new ArrayList<>();
		for (CompanionEntity other : Places.freePeople(level)) {
			if (other != c && other.distanceToSqr(c) <= WISH_RANGE * WISH_RANGE && other.getTarget() == null && wishers.size() < 3) {
				wishers.add(other);
			}
		}
		UUID id = c.getUUID();
		int delay = 20;
		for (CompanionEntity w : wishers) {
			UUID wid = w.getUUID();
			Later.run(server, delay, () -> {
				CompanionEntity wisher = Places.loaded(level, wid);
				if (wisher != null) {
					Speech.say(wisher, Line.BIRTHDAY_WISH, name);
				}
			});
			delay += 40;
		}
		Later.run(server, delay + 10, () -> {
			CompanionEntity self = Places.loaded(level, id);
			if (self != null) {
				Speech.say(self, Line.BIRTHDAY_THANKS);
			}
		});
		present(level, c);
		Unity.add(level, UNITY, 2, 6);
	}

	/**
	 * A present from the stores when the camp has plenty: a cake shared by everyone near (taken from the supply chest,
	 * or from the bakery's own shelves), else a cookie, a pumpkin pie or an apple for the birthday friend's backpack.
	 */
	private static void present(ServerLevel level, CompanionEntity c) {
		if (CampNeeds.need(CampNeeds.Need.FOOD) > 0.4) {
			return; // not while the camp is short of food
		}
		Container chest = SupplyChest.of(level).orElse(null);
		boolean cake = chest != null && SupplyChest.count(chest, s -> s.is(Items.CAKE)) > 0 && take(chest, Items.CAKE);
		if (!cake) {
			cake = bakeryCake(level);
		}
		if (cake) {
			for (CompanionEntity other : Places.freePeople(level)) {
				if (other.distanceToSqr(c) <= CAKE_RANGE * CAKE_RANGE) {
					// A slice each: two points of food, as the game's cake gives a player.
					other.needs().add(Needs.Need.HUNGER, 12);
					other.needs().add(Needs.Need.FUN, 10);
				}
			}
			Speech.announce(level.getServer(), Component.literal("There's cake for everyone near " + c.displayName() + "!")
				.withStyle(ChatFormatting.GOLD));
			return;
		}
		if (chest == null || !c.backpack().canFit(new ItemStack(Items.COOKIE))) {
			return;
		}
		for (var treat : new Item[] {Items.COOKIE, Items.PUMPKIN_PIE, Items.APPLE}) {
			if (SupplyChest.count(chest, s -> s.is(treat)) >= 4 && SupplyChest.withdraw(chest, c.backpack(), s -> s.is(treat), 1) > 0) {
				return;
			}
		}
	}

	/** Takes one of an item out of a container. */
	private static boolean take(Container container, Item item) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (!s.isEmpty() && s.is(item)) {
				s.shrink(1);
				if (s.isEmpty()) {
					container.setItem(i, ItemStack.EMPTY);
				}
				container.setChanged();
				return true;
			}
		}
		return false;
	}

	/** A cake from the bakery's shelves (the village's own bakery chests, which the friends built), if there is one. */
	private static boolean bakeryCake(ServerLevel level) {
		for (VillagePlan.Building bakery : VillagePlan.buildingsOfKind(level.getServer(), "shop:bakery")) {
			for (BlockPos pos : bakery.marker("chest")) {
				if (!level.isLoaded(pos) || !Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
					continue;
				}
				Container shelf = SupplyChest.at(level, pos).orElse(null);
				if (shelf != null && take(shelf, Items.CAKE)) {
					return true;
				}
			}
		}
		return false;
	}
}
