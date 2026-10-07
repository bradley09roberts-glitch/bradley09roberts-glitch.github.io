package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A friend's personal storage. Always 27 physical slots; only the first {@link #capacity()} accept new items
 * (9, 18 or 27 depending on the Unity bond). Items never vanish when capacity shrinks; they just stop growing.
 */
public final class Backpack {
	public static final int MAX_SLOTS = 27;

	private final SimpleContainer container = new SimpleContainer(MAX_SLOTS);
	private int capacity = 9;

	public SimpleContainer container() {
		return container;
	}

	public int capacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = Math.clamp(capacity, 9, MAX_SLOTS);
	}

	/** Rows to show in the backpack screen: enough for the capacity and for any item stored beyond it. */
	public int visibleRows() {
		int highest = 0;
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!container.getItem(i).isEmpty()) {
				highest = i + 1;
			}
		}
		return Math.max(1, (Math.max(capacity, highest) + 8) / 9);
	}

	public ItemStack get(int slot) {
		return container.getItem(slot);
	}

	public int count(Predicate<ItemStack> filter) {
		int total = 0;
		for (int i = 0; i < MAX_SLOTS; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public int count(Item item) {
		return count(s -> s.is(item));
	}

	public int count(TagKey<Item> tag) {
		return count(s -> s.is(tag));
	}

	public boolean has(Predicate<ItemStack> filter) {
		return slotOf(filter) >= 0;
	}

	public int slotOf(Predicate<ItemStack> filter) {
		for (int i = 0; i < MAX_SLOTS; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				return i;
			}
		}
		return -1;
	}

	/** First matching stack (live reference, do not keep), or {@link ItemStack#EMPTY}. */
	public ItemStack find(Predicate<ItemStack> filter) {
		int slot = slotOf(filter);
		return slot < 0 ? ItemStack.EMPTY : container.getItem(slot);
	}

	/** Adds as much of the stack as fits into the usable slots. Returns the remainder (possibly empty). */
	public ItemStack insert(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack remaining = stack.copy();
		for (int i = 0; i < MAX_SLOTS && !remaining.isEmpty(); i++) {
			ItemStack existing = container.getItem(i);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
				int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
				if (room > 0) {
					int moved = Math.min(room, remaining.getCount());
					existing.grow(moved);
					remaining.shrink(moved);
				}
			}
		}
		for (int i = 0; i < capacity && !remaining.isEmpty(); i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, remaining.copy());
				remaining = ItemStack.EMPTY;
			}
		}
		container.setChanged();
		return remaining;
	}

	public boolean canFit(ItemStack stack) {
		int need = stack.getCount();
		for (int i = 0; i < MAX_SLOTS && need > 0; i++) {
			ItemStack existing = container.getItem(i);
			if (existing.isEmpty()) {
				if (i < capacity) {
					need -= stack.getMaxStackSize();
				}
			} else if (ItemStack.isSameItemSameComponents(existing, stack)) {
				need -= existing.getMaxStackSize() - existing.getCount();
			}
		}
		return need <= 0;
	}

	/** Removes up to {@code amount} matching items. Returns how many were removed. */
	public int remove(Predicate<ItemStack> filter, int amount) {
		int removed = 0;
		for (int i = MAX_SLOTS - 1; i >= 0 && removed < amount; i--) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				int take = Math.min(amount - removed, stack.getCount());
				stack.shrink(take);
				removed += take;
				if (stack.isEmpty()) {
					container.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		if (removed > 0) {
			container.setChanged();
		}
		return removed;
	}

	/** Takes up to {@code amount} items of the first matching kind as one stack. */
	public ItemStack take(Predicate<ItemStack> filter, int amount) {
		int slot = slotOf(filter);
		if (slot < 0 || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack template = container.getItem(slot).copyWithCount(1);
		int limit = Math.min(amount, template.getMaxStackSize());
		int removed = remove(s -> ItemStack.isSameItemSameComponents(s, template), limit);
		return template.copyWithCount(removed);
	}

	/** Removes and returns the whole stack in a slot. */
	public ItemStack removeSlot(int slot) {
		ItemStack stack = container.removeItemNoUpdate(slot);
		container.setChanged();
		return stack;
	}

	public int usedSlots() {
		int used = 0;
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!container.getItem(i).isEmpty()) {
				used++;
			}
		}
		return used;
	}

	public int freeSlots() {
		int free = 0;
		for (int i = 0; i < capacity; i++) {
			if (container.getItem(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/** Fraction of usable slots that hold something (0–1). */
	public double fullness() {
		return Math.min(1.0, usedSlots() / (double) capacity);
	}

	public boolean isEmpty() {
		return container.isEmpty();
	}

	/** Empties the backpack and returns every stack it held. */
	public List<ItemStack> drainAll() {
		List<ItemStack> all = new ArrayList<>();
		for (int i = 0; i < MAX_SLOTS; i++) {
			ItemStack stack = container.removeItemNoUpdate(i);
			if (!stack.isEmpty()) {
				all.add(stack);
			}
		}
		container.setChanged();
		return all;
	}

	/** Non-empty stacks, as live references. */
	public List<ItemStack> stacks() {
		List<ItemStack> all = new ArrayList<>();
		for (int i = 0; i < MAX_SLOTS; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				all.add(stack);
			}
		}
		return all;
	}

	public void save(ValueOutput output) {
		output.putInt("BackpackCapacity", capacity);
		container.storeAsItemList(output.list("Backpack", ItemStack.CODEC));
	}

	public void load(ValueInput input) {
		capacity = Math.clamp(input.getIntOr("BackpackCapacity", 9), 9, MAX_SLOTS);
		container.clearContent();
		container.fromItemList(input.listOrEmpty("Backpack", ItemStack.CODEC));
	}
}
