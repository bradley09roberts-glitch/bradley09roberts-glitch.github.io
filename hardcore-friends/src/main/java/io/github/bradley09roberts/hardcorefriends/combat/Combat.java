package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Reach;
import io.github.bradley09roberts.hardcorefriends.ai.role.guard.EquipGearTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;

/**
 * Combat and gear for every friend: armour, shields and swords up the gear ladder (leather, iron, diamond), bows and
 * arrows for whoever has them, raising a shield against arrows and creepers, focusing on one target together, and
 * emergency healing (golden apples, healing potions). Also the night-safety fixes (in {@code camp/NightWatch}, the
 * goals in {@code ai/goal} and the guard and sleep jobs).
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES} (combat edits no blocks, so it registers no policy).
 */
public final class Combat {
	/** Arrows a friend keeps in the backpack. */
	public static final int ARROWS_KEPT = 64;

	private Combat() {
	}

	/** Wires the combat package in (called once, at start-up). */
	public static void init() {
		CombatLines.register();
		Smithing.addRecipes();
		FriendlyFire.register();

		// What every friend keeps through a trip to the chest: their weapon, a bow and arrows, a spare shield, and
		// emergency healing.
		KeepList.addCommonRule(new KeepList.Rule("weapon", s -> s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES), 1));
		KeepList.addCommonRule(new KeepList.Rule("bow", s -> s.is(Items.BOW), 1));
		KeepList.addCommonRule(new KeepList.Rule("arrows", s -> s.is(ItemTags.ARROWS), ARROWS_KEPT));
		KeepList.addCommonRule(new KeepList.Rule("shield", Gear::blocks, 1));
		KeepList.addCommonRule(new KeepList.Rule("healing", Gear::isHealing, GearPlan.HEALING_FIGHTER));
		KeepList.addCommonRule(new KeepList.Rule("fire resistance", Gear::isFireResistance, 1));

		// Every friend gears up from the chest (Aegis first), and anyone may smith.
		TaskRegistry.PACKS.add(id -> List.of(new EquipGearTask(), new SmithTask()));
		TaskScheduler.NIGHT_JOBS.add("combat.gear");
		TaskScheduler.FIT_WHEN_WEAK.add("combat.gear");

		CompanionEvents.GOALS.add((companion, goals, targets) -> {
			goals.addGoal(2, new ShieldGoal(companion));
			goals.addGoal(3, new BowAttackGoal(companion)); // the melee goal's priority: the two never run together
		});
		CompanionEvents.TICK.add((companion, level) -> {
			Healing.tick(companion, level);
			GearUp.tick(companion, level);
		});
		FriendsCommand.EXTENSIONS.add(GearCommand::register);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GearPlan.clear();
			Smithing.clear();
			Reach.clear();
		});
	}
}
