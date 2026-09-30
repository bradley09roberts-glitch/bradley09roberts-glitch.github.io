package net.palemeridian.world;

import java.util.ArrayList;
import java.util.List;

/** Deterministic lamp-post positions along the valley roads (shared by worldgen and restoration). */
public final class LampPosts {
	public static final double SPACING = 26.0;
	public static final double OFFSET = 3.5;

	public record Post(int x, int z, String road) {
	}

	private static volatile List<Post> cache;

	private LampPosts() {
	}

	public static List<Post> all() {
		List<Post> local = cache;
		if (local == null) {
			synchronized (LampPosts.class) {
				local = cache;
				if (local == null) {
					cache = local = compute(ValleyLayout.get());
				}
			}
		}
		return local;
	}

	private static List<Post> compute(ValleyLayout layout) {
		List<Post> out = new ArrayList<>();
		for (ValleyLayout.Road road : layout.roads) {
			double[][] p = road.points();
			double carried = SPACING * 0.5;
			int index = 0;
			for (int i = 0; i + 1 < p.length; i++) {
				double ax = p[i][0], az = p[i][1], bx = p[i + 1][0], bz = p[i + 1][1];
				double len = Math.hypot(bx - ax, bz - az);
				if (len <= 0.0) {
					continue;
				}
				double ux = (bx - ax) / len, uz = (bz - az) / len;
				double t = carried;
				while (t < len) {
					double side = (index % 2 == 0) ? 1.0 : -1.0;
					double x = ax + ux * t - uz * OFFSET * side;
					double z = az + uz * t + ux * OFFSET * side;
					out.add(new Post((int) Math.floor(x), (int) Math.floor(z), road.id()));
					index++;
					t += SPACING;
				}
				carried = t - len;
			}
		}
		return List.copyOf(out);
	}
}
