package net.palemeridian.world;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Pure, deterministic terrain maths for the Vale of Vell.
 *
 * <p>The valley deliberately uses a fixed internal noise seed rather than the world seed, so the
 * designed valley (heights, lake, cliffs, district boundaries) is identical in every world. This is
 * what lets the campaign's fixed coordinates be verified once. The world outside the rim is ordinary
 * seed-driven vanilla terrain, blended in across the rim band.</p>
 *
 * <p>All methods are thread-safe: noise instances are immutable and the per-thread column cache is a
 * {@link ThreadLocal}.</p>
 */
public final class ValleyTerrain {
	/** Fixed seed: the valley is the same in every world. */
	private static final long VALLEY_SEED = 0x50A1E_3E81D1A7L;
	private static volatile ValleyTerrain instance;

	private final ValleyLayout l;
	private final SimplexNoise hillsA, hillsB, hillsC, rimNoise, fenNoise, cliffNoise, detail, boundary;
	private final ThreadLocal<double[]> columnCache = ThreadLocal.withInitial(() -> new double[] {Double.NaN, Double.NaN, 0.0});

	private ValleyTerrain(ValleyLayout layout) {
		this.l = layout;
		XoroshiroRandomSource r = new XoroshiroRandomSource(VALLEY_SEED);
		this.hillsA = new SimplexNoise(r);
		this.hillsB = new SimplexNoise(r);
		this.hillsC = new SimplexNoise(r);
		this.rimNoise = new SimplexNoise(r);
		this.fenNoise = new SimplexNoise(r);
		this.cliffNoise = new SimplexNoise(r);
		this.detail = new SimplexNoise(r);
		this.boundary = new SimplexNoise(r);
	}

	public static ValleyTerrain get() {
		ValleyTerrain local = instance;
		if (local == null) {
			synchronized (ValleyTerrain.class) {
				local = instance;
				if (local == null) {
					instance = local = new ValleyTerrain(ValleyLayout.get());
				}
			}
		}
		return local;
	}

	public ValleyLayout layout() {
		return this.l;
	}

	static double smooth(double t) {
		if (t <= 0.0) {
			return 0.0;
		}
		if (t >= 1.0) {
			return 1.0;
		}
		return t * t * (3.0 - 2.0 * t);
	}

	static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}

	public double distanceFromCenter(double x, double z) {
		return Math.hypot(x - this.l.centerX, z - this.l.centerZ);
	}

	/** 1 inside the designed valley, 0 outside the blend band, smooth in between. */
	public double valleyWeight(double x, double z) {
		double r = this.distanceFromCenter(x, z);
		if (r <= this.l.innerRadius) {
			return 1.0;
		}
		if (r >= this.l.blendOuterRadius) {
			return 0.0;
		}
		return 1.0 - smooth((r - this.l.innerRadius) / (this.l.blendOuterRadius - this.l.innerRadius));
	}

	/** Normalised lake ellipse distance: &lt;1 inside the lake. */
	public double lakeEllipse(double x, double z) {
		double ex = (x - this.l.lakeX) / this.l.lakeRx;
		double ez = (z - this.l.lakeZ) / this.l.lakeRz;
		return Math.sqrt(ex * ex + ez * ez);
	}

	/** Terrain before roads, sites and the cliff. */
	private double base(double x, double z) {
		double r = this.distanceFromCenter(x, z);
		double t = smooth((r - this.l.rimStart) / (this.l.innerRadius - this.l.rimStart));
		double h = this.l.floorHeight + (this.l.rimHeight - this.l.floorHeight) * t;
		double hills = 0.6 * this.hillsA.getValue(x / 150.0, z / 150.0)
			+ 0.3 * this.hillsB.getValue(x / 55.0, z / 55.0)
			+ 0.1 * this.hillsC.getValue(x / 22.0, z / 22.0);
		h += this.l.hillAmplitude * hills;
		double rim = 0.5 + 0.5 * this.rimNoise.getValue(x / 90.0, z / 90.0);
		h += this.l.rimHillAmplitude * rim * t;

		double fenD = Math.hypot(x - this.l.fenX, z - this.l.fenZ);
		if (fenD < this.l.fenRadius * 1.35) {
			double fw = 1.0 - smooth((fenD - this.l.fenRadius * 0.7) / (this.l.fenRadius * 0.65));
			double fenH = this.l.fenHeight + this.l.fenPoolNoise * this.fenNoise.getValue(x / 28.0, z / 28.0);
			h = lerp(fw, h, fenH);
		}

		double e = this.lakeEllipse(x, z);
		if (e < 1.0) {
			h = (this.l.waterLevel - 1.0) - this.l.lakeDepth * Math.pow(1.0 - e, 0.7);
		} else if (e < 1.0 + this.l.lakeShoreWidth) {
			double s = smooth((e - 1.0) / this.l.lakeShoreWidth);
			h = lerp(s, this.l.waterLevel + 0.2, Math.max(h, this.l.waterLevel + 0.2));
		}
		return h;
	}

	/** Site plateau influence: 1 on the plateau, fading to 0 across the falloff. */
	public double siteInfluence(double x, double z, double margin) {
		double best = 0.0;
		for (ValleyLayout.Site s : this.l.sites) {
			double d = Math.hypot(x - s.x(), z - s.z());
			double w = 1.0 - smooth((d - s.flatRadius() - margin) / Math.max(1.0, s.falloff()));
			if (w > best) {
				best = w;
			}
		}
		return best;
	}

	private double applySites(double h, double x, double z) {
		for (ValleyLayout.Site s : this.l.sites) {
			double d = Math.hypot(x - s.x(), z - s.z());
			if (d < s.flatRadius() + s.falloff()) {
				double w = 1.0 - smooth((d - s.flatRadius()) / Math.max(1.0, s.falloff()));
				// Aim half a block below the floor so the solid/air boundary never lands on an exact
				// integer (density exactly 0 there would round either way).
				h = lerp(w, h, s.floor() - 0.5);
			}
		}
		return h;
	}

	private double baseWithSites(double x, double z) {
		return this.applySites(this.base(x, z), x, z);
	}

	/** 0..1 how strongly the north cliff applies (for detail roughness). */
	public double cliffFactor(double x, double z) {
		double fade = this.l.cliffEdgeFade;
		if (x < this.l.cliffXMin - fade || x > this.l.cliffXMax + fade) {
			return 0.0;
		}
		double lineZ = this.l.cliffBaseZ + this.l.cliffCurve * (x - this.l.cliffX0) * (x - this.l.cliffX0);
		double dz = lineZ - z;
		double s = smooth((dz + this.l.cliffWidth * 0.5) / this.l.cliffWidth);
		double ef = Math.min(smooth((x - (this.l.cliffXMin - fade)) / fade), smooth(((this.l.cliffXMax + fade) - x) / fade));
		return s * ef;
	}

	/** True when (x, z) is north of the cliff line (inside the cliff mass or on top of it). */
	public boolean northOfCliff(double x, double z, double margin) {
		double lineZ = this.l.cliffBaseZ + this.l.cliffCurve * (x - this.l.cliffX0) * (x - this.l.cliffX0);
		return z < lineZ + margin;
	}

	private double cliff(double x, double z) {
		double f = this.cliffFactor(x, z);
		if (f <= 0.0) {
			return 0.0;
		}
		double jag = 4.0 * this.cliffNoise.getValue(x / 30.0, z / 30.0);
		return (this.l.cliffHeight + jag) * f;
	}

	/** Distance-weighted road grading influence and target height. Returns {weight, height}. */
	private double[] road(double x, double z) {
		double bestW = 0.0;
		double bestH = 0.0;
		for (ValleyLayout.Road road : this.l.roads) {
			double[][] p = road.points();
			double half = road.width() * 0.5 + 1.5;
			for (int i = 0; i + 1 < p.length; i++) {
				double ax = p[i][0], az = p[i][1], bx = p[i + 1][0], bz = p[i + 1][1];
				double vx = bx - ax, vz = bz - az;
				double len2 = vx * vx + vz * vz;
				double t = len2 <= 0.0 ? 0.0 : ((x - ax) * vx + (z - az) * vz) / len2;
				t = Math.max(0.0, Math.min(1.0, t));
				double px = ax + vx * t, pz = az + vz * t;
				double d = Math.hypot(x - px, z - pz);
				if (d > half + 7.0) {
					continue;
				}
				double w = 1.0 - smooth((d - half) / 7.0);
				if (w > bestW) {
					bestW = w;
					bestH = this.baseWithSites(px, pz);
				}
			}
		}
		return new double[] {bestW, bestH};
	}

	public double roadInfluence(double x, double z) {
		return this.road(x, z)[0];
	}

	/** Designed surface height: the Y of the first air block above ground (fractional). */
	public double height(double x, double z) {
		double[] cache = this.columnCache.get();
		if (cache[0] == x && cache[1] == z) {
			return cache[2];
		}
		double h = this.baseWithSites(x, z);
		double[] rd = this.road(x, z);
		if (rd[0] > 0.0) {
			h = lerp(rd[0], h, rd[1]);
		}
		h = this.applySites(h, x, z);
		h += this.cliff(x, z);
		cache[0] = x;
		cache[1] = z;
		cache[2] = h;
		return h;
	}

	/**
	 * Replacement for vanilla's {@code sloped_cheese} inside the valley. Positive = solid. The scale
	 * (≈0.1 per block below the surface) means vanilla's underground-cave branch (threshold 1.5625)
	 * starts ~16 blocks below the designed surface.
	 */
	public double surfaceDensity(int x, int y, int z) {
		double h = this.height(x, z);
		double dy = h - y;
		// Linear (same slope) within 16 blocks of the surface: the noise router interpolates density
		// across 8-block cells, so any kink near the surface would shift the ground by 1-2 blocks.
		double dens = dy >= -16.0 ? dy * 0.1 : -1.6 + (dy + 16.0) * 0.045;
		if (Math.abs(dy) < 18.0) {
			double calm = Math.max(this.siteInfluence(x, z, 4.0), this.roadInfluence(x, z));
			double amp = (0.10 + 0.55 * this.cliffFactor(x, z)) * (1.0 - calm);
			if (this.lakeEllipse(x, z) < 1.05) {
				amp *= 0.3;
			}
			if (amp > 0.001) {
				dens += amp * this.detail.getValue(x / 20.0, y / 14.0, z / 20.0);
			}
		}
		return dens;
	}

	/** Positive near sites and roads down to 40 blocks below the surface: suppresses caves there. */
	public double caveGuard(int x, int y, int z) {
		for (ValleyLayout.QuietZone q : this.l.quietZones) {
			if (q.contains(x, y, z)) {
				return 1.0;
			}
		}
		double calm = Math.max(this.siteInfluence(x, z, 10.0), this.roadInfluence(x, z));
		if (calm < 0.15) {
			return -1.0E6;
		}
		double h = this.height(x, z);
		return y > h - 40.0 ? 1.0 : -1.0E6;
	}

	/** Deterministic boundary jitter for district borders, in blocks. */
	public double boundaryJitter(double x, double z, int salt) {
		return this.l.boundaryNoise * this.boundary.getValue(x / 70.0 + salt * 13.37, z / 70.0 - salt * 7.91);
	}

	/** Story district for the biome layout at a block position inside the valley. */
	public String district(int x, int y, int z) {
		if (this.northOfCliff(x, z, 3.0)
			&& Math.hypot(x - this.l.deepcutX, z - this.l.deepcutZ) < this.l.deepcutRadius + this.boundaryJitter(x, z, 11) * 0.5) {
			return "deepcut";
		}
		double e = this.lakeEllipse(x, z);
		if (e < 1.02) {
			return "mere";
		}
		double r = this.distanceFromCenter(x, z);
		if (r >= this.l.rimRadius + this.boundaryJitter(x, z, 3)) {
			return "rim";
		}
		String best = "hollin";
		double bestScore = Double.MAX_VALUE;
		int salt = 0;
		for (var entry : this.l.districtSeeds.entrySet()) {
			double[] s = entry.getValue();
			double score = Math.hypot(x - s[0], z - s[1]) / s[2] + this.boundaryJitter(x, z, 20 + salt);
			if (score < bestScore) {
				bestScore = score;
				best = entry.getKey();
			}
			salt++;
		}
		return best;
	}
}
