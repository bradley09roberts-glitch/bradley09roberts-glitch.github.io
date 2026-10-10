package io.github.bradley09roberts.hardcorefriends.architecture;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

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
 *
 * <p>Every plan read keeps the content it was read from ({@link #sourceOf}), so a site can remember the exact version
 * of the plan it was reserved with and go on using it ({@link #kept}) after a data pack or an update of the mod
 * changes or removes the plan under that id: a building is never finished, repaired or furnished from another plan.
 */
public final class PlanLibrary implements BlueprintLibrary.Provider {
	/** The library's reload listener id. */
	public static final Identifier ID = Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "blueprints");
	private static final String FOLDER = "blueprints";

	/** What one load produced. */
	public record Snapshot(Map<String, Blueprint> byId, List<String> problems, int read, int skipped) {
		static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), 0, 0);
	}

	/** A plan file's content as read (compact JSON), and its fingerprint. */
	public record Source(String json, String print) {
		/**
		 * Longest content kept with a site: a saved text may take at most 65,535 bytes, and a character at most three
		 * (the mod's largest plan is under 7,000 characters).
		 */
		static final int MAX_KEPT = 21_000;

		/** True if this content can be kept with a site in the world's save. */
		public boolean keepable() {
			return !json.isEmpty() && json.length() <= MAX_KEPT;
		}
	}

	static final PlanLibrary INSTANCE = new PlanLibrary();
	private static volatile Snapshot snapshot = Snapshot.EMPTY;
	/** The content each plan in memory was read from (loaded or kept), by the plan object itself. */
	private static final Map<Blueprint, Source> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
	/** Plans read again from content kept with sites, by id and fingerprint (empty: it no longer reads). */
	private static final Map<String, Optional<Blueprint>> KEPT = new ConcurrentHashMap<>();

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

	// ------------------------------------------------------------------- versions

	/** The content a library plan was read from, or null for plans that are not from the library (the camp's own). */
	public static @Nullable Source sourceOf(Blueprint plan) {
		return SOURCES.get(plan);
	}

	/**
	 * The plan with this id as it was when its content was kept (a site reserved with an older version): read again from
	 * that content, once per version. Empty if it no longer reads (the mod's plan format changed), and logged once.
	 */
	public static Optional<Blueprint> kept(String id, String print, String json) {
		return KEPT.computeIfAbsent(id + "@" + print, k -> {
			try {
				PlanParser.Result result = PlanParser.parse(id, JsonParser.parseString(json));
				if (result.plan() != null) {
					SOURCES.put(result.plan(), new Source(json, print));
					return Optional.of(result.plan());
				}
				HardcoreFriends.LOGGER.warn("The kept version of building plan {} no longer reads ({}); its sites use the "
					+ "library's", id, String.join("; ", result.errors()));
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.warn("The kept version of building plan {} no longer reads; its sites use the library's", id, e);
			}
			return Optional.empty();
		});
	}

	/** A short fingerprint of a plan's content: the same content always gives the same one. */
	static String fingerprint(String json) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(json.hashCode()) + "-" + json.length(); // every Java has SHA-256; just in case
		}
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
			String content = "";
			try (Reader reader = f.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				content = json.toString(); // compact: the same plan gives the same text however the file is laid out
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
				SOURCES.put(result.plan(), new Source(content, fingerprint(content)));
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
