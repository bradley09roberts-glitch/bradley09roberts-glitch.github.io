package com.terracraft.item.accessory;

import com.terracraft.item.TerraItem;
import com.terracraft.player.stats.StatEffects;

/**
 * An accessory. Its effects are a {@link StatEffects} bundle applied while it sits in an accessory slot.
 * Only one copy of each accessory counts (Terraria forbids equipping duplicates).
 */
public class AccessoryItem extends TerraItem {
    private final StatEffects effects;

    public AccessoryItem(Properties properties, StatEffects effects) {
        super(properties.stacksTo(1));
        this.effects = effects;
    }

    public StatEffects effects() {
        return effects;
    }
}
