package com.terracraft.player;

import com.terracraft.registry.ModEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Terraria has no hunger: the food bar is held just below full (so food can always be eaten and sprinting
 * always works) and hidden from the HUD. Eating gives Terraria's food buffs instead: Well Fed, Plenty Satisfied
 * or Exquisitely Stuffed depending on how filling the food is, lasting longer for bigger meals. A buff of a
 * higher tier replaces a lower one, and eating the same tier again refreshes it if the new one is longer.
 */
public final class FoodManager {
    private static final int HELD_FOOD_LEVEL = 19;

    private FoodManager() {}

    public static void register() {
        LivingEntityUseItemEvent.Finish.BUS.addListener(FoodManager::onEaten);
    }

    public static void tick(ServerPlayer player) {
        var food = player.getFoodData();
        if (food.getFoodLevel() != HELD_FOOD_LEVEL) {
            food.setFoodLevel(HELD_FOOD_LEVEL);
        }
        if (food.getSaturationLevel() != 0.0F) {
            food.setSaturation(0.0F);
        }
    }

    public static boolean isWellFed(Player player) {
        return player.hasEffect(ModEffects.holder(ModEffects.WELL_FED)) || player.hasEffect(ModEffects.holder(ModEffects.PLENTY_SATISFIED))
            || player.hasEffect(ModEffects.holder(ModEffects.EXQUISITELY_STUFFED));
    }

    private static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null) {
            return;
        }
        // tier by how filling the food is; duration 5-30 minutes like Terraria's foods
        int tier = food.nutrition() >= 8 ? 3 : food.nutrition() >= 5 ? 2 : 1;
        int minutes = Math.min(30, 4 + food.nutrition() * 3);
        var buff = switch (tier) {
            case 3 -> ModEffects.EXQUISITELY_STUFFED;
            case 2 -> ModEffects.PLENTY_SATISFIED;
            default -> ModEffects.WELL_FED;
        };
        var buffs = java.util.List.of(ModEffects.WELL_FED, ModEffects.PLENTY_SATISFIED, ModEffects.EXQUISITELY_STUFFED);
        for (int t = 0; t < buffs.size(); t++) {
            var other = ModEffects.holder(buffs.get(t));
            MobEffectInstance existing = player.getEffect(other);
            if (existing == null) {
                continue;
            }
            if (t + 1 > tier) {
                return;   // already better fed
            }
            if (t + 1 < tier) {
                player.removeEffect(other);
            }
        }
        player.addEffect(new MobEffectInstance(ModEffects.holder(buff), minutes * 60 * 20, 0, false, true, true));
    }
}
