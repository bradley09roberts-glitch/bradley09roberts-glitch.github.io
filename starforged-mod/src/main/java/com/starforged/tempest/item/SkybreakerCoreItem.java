package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormgateBuilder;
import com.starforged.tempest.world.StormreachTravel;
import com.starforged.util.Fx;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Heart of the Moon caged in charged Moonsilver: it pulls toward the storm.
 * Use it on the ground (Overworld, Stormreach, or the Pale Reach at High Tide) to raise a Stormgate.
 */
public class SkybreakerCoreItem extends Item {
    public SkybreakerCoreItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            if (!StormreachTravel.canOpenGate(server)) {
                player.sendOverlayMessage(Component.translatable("item.starforged.skybreaker_core.wrong_world").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            BlockPos center = context.getClickedPos().above();
            // Push the gate out in front of the player so it never forms underneath them: they step in when ready.
            BlockPos feet = player.blockPosition();
            Direction facing = player.getDirection();
            while (Math.max(Math.abs(center.getX() - feet.getX()), Math.abs(center.getZ() - feet.getZ())) < 3) {
                center = center.relative(facing);
            }
            StormgateBuilder.build(server, center);
            Vec3 c = Vec3.atBottomCenterOf(center);
            Fx.column(server, ModParticles.STATIC_SPARK.get(), c, 10.0, 120, 1.2, 0.2);
            Fx.ring(server, ModParticles.STORM_WISP.get(), c.add(0, 0.2, 0), 2.5, 40, 0.3, 0.05);
            server.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFB8DCFF), c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            server.playSound(null, c.x, c.y, c.z, TempestSounds.GATEWAY_FORM.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
            Fx.shake(server, c, 24.0, 0.8F, 20);
            player.sendOverlayMessage(Component.translatable("item.starforged.skybreaker_core.opened").withStyle(ChatFormatting.AQUA));
            context.getItemInHand().consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addLore(this.getDescriptionId(), 2, builder);
        LoreItem.addAbility(builder, "ability.starforged.skybreaker_core");
    }
}
