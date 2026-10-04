package com.terracraft.registry.content;

import com.terracraft.block.CloudBlock;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.accessory.WingsItem;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.MeleeWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.SkyfallSwordItem;
import com.terracraft.item.weapon.ThrownWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The Desert and Underground Desert, the Snow biome and Ice caverns, and the Floating Islands: their blocks,
 * materials and treasures (chest loot, enemy drops and what they craft).
 */
public final class BiomeContent {
    // --- blocks -------------------------------------------------------------------------------------------
    public static final RegistryObject<Block> HARDENED_SAND = ModBlocks.register("hardened_sand", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.0F).sound(SoundType.SAND));
    public static final RegistryObject<Block> DESERT_FOSSIL = ModBlocks.register("desert_fossil", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F).sound(SoundType.BONE_BLOCK).requiresCorrectToolForDrops());
    public static final RegistryObject<CloudBlock> CLOUD = ModBlocks.register("cloud", TabGroup.BLOCKS, CloudBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> SUNPLATE_BLOCK = ModBlocks.register("sunplate_block", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // --- Desert -------------------------------------------------------------------------------------------
    public static final RegistryObject<TerraItem> ANTLION_MANDIBLE = CoreItems.material("antlion_mandible", TerraRarity.WHITE, 100);
    public static final RegistryObject<TerraItem> STURDY_FOSSIL = CoreItems.material("sturdy_fossil", TerraRarity.BLUE, 300);
    public static final RegistryObject<TerraItem> FORBIDDEN_FRAGMENT = CoreItems.material("forbidden_fragment", TerraRarity.PINK, 2000);
    public static final RegistryObject<MeleeWeaponItem> MANDIBLE_BLADE = WeaponContent.sword("mandible_blade", 16, 18, 6.0F, 4, TerraRarity.BLUE, 5400);
    public static final ArmorContent.ArmorPieces FOSSIL = ArmorContent.named("fossil", new String[]{"fossil_helm", "fossil_plate", "fossil_greaves"},
        new int[]{3, 6, 3},
        new StatEffects[]{
            StatEffects.builder().add(Stat.RANGED_CRIT, 4).build(),
            StatEffects.builder().add(Stat.RANGED_DAMAGE, 0.05F).build(),
            StatEffects.builder().add(Stat.RANGED_CRIT, 4).build()},
        StatEffects.builder().add(Stat.AMMO_CONSERVATION, 0.20F).add(Stat.RANGED_DAMAGE, 0.05F).build(), TerraRarity.BLUE, 6000);
    public static final RegistryObject<AccessoryItem> SANDSTORM_IN_A_BOTTLE = accessory("sandstorm_in_a_bottle", TerraRarity.GREEN, 20000,
        StatEffects.builder().add(Stat.EXTRA_JUMPS, 1).add(Stat.JUMP_HEIGHT, 0.15F));
    public static final RegistryObject<AccessoryItem> ANCIENT_CHISEL = accessory("ancient_chisel", TerraRarity.GREEN, 20000,
        StatEffects.builder().add(Stat.MINING_SPEED, 0.25F));
    public static final RegistryObject<MagicWeaponItem> SPIRIT_FLAME = ModItems.register("spirit_flame", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.SPIRIT_FLAME, 2, 12.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(35).mana(12).useTime(28).knockback(3.0F).crit(4)
            .velocity(6.0F).rarity(TerraRarity.PINK).value(60000).build()));

    // --- Snow and Ice -------------------------------------------------------------------------------------
    public static final RegistryObject<TerraItem> FROST_CORE = CoreItems.material("frost_core", TerraRarity.PINK, 10000);
    public static final RegistryObject<AccessoryItem> ICE_SKATES = accessory("ice_skates", TerraRarity.GREEN, 10000,
        StatEffects.builder().add(Stat.MOVE_SPEED, 0.08F));
    public static final RegistryObject<AccessoryItem> BLIZZARD_IN_A_BOTTLE = accessory("blizzard_in_a_bottle", TerraRarity.GREEN, 20000,
        StatEffects.builder().add(Stat.EXTRA_JUMPS, 1).add(Stat.JUMP_HEIGHT, 0.2F));
    public static final RegistryObject<AccessoryItem> FLURRY_BOOTS = accessory("flurry_boots", TerraRarity.GREEN, 20000,
        StatEffects.builder().add(Stat.MOVE_SPEED, 0.25F));
    public static final RegistryObject<ProjectileSwordItem> ICE_BLADE = ModItems.register("ice_blade", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.ICE_BOLT, 0.8F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(17).useTime(20).knockback(4.0F).crit(4).velocity(10.0F)
            .rarity(TerraRarity.BLUE).value(10000).build()));
    public static final RegistryObject<ThrownWeaponItem> ICE_BOOMERANG = WeaponContent.thrown("ice_boomerang", ProjectileKinds.ICE_BOOMERANG, false,
        16, 15, 8.0F, 9.0F, 10000);
    public static final RegistryObject<ProjectileSwordItem> FROSTBRAND = ModItems.register("frostbrand", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.ICE_BOLT, 0.9F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(49).useTime(23).knockback(4.5F).crit(4).velocity(12.0F)
            .rarity(TerraRarity.LIGHT_RED).value(50000).build()));
    public static final RegistryObject<MagicWeaponItem> FROST_STAFF = ModItems.register("frost_staff", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.FROST_BOLT, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(46).mana(9).useTime(20).knockback(5.0F).crit(4)
            .velocity(11.0F).rarity(TerraRarity.LIGHT_RED).value(50000).build()));
    public static final ArmorContent.ArmorPieces FROST = ArmorContent.named("frost", new String[]{"frost_helmet", "frost_breastplate", "frost_leggings"},
        new int[]{10, 20, 13},
        new StatEffects[]{
            StatEffects.builder().add(Stat.MELEE_DAMAGE, 0.16F).add(Stat.RANGED_DAMAGE, 0.16F).build(),
            StatEffects.builder().add(Stat.MELEE_CRIT, 11).add(Stat.RANGED_CRIT, 11).build(),
            StatEffects.builder().add(Stat.MELEE_SPEED, 0.08F).add(Stat.MOVE_SPEED, 0.10F).build()},
        StatEffects.builder().add(Stat.MELEE_DAMAGE, 0.10F).add(Stat.RANGED_DAMAGE, 0.10F).build(), TerraRarity.PINK, 60000);

    // --- Floating Islands ---------------------------------------------------------------------------------
    public static final RegistryObject<TerraItem> FEATHER = CoreItems.material("feather", TerraRarity.WHITE, 50);
    public static final RegistryObject<TerraItem> GIANT_HARPY_FEATHER = CoreItems.material("giant_harpy_feather", TerraRarity.PINK, 10000);
    public static final RegistryObject<SkyfallSwordItem> STARFURY = ModItems.register("starfury", TabGroup.WEAPONS,
        p -> new SkyfallSwordItem(p, () -> ProjectileKinds.STARFURY_STAR),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(22).useTime(20).knockback(5.0F).crit(4).velocity(12.0F)
            .rarity(TerraRarity.GREEN).value(20000).build()));
    public static final RegistryObject<WingsItem> HARPY_WINGS = ModItems.register("harpy_wings", TabGroup.ACCESSORIES,
        p -> new WingsItem(p, StatEffects.NONE, new WingsItem.Flight("harpy", 50, 0.45F)),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.PINK).value(200000).build()));

    private BiomeContent() {}

    public static void init() {}

    private static RegistryObject<AccessoryItem> accessory(String name, TerraRarity rarity, int value, StatEffects.Builder effects) {
        StatEffects built = effects.build();
        return ModItems.register(name, TabGroup.ACCESSORIES, p -> new AccessoryItem(p, built),
            p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(rarity).value(value).build()));
    }
}
