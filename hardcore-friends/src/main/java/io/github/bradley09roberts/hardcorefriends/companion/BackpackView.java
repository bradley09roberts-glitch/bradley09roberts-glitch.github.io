package io.github.bradley09roberts.hardcorefriends.companion;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The backpack as seen through a chest screen. Closes itself if the friend dies or walks away. */
final class BackpackView implements Container {
	private final CompanionEntity owner;
	private final Container inner;

	BackpackView(CompanionEntity owner) {
		this.owner = owner;
		this.inner = owner.backpack().container();
	}

	@Override
	public int getContainerSize() {
		return inner.getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return inner.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return inner.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return inner.removeItem(slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return inner.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack itemStack) {
		inner.setItem(slot, itemStack);
	}

	@Override
	public void setChanged() {
		inner.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return owner.isAlive() && !owner.isRemoved() && player.distanceToSqr(owner) <= 64;
	}

	@Override
	public void clearContent() {
		inner.clearContent();
	}
}
