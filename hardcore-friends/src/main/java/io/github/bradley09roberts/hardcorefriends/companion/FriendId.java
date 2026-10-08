package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The nine named friends. Each has a fixed role, skin and personality. Ordinal values are synced to clients
 * and saved to disk, so new entries must only ever be appended.
 */
public enum FriendId {
	//     name     role                 colour     chat  retreat brave generous roam speed hp  armour starter
	FERN("Fern", Role.FARMER, 0x6BBF59, 0.6, 0.45, 0.3, 0.9, 0, 0.0, 20, 0, Items.WOODEN_HOE,
		"patient and caring"),
	OAK("Oak", Role.BUILDER, 0xC08A4E, 0.4, 0.40, 0.5, 0.6, 0, 0.0, 20, 0, Items.WOODEN_AXE,
		"practical and methodical"),
	FLINT("Flint", Role.MINER, 0x9AA3AD, 0.3, 0.60, 0.4, 0.5, 48, 0.0, 20, 0, Items.WOODEN_PICKAXE,
		"cautious, with a dry sense of humour"),
	SCOUT("Scout", Role.EXPLORER, 0x3FB0AC, 0.7, 0.50, 0.6, 0.6, 96, 0.10, 20, 0, Items.WOODEN_SWORD,
		"curious and adventurous"),
	SPARK("Spark", Role.INVENTOR, 0xE0533D, 0.9, 0.50, 0.4, 0.6, 0, 0.0, 20, 0, Items.WOODEN_PICKAXE,
		"clever and excitable"),
	AEGIS("Aegis", Role.WARRIOR, 0x6F95D6, 0.4, 0.25, 1.0, 0.6, 0, 0.0, 24, 2, Items.WOODEN_SWORD,
		"calm and protective"),
	SAGE("Sage", Role.STRATEGIST, 0xB39DDB, 0.6, 0.50, 0.3, 0.7, 0, 0.0, 20, 0, Items.WOODEN_SWORD,
		"thoughtful and observant"),
	TERRA("Terra", Role.LANDSCAPER, 0xC27BA0, 0.5, 0.45, 0.4, 0.7, 0, 0.0, 20, 0, Items.WOODEN_SHOVEL,
		"creative and tidy"),
	ROWAN("Rowan", Role.FORAGER, 0x8DB255, 0.5, 0.45, 0.5, 1.0, 48, 0.0, 20, 0, Items.WOODEN_AXE,
		"resourceful and generous");

	private static final FriendId[] VALUES = values();

	private final String displayName;
	private final Role role;
	private final int colour;
	private final double chattiness;
	private final double retreatFraction;
	private final double bravery;
	private final double generosity;
	private final int roam;
	private final double speedBonus;
	private final int maxHealth;
	private final int baseArmour;
	private final Item starterTool;
	private final String personality;

	FriendId(String displayName, Role role, int colour, double chattiness, double retreatFraction, double bravery,
			double generosity, int roam, double speedBonus, int maxHealth, int baseArmour, Item starterTool,
			String personality) {
		this.displayName = displayName;
		this.role = role;
		this.colour = colour;
		this.chattiness = chattiness;
		this.retreatFraction = retreatFraction;
		this.bravery = bravery;
		this.generosity = generosity;
		this.roam = roam;
		this.speedBonus = speedBonus;
		this.maxHealth = maxHealth;
		this.baseArmour = baseArmour;
		this.starterTool = starterTool;
		this.personality = personality;
	}

	public String displayName() {
		return displayName;
	}

	/** Lower-case id used for commands, textures and save data, e.g. {@code "fern"}. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Role role() {
		return role;
	}

	public int colour() {
		return colour;
	}

	/** 0–1. Higher means more frequent casual chatter. */
	public double chattiness() {
		return chattiness;
	}

	/** Retreat to safety when health falls to this fraction of max health or lower. */
	public double retreatFraction() {
		return retreatFraction;
	}

	/** 0–1. Willingness to fight back instead of fleeing. 1.0 = always stands ground (Aegis). */
	public double bravery() {
		return bravery;
	}

	/** 0–1. How readily spare items are handed to friends and players. */
	public double generosity() {
		return generosity;
	}

	/** Extra working distance beyond the camp radius (Scout explores, Flint mines, Rowan gathers). */
	public int roam() {
		return roam;
	}

	public double speedBonus() {
		return speedBonus;
	}

	public int maxHealth() {
		return maxHealth;
	}

	public int baseArmour() {
		return baseArmour;
	}

	/**
	 * Wooden tool the friend brings when recruited: every friend brings something they can fight back with (Sage, who
	 * needs no tool for her planning, a sword), so nobody can only run from a zombie.
	 */
	public Item starterTool() {
		return starterTool;
	}

	public String personality() {
		return personality;
	}

	public static FriendId byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FERN;
	}

	public static Optional<FriendId> byKey(String key) {
		String k = key.toLowerCase(Locale.ROOT);
		for (FriendId id : VALUES) {
			if (id.key().equals(k)) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}
}
