package io.github.bradley09roberts.hardcorefriends.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;

/**
 * The backpack a friend drops when they die or are dismissed. Using it empties everything into your inventory
 * (anything that does not fit drops at your feet) and the bag is used up. The bag is fire-resistant (registered
 * with {@code fireResistant()}), so it floats on lava, and if a cactus or an explosion destroys it the contents
 * spill out like a shulker box's instead of vanishing.
 */
public class BackpackItem extends Item {
	public BackpackItem(Item.Properties properties) {
		super(properties);
	}

	/** Packs items into one or more backpacks named after the friend. */
	public static List<ItemStack> pack(FriendId owner, List<ItemStack> items) {
		List<ItemStack> bags = new ArrayList<>();
		for (int start = 0; start < items.size(); start += ItemContainerContents.MAX_SIZE) {
			List<ItemStack> chunk = items.subList(start, Math.min(items.size(), start + ItemContainerContents.MAX_SIZE));
			ItemStack bag = new ItemStack(ModItems.BACKPACK);
			bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(new ArrayList<>(chunk)));
			bag.set(DataComponents.CUSTOM_NAME, Component.literal(owner.displayName() + "'s Backpack")
				.withStyle(s -> s.withColor(owner.colour()).withItalic(false)));
			bags.add(bag);
		}
		return bags;
	}

	@Override
	public void onDestroyed(ItemEntity entity) {
		ItemContainerContents contents = entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (contents != null) {
			ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemCopyStream());
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack bag = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		ItemContainerContents contents = bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		List<ItemStack> items = contents.nonEmptyItemCopyStream().toList();
		bag.shrink(1);
		int count = 0;
		for (ItemStack stack : items) {
			count += stack.getCount();
			if (!player.getInventory().add(stack)) {
				player.spawnAtLocation(serverLevel, stack);
			}
		}
		serverLevel.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.literal("You unpack " + count + " items.").withStyle(ChatFormatting.GRAY));
		return InteractionResult.SUCCESS_SERVER;
	}
}
