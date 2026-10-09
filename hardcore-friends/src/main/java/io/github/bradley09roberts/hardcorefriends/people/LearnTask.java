package io.github.bradley09roberts.hardcorefriends.people;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.survival.Skills;

/**
 * A child learning. By day a child goes to school if the village has a teacher at work (the market package's
 * {@code teacher} profession, at their workplace: {@link #TEACHER}), otherwise follows a parent who is at work inside
 * the camp and watches from a few steps away. Every ten seconds close by earns skill experience ({@code survival.Skills})
 * in the work being watched (at school: their own trade, and their trade's interest besides), so a child grows up good
 * at something. Watching changes nothing; a parent leaving the camp ends the lesson.
 */
final class LearnTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "learn";
	/** The village trade that runs a school (see {@code civic.Professions}). */
	static final String TEACHER = "teacher";
	private static final double SCHOOL = 42;
	private static final double WATCH_PARENT = 38;
	private static final double PARENT_RANGE = 32;
	private static final int LESSON_TICKS = 20 * 60;
	private static final int POINT_TICKS = 20 * 10;
	/** How often the teacher at work in each world is looked up again. */
	private static final int TEACHER_REFRESH = 100;

	private record TeacherAt(long at, @Nullable UUID teacher, @Nullable BlockPos school) {
	}

	private static final Map<ResourceKey<Level>, TeacherAt> TEACHERS = new HashMap<>();

	private @Nullable UUID mentor;
	private @Nullable BlockPos school;
	private @Nullable Role work;
	private int ticks;
	private int near;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Role w = work;
		if (school != null) {
			return "at school";
		}
		return w == null ? "learning" : "watching and learning " + Speciality.workName(w);
	}

	@Override
	public double score(CompanionEntity c) {
		if (!c.isChild() || !(c.level() instanceof ServerLevel level) || Camp.isNight(level) || !Spots.inCamp(c, c.blockPosition())) {
			return 0;
		}
		TeacherAt t = teacher(level);
		if (t.teacher() != null && t.school() != null && t.school().closerThan(c.blockPosition(), 64)) {
			return SCHOOL;
		}
		return parentAtWork(c, level) != null ? WATCH_PARENT : 0;
	}

	/** A parent at work inside the camp, near enough to go and watch. */
	private static @Nullable CompanionEntity parentAtWork(CompanionEntity c, ServerLevel level) {
		Optional<PeopleData.Person> me = PeopleData.get(level.getServer()).person(c.getUUID());
		if (me.isEmpty()) {
			return null;
		}
		for (UUID id : me.get().parents) {
			CompanionEntity parent = PeopleEvents.loaded(level.getServer(), id);
			if (parent != null && parent.level() == level && parent.distanceToSqr(c) <= PARENT_RANGE * PARENT_RANGE
				&& Relationships.working(parent) && parent.mode() == CompanionMode.WORK && Spots.inCamp(c, parent.blockPosition())
				&& !parent.isChild() && nearSurface(level, parent.blockPosition())) {
				return parent;
			}
		}
		return null;
	}

	/**
	 * At or just below the ground's surface there (indoors in a cabin counts): never down a mine or in a cave, where a
	 * child following to watch would be in the dark with whatever lives there.
	 */
	private static boolean nearSurface(ServerLevel level, BlockPos pos) {
		return pos.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 6;
	}

	/** The teacher at work in this world, looked up at most every few seconds through {@code Professions}. */
	private static TeacherAt teacher(ServerLevel level) {
		long now = level.getGameTime();
		TeacherAt cached = TEACHERS.get(level.dimension());
		if (cached != null && now - cached.at() < TEACHER_REFRESH && now >= cached.at()) {
			return cached;
		}
		TeacherAt found = new TeacherAt(now, null, null);
		Professions.Provider professions = Professions.get();
		for (CompanionEntity adult : Companions.in(level)) {
			if (adult.isChild() || !TEACHER.equals(professions.professionOf(adult).orElse(null))) {
				continue;
			}
			Optional<BlockPos> workplace = professions.workplaceOf(adult);
			if (workplace.isPresent() && adult.blockPosition().closerThan(workplace.get(), 8) && !adult.isAsleep()) {
				found = new TeacherAt(now, adult.getUUID(), workplace.get());
				break;
			}
		}
		TEACHERS.put(level.dimension(), found);
		return found;
	}

	static void clear() {
		TEACHERS.clear();
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ticks = 0;
		near = 0;
		mentor = null;
		school = null;
		work = null;
		TeacherAt t = teacher(level);
		if (t.teacher() != null && t.school() != null) {
			mentor = t.teacher();
			school = t.school();
			work = c.friendId().role();
			Speech.say(c, Line.CHILD_LEARN, Speciality.workName(work));
			return true;
		}
		CompanionEntity parent = parentAtWork(c, level);
		if (parent == null) {
			return false;
		}
		mentor = parent.getUUID();
		work = workOf(parent);
		Speech.say(c, Line.CHILD_LEARN, Speciality.workName(work));
		return true;
	}

	/** The kind of work a grown-up is at: the job in hand's speciality, or their own trade. */
	private static Role workOf(CompanionEntity adult) {
		CompanionTask job = adult.mode() == CompanionMode.WORK ? adult.scheduler().current() : null;
		return job instanceof SpecialityTask shared ? shared.role() : adult.friendId().role();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		UUID id = mentor;
		Role w = work;
		if (id == null || w == null || !(c.level() instanceof ServerLevel level) || Camp.isNight(level)) {
			return done();
		}
		CompanionEntity teacher = PeopleEvents.loaded(level.getServer(), id);
		if (teacher == null || teacher.level() != level || !Spots.inCamp(c, teacher.blockPosition())
			|| school == null && (!Relationships.working(teacher) || !nearSurface(level, teacher.blockPosition()))) {
			return done();
		}
		if (++ticks > LESSON_TICKS) {
			return done();
		}
		BlockPos watchFrom = school != null ? school : teacher.blockPosition();
		double d2 = c.distanceToSqr(Vec3.atBottomCenterOf(watchFrom));
		if (d2 > 4 * 4) {
			if (ticks % 20 == 1) {
				c.getNavigation().moveTo(watchFrom.getX() + 0.5, watchFrom.getY(), watchFrom.getZ() + 0.5, 1.0);
			}
			return TaskStatus.RUNNING;
		}
		if (d2 < 2 * 2) {
			c.getNavigation().stop(); // close enough to see, not in the way
		}
		c.getLookControl().setLookAt(teacher);
		if (++near % POINT_TICKS == 0) {
			Skills.add(c, Speciality.workName(w), 2);
			if (school != null) {
				Skills.add(c, Speciality.workName(Speciality.interest(c.friendId())), 1);
			}
		}
		return TaskStatus.RUNNING;
	}

	/** A lesson with some watching in it counts as done; one that never got going failed. */
	private TaskStatus done() {
		return near >= POINT_TICKS ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.getNavigation().stop();
		mentor = null;
		school = null;
		work = null;
		ticks = 0;
		near = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int successCooldown() {
		return 20 * 45;
	}

	@Override
	public int maxTicks() {
		return LESSON_TICKS + 20 * 10;
	}
}
