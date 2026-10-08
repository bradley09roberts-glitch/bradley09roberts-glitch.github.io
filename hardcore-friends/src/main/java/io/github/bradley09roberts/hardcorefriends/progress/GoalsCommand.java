package io.github.bradley09roberts.hardcorefriends.progress;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /friends goals}: Sage's plan, step by step, with a checklist of what the step in hand needs and what the camp
 * already has, the camp's experience, and what the plan wants gathered. Read-only, so it works at permission level 0
 * with cheats off.
 */
public final class GoalsCommand {
	private GoalsCommand() {
	}

	/** Adds {@code goals} to {@code /friends}. */
	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("goals").executes(GoalsCommand::goals));
	}

	private static int goals(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ProgressPlan.evaluate(source.getServer());
		for (Component line : ProgressPlan.describe(source.getServer())) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}
}
