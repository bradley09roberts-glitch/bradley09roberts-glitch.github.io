package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The town sub-commands of {@code /friends}: {@code trust}, {@code untrust}, {@code trusted}, {@code owner},
 * {@code bond}, {@code jobs}, {@code deliver}, {@code note}, {@code notes}, {@code mailbox} and {@code send}. All of
 * them work at permission level 0 with cheats off; none gives anything away or moves a player. Changing who is trusted
 * is for the owner; anyone may look.
 */
final class TownCommands {
	/** How close to the supply chest a player must stand to deliver. */
	private static final double DELIVER_REACH = 8;
	/** Friends this close to a delivering player are thanked into a closer bond. */
	private static final double THANKS_RANGE = 16;

	private TownCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("trust").then(playerArg().executes(TownCommands::trust)));
		root.then(Commands.literal("untrust").then(playerArg().executes(TownCommands::untrust)));
		root.then(Commands.literal("trusted").executes(TownCommands::trusted));
		root.then(Commands.literal("owner").executes(TownCommands::trusted).then(playerArg().executes(TownCommands::owner)));
		root.then(Commands.literal("bond")
			.executes(ctx -> bond(ctx, null))
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, b) -> SharedSuggestionProvider.suggest(friendNames(), b))
				.executes(ctx -> bond(ctx, StringArgumentType.getString(ctx, "name")))));
		root.then(Commands.literal("jobs").executes(TownCommands::jobs));
		root.then(Commands.literal("deliver")
			.executes(ctx -> deliver(ctx, 0))
			.then(Commands.argument("number", IntegerArgumentType.integer(1, 99))
				.executes(ctx -> deliver(ctx, IntegerArgumentType.getInteger(ctx, "number")))));
		root.then(Commands.literal("note").then(Commands.argument("text", StringArgumentType.greedyString()).executes(TownCommands::note)));
		root.then(Commands.literal("notes").executes(TownCommands::notes));
		root.then(Commands.literal("mailbox")
			.executes(TownCommands::mailbox)
			.then(Commands.literal("remove").executes(TownCommands::mailboxRemove)));
		root.then(Commands.literal("send")
			.then(Commands.argument("item", IdentifierArgument.id())
				.suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(chestItems(ctx.getSource()), b))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(TownCommands::send))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> playerArg() {
		return Commands.argument("player", StringArgumentType.word()).suggests((ctx, b) -> {
			Set<String> names = new LinkedHashSet<>();
			for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
				names.add(p.getName().getString());
			}
			names.addAll(TownData.get(ctx.getSource().getServer()).players().values());
			return SharedSuggestionProvider.suggest(names, b);
		});
	}

	/** An online player by name, otherwise one the camp remembers. */
	private static Optional<Map.Entry<UUID, String>> findPlayer(MinecraftServer server, String name) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			return Optional.of(Map.entry(online.getUUID(), online.getName().getString()));
		}
		TownData data = TownData.get(server);
		return data.byName(name).map(id -> Map.entry(id, data.name(id)));
	}

	private static void say(CommandSourceStack source, String text, ChatFormatting colour) {
		source.sendSuccess(() -> Component.literal(text).withStyle(colour), false);
	}

	// ------------------------------------------------------------------ trust

	private static int trust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TownData data = TownData.get(server);
		if (data.owner().isEmpty()) {
			TownPermissions.mayCommand(player); // the first to change anything becomes the owner
		}
		if (!TownPermissions.mayManage(player)) {
			source.sendFailure(Component.literal("Only the camp's owner (" + data.ownerName() + ") can choose whom to trust."));
			return 0;
		}
		Optional<Map.Entry<UUID, String>> who = findPlayer(server, StringArgumentType.getString(ctx, "player"));
		if (who.isEmpty()) {
			source.sendFailure(Component.literal("No player of that name is online or known to the camp."));
			return 0;
		}
		UUID id = who.get().getKey();
		String name = who.get().getValue();
		if (data.isOwner(id)) {
			source.sendFailure(Component.literal(name + " owns the camp already."));
			return 0;
		}
		data.trust(id, name);
		say(source, name + " is now trusted: they may give the friends orders, recruit, open backpacks and move the camp.",
			ChatFormatting.GREEN);
		ServerPlayer target = server.getPlayerList().getPlayer(id);
		if (target != null && target != player) {
			target.sendSystemMessage(Component.literal(player.getName().getString() + " trusts you with the camp now. "
				+ "The friends will take your orders.").withStyle(ChatFormatting.GREEN));
		}
		return 1;
	}

	private static int untrust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TownData data = TownData.get(server);
		if (!TownPermissions.mayManage(player)) {
			source.sendFailure(Component.literal("Only the camp's owner (" + data.ownerName() + ") can choose whom to trust."));
			return 0;
		}
		Optional<Map.Entry<UUID, String>> who = findPlayer(server, StringArgumentType.getString(ctx, "player"));
		if (who.isEmpty() || !data.untrust(who.get().getKey())) {
			source.sendFailure(Component.literal("That player is not on the trusted list."));
			return 0;
		}
		say(source, who.get().getValue() + " is no longer trusted with the camp.", ChatFormatting.YELLOW);
		TownPermissions.letGoAll(); // friends following them, or on their lead, let go now
		return 1;
	}

	private static int trusted(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		TownData data = TownData.get(source.getServer());
		boolean required = FriendsConfig.get().requireTrust;
		if (data.owner().isEmpty()) {
			say(source, "Nobody owns the camp yet: the first player to set the camp, recruit someone or give an order will.",
				ChatFormatting.GOLD);
		} else {
			say(source, "Camp owner: " + data.ownerName(), ChatFormatting.GOLD);
		}
		List<String> names = new ArrayList<>(data.trusted().values());
		say(source, "Trusted: " + (names.isEmpty() ? "nobody yet (/friends trust <player>)" : String.join(", ", names)), ChatFormatting.GRAY);
		say(source, required
			? "Only the owner and trusted players may give orders, recruit, open backpacks or move the camp. Anyone may look, and anyone may hand a friend food."
			: "Trust is not required on this world (requireTrust is off): everyone may give orders.", ChatFormatting.DARK_GRAY);
		return 1;
	}

	private static int owner(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TownData data = TownData.get(server);
		if (!TownPermissions.mayManage(player)) {
			source.sendFailure(Component.literal("Only the camp's owner (" + data.ownerName() + ") can hand the camp over."));
			return 0;
		}
		Optional<Map.Entry<UUID, String>> who = findPlayer(server, StringArgumentType.getString(ctx, "player"));
		if (who.isEmpty()) {
			source.sendFailure(Component.literal("No player of that name is online or known to the camp."));
			return 0;
		}
		data.setOwner(who.get().getKey(), who.get().getValue());
		Speech.announce(server, Component.literal(who.get().getValue() + " now owns the camp.").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	// ------------------------------------------------------------------- bond

	private static int bond(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		TownData data = TownData.get(source.getServer());
		if (name == null) {
			ServerPlayer player = source.getPlayerOrException();
			say(source, "Your bond with each friend (-100 to 100)", ChatFormatting.GOLD);
			List<CompanionEntity> friends = new ArrayList<>(Companions.all());
			friends.sort(Comparator.comparingInt((CompanionEntity c) -> Bonds.get(c, player.getUUID())).reversed());
			Set<String> shown = new LinkedHashSet<>();
			for (CompanionEntity c : friends) {
				shown.add(Bonds.key(c));
				int bond = Bonds.get(c, player.getUUID());
				source.sendSuccess(() -> Speech.prefix(c).append(bondText(bond)), false);
			}
			// Friends who are away (not loaded) but remember you.
			data.allBonds().forEach((key, map) -> {
				Integer bond = map.get(player.getUUID());
				if (bond != null && !shown.contains(key)) {
					source.sendSuccess(() -> Component.literal("[" + data.friendName(key) + "] ").withStyle(ChatFormatting.GRAY)
						.append(bondText(bond)).append(Component.literal(" (away)").withStyle(ChatFormatting.DARK_GRAY)), false);
				}
			});
			if (friends.isEmpty() && data.allBonds().isEmpty()) {
				say(source, "No friends nearby yet.", ChatFormatting.GRAY);
			}
			say(source, "Bonds grow with food, gifts, help in a fight, deliveries and time together; they fall when you hurt "
				+ "or dismiss a friend.", ChatFormatting.DARK_GRAY);
			return 1;
		}
		Optional<CompanionEntity> found = FriendId.byKey(name).flatMap(Companions::find).or(() -> FriendsCommand.findNewcomer(name));
		if (found.isEmpty()) {
			source.sendFailure(Component.literal("No friend called " + name + " is nearby."));
			return 0;
		}
		CompanionEntity c = found.get();
		source.sendSuccess(() -> Speech.prefix(c).append(Component.literal("bonds with players").withStyle(ChatFormatting.GOLD)), false);
		List<Map.Entry<UUID, Integer>> bonds = new ArrayList<>(data.bondsOf(Bonds.key(c)).entrySet());
		bonds.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
		if (bonds.isEmpty()) {
			say(source, "  nobody yet", ChatFormatting.GRAY);
		}
		for (Map.Entry<UUID, Integer> e : bonds) {
			source.sendSuccess(() -> Component.literal("  " + data.name(e.getKey()) + ": ").withStyle(ChatFormatting.WHITE)
				.append(bondText(e.getValue())), false);
		}
		return bonds.size();
	}

	private static MutableComponent bondText(int bond) {
		return Component.literal(bond + " (" + Bonds.word(bond) + ")").withStyle(Bonds.colour(bond));
	}

	private static List<String> friendNames() {
		List<String> names = new ArrayList<>();
		for (FriendId id : FriendId.values()) {
			names.add(id.key());
		}
		for (CompanionEntity c : Companions.all()) {
			if (c.isSettler()) {
				names.add(c.displayName().toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	// -------------------------------------------------------------- job board

	private static int jobs(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		if (Camp.data(server).campPos().isEmpty()) {
			source.sendFailure(Component.literal("No camp yet, so there are no jobs. Set one with /friends camp set."));
			return 0;
		}
		List<JobBoard.Job> board = JobBoard.board(server);
		say(source, "The camp's job board", ChatFormatting.GOLD);
		if (board.isEmpty()) {
			say(source, "Nothing is needed right now: the camp is well stocked.", ChatFormatting.GREEN);
		}
		for (int i = 0; i < board.size(); i++) {
			JobBoard.Job job = board.get(i);
			String text = " #" + (i + 1) + "  " + job.request().text() + (job.kind().withoutNumber() ? "" : "  (by number)");
			say(source, text, ChatFormatting.WHITE);
		}
		if (!board.isEmpty()) {
			say(source, "Bring them within " + (int) DELIVER_REACH + " blocks of the supply chest. /friends deliver brings building "
				+ "and camp materials; /friends deliver <number> brings one job, and is the only way to give food or what Sage's "
				+ "plan wants. Only your main inventory is used: never your hotbar, armour, off hand, tools, buckets or totems.",
				ChatFormatting.GRAY);
		}
		TownData data = TownData.get(server);
		List<Map.Entry<UUID, Integer>> helpers = new ArrayList<>(data.helpedAll().entrySet());
		helpers.removeIf(e -> e.getValue() <= 0);
		helpers.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
		if (!helpers.isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (int i = 0; i < helpers.size() && i < 5; i++) {
				parts.add(data.name(helpers.get(i).getKey()) + " " + helpers.get(i).getValue());
			}
			say(source, "Helped the camp (things delivered): " + String.join(", ", parts), ChatFormatting.GRAY);
		}
		ServerPlayer player = source.getPlayer();
		if (player != null) {
			JobBoard.remember(player, board); // these are the numbers /friends deliver <number> means for this player
			say(source, "You have helped with " + data.helped(player.getUUID()) + " things so far.", ChatFormatting.DARK_GRAY);
		}
		return board.size();
	}

	private static int deliver(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();
		MinecraftServer server = source.getServer();
		CampData camp = Camp.data(server);
		Optional<Container> chest = SupplyChest.of(level);
		Optional<BlockPos> chestPos = camp.chestPos();
		if (chest.isEmpty() || chestPos.isEmpty()) {
			source.sendFailure(Component.literal("There is no supply chest here to deliver to. Stand near the camp's supply chest."));
			return 0;
		}
		if (player.position().distanceToSqr(Vec3.atCenterOf(chestPos.get())) > DELIVER_REACH * DELIVER_REACH) {
			source.sendFailure(Component.literal("Stand within " + (int) DELIVER_REACH + " blocks of the supply chest at "
				+ Compass.coords(chestPos.get()) + " to deliver."));
			return 0;
		}
		List<JobBoard.Job> board = JobBoard.board(server);
		if (board.isEmpty()) {
			say(source, "The camp needs nothing right now. Thank you all the same!", ChatFormatting.GREEN);
			return 0;
		}
		List<JobBoard.Job> chosen;
		if (number > 0) {
			// The job the player saw under this number, not whatever has moved into its place since.
			StringBuilder why = new StringBuilder();
			Optional<JobBoard.Job> job = JobBoard.numbered(player, board, number, why);
			if (job.isEmpty()) {
				source.sendFailure(Component.literal(why.toString()));
				return 0;
			}
			chosen = List.of(job.get());
		} else {
			chosen = board.stream().filter(j -> j.kind().withoutNumber()).toList();
			if (chosen.isEmpty()) {
				source.sendFailure(Component.literal("The jobs on the board now (food, or what Sage's plan wants) are only brought "
					+ "by number, so you choose what to give. See /friends jobs, then /friends deliver <number>."));
				return 0;
			}
		}
		JobBoard.Delivered done = JobBoard.deliver(player, chest.get(), chosen, number > 0);
		if (done.total() == 0) {
			source.sendFailure(Component.literal(done.chestFull() ? "The supply chest is full."
				: "You have nothing in your main inventory that " + (number > 0 ? "job #" + number + " asks for" : "the camp asked for")
				+ ". Plain items only: never from your hotbar, and never tools, armour, buckets, totems, or anything enchanted, "
				+ "renamed or worn" + (number > 0 ? "." : "; food and Sage's plan only by number.")));
			return 0;
		}
		TownData data = TownData.get(server);
		data.remember(player);
		data.addHelped(player.getUUID(), done.total());
		camp.addStat("job_deliveries", done.total());
		Unity.add(level, "jobs", Math.max(1, done.total() / 16), 20);
		CompanionEntity thanker = null;
		double best = 24 * 24;
		for (CompanionEntity c : Companions.near(level, player.getBoundingBox().inflate(24))) {
			double d = c.distanceToSqr(player);
			if (d <= THANKS_RANGE * THANKS_RANGE) {
				Bonds.add(c, player, 2, Bonds.JOBS, 8);
			}
			if (d < best) {
				best = d;
				thanker = c;
			}
		}
		if (thanker != null) {
			Speech.say(thanker, Line.THANKS_DELIVERY, player.getName().getString());
		}
		CampNeeds.recompute(server); // the board and the friends see the delivery at once
		String forJob = number > 0 ? " for job #" + number + " (" + chosen.getFirst().source() + ")" : "";
		say(source, "You delivered " + done.describe() + " to the supply chest" + forJob + "." + (done.chestFull() ? " The chest is full now." : "")
			+ " You have helped with " + data.helped(player.getUUID()) + " things in all.", ChatFormatting.GREEN);
		if (number == 0 && board.stream().anyMatch(j -> !j.kind().withoutNumber())) {
			say(source, "Food and what Sage's plan wants are only brought by number: /friends deliver <number>.", ChatFormatting.GRAY);
		}
		return done.total();
	}

	// ------------------------------------------------------------------ notes

	private static int note(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String problem = Notes.leave(player, StringArgumentType.getString(ctx, "text"));
		if (problem != null) {
			source.sendFailure(Component.literal(problem));
			return 0;
		}
		say(source, "Note left for the camp. A friend will read it out the next time someone is at camp.", ChatFormatting.GREEN);
		return 1;
	}

	private static int notes(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<TownData.Note> notes = TownData.get(source.getServer()).notes();
		if (notes.isEmpty()) {
			say(source, "No notes yet. Leave one with /friends note <text>.", ChatFormatting.GRAY);
			return 0;
		}
		say(source, "Notes left for the camp (last " + TownData.MAX_NOTES + ")", ChatFormatting.GOLD);
		for (TownData.Note n : notes) {
			source.sendSuccess(() -> Component.literal("Day " + n.day + ", " + n.authorName + (n.read ? "" : " (not read out yet)") + ": ")
				.withStyle(ChatFormatting.GRAY).append(Component.literal(n.text).withStyle(ChatFormatting.WHITE)), false);
		}
		return notes.size();
	}

	// ---------------------------------------------------------------- mailbox

	private static int mailbox(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TownData data = TownData.get(server);
		HitResult hit = player.pick(Mail.REGISTER_REACH, 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
			&& SupplyChest.isValidStorage(player.level(), blockHit.getBlockPos())) {
			String problem = Mail.register(player, blockHit.getBlockPos());
			if (problem != null) {
				source.sendFailure(Component.literal(problem));
				return 0;
			}
			BlockPos pos = blockHit.getBlockPos();
			say(source, "Mailbox registered at " + Compass.coords(pos) + ". Friends will only ever put deliveries in it, "
				+ "never take anything out.", ChatFormatting.GREEN);
			data.mailbox(player.getUUID()).map(m -> Mail.outOfReach(server, m)).ifPresent(why -> say(source, why, ChatFormatting.YELLOW));
			if (!Mail.receives(server, player.getUUID())) {
				say(source, "Deliveries go to the camp's owner and trusted players: ask " + data.ownerName() + " to trust you.",
					ChatFormatting.YELLOW);
			}
			return 1;
		}
		Optional<TownData.Mailbox> box = data.mailbox(player.getUUID());
		if (box.isEmpty()) {
			say(source, "No mailbox yet. Look at a chest or barrel of your own (within 5 blocks) and use /friends mailbox.",
				ChatFormatting.GRAY);
			return 0;
		}
		say(source, "Your mailbox: " + Compass.coords(box.get().pos()) + " (" + box.get().dimension() + "). "
			+ "Ask for something with /friends send <item> <count>; /friends mailbox remove to stop deliveries.", ChatFormatting.GRAY);
		String why = Mail.outOfReach(server, box.get());
		if (why != null) {
			say(source, why, ChatFormatting.YELLOW);
		}
		long waiting = data.queue().stream().filter(r -> r.player().equals(player.getUUID())).count();
		if (waiting > 0) {
			say(source, waiting + " delivery" + (waiting == 1 ? "" : "s") + " waiting.", ChatFormatting.GRAY);
		}
		return 1;
	}

	private static int mailboxRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		TownData data = TownData.get(source.getServer());
		if (data.mailbox(player.getUUID()).isEmpty()) {
			source.sendFailure(Component.literal("You have no mailbox."));
			return 0;
		}
		data.setMailbox(player.getUUID(), null);
		Mail.invalidate();
		say(source, "Mailbox removed. No more deliveries will come.", ChatFormatting.YELLOW);
		return 1;
	}

	private static int send(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		if (!TownPermissions.require(source)) {
			return 0;
		}
		MinecraftServer server = source.getServer();
		TownData data = TownData.get(server);
		Optional<TownData.Mailbox> box = data.mailbox(player.getUUID());
		if (box.isEmpty()) {
			source.sendFailure(Component.literal("Register a mailbox first: look at a chest or barrel of your own and use /friends mailbox."));
			return 0;
		}
		String why = Mail.outOfReach(server, box.get());
		if (why != null) {
			source.sendFailure(Component.literal("Your mailbox is out of reach. " + why));
			return 0;
		}
		Identifier id = IdentifierArgument.getId(ctx, "item");
		Optional<Item> item = Mail.sendable(id);
		if (item.isEmpty()) {
			source.sendFailure(Component.literal("Friends cannot send that."));
			return 0;
		}
		int count = IntegerArgumentType.getInteger(ctx, "count");
		ServerLevel campLevel = campLevel(server);
		if (campLevel != null) {
			Optional<Container> chest = SupplyChest.of(campLevel);
			Item it = item.get();
			if (chest.isPresent() && SupplyChest.count(chest.get(), s -> Mail.matches(s, it)) == 0) {
				source.sendFailure(Component.literal("The supply chest has no " + new ItemStack(it).getHoverName().getString() + " just now."));
				return 0;
			}
		}
		long mine = data.queue().stream().filter(r -> r.player().equals(player.getUUID())).count();
		if (mine >= Mail.MAX_PER_PLAYER) {
			source.sendFailure(Component.literal("You already have " + mine + " deliveries waiting. Let the friends catch up first."));
			return 0;
		}
		long now = campLevel != null ? campLevel.getGameTime() : server.overworld().getGameTime();
		if (!data.enqueue(new TownData.SendRequest(player.getUUID(), player.getName().getString(), BuiltInRegistries.ITEM.getKey(item.get()),
			count, now))) {
			source.sendFailure(Component.literal("The friends have too many deliveries to make already. Try again later."));
			return 0;
		}
		Mail.invalidate();
		say(source, "Asked for " + count + " " + new ItemStack(item.get()).getHoverName().getString() + ". A friend will bring it to "
			+ "your mailbox by day (as much as the chest has when they pack).", ChatFormatting.GREEN);
		return 1;
	}

	private static ServerLevel campLevel(MinecraftServer server) {
		CampData camp = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, camp)) {
				return level;
			}
		}
		return null;
	}

	/** The kinds of item in the supply chest, for suggestions. */
	private static List<Identifier> chestItems(CommandSourceStack source) {
		Set<Identifier> ids = new LinkedHashSet<>();
		ServerLevel level = campLevel(source.getServer());
		if (level != null) {
			SupplyChest.of(level).ifPresent(chest -> {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					ItemStack s = chest.getItem(i);
					if (!s.isEmpty()) {
						ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()));
					}
				}
			});
		}
		return new ArrayList<>(ids);
	}

	// --------------------------------------------------------------- memorial

	/** Lines for {@code /friends camp}: the players the camp has lost. */
	static List<Component> memorial(MinecraftServer server) {
		List<Component> lines = new ArrayList<>();
		List<TownData.Memorial> memorials = TownData.get(server).memorials();
		if (memorials.isEmpty()) {
			return lines;
		}
		lines.add(Component.literal("Remembered: the players the camp has lost").withStyle(ChatFormatting.GRAY));
		for (TownData.Memorial m : memorials) {
			lines.add(Component.literal("  " + m.name() + ", day " + m.day() + " - " + m.cause() + " (" + Compass.coords(m.pos()) + ")")
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}
}
