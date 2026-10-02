package com.terracraft.item.coin;

import com.terracraft.item.TerraItem;

/** A Terraria coin. {@link #value} is its worth in copper coins. */
public class CoinItem extends TerraItem {
    private final long value;

    public CoinItem(Properties properties, long value) {
        super(properties.stacksTo(99));
        this.value = value;
    }

    public long value() {
        return value;
    }
}
