package com.starforged.moon.world;

import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModTags;
import com.starforged.sun.SunFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The Tide Cycle of the Pale Reach: every five minutes the tide turns.
 * <ul>
 *     <li><b>High Tide</b> - gravity grows even weaker, the Silver Sea's clams clamp shut, and tidebound creatures grow
 *     stronger and faster.</li>
 *     <li><b>Low Tide</b> - the clams open, offering their Lunar Pearls.</li>
 * </ul>
 */
public final class MoonTides {
    public static final int HALF_CYCLE = 6000;
    private static Boolean lastHigh;
    /** Shift applied by {@code /starforged tide turn} (not saved - a restart returns to the natural rhythm). */
    private static long offset;

    private MoonTides() {
    }

    public static boolean isHighTide(Level level) {
        return PaleReachTravel.isPaleReach(level) && (time(level) / HALF_CYCLE) % 2 == 0;
    }

    public static int ticksUntilTurn(Level level) {
        return (int) (HALF_CYCLE - time(level) % HALF_CYCLE);
    }

    private static long time(Level level) {
        return level.getGameTime() + offset;
    }

    /** Turns the tide right now (admin/showcase command). */
    public static void turn(Level level) {
        offset += ticksUntilTurn(level);
    }

    public static Component describe(Level level) {
        if (!PaleReachTravel.isPaleReach(level)) {
            return Component.translatable("commands.starforged.tide").withStyle(ChatFormatting.GRAY);
        }
        int minutes = Math.max(1, Math.round(ticksUntilTurn(level) / 1200.0F));
        return isHighTide(level)
            ? Component.translatable("event.starforged.tide.status_high", minutes).withStyle(ChatFormatting.AQUA)
            : Component.translatable("event.starforged.tide.status_low", minutes).withStyle(ChatFormatting.GRAY);
    }

    public static void tick(ServerLevel level) {
        if (!PaleReachTravel.isPaleReach(level)) {
            return;
        }
        boolean high = isHighTide(level);
        if (lastHigh == null || lastHigh != high) {
            boolean announce = lastHigh != null;
            lastHigh = high;
            MoonGravity.refreshAll(level);
            if (announce) {
                for (ServerPlayer player : level.players()) {
                    SunFx.title(player, Component.translatable(high ? "event.starforged.tide.high" : "event.starforged.tide.low")
                            .withStyle(high ? ChatFormatting.AQUA : ChatFormatting.WHITE, ChatFormatting.BOLD),
                        Component.translatable(high ? "event.starforged.tide.high_sub" : "event.starforged.tide.low_sub").withStyle(ChatFormatting.GRAY),
                        10, 50, 20);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), high ? MoonSounds.TIDE_HIGH.get() : MoonSounds.TIDE_LOW.get(),
                        SoundSource.AMBIENT, 1.0F, 1.0F);
                }
            }
        }
        if (high && level.getGameTime() % 100 == 0) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && living.is(ModTags.TIDEBOUND)) {
                    living.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 120, 0, true, false));
                    living.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, 0, true, false));
                }
            }
        }
    }
}
