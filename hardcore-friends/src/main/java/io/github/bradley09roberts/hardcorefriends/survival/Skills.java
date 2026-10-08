package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Friends get better at what they do. Each friend has experience in every kind of work (farming, building, mining
 * and so on) and in fighting, kept with them in {@code extra()} under {@value #KEY}, and a level from 0 to
 * {@value #MAX_LEVEL} for each, the steps growing as they go ({@link #threshold}). Work earns experience when a job
 * of that kind is finished (more for a longer job), fighting when a blow lands on a monster (more for the last one).
 *
 * <p>What a level brings: work goes up to 20% faster at level 10 (folded into {@code Speciality.skill}, so a
 * specialist always stays the fastest at their own work); fighting adds up to +2 attack damage and +4 health. A
 * friend who goes up a level says so.
 */
public final class Skills {
	public static final String KEY = "survival.skills";
	/** The skill name for fighting (work skills use their work's name: "farming", "building"...). */
	public static final String FIGHTING = "fighting";
	public static final int MAX_LEVEL = 10;
	/** Work speed at level 10, on top of the friend's own skill. */
	public static final double MAX_WORK_BONUS = 0.2;
	public static final double ATTACK_PER_LEVEL = 0.2;
	public static final double HEALTH_PER_LEVEL = 0.4;
	private static final Identifier ATTACK_ID = Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "skill_attack");
	private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "skill_health");
	/** The most experience one finished job gives. */
	private static final int MAX_PER_JOB = 5;
	/** Ticks of work worth one more point of experience. */
	private static final int TICKS_PER_POINT = 600;

	private Skills() {
	}

	/** Every skill a friend has, in display order: the nine kinds of work, then fighting. */
	public static List<String> kinds() {
		List<String> kinds = new ArrayList<>();
		for (Role role : Role.values()) {
			kinds.add(Speciality.workName(role));
		}
		kinds.add(FIGHTING);
		return kinds;
	}

	/** Experience needed for a level: 15, 60, 135 ... 1500 at level 10. */
	public static int threshold(int level) {
		return 15 * level * level;
	}

	/** The level a total of experience reaches. */
	public static int levelFor(int xp) {
		int level = 0;
		while (level < MAX_LEVEL && xp >= threshold(level + 1)) {
			level++;
		}
		return level;
	}

	public static int xp(CompanionEntity c, String kind) {
		return c.extra().getCompoundOrEmpty(KEY).getIntOr(kind, 0);
	}

	public static int level(CompanionEntity c, String kind) {
		return levelFor(xp(c, kind));
	}

	/** Work speed bonus from practice at a kind of work: 0 to {@value #MAX_WORK_BONUS}. */
	public static double workBonus(CompanionEntity c, Role work) {
		return level(c, Speciality.workName(work)) * MAX_WORK_BONUS / MAX_LEVEL;
	}

	/** Adds experience; a friend who reaches a new level says so (and grows stronger, for fighting). */
	public static void add(CompanionEntity c, String kind, int amount) {
		if (amount <= 0 || !c.isTeamMember()) {
			return;
		}
		CompoundTag tag = c.extra().getCompoundOrEmpty(KEY).copy();
		int before = tag.getIntOr(kind, 0);
		int after = (int) Math.min(Integer.MAX_VALUE / 2, (long) before + amount);
		tag.putInt(kind, after);
		c.extra().put(KEY, tag);
		int oldLevel = levelFor(before);
		int newLevel = levelFor(after);
		if (newLevel > oldLevel) {
			Speech.say(c, Line.LEVEL_UP, kind, Integer.toString(newLevel));
			if (FIGHTING.equals(kind)) {
				applyCombat(c);
			}
		}
	}

	/** A {@code TaskScheduler.JOB_DONE} listener: a finished job earns experience in its kind of work. */
	static void onJobDone(CompanionEntity c, CompanionTask task, boolean success, long ticks) {
		if (!success) {
			return;
		}
		Role role = roleOf(task);
		if (role != null) {
			add(c, Speciality.workName(role), (int) Math.min(MAX_PER_JOB, 1 + ticks / TICKS_PER_POINT));
		}
	}

	private static @Nullable Role roleOf(CompanionTask task) {
		if (task instanceof SpecialityTask shared) {
			return shared.role();
		}
		String id = task.id();
		if (id.equals(FarTripTask.ID) || id.equals(TradeTripTask.ID)) {
			return Role.EXPLORER;
		}
		return null;
	}

	/** A {@code CompanionEvents.HIT} listener: blows on monsters earn fighting experience, the last blow most. */
	static void onHit(CompanionEntity c, ServerLevel level, Entity target, boolean killed) {
		if (target instanceof Enemy) {
			add(c, FIGHTING, killed ? 5 : 1);
		}
	}

	/** A {@code CompanionEvents.TICK} listener: keeps the fighting bonuses in step with the level now and then. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if (c.tickCount % 100 == 37) {
			applyCombat(c);
		}
	}

	/** Sets the fighting bonuses (fixed ids, so they never stack) for the friend's fighting level. */
	static void applyCombat(CompanionEntity c) {
		int level = level(c, FIGHTING);
		setModifier(c, Attributes.ATTACK_DAMAGE, ATTACK_ID, level * ATTACK_PER_LEVEL);
		setModifier(c, Attributes.MAX_HEALTH, HEALTH_ID, level * HEALTH_PER_LEVEL);
	}

	private static void setModifier(CompanionEntity c, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = c.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount <= 0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || Math.abs(current.amount() - amount) > 1.0E-6) {
			// Permanent, so it is saved with the friend and their health is not cut back when the world loads.
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** "■■■□□□□□□□" for a level. */
	public static String bar(int level) {
		return "■".repeat(Math.clamp(level, 0, MAX_LEVEL)) + "□".repeat(MAX_LEVEL - Math.clamp(level, 0, MAX_LEVEL));
	}
}
