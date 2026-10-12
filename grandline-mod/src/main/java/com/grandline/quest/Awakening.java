package com.grandline.quest;

import com.grandline.combat.Loadouts;
import com.grandline.net.GLNetwork;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Devil Fruit awakening (agreed rule: mastery plus a trial). When a character's fruit mastery reaches
 * {@link #MASTERY} and they are at least level {@link #LEVEL}, the fruit "stirs" and the Awakening Trial begins:
 * bring down twelve strong foes (tier 6+) with the fruit's own powers and reach the New World. Completing it
 * awakens the fruit: awakened abilities unlock, Zoans gain their awakened form, every power hits harder.
 */
public final class Awakening {
    public static final int MASTERY = 50, LEVEL = 40;
    public static final String TRIAL_FLAG = "fruit_awakening_trial_done";

    public static void tick(ServerPlayer sp, CharacterData d) {
        if (sp.tickCount % 100 == 0) check(sp, d);
    }

    public static void check(ServerPlayer sp, CharacterData d) {
        if (d.fruit == null || d.fruitAwakened) return;
        if (d.flags.contains(TRIAL_FLAG)) {
            d.fruitAwakened = true;
            Loadouts.refresh(sp, d);
            sp.sendSystemMessage(Component.translatable("grandline.awakening.done").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.7f);
            GLNetwork.fx(sp, "awaken", sp.getX(), sp.getY() + 1, sp.getZ(), 1, 0, 0);
            CharacterManager.markDirty(sp);
            com.grandline.journey.Journey.onAwakened(sp);
            return;
        }
        if (d.level >= LEVEL && d.mastery("fruit/" + d.fruit) >= MASTERY && !d.questProgress.containsKey("awakening_trial")) {
            if (Quests.start(sp, "awakening_trial"))
                sp.sendSystemMessage(Component.translatable("grandline.awakening.stirs").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private Awakening() {}
}
