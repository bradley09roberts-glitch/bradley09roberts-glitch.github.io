package io.github.bradley09roberts.hardcorefriends.market;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * The market's one way of asking the village for a building: the village package decides <i>where</i> shops and
 * workplaces go, so the market only says which kind it would like, and why. The village was written at the same time
 * as the market, so this adapter looks up its published call, {@code village.VillagePlan.requestBuilding(MinecraftServer
 * server, String kind, String reason)}, once by name and calls it; without it (no town plan in this build) requests do
 * nothing and the trades work plainly at the camp. Built workplaces are found through the building records themselves
 * ({@link Workplaces}), so nothing else of the village is needed.
 *
 * <p>Merging: once {@code VillagePlan} is in the tree, {@link #request} may simply call it directly; nothing else
 * changes.
 */
final class VillageLink {
	private static final String PLAN_CLASS = "io.github.bradley09roberts.hardcorefriends.village.VillagePlan";
	private static volatile @Nullable Method method;
	private static volatile boolean looked;
	private static volatile boolean failedOnce;

	private VillageLink() {
	}

	/** True if the village's town plan takes requests in this build. */
	static boolean available() {
		return method() != null;
	}

	/**
	 * Asks the village to put up a building of this kind ({@code "shop:bakery"}), saying why for its listings. Returns
	 * false if there is no town plan to ask, or it turned the request down.
	 */
	static boolean request(MinecraftServer server, String kind, String reason) {
		Method m = method();
		if (m == null) {
			return false;
		}
		try {
			Object answer = m.invoke(null, server, kind, reason);
			return !(answer instanceof Boolean b) || b;
		} catch (ReflectiveOperationException | RuntimeException e) {
			if (!failedOnce) {
				failedOnce = true;
				HardcoreFriends.LOGGER.warn("Asking the village for a {} failed; trades will work at the camp", kind, e);
			}
			return false;
		}
	}

	private static @Nullable Method method() {
		if (!looked) {
			looked = true;
			try {
				Method m = Class.forName(PLAN_CLASS).getMethod("requestBuilding", MinecraftServer.class, String.class, String.class);
				method = Modifier.isStatic(m.getModifiers()) ? m : null;
			} catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
				method = null;
			}
		}
		return method;
	}
}
