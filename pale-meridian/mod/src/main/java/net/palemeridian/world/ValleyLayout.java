package net.palemeridian.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable view of {@code palemeridian_layout.json} (generated from {@code tools/layout.json}).
 * The layout is the single source of truth for valley geometry shared by the Java worldgen and the
 * Python content generators.
 */
public final class ValleyLayout {
	public static final String RESOURCE = "/palemeridian_layout.json";
	private static volatile ValleyLayout instance;

	public final double centerX, centerZ;
	public final double innerRadius, blendOuterRadius, biomeRadius;
	public final double floorHeight, rimHeight, rimStart, rimHillAmplitude, hillAmplitude;

	public final double lakeX, lakeZ, lakeRx, lakeRz, lakeDepth, lakeShoreWidth;
	public final int waterLevel;

	public final double cliffBaseZ, cliffX0, cliffCurve, cliffXMin, cliffXMax, cliffEdgeFade, cliffHeight, cliffWidth;

	public final double fenX, fenZ, fenRadius, fenHeight, fenPoolNoise;

	public final List<Site> sites;
	public final List<Road> roads;
	public final List<QuietZone> quietZones;
	public final Map<String, double[]> districtSeeds;
	public final double rimRadius, deepcutX, deepcutZ, deepcutRadius, deepcutDepthTop, boundaryNoise;
	public final Map<String, String> districtGroups;
	public final int spawnX, spawnY, spawnZ;
	public final float spawnYaw;

	public record Site(String id, double x, double z, int floor, double flatRadius, double falloff, String district, boolean island) {
	}

	public record Road(String id, double width, double[][] points) {
	}

	/** Authored underground space: natural caves are suppressed inside the box above yMin. */
	public record QuietZone(String id, int x1, int z1, int x2, int z2, int yMin) {
		public boolean contains(int x, int y, int z) {
			return x >= this.x1 && x <= this.x2 && z >= this.z1 && z <= this.z2 && y >= this.yMin;
		}
	}

	private ValleyLayout(JsonObject root) {
		JsonObject v = root.getAsJsonObject("valley");
		JsonArray c = v.getAsJsonArray("center");
		this.centerX = c.get(0).getAsDouble();
		this.centerZ = c.get(1).getAsDouble();
		this.innerRadius = v.get("inner_radius").getAsDouble();
		this.blendOuterRadius = v.get("blend_outer_radius").getAsDouble();
		this.biomeRadius = v.get("biome_radius").getAsDouble();
		this.floorHeight = v.get("floor_height").getAsDouble();
		this.rimHeight = v.get("rim_height").getAsDouble();
		this.rimStart = v.get("rim_start").getAsDouble();
		this.rimHillAmplitude = v.get("rim_hill_amplitude").getAsDouble();
		this.hillAmplitude = v.get("hill_amplitude").getAsDouble();

		JsonObject l = root.getAsJsonObject("lake");
		JsonArray lc = l.getAsJsonArray("center");
		this.lakeX = lc.get(0).getAsDouble();
		this.lakeZ = lc.get(1).getAsDouble();
		this.lakeRx = l.get("radius_x").getAsDouble();
		this.lakeRz = l.get("radius_z").getAsDouble();
		this.lakeDepth = l.get("depth").getAsDouble();
		this.lakeShoreWidth = l.get("shore_width").getAsDouble();
		this.waterLevel = l.get("water_level").getAsInt();

		JsonObject cl = root.getAsJsonObject("cliff");
		this.cliffBaseZ = cl.get("base_z").getAsDouble();
		this.cliffX0 = cl.get("x0").getAsDouble();
		this.cliffCurve = cl.get("curve").getAsDouble();
		this.cliffXMin = cl.get("x_min").getAsDouble();
		this.cliffXMax = cl.get("x_max").getAsDouble();
		this.cliffEdgeFade = cl.get("edge_fade").getAsDouble();
		this.cliffHeight = cl.get("height").getAsDouble();
		this.cliffWidth = cl.get("width").getAsDouble();

		JsonObject f = root.getAsJsonObject("fen");
		JsonArray fc = f.getAsJsonArray("center");
		this.fenX = fc.get(0).getAsDouble();
		this.fenZ = fc.get(1).getAsDouble();
		this.fenRadius = f.get("radius").getAsDouble();
		this.fenHeight = f.get("height").getAsDouble();
		this.fenPoolNoise = f.get("pool_noise").getAsDouble();

		List<Site> siteList = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("sites").entrySet()) {
			JsonObject s = e.getValue().getAsJsonObject();
			JsonArray sc = s.getAsJsonArray("center");
			siteList.add(new Site(
				e.getKey(), sc.get(0).getAsDouble(), sc.get(1).getAsDouble(), s.get("floor").getAsInt(),
				s.get("flat_radius").getAsDouble(), s.get("falloff").getAsDouble(), s.get("district").getAsString(),
				s.has("island") && s.get("island").getAsBoolean()));
		}
		this.sites = List.copyOf(siteList);

		List<Road> roadList = new ArrayList<>();
		for (JsonElement re : root.getAsJsonArray("roads")) {
			JsonObject r = re.getAsJsonObject();
			JsonArray pts = r.getAsJsonArray("points");
			double[][] p = new double[pts.size()][2];
			for (int i = 0; i < pts.size(); i++) {
				p[i][0] = pts.get(i).getAsJsonArray().get(0).getAsDouble();
				p[i][1] = pts.get(i).getAsJsonArray().get(1).getAsDouble();
			}
			roadList.add(new Road(r.get("id").getAsString(), r.get("width").getAsDouble(), p));
		}
		this.roads = List.copyOf(roadList);

		List<QuietZone> zones = new ArrayList<>();
		if (root.has("quiet_zones")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("quiet_zones").entrySet()) {
				if (e.getKey().startsWith("_")) {
					continue;
				}
				JsonObject q = e.getValue().getAsJsonObject();
				JsonArray b = q.getAsJsonArray("box");
				zones.add(new QuietZone(e.getKey(), Math.min(b.get(0).getAsInt(), b.get(2).getAsInt()), Math.min(b.get(1).getAsInt(), b.get(3).getAsInt()),
					Math.max(b.get(0).getAsInt(), b.get(2).getAsInt()), Math.max(b.get(1).getAsInt(), b.get(3).getAsInt()), q.get("y_min").getAsInt()));
			}
		}
		this.quietZones = List.copyOf(zones);

		JsonObject d = root.getAsJsonObject("districts");
		Map<String, double[]> seeds = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("seeds").entrySet()) {
			JsonArray a = e.getValue().getAsJsonArray();
			seeds.put(e.getKey(), new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()});
		}
		// Insertion order matters (district salts); Map.copyOf would randomise iteration order per JVM.
		this.districtSeeds = Collections.unmodifiableMap(seeds);
		this.rimRadius = d.get("rim_radius").getAsDouble();
		JsonArray ds = d.getAsJsonArray("deepcut_seed");
		this.deepcutX = ds.get(0).getAsDouble();
		this.deepcutZ = ds.get(1).getAsDouble();
		this.deepcutRadius = d.get("deepcut_radius").getAsDouble();
		this.deepcutDepthTop = d.get("deepcut_depth_top").getAsDouble();
		this.boundaryNoise = d.get("boundary_noise").getAsDouble();

		Map<String, String> groups = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("district_groups").entrySet()) {
			if (!e.getKey().startsWith("_")) {
				groups.put(e.getKey(), e.getValue().getAsString());
			}
		}
		this.districtGroups = Collections.unmodifiableMap(groups);

		JsonObject sp = root.getAsJsonObject("spawn");
		JsonArray pos = sp.getAsJsonArray("pos");
		this.spawnX = pos.get(0).getAsInt();
		this.spawnY = pos.get(1).getAsInt();
		this.spawnZ = pos.get(2).getAsInt();
		this.spawnYaw = sp.get("yaw").getAsFloat();
	}

	public static ValleyLayout get() {
		ValleyLayout local = instance;
		if (local == null) {
			synchronized (ValleyLayout.class) {
				local = instance;
				if (local == null) {
					instance = local = load();
				}
			}
		}
		return local;
	}

	private static ValleyLayout load() {
		try (InputStream in = ValleyLayout.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing " + RESOURCE + " in the Pale Meridian jar");
			}
			return new ValleyLayout(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read " + RESOURCE, e);
		}
	}

	public Site site(String id) {
		for (Site s : this.sites) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		throw new IllegalArgumentException("Unknown site " + id);
	}
}
