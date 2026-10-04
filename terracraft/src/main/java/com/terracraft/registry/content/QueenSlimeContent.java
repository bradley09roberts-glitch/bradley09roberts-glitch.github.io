package com.terracraft.registry.content;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.consumable.BossSummonItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.hardmode.HardmodeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The underground Hallow's crystals and Queen Slime: Crystal Shards grow on cave walls in the Hallow (placed by
 * {@link HardmodeWorld}), rarer Gelatin Crystals break into the item that summons Queen Slime there, and she drops
 * Crystal Assassin armor and Volatile Gelatin.
 */
public final class QueenSlimeContent {
    /** Crystal Shard: a glowing crystal cluster on any face; mined (or placed) as the material of the same name. */
    public static final RegistryObject<AmethystClusterBlock> CRYSTAL_SHARD = ModBlocks.register("crystal_shard", TabGroup.MATERIALS,
        p -> new AmethystClusterBlock(7.0F, 3.0F, p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion().strength(1.0F)
            .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 6).pushReaction(PushReaction.DESTROY),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.LIGHT_RED).value(1500).build()));
    /** The pink crystal in which the Gelatin Crystal grows; breaking it drops the summon item. */
    public static final RegistryObject<AmethystClusterBlock> GELATIN_CRYSTAL_BLOCK = ModBlocks.registerNoItem("gelatin_crystal_block",
        p -> new AmethystClusterBlock(9.0F, 4.0F, p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).forceSolidOn().noOcclusion().strength(1.5F)
            .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 10).pushReaction(PushReaction.DESTROY));
    /** Gelatin Crystal: summons Queen Slime, only inside the Hallow. Not consumed in Terraria, but here it is (like other summons). */
    public static final RegistryObject<BossSummonItem> GELATIN_CRYSTAL = ModItems.register("gelatin_crystal", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.QUEEN_SLIME, BossSummoning.Arrival.FALL, false, QueenSlimeContent::inHallow),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.LIGHT_RED, 0)));
    /** Volatile Gelatin: now and then flings a bouncing gel ball at a nearby enemy. */
    public static final RegistryObject<AccessoryItem> VOLATILE_GELATIN = ModItems.register("volatile_gelatin", TabGroup.ACCESSORIES,
        p -> new AccessoryItem(p, StatEffects.builder().ability(Ability.VOLATILE_GELATIN).build()),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.PINK).value(40000).build()));

    private QueenSlimeContent() {}

    public static void init() {}

    /** Standing in the Hallow: enough Hallow blocks around the player. */
    private static boolean inHallow(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -6, -6), center.offset(6, 6, 6))) {
            if (HardmodeWorld.infectionOf(player.level().getBlockState(pos)) == HardmodeWorld.Infection.HALLOW && ++count >= 20) {
                return true;
            }
        }
        return false;
    }
}
