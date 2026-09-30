package net.palemeridian.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.palemeridian.state.PMState;
import net.palemeridian.world.Restoration;
import net.palemeridian.world.WorldSetup;

/**
 * {@code /pmadmin} — operator diagnostics for the Java side. Campaign repairs live in the data pack
 * ({@code /function palemeridian:admin/...}); see docs/SERVER_GUIDE.md.
 */
public final class AdminCommand {
	private AdminCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pmadmin")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("status").executes(ctx -> {
				var server = ctx.getSource().getServer();
				StringBuilder sb = new StringBuilder("[Pale Meridian] valley world: ").append(WorldSetup.isValleyWorld())
					.append(", restored districts: ").append(Restoration.restored(server))
					.append(", lifting jobs running: ").append(Restoration.runningJobs())
					.append(", schema: ").append(PMState.get(server, "#schema"))
					.append(", chapter: ").append(PMState.get(server, "#chapter"));
				ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
				return 1;
			}))
			.then(Commands.literal("relift").then(Commands.argument("district", StringArgumentType.word()).executes(ctx -> {
				String d = StringArgumentType.getString(ctx, "district");
				if (!Restoration.GROUPS.contains(d)) {
					ctx.getSource().sendFailure(Component.literal("Unknown district. One of: " + Restoration.GROUPS));
					return 0;
				}
				var server = ctx.getSource().getServer();
				if (PMState.get(server, "#r." + d) != 1) {
					ctx.getSource().sendFailure(Component.literal("District '" + d + "' is not restored in the campaign state; nothing to re-apply."));
					return 0;
				}
				PMState.set(server, "#req." + d, 1);
				ctx.getSource().sendSuccess(() -> Component.literal("Re-applying the lifted Pall for '" + d + "' to loaded chunks."), true);
				return 1;
			}))));
	}
}
