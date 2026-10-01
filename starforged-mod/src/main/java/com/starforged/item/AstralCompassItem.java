package com.starforged.item;

import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.registry.ModTags;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Astral Compass: right-click to attune it to the nearest Fallen Observatory. Its needle then points the way
 * (it uses the vanilla lodestone tracking, so the needle really turns), and a trail of stars briefly lights the path.
 */
public class AstralCompassItem extends Item {
    public AstralCompassItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            player.getCooldowns().addCooldown(stack, 60);
            BlockPos found = server.findNearestMapStructure(ModTags.OBSERVATORIES, player.blockPosition(), 100, false);
            if (found == null) {
                player.sendOverlayMessage(Component.translatable("item.starforged.astral_compass.none").withStyle(ChatFormatting.GRAY));
                server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COMPASS_PING.get(), SoundSource.PLAYERS, 0.6F, 0.5F);
                return InteractionResult.FAIL;
            }
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(server.dimension(), found)), false));

            Vec3 to = Vec3.atCenterOf(found).subtract(player.position());
            int distance = (int) Math.sqrt(to.x * to.x + to.z * to.z);
            String dir = direction(to);
            player.sendOverlayMessage(Component.translatable("item.starforged.astral_compass.found", distance,
                Component.translatable("direction.starforged." + dir)).withStyle(ChatFormatting.AQUA));
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COMPASS_PING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

            Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
            Vec3 start = player.position().add(0, 1.2, 0);
            for (int i = 2; i < 40; i++) {
                Vec3 p = start.add(flat.scale(i * 0.8)).add(0, Math.sin(i * 0.5) * 0.25, 0);
                server.sendParticles(player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                    ModParticles.STAR_SPARKLE.get(), true, true, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static String direction(Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(-to.x, to.z));
        angle = (angle + 360.0) % 360.0;
        String[] names = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
        return names[(int) Math.round(angle / 45.0) % 8];
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.astral_compass");
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null && tracker.target().isPresent()) {
            BlockPos pos = tracker.target().get().pos();
            builder.accept(Component.translatable("item.starforged.astral_compass.tracking", pos.getX(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        }
    }
}
