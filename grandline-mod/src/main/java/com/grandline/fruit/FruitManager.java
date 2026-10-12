package com.grandline.fruit;

import com.grandline.combat.Loadouts;
import com.grandline.core.GLConfig;
import com.grandline.item.DevilFruitItem;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Eating fruits: ownership, the second-fruit death rule, and the world ledger. */
public final class FruitManager {
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    /** Called on right-click. A character who already has a fruit must confirm within 5 s (warning first). */
    public static boolean confirmEat(ServerPlayer sp, ItemStack stack) {
        CharacterData d = Characters.get(sp);
        if (!d.created) return false;
        if (d.fruit == null) return true;
        long now = sp.level().getGameTime();
        Long warned = WARNED.get(sp.getUUID());
        if (warned != null && now - warned < 100) return true;
        WARNED.put(sp.getUUID(), now);
        sp.sendSystemMessage(Component.translatable("grandline.fruit.second_warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        return false;
    }

    public static void eat(ServerPlayer sp, DevilFruit f) {
        CharacterData d = Characters.get(sp);
        FruitWorldData ledger = FruitWorldData.get(sp.server);
        if (d.fruit != null) {
            // canon: the body cannot hold two fruits
            WARNED.remove(sp.getUUID());
            ledger.itemDestroyed(f.id);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.6f, 1.4f);
            sp.hurt(sp.damageSources().genericKill(), Float.MAX_VALUE);
            return;
        }
        d.fruit = f.id;
        d.fruitAwakened = false;
        d.zoanForm = "";
        if (!GLConfig.ALLOW_DUPLICATE_FRUITS.get()) ledger.eaten(f.id, sp.getUUID(), d.name);
        sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
        sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
        sp.sendSystemMessage(Component.translatable("grandline.fruit.eaten", Component.translatable(f.translationKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
        Loadouts.refresh(sp, d);
        CharacterManager.markVisualsDirty(sp);
    }

    /** Admin/testing: give a fruit item, respecting uniqueness. */
    public static ItemStack create(net.minecraft.server.MinecraftServer server, String id) {
        if (!FruitWorldData.get(server).claimSpecific(id)) return ItemStack.EMPTY;
        return DevilFruitItem.of(id);
    }

    private FruitManager() {}
}
