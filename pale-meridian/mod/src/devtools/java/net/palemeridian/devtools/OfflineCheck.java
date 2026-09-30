package net.palemeridian.devtools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.palemeridian.world.PMWorldgen;
import net.palemeridian.world.ValleyLayout;

/**
 * Development-only offline checker. It does NOT start a Minecraft server, create a world or
 * connect to anything. It bootstraps the game's registries (like Mojang's data generator does),
 * loads vanilla + the Pale Meridian data pack through the same loader the server uses, and reports
 * every parse error. It then evaluates the valley's terrain generator for several seeds.
 *
 * Usage: OfflineCheck <resourcesDir> <outDir> [validate|worldgen|all]
 */
public final class OfflineCheck {
	private static int failures = 0;

	public static void main(String[] args) throws Exception {
		Path resources = Path.of(args[0]);
		Path out = Path.of(args[1]);
		String mode = args.length > 2 ? args[2] : "all";
		Files.createDirectories(out);

		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		registerModTypes();

		PackResources vanilla = ServerPacksSource.createVanillaPackSource();
		PackResources mod = new PathPackResources(
			new PackLocationInfo("palemeridian", Component.literal("Pale Meridian"), PackSource.BUILT_IN, Optional.empty()), resources);
		MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla, mod));
		Executor direct = Runnable::run;

		LayeredRegistryAccess<RegistryLayer> initialLayers = RegistryLayer.createRegistryAccess();
		List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(manager, initialLayers.getLayer(RegistryLayer.STATIC));
		RegistryAccess.Frozen worldgenContext = initialLayers.getAccessForLoading(RegistryLayer.WORLDGEN);
		List<HolderLookup.RegistryLookup<?>> worldgenContextRegistries = TagLoader.buildUpdatedLookups(worldgenContext, staticTags);

		RegistryAccess.Frozen worldgen = RegistryDataLoader.load(manager, worldgenContextRegistries, RegistryDataLoader.WORLDGEN_REGISTRIES, direct).join();
		System.out.println("[check] worldgen registries loaded");
		List<HolderLookup.RegistryLookup<?>> dimensionContext = Stream.concat(worldgenContextRegistries.stream(), worldgen.listRegistries()).toList();
		RegistryAccess.Frozen dimensions = RegistryDataLoader.load(manager, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES, direct).join();
		System.out.println("[check] dimension registries loaded");

		if (mode.equals("validate") || mode.equals("all")) {
			LayeredRegistryAccess<RegistryLayer> resourceLayers = initialLayers.replaceFrom(RegistryLayer.WORLDGEN, worldgen, dimensions);
			ReloadableServerResources rsr = ReloadableServerResources.loadResources(
					manager, resourceLayers, staticTags, FeatureFlags.DEFAULT_FLAGS, Commands.CommandSelection.DEDICATED,
					LevelBasedPermissionSet.GAMEMASTER, direct, direct)
				.join();
			reportContent(resources, rsr, worldgen);
		}

		if (mode.equals("sites") || mode.equals("map") || mode.equals("all")) {
			worldgenProbe(worldgen, out, mode.equals("map"));
		}

		System.out.println(failures == 0 ? "[check] RESULT: PASS" : "[check] RESULT: FAIL (" + failures + " problems)");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void fail(String msg) {
		failures++;
		System.out.println("[check] FAIL: " + msg);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerModTypes() throws Exception {
		// Dev-only: in the real game Fabric registers these before the registries freeze. Here the
		// registries are already frozen by Bootstrap, so unfreeze, register, then bind the new holders.
		Field frozen = MappedRegistry.class.getDeclaredField("frozen");
		frozen.setAccessible(true);
		List<Registry<?>> regs = List.of(
			BuiltInRegistries.STRUCTURE_PIECE, BuiltInRegistries.STRUCTURE_TYPE, BuiltInRegistries.STRUCTURE_PLACEMENT,
			BuiltInRegistries.DENSITY_FUNCTION_TYPE, BuiltInRegistries.BIOME_SOURCE, BuiltInRegistries.FEATURE);
		for (Registry<?> r : regs) {
			frozen.setBoolean(r, false);
		}
		PMWorldgen.register();
		java.lang.reflect.Method bindValue = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
		bindValue.setAccessible(true);
		java.lang.reflect.Method bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
		bindTags.setAccessible(true);
		Field byValueField = MappedRegistry.class.getDeclaredField("byValue");
		byValueField.setAccessible(true);
		for (Registry<?> r : regs) {
			Map<Object, Holder.Reference> byValue = (Map<Object, Holder.Reference>) byValueField.get(r);
			for (Map.Entry<Object, Holder.Reference> e : byValue.entrySet()) {
				Holder.Reference ref = e.getValue();
				if (ref.key().identifier().getNamespace().equals("palemeridian")) {
					bindValue.invoke(ref, e.getKey());
					bindTags.invoke(ref, List.of());
				}
			}
			frozen.setBoolean(r, true);
		}
	}

	private static long countFiles(Path root, String sub, String ext) throws Exception {
		Path p = root.resolve(sub);
		if (!Files.isDirectory(p)) {
			return 0;
		}
		try (Stream<Path> s = Files.walk(p)) {
			return s.filter(f -> f.toString().endsWith(ext)).count();
		}
	}

	private static void reportContent(Path resources, ReloadableServerResources rsr, RegistryAccess.Frozen worldgen) throws Exception {
		long fnFiles = countFiles(resources, "data/palemeridian/function", ".mcfunction");
		long fnLoaded = rsr.getFunctionLibrary().getFunctions().keySet().stream().filter(id -> id.getNamespace().equals("palemeridian")).count();
		System.out.println("[check] functions: files=" + fnFiles + " loaded=" + fnLoaded);
		if (fnFiles != fnLoaded) {
			fail("function files that failed to parse: " + (fnFiles - fnLoaded) + " (see log above)");
		}
		long advFiles = countFiles(resources, "data/palemeridian/advancement", ".json");
		long advLoaded = rsr.getAdvancements().getAllAdvancements().stream().filter(a -> a.id().getNamespace().equals("palemeridian")).count();
		System.out.println("[check] advancements: files=" + advFiles + " loaded=" + advLoaded);
		if (advFiles != advLoaded) {
			fail("advancements that failed to load: " + (advFiles - advLoaded));
		}
		long recipeFiles = countFiles(resources, "data/palemeridian/recipe", ".json");
		long recipeLoaded = rsr.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().getNamespace().equals("palemeridian")).count();
		System.out.println("[check] recipes: files=" + recipeFiles + " loaded=" + recipeLoaded);
		if (recipeFiles != recipeLoaded) {
			fail("recipes that failed to load: " + (recipeFiles - recipeLoaded));
		}
		for (var key : List.of(Registries.BIOME, Registries.STRUCTURE, Registries.STRUCTURE_SET, Registries.NOISE_SETTINGS,
			Registries.DENSITY_FUNCTION, Registries.DIALOG, Registries.WORLD_CLOCK, Registries.TIMELINE, Registries.WORLD_PRESET,
			Registries.PLACED_FEATURE, Registries.CONFIGURED_FEATURE, Registries.NOISE)) {
			Registry<?> reg = worldgen.lookupOrThrow(key);
			long mine = reg.keySet().stream().filter(id -> id.getNamespace().equals("palemeridian")).count();
			String folder = key.identifier().getPath();
			long files = countFiles(resources, "data/palemeridian/" + folder, ".json");
			System.out.println("[check] " + folder + ": files=" + files + " loaded=" + mine);
			if (files != mine) {
				fail(folder + " entries that failed to load: " + (files - mine));
			}
		}
	}

	private static void worldgenProbe(RegistryAccess.Frozen worldgen, Path out, boolean renderMap) throws Exception {
		ValleyLayout layout = ValleyLayout.get();
		WorldPreset preset = worldgen.lookupOrThrow(Registries.WORLD_PRESET).getValue(WorldPresets.NORMAL);
		if (preset == null) {
			fail("minecraft:normal world preset missing");
			return;
		}
		WorldDimensions dims = preset.createWorldDimensions();
		LevelStem overworld = dims.dimensions().get(LevelStem.OVERWORLD);
		ChunkGenerator generator = overworld.generator();
		if (!(generator instanceof NoiseBasedChunkGenerator noiseGen)) {
			fail("overworld generator is not a noise generator: " + generator);
			return;
		}
		BiomeSource biomes = generator.getBiomeSource();
		System.out.println("[check] overworld biome source: " + biomes.getClass().getName());
		if (!biomes.getClass().getName().contains("ValleyBiomeSource")) {
			fail("default world preset does not use the valley biome source");
		}
		var noiseSettings = noiseGen.generatorSettings();
		LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(-64, 384);
		var noises = worldgen.lookupOrThrow(Registries.NOISE);

		long[] seeds = {0L, 12345L, -987654321L};
		Map<String, Integer> problems = new LinkedHashMap<>();
		for (int si = 0; si < seeds.length; si++) {
			long seed = seeds[si];
			RandomState rs = RandomState.create(noiseSettings.value(), noises, seed);
			// Site plateau checks: exact floor across the flat radius and solid ground below.
			for (ValleyLayout.Site site : layout.sites) {
				List<int[]> pts = new ArrayList<>();
				double rad = Math.max(1.0, site.flatRadius() - 6.0);
				for (int dx = (int) -rad; dx <= rad; dx += 4) {
					for (int dz = (int) -rad; dz <= rad; dz += 4) {
						if (dx * dx + dz * dz <= rad * rad) {
							pts.add(new int[] {(int) Math.round(site.x()) + dx, (int) Math.round(site.z()) + dz});
						}
					}
				}
				java.util.concurrent.atomic.AtomicInteger bad = new java.util.concurrent.atomic.AtomicInteger();
				java.util.concurrent.atomic.AtomicInteger caveHoles = new java.util.concurrent.atomic.AtomicInteger();
				java.util.concurrent.ConcurrentHashMap<Integer, Integer> seen = new java.util.concurrent.ConcurrentHashMap<>();
				pts.parallelStream().forEach(pt -> {
					int h = noiseGen.getBaseHeight(pt[0], pt[1], Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, rs);
					seen.merge(h, 1, Integer::sum);
					if (h != site.floor()) {
						bad.incrementAndGet();
					}
					NoiseColumn col = noiseGen.getBaseColumn(pt[0], pt[1], heightAccessor, rs);
					for (int y = site.floor() - 1; y >= site.floor() - 16; y--) {
						if (col.getBlock(y).isAir()) {
							caveHoles.incrementAndGet();
							break;
						}
					}
				});
				String key = site.id();
				if (bad.get() > 0 || caveHoles.get() > 0) {
					problems.merge(key, bad.get() + caveHoles.get(), Integer::sum);
				}
				System.out.println("[check] seed " + seed + " site " + key + ": sampled=" + pts.size() + " wrongFloor=" + bad.get()
					+ " caveHolesBelow=" + caveHoles.get() + " heights=" + new java.util.TreeMap<>(seen));
			}
			if (si == 0 && renderMap) {
				renderMap(noiseGen, biomes, rs, heightAccessor, layout, out.resolve("valley_map_seed" + seed + ".png").toFile());
			}
		}
		for (var e : problems.entrySet()) {
			fail("site " + e.getKey() + " plateau problems across seeds: " + e.getValue());
		}
	}

	private static int biomeColor(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		int base;
		if (path.startsWith("landing")) base = 0xC9B27C;
		else if (path.startsWith("hollin")) base = 0xD98E5A;
		else if (path.startsWith("aldercross")) base = 0x8DBF5A;
		else if (path.startsWith("glassworks")) base = 0xB07A4A;
		else if (path.startsWith("deepcut")) base = 0x6B5B73;
		else if (path.startsWith("mere")) base = 0x6F9FC0;
		else if (path.startsWith("fen")) base = 0x6E8B5A;
		else if (path.startsWith("westwood")) base = 0x5E8F6A;
		else if (path.startsWith("southmoor")) base = 0xA7A36A;
		else if (path.startsWith("rim")) base = 0x8A8F96;
		else base = 0x9AA37A;
		return base;
	}

	private static void renderMap(NoiseBasedChunkGenerator gen, BiomeSource biomes, RandomState rs, LevelHeightAccessor ha,
		ValleyLayout layout, File file) throws Exception {
		int half = 640, step = 4, size = half * 2 / step;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		int[][] heights = new int[size][size];
		java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
		java.util.stream.IntStream.range(0, size).parallel().forEach(px -> {
			for (int pz = 0; pz < size; pz++) {
				int x = -half + px * step, z = -half + pz * step;
				heights[px][pz] = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ha, rs);
			}
			int d = done.incrementAndGet();
			if (d % 40 == 0) {
				System.out.println("[check] map rows " + d + "/" + size);
			}
		});
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				int x = -half + px * step, z = -half + pz * step;
				int h = heights[px][pz];
				Holder<Biome> b = biomes.getNoiseBiome(x >> 2, Math.max(0, h) >> 2, z >> 2, rs.sampler());
				String id = b.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
				int color = id.startsWith("palemeridian:") ? biomeColor(id) : 0x7F9F6F;
				if (!id.startsWith("palemeridian:")) {
					color = 0x5F7F5F;
				}
				double shade = 1.0;
				if (px > 0 && pz > 0) {
					int d = h - heights[px - 1][pz - 1];
					shade = 1.0 + Math.max(-0.35, Math.min(0.35, d * 0.08));
				}
				if (h < layout.waterLevel) {
					int depth = layout.waterLevel - h;
					color = depth > 8 ? 0x2E5C8A : 0x4A7FB0;
					shade = 1.0;
				}
				int r = (int) Math.min(255, ((color >> 16) & 255) * shade);
				int g = (int) Math.min(255, ((color >> 8) & 255) * shade);
				int bl = (int) Math.min(255, (color & 255) * shade);
				if (h % 8 == 0 && h >= layout.waterLevel) {
					r = (int) (r * 0.85);
					g = (int) (g * 0.85);
					bl = (int) (bl * 0.85);
				}
				img.setRGB(px, pz, (r << 16) | (g << 8) | bl);
			}
		}
		for (ValleyLayout.Road road : layout.roads) {
			double[][] p = road.points();
			for (int i = 0; i + 1 < p.length; i++) {
				for (double t = 0; t <= 1.0; t += 0.002) {
					int x = (int) (p[i][0] + (p[i + 1][0] - p[i][0]) * t), z = (int) (p[i][1] + (p[i + 1][1] - p[i][1]) * t);
					int px = (x + half) / step, pz = (z + half) / step;
					if (px >= 0 && pz >= 0 && px < size && pz < size) {
						img.setRGB(px, pz, 0xE8D8A8);
					}
				}
			}
		}
		for (ValleyLayout.Site s : layout.sites) {
			int cx = (int) ((s.x() + half) / step), cz = (int) ((s.z() + half) / step);
			for (int a = 0; a < 360; a += 2) {
				int px = cx + (int) (Math.cos(Math.toRadians(a)) * s.flatRadius() / step);
				int pz = cz + (int) (Math.sin(Math.toRadians(a)) * s.flatRadius() / step);
				if (px >= 0 && pz >= 0 && px < size && pz < size) {
					img.setRGB(px, pz, 0xFFFFFF);
				}
			}
		}
		ImageIO.write(img, "png", file);
		System.out.println("[check] wrote " + file);
	}
}
