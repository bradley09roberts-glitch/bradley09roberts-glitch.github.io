package com.terracraft.registry.content;

import com.terracraft.block.CraftingStationBlock;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.consumable.EventSummonItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.event.TerrariaEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import com.terracraft.registry.RegistryObject;

/** Goblin Army drops, its summoning standard and the Goblin Tinkerer's workshop. */
public final class GoblinContent {
    public static final RegistryObject<TerraItem> TATTERED_CLOTH = CoreItems.material("tattered_cloth", TerraRarity.WHITE, 500);
    public static final RegistryObject<EventSummonItem> GOBLIN_BATTLE_STANDARD = ModItems.register("goblin_battle_standard", TabGroup.CONSUMABLES,
        p -> new EventSummonItem(p, () -> TerrariaEvents.GOBLIN_ARMY), p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.BLUE, 0)));
    /** Tinkerer's Workshop: combines accessories (recipes with the {@code tinkerers_workshop} station). */
    public static final RegistryObject<CraftingStationBlock> TINKERERS_WORKSHOP = ModBlocks.register("tinkerers_workshop", TabGroup.BLOCKS,
        p -> new CraftingStationBlock(p, Block.box(0, 0, 0, 16, 13, 16)),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion());

    private GoblinContent() {}

    public static void init() {}
}
