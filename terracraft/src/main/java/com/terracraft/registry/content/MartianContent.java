package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.event.EventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Martian Madness: after Golem, a Martian Probe now and then drifts over a player on the surface by day; if it
 * finishes its scan and gets away, the martians invade. The Martian Saucer drops these weapons.
 */
public final class MartianContent {
    public static final RegistryObject<ProjectileSwordItem> INFLUX_WAVER = ModItems.register("influx_waver", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.INFLUX_WAVE, 0.8F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(110).useTime(16).knockback(4.5F).crit(4).velocity(12.0F)
            .rarity(TerraRarity.YELLOW).value(300000).build()));
    public static final RegistryObject<MagicWeaponItem> LASER_MACHINEGUN = ModItems.register("laser_machinegun", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.LASER_BEAM, 1, 0.05F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(50).mana(3).useTime(6).knockback(2.0F).crit(4).velocity(16.0F)
            .rarity(TerraRarity.YELLOW).value(300000).build()));
    /** Xenopopper: a bubbly shotgun - four bullets per shot. */
    public static final RegistryObject<RangedWeaponItem> XENOPOPPER = ModItems.register("xenopopper", TabGroup.WEAPONS,
        p -> new RangedWeaponItem(p, AmmoType.BULLET, SoundEvents.BUBBLE_POP, 3.0F, 4, 0.1F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(50).useTime(18).knockback(3.0F).crit(4).velocity(12.0F)
            .rarity(TerraRarity.YELLOW).value(300000).build()));

    private MartianContent() {}

    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MartianContent::onServerTick);
    }

    /** By day after Golem, about once every 25 minutes per world, a probe comes looking. */
    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 600 != 300 || !ProgressionManager.has(server, ProgressionFlags.GOLEM) || EventManager.active(server) != null) {
            return;
        }
        ServerLevel level = server.overworld();
        if (!level.isBrightOutside() || level.getRandom().nextInt(50) != 0
            || !level.getEntities(MobContent.MARTIAN_PROBE.get(), e -> true).isEmpty()) {
            return;
        }
        var players = level.players().stream().filter(p -> !p.isSpectator() && level.canSeeSky(p.blockPosition())
            && !com.terracraft.world.spawn.SafeZones.isSafe(server, p.getBlockX(), p.getBlockY(), p.getBlockZ())).toList();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
        var probe = MobContent.MARTIAN_PROBE.get().create(level, EntitySpawnReason.EVENT);
        if (probe != null) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            probe.snapTo(player.getX() + Math.cos(a) * 35, player.getY() + 15, player.getZ() + Math.sin(a) * 35, 0, 0);
            probe.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(probe);
        }
    }
}
