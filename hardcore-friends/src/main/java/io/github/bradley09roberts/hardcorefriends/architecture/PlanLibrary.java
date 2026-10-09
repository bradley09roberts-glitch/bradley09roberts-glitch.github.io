package io.github.bradley09roberts.hardcorefriends.architecture;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;

/**
 * The building library: every plan file in {@code data/<namespace>/blueprints/} (sub-folders allowed), read with the
 * server's data each time it loads or {@code /reload} runs, so data packs can add plans or replace the mod's own. A
 * file's id is its namespace and path without the folder and {@code .json}: {@code hardcorefriends:house/oak_cottage}.
 * A bad file is logged and skipped, never a crash; what was wrong is kept for {@code /friends builds check}.
 *
 * <p>Provides {@link BlueprintLibrary}: {@code get(id)}, {@code byKind(kind)} (exact kind, or every sub-kind for a kind
 * with no colon: {@code shop} gives {@code shop:bakery} and {@code shop:smithy}) and {@code ids()}.
 */
public final class PlanLibrary implements BlueprintLibrary.Provider {
	/** The library's reload listener id. */
	public static final Identifier ID = Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "blueprints");
	private static final String FOLDER = "blueprints";

	/** What one load produced. */
	public record Snapshot(Map<String, Blueprint> byId, List<String> problems, int read, int skipped) {
		static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), 0, 0);
	}

	static final PlanLibrary INSTANCE = new PlanLibrary();
	private static volatile Snapshot snapshot = Snapshot.EMPTY;

	private PlanLibrary() {
	}

	/** Registers the reload listener and the library provider. */
	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new Listener());
		BlueprintLibrary.provide(INSTANCE);
	}

	/** The plans as last loaded. */
	public static Snapshot snapshot() {
		return snapshot;
	}

	// ------------------------------------------------------------------ provider

	@Override
	public Optional<Blueprint> get(String id) {
		return Optional.ofNullable(snapshot.byId().get(id));
	}

	@Override
	public List<Blueprint> byKind(String kind) {
		List<Blueprint> list = new ArrayList<>();
		boolean family = !kind.contains(":");
		for (Blueprint b : snapshot.byId().values()) {
			if (b.kind().equals(kind) || family && b.kind().startsWith(kind + ":")) {
				list.add(b);
			}
		}
		return list;
	}

	@Override
	public List<String> ids() {
		return List.copyOf(snapshot.byId().keySet());
	}

	/** Every kind with its plans, sorted, for listing. */
	public static Map<String, List<Blueprint>> byKindSorted() {
		Map<String, List<Blueprint>> map = new TreeMap<>();
		for (Blueprint b : snapshot.byId().values()) {
			map.computeIfAbsent(b.kind(), k -> new ArrayList<>()).add(b);
		}
		map.values().forEach(l -> l.sort(Comparator.comparing(Blueprint::id)));
		return map;
	}

	// -------------------------------------------------------------------- loading

	/** Reads every plan file; runs off the server thread while data loads. */
	static Snapshot load(Map<Identifier, Resource> files) {
		Map<String, Blueprint> byId = new LinkedHashMap<>();
		List<String> problems = new ArrayList<>();
		int read = 0;
		int skipped = 0;
		List<Map.Entry<Identifier, Resource>> sorted = new ArrayList<>(files.entrySet());
		sorted.sort(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)));
		for (Map.Entry<Identifier, Resource> f : sorted) {
			Identifier file = f.getKey();
			String path = file.getPath();
			String id = file.getNamespace() + ":" + path.substring(FOLDER.length() + 1, path.length() - ".json".length());
			read++;
			PlanParser.Result result;
			try (Reader reader = f.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				result = PlanParser.parse(id, json);
			} catch (Exception e) {
				result = new PlanParser.Result(null, List.of("could not be read: " + e.getMessage()), List.of());
			}
			for (String w : result.warnings()) {
				problems.add(id + " (warning): " + w);
			}
			if (result.plan() == null) {
				skipped++;
				for (String err : result.errors()) {
					problems.add(id + ": " + err);
				}
				HardcoreFriends.LOGGER.warn("Building plan {} skipped: {}", id, String.join("; ", result.errors()));
			} else {
				byId.put(id, result.plan());
			}
		}
		return new Snapshot(Map.copyOf(byId), List.copyOf(problems), read, skipped);
	}

	/** Loads the plans with the server's data, and swaps them in on the server thread. */
	private static final class Listener extends SimpleReloadListener<Snapshot> {
		@Override
		protected Snapshot prepare(PreparableReloadListener.SharedState state) {
			Map<Identifier, Resource> files = state.resourceManager().listResources(FOLDER, file -> file.getPath().endsWith(".json"));
			return load(files);
		}

		@Override
		protected void apply(Snapshot loaded, PreparableReloadListener.SharedState state) {
			snapshot = loaded;
			HardcoreFriends.LOGGER.info("Hardcore Friends: {} building plans loaded{}", loaded.byId().size(),
				loaded.skipped() > 0 ? " (" + loaded.skipped() + " skipped: see /friends builds check)" : "");
		}
	}
}
