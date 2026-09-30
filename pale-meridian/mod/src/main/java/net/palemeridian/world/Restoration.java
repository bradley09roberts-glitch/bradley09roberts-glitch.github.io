package net.palemeridian.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.palemeridian.PaleMeridian;
import net.palemeridian.state.PMState;

/**
 * Lifts the Pall from a restored district: swaps its Pall biomes for clear ones and lights its road
 * lamps, in a ring that spreads out from the district's lamp, then keeps doing so for chunks that load
 * later. Requests arrive from the data pack as scoreboard flags ({@code #req.<group> pm.world = 1});
 * which districts are restored is read from {@code #r.<group> pm.world}. Idempotent by design: running
 * the conversion twice changes nothing.
 */
public final class Restoration {
	/** Story district groups, in a fixed order. */
	public static final List<String> GROUPS = List.of("landing", "hollin", "aldercross", "glassworks", "deepcut", "mere", "fen", "wilds");
	private static final double RING_SPEED = 7.0;  // blocks per tick
	private static final double MAX_RADIUS = 640.0;

	private record Job(String group, double cx, double cz, List<long[]> order, int[] cursor, double[] radius) {
	}

	private static final List<Job> JOBS = new ArrayList<>();
	/** Chunks that loaded after their district was restored; their lamps are lit on the next tick. */
	private static final Set<Long> PENDING_LAMPS = new HashSet<>();
	private static final Map<String, Set<ResourceKey<Biome>>> PALL_BY_GROUP = new HashMap<>();
	private static final Map<String, List<LampPosts.Post>> POSTS_BY_GROUP = new HashMap<>();
	private static Set<String> restoredCache = Set.of();
	private static int cacheAge = 0;

	private Restoration() {
	}

	private static ResourceKey<Biome> biome(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(PaleMeridian.MOD_ID, path));
	}

	private static synchronized void index() {
		if (!PALL_BY_GROUP.isEmpty()) {
			return;
		}
		ValleyLayout layout = ValleyLayout.get();
		for (String g : GROUPS) {
			PALL_BY_GROUP.put(g, new HashSet<>());
			POSTS_BY_GROUP.put(g, new ArrayList<>());
		}
		for (Map.Entry<String, String> e : layout.districtGroups.entrySet()) {
			PALL_BY_GROUP.get(e.getValue()).add(biome(e.getKey() + "_pall"));
		}
		ValleyTerrain terrain = ValleyTerrain.get();
		for (LampPosts.Post p : LampPosts.all()) {
			String region = terrain.district(p.x(), 70, p.z());
			String group = layout.districtGroups.getOrDefault(region, "wilds");
			POSTS_BY_GROUP.get(group).add(p);
		}
	}

	private static double[] origin(String group) {
		ValleyLayout l = ValleyLayout.get();
		return switch (group) {
			case "landing" -> new double[] {l.site("landing").x(), l.site("landing").z()};
			case "hollin" -> new double[] {l.site("hollin").x(), l.site("hollin").z()};
			case "aldercross" -> new double[] {l.site("aldercross").x(), l.site("aldercross").z()};
			case "glassworks" -> new double[] {l.site("glassworks").x(), l.site("glassworks").z()};
			case "deepcut" -> new double[] {l.deepcutX, l.deepcutZ};
			case "mere" -> new double[] {l.lakeX, l.lakeZ};
			case "fen" -> new double[] {l.fenX, l.fenZ};
			default -> new double[] {l.centerX, l.centerZ};
		};
	}

	public static Set<String> restored(MinecraftServer server) {
		if (cacheAge-- > 0) {
			return restoredCache;
		}
		Set<String> out = new HashSet<>();
		for (String g : GROUPS) {
			if (PMState.get(server, "#r." + g) == 1) {
				out.add(g);
			}
		}
		restoredCache = out;
		cacheAge = 20;
		return out;
	}

	/** Called every server tick. */
	public static void tick(MinecraftServer server) {
		index();
		for (String g : GROUPS) {
			if (PMState.get(server, "#req." + g) == 1) {
				PMState.set(server, "#req." + g, 0);
				cacheAge = 0;
				start(g);
			}
		}
		ServerLevel level = server.overworld();
		if (!PENDING_LAMPS.isEmpty()) {
			Set<String> now = restored(server);
			for (long key : PENDING_LAMPS) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
				if (chunk != null) {
					lightPosts(level, chunk, now);
				}
			}
			PENDING_LAMPS.clear();
		}
		if (JOBS.isEmpty()) {
			return;
		}
		Set<String> restored = restored(server);
		List<ChunkAccess> changed = new ArrayList<>();
		JOBS.removeIf(job -> step(level, job, restored, changed));
		if (!changed.isEmpty()) {
			level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
		}
	}

	private static void start(String group) {
		double[] o = origin(group);
		int rc = (int) Math.ceil(MAX_RADIUS / 16.0) + 1;
		int ocx = (int) Math.floor(o[0]) >> 4, ocz = (int) Math.floor(o[1]) >> 4;
		List<long[]> order = new ArrayList<>();
		for (int dx = -rc; dx <= rc; dx++) {
			for (int dz = -rc; dz <= rc; dz++) {
				int cx = ocx + dx, cz = ocz + dz;
				double d = Math.hypot(cx * 16 + 8 - o[0], cz * 16 + 8 - o[1]);
				if (d <= MAX_RADIUS) {
					order.add(new long[] {cx, cz, (long) (d * 16)});
				}
			}
		}
		order.sort(Comparator.comparingLong(a -> a[2]));
		JOBS.removeIf(j -> j.group().equals(group));
		JOBS.add(new Job(group, o[0], o[1], order, new int[] {0}, new double[] {0.0}));
		PaleMeridian.LOG.info("Lifting the Pall from district '{}'", group);
	}

	/** Advance one job by one tick. Returns true when finished. */
	private static boolean step(ServerLevel level, Job job, Set<String> restored, List<ChunkAccess> changed) {
		job.radius()[0] += RING_SPEED;
		double r16 = job.radius()[0] * 16;
		List<long[]> order = job.order();
		int i = job.cursor()[0];
		while (i < order.size() && order.get(i)[2] <= r16) {
			long[] c = order.get(i++);
			LevelChunk chunk = level.getChunkSource().getChunkNow((int) c[0], (int) c[1]);
			if (chunk != null && convert(level, chunk, Set.of(job.group()))) {
				changed.add(chunk);
			}
			if (chunk != null) {
				lightPosts(level, chunk, Set.of(job.group()));
			}
		}
		job.cursor()[0] = i;
		return i >= order.size();
	}

	/** Chunk-load hook: bring newly loaded chunks up to date with every restored district. */
	public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return;
		}
		index();
		Set<String> restored = restored(level.getServer());
		if (restored.isEmpty()) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		if (ValleyTerrain.get().distanceFromCenter(pos.getMiddleBlockX(), pos.getMiddleBlockZ()) > MAX_RADIUS) {
			return;
		}
		if (convert(level, chunk, restored)) {
			// Not yet sent to players in the usual case; resend in case it already was.
			level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
		}
		// Block changes are deferred: setting blocks inside the load callback could reach into chunks
		// that are still loading.
		PENDING_LAMPS.add(pos.pack());
	}

	private static boolean convert(ServerLevel level, ChunkAccess chunk, Set<String> groups) {
		Set<ResourceKey<Biome>> from = new HashSet<>();
		for (String g : groups) {
			from.addAll(PALL_BY_GROUP.getOrDefault(g, Set.of()));
		}
		if (from.isEmpty()) {
			return false;
		}
		var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		boolean[] any = {false};
		chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
			Holder<Biome> current = chunk.getNoiseBiome(qx, qy, qz);
			var key = current.unwrapKey();
			if (key.isPresent() && from.contains(key.get())) {
				String path = key.get().identifier().getPath();
				var clear = biomes.get(biome(path.substring(0, path.length() - "_pall".length()) + "_clear"));
				if (clear.isPresent()) {
					any[0] = true;
					return clear.get();
				}
			}
			return current;
		}, level.getChunkSource().randomState().sampler());
		if (any[0]) {
			chunk.markUnsaved();
		}
		return any[0];
	}

	private static void lightPosts(ServerLevel level, LevelChunk chunk, Set<String> groups) {
		ChunkPos cp = chunk.getPos();
		for (String g : groups) {
			for (LampPosts.Post p : POSTS_BY_GROUP.getOrDefault(g, List.of())) {
				if ((p.x() >> 4) != cp.x() || (p.z() >> 4) != cp.z()) {
					continue;
				}
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, p.x() & 15, p.z() & 15);
				BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
				for (int y = top + 1; y >= top - 8; y--) {
					m.set(p.x(), y, p.z());
					BlockState st = level.getBlockState(m);
					if (st.getBlock() instanceof CopperBulbBlock && st.hasProperty(CopperBulbBlock.LIT)) {
						if (!st.getValue(CopperBulbBlock.LIT)) {
							// Clients only: a bulb's light needs no neighbour updates (and must not power redstone here).
							level.setBlock(m, st.setValue(CopperBulbBlock.LIT, true), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
						}
						break;
					}
				}
			}
		}
	}

	/** Admin: how many jobs are running. */
	public static int runningJobs() {
		return JOBS.size();
	}
}
