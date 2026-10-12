package com.grandline.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Automated in-game verification commands (/grandline test ...), filled in by the testing milestone. */
public final class TestCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("test");
    }

    private TestCommands() {}
}
