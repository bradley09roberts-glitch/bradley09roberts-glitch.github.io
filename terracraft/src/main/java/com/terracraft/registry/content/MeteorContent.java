package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.SpaceGunItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import com.terracraft.registry.RegistryObject;

/** Meteorite (lands in craters, see {@code world.MeteorManager}), its bar and the Space Gun. Meteor armor lives in ArmorContent. */
public final class MeteorContent {
    /** Meteorite: 50% pickaxe power (data: mining_power/meteorite), glows faintly. */
    public static final RegistryObject<Block> METEORITE = ModBlocks.register("meteorite", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).requiresCorrectToolForDrops().strength(3.5F, 9.0F).sound(SoundType.ANCIENT_DEBRIS)
            .lightLevel(s -> 5));
    public static final RegistryObject<TerraItem> METEORITE_BAR = CoreItems.material("meteorite_bar", TerraRarity.BLUE, 1400);
    public static final RegistryObject<SpaceGunItem> SPACE_GUN = ModItems.register("space_gun", TabGroup.WEAPONS,
        p -> new SpaceGunItem(p, ProjectileKinds.SPACE_LASER),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(17).mana(6).useTime(16).knockback(0.0F).velocity(12.0F)
            .rarity(TerraRarity.BLUE).value(20000).build()));

    private MeteorContent() {}

    public static void init() {}
}
