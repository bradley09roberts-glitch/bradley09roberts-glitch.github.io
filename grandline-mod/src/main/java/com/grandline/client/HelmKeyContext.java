package com.grandline.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Keys shared between the helm and the deck: at a ship's helm V/N are the gun orders, anywhere else they are the
 * crew roster and the quick crew order. The two contexts never overlap, so the Controls menu shows no conflict.
 */
public enum HelmKeyContext implements IKeyConflictContext {
    AT_HELM, ELSEWHERE;

    private static boolean atHelm() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof com.grandline.ship.ShipEntity;
    }

    @Override
    public boolean isActive() {
        return KeyConflictContext.IN_GAME.isActive() && (this == AT_HELM) == atHelm();
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this;
    }
}
