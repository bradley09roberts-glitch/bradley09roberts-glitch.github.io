package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapRecord;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapState;
import io.github.bradley09roberts.hardcorefriends.town.TownPermissions;

/**
 * {@code /friends pets} (every pet: its name and kind, whose it is and where), {@code /friends maps} (every map the
 * camp's explorer made: what it shows, how far along, where it is) and {@code /friends map [number]} (ask the map maker
 * for a copy, made from the camp's own paper). All work at permission level 0 with cheats off; the copy is never a
 * free item (it is drawn on an empty map from the camp's stock, carried over by the map maker), so asking for one is
 * kept for the players trusted with orders ({@link TownPermissions#require}), and is only taken by day.
 */
final class PetsCommands {
	private PetsCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("pets").executes(PetsCommands::pets))
			.then(Commands.literal("maps").executes(PetsCommands::maps))
			.then(Commands.literal("map")
				.executes(ctx -> copy(ctx, -1))
				.then(Commands.argument("number", IntegerArgumentType.integer(0))
					.executes(ctx -> copy(ctx, IntegerArgumentType.getInteger(ctx, "number")))));
	}

	private static void send(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	// ------------------------------------------------------------------ pets

	private static int pets(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		PetsData data = PetsData.get(server);
		FriendsConfig cfg = FriendsConfig.get();
		if (data.pets().isEmpty()) {
			source.sendFailure(Component.literal(cfg.pets
				? "No pets yet. Friends and children adopt stray cats (with raw fish from the chest) and wild wolves "
					+ "(with bones) once the camp has a home for them: a cabin, or houses in the village."
				: "Pets are switched off (pets in config/hardcorefriends.json)."));
			return 0;
		}
		send(source, Component.literal("The camp's pets (" + data.pets().size() + " of " + cfg.maxPets + ")").withStyle(ChatFormatting.GOLD));
		for (PetsData.Pet p : data.pets()) {
			CompanionEntity owner = Pets.companion(server, p.owner);
			String kind = p.kind == PetKind.WOLF ? "dog (a tamed wolf)" : "cat";
			String who;
			if (p.owner == null) {
				who = p.name + ", the camp's own " + kind;
			} else {
				String name = owner != null ? Pets.fullName(server, owner) : p.ownerName.isEmpty() ? "someone away" : p.ownerName;
				// "Pip Hart and their cat Biscuit": a child and their pet go together.
				who = owner != null && owner.isChild() ? name + " and their " + kind + " " + p.name : p.name + ", " + name + "'s " + kind;
			}
			TamableAnimal a = Pets.pet(server, p.id);
			String where = a != null ? doing(server, a, owner) : "last seen at " + place(p.dimension, p.lastPos);
			String health = a != null ? String.format(Locale.ROOT, ", health %.0f/%.0f", a.getHealth(), a.getMaxHealth()) : "";
			send(source, Component.literal("- " + who + ": " + where + health).withStyle(ChatFormatting.GRAY));
		}
		return data.pets().size();
	}

	/** What a loaded pet is doing, in a few words with where it is. */
	private static String doing(MinecraftServer server, TamableAnimal a, CompanionEntity owner) {
		PetBrain.State s = PetBrain.peek(a);
		String at = place(Camp.dimensionId((ServerLevel) a.level()), a.blockPosition());
		if (a.isLeashed()) {
			return "on a lead at " + at;
		}
		if (a.getTarget() != null) {
			return "chasing off " + a.getTarget().getName().getString() + " at " + at;
		}
		if (a.isOrderedToSit()) {
			return "sitting at home for the night at " + at;
		}
		if (s != null && s.plan == PetBrain.Plan.FOLLOW && owner != null) {
			return "following " + owner.displayName() + " at " + at;
		}
		if (s != null && s.plan == PetBrain.Plan.GO_TO) {
			return "heading home, at " + at;
		}
		return "about the camp at " + at;
	}

	private static String place(String dimension, BlockPos pos) {
		String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		return dimension.equals("minecraft:overworld") || dimension.isEmpty() ? where : where + " (" + dimension.replace("minecraft:", "") + ")";
	}

	// ------------------------------------------------------------------ maps

	private static int maps(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		PetsData data = PetsData.get(server);
		if (!FriendsConfig.get().scoutMaps && data.maps().isEmpty()) {
			source.sendFailure(Component.literal("Map making is switched off (scoutMaps in config/hardcorefriends.json)."));
			return 0;
		}
		if (data.maps().isEmpty()) {
			source.sendFailure(Component.literal("No maps yet. The camp's explorer makes them from eight paper and a compass "
				+ "(or four iron and a redstone for one) in the camp chest, at the crafting table."));
			return 0;
		}
		send(source, Component.literal("The camp's maps (/friends map <number> for a copy)").withStyle(ChatFormatting.GOLD));
		for (MapRecord m : data.maps()) {
			String state = switch (m.state) {
				case DRAWING -> m.maker + " is drawing it";
				case FINISHED -> "finished, " + m.maker + " is bringing it home";
				case HUNG -> "hanging " + (m.frame == null ? "on the wall" : "at " + m.frame.getX() + " " + m.frame.getY() + " " + m.frame.getZ());
				case STORED -> "in the camp chest";
				case GONE -> "lost or taken";
			};
			String drawn = String.format(Locale.ROOT, "%.0f%% drawn", m.coverage * 100);
			int width = m.halfWidth() * 2;
			send(source, Component.literal("#" + m.id + " " + m.title() + ": " + state + ", " + drawn + " (" + width + " blocks across)")
				.withStyle(m.state == MapState.GONE ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
		}
		return data.maps().size();
	}

	/**
	 * Asks the map maker for a copy: {@code number} picks the map, or -1 for the best one (where the player stands, else
	 * the camp's). The maker brings it over, made from the camp's stock.
	 */
	private static int copy(CommandContext<CommandSourceStack> ctx, int number) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can be handed a map."));
			return 0;
		}
		// A copy uses up the camp's paper and a compass: a delivery from the stores, so only for those trusted with orders.
		if (!TownPermissions.require(source)) {
			return 0;
		}
		ServerLevel level = player.level();
		PetsData data = PetsData.get(source.getServer());
		Optional<MapRecord> map = number < 0 ? Maps.bestFor(level, player.blockPosition()) : data.map(number).filter(Maps::done);
		if (map.isEmpty()) {
			source.sendFailure(Component.literal(number < 0 ? "There is no finished map yet." : "There is no finished map #" + number
				+ " (see /friends maps)."));
			return 0;
		}
		CompanionEntity maker = null;
		for (CompanionEntity c : Companions.all()) {
			if (Maps.isMaker(c)) {
				maker = c;
				break;
			}
		}
		if (maker == null) {
			source.sendFailure(Component.literal("The camp's explorer is not about to make you a copy just now."));
			return 0;
		}
		// Copies are made by day: after nightfall the map maker goes to bed, and work never keeps a friend up.
		if (maker.level() instanceof ServerLevel makerLevel && Camp.isNightTime(makerLevel)) {
			source.sendFailure(Component.literal(maker.displayName() + " makes copies by day. Ask again in the morning, or "
				+ "right-click " + maker.displayName() + " with an empty map of your own."));
			return 0;
		}
		if (CopyMapTask.pending(player.getUUID())) {
			source.sendFailure(Component.literal(maker.displayName() + " is already seeing to your copy."));
			return 0;
		}
		CopyMapTask.request(player, map.get(), level.getGameTime());
		List<String> how = new ArrayList<>();
		how.add(maker.displayName() + " will make you a copy of the " + map.get().title().toLowerCase(Locale.ROOT)
			+ " from the camp's paper and bring it over.");
		if (maker.level() != level || maker.distanceToSqr(player) > 48 * 48) {
			how.add("Wait near " + maker.displayName() + " (within 48 blocks) for it.");
		}
		how.add("Or right-click " + maker.displayName() + " with an empty map of your own.");
		send(source, Component.literal(String.join(" ", how)).withStyle(ChatFormatting.GRAY));
		return 1;
	}
}
