package com.terracraft.item.weapon;

import com.terracraft.item.TerraItem;

/** A stackable ammunition item (arrows, musket balls...). */
public class AmmoItem extends TerraItem {
    private final AmmoInfo ammo;

    public AmmoItem(Properties properties, AmmoInfo ammo) {
        super(properties);
        this.ammo = ammo;
    }

    public AmmoInfo ammo() {
        return ammo;
    }
}
