package com.terracraft.world.evil;

import com.terracraft.TerraCraft;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Smashing Shadow Orbs / Crimson Hearts: each one drops a treasure (loot tables
 * {@code terracraft:gameplay/shadow_orb} and {@code terracraft:gameplay/crimson_heart}), counts toward the
 * world counter {@code orbs_smashed}, and every third one awakens the Eater of Worlds or Brain of Cthulhu
 * (via {@link #bossSummoner}).
 */
public final class OrbSmashing {
    public static final Identifier COUNTER = TerraCraft.id("orbs_smashed");
    private static final ResourceKey<LootTable> SHADOW_ORB_LOOT = ResourceKey.create(Registries.LOOT_TABLE, TerraCraft.id("gameplay/shadow_orb"));
    private static final ResourceKey<LootTable> CRIMSON_HEART_LOOT = ResourceKey.create(Registries.LOOT_TABLE, TerraCraft.id("gameplay/crimson_heart"));

    /** Spawns the evil boss for the given player (set by the boss package). */
    public interface BossSummoner {
        void summon(ServerLevel level, Player player, boolean crimson);
    }

    public static BossSummoner bossSummoner = (level, player, crimson) -> {};

    private OrbSmashing() {}

    public static void onSmashed(ServerLevel level, BlockPos pos, boolean crimson, Player player) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(crimson ? CRIMSON_HEART_LOOT : SHADOW_ORB_LOOT);
        table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY),
            stack -> level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack)));
        level.playSound(null, pos, crimson ? SoundEvents.SLIME_DEATH : SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
        boolean firstEver = ProgressionManager.set(level.getServer(), ProgressionFlags.ORB_SMASHED, true);
        int count = ProgressionManager.incrementCounter(level.getServer(), COUNTER, 1);
        if (count % 3 == 0) {
            bossSummoner.summon(level, player, crimson);
        } else if (!firstEver) {
            // (the very first smash already shows the world announcement for the flag)
            level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                count % 3 == 1 ? "message.terracraft.orb.first" : "message.terracraft.orb.second").withStyle(ChatFormatting.DARK_PURPLE), false);
        }
    }
}
