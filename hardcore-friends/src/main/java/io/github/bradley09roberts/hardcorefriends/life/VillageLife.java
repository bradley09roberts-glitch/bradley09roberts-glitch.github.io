package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;

/**
 * Village life: a calendar, market days, feasts and festivals, birthdays, music in the evenings, funerals and graves
 * for those who die, and the Village Chronicle, a book of everything that happens.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}. Every block it changes (graves, festival lights, a
 * musician's note block, the town hall lectern's book) goes through the edit guard's existing BUILD and INVENT rules,
 * on the friends' own blocks inside the camp, so it registers no rules of its own.
 *
 * <p>The parts: {@link Calendar} (the date, the week, the seasons, the feasts), {@link LifeData} (what the world
 * remembers), {@link Gatherings} (the feasts and funerals, run on the server) with {@link GatherTask} (going to one),
 * {@link FeastCookTask} and {@link LightsTask}; {@link MusicTask} and {@link Tunes}; {@link MarketDay} with
 * {@link MarketStallTask} and {@link MarketVisitTask}; {@link Birthdays}; {@link Grief} (deaths and mourning),
 * {@link Graves}, {@link GraveTask} and {@link VisitGraveTask}; {@link TidyUpTask}; {@link Chronicle} and
 * {@link ChronicleTask}; {@link LifeCommands}, {@link LifeLines}, {@link Places} and {@link Later}.
 */
public final class VillageLife {
	/**
	 * The start of the camp site keys that reserve the graves' ground ({@code life.grave.N}): kept clear by the builders
	 * and the town plan, but no building still to come, so the landscaping lights and plants round them as anywhere.
	 */
	public static final String GRAVE_SITES = "life.grave.";

	private VillageLife() {
	}

	public static void init() {
		LifeLines.register();
		recipes();
		TaskRegistry.PACKS.add(id -> List.of(new GatherTask(), new FeastCookTask(), new LightsTask(), new MusicTask(), new GraveTask(),
			new VisitGraveTask(), new TidyUpTask(), new MarketStallTask(), new MarketVisitTask(), new ChronicleTask()));
		TaskScheduler.JOB_FILTERS.add(Gatherings::mayDo);
		CompanionEvents.DEATH.add(Grief::died);
		CompanionEvents.DISMISSED.add(Grief::dismissed);
		CompanionEvents.TICK.add(Grief::tick);
		Construction.FINISHED.add(Chronicle::buildingFinished);
		FriendsCommand.EXTENSIONS.add(LifeCommands::register);
		FriendsCommand.CAMP_STATUS.add(VillageLife::campStatus);
		ServerTickEvents.END_SERVER_TICK.register(VillageLife::serverTick);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/**
	 * Writes a line in the Village Chronicle, dated today ("Raiders came to the village, and were driven off."). For the
	 * other packages: a short sentence in plain words, a full stop at the end. Server thread only.
	 */
	public static void chronicle(MinecraftServer server, String text) {
		Chronicle.write(server, text);
	}

	/** The vanilla recipes village life needs, unless another package already taught them. */
	private static void recipes() {
		recipe(Items.NOTE_BLOCK, 1, true, Crafting.of(ItemTags.PLANKS, 8), Crafting.of(Items.REDSTONE, 1));
		recipe(Items.WRITABLE_BOOK, 1, false, Crafting.of(Items.BOOK, 1), Crafting.of(Items.INK_SAC, 1), Crafting.of(Items.FEATHER, 1));
		// Any planks make oak signs here (the game would match the wood): a grave's sign is a sign.
		recipe(Items.OAK_SIGN, 3, true, Crafting.of(ItemTags.PLANKS, 6), Crafting.of(Items.STICK, 1));
	}

	private static void recipe(Item output, int count, boolean table, Crafting.Ingredient... inputs) {
		if (!Crafting.hasAddedRecipe(output)) {
			Crafting.addRecipe(output, count, table, inputs);
		}
	}

	private static void serverTick(MinecraftServer server) {
		try {
			Later.tick(server);
			int t = server.getTickCount();
			if (t % 20 == 9) {
				Gatherings.second(server);
			}
			if (t % 100 == 47) {
				Chronicle.poll(server);
				Birthdays.check(server);
			}
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Village life failed this tick", e);
		}
	}

	/** A line for {@code /friends camp}: the date and what is on. */
	private static List<Component> campStatus(MinecraftServer server) {
		List<Component> lines = new ArrayList<>();
		if (Camp.data(server).campPos().isEmpty()) {
			return lines;
		}
		long day = Calendar.today(server);
		StringBuilder sb = new StringBuilder("Calendar: " + Calendar.weekdayName(day) + ", day " + (day + 1) + ", "
			+ Calendar.seasonName(day));
		Calendar.Feast feast = Calendar.feastOn(day);
		if (feast != null) {
			sb.append(" (").append(feast.phrase()).append(")");
		} else if (Calendar.marketDay(day)) {
			sb.append(" (market day)");
		}
		sb.append(". The Chronicle has ").append(LifeData.get(server).entries.size()).append(" lines (/friends calendar, /friends chronicle).");
		lines.add(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	private static void clear() {
		Gatherings.clear();
		Later.clear();
		Chronicle.clear();
		MusicTask.clear();
		LightsTask.clear();
		GraveTask.clear();
		TidyUpTask.clear();
		FeastCookTask.clear();
		MarketVisitTask.clear();
		MarketDay.clear();
		ChronicleTask.clear();
	}
}
