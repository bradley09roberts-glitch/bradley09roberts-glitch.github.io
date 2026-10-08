package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.goal.AvoidDangerGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.CompanionMeleeGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.DefendFriendsTargetGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.FollowLeaderGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.MutualDefenceTargetGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.RetreatGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.StayGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.goal.WorkGoal;
import io.github.bradley09roberts.hardcorefriends.ai.role.RolePassives;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.item.BackpackItem;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.town.TownPermissions;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * A human companion. One entity type serves all nine friends and every newcomer met in the world: the synced
 * {@link FriendId} selects the routines, personality and wording, and for a newcomer a {@link Persona} adds their own
 * name, colour and skin. Friends are persistent, mortal and carry their belongings in a {@link Backpack}. Features
 * plug in through {@link CompanionEvents} and keep per-friend state in {@link #extra()}.
 */
public class CompanionEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_FRIEND = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
	/** The skin to draw: -1 for the friend's own, otherwise a {@link Persona} skin id. */
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
	/** A friend whose hunger is below this eats food a player hands them, even at full health. */
	public static final double EATS_HANDED_FOOD_BELOW = 60;
	/** How far a friend goes to meet a hand-to-hand threat, away from camp or without a weapon. */
	public static final double NEAR_REACH = 8;
	/**
	 * How far an armed friend (or the one on watch) goes to meet a hand-to-hand threat when both are inside the camp:
	 * across the camp, so friends stand together there instead of one being picked off alone.
	 */
	public static final double CAMP_REACH = 24;
	/** How close an archer must be before a friend who is not a fighter stands up to it. */
	public static final double ARCHER_REACH = 3;
	/** A target neither hit nor hit back for this long, while out of reach, is given up (it cannot be got at). */
	private static final int GIVE_UP_TICKS = 200;
	/** How long a given-up target is left alone. */
	private static final int GIVEN_UP_FOR = 600;
	/** A target given up a second time (within a day) is left alone this long by day; at night, until dawn. */
	private static final int GIVEN_UP_AGAIN_FOR = 6000;

	private final Backpack backpack = new Backpack();
	private final Actions actions = new Actions(this);
	private @Nullable TaskScheduler scheduler;
	private final Needs needs = new Needs();
	private boolean asleep;
	private @Nullable LivingEntity fleeingFrom;
	private @Nullable FriendId schedulerFor;
	private @Nullable BlockPos homePos;
	private @Nullable BlockPos stayPos;
	private @Nullable UUID leaderId;
	private boolean retreating;
	private long lastEditTick = -100;
	private long lastCasualSpeech = -100_000;
	private final Map<Line, Long> speechMemory = new EnumMap<>(Line.class);
	private final Set<BlockPos> approvedLogs = new HashSet<>();
	private long approvedLogsUntil;
	private boolean deathHandled;
	private int lastDamagedTick = -1000;
	/** When sleep was last settled (game time), and the overworld clock then; see {@link #settleSleep}. */
	private long sleepSettledAt = Long.MIN_VALUE;
	private long sleepSettledClock;
	/** When the current target was chosen (tick count), and hostiles given up on until a tick count. */
	private int targetSetTick;
	private final Map<UUID, Integer> givenUp = new HashMap<>();
	/** When each hostile was last given up on (tick count), so a second give-up lasts longer. */
	private final Map<UUID, Integer> gaveUpAt = new HashMap<>();
	/** When this friend last pressed their target without a blow landing (an arrow loosed), see {@link #markEngaged}. */
	private int engagedTick = -1000;
	/** Set for a newcomer met in the world; null for the nine named friends. */
	private @Nullable Persona persona;
	/** Per-friend state kept by the feature packages, each under its own key; saved with the friend. */
	private CompoundTag extra = new CompoundTag();

	public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(true);
		this.setPathfindingMalus(PathType.WATER, 4.0F);
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 16.0F);
		this.setPathfindingMalus(PathType.FIRE, -1.0F);
		this.setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 16.0F);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F); // gear goes into the dropped backpack instead
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ARMOR, 0.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// Badly hurt, falling back outranks everything, even dodging a threat (goals never interrupt an equal one).
		this.goalSelector.addGoal(0, new RetreatGoal(this));
		this.goalSelector.addGoal(1, new AvoidDangerGoal(this, true));
		this.goalSelector.addGoal(3, new CompanionMeleeGoal(this));
		this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
		this.goalSelector.addGoal(4, new FollowLeaderGoal(this));
		this.goalSelector.addGoal(4, new StayGoal(this));
		this.goalSelector.addGoal(5, new WorkGoal(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Player.class, CompanionEntity.class));
		this.targetSelector.addGoal(2, new DefendFriendsTargetGoal(this));
		this.targetSelector.addGoal(3, new MutualDefenceTargetGoal(this));
		for (CompanionEvents.Goals hook : CompanionEvents.GOALS) {
			hook.addGoals(this, this.goalSelector, this.targetSelector);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FRIEND, 0);
		builder.define(DATA_MODE, 0);
		builder.define(DATA_SKIN, -1);
	}

	// --------------------------------------------------------------- identity

	/**
	 * Which of the nine friends this is, or for a newcomer the named friend whose work, personality and wording they
	 * share (see {@link Persona#archetype()}). For their own name use {@link #displayName()}.
	 */
	public FriendId friendId() {
		return FriendId.byOrdinal(this.entityData.get(DATA_FRIEND));
	}

	/** Skin id for the renderer: 0-8 the nine friends' own, {@link Persona#DEFAULT_SKIN_BASE} and up a default skin. */
	public int getSkinId() {
		int skin = this.entityData.get(DATA_SKIN);
		return skin >= 0 ? skin : this.entityData.get(DATA_FRIEND);
	}

	/** Their own name: "Fern", or a newcomer's, such as "Mabel". */
	public String displayName() {
		return persona != null ? persona.name() : friendId().displayName();
	}

	/** Their name colour, 0xRRGGBB. */
	public int nameColour() {
		return persona != null ? persona.colour() : friendId().colour();
	}

	/** A newcomer met in the world, rather than one of the nine named friends. */
	public boolean isSettler() {
		return persona != null;
	}

	public @Nullable Persona persona() {
		return persona;
	}

	/**
	 * Makes this friend a newcomer with their own name, colour and skin, working like their archetype. Call once right
	 * after creating (instead of {@link #setFriendId}).
	 */
	public void setPersona(Persona persona) {
		this.persona = persona;
		this.entityData.set(DATA_FRIEND, persona.archetype().ordinal());
		this.entityData.set(DATA_SKIN, persona.skin());
		this.setCustomName(Component.literal(persona.name()).withStyle(s -> s.withColor(persona.colour())));
		this.setCustomNameVisible(true);
		applyStats(persona.archetype());
	}

	/**
	 * A small stable number per friend for spreading them out (bed spots, watch posts): the nine friends' own order,
	 * then newcomers after them.
	 */
	public int rosterIndex() {
		return persona == null ? friendId().ordinal() : FriendId.values().length + (getUUID().hashCode() & 0x3F);
	}

	/** On the team: anyone but a stranger who has not been recruited yet. */
	public boolean isTeamMember() {
		return mode() != CompanionMode.STRANGER;
	}

	/** Per-friend state kept by the feature packages, each under its own key. Saved and loaded with the friend. */
	public CompoundTag extra() {
		return extra;
	}

	/** Sets who this friend is, including name, stats and starter details. Call once right after creating. */
	public void setFriendId(FriendId id) {
		this.entityData.set(DATA_FRIEND, id.ordinal());
		this.setCustomName(Component.literal(id.displayName()).withStyle(s -> s.withColor(id.colour())));
		this.setCustomNameVisible(true);
		applyStats(id);
	}

	private void applyStats(FriendId id) {
		var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(id.maxHealth());
		}
		var armour = this.getAttribute(Attributes.ARMOR);
		if (armour != null) {
			armour.setBaseValue(id.baseArmour());
		}
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(0.3 * (1.0 + id.speedBonus()));
		}
	}

	public CompanionMode mode() {
		return CompanionMode.byOrdinal(this.entityData.get(DATA_MODE));
	}

	/** Changes standing orders. {@code leader} is the player to follow in FOLLOW mode. */
	public void setMode(CompanionMode mode, @Nullable ServerPlayer leader) {
		this.entityData.set(DATA_MODE, mode.ordinal());
		this.leaderId = leader != null ? leader.getUUID() : null;
		this.stayPos = mode == CompanionMode.STAY ? this.blockPosition() : null;
		if (scheduler != null) {
			scheduler.interrupt();
		}
		this.getNavigation().stop();
	}

	public @Nullable ServerPlayer leader() {
		if (leaderId == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(leaderId);
	}

	public @Nullable BlockPos stayPos() {
		return stayPos;
	}

	public Backpack backpack() {
		return backpack;
	}

	public Actions actions() {
		return actions;
	}

	/** Hunger, energy, social, fun and comfort, and the mood they make. */
	public Needs needs() {
		return needs;
	}

	/** Set by the sleep job while this friend is sleeping (energy then refills instead of draining). */
	public void setAsleep(boolean asleep) {
		this.asleep = asleep;
	}

	public boolean isAsleep() {
		return asleep;
	}

	/**
	 * Brings sleep up to date, at most once a tick (the friend's own tick and the sleep job both call it). A sleeping
	 * friend regains energy for each tick asleep, and when the players sleep through the night the overworld clock
	 * jumps ahead: the skipped time counts as sleep for every friend at work, whether already lying down or still on
	 * the way to bed, so they wake as rested as after a whole night. Time spent unloaded counts for nothing, as for
	 * every need.
	 */
	public void settleSleep() {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (now == sleepSettledAt) {
			return;
		}
		long clock = level.getOverworldClockTime();
		boolean first = sleepSettledAt == Long.MIN_VALUE;
		// How much further the clock moved than the world ticked: a night slept through, never a time going backwards.
		long skipped = first ? 0 : Math.clamp(clock - sleepSettledClock - (now - sleepSettledAt), 0L, 24000L);
		sleepSettledAt = now;
		sleepSettledClock = clock;
		if (asleep) {
			needs.rest(this, (first ? 0 : 1) + skipped);
		} else if (skipped > 0 && mode() == CompanionMode.WORK) {
			needs.rest(this, skipped);
		}
	}

	/** Badly hurt: at or below the health at which this friend falls back to recover (their retreat fraction). */
	public boolean badlyHurt() {
		return getHealth() <= getMaxHealth() * friendId().retreatFraction();
	}

	/**
	 * Too weak to work: badly hurt and too hungry to heal, as a starving friend soon is. They eat the moment there is
	 * food, and until then rest at camp and take on no work that could lead them into danger (see
	 * {@link TaskScheduler}).
	 */
	public boolean tooWeakToWork() {
		return badlyHurt() && !needs.canHeal();
	}

	/** Set by AvoidDangerGoal while this friend is getting away from something (shown in their status). */
	public void setFleeingFrom(@Nullable LivingEntity danger) {
		this.fleeingFrom = danger;
	}

	/**
	 * How fast this friend works at a kind of job: their skill at it (specialists are quicker, and practice helps)
	 * times their mood, times the team's work rhythm.
	 */
	public double workSpeed(WorldEditGuard.@Nullable Reason reason) {
		Role work = reason == null ? null : Speciality.roleFor(reason);
		return Speciality.skill(this, work) * needs.workSpeed() * Unity.workSpeed(this);
	}

	/** The job scheduler, built for this friend's role on first use. */
	public TaskScheduler scheduler() {
		FriendId id = friendId();
		if (scheduler == null || schedulerFor != id) {
			scheduler = new TaskScheduler(this, TaskRegistry.create(id));
			schedulerFor = id;
		}
		return scheduler;
	}

	public String activity() {
		if (retreating) {
			return "falling back to recover";
		}
		LivingEntity dodging = fleeingFrom;
		if (dodging != null && dodging.isAlive()) {
			return "getting away from " + dodging.getName().getString();
		}
		if (getTarget() != null) {
			return "fighting " + getTarget().getName().getString();
		}
		return switch (mode()) {
			case FOLLOW -> "following " + (leader() != null ? leader().getName().getString() : "you");
			case STAY -> "holding position";
			case WORK -> scheduler().activity();
			case STRANGER -> "minding their own business";
		};
	}

	/**
	 * The camp centre if the camp is in this dimension, otherwise where this friend was recruited. A stranger (not on
	 * the team yet) lives somewhere else entirely, so theirs is always their own spot.
	 */
	public BlockPos homePos() {
		if (mode() == CompanionMode.STRANGER && homePos != null) {
			return homePos;
		}
		if (this.level() instanceof ServerLevel level) {
			var camp = Camp.center(level);
			if (camp.isPresent()) {
				return camp.get();
			}
		}
		return homePos != null ? homePos : this.blockPosition();
	}

	public void setHomePos(BlockPos pos) {
		this.homePos = pos.immutable();
	}

	public boolean isRetreating() {
		return retreating;
	}

	public void setRetreating(boolean retreating) {
		this.retreating = retreating;
	}

	public boolean isFighter() {
		return friendId().role() == Role.WARRIOR;
	}

	public long lastEditTick() {
		return lastEditTick;
	}

	public void setLastEditTick(long tick) {
		this.lastEditTick = tick;
	}

	public long lastCasualSpeech() {
		return lastCasualSpeech;
	}

	public void setLastCasualSpeech(long tick) {
		this.lastCasualSpeech = tick;
	}

	public Map<Line, Long> speechMemory() {
		return speechMemory;
	}

	/** Marks a tree's logs as checked and approved for felling for the next few minutes. */
	public void approveLogs(Collection<BlockPos> logs) {
		approvedLogs.clear();
		for (BlockPos p : logs) {
			approvedLogs.add(p.immutable());
		}
		approvedLogsUntil = this.level().getGameTime() + 20 * 180;
	}

	public boolean isApprovedLog(BlockPos pos) {
		return this.level().getGameTime() < approvedLogsUntil && approvedLogs.contains(pos);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		// A non-fighter drops a target beyond their reach (8 blocks, or across the camp for an armed friend in it),
		// where canStandAndFight ends: AvoidDangerGoal then moves them out of a shooter's line of fire, or they go back
		// to work, instead of standing still.
		if (target != null && (!target.isAlive() || target.isRemoved() || target.distanceToSqr(this) > 32 * 32
			|| target instanceof Player || target instanceof CompanionEntity
			|| (!Threats.isThreat(target) && getLastHurtByMob() != target)
			|| (!isFighter() && mode() != CompanionMode.FOLLOW && outOfReach(target)))) {
			setTarget(null);
		} else if (target != null && mode() == CompanionMode.WORK && cannotGetAt(target)) {
			// Stuck behind a wall or a drop: give it up for a while, instead of standing still with it all night.
			givenUp.put(target.getUUID(), this.tickCount + giveUpTicks(level, target));
			setTarget(null);
		}
		if (this.tickCount % 100 == 0) {
			CampData data = Camp.data(level.getServer());
			CampData.Ledger ledger = data.ledger(friendId());
			if (ledger.state == CampData.LifeState.ALIVE && getUUID().equals(ledger.entityId)) {
				ledger.lastKnownPos = blockPosition();
				ledger.lastKnownDimension = Camp.dimensionId(level);
				data.touchLedger();
			}
			int slots = Unity.backpackSlots(level.getServer());
			if (backpack.capacity() != slots) {
				backpack.setCapacity(slots);
			}
		}
		// A stranger (not recruited yet) lives off their own home: their needs only start once they join the team.
		if (this.tickCount % 20 == 0 && isTeamMember()) {
			CompanionTask job = mode() == CompanionMode.WORK ? scheduler().current() : null;
			boolean working = mode() == CompanionMode.FOLLOW || job != null && !job.id().startsWith("needs.") && !job.id().equals("common.idle");
			needs.tickSecond(this, working, asleep);
		}
		settleSleep();
		// Following a player or holding a spot, a friend runs no jobs, so they snack from their backpack when hungry.
		if (this.tickCount % 100 == 0 && isTeamMember() && mode() != CompanionMode.WORK && needs.get(Needs.Need.HUNGER) < 25) {
			ItemStack snack = backpack.take(CompanionEntity::isEdible, 1);
			if (!snack.isEmpty()) {
				String name = snack.getHoverName().getString();
				eat(snack);
				Speech.say(this, Line.ATE, name);
			}
		}
		// Starving hurts, as on Normal difficulty: down to one heart, never to death on its own.
		if (this.tickCount % 80 == 0 && needs.get(Needs.Need.HUNGER) <= 0 && getHealth() > 2.0F) {
			hurtServer(level, level.damageSources().starve(), 1.0F);
		}
		// Out of combat and not starving, friends recover like a well-fed player (1 health every 4 seconds).
		if (this.tickCount % 80 == 0 && getHealth() < getMaxHealth() && this.tickCount - lastDamagedTick > 200
			&& getTarget() == null && !isOnFire() && needs.canHeal()) {
			heal(1.0F);
		}
		if (isTeamMember()) {
			RolePassives.tick(this);
			MoodPassives.tick(this);
		}
		for (CompanionEvents.Tick hook : CompanionEvents.TICK) {
			hook.tick(this, level);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	// ------------------------------------------------------------ interaction

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!(this.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		for (CompanionEvents.Interact hook : CompanionEvents.INTERACT) {
			InteractionResult result = hook.interact(this, serverPlayer, hand);
			if (result != InteractionResult.PASS) {
				return result;
			}
		}
		if (!isTeamMember()) {
			// A stranger is not one of yours: no backpack to open and no gifts taken (their own handling comes first).
			serverPlayer.sendSystemMessage(statusLine());
			return InteractionResult.SUCCESS_SERVER;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			if (player.isShiftKeyDown()) {
				openBackpack(serverPlayer);
			} else {
				Speech.say(this, Line.GREETING, activity(), player.getName().getString());
				serverPlayer.sendSystemMessage(statusLine());
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		FoodProperties food = held.get(DataComponents.FOOD);
		// A hurt or hungry friend eats food handed to them at once; anyone else puts it in their backpack for later.
		if (food != null && (getHealth() < getMaxHealth() || needs.get(Needs.Need.HUNGER) < EATS_HANDED_FOOD_BELOW)) {
			ItemStack meal = held.copyWithCount(1);
			for (CompanionEvents.Gift hook : CompanionEvents.GIFT) {
				hook.gift(this, serverPlayer, meal, true);
			}
			heal(Math.max(1, food.nutrition()));
			needs.add(Needs.Need.HUNGER, hungerValue(held));
			UseRemainder remainder = held.get(DataComponents.USE_REMAINDER);
			held.consume(1, player);
			if (remainder != null && !player.hasInfiniteMaterials()) {
				// Stews leave a bowl and honey leaves a bottle, just as when the player eats them.
				ItemStack leftover = remainder.convertInto().create();
				if (held.isEmpty()) {
					player.setItemInHand(hand, leftover);
				} else if (!player.getInventory().add(leftover)) {
					player.spawnAtLocation(level, leftover);
				}
			}
			level.playSound(null, blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
			Speech.say(this, Line.THANKS_FOOD);
			Unity.add(level, Unity.GIFT, 2, 20);
			return InteractionResult.SUCCESS_SERVER;
		}
		ItemStack gift = held.copyWithCount(held.getCount());
		ItemStack left = backpack.insert(gift);
		int given = held.getCount() - left.getCount();
		if (given > 0) {
			held.shrink(given);
			Speech.say(this, Line.THANKS_GIFT, gift.getHoverName().getString());
			Unity.add(level, Unity.GIFT, 2, 20);
			for (CompanionEvents.Gift hook : CompanionEvents.GIFT) {
				hook.gift(this, serverPlayer, gift.copyWithCount(given), false);
			}
		} else {
			serverPlayer.sendSystemMessage(Component.literal(displayName() + "'s backpack is full.")
				.withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Leading a friend about on a lead is an order like any other: on a shared world, only the camp's owner and the
	 * players they trust may tie one on (see the town package).
	 */
	@Override
	public boolean canHaveALeashAttachedTo(Entity holder) {
		if (holder instanceof ServerPlayer player && !TownPermissions.isAllowed(player)) {
			return false;
		}
		return super.canHaveALeashAttachedTo(holder);
	}

	/** Opens the backpack as a chest screen for a nearby player. */
	public void openBackpack(ServerPlayer player) {
		int rows = backpack.visibleRows();
		MenuType<ChestMenu> type = rows <= 1 ? MenuType.GENERIC_9x1 : rows == 2 ? MenuType.GENERIC_9x2 : MenuType.GENERIC_9x3;
		BackpackView view = new BackpackView(this);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(type, id, inventory, view, rows),
			Component.literal(displayName() + "'s Backpack")));
	}

	/** "Fern (Farmer) - harvesting crops - health 20/20 - mood good - backpack 3/9", shown on right-click. */
	public Component statusLine() {
		String health = String.format(Locale.ROOT, "%.0f/%.0f", getHealth(), getMaxHealth());
		return Component.literal(displayName() + " (" + friendId().role().title() + ") - " + activity()
			+ " - health " + health + " - mood " + MoodPassives.moodText(this) + " - backpack " + backpack.usedSlots()
			+ "/" + backpack.capacity())
			.withStyle(ChatFormatting.GRAY);
	}

	// ------------------------------------------------------------------ combat

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		// Friends never hurt each other, and a player only hurts a friend on purpose (while sneaking).
		if (attacker instanceof CompanionEntity) {
			return false;
		}
		if (attacker instanceof Player player && !player.isShiftKeyDown()) {
			return false;
		}
		for (CompanionEvents.Hurt hook : CompanionEvents.HURT) {
			damage = hook.hurt(this, level, source, damage);
			if (damage <= 0) {
				return false;
			}
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			lastDamagedTick = this.tickCount;
		}
		return hurt;
	}

	/** Ticks since this friend last took damage. */
	public int ticksSinceDamaged() {
		return this.tickCount - lastDamagedTick;
	}

	/** True if a sword, axe, pickaxe, shovel or hoe is carried: anything that hits harder than a fist. */
	public boolean hasMeleeTool() {
		return actions.has(s -> s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES)
			|| s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES));
	}

	/**
	 * Whether this friend should stand and fight a threat rather than run. Aegis fights anything but a hissing
	 * creeper. Everyone else fights hand-to-hand threats within their reach ({@link #meleeReach}) while healthy and
	 * holding a tool, so friends defend each other instead of being picked off one by one, and still run from
	 * creepers and when hurt. A friend with a bow and arrows also stands up to what they would shoot, from a safe
	 * distance ({@link Archery#standsWithBow}).
	 */
	public boolean canStandAndFight(LivingEntity threat) {
		if (isRetreating() || !threat.isAlive()) {
			return false;
		}
		if (threat instanceof net.minecraft.world.entity.monster.Creeper creeper) {
			return Archery.standsWithBow(this, creeper)
				|| isFighter() && creeper.getSwellDir() <= 0 && !creeper.isIgnited() && getHealth() > getMaxHealth() * 0.6F;
		}
		if (isFighter()) {
			return true;
		}
		if (Archery.standsWithBow(this, threat)) {
			return true; // a bow and arrows, and the threat at a good range for them
		}
		double reach = meleeReach(threat);
		return hasMeleeTool() && isHealthy() && distanceToSqr(threat) <= reach * reach;
	}

	/** Healthy enough to stand and fight: above half health, and well above the point where they fall back. */
	public boolean isHealthy() {
		return getHealth() > getMaxHealth() * Math.max(0.5, friendId().retreatFraction() + 0.1);
	}

	/**
	 * How far this friend goes to meet a threat. Chasing an archer with a hoe only gets a non-fighter shot, so they
	 * stand up to one only in arm's reach ({@value #ARCHER_REACH}). Against a hand-to-hand threat, an armed friend at
	 * work in the camp, or whoever is on watch, crosses the camp to stand with the others ({@value #CAMP_REACH}) when
	 * the threat is inside the camp too; otherwise they meet it within {@value #NEAR_REACH} blocks.
	 */
	public double meleeReach(LivingEntity threat) {
		if (Threats.isRanged(threat)) {
			return ARCHER_REACH;
		}
		if (mode() == CompanionMode.WORK && (isArmed() || NightWatch.isOnWatch(this)) && NightWatch.insideCamp(this)
			&& NightWatch.insideCamp(threat)) {
			return CAMP_REACH;
		}
		return NEAR_REACH;
	}

	private boolean outOfReach(LivingEntity target) {
		double reach = Math.max(NEAR_REACH, Math.max(meleeReach(target), Archery.reach(this, target)));
		return target.distanceToSqr(this) > reach * reach;
	}

	/**
	 * True when a target has been chased this long without a blow either way (or an arrow loosed at it), is still out
	 * of arm's reach, and no whole path leads to it: a long way round is followed to the end, a mob behind a fence or
	 * on a ledge is not.
	 */
	private boolean cannotGetAt(LivingEntity target) {
		int engaged = Math.max(Math.max(targetSetTick, engagedTick), Math.max(getLastHurtMobTimestamp(), lastDamagedTick));
		if (this.tickCount - engaged <= GIVE_UP_TICKS || target.distanceToSqr(this) <= 3 * 3) {
			return false;
		}
		net.minecraft.world.level.pathfinder.Path path = getNavigation().getPath();
		return path == null || path.isDone() || !path.canReach();
	}

	/**
	 * How long to leave a target alone that could not be got at: {@value #GIVEN_UP_FOR} ticks the first time, and when
	 * the same one is given up again within a day, until dawn at night (so a mob nobody can reach does not wake the
	 * camp all night) or {@value #GIVEN_UP_AGAIN_FOR} ticks by day.
	 */
	private int giveUpTicks(ServerLevel level, LivingEntity target) {
		gaveUpAt.values().removeIf(at -> this.tickCount - at > 24000);
		Integer before = gaveUpAt.put(target.getUUID(), this.tickCount);
		if (before == null) {
			return GIVEN_UP_FOR;
		}
		return Camp.isNightTime(level) ? (int) Math.max(GIVEN_UP_FOR, 24000 - Camp.timeOfDay(level)) : GIVEN_UP_AGAIN_FOR;
	}

	/**
	 * True while this friend has given up on a hostile they could not get at (see {@link #cannotGetAt}). One that comes
	 * within arm's reach, or is landing blows on someone, can be got at after all and is forgiven at once.
	 */
	public boolean hasGivenUpOn(LivingEntity threat) {
		Integer until = givenUp.get(threat.getUUID());
		if (until == null || this.tickCount >= until) {
			return false;
		}
		if (threat.distanceToSqr(this) <= 3 * 3
			|| threat.getLastHurtMob() != null && threat.tickCount - threat.getLastHurtMobTimestamp() < 60) {
			givenUp.remove(threat.getUUID());
			return false;
		}
		return true;
	}

	/**
	 * Counts as pressing the current target for the give-up timer without a blow landing: an archer calls it for every
	 * arrow loosed, so a friend shooting at a mob they cannot walk to does not give it up.
	 */
	public void markEngaged() {
		this.engagedTick = this.tickCount;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// The map is null while the entity is still being constructed (a mob may clear its target from there).
		if (target != getTarget() && givenUp != null) {
			targetSetTick = this.tickCount;
			givenUp.values().removeIf(until -> until <= this.tickCount);
		}
		super.setTarget(target);
	}

	/** Where to spend the night: inside the cabin once it is built, otherwise the camp centre. */
	public BlockPos restPos() {
		if (this.level() instanceof ServerLevel level) {
			CampData data = Camp.data(level.getServer());
			if (Camp.isCampLevel(level, data) && data.isCompleted(io.github.bradley09roberts.hardcorefriends.camp.Structures.CABIN)) {
				var site = data.site(io.github.bradley09roberts.hardcorefriends.camp.Structures.CABIN);
				if (site.isPresent()) {
					BlockPos inside = io.github.bradley09roberts.hardcorefriends.camp.Blueprint.worldPos(site.get().origin, site.get().rotation, 3, 1, 4);
					if (level.isLoaded(inside) && level.getBlockState(inside).getCollisionShape(level, inside).isEmpty()
						&& level.getBlockState(inside.above()).getCollisionShape(level, inside.above()).isEmpty()) {
						return inside;
					}
				}
			}
		}
		return homePos();
	}

	@Override
	public void hurtArmor(DamageSource source, float damage) {
		this.doHurtEquipment(source, damage, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (target instanceof Player || target instanceof CompanionEntity) {
			return false;
		}
		boolean hit = super.doHurtTarget(level, target);
		if (hit && getMainHandItem().isDamageableItem() && !Unity.carefulHands(this)) {
			damageMainHandTool(1);
		}
		// Defending the team earns Unity; butchering or hunting an animal for food does not.
		if (hit && target instanceof LivingEntity living && !living.isAlive() && target instanceof net.minecraft.world.entity.monster.Enemy) {
			Unity.add(level, Unity.DEFENCE, 3, 60);
			Camp.data(level.getServer()).addStat("mobs_defeated", 1);
		}
		if (hit) {
			onHitLanded(level, target);
		}
		return hit;
	}

	/**
	 * Tells the {@link CompanionEvents#HIT} listeners that a blow (or an arrow) of this friend's landed. Melee hits call
	 * it themselves; a ranged attack calls it when its arrow hits.
	 */
	public void onHitLanded(ServerLevel level, Entity target) {
		boolean killed = target instanceof LivingEntity living ? !living.isAlive() : target.isRemoved();
		for (CompanionEvents.Hit hook : CompanionEvents.HIT) {
			hook.hit(this, level, target, killed);
		}
	}

	public boolean isArmed() {
		return actions.has(s -> s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES));
	}

	/**
	 * How good the best weapon carried is: any sword beats any axe, the sturdier the better (as {@link #equipBestWeapon}
	 * chooses), then any other tool to hit with; 0 for bare hands.
	 */
	public int bestWeaponRank() {
		int best = rankWeapon(getMainHandItem());
		for (ItemStack s : backpack.stacks()) {
			best = Math.max(best, rankWeapon(s));
		}
		return best > 0 ? best : hasMeleeTool() ? 1 : 0;
	}

	/** Holds the strongest sword (or axe) carried. */
	public void equipBestWeapon() {
		ItemStack best = ItemStack.EMPTY;
		int bestRank = rankWeapon(getMainHandItem());
		for (ItemStack s : backpack.stacks()) {
			int r = rankWeapon(s);
			if (r > bestRank) {
				bestRank = r;
				best = s;
			}
		}
		if (!best.isEmpty()) {
			ItemStack chosen = best;
			actions.equip(s -> s == chosen);
		}
	}

	private static int rankWeapon(ItemStack s) {
		if (s.isEmpty()) {
			return 0;
		}
		if (s.is(ItemTags.SWORDS)) {
			return 2000 + s.getMaxDamage();
		}
		if (s.is(ItemTags.AXES)) {
			return 1000 + s.getMaxDamage();
		}
		return 0;
	}

	/** Plays the arm swing animation for nearby players. */
	public void swingArm() {
		this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	/** Damages the held tool, announcing when it breaks. */
	public void damageMainHandTool(int amount) {
		ItemStack tool = getMainHandItem();
		if (!tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(amount, this, EquipmentSlot.MAINHAND);
		}
	}

	@Override
	public void onEquippedItemBroken(ItemStack brokenItem, EquipmentSlot slot) {
		super.onEquippedItemBroken(brokenItem, slot);
		Speech.say(this, Line.TOOL_BROKE, brokenItem.getHoverName().getString());
		if (this.level() instanceof ServerLevel level) {
			Camp.data(level.getServer()).addStat("tools_worn_out", 1);
		}
	}

	/** A player or Aegis nearby to run towards when in danger. */
	public @Nullable LivingEntity nearestProtector(double radius) {
		LivingEntity best = null;
		double bestDist = radius * radius;
		if (!isFighter()) {
			for (CompanionEntity c : Companions.near((ServerLevel) level(), getBoundingBox().inflate(radius))) {
				if (c != this && c.isFighter() && !c.isRetreating()) {
					double d = c.distanceToSqr(this);
					if (d < bestDist) {
						bestDist = d;
						best = c;
					}
				}
			}
		}
		if (best == null) {
			Player p = level().getNearestPlayer(this, radius);
			if (p != null && !p.isSpectator()) {
				best = p;
			}
		}
		return best;
	}

	// -------------------------------------------------------------------- food

	public boolean hasFood() {
		return backpack.has(CompanionEntity::isEdible);
	}

	public static boolean isEdible(ItemStack s) {
		return s.is(ModTags.COMPANION_FOOD);
	}

	/** Eats one food item from the backpack to heal. Returns true if something was eaten. */
	public boolean eatFromBackpack() {
		if (getHealth() >= getMaxHealth()) {
			return false;
		}
		ItemStack food = backpack.take(CompanionEntity::isEdible, 1);
		if (food.isEmpty()) {
			return false;
		}
		eat(food);
		return true;
	}

	/** How much hunger a food satisfies: six points per point of nutrition (bread 30, cooked beef 48). */
	public static double hungerValue(ItemStack food) {
		FoodProperties props = food.get(DataComponents.FOOD);
		return props != null ? Math.max(1, props.nutrition()) * 6.0 : 6.0;
	}

	/**
	 * Eats one food item that has already been taken out of the backpack or chest: it fills hunger, heals a little,
	 * and leaves any bowl or bottle behind in the backpack.
	 */
	public void eat(ItemStack food) {
		FoodProperties props = food.get(DataComponents.FOOD);
		heal(props != null ? Math.max(1, props.nutrition()) : 2);
		needs.add(Needs.Need.HUNGER, hungerValue(food));
		UseRemainder remainder = food.get(DataComponents.USE_REMAINDER);
		if (remainder != null) {
			ItemStack left = backpack.insert(remainder.convertInto().create());
			if (!left.isEmpty() && this.level() instanceof ServerLevel level) {
				spawnAtLocation(level, left);
			}
		}
		this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
		this.swingArm();
	}

	// ------------------------------------------------------------------- death

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && !deathHandled) {
			deathHandled = true;
			SpecialityTask.release(getUUID()); // the shared jobs they held, or asked back, are free for the others now
			for (CompanionEvents.Death hook : CompanionEvents.DEATH) {
				hook.died(this, level, source);
			}
			Component cause = source.getLocalizedDeathMessage(this);
			dropBackpack(level);
			CampData data = Camp.data(level.getServer());
			CampData.Ledger ledger = data.ledger(friendId());
			if (!isSettler() && (getUUID().equals(ledger.entityId) || ledger.state == CampData.LifeState.ALIVE)) {
				ledger.state = CampData.LifeState.DEAD;
				ledger.diedAtGameTime = level.getOverworldClockTime();
				ledger.deaths++;
				ledger.lastKnownPos = blockPosition();
				ledger.lastKnownDimension = Camp.dimensionId(level);
				ledger.deathCause = cause.getString();
				data.markDanger(blockPosition(), level.getGameTime());
				data.touchLedger();
			}
			if (!isTeamMember()) {
				// A stranger was never on the team: the team does not mourn them (the settler package tells those nearby).
				super.die(source);
				return;
			}
			BlockPos p = blockPosition();
			Speech.announce(level.getServer(), Speech.prefix(this).append(Component.literal(
				cause.getString() + ". Their backpack lies at " + p.getX() + " " + p.getY() + " " + p.getZ() + ".")
				.withStyle(ChatFormatting.RED)));
			Unity.lose(level.getServer(), 80);
			for (CompanionEntity other : Companions.all()) {
				if (other != this) {
					Speech.say(other, Line.FRIEND_DIED, displayName());
				}
			}
		}
		super.die(source);
	}

	/** Drops every carried item and piece of gear inside one backpack item that never despawns. */
	public void dropBackpack(ServerLevel level) {
		List<ItemStack> items = new ArrayList<>(backpack.drainAll());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack gear = getItemBySlot(slot);
			if (!gear.isEmpty()) {
				items.add(gear.copy());
				setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		if (items.isEmpty()) {
			return;
		}
		for (ItemStack bag : BackpackItem.pack(displayName(), nameColour(), items)) {
			ItemEntity entity = new ItemEntity(level, getX(), getY() + 0.5, getZ(), bag);
			entity.setUnlimitedLifetime();
			entity.setPickUpDelay(20);
			level.addFreshEntity(entity);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		// Everything is already inside the dropped backpack.
	}

	@Override
	protected void dropEquipment(ServerLevel level) {
		// Everything is already inside the dropped backpack.
	}

	// ------------------------------------------------------------- persistence

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Friend", friendId().key());
		output.putInt("Mode", mode().ordinal());
		if (homePos != null) {
			output.putLong("Home", homePos.asLong());
		}
		if (stayPos != null) {
			output.putLong("StayAt", stayPos.asLong());
		}
		if (leaderId != null) {
			output.putString("Leader", leaderId.toString());
		}
		backpack.save(output);
		needs.save(output);
		output.storeNullable("Persona", Persona.CODEC, persona);
		if (!extra.isEmpty()) {
			output.store("Extra", CompoundTag.CODEC, extra);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		FriendId id = FriendId.byKey(input.getStringOr("Friend", "fern")).orElse(FriendId.FERN);
		this.entityData.set(DATA_FRIEND, id.ordinal());
		applyStats(id);
		this.entityData.set(DATA_MODE, CompanionMode.byOrdinal(input.getIntOr("Mode", 0)).ordinal());
		input.getLong("Home").ifPresent(l -> homePos = BlockPos.of(l));
		input.getLong("StayAt").ifPresent(l -> stayPos = BlockPos.of(l));
		input.getString("Leader").ifPresent(s -> {
			try {
				leaderId = UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				leaderId = null;
			}
		});
		backpack.load(input);
		needs.load(input);
		input.read("Persona", Persona.CODEC).ifPresent(this::setPersona);
		extra = input.read("Extra", CompoundTag.CODEC).map(CompoundTag::copy).orElseGet(CompoundTag::new);
	}
}
