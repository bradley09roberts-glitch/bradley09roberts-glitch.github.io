package com.starforged.sun.item;

import com.starforged.item.LoreItem;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
import com.starforged.sun.SunSounds;
import com.starforged.sun.entity.SolarPhoenixEntity;
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

/** A smouldering egg from the Sun Temple. Hatches a Solar Phoenix that is bonded to you - and rideable. */
public class PhoenixEggItem extends Item {
    public PhoenixEggItem(Item.Properties properties) {
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
        Vec3 pos = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5)).add(0, 0.5, 0);
        return hatch(level, player, player.getItemInHand(hand), pos);
    }

    private static InteractionResult hatch(Level level, Player player, ItemStack stack, Vec3 pos) {
        if (level instanceof ServerLevel server) {
            SolarPhoenixEntity phoenix = SunEntities.SOLAR_PHOENIX.get().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (phoenix == null) {
                return InteractionResult.FAIL;
            }
            phoenix.snapTo(pos.x, pos.y + 0.5, pos.z, player.getYRot() + 180.0F, 0.0F);
            phoenix.tame(player);
            phoenix.setHealth(phoenix.getMaxHealth());
            server.addFreshEntity(phoenix);
            Fx.column(server, ParticleTypes.FLAME, pos, 4.0, 80, 0.5, 0.12);
            Fx.sphere(server, ModParticles.SOLAR_SPARK.get(), pos.add(0, 1, 0), 0.3, 60, 0.35);
            Fx.burst(server, ParticleTypes.HEART, pos.add(0, 1.5, 0), 5, 0.4, 0.05);
            server.playSound(null, pos.x, pos.y, pos.z, SunSounds.EGG_HATCH.get(), SoundSource.NEUTRAL, 1.4F, 1.0F);
            player.sendOverlayMessage(Component.translatable("item.starforged.phoenix_egg.hatched"));
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
