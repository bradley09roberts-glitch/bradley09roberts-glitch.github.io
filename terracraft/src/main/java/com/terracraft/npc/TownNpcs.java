package com.terracraft.npc;

import com.terracraft.economy.Coins;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.registry.content.NpcContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The town NPCs and their Terraria arrival rules (checked in this order). */
public final class TownNpcs {
    private static final Map<String, TownNpcType> TYPES = new LinkedHashMap<>();

    public static final TownNpcType GUIDE = register(new TownNpcType("guide", NpcContent.GUIDE,
        List.of("Alden", "Bram", "Corwin", "Dorian", "Ewan", "Finn", "Gideon", "Hollis", "Ivo", "Jasper", "Kellan", "Lorne"),
        server -> true, false, () -> ProjectileKinds.WOODEN_ARROW, 8.0F, 8,
        List.of(TownNpcType.Service.HELP)));
    public static final TownNpcType MERCHANT = register(new TownNpcType("merchant", NpcContent.MERCHANT,
        List.of("Barnaby", "Cornelius", "Dunstan", "Ebenezer", "Fitzgerald", "Horace", "Mortimer", "Percival", "Reginald", "Silas"),
        server -> anyPlayer(server, p -> Coins.total(p) >= 50 * Coins.SILVER), true, () -> ProjectileKinds.THROWING_KNIFE, 10.0F, 6,
        List.of(TownNpcType.Service.SHOP)));
    public static final TownNpcType NURSE = register(new TownNpcType("nurse", NpcContent.NURSE,
        List.of("Adeline", "Beatrix", "Clara", "Delphine", "Eleanor", "Florence", "Greta", "Henrietta", "Imogen", "Lucinda"),
        server -> NpcManager.isPresent(server, "merchant") && anyPlayer(server, p -> TerraPlayerData.get(p).lifeCrystals() > 0),
        true, null, 0.0F, 6, List.of(TownNpcType.Service.HEAL)));
    public static final TownNpcType DEMOLITIONIST = register(new TownNpcType("demolitionist", NpcContent.DEMOLITIONIST,
        List.of("Borin", "Dagny", "Grimbold", "Hagar", "Korrin", "Mundy", "Orlek", "Thrain", "Ulfgar", "Varik"),
        server -> NpcManager.isPresent(server, "merchant") && anyPlayer(server, p -> p.getInventory().contains(Items.TNT.getDefaultInstance())),
        true, () -> ProjectileKinds.SHURIKEN, 12.0F, 6, List.of(TownNpcType.Service.SHOP)));

    public static final TownNpcType ARMS_DEALER = register(new TownNpcType("arms_dealer", NpcContent.ARMS_DEALER,
        List.of("Brock", "Dalton", "Hank", "Jesse", "Keagan", "Maurice", "Ruben", "Sterling", "Tyrell", "Wade"),
        server -> anyPlayer(server, p -> p.getInventory().contains(s -> s.getItem() instanceof com.terracraft.item.weapon.RangedWeaponItem r
            && r.ammoType() == com.terracraft.item.weapon.AmmoType.BULLET || s.is(com.terracraft.registry.content.WeaponContent.MUSKET_BALL.get()))),
        true, () -> ProjectileKinds.MUSKET_BALL, 14.0F, 6, List.of(TownNpcType.Service.SHOP)));
    public static final TownNpcType DRYAD = register(new TownNpcType("dryad", NpcContent.DRYAD,
        List.of("Alalia", "Celestine", "Elowen", "Fernanda", "Ivy", "Liliana", "Meadow", "Rosalind", "Sylvie", "Willow"),
        server -> NpcManager.isPresent(server, "guide") && (com.terracraft.progression.ProgressionManager.has(server, com.terracraft.progression.ProgressionFlags.EYE_OF_CTHULHU)
            || com.terracraft.progression.ProgressionManager.has(server, com.terracraft.progression.ProgressionFlags.EVIL_BOSS)
            || com.terracraft.progression.ProgressionManager.has(server, com.terracraft.progression.ProgressionFlags.SKELETRON)),
        true, null, 0.0F, 6, List.of(TownNpcType.Service.SHOP)));

    /** Waits at the Dungeon entrance until Skeletron is defeated; spawned by {@code DungeonManager}, never "arrives". */
    public static final TownNpcType OLD_MAN = register(new TownNpcType("old_man", NpcContent.OLD_MAN, List.of(),
        server -> false, false, null, 0.0F, 5, List.of(TownNpcType.Service.CURSE)));
    public static final TownNpcType CLOTHIER = register(new TownNpcType("clothier", NpcContent.CLOTHIER,
        List.of("Archibald", "Bartholomew", "Crispin", "Desmond", "Eustace", "Leopold", "Montgomery", "Rupert", "Sebastian", "Theodore"),
        server -> com.terracraft.progression.ProgressionManager.has(server, com.terracraft.progression.ProgressionFlags.SKELETRON),
        true, () -> ProjectileKinds.BOOK_SKULL, 12.0F, 6, List.of(TownNpcType.Service.SHOP)));

    private TownNpcs() {}

    private static TownNpcType register(TownNpcType type) {
        TYPES.put(type.id(), type);
        return type;
    }

    public static Map<String, TownNpcType> all() {
        return TYPES;
    }

    public static TownNpcType get(String id) {
        return TYPES.get(id);
    }

    public static TownNpcType byEntity(EntityType<?> type) {
        for (TownNpcType npc : TYPES.values()) {
            if (npc.entity().get() == type) {
                return npc;
            }
        }
        return GUIDE;
    }

    private static boolean anyPlayer(MinecraftServer server, java.util.function.Predicate<ServerPlayer> test) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (test.test(player)) {
                return true;
            }
        }
        return false;
    }
}
