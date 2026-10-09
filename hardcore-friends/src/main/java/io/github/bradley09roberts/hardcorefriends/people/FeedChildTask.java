package io.github.bradley09roberts.hardcorefriends.people;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.EntityApproach;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A parent brings food to their hungry child: when one of their children nearby is hungry ({@value #HUNGRY}) and has
 * nothing to eat on them, a parent with food in their backpack walks over and hands them a little (real food out of
 * the parent's own backpack, into the child's, where the child eats it). Children also fetch food from the camp chest
 * themselves (their own eating job); this covers a child far from the chest, or a chest that has run dry.
 */
final class FeedChildTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "feed_child";
	/** A child below this hunger with no food on them is brought some. */
	static final double HUNGRY = 35;
	private static final double SCORE = 74;
	private static final double RANGE = 48;
	private static final double GIVE_REACH = 2.0;

	private @Nullable UUID child;
	private final EntityApproach approach = new EntityApproach();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "bringing food to their child";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !c.hasFood() || c.getTarget() != null) {
			return 0;
		}
		return hungryChild(c) != null ? SCORE : 0;
	}

	/** One of this friend's children nearby who is hungry with nothing to eat. */
	private static @Nullable CompanionEntity hungryChild(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return null;
		}
		// Their own children from the records (a few at most), then whether each is loaded, near and hungry.
		for (PeopleData.Person p : PeopleData.get(level.getServer()).people()) {
			if (!p.child || !p.alive() || !p.parents.contains(c.getUUID())) {
				continue;
			}
			CompanionEntity kid = PeopleEvents.loaded(level.getServer(), p.id);
			if (kid != null && kid.level() == level && kid.distanceToSqr(c) <= RANGE * RANGE && kid.isChild()
				&& kid.needs().get(Needs.Need.HUNGER) < HUNGRY && !kid.hasFood()) {
				return kid;
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		approach.reset();
		CompanionEntity kid = hungryChild(c);
		child = kid == null ? null : kid.getUUID();
		return kid != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		CompanionEntity kid = child == null ? null : PeopleEvents.loaded(((ServerLevel) c.level()).getServer(), child);
		if (kid == null || kid.level() != c.level() || !kid.isChild() || kid.hasFood()) {
			return TaskStatus.FAILURE;
		}
		if (!approach.walk(c, kid, GIVE_REACH)) {
			return approach.isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(kid);
		ItemStack food = c.backpack().take(CompanionEntity::isEdible, 2);
		if (food.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		int count = food.getCount();
		ItemStack left = kid.backpack().insert(food);
		if (!left.isEmpty()) {
			ItemStack back = c.backpack().insert(left);
			if (!back.isEmpty() && c.level() instanceof ServerLevel level) {
				c.spawnAtLocation(level, back);
			}
		}
		int given = count - left.getCount();
		if (given <= 0) {
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		Speech.say(c, Line.SHARE, kid.displayName(), given + " " + food.getHoverName().getString());
		if (c.level() instanceof ServerLevel level) {
			Relationships.change(level.getServer(), c, kid, 2, 0);
		}
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		child = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
