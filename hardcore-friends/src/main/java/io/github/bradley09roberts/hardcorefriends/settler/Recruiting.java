package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Asking a stranger to join. The first right-click, they say who they are, what they do and a little of their story,
 * and ask for something small that suits their trade (see {@link Requests}); they remember it. A later right-click
 * by a player carrying all of it takes exactly that from the player and the stranger joins the team: working at the
 * camp, or following the player there when the camp is far off or in another dimension (and with no camp yet, simply
 * following them). Otherwise they say what is still missing.
 *
 * <p>The team takes at most {@code maxSettlers} newcomers at once, and one player may ask in at most
 * {@code maxSettlersPerPlayer} of them; a stranger says so kindly when there is no room. Nothing is taken until the
 * stranger actually joins, and nothing is ever given away for free.
 */
final class Recruiting {
	/** Unity category for newcomers joining: +10 each, at most 30 a day. */
	static final String UNITY_CATEGORY = "newcomer";
	/** A recruit this far beyond the camp's edge follows the player home instead of setting off alone. */
	private static final int WALK_HOME_ALONE = 48;

	private Recruiting() {
	}

	/** The {@code CompanionEvents.INTERACT} hook: strangers talk to whoever right-clicks them; team members pass. */
	static InteractionResult interact(CompanionEntity c, ServerPlayer player, InteractionHand hand) {
		if (c.isTeamMember() || !c.isSettler() || !(c.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		CompoundTag tag = Strangers.state(c);
		long now = level.getGameTime();
		if (now - tag.getLongOr(Strangers.LAST_TALK, -100L) < 10) {
			return InteractionResult.SUCCESS_SERVER; // one click, one answer
		}
		tag.putLong(Strangers.LAST_TALK, now);
		c.getNavigation().stop();
		c.getLookControl().setLookAt(player);
		int index = tag.getIntOr(Strangers.REQUEST, -1);
		if (index < 0) {
			index = c.getRandom().nextInt(Requests.choices(c.friendId()));
			tag.putInt(Strangers.REQUEST, index);
			introduce(c, player, tag, Requests.get(c.friendId(), index));
			return InteractionResult.SUCCESS_SERVER;
		}
		Requests.Request request = Requests.get(c.friendId(), index);
		String missing = Requests.missing(player, request);
		if (!missing.isEmpty()) {
			if (!Speech.say(c, Line.STRANGER_NOT_YET, missing)) {
				Strangers.note(player, c.displayName() + " is still hoping for " + missing + ".");
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		FriendsConfig cfg = FriendsConfig.get();
		SettlerData data = SettlerData.get(level.getServer());
		if (data.aliveCount() >= cfg.maxSettlers) {
			Speech.say(c, Line.STRANGER_TEAM_FULL);
			Strangers.note(player, "Your team already has " + data.aliveCount() + " newcomers, the most this world allows ("
				+ cfg.maxSettlers + ").");
			return InteractionResult.SUCCESS_SERVER;
		}
		if (data.aliveRecruitedBy(player.getUUID()) >= cfg.maxSettlersPerPlayer) {
			Speech.say(c, Line.STRANGER_TEAM_FULL);
			Strangers.note(player, "You have already asked in " + data.aliveRecruitedBy(player.getUUID())
				+ " newcomers, the most one player may (" + cfg.maxSettlersPerPlayer + ").");
			return InteractionResult.SUCCESS_SERVER;
		}
		List<ItemStack> given = Requests.take(player, request);
		if (given == null) {
			return InteractionResult.SUCCESS_SERVER; // changed in the meantime: nothing taken
		}
		join(c, player, level, given, data);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Who they are, their story, and what they would like before they join. */
	private static void introduce(CompanionEntity c, ServerPlayer player, CompoundTag tag, Requests.Request request) {
		String story = tag.getStringOr(Strangers.STORY, "");
		Speech.sayText(c, "I'm " + c.displayName() + ", " + Personas.tradeWithArticle(c.friendId().role()) + "."
			+ (story.isEmpty() ? "" : " " + story));
		Speech.say(c, Line.STRANGER_ASKS, request.describe());
		Strangers.note(player, "Bring " + request.describe() + " and right-click " + c.displayName()
			+ " again to ask them to join your team.");
	}

	/** The stranger joins: the team, the camp, a backpack of their own, and the records. */
	private static void join(CompanionEntity c, ServerPlayer player, ServerLevel level, List<ItemStack> given, SettlerData data) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = Strangers.state(c);
		// Their name must be theirs alone among the living team, so orders by name reach only them.
		Persona persona = c.persona();
		if (persona != null && data.teamNames(c.getUUID()).contains(persona.name().toLowerCase(Locale.ROOT))) {
			persona = new Persona(Personas.freeName(c.getRandom(), data.namesInUse(c.getUUID())), persona.colour(), persona.skin(),
				persona.archetype());
			c.setPersona(persona);
		}
		CampData camp = Camp.data(server);
		boolean campHere = Camp.isCampLevel(level, camp);
		BlockPos centre = camp.campPos().orElse(null);
		int reach = Camp.radius(camp) + WALK_HOME_ALONE;
		boolean nearCamp = campHere && centre != null && Camp.horizontalDistSqr(c.blockPosition(), centre) <= (double) reach * reach;
		tag.remove(Strangers.LEAVING);
		tag.remove(Strangers.LEAVE_AT);
		tag.remove(Strangers.NIGHT_SPOT);
		tag.putString(Strangers.RECRUITED_BY, player.getUUID().toString());
		tag.putString(Strangers.RECRUITED_BY_NAME, player.getName().getString());
		if (nearCamp) {
			c.setHomePos(centre);
			c.setMode(CompanionMode.WORK, player);
		} else {
			c.setHomePos(player.blockPosition());
			c.setMode(CompanionMode.FOLLOW, player);
			tag.putBoolean(Strangers.TO_CAMP, centre != null);
		}
		c.backpack().setCapacity(Unity.backpackSlots(server));
		if (c.getMainHandItem().isEmpty()) {
			c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(c.friendId().starterTool()));
		}
		for (ItemStack stack : given) {
			ItemStack left = c.backpack().insert(stack);
			if (!left.isEmpty()) {
				c.spawnAtLocation(level, left);
			}
		}
		if (Personas.Origin.ROAD.key().equals(tag.getStringOr(Strangers.ORIGIN, ""))) {
			data.setWandererUntil(level.getGameTime()); // their visit is over: they live here now
		}
		data.recordJoined(c.getUUID(), c.displayName(), c.friendId(), c.nameColour(), player.getUUID(), player.getName().getString(),
			level.getGameTime());
		camp.addStat("newcomers_recruited", 1);
		Speech.say(c, Line.STRANGER_JOINS, player.getName().getString());
		Speech.announce(server, Speech.prefix(c).append(Component.literal(Strangers.nameAndTrade(c) + " has joined the team, asked in by "
			+ player.getName().getString() + ".").withStyle(ChatFormatting.GOLD)));
		Unity.add(level, UNITY_CATEGORY, 10, 30);
		String where;
		if (nearCamp) {
			where = " They will work at the camp.";
		} else if (centre != null) {
			where = " The camp is a long way off, so they will follow you there and start work once they arrive.";
		} else {
			where = " No camp yet, so they will follow you. Use /friends camp set where you want to live.";
		}
		player.sendSystemMessage(Component.literal(c.displayName() + " (" + c.friendId().role().title() + ", "
			+ c.friendId().personality() + ") is on your team." + where).withStyle(ChatFormatting.GREEN));
	}
}
