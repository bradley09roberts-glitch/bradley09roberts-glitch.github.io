package com.terracraft.item.consumable;

import com.terracraft.config.TerraConfig;
import com.terracraft.item.TerraItem;
import com.terracraft.player.ManaManager;
import com.terracraft.player.PlayerEvents;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Life Crystal, Life Fruit and Mana Crystal: permanently raise maximum life or mana. */
public class PermanentUpgradeItem extends TerraItem {
    public enum Kind { LIFE_CRYSTAL, LIFE_FRUIT, MANA_CRYSTAL }

    private final Kind kind;

    public PermanentUpgradeItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        TerraPlayerData data = TerraPlayerData.get(serverPlayer);
        TerraConfig.Common config = TerraConfig.COMMON;
        boolean used = switch (kind) {
            case LIFE_CRYSTAL -> {
                if (data.lifeCrystals() >= config.maxLifeCrystals.get()) {
                    yield fail(serverPlayer, "message.terracraft.life_crystal.max");
                }
                data.setLifeCrystals(data.lifeCrystals() + 1);
                PlayerEvents.refreshAndSync(serverPlayer);
                serverPlayer.heal(config.lifeCrystalLife.get());
                yield true;
            }
            case LIFE_FRUIT -> {
                if (data.lifeCrystals() < config.maxLifeCrystals.get()) {
                    yield fail(serverPlayer, "message.terracraft.life_fruit.need_crystals");
                }
                if (data.lifeFruit() >= config.maxLifeFruit.get()) {
                    yield fail(serverPlayer, "message.terracraft.life_fruit.max");
                }
                data.setLifeFruit(data.lifeFruit() + 1);
                PlayerEvents.refreshAndSync(serverPlayer);
                serverPlayer.heal(config.lifeFruitLife.get());
                yield true;
            }
            case MANA_CRYSTAL -> {
                if (data.manaCrystals() >= config.maxManaCrystals.get()) {
                    yield fail(serverPlayer, "message.terracraft.mana_crystal.max");
                }
                data.setManaCrystals(data.manaCrystals() + 1);
                PlayerEvents.refreshAndSync(serverPlayer);
                ManaManager.restore(data, config.manaCrystalMana.get());
                yield true;
            }
        };
        if (!used) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, kind == Kind.MANA_CRYSTAL ? 1.6F : 1.2F);
        serverPlayer.level().sendParticles(kind == Kind.MANA_CRYSTAL ? ParticleTypes.ENCHANT : ParticleTypes.HEART,
            player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.4, 0.5, 0.4, 0.05);
        return InteractionResult.SUCCESS;
    }

    private static boolean fail(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
        return false;
    }
}
