package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The doctor looks after the camp's hurt and poorly, by day, inside the camp, between fights:
 * <ul>
 * <li>a friend poisoned or withering is given a bucket of milk (it clears every effect, as for a player);</li>
 * <li>a friend badly hurt (40% of their health or less) who is not already mending (no regeneration, and either too
 * hungry to heal or down to 30%) is given a potion of regeneration or of healing, or at 30% or less a golden apple.</li>
 * </ul>
 * Only real remedies from the clinic's chests or the supply chest are used, and used up as the game uses them (the
 * empty bottle or bucket comes back). Remedies are kept for those who need them: a friend who will heal on their own
 * in a moment is left to it, and the camp's emergency potions for fights are the friends' own to carry.
 */
final class DoctorTask extends TradeJob {
	static final String ID = "market.doctor";
	private static final int REPLAN = 100;
	private static final double BADLY_HURT = 0.4;
	private static final double DIRE = 0.3;
	private static final int CALM_TICKS = 100;
	private static final List<Holder<MobEffect>> AILMENTS = List.of(MobEffects.POISON, MobEffects.WITHER);

	/** A patient and what to give them. */
	private record Case(UUID patient, String what, Predicate<ItemStack> remedy, boolean cure) {
	}

	private @Nullable Case current;
	private @Nullable Workplace clinic;
	private boolean fetched;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Case planned;

	DoctorTask() {
		super(ID, Set.of(), Trade.DOCTOR);
	}

	@Override
	public String describe() {
		Case k = current;
		return k == null ? "looking after the hurt and poorly" : "seeing to a patient";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (!workingHours(level) || c.getTarget() != null) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= REPLAN || now < plannedAt) {
			plannedAt = now;
			planned = findCase(c, level, w);
		}
		Case k = planned;
		return k == null ? 0 : k.cure() ? 60 : 56;
	}

	private static @Nullable Case findCase(CompanionEntity doctor, ServerLevel level, @Nullable Workplace w) {
		List<BlockPos> stores = stores(level, w);
		for (CompanionEntity p : Companions.in(level)) {
			if (!p.isAlive() || p.mode() == CompanionMode.FOLLOW || p.getTarget() != null || p.ticksSinceDamaged() < CALM_TICKS
				|| !WorldEditGuard.inCamp(doctor, p.blockPosition())) {
				continue;
			}
			boolean ailing = false;
			for (Holder<MobEffect> e : AILMENTS) {
				ailing |= p.hasEffect(e);
			}
			if (ailing && available(doctor, level, stores, s -> s.is(Items.MILK_BUCKET))) {
				return new Case(p.getUUID(), "milk", s -> s.is(Items.MILK_BUCKET), true);
			}
			double health = p.getHealth() / p.getMaxHealth();
			boolean mending = p.hasEffect(MobEffects.REGENERATION) || p.needs().canHeal() && health > DIRE;
			if (health > BADLY_HURT || mending) {
				continue;
			}
			Predicate<ItemStack> regen = s -> Gear.potionWith(s, MobEffects.REGENERATION);
			Predicate<ItemStack> heal = s -> Gear.potionWith(s, MobEffects.INSTANT_HEALTH);
			if (available(doctor, level, stores, regen)) {
				return new Case(p.getUUID(), "a potion of regeneration", regen, false);
			}
			if (available(doctor, level, stores, heal)) {
				return new Case(p.getUUID(), "a potion of healing", heal, false);
			}
			if (health <= DIRE && available(doctor, level, stores, s -> s.is(Items.GOLDEN_APPLE))) {
				return new Case(p.getUUID(), "a golden apple", s -> s.is(Items.GOLDEN_APPLE), false);
			}
		}
		return null;
	}

	/** Where remedies are kept: the clinic's own chests, then the supply chest. */
	private static List<BlockPos> stores(ServerLevel level, @Nullable Workplace w) {
		List<BlockPos> list = new ArrayList<>(Stores.chests(level, w));
		Stores.supplyPos(level).ifPresent(list::add);
		return list;
	}

	private static boolean available(CompanionEntity doctor, ServerLevel level, List<BlockPos> stores, Predicate<ItemStack> remedy) {
		return doctor.backpack().has(remedy) || Stores.count(level, stores, remedy) > 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		clinic = workplace(c, holding(c));
		current = findCase(c, level, clinic);
		plannedAt = Long.MIN_VALUE / 2;
		Case k = current;
		if (k == null) {
			return false;
		}
		fetched = c.backpack().has(k.remedy());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Case k = current;
		CompanionEntity patient = k == null ? null : find(level, k.patient());
		if (k == null || patient == null || !patient.isAlive() || patient.getTarget() != null || !workingHours(level)) {
			return TaskStatus.FAILURE;
		}
		if (!fetched) {
			BlockPos from = null;
			for (BlockPos p : stores(level, clinic)) {
				if (Stores.count(level, List.of(p), k.remedy()) > 0) {
					from = p;
					break;
				}
			}
			if (from == null) {
				return TaskStatus.FAILURE;
			}
			switch (Stores.walk(c, from, 2.5)) {
				case WALKING -> {
					return TaskStatus.RUNNING;
				}
				case FAILED -> {
					return TaskStatus.FAILURE;
				}
				case ARRIVED -> {
				}
			}
			Stores.withdraw(c, from, k.remedy(), 1);
			fetched = c.backpack().has(k.remedy());
			return fetched ? TaskStatus.RUNNING : TaskStatus.FAILURE;
		}
		if (!c.actions().walkToEntity(patient, 2.0)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (!stillNeeds(patient, k)) {
			return TaskStatus.SUCCESS; // better already: the remedy is kept for someone else
		}
		ItemStack remedy = c.backpack().take(k.remedy(), 1);
		if (remedy.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(patient);
		c.swingArm();
		Speech.say(c, Line.HEALING, patient.displayName());
		if (remedy.has(net.minecraft.core.component.DataComponents.FOOD)) {
			patient.needs().add(Needs.Need.HUNGER, CompanionEntity.hungerValue(remedy));
		}
		// Used up as the game uses it, on the patient: its effects, and the bottle or bucket left over.
		ItemStack left = remedy.finishUsingItem(level, patient);
		if (!left.isEmpty()) {
			Stores.give(c, left);
		}
		Camp.data(level.getServer()).addStat("patients_treated", 1);
		return TaskStatus.SUCCESS;
	}

	private static boolean stillNeeds(CompanionEntity p, Case k) {
		if (k.cure()) {
			for (Holder<MobEffect> e : AILMENTS) {
				if (p.hasEffect(e)) {
					return true;
				}
			}
			return false;
		}
		return p.getHealth() / p.getMaxHealth() <= BADLY_HURT && !p.hasEffect(MobEffects.REGENERATION);
	}

	private static @Nullable CompanionEntity find(ServerLevel level, UUID id) {
		for (CompanionEntity c : Companions.in(level)) {
			if (c.getUUID().equals(id)) {
				return c;
			}
		}
		return null;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		current = null;
		clinic = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int successCooldown() {
		return 20 * 5;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
