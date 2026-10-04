package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Chlorophyte: a green ore deep in the jungle mud (placed in Hardmode by {@code HardmodeWorld}; needs pickaxe power
 * 200, the Pickaxe Axe), its bars (Hardmode forge) and gear. The armor's set bonus is a Leaf Crystal that fires leaves
 * at nearby enemies.
 */
public final class ChlorophyteContent {
    public static final RegistryObject<Block> CHLOROPHYTE_ORE = ModBlocks.register("chlorophyte_ore", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).requiresCorrectToolForDrops().strength(6.0F, 8.0F)
            .sound(SoundType.STONE).lightLevel(s -> 3));
    public static final RegistryObject<TerraItem> RAW_CHLOROPHYTE = ModItems.register("raw_chlorophyte", TabGroup.MATERIALS, TerraItem::new,
        p -> WeaponProperties.stats(p, CoreItems.stats(TerraRarity.LIME, 1500)));
    public static final RegistryObject<TerraItem> CHLOROPHYTE_BAR = ModItems.register("chlorophyte_bar", TabGroup.MATERIALS, TerraItem::new,
        p -> WeaponProperties.stats(p, CoreItems.stats(TerraRarity.LIME, 9000)));

    /** Chlorophyte Claymore: a heavy sword that launches a slow, homing chlorophyte orb with each swing. */
    public static final RegistryObject<ProjectileSwordItem> CHLOROPHYTE_CLAYMORE = ModItems.register("chlorophyte_claymore", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.CHLOROPHYTE_ORB, 1.0F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(95).useTime(26).knockback(6.0F).crit(4).velocity(4.0F)
            .rarity(TerraRarity.LIME).value(276000).build()));
    /** Chlorophyte Shotbow: fires a spread of arrows. */
    public static final RegistryObject<RangedWeaponItem> CHLOROPHYTE_SHOTBOW = ModItems.register("chlorophyte_shotbow", TabGroup.WEAPONS,
        p -> new RangedWeaponItem(p, AmmoType.ARROW, SoundEvents.CROSSBOW_SHOOT, 2.0F, 3, 0.12F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(40).useTime(19).knockback(2.0F).velocity(11.0F)
            .rarity(TerraRarity.LIME).value(276000).build()));
    public static final RegistryObject<com.terracraft.item.tool.TerrariaToolItem> CHLOROPHYTE_PICKAXE =
        ToolContent.hardmodePickaxe("chlorophyte_pickaxe", 200, 40, TerraRarity.LIME, 216000);

    public static final ArmorContent.ArmorPieces CHLOROPHYTE = ArmorContent.named("chlorophyte",
        new String[]{"chlorophyte_helmet", "chlorophyte_plate_mail", "chlorophyte_greaves"}, new int[]{15, 24, 18},
        new StatEffects[]{StatEffects.builder().add(Stat.DAMAGE, 0.10F).build(),
            StatEffects.builder().add(Stat.DAMAGE, 0.05F).add(Stat.CRIT, 7).build(),
            StatEffects.builder().add(Stat.CRIT, 8).add(Stat.MOVE_SPEED, 0.05F).build()},
        StatEffects.builder().ability(Ability.LEAF_CRYSTAL).build(), TerraRarity.LIME, 60000);

    private ChlorophyteContent() {}

    public static void init() {}
}
