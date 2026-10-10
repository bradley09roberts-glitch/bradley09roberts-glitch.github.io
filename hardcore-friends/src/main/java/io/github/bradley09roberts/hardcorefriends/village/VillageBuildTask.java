package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Building the village: one batch at a time of a building the planner reserved, through the builders' own building
 * job ({@link Construction#job}: real materials fetched, crafted and placed through the edit guard, scaffolding where
 * needed). Three kinds of it:
 * <ul>
 * <li>{@link Mode#HOME}: a friend building the house planned for their own household. A personal job: everyone puts
 * up their own home in their spare time (the builder fastest, everyone else at their keenness for building work).</li>
 * <li>{@link Mode#VILLAGE}: the builder's work on everything else (and on anyone's house): civic buildings, shops,
 * workplaces, farms, the walls and the gate.</li>
 * <li>{@link Mode#DECOR}: the landscaper's lamp posts, benches, gardens, signposts and fountain.</li>
 * </ul>
 * One builder per building: a friend claims the site while they work on it, so several houses go up at once, each
 * with its own builder. A building that cannot be got on with (short of materials, no table, out of reach, the site
 * still being cleared) is set aside for a while, like the camp's own buildings.
 */
final class VillageBuildTask implements CompanionTask {
	/** What a run builds. */
	enum Mode {
		HOME("village.build_home", 50),
		VILLAGE("village.build", 52),
		DECOR("village.decor", 42);

		final String id;
		final double score;

		Mode(String id, double score) {
			this.id = id;
			this.score = score;
		}
	}

	/** A claim on a site lapses this long after its builder last worked on it. */
	private static final int CLAIM_TICKS = 100;
	private static final int CHOOSE_INTERVAL = 100;

	private record Claim(UUID who, long at) {
	}

	/** Who is building which village site right now (one builder per building). */
	private static final Map<String, Claim> CLAIMS = new HashMap<>();

	private final Mode mode;
	private final Map<String, Long> setAside = new HashMap<>();
	private @Nullable BuildJob job;
	private @Nullable String siteKey;
	private String targetName = "building";
	private @Nullable String chosen;
	private long chosenAt = Long.MIN_VALUE / 2;
	private int cooldown = 600;
	private final List<BlockPos> torches = new ArrayList<>();

	VillageBuildTask(Mode mode) {
		this.mode = mode;
	}

	/** Forgets every claim (a server stopping or starting). */
	static void clearClaims() {
		CLAIMS.clear();
	}

	@Override
	public String id() {
		return mode.id;
	}

	@Override
	public String describe() {
		String key = siteKey;
		if (key == null) {
			return mode == Mode.HOME ? "building a home" : "building the village";
		}
		return mode == Mode.HOME ? "building our new home" : "building the village's " + targetName;
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !(c.level() instanceof ServerLevel level) || !FriendsConfig.get().villageHomes) {
			return 0;
		}
		if (!Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - chosenAt >= CHOOSE_INTERVAL || now < chosenAt) {
			chosenAt = now;
			chosen = choose(c, level);
		}
		return chosen != null ? mode.score : 0;
	}

	/** The building to work on next, oldest plan first, or null. */
	private @Nullable String choose(CompanionEntity c, ServerLevel level) {
		VillageData v = VillageData.get(level.getServer());
		CampData camp = Camp.data(level.getServer());
		long now = level.getGameTime();
		for (VillageData.Plot p : v.plots()) {
			if (p.standing() || !fits(c, p) || camp.site(p.siteKey).isEmpty() || Blueprints.isFinished(camp, p.siteKey)) {
				continue;
			}
			Long until = setAside.get(p.siteKey);
			if (until != null && now < until) {
				continue;
			}
			if (claimedByAnother(p.siteKey, c, now)) {
				continue;
			}
			return p.siteKey;
		}
		return null;
	}

	private boolean fits(CompanionEntity c, VillageData.Plot p) {
		return switch (mode) {
			case HOME -> p.isHouse() && p.intended.contains(c.getUUID());
			case VILLAGE -> !p.kind.startsWith("decor:");
			case DECOR -> p.kind.startsWith("decor:");
		};
	}

	private static boolean claimedByAnother(String key, CompanionEntity c, long now) {
		Claim claim = CLAIMS.get(key);
		if (claim == null || claim.who().equals(c.getUUID())) {
			return false;
		}
		if (now - claim.at() > CLAIM_TICKS || now < claim.at()) {
			CLAIMS.remove(key, claim);
			return false;
		}
		return true;
	}

	@Override
	public boolean start(CompanionEntity c) {
		cooldown = 600;
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		String key = choose(c, level);
		chosenAt = Long.MIN_VALUE / 2;
		if (key == null) {
			return false;
		}
		BuildJob j = Construction.job(c, key, WorldEditGuard.Reason.BUILD, false);
		if (j == null) {
			return false;
		}
		job = j;
		siteKey = key;
		targetName = Blueprints.displayName(Camp.data(level.getServer()), key);
		torches.clear();
		torches.addAll(ownTorches(level, key));
		CLAIMS.put(key, new Claim(c.getUUID(), level.getGameTime()));
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BuildJob j = job;
		String key = siteKey;
		if (j == null || key == null) {
			return TaskStatus.FAILURE;
		}
		long now = c.level().getGameTime();
		Claim claim = CLAIMS.get(key);
		if (claim != null && !claim.who().equals(c.getUUID()) && now - claim.at() <= CLAIM_TICKS) {
			return TaskStatus.FAILURE; // someone else has it (a claim that lapsed while we were away)
		}
		CLAIMS.put(key, new Claim(c.getUUID(), now));
		if (!torches.isEmpty()) {
			takeUpTorch(c, (ServerLevel) c.level());
			return TaskStatus.RUNNING;
		}
		TaskStatus status = j.tick();
		if (status == TaskStatus.FAILURE) {
			int wait = switch (j.failure()) {
				case SHORT -> 1200;
				case NO_SITE -> 2400;
				case NO_TABLE -> 600;
				case UNREACHABLE -> 300;
				case CLEARING -> 400;
				case NONE -> 200;
			};
			setAside.put(key, now + wait);
			cooldown = 60; // try another building soon
		}
		return status;
	}

	/**
	 * The friends' own torches standing where the building goes (the landscaper lit the ground before the village grew
	 * over it): taken up first, so none is left in a wall. Only torches the friends placed, through the edit guard.
	 */
	private static List<BlockPos> ownTorches(ServerLevel level, String key) {
		List<BlockPos> list = new ArrayList<>();
		VillageData.Plot plot = VillageData.get(level.getServer()).plotBySite(key).orElse(null);
		CampData camp = Camp.data(level.getServer());
		Blueprint plan = Blueprints.forSite(camp, key).orElse(null);
		if (plot == null || plan == null) {
			return list;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = plot.box[0]; x <= plot.box[2]; x++) {
			for (int z = plot.box[1]; z <= plot.box[3]; z++) {
				for (int y = plot.floorY - 1; y < plot.floorY + plan.height(); y++) {
					m.set(x, y, z);
					if (level.isLoaded(m) && PlotSurvey.ownTorch(level, camp, level.getBlockState(m), m)) {
						list.add(m.immutable());
					}
				}
			}
		}
		return list;
	}

	private void takeUpTorch(CompanionEntity c, ServerLevel level) {
		BlockPos t = torches.getFirst();
		if (!PlotSurvey.ownTorch(level, Camp.data(level.getServer()), level.getBlockState(t), t)) {
			torches.removeFirst();
			return;
		}
		if (!c.actions().canReach(t)) {
			c.actions().walkTo(t, 2.5);
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				torches.removeFirst(); // out of reach: the building simply goes round it
			}
			return;
		}
		c.actions().stopWalking();
		if (c.actions().mine(t, WorldEditGuard.Reason.BUILD) != Actions.Result.RUNNING) {
			torches.removeFirst();
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().cancelMining();
		torches.clear();
		BuildJob j = job;
		if (j != null) {
			j.stop();
		}
		String key = siteKey;
		if (key != null) {
			Claim claim = CLAIMS.get(key);
			if (claim != null && claim.who().equals(c.getUUID())) {
				CLAIMS.remove(key);
			}
		}
		job = null;
		siteKey = null;
	}

	@Override
	public int failureCooldown() {
		return cooldown;
	}

	@Override
	public int successCooldown() {
		return 40;
	}

	@Override
	public int maxTicks() {
		return 20 * 180;
	}
}
