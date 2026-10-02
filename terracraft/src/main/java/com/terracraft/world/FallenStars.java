package com.terracraft.world;

import com.terracraft.registry.content.CoreItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;

/**
 * Terraria Fallen Stars: at night stars fall from the sky near players (glowing, with a sparkle trail) and
 * every star still lying on the ground vanishes at sunrise. Stars in inventories are kept.
 */
public final class FallenStars {
    private static final String TAG = "terracraft_fallen_star";
    /** Chance per player per second at night (about one star every 70 seconds). */
    private static final float CHANCE_PER_SECOND = 0.015F;

    private FallenStars() {}

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(FallenStars::onServerTick);
        com.terracraft.command.TerrariaCommand.addExtension(root -> root.then(net.minecraft.commands.Commands.literal("star").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            drop(player.level(), player, player.getRandom());
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("A star fell from the sky."), false);
            return 1;
        })));
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.server();
        ServerLevel level = server.overworld();
        long tick = server.getTickCount();
        if (tick % 20 == 0 && level.isDarkOutside()) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && level.getRandom().nextFloat() < CHANCE_PER_SECOND) {
                    drop(level, player, level.getRandom());
                }
            }
        }
        if (tick % 5 == 0) {
            // falling trail + landing sparkle
            for (ItemEntity star : level.getEntities(EntityTypes.ITEM, e -> e.entityTags().contains(TAG))) {
                if (!star.onGround()) {
                    level.sendParticles(ParticleTypes.END_ROD, star.getX(), star.getY() + 0.3, star.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
                } else if (tick % 40 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, star.getX(), star.getY() + 0.4, star.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
                }
            }
        }
        if (tick % 100 == 0 && level.isBrightOutside()) {
            for (ItemEntity star : level.getEntities(EntityTypes.ITEM, e -> e.entityTags().contains(TAG))) {
                star.discard();
            }
        }
    }

    /** Drops one star somewhere around the player; also used by {@code /terraria star}. */
    public static void drop(ServerLevel level, ServerPlayer player, RandomSource random) {
        double x = player.getX() + (random.nextDouble() - 0.5) * 70;
        double z = player.getZ() + (random.nextDouble() - 0.5) * 70;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double y = Math.min(level.getMaxY() - 2, Math.max(ground, player.getY()) + 45);
        ItemEntity star = new ItemEntity(level, x, y, z, new ItemStack(CoreItems.FALLEN_STAR.get()));
        star.setDeltaMovement((random.nextDouble() - 0.5) * 0.3, -1.2, (random.nextDouble() - 0.5) * 0.3);
        star.setGlowingTag(true);
        star.addTag(TAG);
        star.setUnlimitedLifetime();
        level.addFreshEntity(star);
        level.playSound(null, x, Math.max(ground, player.getY()), z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 2.0F, 0.6F);
    }
}
