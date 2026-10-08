package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.progress.work.AnvilTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.BrewTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.CaneTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.DeepMineTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.EnchantTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.ObsidianTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.PickaxeTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.StationsTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.WartTask;
import io.github.bradley09roberts.hardcorefriends.progress.work.WorkshopTask;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Beating the game, step by step: Sage's long-term plan (iron, diamonds, enchanting, the Nether, blaze rods and
 * ender pearls, eyes of ender, the stronghold, the End), and the work it needs: deep mining at diamond level with lava
 * safety, making obsidian, books and bookshelves, enchanting, repairs at the anvil, brewing, and a sugar cane farm.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Progression {
	private Progression() {
	}

	public static void init() {
		WorldEditGuard.POLICIES.put(WorldEditGuard.Reason.CAST, new CastPolicy());
		WorldEditGuard.LISTENERS.add(Experience::onEdit);
		CompanionEvents.HIT.add(Experience::onHit);
		CampNeeds.EXTRA.add(ProgressPlan::extraNeeds);
		ProgressLines.register();
		recipes();
		jobs();
		FriendsCommand.EXTENSIONS.add(GoalsCommand::register);
		ServerTickEvents.END_SERVER_TICK.register(ProgressPlan::tick);
		// Obsidian a player breaks is no longer the friends' cast obsidian, whatever is put there later.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && state.is(Blocks.OBSIDIAN)) {
				ProgressData.get(serverLevel.getServer()).forgetCast(serverLevel, pos);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CampStock.clear();
			ProgressPlan.clear();
		});
	}

	/** Every recipe the plan's work needs, with vanilla quantities. */
	private static void recipes() {
		Crafting.addRecipe(Items.PAPER, 3, true, Crafting.of(Items.SUGAR_CANE, 3));
		Crafting.addRecipe(Items.BOOK, 1, false, Crafting.of(Items.PAPER, 3), Crafting.of(Items.LEATHER, 1));
		Crafting.addRecipe(Items.BOOKSHELF, 1, true, Crafting.of(ItemTags.PLANKS, 6), Crafting.of(Items.BOOK, 3));
		Crafting.addRecipe(Items.ENCHANTING_TABLE, 1, true, Crafting.of(Items.BOOK, 1), Crafting.of(Items.DIAMOND, 2),
			Crafting.of(Items.OBSIDIAN, 4));
		Crafting.addRecipe(Items.BUCKET, 1, true, Crafting.of(Items.IRON_INGOT, 3));
		Crafting.addRecipe(Items.FLINT_AND_STEEL, 1, false, Crafting.of(Items.IRON_INGOT, 1), Crafting.of(Items.FLINT, 1));
		Crafting.addRecipe(Items.GLASS_BOTTLE, 3, true, Crafting.of(Items.GLASS, 3));
		Crafting.addRecipe(Items.IRON_BLOCK, 1, true, Crafting.of(Items.IRON_INGOT, 9));
		Crafting.addRecipe(Items.ANVIL, 1, true, Crafting.of(Items.IRON_BLOCK, 3), Crafting.of(Items.IRON_INGOT, 4));
		Crafting.addRecipe(Items.BREWING_STAND, 1, true, Crafting.of(Items.BLAZE_ROD, 1), Crafting.of(ItemTags.STONE_TOOL_MATERIALS, 3));
		Crafting.addRecipe(Items.BLAZE_POWDER, 2, false, Crafting.of(Items.BLAZE_ROD, 1));
		Crafting.addRecipe(Items.ENDER_EYE, 1, false, Crafting.of(Items.ENDER_PEARL, 1), Crafting.of(Items.BLAZE_POWDER, 1));
		Crafting.addRecipe(Items.GOLD_NUGGET, 9, false, Crafting.of(Items.GOLD_INGOT, 1));
		Crafting.addRecipe(Items.GLISTERING_MELON_SLICE, 1, true, Crafting.of(Items.GOLD_NUGGET, 8), Crafting.of(Items.MELON_SLICE, 1));
		Crafting.addRecipe(Items.SUGAR, 1, false, Crafting.of(Items.SUGAR_CANE, 1));
		Crafting.addRecipe(Items.FERMENTED_SPIDER_EYE, 1, false, Crafting.of(Items.SPIDER_EYE, 1),
			Crafting.of(Items.BROWN_MUSHROOM, 1), Crafting.of(Items.SUGAR, 1));
		Crafting.addRecipe(Items.MAGMA_CREAM, 1, false, Crafting.of(Items.BLAZE_POWDER, 1), Crafting.of(Items.SLIME_BALL, 1));
		Crafting.addRecipe(Items.DIAMOND_PICKAXE, 1, true, Crafting.of(Items.DIAMOND, 3), Crafting.of(Items.STICK, 2));
	}

	/**
	 * The plan's jobs, for everyone, each wrapped for its speciality so the specialist does it first: deep mining,
	 * obsidian and the better pickaxe for the miner; sugar cane and nether wart for the farmer; the work stations and
	 * the anvil for the builder; the workshop and enchanting for the strategist; brewing for the inventor.
	 */
	private static void jobs() {
		SpecialityTask.EXCLUSIVE.addAll(Set.of("flint.deep_mine", "flint.obsidian", "fern.sugar_cane", "fern.nether_wart",
			"oak.stations", "oak.anvil", "sage.workshop", "sage.enchant", "spark.brew"));
		TaskRegistry.PACKS.add(id -> List.of(
			new SpecialityTask(new DeepMineTask(), Role.MINER),
			new SpecialityTask(new ObsidianTask(), Role.MINER),
			new SpecialityTask(new PickaxeTask(), Role.MINER),
			new SpecialityTask(new CaneTask(), Role.FARMER),
			new SpecialityTask(new WartTask(), Role.FARMER),
			new SpecialityTask(new StationsTask(), Role.BUILDER),
			new SpecialityTask(new AnvilTask(), Role.BUILDER),
			new SpecialityTask(new WorkshopTask(), Role.STRATEGIST),
			new SpecialityTask(new EnchantTask(), Role.STRATEGIST),
			new SpecialityTask(new BrewTask(), Role.INVENTOR)));
	}
}
