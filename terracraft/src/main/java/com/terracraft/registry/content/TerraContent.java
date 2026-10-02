package com.terracraft.registry.content;

/** Loads every content class so their registry entries exist before registration events fire. */
public final class TerraContent {
    private TerraContent() {}

    public static void init() {
        CoreItems.init();
        OreContent.init();
        StationContent.init();
        WeaponContent.init();
        ToolContent.init();
        ArmorContent.init();
        AccessoryContent.init();
        WorldBlockContent.init();
        MobContent.init();
        BossContent.init();
        NpcContent.init();
    }
}
