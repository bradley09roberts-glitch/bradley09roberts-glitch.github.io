package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Who a newcomer is: someone met in the world (a settler in a village, at a survivor camp, or a wanderer passing the
 * camp) rather than one of the nine named friends. A newcomer works, talks and fights like the named friend whose
 * kind of work they share (their {@code archetype}, which picks their routines, keenness and wording), but has their
 * own name, colour and look. The nine named friends have no persona: their {@link FriendId} is who they are.
 *
 * @param name      what they are called, e.g. "Mabel"
 * @param colour    their name colour, 0xRRGGBB
 * @param skin      a skin number from the skin list ({@code people.Skins}, read from {@code skins.json}): 0-8 are the
 *                  nine friends' own skins, {@value #DEFAULT_SKIN_BASE}-108 the game's default player skins with wide
 *                  arms ({@link #DEFAULT_SKINS}), 109-117 the same with slim arms, and 1000 up any skins added later
 * @param archetype the named friend whose work, personality numbers and wording they share
 */
public record Persona(String name, int colour, int skin, FriendId archetype) {
	/** Skin ids from here up are the game's own default player skins, in {@link #DEFAULT_SKINS} order. */
	public static final int DEFAULT_SKIN_BASE = 100;
	/**
	 * The game's default player skins, as named in {@code textures/entity/player/wide/} and {@code slim/}: wide-armed from
	 * {@value #DEFAULT_SKIN_BASE}, slim-armed nine numbers later.
	 */
	public static final List<String> DEFAULT_SKINS = List.of(
		"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");

	public static final Codec<Persona> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(Persona::name),
		Codec.INT.fieldOf("colour").forGetter(Persona::colour),
		Codec.INT.fieldOf("skin").forGetter(Persona::skin),
		Codec.STRING.xmap(k -> FriendId.byKey(k).orElse(FriendId.ROWAN), FriendId::key).fieldOf("archetype")
			.forGetter(Persona::archetype)
	).apply(i, Persona::new));

	/** Their kind of work: the same as their archetype's. */
	public Role role() {
		return archetype.role();
	}
}
