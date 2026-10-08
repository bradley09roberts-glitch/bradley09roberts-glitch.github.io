package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * {@code /friends gear}: what each friend nearby wears and carries to fight with (armour points, weapon, shield, bow and
 * arrows, emergency healing). Read-only and open to everyone (permission level 0, no cheats): it gives nothing and
 * moves nothing.
 */
public final class GearCommand {
	private GearCommand() {
	}

	/** Adds {@code gear} to the {@code /friends} root. */
	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("gear").executes(GearCommand::gear));
	}

	private static int gear(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<CompanionEntity> friends = new ArrayList<>(Companions.all());
		if (friends.isEmpty()) {
			source.sendFailure(Component.literal("None of your friends are nearby."));
			return 0;
		}
		friends.sort(Comparator.comparingInt(CompanionEntity::rosterIndex));
		source.sendSuccess(() -> Component.literal("Your friends' gear").withStyle(ChatFormatting.GOLD), false);
		for (CompanionEntity c : friends) {
			Component line = Speech.prefix(c).append(Component.literal(describe(c)).withStyle(ChatFormatting.GRAY));
			source.sendSuccess(() -> line, false);
		}
		return friends.size();
	}

	/** "armour 7 (iron chestplate, leather boots), weapon Iron Sword, shield, bow and 23 arrows, 1 healing item". */
	static String describe(CompanionEntity c) {
		List<String> worn = new ArrayList<>();
		for (EquipmentSlot slot : Gear.ARMOUR_SLOTS) {
			ItemStack piece = c.getItemBySlot(slot);
			if (!piece.isEmpty()) {
				worn.add(piece.getHoverName().getString().toLowerCase(Locale.ROOT));
			}
		}
		StringBuilder text = new StringBuilder();
		text.append(String.format(Locale.ROOT, "armour %.0f", c.getAttributeValue(Attributes.ARMOR)));
		if (!worn.isEmpty()) {
			text.append(" (").append(String.join(", ", worn)).append(')');
		}
		ItemStack weapon = bestWeapon(c);
		text.append(", ").append(weapon.isEmpty() ? "no sword or axe" : "weapon " + weapon.getHoverName().getString());
		text.append(Gear.blocks(c.getOffhandItem()) ? ", shield" : Gear.hasShield(c) ? ", shield (carried)" : ", no shield");
		if (Gear.hasBow(c)) {
			int arrows = Gear.arrows(c);
			text.append(", bow and ").append(arrows).append(arrows == 1 ? " arrow" : " arrows");
		}
		int healing = Gear.healingItems(c);
		if (healing > 0) {
			text.append(", ").append(healing).append(healing == 1 ? " healing item" : " healing items");
		}
		return text.toString();
	}

	private static ItemStack bestWeapon(CompanionEntity c) {
		ItemStack best = Gear.weaponRank(c.getMainHandItem()) > 0 ? c.getMainHandItem() : ItemStack.EMPTY;
		for (ItemStack s : c.backpack().stacks()) {
			if (Gear.weaponRank(s) > Gear.weaponRank(best)) {
				best = s;
			}
		}
		return best;
	}
}
