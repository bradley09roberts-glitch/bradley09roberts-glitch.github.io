package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Flint takes raw iron, copper or gold (at least {@value #MIN_RAW}) to the camp furnace with enough fuel to smelt
 * it: coal or charcoal (8 items each) or planks (1.5 items each). He only adds to empty slots or slots holding the
 * same item, so nobody else's smelting is disturbed. The ingots are collected later by {@link CollectSmeltedTask}.
 */
public final class SmeltTask implements CompanionTask {
	public static final int MIN_RAW = 3;
	private static final Item[] RAW = {Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD};

	private final CampFurnace furnace;
	private @Nullable BlockPos target;

	public SmeltTask(CampFurnace furnace) {
		this.furnace = furnace;
	}

	@Override
	public String id() {
		return "flint.smelt";
	}

	@Override
	public String describe() {
		return "smelting ore";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel)) {
			return 0;
		}
		Backpack bp = c.backpack();
		if (bp.count(MiningHelper::isRawOre) < MIN_RAW || !bp.has(SmeltTask::isFuel)) {
			return 0;
		}
		AbstractFurnaceBlockEntity f = furnace.get(c);
		return f != null && chooseRaw(bp, f.getItem(CampFurnace.SLOT_INPUT)) != null ? 40 : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = furnace.pos(c);
		return target != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (target == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (!c.actions().canReach(target)) {
			c.actions().walkTo(target, 2.0);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(level, target);
		if (f == null) {
			furnace.forget();
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		int loaded = load(c.backpack(), f);
		if (loaded <= 0) {
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		level.playSound(null, target, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
		Camp.data(level.getServer()).addStat("ores_smelted", loaded);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}

	// ------------------------------------------------------------------ loading

	public static boolean isFuel(ItemStack s) {
		return s.is(ItemTags.COALS) || s.is(ItemTags.PLANKS);
	}

	/** Smelts per fuel item, doubled so planks (1.5) stay whole: coal 16, planks 3. */
	private static int halfSmeltsPer(ItemStack fuel) {
		if (fuel.is(ItemTags.COALS)) {
			return 16;
		}
		if (fuel.is(ItemTags.PLANKS)) {
			return 3;
		}
		return 0;
	}

	/** The raw ore to put in: whatever the input slot already holds (if carried), else the one carried most. */
	private static @Nullable Item chooseRaw(Backpack bp, ItemStack input) {
		if (!input.isEmpty()) {
			for (Item raw : RAW) {
				if (input.is(raw) && input.getCount() < input.getMaxStackSize() && bp.count(raw) > 0) {
					return raw;
				}
			}
			return null;
		}
		Item best = null;
		int most = 0;
		for (Item raw : RAW) {
			int n = bp.count(raw);
			if (n > most) {
				most = n;
				best = raw;
			}
		}
		return best;
	}

	/**
	 * Puts raw ore into the input slot and enough fuel into the fuel slot. Returns how many ore items went in.
	 * Public for tests.
	 */
	public static int load(Backpack bp, AbstractFurnaceBlockEntity f) {
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		Item raw = chooseRaw(bp, input);
		if (raw == null) {
			return 0;
		}
		int room = input.isEmpty() ? raw.getDefaultMaxStackSize() : input.getMaxStackSize() - input.getCount();
		ItemStack taken = bp.take(s -> s.is(raw), room);
		if (taken.isEmpty()) {
			return 0;
		}
		int loaded = taken.getCount();
		if (input.isEmpty()) {
			f.setItem(CampFurnace.SLOT_INPUT, taken);
		} else {
			input.grow(loaded);
			f.setItem(CampFurnace.SLOT_INPUT, input);
		}
		int queued = f.getItem(CampFurnace.SLOT_INPUT).getCount();
		ItemStack fuelSlot = f.getItem(CampFurnace.SLOT_FUEL);
		int needHalf = queued * 2 - halfSmeltsPer(fuelSlot) * fuelSlot.getCount();
		if (needHalf > 0) {
			ItemStack fuel = fuelSlot.isEmpty()
				? bp.find(s -> s.is(ItemTags.COALS)).isEmpty() ? bp.find(s -> s.is(ItemTags.PLANKS)) : bp.find(s -> s.is(ItemTags.COALS))
				: bp.find(s -> ItemStack.isSameItemSameComponents(s, fuelSlot));
			int per = halfSmeltsPer(fuel);
			if (!fuel.isEmpty() && per > 0 && f.canPlaceItem(CampFurnace.SLOT_FUEL, fuel)) {
				ItemStack template = fuel.copyWithCount(1);
				int want = (needHalf + per - 1) / per;
				int space = fuelSlot.isEmpty() ? template.getMaxStackSize() : fuelSlot.getMaxStackSize() - fuelSlot.getCount();
				ItemStack fuelTaken = bp.take(s -> ItemStack.isSameItemSameComponents(s, template), Math.min(want, space));
				if (!fuelTaken.isEmpty()) {
					if (fuelSlot.isEmpty()) {
						f.setItem(CampFurnace.SLOT_FUEL, fuelTaken);
					} else {
						fuelSlot.grow(fuelTaken.getCount());
						f.setItem(CampFurnace.SLOT_FUEL, fuelSlot);
					}
				}
			}
		}
		f.setChanged();
		return loaded;
	}
}
