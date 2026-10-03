package com.terracraft.registry.content;

import com.terracraft.block.LarvaBlock;
import com.terracraft.block.PlantBlock;
import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.consumable.BossSummonItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import com.terracraft.registry.RegistryObject;

/** Blocks and materials of the Jungle (worldgen: {@code world.jungle.JungleFeature}). Mud is vanilla mud. */
public final class JungleContent {
    public static final RegistryObject<Block> JUNGLE_GRASS = ModBlocks.register("jungle_grass", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.6F).sound(SoundType.WET_GRASS));
    /** Glowing Jungle Spores: the jungle's crafting material, picked from cave floors. */
    public static final RegistryObject<PlantBlock> JUNGLE_SPORES = ModBlocks.register("jungle_spores_plant", TabGroup.MATERIALS, PlantBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).noCollision().instabreak().sound(SoundType.GRASS).noOcclusion()
            .lightLevel(s -> 6).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> HIVE = ModBlocks.register("hive", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.8F).sound(SoundType.CORAL_BLOCK));
    public static final RegistryObject<LarvaBlock> LARVA = ModBlocks.register("larva", TabGroup.BLOCKS, LarvaBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.SLIME_BLOCK).noOcclusion()
            .pushReaction(PushReaction.BLOCK));

    public static final RegistryObject<TerraItem> JUNGLE_SPORES_ITEM = CoreItems.material("jungle_spores", TerraRarity.WHITE, 120);
    public static final RegistryObject<TerraItem> STINGER = CoreItems.material("stinger", TerraRarity.WHITE, 200);
    public static final RegistryObject<TerraItem> BEE_WAX = CoreItems.material("bee_wax", TerraRarity.GREEN, 500);
    public static final RegistryObject<BossSummonItem> ABEEMINATION = ModItems.register("abeemination", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.QUEEN_BEE, BossSummoning.Arrival.OFFSCREEN, false, JungleContent::inJungle),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.BLUE, 0)));

    private JungleContent() {}

    public static void init() {}

    /** The Abeemination only works in the Jungle. */
    public static boolean inJungle(ServerPlayer player) {
        return com.terracraft.world.jungle.JungleFeature.isJungle(player.level(), player.getBlockX(), player.getBlockZ());
    }
}
