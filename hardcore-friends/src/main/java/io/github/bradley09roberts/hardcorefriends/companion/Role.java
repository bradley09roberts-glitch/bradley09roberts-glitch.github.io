package io.github.bradley09roberts.hardcorefriends.companion;

/** The job each friend is known for. Drives task selection and keep-lists. */
public enum Role {
	FARMER("Farmer"),
	BUILDER("Builder"),
	MINER("Miner"),
	EXPLORER("Explorer"),
	INVENTOR("Redstone inventor"),
	WARRIOR("Warrior"),
	STRATEGIST("Strategist"),
	LANDSCAPER("Landscaper"),
	FORAGER("Forager");

	private final String title;

	Role(String title) {
		this.title = title;
	}

	public String title() {
		return title;
	}
}
