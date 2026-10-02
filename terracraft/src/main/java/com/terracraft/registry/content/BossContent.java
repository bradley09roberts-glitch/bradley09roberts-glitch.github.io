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

    // Eye of Cthulhu drops the ore of the world's evil (until Corruption/Crimson worldgen exists, this is the only source)
    public static final RegistryObject<TerraItem> DEMONITE_ORE = CoreItems.material("demonite_ore", TerraRarity.BLUE, 1000);
    public static final RegistryObject<TerraItem> DEMONITE_BAR = CoreItems.material("demonite_bar", TerraRarity.BLUE, 3000);
    public static final RegistryObject<TerraItem> CRIMTANE_ORE = CoreItems.material("crimtane_ore", TerraRarity.BLUE, 1300);
    public static final RegistryObject<TerraItem> CRIMTANE_BAR = CoreItems.material("crimtane_bar", TerraRarity.BLUE, 3900);

    private BossContent() {}

    public static void init() {}
}
