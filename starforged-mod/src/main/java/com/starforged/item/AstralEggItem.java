package com.starforged.item;

import com.starforged.entity.animal.StarlingEntity;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Fx;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An egg of pure starlight, found in hollow meteors and observatory vaults. Hatches into a Starling that is bonded to you.
 */
public class AstralEggItem extends Item {
    public AstralEggItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Vec3 pos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        return hatch(context.getLevel(), player, context.getItemInHand(), pos);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 pos = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)).add(0, 0.5, 0);
        return hatch(level, player, player.getItemInHand(hand), pos);
    }

    private static InteractionResult hatch(Level level, Player player, ItemStack stack, Vec3 pos) {
        if (level instanceof ServerLevel server) {
            StarlingEntity starling = ModEntities.STARLING.get().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (starling == null) {
                return InteractionResult.FAIL;
            }
            starling.snapTo(pos.x, pos.y, pos.z, player.getYRot() + 180.0F, 0.0F);
            starling.tame(player);
            starling.setHealth(starling.getMaxHealth());
            server.addFreshEntity(starling);
            Fx.burst(server, ParticleTypes.EGG_CRACK, pos, 12, 0.2, 0.05);
            Fx.burst(server, ModParticles.STAR_SPARKLE.get(), pos.add(0, 0.4, 0), 40, 0.4, 0.12);
            Fx.burst(server, ParticleTypes.HEART, pos.add(0, 0.8, 0), 5, 0.4, 0.05);
            server.playSound(null, pos.x, pos.y, pos.z, ModSounds.EGG_HATCH.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("item.starforged.astral_egg.hatched"), true);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addLore(this.getDescriptionId(), 2, builder);
    }
}
