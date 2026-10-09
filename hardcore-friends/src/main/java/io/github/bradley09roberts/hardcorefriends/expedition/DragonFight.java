package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The dragon fight, as the whole team sees it: the dragon and the end crystals, which crystal each archer can shoot
 * from where (worked out once for everyone), who climbs to a caged crystal, when the dragon lands, and the celebration
 * when it falls. Looked at once a second while friends are in the End. Also the mod's own damage types: in 26.3 the
 * dragon only takes damage from players or from damage types in {@code minecraft:always_hurts_ender_dragons}, so the
 * friends' blows ({@value #STRIKE}) and arrows ({@value #ARROW}) use types of their own that the data pack adds to it.
 */
public final class DragonFight {
	/** The damage type of a friend's blow on the dragon. */
	public static final String STRIKE = "friend_strike";
	/** The damage type of a friend's arrow hitting the dragon. */
	public static final String ARROW = "friend_arrow";
	private static final ResourceKey<DamageType> STRIKE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, STRIKE));
	private static final ResourceKey<DamageType> ARROW_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, ARROW));

	/** A full draw, as a player's: the speed friends loose arrows at the crystals and the dragon. */
	static final float FULL_DRAW = 3.0F;
	/** Nobody may stand this close to a crystal when it is shot (it blows up). */
	static final double BLAST_CLEARANCE = 8;
	/** Crystals and the dragon are looked for this far from the middle of the island. */
	private static final double ARENA = 160;
	private static final int UNITY_FOR_VICTORY = 100;

	/** Where an archer can shoot a crystal from: a spot on the ground with a clear arc to it. */
	record ShootSpot(BlockPos feet, long foundAt) {
	}

	/** The friend climbing to a caged crystal: which crystal, the column of their pillar, and its bottom and top. */
	record Climb(UUID friend, UUID crystal, int x, int z, int baseY, int topY) {
	}

	private static @Nullable EnderDragon dragon;
	private static final List<EndCrystal> CRYSTALS = new ArrayList<>();
	private static final Map<UUID, ShootSpot> SPOTS = new HashMap<>();
	private static final Map<UUID, Long> NO_SPOT_UNTIL = new HashMap<>();
	private static @Nullable Climb climb;
	private static boolean wasSitting;

	private DragonFight() {
	}

	static void clear() {
		dragon = null;
		CRYSTALS.clear();
		SPOTS.clear();
		NO_SPOT_UNTIL.clear();
		climb = null;
		wasSitting = false;
	}

	// ------------------------------------------------------------------ team

	/** Once a second: the dragon, the crystals, a landing to call out, the climber, and one crystal's shooting spot. */
	static void tick(MinecraftServer server) {
		ServerLevel end = server.getLevel(Level.END);
		if (end == null || Companions.in(end).isEmpty()) {
			if (dragon != null || !CRYSTALS.isEmpty() || climb != null) {
				clear();
			}
			return;
		}
		List<? extends EnderDragon> dragons = end.getDragons();
		dragon = dragons.isEmpty() ? null : dragons.getFirst();
		CRYSTALS.clear();
		AABB arena = new AABB(-ARENA, end.getMinY(), -ARENA, ARENA, end.getMaxY(), ARENA);
		CRYSTALS.addAll(end.getEntitiesOfClass(EndCrystal.class, arena, EndCrystal::isAlive));
		SPOTS.keySet().removeIf(id -> CRYSTALS.stream().noneMatch(c -> c.getUUID().equals(id)));
		EnderDragon d = dragon;
		boolean sitting = d != null && d.getPhaseManager().getCurrentPhase().isSitting() && !dying(d);
		if (sitting && !wasSitting) {
			callOut(end, d, Line.DRAGON_PERCHED);
		}
		wasSitting = sitting;
		updateClimb(end);
		long now = end.getGameTime();
		for (EndCrystal crystal : CRYSTALS) {
			ShootSpot spot = SPOTS.get(crystal.getUUID());
			Long wait = NO_SPOT_UNTIL.get(crystal.getUUID());
			if ((spot == null || now - spot.foundAt() > 600) && (wait == null || now >= wait)) {
				BlockPos feet = findSpot(end, crystal);
				if (feet != null) {
					SPOTS.put(crystal.getUUID(), new ShootSpot(feet, now));
				} else {
					SPOTS.remove(crystal.getUUID());
					NO_SPOT_UNTIL.put(crystal.getUUID(), now + 200);
				}
				break; // one crystal a second is plenty
			}
		}
	}

	/** True while there is a fight on in this level: a living dragon, or crystals left. */
	static boolean active(ServerLevel level) {
		if (level.dimension() != Level.END) {
			return false;
		}
		EnderDragon d = dragon;
		return d != null && d.isAlive() && !dying(d) || !CRYSTALS.isEmpty();
	}

	static @Nullable EnderDragon dragon() {
		EnderDragon d = dragon;
		return d != null && d.isAlive() && !d.isRemoved() ? d : null;
	}

	static boolean dying(EnderDragon d) {
		return d.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
	}

	/** True while the dragon sits on the portal (not dying). */
	static boolean perched(EnderDragon d) {
		return d.getPhaseManager().getCurrentPhase().isSitting() && !dying(d);
	}

	/** The living crystals, with where they can be shot from (null spot: none found yet). */
	static List<EndCrystal> crystals() {
		CRYSTALS.removeIf(c -> !c.isAlive() || c.isRemoved());
		return List.copyOf(CRYSTALS);
	}

	static @Nullable ShootSpot spotFor(EndCrystal crystal) {
		return SPOTS.get(crystal.getUUID());
	}

	/** True when nobody (no player, no friend) stands within {@value #BLAST_CLEARANCE} blocks of the crystal. */
	static boolean clearToBlow(ServerLevel level, EndCrystal crystal) {
		double r2 = BLAST_CLEARANCE * BLAST_CLEARANCE;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(crystal) < r2) {
				return false;
			}
		}
		for (CompanionEntity c : Companions.in(level)) {
			if (c.distanceToSqr(crystal) < r2) {
				return false;
			}
		}
		return true;
	}

	/** One friend near the dragon says a line (the nearest to it, within 64 blocks). */
	private static void callOut(ServerLevel level, EnderDragon d, Line line) {
		CompanionEntity best = null;
		double bestDist = 64 * 64;
		for (CompanionEntity c : Companions.in(level)) {
			double dist = c.distanceToSqr(d);
			if (dist < bestDist) {
				bestDist = dist;
				best = c;
			}
		}
		if (best != null) {
			Speech.say(best, line);
		}
	}

	// --------------------------------------------------------------- shooting

	/**
	 * A spot on the island's ground from which a full draw clears everything on its way to the crystal (the tower's
	 * edge, a cage): rings of 10 to 36 blocks round the tower, nearest first, never over the void, never on another
	 * tower, and not far below or above the players on the ground.
	 */
	private static @Nullable BlockPos findSpot(ServerLevel level, EndCrystal crystal) {
		Vec3 target = crystal.getBoundingBox().getCenter();
		double groundY = groundLevel(level);
		for (int radius : new int[] {10, 16, 22, 28, 36}) {
			for (int i = 0; i < 12; i++) {
				double angle = i * Math.PI / 6 + radius;
				int x = (int) Math.floor(crystal.getX() + Math.cos(angle) * radius);
				int z = (int) Math.floor(crystal.getZ() + Math.sin(angle) * radius);
				if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (y <= level.getMinY() + 1 || Math.abs(y - groundY) > 8) {
					continue; // the void, or the top of a tower
				}
				BlockPos feet = new BlockPos(x, y, z);
				if (!Travel.standable(level, feet) || nearEdge(level, feet)) {
					continue;
				}
				Vec3 from = new Vec3(x + 0.5, y + 1.52, z + 0.5);
				Vec3 dir = Ballistics.aim(from, target, FULL_DRAW);
				if (dir != null && Ballistics.clearFlight(level, crystal, from, dir, FULL_DRAW, target)) {
					return feet;
				}
			}
		}
		return null;
	}

	/** The height of the ground the players are fighting on (the highest player or friend near the middle, roughly). */
	private static double groundLevel(ServerLevel level) {
		double total = 0;
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.onGround() && Math.abs(p.getX()) < ARENA && Math.abs(p.getZ()) < ARENA) {
				total += p.getY();
				n++;
			}
		}
		for (CompanionEntity c : Companions.in(level)) {
			if (c.onGround()) {
				total += c.getY();
				n++;
			}
		}
		return n == 0 ? 64 : total / n;
	}

	/**
	 * True when the void is within two blocks of {@code feet} on any side (no ground within four blocks below): a
	 * blow or a gust there could throw a friend off the island.
	 */
	static boolean nearEdge(ServerLevel level, BlockPos feet) {
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				boolean ground = false;
				for (int dy = 1; dy <= 4 && !ground; dy++) {
					BlockPos p = feet.offset(dx, -dy, dz);
					ground = level.isLoaded(p) && !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
				}
				if (!ground) {
					return true;
				}
			}
		}
		return false;
	}

	/** Makes the arrow for a shot at the dragon: one that can hurt it ({@link DragonArrow}). */
	static Archery.ArrowMaker dragonArrows() {
		return (c, level, projectile, bow, power) -> {
			if (!projectile.is(Items.ARROW) && !projectile.is(Items.TIPPED_ARROW)) {
				return null; // a spectral arrow flies as it always does
			}
			DragonArrow arrow = new DragonArrow(level, c, projectile.copyWithCount(1), bow);
			arrow.setBaseDamageFromMob(power);
			arrow.setCritArrow(power >= 1.0F);
			return arrow;
		};
	}

	// ----------------------------------------------------------------- damage

	/** The damage source of a friend's blow on the dragon, or null if the data pack's type is missing. */
	static @Nullable DamageSource strikeSource(ServerLevel level, CompanionEntity friend) {
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(STRIKE_TYPE).orElse(null);
		return type == null ? null : new DamageSource(type, friend, friend);
	}

	/** The damage source of a friend's arrow hitting the dragon, or null if the data pack's type is missing. */
	static @Nullable DamageSource arrowSource(ServerLevel level, AbstractArrow arrow, CompanionEntity friend) {
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(ARROW_TYPE).orElse(null);
		return type == null ? null : new DamageSource(type, arrow, friend);
	}

	/**
	 * A blow on the perched dragon with the best weapon carried, as the friend's own melee works it out (their attack
	 * strength with the weapon, and its enchantments), dealt with the friends' damage type. Returns true if it landed.
	 */
	static boolean strike(CompanionEntity c, ServerLevel level, EnderDragon d, EnderDragonPart part) {
		DamageSource source = strikeSource(level, c);
		if (source == null) {
			return false;
		}
		c.equipBestWeapon();
		float base = (float) c.getAttributeValue(Attributes.ATTACK_DAMAGE);
		ItemStack weapon = c.getMainHandItem();
		float amount = weapon.isEmpty() ? base : EnchantmentHelper.modifyDamage(level, weapon, d, source, base);
		c.swingArm();
		c.getLookControl().setLookAt(part.position());
		float before = d.getHealth();
		d.hurt(level, part, source, amount);
		boolean landed = d.getHealth() < before || !d.isAlive();
		if (landed) {
			c.setLastHurtMob(d);
			if (weapon.isDamageableItem() && !Unity.carefulHands(c)) {
				c.damageMainHandTool(1);
			}
			c.onHitLanded(level, d);
		}
		return landed;
	}

	// ------------------------------------------------------------------ climb

	/** Cobblestone, dirt or netherrack: what a pillar to a cage is made of. */
	static boolean isPillarBlock(BlockState state) {
		return state.is(Blocks.COBBLESTONE) || state.is(Blocks.DIRT) || state.is(Blocks.NETHERRACK);
	}

	static boolean isPillarItem(ItemStack s) {
		return s.is(Items.COBBLESTONE) || s.is(Items.DIRT) || s.is(Items.NETHERRACK);
	}

	static @Nullable Climb climb() {
		return climb;
	}

	/** True when this friend is the climber and {@code pos} is in their pillar's column. */
	static boolean inClimbColumn(CompanionEntity c, BlockPos pos) {
		Climb k = climb;
		return k != null && k.friend().equals(c.getUUID()) && pos.getX() == k.x() && pos.getZ() == k.z()
			&& pos.getY() >= k.baseY() && pos.getY() < k.topY();
	}

	/** The climb is over (done, given up or the climber gone). */
	static void endClimb(CompanionEntity c) {
		Climb k = climb;
		if (k != null && k.friend().equals(c.getUUID())) {
			climb = null;
		}
	}

	/** True when iron bars stand within reach of the crystal: a cage. */
	static boolean caged(ServerLevel level, EndCrystal crystal) {
		BlockPos at = crystal.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 2, 2))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.IRON_BARS)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Keeps the climb up to date: forgets it when the climber or the crystal is gone; otherwise, when a caged crystal
	 * nobody can shoot is left, picks a climber: a friend following a player here, healthy, with a pickaxe for the
	 * bars and blocks enough for the pillar (the one carrying most).
	 */
	private static void updateClimb(ServerLevel level) {
		Climb k = climb;
		if (k != null) {
			boolean friendThere = false;
			for (CompanionEntity c : Companions.in(level)) {
				if (c.getUUID().equals(k.friend()) && c.isAlive()) {
					friendThere = true;
					break;
				}
			}
			if (!friendThere) {
				climb = null;
			}
			return;
		}
		EnderDragon d = dragon();
		if (d == null) {
			return; // no dragon to fight: nobody climbs anything
		}
		for (EndCrystal crystal : CRYSTALS) {
			if (SPOTS.containsKey(crystal.getUUID()) || !caged(level, crystal)) {
				continue;
			}
			Climb plan = planClimb(level, crystal);
			if (plan == null) {
				continue;
			}
			int needed = plan.topY() - plan.baseY() + 2;
			CompanionEntity best = null;
			int bestBlocks = needed - 1;
			for (CompanionEntity c : Companions.in(level)) {
				ServerPlayer leader = c.leader();
				if (c.mode() != CompanionMode.FOLLOW || leader == null || leader.level() != level || c.isRetreating()
					|| c.getHealth() < c.getMaxHealth() * 0.75F || !c.actions().hasTool(ItemTags.PICKAXES)) {
					continue;
				}
				int blocks = c.backpack().count(DragonFight::isPillarItem);
				if (blocks > bestBlocks) {
					best = c;
					bestBlocks = blocks;
				}
			}
			if (best != null) {
				climb = new Climb(best.getUUID(), plan.crystal(), plan.x(), plan.z(), plan.baseY(), plan.topY());
				Speech.say(best, Line.CAGE_CLIMB);
				return;
			}
		}
	}

	/**
	 * Where to pillar up beside a caged crystal's tower: a column three blocks out from its middle on one of its four
	 * sides, with solid ground under it (not the void) and nothing in the way up to the cage's lowest ring.
	 */
	private static @Nullable Climb planClimb(ServerLevel level, EndCrystal crystal) {
		BlockPos at = crystal.blockPosition();
		int topY = at.getY() - 1; // the cage's bottom ring is level with the bedrock under the crystal
		int[][] sides = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
		Climb best = null;
		for (int[] side : sides) {
			int x = at.getX() + side[0];
			int z = at.getZ() + side[1];
			int ground = Integer.MIN_VALUE;
			boolean clear = true;
			for (int y = topY + 1; y > topY - 48 && y > level.getMinY(); y--) {
				BlockPos p = new BlockPos(x, y, z);
				if (!level.isLoaded(p)) {
					clear = false;
					break;
				}
				if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
					ground = y;
					break;
				}
			}
			if (!clear || ground == Integer.MIN_VALUE || !level.getFluidState(new BlockPos(x, ground + 1, z)).isEmpty()) {
				continue;
			}
			Climb option = new Climb(new UUID(0, 0), crystal.getUUID(), x, z, ground + 1, topY);
			if (best == null || option.baseY() > best.baseY()) {
				best = option; // the side with the highest ground needs the fewest blocks
			}
		}
		return best;
	}

	/** The crystal a climb is for, if it is still there. */
	static @Nullable EndCrystal crystal(UUID id) {
		for (EndCrystal crystal : CRYSTALS) {
			if (crystal.getUUID().equals(id) && crystal.isAlive()) {
				return crystal;
			}
		}
		return null;
	}

	// -------------------------------------------------------------- victory

	/**
	 * The dragon is dead: every friend in the End cheers, the team's bond grows, everyone on the server hears who
	 * fought, and Sage's plan reaches its last step.
	 */
	static void defeated(ServerLevel level, EnderDragon d) {
		MinecraftServer server = level.getServer();
		List<CompanionEntity> there = Companions.in(level);
		ExpeditionData.get(server).addDragonDefeated();
		Camp.data(server).addStat("dragons_defeated", 1);
		clear();
		if (!there.isEmpty()) {
			List<String> names = new ArrayList<>();
			for (CompanionEntity c : there) {
				names.add(c.displayName());
				Speech.say(c, Line.VICTORY);
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.getX(), c.getY() + 1.0, c.getZ(), 40, 0.6, 1.0, 0.6, 0.4);
				level.playSound(null, c.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			Unity.add(level, "expedition", UNITY_FOR_VICTORY, 0);
			Speech.announce(server, Component.literal("The ender dragon is defeated! " + join(names)
				+ (names.size() == 1 ? " was" : " were") + " there to see it fall.").withStyle(ChatFormatting.GOLD));
		}
		if (ProgressPlan.enabled()) {
			ProgressPlan.complete(server, Milestone.STRONGHOLD);
			ProgressPlan.complete(server, Milestone.END_PORTAL);
			ProgressPlan.complete(server, Milestone.DRAGON);
		}
	}

	private static String join(List<String> names) {
		if (names.size() == 1) {
			return names.getFirst();
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
	}

	/** True when the entity is part of the dragon or the dragon itself. */
	static boolean isDragon(Entity e) {
		return e instanceof EnderDragon || e instanceof EnderDragonPart;
	}
}
