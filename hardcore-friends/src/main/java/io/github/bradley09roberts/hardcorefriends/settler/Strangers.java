package io.github.bradley09roberts.hardcorefriends.settler;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;

/**
 * Making strangers and remembering things about them. A stranger is a newcomer ({@link CompanionEntity} with a
 * {@link Persona}) in {@link CompanionMode#STRANGER}: they have a home spot they keep near, a story, and later a request.
 * What the settler package remembers about one is kept in their {@link CompanionEntity#extra()} under {@value #KEY}.
 *
 * <p>Strangers are made in two places: out of a survivor camp as the world is generated (off the server thread, so
 * nothing there may touch the server's data), and on the server thread in villages and for travellers. Both go through
 * {@link #setUp}; the server-thread side then checks the name is free on the stranger's first tick
 * ({@link SettlerEvents}).
 */
public final class Strangers {
	/** The key of the settler package's state in a friend's {@link CompanionEntity#extra()}. */
	public static final String KEY = "settler";
	static final String ORIGIN = "origin";
	static final String STORY = "story";
	static final String REQUEST = "request";
	static final String REGISTERED = "registered";
	static final String NIGHT_SPOT = "night";
	static final String LEAVE_AT = "leaveAt";
	static final String LEAVING = "leaving";
	static final String ARRIVED = "arrived";
	static final String LAST_TALK = "lastTalk";
	static final String RECRUITED_BY = "recruitedBy";
	static final String RECRUITED_BY_NAME = "recruitedByName";
	static final String TO_CAMP = "toCamp";

	private Strangers() {
	}

	/** This friend's settler state, created empty (and kept) if there is none yet. */
	public static CompoundTag state(CompanionEntity c) {
		CompoundTag extra = c.extra();
		var existing = extra.getCompound(KEY);
		if (existing.isPresent()) {
			return existing.get();
		}
		CompoundTag tag = new CompoundTag();
		extra.put(KEY, tag);
		return tag;
	}

	/**
	 * Makes a freshly created friend into a stranger living at {@code home}: their persona, their wooden tool (so they
	 * can stand up for themselves), full health, where they were met, their story and, if they have one, where they
	 * spend the night. Safe to call off the server thread on an entity not yet in the world.
	 */
	public static void setUp(CompanionEntity c, Persona persona, BlockPos home, Personas.Origin origin, String story,
			@Nullable BlockPos nightSpot) {
		c.setPersona(persona);
		c.setMode(CompanionMode.STRANGER, null);
		c.setHomePos(home);
		c.setHealth(c.getMaxHealth());
		c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(persona.archetype().starterTool()));
		CompoundTag tag = state(c);
		tag.putString(ORIGIN, origin.key());
		tag.putString(STORY, story);
		tag.putInt(REQUEST, -1);
		if (nightSpot != null) {
			tag.putLong(NIGHT_SPOT, nightSpot.asLong());
		}
	}

	/**
	 * Adds a new stranger to a loaded part of the world on the server thread, with a name nobody living has. Returns
	 * null if the entity could not be made.
	 */
	public static @Nullable CompanionEntity spawn(ServerLevel level, BlockPos spot, Personas.Origin origin, RandomSource random,
			@Nullable BlockPos nightSpot) {
		SettlerData data = SettlerData.get(level.getServer());
		CompanionEntity c = ModEntities.COMPANION.create(level, EntitySpawnReason.EVENT);
		if (c == null) {
			return null;
		}
		Persona persona = Personas.create(random, data.namesInUse(null));
		setUp(c, persona, spot, origin, Personas.story(random, origin), nightSpot);
		c.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		if (!level.addFreshEntity(c)) {
			return null;
		}
		state(c).putBoolean(REGISTERED, true);
		data.rememberStranger(c.getUUID(), persona.name());
		Companions.track(c);
		return c;
	}

	/** "Mabel the farmer", for messages about a stranger. */
	public static String nameAndTrade(CompanionEntity c) {
		return c.displayName() + " the " + c.friendId().role().title().toLowerCase(java.util.Locale.ROOT);
	}

	/** A quiet grey note to one player. */
	static void note(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	// ------------------------------------------------------------ safe spots

	/**
	 * A spot near {@code around} where someone can stand safely: firm ground, two blocks of open air, no water, lava or
	 * fire. Looks at most {@code radius} blocks out and {@code dy} up or down, nearest first, in loaded chunks only;
	 * null if there is none. Searching up and down from a known ground level (a village's bell) keeps strangers off
	 * rooftops.
	 */
	public static @Nullable BlockPos standingSpot(ServerLevel level, BlockPos around, int minRadius, int radius, int dy) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int r = minRadius; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue; // only the ring at this distance: nearest rings first
					}
					int x = around.getX() + dx;
					int z = around.getZ() + dz;
					if (!level.hasChunk(x >> 4, z >> 4)) {
						continue;
					}
					for (int y = around.getY() + dy; y >= around.getY() - dy; y--) {
						p.set(x, y, z);
						if (canStand(level, p)) {
							return p.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * A safe spot on the open ground near {@code around}, using the surface height of each column (for the wilds round
	 * the camp, where there are no roofs to land on). Loaded chunks only; null if there is none.
	 */
	public static @Nullable BlockPos surfaceSpot(ServerLevel level, BlockPos around, int radius) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = around.getX() + dx;
					int z = around.getZ() + dz;
					if (!level.hasChunk(x >> 4, z >> 4)) {
						continue;
					}
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					p.set(x, y, z);
					if (y > level.getMinY() + 1 && canStand(level, p)) {
						return p.immutable();
					}
				}
			}
		}
		return null;
	}

	/** Firm ground below, room for a person, and nothing that hurts. Never loads a chunk. */
	public static boolean canStand(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet) || !level.isLoaded(feet.above())) {
			return false;
		}
		BlockPos below = feet.below();
		BlockState ground = level.getBlockState(below);
		if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.CAMPFIRES)
			|| ground.is(Blocks.MAGMA_BLOCK) || !level.getFluidState(below).isEmpty()) {
			return false;
		}
		for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
			BlockState s = level.getBlockState(p);
			if (!s.getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty() || s.is(BlockTags.FIRE)
				|| s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.COBWEB) || s.is(Blocks.CACTUS)) {
				return false;
			}
		}
		return true;
	}
}
