package io.github.bradley09roberts.hardcorefriends.ai.role.sage;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.bradley09roberts.hardcorefriends.ai.role.forage.DeliverToBuilderTask;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/** Sage's reading of the camp's needs: who is best placed to cover the top need, and how to say so. */
public final class TeamPlan {
	private TeamPlan() {
	}

	/** Friends who can cover a need, best first. */
	public static List<FriendId> ownersOf(Need need) {
		return switch (need) {
			case FOOD -> List.of(FriendId.FERN, FriendId.ROWAN);
			case WOOD -> List.of(FriendId.ROWAN, FriendId.OAK);
			case STONE -> List.of(FriendId.FLINT, FriendId.ROWAN);
			case DIRT -> List.of(FriendId.ROWAN, FriendId.TERRA);
			case TORCHES -> List.of(FriendId.SPARK, FriendId.FLINT);
			case ORE -> List.of(FriendId.FLINT, FriendId.SCOUT);
			case SEEDS -> List.of(FriendId.FERN, FriendId.ROWAN);
			case BUILD -> List.of(FriendId.ROWAN, FriendId.FLINT);
		};
	}

	/** The first owner of the need who is with the team right now. */
	public static Optional<CompanionEntity> ownerPresent(Need need) {
		for (FriendId id : ownersOf(need)) {
			Optional<CompanionEntity> friend = Companions.find(id);
			if (friend.isPresent()) {
				return friend;
			}
		}
		return Optional.empty();
	}

	/** The announcement for a team focus, e.g. "Our stores are low on wood. Rowan, the forest is yours." */
	public static String announce(@Nullable Need focus, long gameTime) {
		if (focus == null) {
			return "Our stores are in good shape. Keep doing what you each do best.";
		}
		String who = ownerPresent(focus).map(c -> c.friendId().displayName()).orElse(null);
		String problem = switch (focus) {
			case FOOD -> "Our stores are low on food.";
			case WOOD -> "Our stores are low on wood.";
			case STONE -> "Our stores are low on stone.";
			case DIRT -> "We are short of dirt for building.";
			case TORCHES -> "We are running out of torches.";
			case ORE -> "We have almost no iron.";
			case SEEDS -> "We are short of seeds to plant.";
			case BUILD -> buildProblem(gameTime);
		};
		if (who == null) {
			return problem + " Everyone, bring back what you can find.";
		}
		String request = switch (focus) {
			case FOOD -> "%s, the fields come first.";
			case WOOD -> "%s, the forest is yours.";
			case STONE -> "%s, we need cobblestone from below.";
			case DIRT -> "%s, a little digging at the quarry would help.";
			case TORCHES -> "%s, coal and sticks, please, before the nights draw in.";
			case ORE -> "%s, the mine is yours.";
			case SEEDS -> "%s, keep some back from the harvest.";
			case BUILD -> "%s, let us gather what the plan needs.";
		};
		return problem + " " + String.format(request, who);
	}

	/** Who waits on what: the builder is whoever has been building (Oak, or a friend standing in for him). */
	private static String buildProblem(long gameTime) {
		String shortage = CampNeeds.shortageText(gameTime);
		String who = DeliverToBuilderTask.builder().map(c -> c.friendId().displayName()).orElse("The builder");
		return who + " is waiting on " + (shortage.isEmpty() ? "building materials" : shortage) + ".";
	}
}
