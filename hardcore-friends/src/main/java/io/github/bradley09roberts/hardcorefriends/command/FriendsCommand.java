package io.github.bradley09roberts.hardcorefriends.command;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import io.github.bradley09roberts.hardcorefriends.ai.role.RolePassives;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * {@code /friends}. Every sub-command works at permission level 0 with cheats disabled, and none of them hands out
 * items, moves players, or changes time, weather, game mode or difficulty.
 */
public final class FriendsCommand {
	public static final int RECRUIT_COST = 2;
	/** A dismissed friend needs one in-game day before they will rejoin. */
	public static final long REJOIN_AFTER_DISMISSAL = 24000L;
	/** A friend hurt within this many ticks is in a fight and will not leave. */
	public static final int NO_DISMISS_AFTER_HURT = 200;

	private FriendsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("friends")
			.executes(FriendsCommand::help)
			.then(Commands.literal("help").executes(FriendsCommand::help))
			.then(Commands.literal("list").executes(FriendsCommand::list))
			.then(Commands.literal("recruit").then(friendArg(false).executes(FriendsCommand::recruit)))
			.then(Commands.literal("dismiss").then(friendArg(false).executes(FriendsCommand::dismiss)))
			.then(Commands.literal("follow").then(friendArg(true).executes(ctx -> order(ctx, CompanionMode.FOLLOW))))
			.then(Commands.literal("stay").then(friendArg(true).executes(ctx -> order(ctx, CompanionMode.STAY))))
			.then(Commands.literal("work").then(friendArg(true).executes(ctx -> order(ctx, CompanionMode.WORK))))
			.then(Commands.literal("where").then(friendArg(false).executes(FriendsCommand::where)))
			.then(Commands.literal("backpack").then(friendArg(false).executes(FriendsCommand::backpack)))
			.then(Commands.literal("camp")
				.executes(FriendsCommand::campStatus)
				.then(Commands.literal("set").executes(FriendsCommand::campSet)))
			.then(Commands.literal("chest").executes(FriendsCommand::chest))
			.then(Commands.literal("unity").executes(FriendsCommand::unity))
			.then(Commands.literal("advice").executes(FriendsCommand::advice))
			.then(Commands.literal("plan").executes(FriendsCommand::plan))
			.then(Commands.literal("log").executes(FriendsCommand::log))
			.then(Commands.literal("chatter")
				.then(Commands.argument("level", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"quiet", "normal", "chatty"}, b))
					.executes(FriendsCommand::chatter))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> friendArg(boolean allowAll) {
		return Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> {
			List<String> names = new ArrayList<>();
			for (FriendId id : FriendId.values()) {
				names.add(id.key());
			}
			if (allowAll) {
				names.add("all");
			}
			return SharedSuggestionProvider.suggest(names, builder);
		});
	}

	// ------------------------------------------------------------------- help

	private static int help(CommandContext<CommandSourceStack> ctx) {
		String[] lines = {
			"Hardcore Friends - nine companions, one life each.",
			"/friends recruit <name>  - costs " + RECRUIT_COST + " common food (bread, apples, carrots, potatoes, meat...)",
			"/friends list | where <name> | backpack <name>",
			"/friends follow|stay|work <name|all>",
			"/friends camp | camp set | chest (look at a chest or barrel)",
			"/friends unity | plan | advice | log | chatter <quiet|normal|chatty>",
			"/friends dismiss <name>  - they leave and drop their backpack",
			"Right-click a friend: status. Sneak + right-click: open backpack. Give food to heal them.",
			"Friends: Fern (farmer), Oak (builder), Flint (miner), Scout (explorer), Spark (inventor),",
			"Aegis (warrior), Sage (strategist), Terra (landscaper), Rowan (forager)."
		};
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	// ------------------------------------------------------------------- list

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		CampData data = Camp.data(source.getServer());
		source.sendSuccess(() -> Component.literal("Your friends (Unity: " + Unity.levelName(Unity.level(data.unity())) + ")")
			.withStyle(ChatFormatting.GOLD), false);
		for (FriendId id : FriendId.values()) {
			CampData.Ledger ledger = data.ledger(id);
			MutableComponent line = Speech.prefix(id).append(Component.literal(id.role().title() + ": ").withStyle(ChatFormatting.GRAY));
			Optional<CompanionEntity> live = Companions.find(id);
			String text = switch (ledger.state) {
				case NEVER_RECRUITED -> "not recruited yet";
				case DISMISSED -> "dismissed (can be recruited again a day after leaving)";
				case DEAD -> deadText(source.getLevel(), ledger);
				case ALIVE -> live.map(c -> c.activity() + String.format(Locale.ROOT, " (%.0f hp)", c.getHealth()))
					.orElse("away (last seen " + posText(ledger.lastKnownPos) + ")");
			};
			line.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static String deadText(ServerLevel level, CampData.Ledger ledger) {
		int days = FriendsConfig.get().deadFriendsReturnAfterDays;
		String cause = ledger.deathCause.isEmpty() ? "fallen" : ledger.deathCause;
		if (days < 0) {
			return "fallen for good - " + cause;
		}
		long waitTicks = ledger.diedAtGameTime + days * 24000L - level.getOverworldClockTime();
		return waitTicks <= 0 ? "fallen - " + cause + " (a newcomer of the same name could join)"
			: "fallen - " + cause + String.format(Locale.ROOT, " (%.1f days of mourning left)", waitTicks / 24000.0);
	}

	private static String posText(BlockPos pos) {
		return pos == null ? "somewhere" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	// ---------------------------------------------------------------- recruit

	private static int recruit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Optional<FriendId> parsed = FriendId.byKey(StringArgumentType.getString(ctx, "name"));
		if (parsed.isEmpty()) {
			source.sendFailure(Component.literal("Unknown friend. Try: " + names()));
			return 0;
		}
		FriendId id = parsed.get();
		ServerLevel level = player.level();
		CampData data = Camp.data(source.getServer());
		CampData.Ledger ledger = data.ledger(id);
		if (ledger.state == CampData.LifeState.ALIVE) {
			if (Companions.find(id).isPresent() || !isLost(source, ledger)) {
				source.sendFailure(Component.literal(id.displayName() + " is already part of your team (last seen "
					+ posText(ledger.lastKnownPos) + ")."));
				return 0;
			}
		}
		if (ledger.state == CampData.LifeState.DEAD) {
			int days = FriendsConfig.get().deadFriendsReturnAfterDays;
			if (days < 0) {
				source.sendFailure(Component.literal(id.displayName() + " has fallen. In this world, the fallen do not return."));
				return 0;
			}
			long wait = ledger.diedAtGameTime + days * 24000L - level.getOverworldClockTime();
			if (wait > 0) {
				source.sendFailure(Component.literal(String.format(Locale.ROOT,
					"The team is still mourning %s. A newcomer of that name may arrive in %.1f days.", id.displayName(), wait / 24000.0)));
				return 0;
			}
		}
		if (ledger.state == CampData.LifeState.DISMISSED) {
			long wait = ledger.dismissedAtGameTime + REJOIN_AFTER_DISMISSAL - level.getOverworldClockTime();
			if (wait > 0) {
				source.sendFailure(Component.literal(String.format(Locale.ROOT,
					"%s only just left the team. Give them %.1f more days before asking them back.", id.displayName(), wait / 24000.0)));
				return 0;
			}
		}
		Inventory inv = player.getInventory();
		int food = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModTags.RECRUIT_FOOD)) {
				food += s.getCount();
			}
		}
		if (food < RECRUIT_COST) {
			source.sendFailure(Component.literal("Recruiting " + id.displayName() + " costs " + RECRUIT_COST
				+ " common food items (bread, apples, carrots, potatoes, berries, meat or fish). You have " + food + "."));
			return 0;
		}
		BlockPos spot = findSpawnSpot(level, player.blockPosition());
		CompanionEntity companion = ModEntities.COMPANION.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (companion == null || spot == null) {
			source.sendFailure(Component.literal("There is no safe space next to you for " + id.displayName() + " to stand."));
			return 0;
		}
		int toTake = RECRUIT_COST;
		for (int i = 0; i < inv.getContainerSize() && toTake > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModTags.RECRUIT_FOOD)) {
				int take = Math.min(toTake, s.getCount());
				s.shrink(take);
				toTake -= take;
			}
		}
		inv.setChanged();
		companion.setFriendId(id);
		companion.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		companion.setHomePos(player.blockPosition());
		companion.setHealth(companion.getMaxHealth());
		companion.backpack().setCapacity(Unity.backpackSlots(source.getServer()));
		// The starter tool comes once per friend (a newcomer after a death gets their own). A dismissed friend's tool
		// went into the backpack they left behind, so rejoining does not make a new one.
		if (id.starterTool() != null && (!ledger.starterGiven || ledger.state == CampData.LifeState.DEAD)) {
			companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(id.starterTool()));
		}
		ledger.starterGiven = true;
		boolean hasCampHere = Camp.isCampLevel(level, data);
		companion.setMode(hasCampHere ? CompanionMode.WORK : CompanionMode.FOLLOW, player);
		level.addFreshEntity(companion);
		ledger.state = CampData.LifeState.ALIVE;
		ledger.entityId = companion.getUUID();
		ledger.lastKnownPos = spot;
		ledger.lastKnownDimension = Camp.dimensionId(level);
		data.touchLedger();
		Companions.track(companion);
		Speech.say(companion, Line.RECRUITED, player.getName().getString());
		source.sendSuccess(() -> Component.literal(id.displayName() + " the " + id.role().title().toLowerCase(Locale.ROOT)
			+ " joins you (" + id.personality() + ")."
			+ (hasCampHere ? "" : " No camp yet, so they will follow you. Use /friends camp set where you want to live."))
			.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/** An entity recorded as alive is lost if its last chunk is loaded but it is nowhere to be found. */
	private static boolean isLost(CommandSourceStack source, CampData.Ledger ledger) {
		if (ledger.entityId == null || ledger.lastKnownPos == null) {
			return true;
		}
		for (ServerLevel level : source.getServer().getAllLevels()) {
			if (Camp.dimensionId(level).equals(ledger.lastKnownDimension)) {
				if (!level.isLoaded(ledger.lastKnownPos)) {
					return false; // cannot check; assume still out there
				}
				return level.getEntity(ledger.entityId) == null;
			}
		}
		return true;
	}

	private static BlockPos findSpawnSpot(ServerLevel level, BlockPos around) {
		for (int r = 1; r <= 3; r++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos p = around.relative(d, r).above(dy);
					BlockState below = level.getBlockState(p.below());
					if (below.isFaceSturdy(level, p.below(), Direction.UP) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
						&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
						&& level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty()) {
						return p;
					}
				}
			}
		}
		return null;
	}

	private static String names() {
		StringBuilder sb = new StringBuilder();
		for (FriendId id : FriendId.values()) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(id.key());
		}
		return sb.toString();
	}

	// -------------------------------------------------------- dismiss & orders

	private static int dismiss(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Optional<CompanionEntity> found = findLoaded(ctx);
		if (found.isEmpty()) {
			return 0;
		}
		CompanionEntity c = found.get();
		ServerLevel level = (ServerLevel) c.level();
		if (c.ticksSinceDamaged() < NO_DISMISS_AFTER_HURT) {
			// Leaving mid-fight would let a dying friend walk away from a hardcore death.
			source.sendFailure(Component.literal(c.friendId().displayName() + " is in the middle of a fight and will not leave now."));
			return 0;
		}
		Speech.say(c, Line.DISMISSED);
		c.dropBackpack(level);
		CampData data = Camp.data(source.getServer());
		CampData.Ledger ledger = data.ledger(c.friendId());
		ledger.state = CampData.LifeState.DISMISSED;
		ledger.entityId = null;
		ledger.dismissedAtGameTime = level.getOverworldClockTime();
		data.touchLedger();
		Companions.untrack(c);
		c.discard();
		Unity.lose(source.getServer(), 20);
		source.sendSuccess(() -> Component.literal(c.friendId().displayName() + " has left the team. Their backpack was left behind.")
			.withStyle(ChatFormatting.YELLOW), false);
		return 1;
	}

	private static int order(CommandContext<CommandSourceStack> ctx, CompanionMode mode) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "name");
		List<CompanionEntity> targets = new ArrayList<>();
		if ("all".equalsIgnoreCase(name)) {
			for (CompanionEntity c : Companions.in(player.level())) {
				if (c.distanceToSqr(player) <= 64 * 64) {
					targets.add(c);
				}
			}
		} else {
			findLoaded(ctx).ifPresent(targets::add);
		}
		if (targets.isEmpty()) {
			if ("all".equalsIgnoreCase(name)) {
				source.sendFailure(Component.literal("No friends within 64 blocks."));
			}
			return 0;
		}
		if (mode == CompanionMode.WORK && Camp.center(player.level()).isEmpty()) {
			source.sendSuccess(() -> Component.literal("Tip: without a camp they work around where they were recruited. Use /friends camp set.")
				.withStyle(ChatFormatting.GRAY), false);
		}
		for (CompanionEntity c : targets) {
			c.setMode(mode, player);
			Speech.say(c, switch (mode) {
				case FOLLOW -> Line.FOLLOW;
				case STAY -> Line.STAY;
				case WORK -> Line.WORK;
			}, player.getName().getString());
		}
		int n = targets.size();
		source.sendSuccess(() -> Component.literal(n + (n == 1 ? " friend" : " friends") + " now: "
			+ mode.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GREEN), false);
		return n;
	}

	private static Optional<CompanionEntity> findLoaded(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Optional<FriendId> id = FriendId.byKey(StringArgumentType.getString(ctx, "name"));
		if (id.isEmpty()) {
			source.sendFailure(Component.literal("Unknown friend. Try: " + names()));
			return Optional.empty();
		}
		Optional<CompanionEntity> c = Companions.find(id.get());
		if (c.isEmpty()) {
			CampData.Ledger ledger = Camp.data(source.getServer()).ledger(id.get());
			source.sendFailure(Component.literal(id.get().displayName() + (ledger.state == CampData.LifeState.ALIVE
				? " is too far away to hear you (last seen " + posText(ledger.lastKnownPos) + ")."
				: " is not on your team right now.")));
		}
		return c;
	}

	// ------------------------------------------------------------ info commands

	private static int where(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Optional<FriendId> id = FriendId.byKey(StringArgumentType.getString(ctx, "name"));
		if (id.isEmpty()) {
			source.sendFailure(Component.literal("Unknown friend. Try: " + names()));
			return 0;
		}
		Optional<CompanionEntity> live = Companions.find(id.get());
		CampData.Ledger ledger = Camp.data(source.getServer()).ledger(id.get());
		String text = live.map(c -> {
			BlockPos p = c.blockPosition();
			double dist = Math.sqrt(c.distanceToSqr(source.getPosition()));
			return id.get().displayName() + " is at " + posText(p) + String.format(Locale.ROOT, " (%.0f blocks away), ", dist) + c.activity() + ".";
		}).orElse(id.get().displayName() + " was last seen at " + posText(ledger.lastKnownPos) + " in " + ledger.lastKnownDimension + ".");
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int backpack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Optional<CompanionEntity> found = findLoaded(ctx);
		if (found.isEmpty()) {
			return 0;
		}
		CompanionEntity c = found.get();
		if (c.distanceToSqr(player) <= 36) {
			c.openBackpack(player);
			return 1;
		}
		StringBuilder sb = new StringBuilder();
		for (ItemStack s : c.backpack().stacks()) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(s.getCount()).append(' ').append(s.getHoverName().getString());
		}
		ItemStack hand = c.getMainHandItem();
		String holding = hand.isEmpty() ? "nothing" : hand.getHoverName().getString()
			+ (hand.isDamageableItem() ? String.format(Locale.ROOT, " (%d/%d)", hand.getMaxDamage() - hand.getDamageValue(), hand.getMaxDamage()) : "");
		String contents = sb.isEmpty() ? "empty" : sb.toString();
		source.sendSuccess(() -> Component.literal(c.friendId().displayName() + " holds " + holding + ". Backpack ("
			+ c.backpack().usedSlots() + "/" + c.backpack().capacity() + "): " + contents + ". Come within 6 blocks to open it."), false);
		return 1;
	}

	private static int campStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		CampData data = Camp.data(source.getServer());
		if (data.campPos().isEmpty()) {
			source.sendSuccess(() -> Component.literal("No camp yet. Stand where you want to live and run /friends camp set.")
				.withStyle(ChatFormatting.YELLOW), false);
			return 1;
		}
		BlockPos camp = data.campPos().get();
		int stage = data.stage();
		List<String> lines = new ArrayList<>();
		lines.add("Camp: " + Camp.stageName(stage) + " at " + posText(camp) + " (" + data.campDimension() + "), radius " + Camp.radius(data));
		lines.add("Supply chest: " + data.chestPos().map(FriendsCommand::posText).orElse("none yet - Oak will build one, or look at a chest and use /friends chest"));
		lines.add("Built: " + (data.completed().isEmpty() ? "nothing yet" : String.join(", ", data.completed())));
		if (stage < Camp.MAX_STAGE) {
			int needUnity = Camp.STAGE_UNITY[stage + 1];
			lines.add("Next stage: " + Camp.stageName(stage + 1) + " (Unity " + data.unity() + "/" + needUnity + " and this stage's buildings)");
		}
		String shortage = CampNeeds.shortageText(source.getLevel().getGameTime());
		if (!shortage.isEmpty()) {
			lines.add("Oak still needs: " + shortage);
		}
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int campSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();
		CampData data = Camp.data(source.getServer());
		BlockPos pos = player.blockPosition();
		data.setCamp(pos, Camp.dimensionId(level));
		if (data.chestPos().isEmpty() || !SupplyChest.isValidStorage(level, data.chestPos().get())) {
			BlockPos found = nearestStorage(level, pos, 8);
			data.setChestPos(found);
		}
		String chest = data.chestPos().map(p -> " Supply chest linked at " + posText(p) + ".").orElse(
			" No chest nearby: look at one and use /friends chest, or Oak will build one from planks.");
		source.sendSuccess(() -> Component.literal("Camp set at " + posText(pos) + " (radius " + Camp.radius(data) + ")." + chest
			+ " Friends will only build and landscape inside the camp, and gather just outside it.")
			.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static BlockPos nearestStorage(ServerLevel level, BlockPos centre, int radius) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -3, -radius), centre.offset(radius, 3, radius))) {
			if (SupplyChest.isValidStorage(level, p)) {
				double d = p.distSqr(centre);
				if (d < bestDist) {
					bestDist = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	private static int chest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		HitResult hit = player.pick(5.0, 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
			|| !SupplyChest.isValidStorage(player.level(), blockHit.getBlockPos())) {
			source.sendFailure(Component.literal("Look at a chest or barrel (within 5 blocks) and try again."));
			return 0;
		}
		CampData data = Camp.data(source.getServer());
		BlockPos pos = blockHit.getBlockPos();
		if (data.campPos().isEmpty()) {
			data.setCamp(player.blockPosition(), Camp.dimensionId(player.level()));
			source.sendSuccess(() -> Component.literal("No camp was set, so your camp is now here.").withStyle(ChatFormatting.GRAY), false);
		} else if (!Camp.isCampLevel(player.level(), data)) {
			source.sendFailure(Component.literal("The supply chest must be in the same dimension as your camp."));
			return 0;
		}
		data.setChestPos(pos);
		source.sendSuccess(() -> Component.literal("Shared supply chest linked at " + posText(pos)
			+ ". Friends will deposit spare resources here and restock from it.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int unity(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		int score = Camp.data(source.getServer()).unity();
		int level = Unity.level(score);
		int next = Unity.toNextLevel(score);
		source.sendSuccess(() -> Component.literal("Unity bond: " + Unity.levelName(level) + " (" + score + "/1000"
			+ (next > 0 ? ", " + next + " to " + Unity.levelName(level + 1) : "") + ")").withStyle(ChatFormatting.GOLD), false);
		for (int i = 1; i <= level; i++) {
			int idx = i;
			source.sendSuccess(() -> Component.literal(" - " + Unity.BONUSES[idx]).withStyle(ChatFormatting.GRAY), false);
		}
		source.sendSuccess(() -> Component.literal("Grows with time together, deliveries, sharing, defending each other and finishing camp buildings. "
			+ "Losing a friend costs 80.").withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	private static int advice(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String advice = RolePassives.advice(player);
		Optional<CompanionEntity> sage = Companions.find(FriendId.SAGE);
		if (sage.isPresent()) {
			Speech.tell(player, FriendId.SAGE, advice);
		} else {
			player.sendSystemMessage(Component.literal("Survival tip: " + advice).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int plan(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		CampNeeds.Need focus = CampNeeds.focus();
		source.sendSuccess(() -> Component.literal("Team plan - focus: " + (focus == null ? "steady work" : focus.label())
			+ ". Short of: " + CampNeeds.summary()).withStyle(ChatFormatting.GOLD), false);
		for (CompanionEntity c : Companions.all()) {
			source.sendSuccess(() -> Speech.prefix(c.friendId()).append(Component.literal(c.activity()).withStyle(ChatFormatting.WHITE)), false);
		}
		return 1;
	}

	private static int log(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<String> recent = new ArrayList<>();
		Iterator<String> it = Camp.data(source.getServer()).editLog().descendingIterator();
		while (it.hasNext() && recent.size() < 12) {
			recent.add(it.next());
		}
		if (recent.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No block changes by friends since the server started.").withStyle(ChatFormatting.GRAY), false);
		}
		for (String line : recent) {
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return recent.size();
	}

	private static int chatter(CommandContext<CommandSourceStack> ctx) {
		String level = StringArgumentType.getString(ctx, "level").toLowerCase(Locale.ROOT);
		if (!level.equals("quiet") && !level.equals("normal") && !level.equals("chatty")) {
			ctx.getSource().sendFailure(Component.literal("Use quiet, normal or chatty."));
			return 0;
		}
		FriendsConfig.get().chatter = level;
		FriendsConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal("Friends' chatter: " + level + " (danger warnings always show).")
			.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}
