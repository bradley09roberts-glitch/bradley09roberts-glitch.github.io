package com.terracraft.item.consumable;

import com.terracraft.item.TerraItem;
import com.terracraft.player.ManaManager;
import com.terracraft.player.PlayerEvents;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Terraria potions: healing (with Potion Sickness), mana restoration (with Mana Sickness) and timed buffs.
 * Drinking is quicker than vanilla potions (half a second).
 */
public class TerraPotionItem extends TerraItem {
    /** Terraria: 60 seconds of Potion Sickness after a healing potion. */
    public static final int POTION_SICKNESS_TICKS = 60 * 20;
    public static final int MANA_SICKNESS_TICKS = 5 * 20;

    private final int heal;
    private final int mana;
    @Nullable
    private final RegistryObject<MobEffect> buff;
    private final int buffTicks;

    public TerraPotionItem(Properties properties, int heal, int mana, @Nullable RegistryObject<MobEffect> buff, int buffTicks) {
        super(properties);
        this.heal = heal;
        this.mana = mana;
        this.buff = buff;
        this.buffTicks = buffTicks;
    }

    public static Item.Properties drink(Item.Properties properties) {
        return properties.stacksTo(30).component(DataComponents.CONSUMABLE, Consumables.defaultDrink().consumeSeconds(0.5F).build());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (heal > 0 && player.hasEffect(ModEffects.holder(ModEffects.POTION_SICKNESS))) {
            return InteractionResult.FAIL;
        }
        if (heal > 0 && player.getHealth() >= player.getMaxHealth() && mana <= 0 && buff == null) {
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            if (heal > 0) {
                player.heal(heal);
                player.addEffect(new MobEffectInstance(ModEffects.holder(ModEffects.POTION_SICKNESS), POTION_SICKNESS_TICKS));
            }
            if (mana > 0) {
                TerraPlayerData data = TerraPlayerData.get(player);
                ManaManager.restore(data, mana);
                player.addEffect(new MobEffectInstance(ModEffects.holder(ModEffects.MANA_SICKNESS), MANA_SICKNESS_TICKS));
            }
            if (buff != null) {
                Holder<MobEffect> holder = ModEffects.holder(buff);
                player.addEffect(new MobEffectInstance(holder, buffTicks));
            }
            PlayerEvents.refreshAndSync(player);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
