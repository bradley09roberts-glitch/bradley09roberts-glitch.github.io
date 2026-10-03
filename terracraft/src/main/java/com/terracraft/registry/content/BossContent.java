package com.terracraft.registry.content;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.consumable.BossSummonItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraftforge.registries.RegistryObject;

/** Boss summoning items and the materials bosses drop. */
public final class BossContent {
    public static final RegistryObject<BossSummonItem> SLIME_CROWN = ModItems.register("slime_crown", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.KING_SLIME, BossSummoning.Arrival.FALL, false),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.BLUE, 0)));
    public static final RegistryObject<BossSummonItem> SUSPICIOUS_LOOKING_EYE = ModItems.register("suspicious_looking_eye", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.EYE_OF_CTHULHU, BossSummoning.Arrival.OFFSCREEN, true),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.BLUE, 0)));

    // Demonite/Crimtane ore are blocks (EvilContent); Eye of Cthulhu also drops the ore of the world's evil.
    public static final RegistryObject<BossSummonItem> WORM_FOOD = ModItems.register("worm_food", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.EATER_OF_WORLDS, BossSummoning.Arrival.BURROW, false, BossContent::inCorruption),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.BLUE, 0)));
    public static final RegistryObject<BossSummonItem> BLOODY_SPINE = ModItems.register("bloody_spine", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.BRAIN_OF_CTHULHU, BossSummoning.Arrival.NEARBY, false, BossContent::inCrimson),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.BLUE, 0)));
    public static final RegistryObject<TerraItem> SHADOW_SCALE = CoreItems.material("shadow_scale", TerraRarity.BLUE, 100);
    public static final RegistryObject<TerraItem> TISSUE_SAMPLE = CoreItems.material("tissue_sample", TerraRarity.BLUE, 150);
    public static final RegistryObject<TerraItem> VILE_POWDER = CoreItems.material("vile_powder", TerraRarity.WHITE, 20);
    public static final RegistryObject<TerraItem> VICIOUS_POWDER = CoreItems.material("vicious_powder", TerraRarity.WHITE, 20);
    public static final RegistryObject<TerraItem> ROTTEN_CHUNK = CoreItems.material("rotten_chunk", TerraRarity.WHITE, 10);
    public static final RegistryObject<TerraItem> VERTEBRA = CoreItems.material("vertebra", TerraRarity.WHITE, 10);
    public static final RegistryObject<TerraItem> DEMONITE_BAR = CoreItems.material("demonite_bar", TerraRarity.BLUE, 3000);
    public static final RegistryObject<TerraItem> CRIMTANE_BAR = CoreItems.material("crimtane_bar", TerraRarity.BLUE, 3900);

    // Mechanical bosses (Hardmode, night only)
    public static final RegistryObject<BossSummonItem> MECHANICAL_WORM = ModItems.register("mechanical_worm", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.DESTROYER, BossSummoning.Arrival.BURROW, true),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.LIGHT_RED, 0)));
    public static final RegistryObject<BossSummonItem> MECHANICAL_EYE = ModItems.register("mechanical_eye", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.RETINAZER, BossSummoning.Arrival.OFFSCREEN, true),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.LIGHT_RED, 0)));
    public static final RegistryObject<BossSummonItem> MECHANICAL_SKULL = ModItems.register("mechanical_skull", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.SKELETRON_PRIME, BossSummoning.Arrival.OFFSCREEN, true),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.LIGHT_RED, 0)));
    public static final RegistryObject<TerraItem> SOUL_OF_MIGHT = CoreItems.material("soul_of_might", TerraRarity.PINK, 4000);
    public static final RegistryObject<TerraItem> SOUL_OF_SIGHT = CoreItems.material("soul_of_sight", TerraRarity.PINK, 4000);
    public static final RegistryObject<TerraItem> SOUL_OF_FRIGHT = CoreItems.material("soul_of_fright", TerraRarity.PINK, 4000);
    public static final RegistryObject<TerraItem> HALLOWED_BAR = CoreItems.material("hallowed_bar", TerraRarity.PINK, 8000);

    private BossContent() {}

    /** Evil boss summons only work inside the matching evil biome (standing on or near its blocks). */
    private static boolean inCorruption(net.minecraft.server.level.ServerPlayer player) {
        return nearEvil(player, net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, com.terracraft.TerraCraft.id("evil/corruption")));
    }

    private static boolean inCrimson(net.minecraft.server.level.ServerPlayer player) {
        return nearEvil(player, net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, com.terracraft.TerraCraft.id("evil/crimson")));
    }

    private static boolean nearEvil(net.minecraft.server.level.ServerPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        net.minecraft.core.BlockPos center = player.blockPosition();
        int count = 0;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(center.offset(-6, -6, -6), center.offset(6, 6, 6))) {
            if (player.level().getBlockState(pos).is(tag) && ++count >= 20) {
                return true;
            }
        }
        return false;
    }

    public static void init() {}
}
