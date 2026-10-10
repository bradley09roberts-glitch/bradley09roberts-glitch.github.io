package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * The village trades a grown-up may hold besides their speciality: each has its id (the building library's
 * {@code profession} meta uses the same words), a title for status lines, the building kinds it works in, and the
 * specialities that suit it best (first the best). A few trades can also be held "at the camp", plainly, before the
 * village has built them a workplace: a stallholder at the supply chest, a fisher on the bank, a shepherd at the pen
 * and a cook at the campfire.
 */
public enum Trade {
	BAKER("baker", "Baker", null, "baking bread, pies and cakes", List.of("shop:bakery"), Role.FARMER, Role.FORAGER),
	SHOPKEEPER("shopkeeper", "Shopkeeper", "Stallholder", "keeping the village store", List.of("shop:general", "civic:market"),
		Role.STRATEGIST, Role.EXPLORER, Role.FORAGER),
	BUTCHER("butcher", "Butcher", null, "cooking and selling meat", List.of("shop:butcher"), Role.FARMER, Role.WARRIOR),
	FISHMONGER("fishmonger", "Fishmonger", null, "fishing and selling fish", List.of("shop:fishmonger"), Role.FORAGER, Role.EXPLORER),
	TAILOR("tailor", "Tailor", null, "making carpets and beds", List.of("shop:tailor"), Role.LANDSCAPER, Role.BUILDER),
	BLACKSMITH("blacksmith", "Blacksmith", null, "making tools and armour", List.of("workplace:blacksmith", "shop:smith"),
		Role.MINER, Role.WARRIOR, Role.INVENTOR),
	FISHER("fisher", "Fisher", "Fisher", "fishing", List.of("workplace:fisher"), Role.FORAGER, Role.EXPLORER),
	MASON("mason", "Mason", null, "cutting stone for the builders", List.of("workplace:mason"), Role.BUILDER, Role.MINER),
	BEEKEEPER("beekeeper", "Beekeeper", null, "keeping bees", List.of("workplace:beekeeper"), Role.FARMER, Role.LANDSCAPER, Role.FORAGER),
	CARPENTER("carpenter", "Carpenter", null, "making stairs, doors and furniture", List.of("workplace:carpenter"),
		Role.BUILDER, Role.FORAGER),
	DOCTOR("doctor", "Doctor", null, "looking after the hurt and poorly", List.of("workplace:doctor"), Role.STRATEGIST, Role.FARMER),
	SHEPHERD("shepherd", "Shepherd", "Shepherd", "shearing the flock", List.of("workplace:shepherd"), Role.FARMER, Role.LANDSCAPER),
	FARMER("farmer", "Farmer", null, "composting for the fields", List.of("workplace:farmer"), Role.FARMER, Role.LANDSCAPER),
	TEACHER("teacher", "Teacher", null, "teaching the children", List.of("civic:school"), Role.STRATEGIST, Role.BUILDER),
	INNKEEPER("innkeeper", "Innkeeper", "Cook", "cooking meals for the village", List.of("civic:tavern"), Role.FARMER, Role.FORAGER);

	private final String id;
	private final String title;
	private final @Nullable String campTitle;
	private final String work;
	private final List<String> kinds;
	private final List<Role> suits;

	Trade(String id, String title, @Nullable String campTitle, String work, List<String> kinds, Role... suits) {
		this.id = id;
		this.title = title;
		this.campTitle = campTitle;
		this.work = work;
		this.kinds = kinds;
		this.suits = List.of(suits);
	}

	/** The id used in saves, plan meta and {@code civic.Professions}: "baker". */
	public String id() {
		return id;
	}

	/** "Baker". */
	public String title() {
		return title;
	}

	/** The title while held at the camp with no workplace of its own yet ("Stallholder", "Cook"). */
	public String campTitle() {
		return campTitle != null ? campTitle : title;
	}

	/** True if this trade can be held at the camp before the village builds it a workplace. */
	public boolean campTrade() {
		return campTitle != null;
	}

	/** What the work is, for {@code /friends trades}: "baking bread, pies and cakes". */
	public String work() {
		return work;
	}

	/** The building kinds this trade works in. */
	public List<String> kinds() {
		return kinds;
	}

	/** The specialities that suit this trade, best first. */
	public List<Role> suits() {
		return suits;
	}

	/** The kind of work practising this trade trains (its best-suited speciality's). */
	public Role skillRole() {
		return suits.getFirst();
	}

	/** The trade with this id ("baker"), ignoring case. */
	public static Optional<Trade> byId(@Nullable String id) {
		if (id == null) {
			return Optional.empty();
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		for (Trade t : values()) {
			if (t.id.equals(key)) {
				return Optional.of(t);
			}
		}
		return Optional.empty();
	}

	/** The trade a building kind houses ({@code shop:bakery} is the baker's), if any. */
	public static Optional<Trade> forKind(String kind) {
		for (Trade t : values()) {
			if (t.kinds.contains(kind)) {
				return Optional.of(t);
			}
		}
		return Optional.empty();
	}

	/**
	 * The trade that works in a plan: its {@code profession} meta when that names a trade (data packs may add plans of
	 * new kinds for a known trade), otherwise its kind's.
	 */
	public static Optional<Trade> forPlan(Blueprint plan) {
		Optional<Trade> byMeta = byId(plan.meta().get("profession"));
		return byMeta.isPresent() ? byMeta : forKind(plan.kind());
	}
}
