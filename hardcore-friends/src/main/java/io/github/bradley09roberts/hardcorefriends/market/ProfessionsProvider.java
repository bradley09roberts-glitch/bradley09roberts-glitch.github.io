package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * The market's answers to {@code civic.Professions}: a grown-up's trade id ({@link Trade#id()}), where they work (the
 * job block of their workplace, the teacher's place at the school, or the camp spot a trade held at the camp works
 * from), and a trade's title for status lines.
 */
final class ProfessionsProvider implements Professions.Provider {
	@Override
	public Optional<String> professionOf(CompanionEntity companion) {
		if (!(companion.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		MarketData.Holding h = Market.holding(level.getServer(), companion);
		return h == null ? Optional.empty() : Optional.of(h.trade.id());
	}

	@Override
	public Optional<BlockPos> workplaceOf(CompanionEntity companion) {
		if (!(companion.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		MarketData.Holding h = Market.holding(level.getServer(), companion);
		if (h == null) {
			return Optional.empty();
		}
		if (h.atCamp()) {
			return Optional.ofNullable(Market.campStation(level, h.trade));
		}
		Optional<Workplace> w = Workplaces.byKey(level, h.site);
		if (w.isEmpty()) {
			return Optional.empty();
		}
		if (h.trade == Trade.TEACHER) {
			List<BlockPos> teacher = w.get().markers(level, "teacher");
			if (!teacher.isEmpty()) {
				return Optional.of(teacher.getFirst());
			}
		}
		return Optional.of(w.get().job());
	}

	@Override
	public String title(String profession) {
		Optional<Trade> trade = Trade.byId(profession);
		if (trade.isPresent()) {
			return trade.get().title();
		}
		return profession.isEmpty() ? profession : profession.substring(0, 1).toUpperCase(Locale.ROOT) + profession.substring(1);
	}
}
