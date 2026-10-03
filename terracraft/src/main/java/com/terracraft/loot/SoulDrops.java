package com.terracraft.loot;

import com.terracraft.entity.mob.FlyerMob;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.AccessoryContent;
import com.terracraft.world.evil.EvilZones;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * Hardmode souls, the wing materials:
 * <ul>
 *     <li>Soul of Night: enemies killed underground inside the Corruption/Crimson (1 in 5);</li>
 *     <li>Soul of Light: enemies killed underground elsewhere (1 in 5), standing in for the Underground Hallow
 *     until the Hallow arrives with Stage 5;</li>
 *     <li>Soul of Flight: flying enemies killed high in the sky, above y=150 (1 in 3; Terraria's Wyverns).</li>
 * </ul>
 */
public final class SoulDrops {
    private SoulDrops() {}

    public static void register() {
        LivingDropsEvent.BUS.addListener(SoulDrops::onDrops);
    }

    private static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || !(entity.level() instanceof ServerLevel level) || !ProgressionManager.isHardmode(level.getServer())) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player) && !event.isRecentlyHit()) {
            return;
        }
        var random = entity.getRandom();
        if (entity.getY() < 40 && random.nextInt(5) == 0) {
            // Terraria: Souls of Light from the underground Hallow, Souls of Night from the underground Corruption/Crimson
            var stripe = com.terracraft.world.hardmode.HardmodeWorld.stripe(level.getSeed(), entity.getX(), entity.getZ());
            if (stripe == com.terracraft.world.hardmode.HardmodeWorld.Infection.HALLOW) {
                drop(event, entity, AccessoryContent.SOUL_OF_LIGHT.get(), 1);
            } else if (stripe == com.terracraft.world.hardmode.HardmodeWorld.Infection.EVIL || EvilZones.isEvil(level.getSeed(), entity.getX(), entity.getZ())) {
                drop(event, entity, AccessoryContent.SOUL_OF_NIGHT.get(), 1);
            }
        }
        if (entity instanceof FlyerMob && entity.getY() > 150 && random.nextInt(3) == 0) {
            drop(event, entity, AccessoryContent.SOUL_OF_FLIGHT.get(), 1 + random.nextInt(2));
        }
    }

    private static void drop(LivingDropsEvent event, LivingEntity entity, Item item, int count) {
        event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(item, count)));
    }
}
