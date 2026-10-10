package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The teacher keeps school: in school hours (from early morning to mid-afternoon, time of day 1000 to 9000) while the
 * village has children, they stand at the teacher's place in the school (its {@code teacher} spot, else its lectern)
 * and teach. Children go to school while a teacher is there (the {@code people} package's lessons ask
 * {@code civic.Professions} where the teacher works), and learn their own trade faster than by watching a parent. The
 * school's lectern is only the look of it: teaching never needs it there.
 */
final class TeachTask extends TradeJob {
	static final String ID = "market.teach";
	private static final double SCORE = 50;
	private static final long SCHOOL_STARTS = 1000;
	private static final long SCHOOL_ENDS = 9000;
	private static final int ROUND_TICKS = 20 * 90;

	private @Nullable BlockPos spot;
	private int ticks;

	TeachTask() {
		super(ID, Set.of(), Trade.TEACHER);
	}

	@Override
	public String describe() {
		return "teaching at the school";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (w == null || !schoolHours(level) || place(level, w) == null) {
			return 0;
		}
		return childNear(level, w.job(), 64) ? SCORE : 0;
	}

	static boolean schoolHours(ServerLevel level) {
		long t = Camp.timeOfDay(level);
		return TradeJob.workingHours(level) && t >= SCHOOL_STARTS && t < SCHOOL_ENDS;
	}

	private static @Nullable BlockPos place(ServerLevel level, Workplace w) {
		List<BlockPos> teacher = w.markers(level, "teacher");
		return teacher.isEmpty() ? w.job() : teacher.getFirst();
	}

	private static boolean childNear(ServerLevel level, BlockPos school, double range) {
		for (CompanionEntity c : Companions.in(level)) {
			if (c.isChild() && c.blockPosition().closerThan(school, range)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Workplace w = workplace(c, holding(c));
		spot = w == null ? null : place(level, w);
		ticks = 0;
		return spot != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos at = spot;
		if (at == null || !schoolHours(level) || ++ticks > ROUND_TICKS) {
			return TaskStatus.SUCCESS;
		}
		switch (Stores.stand(c, at, 0.9)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		List<CompanionEntity> pupils = level.getEntitiesOfClass(CompanionEntity.class, new AABB(at).inflate(10, 3, 10),
			x -> x.isChild() && x.isAlive());
		if (!pupils.isEmpty()) {
			CompanionEntity look = pupils.get((ticks / 60) % pupils.size());
			c.getLookControl().setLookAt(look);
			if (ticks % 200 == 20) {
				Speech.say(c, Line.TEACHING);
				c.swingArm();
				level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8F, 1.0F);
			}
		} else {
			c.getLookControl().setLookAt(Vec3.atCenterOf(at).add(0, 1, 0));
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		spot = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 5;
	}

	@Override
	public int maxTicks() {
		return ROUND_TICKS + 20 * 60;
	}
}
