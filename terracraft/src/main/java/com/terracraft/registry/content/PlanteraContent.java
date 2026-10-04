package com.terracraft.registry.content;

import com.terracraft.block.PlanteraBulbBlock;
import com.terracraft.block.PlantBlock;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Plantera and the Hardmode jungle: Plantera's Bulb (awakens her), the Life Fruit plant, the Temple Key she drops
 * (opens the Lihzahrd Temple) and her weapons.
 */
public final class PlanteraContent {
    public static final RegistryObject<PlanteraBulbBlock> PLANTERA_BULB = ModBlocks.registerNoItem("planteras_bulb", PlanteraBulbBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.0F).sound(SoundType.AZALEA).noOcclusion()
            .lightLevel(s -> 5).pushReaction(PushReaction.BLOCK));
    /** Life Fruit: a golden fruit on a little plant; picking it gives the item (grows after any mechanical boss). */
    public static final RegistryObject<PlantBlock> LIFE_FRUIT_PLANT = ModBlocks.registerNoItem("life_fruit_plant", PlantBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).noCollision().strength(0.3F).sound(SoundType.SWEET_BERRY_BUSH).noOcclusion()
            .lightLevel(s -> 4).pushReaction(PushReaction.DESTROY));

    public static final RegistryObject<TerraItem> TEMPLE_KEY = ModItems.register("temple_key", TabGroup.MATERIALS, TerraItem::new,
        p -> WeaponProperties.stats(p.stacksTo(99), CoreItems.stats(TerraRarity.LIME, 0)));

    /** Seedler: a vine sword that throws seeds with every swing. */
    public static final RegistryObject<ProjectileSwordItem> SEEDLER = ModItems.register("seedler", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.SEEDLER_SEED, 0.6F, 3, 0.15F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(65).useTime(20).knockback(5.25F).crit(4).velocity(9.0F)
            .rarity(TerraRarity.LIME).value(100000).build()));
    /** Venus Magnum: a rapid-firing flower-shaped pistol. */
    public static final RegistryObject<RangedWeaponItem> VENUS_MAGNUM = ModItems.register("venus_magnum", TabGroup.WEAPONS,
        p -> new RangedWeaponItem(p, AmmoType.BULLET, SoundEvents.CROSSBOW_SHOOT, 1.0F, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(50).useTime(8).knockback(3.0F).crit(4).velocity(15.0F)
            .rarity(TerraRarity.LIME).value(100000).build()));
    /** Leaf Blower: shoots a stream of razor leaves. */
    public static final RegistryObject<MagicWeaponItem> LEAF_BLOWER = ModItems.register("leaf_blower", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.CRYSTAL_LEAF, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(40).mana(4).useTime(7).knockback(4.0F).crit(4).velocity(13.0F)
            .rarity(TerraRarity.LIME).value(100000).build()));

    private PlanteraContent() {}

    public static void init() {}
}
