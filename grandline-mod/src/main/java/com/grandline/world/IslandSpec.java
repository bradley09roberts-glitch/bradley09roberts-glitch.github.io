package com.grandline.world;

import com.grandline.player.Blue;
import com.grandline.world.settlement.SettlementType;

import java.util.ArrayList;
import java.util.List;

/** Mutable builder describing an island before it is instantiated in a plan. */
public final class IslandSpec {
    public String id;
    public String name;
    public boolean canon;
    public int x, z, radius = 300, peak = 30, tier = 0;
    public IslandShape shape = IslandShape.HILLS;
    public IslandTheme theme = IslandTheme.TEMPERATE;
    public SettlementType settlement = SettlementType.VILLAGE;
    public final List<Island.LandmarkPlacement> landmarks = new ArrayList<>();
    public Blue starterFor;
    public String faction = "civilian";
    public com.grandline.world.build.BuildStyle style;
    public IslandTheme altTheme;

    public static IslandSpec canon(String id, int x, int z, int radius) {
        IslandSpec s = new IslandSpec();
        s.id = id;
        s.canon = true;
        s.x = x;
        s.z = z;
        s.radius = radius;
        return s;
    }

    public IslandSpec shape(IslandShape shape, int peak) {
        this.shape = shape;
        this.peak = peak;
        return this;
    }

    public IslandSpec theme(IslandTheme theme) {
        this.theme = theme;
        return this;
    }

    public IslandSpec settle(SettlementType type) {
        this.settlement = type;
        return this;
    }

    public IslandSpec tier(int tier) {
        this.tier = tier;
        return this;
    }

    public IslandSpec faction(String faction) {
        this.faction = faction;
        return this;
    }

    public IslandSpec landmark(String id, int dx, int dz) {
        landmarks.add(new Island.LandmarkPlacement(id, dx, dz, 0));
        return this;
    }

    public IslandSpec landmark(String id, int dx, int dz, int rot) {
        landmarks.add(new Island.LandmarkPlacement(id, dx, dz, rot));
        return this;
    }

    public IslandSpec starter(Blue blue) {
        this.starterFor = blue;
        return this;
    }

    public IslandSpec style(com.grandline.world.build.BuildStyle style) {
        this.style = style;
        return this;
    }

    /** Second climate covering the eastern half (e.g. Punk Hazard's fire and ice). */
    public IslandSpec split(IslandTheme alt) {
        this.altTheme = alt;
        return this;
    }

    public IslandSpec named(String name) {
        this.name = name;
        return this;
    }
}
