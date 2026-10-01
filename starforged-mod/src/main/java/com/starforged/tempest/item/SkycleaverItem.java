package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.event.TempestAbilities;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Skycleaver, Veyr's own greatsword. Right-click for <b>Heaven's Judgement</b>: lightning falls in a line out to eighteen
 * blocks in front of you, one bolt after another.
 */
public class SkycleaverItem extends Item {
    public SkycleaverItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            for (int i = 0; i < 8; i++) {
                Vec3 at = player.position().add(flat.scale(3.0 + i * 2.2));
                int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
                double y = Math.abs(ground - player.getY()) < 5 ? ground : player.getY();
                TempestAbilities.scheduleStrike(server, 2 + i * 3, new Vec3(at.x, y, at.z), player, 14.0F, 2.5);
            }
            server.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.JUDGEMENT.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            player.getCooldowns().addCooldown(stack, 120);
            player.swing(hand, true);
            stack.hurtAndBreak(3, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.heavens_judgement");
    }
}
