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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
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

		installLogCapture();
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
			rsr.updateComponentsAndStaticRegistryTags();   // what the server does after a reload: binds item components and tags
			checkStructures(resources, rsr);
		}

		if (mode.equals("heightmaps")) {
			exportHeightmaps(worldgen, Path.of(args[3]));
		}
		if (mode.equals("sites") || mode.equals("map") || mode.equals("all")) {
			worldgenProbe(worldgen, out, mode.equals("map"));
		}

		synchronized (LOGGED) {
			System.out.println("[check] log warnings/errors mentioning palemeridian: " + LOGGED.size());
			for (String l : LOGGED) {
				fail("logged: " + l);
			}
		}
		System.out.println(failures == 0 ? "[check] RESULT: PASS" : "[check] RESULT: FAIL (" + failures + " problems)");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static final List<String> LOGGED = new ArrayList<>();

	/** Records every WARN/ERROR log event that mentions the mod's namespace (tags, loot tables, parse errors...). */
	private static void installLogCapture() {
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		AbstractAppender app = new AbstractAppender("pmcheck", null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent e) {
				if (!e.getLevel().isMoreSpecificThan(Level.WARN)) {
					return;
				}
				String all = e.getMessage().getFormattedMessage() + (e.getThrown() != null ? " " + e.getThrown() : "");
				if (all.contains("palemeridian")) {
					synchronized (LOGGED) {
						LOGGED.add(e.getLevel() + " " + all);
					}
				}
			}
		};
		app.start();
		ctx.getConfiguration().getRootLogger().addAppender(app, Level.WARN, null);
		ctx.updateLoggers();
	}

	/**
	 * Decodes what the site templates carry beyond block states, with the game's own codecs:
	 * container items (names, lore, components), sign and text-display text, and loot table references.
	 */
	private static void checkStructures(Path resources, ReloadableServerResources rsr) throws Exception {
		Path dir = resources.resolve("data/palemeridian/structure");
		HolderLookup.Provider provider = rsr.fullRegistries().lookup();
		RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
		java.util.Set<String> lootIds = new java.util.HashSet<>();
		provider.lookupOrThrow(Registries.LOOT_TABLE).listElementIds().forEach(k -> lootIds.add(k.identifier().toString()));
		int templates = 0, items = 0, texts = 0, lootRefs = 0;
		List<Path> files;
		try (Stream<Path> st = Files.walk(dir)) {
			files = st.filter(f -> f.toString().endsWith(".nbt")).sorted().toList();
		}
		for (Path p : files) {
			templates++;
			CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
			String name = dir.relativize(p).toString();
			ListTag blocks = root.getListOrEmpty("blocks");
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag be = blocks.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt");
				if (be.isEmpty()) {
					continue;
				}
				for (Tag it : be.getListOrEmpty("Items")) {
					items++;
					var res = ItemStack.CODEC.parse(ops, it);
					if (res.error().isPresent()) {
						fail(name + ": item does not decode: " + it + " -> " + res.error().get().message());
					}
				}
				var loot = be.getString("LootTable");
				if (loot.isPresent()) {
					lootRefs++;
					if (!lootIds.contains(loot.get())) {
						fail(name + ": missing loot table " + loot.get());
					}
				}
				for (String side : List.of("front_text", "back_text")) {
					for (Tag m : be.getCompoundOrEmpty(side).getListOrEmpty("messages")) {
						texts++;
						var res = ComponentSerialization.CODEC.parse(ops, m);
						if (res.error().isPresent()) {
							fail(name + ": sign text does not decode: " + m + " -> " + res.error().get().message());
						}
					}
				}
			}
			ListTag ents = root.getListOrEmpty("entities");
			for (int i = 0; i < ents.size(); i++) {
				CompoundTag en = ents.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt");
				for (String key : List.of("text", "CustomName", "description")) {
					if (en.contains(key)) {
						texts++;
						var res = ComponentSerialization.CODEC.parse(ops, en.get(key));
						if (res.error().isPresent()) {
							fail(name + ": entity " + key + " does not decode: " + res.error().get().message());
						}
					}
				}
			}
		}
		System.out.println("[check] structure templates: " + templates + " (items decoded: " + items + ", texts decoded: " + texts
			+ ", loot table refs: " + lootRefs + ")");
		for (var key : List.of(Registries.LOOT_TABLE, Registries.PREDICATE, Registries.ITEM_MODIFIER)) {
			long mine = provider.lookupOrThrow(key).listElementIds().filter(k -> k.identifier().getNamespace().equals("palemeridian")).count();
			String folder = key.identifier().getPath();
			long filesN = countFiles(resources, "data/palemeridian/" + folder, ".json");
			System.out.println("[check] " + folder + ": files=" + filesN + " loaded=" + mine);
			if (filesN != mine) {
				fail(folder + " entries that failed to load: " + (filesN - mine));
			}
		}
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void checkMacros(Path resources, ReloadableServerResources rsr) throws Exception {
		Path samples = resources.resolve("../../../../tools/generated/macro_samples.json").normalize();
		var json = com.google.gson.JsonParser.parseString(Files.readString(samples)).getAsJsonObject();
		var dispatcher = rsr.getCommands().getDispatcher();
		int macros = 0, ok = 0;
		for (var e : rsr.getFunctionLibrary().getFunctions().entrySet()) {
			if (!e.getKey().getNamespace().equals("palemeridian")) {
				continue;
			}
			if (!(e.getValue() instanceof net.minecraft.commands.functions.MacroFunction mf)) {
				continue;
			}
			macros++;
			String id = e.getKey().toString();
			if (!json.has(id)) {
				fail("macro function without sample arguments: " + id);
				continue;
			}
			try {
				var args = net.minecraft.nbt.TagParser.parseCompoundFully(json.get(id).getAsString());
				mf.instantiate(args, dispatcher);
				ok++;
			} catch (net.minecraft.commands.FunctionInstantiationException ex) {
				fail("macro " + id + " failed to instantiate: " + ex.messageComponent().getString());
			}
		}
		System.out.println("[check] macro functions: " + macros + " instantiated OK: " + ok);
	}

	private static void reportContent(Path resources, ReloadableServerResources rsr, RegistryAccess.Frozen worldgen) throws Exception {
		checkMacros(resources, rsr);
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

	/**
	 * Export terrain heights around every site. Fast path: the designed surface from ValleyTerrain
	 * (ceil of the design height = first air block). A sparse exact sample through the real generator
	 * reports the deviation caused by noise interpolation.
	 */
	private static void exportHeightmaps(RegistryAccess.Frozen worldgen, Path outFile) throws Exception {
		ValleyLayout layout = ValleyLayout.get();
		net.palemeridian.world.ValleyTerrain terrain = net.palemeridian.world.ValleyTerrain.get();
		WorldPreset preset = worldgen.lookupOrThrow(Registries.WORLD_PRESET).getValue(WorldPresets.NORMAL);
		NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) preset.createWorldDimensions().dimensions().get(LevelStem.OVERWORLD).generator();
		RandomState rs = RandomState.create(gen.generatorSettings().value(), worldgen.lookupOrThrow(Registries.NOISE), 0L);
		LevelHeightAccessor ha = LevelHeightAccessor.create(-64, 384);
		StringBuilder json = new StringBuilder("{\n");
		boolean first = true;
		java.util.concurrent.atomic.AtomicInteger maxDev = new java.util.concurrent.atomic.AtomicInteger();
		for (ValleyLayout.Site site : layout.sites) {
			int r = (int) Math.ceil(site.flatRadius() + site.falloff() + 12);
			int x0 = (int) Math.floor(site.x()) - r, z0 = (int) Math.floor(site.z()) - r, w = 2 * r + 1;
			int[] ground = new int[w * w];
			for (int dz = 0; dz < w; dz++) {
				for (int dx = 0; dx < w; dx++) {
					ground[dz * w + dx] = (int) Math.ceil(terrain.height(x0 + dx, z0 + dz));
				}
			}
			List<int[]> sample = new ArrayList<>();
			for (int dz = 0; dz < w; dz += 9) {
				for (int dx = 0; dx < w; dx += 9) {
					sample.add(new int[] {dx, dz});
				}
			}
			java.util.concurrent.ConcurrentHashMap<Integer, Integer> hist = new java.util.concurrent.ConcurrentHashMap<>();
			sample.parallelStream().forEach(pt -> {
				int exact = gen.getBaseHeight(x0 + pt[0], z0 + pt[1], Heightmap.Types.OCEAN_FLOOR_WG, ha, rs);
				int dev = exact - ground[pt[1] * w + pt[0]];
				hist.merge(dev, 1, Integer::sum);
				maxDev.accumulateAndGet(Math.abs(dev), Math::max);
			});
			if (!first) {
				json.append(",\n");
			}
			first = false;
			json.append("  \"").append(site.id()).append("\": {\"x0\": ").append(x0).append(", \"z0\": ").append(z0)
				.append(", \"w\": ").append(w).append(", \"ground\": ").append(java.util.Arrays.toString(ground)).append("}");
			System.out.println("[check] heightmap " + site.id() + " " + w + "x" + w + " exact-minus-design deviation histogram " + new java.util.TreeMap<>(hist));
		}
		json.append("\n}\n");
		Files.createDirectories(outFile.getParent());
		Files.writeString(outFile, json.toString());
		System.out.println("[check] wrote " + outFile + " (max |deviation| " + maxDev.get() + ")");
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
