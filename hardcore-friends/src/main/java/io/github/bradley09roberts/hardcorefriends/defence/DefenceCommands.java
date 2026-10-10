package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends defence}: the village's bells, the alarm (ringing or not, and the last one), the guard posts and who
 * stands them tonight, who is on watch now, fires being put out, and raids won. Read-only and open to everyone
 * (permission level 0, no cheats): it changes nothing and gives nothing.
 */
final class DefenceCommands {
	private DefenceCommands() {
	}

	/** Adds {@code defence} to the {@code /friends} root. */
	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("defence").executes(DefenceCommands::defence));
	}

	private static int defence(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		CampData data = Camp.data(server);
		ServerLevel level = Area.campLevel(server, data);
		if (level == null) {
			source.sendFailure(Component.literal("There is no camp yet: set one with /friends camp set."));
			return 0;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Village defence").withStyle(ChatFormatting.GOLD));
		if (!FriendsConfig.get().villageDefence) {
			lines.add(grey("The village's own defence is switched off (villageDefence: false); only the night watch is kept."));
		}
		lines.add(grey("Alarm: " + alarmText(level)));
		String last = Alarm.lastAlarm(data);
		if (last != null) {
			lines.add(grey("Last alarm: " + last + "."));
		}
		List<Bells.Bell> bells = Bells.known(level);
		if (bells.isEmpty()) {
			lines.add(grey("Bells: none yet. The town hall gets one once a bell is in the supply chest; before that the "
				+ "friends stand one at the square. Without a bell the alarm is shouted."));
		} else {
			List<String> where = new ArrayList<>();
			for (Bells.Bell b : bells) {
				where.add(b.what() + " at " + pos(b.pos()));
			}
			lines.add(grey("Bells: " + String.join("; ", where) + "."));
		}
		List<Posts.Post> posts = Posts.guardPosts(level);
		if (posts.isEmpty()) {
			lines.add(grey("Guard posts: none yet. Guards stand watch once the village has a watchtower, a gate or walls "
				+ "(the Town and City); until then the night watch keeps the camp."));
		} else {
			List<String> where = new ArrayList<>();
			for (Posts.Post p : posts) {
				String guard = "nobody";
				for (CompanionEntity c : GuardRota.onDuty(level)) {
					if (p.equals(GuardRota.postOf(c))) {
						guard = c.displayName();
					}
				}
				where.add(p.what() + " at " + pos(p.stand()) + " (" + guard + ")");
			}
			lines.add(grey("Guard posts: " + String.join("; ", where) + ". Up to " + FriendsConfig.get().guardsPerShift
				+ " guards each half of the night."));
		}
		CompanionEntity watcher = NightWatch.watcher(level);
		NightWatch.Watch watch = NightWatch.watch(level);
		if (watch == null) {
			lines.add(grey("On duty: nobody keeps watch by day."));
		} else {
			List<String> duty = new ArrayList<>();
			duty.add(watcher == null ? "no night watch" : watcher.displayName() + " on the "
				+ (watch == NightWatch.Watch.FIRST ? "first" : "second") + " watch");
			for (CompanionEntity c : GuardRota.onDuty(level)) {
				duty.add(c.displayName() + " on guard");
			}
			lines.add(grey("On duty: " + String.join(", ", duty) + "."));
		}
		if (FireWatch.any()) {
			lines.add(grey("Fires: " + FireWatch.count() + " to put out in the village."));
		}
		Map<String, Long> stats = data.stats();
		lines.add(grey("So far: " + stats.getOrDefault("defence.alarms", 0L) + " alarms, "
			+ stats.getOrDefault("defence.raids_won", 0L) + " raids beaten off, "
			+ stats.getOrDefault("defence.fires_out", 0L) + " fires put out, "
			+ stats.getOrDefault("defence.guard_shifts", 0L) + " nights on guard."));
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static String alarmText(ServerLevel level) {
		Alarm.State s = Alarm.state();
		if (s == null) {
			return "quiet.";
		}
		String rung = s.rung ? "the bell has been rung" : s.ringer != null ? "someone is running to ring the bell" : "no bell rung";
		return "ringing for " + Alarm.minutes(level.getGameTime() - s.since) + ": " + s.cause.description + " (" + rung + "; "
			+ Duty.fighters() + " at their posts, " + Duty.takingCover() + " taking cover).";
	}

	/** A line for {@code /friends camp} while the alarm rings. */
	static List<Component> campStatus(MinecraftServer server) {
		List<Component> lines = new ArrayList<>();
		Alarm.State s = Alarm.state();
		if (s != null) {
			lines.add(Component.literal("Defence: the alarm is ringing (" + s.cause.description + "); see /friends defence.")
				.withStyle(ChatFormatting.RED));
		}
		return lines;
	}

	private static String pos(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	private static Component grey(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}
}
