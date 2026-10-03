package com.terracraft.registry.content;

import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.npc.HousingQueryItem;
import com.terracraft.npc.TownNpc;
import com.terracraft.registry.ModEntities;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import com.terracraft.registry.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Town NPC entity types and housing tools. */
public final class NpcContent {
    private static final List<RegistryObject<EntityType<TownNpc>>> ALL = new ArrayList<>();

    public static final RegistryObject<EntityType<TownNpc>> GUIDE = npc("guide");
    public static final RegistryObject<EntityType<TownNpc>> MERCHANT = npc("merchant");
    public static final RegistryObject<EntityType<TownNpc>> NURSE = npc("nurse");
    public static final RegistryObject<EntityType<TownNpc>> DEMOLITIONIST = npc("demolitionist");
    public static final RegistryObject<EntityType<TownNpc>> ARMS_DEALER = npc("arms_dealer");
    public static final RegistryObject<EntityType<TownNpc>> DRYAD = npc("dryad");
    public static final RegistryObject<EntityType<TownNpc>> OLD_MAN = npc("old_man");
    public static final RegistryObject<EntityType<TownNpc>> CLOTHIER = npc("clothier");
    /** Found tied up in the caverns after the Goblin Army: talking to him frees him. */
    public static final RegistryObject<EntityType<TownNpc>> BOUND_GOBLIN = npc("bound_goblin");
    public static final RegistryObject<EntityType<TownNpc>> GOBLIN_TINKERER = npc("goblin_tinkerer");
    // Hardmode
    public static final RegistryObject<EntityType<TownNpc>> BOUND_WIZARD = npc("bound_wizard");
    public static final RegistryObject<EntityType<TownNpc>> WIZARD = npc("wizard");
    public static final RegistryObject<EntityType<TownNpc>> STEAMPUNKER = npc("steampunker");
    public static final RegistryObject<EntityType<TownNpc>> WITCH_DOCTOR = npc("witch_doctor");

    public static final RegistryObject<com.terracraft.item.tool.ClentaminatorItem> CLENTAMINATOR = ModItems.register("clentaminator", TabGroup.TOOLS_ARMOR,
        com.terracraft.item.tool.ClentaminatorItem::new,
        p -> com.terracraft.item.weapon.WeaponProperties.stats(p.stacksTo(1), CoreItems.stats(com.terracraft.item.TerraRarity.YELLOW, 150_000)));
    public static final RegistryObject<com.terracraft.item.tool.ClentaminatorItem.SolutionItem> GREEN_SOLUTION = solution("green_solution",
        com.terracraft.item.tool.ClentaminatorItem.SolutionItem.Kind.GREEN, 0x50E050);
    public static final RegistryObject<com.terracraft.item.tool.ClentaminatorItem.SolutionItem> BLUE_SOLUTION = solution("blue_solution",
        com.terracraft.item.tool.ClentaminatorItem.SolutionItem.Kind.BLUE, 0x50A0F0);
    public static final RegistryObject<com.terracraft.item.tool.ClentaminatorItem.SolutionItem> PURPLE_SOLUTION = solution("purple_solution",
        com.terracraft.item.tool.ClentaminatorItem.SolutionItem.Kind.PURPLE, 0xA050E0);
    public static final RegistryObject<com.terracraft.item.tool.ClentaminatorItem.SolutionItem> RED_SOLUTION = solution("red_solution",
        com.terracraft.item.tool.ClentaminatorItem.SolutionItem.Kind.RED, 0xE04040);
    public static final RegistryObject<com.terracraft.item.TerraItem> SPELL_TOME = CoreItems.material("spell_tome", com.terracraft.item.TerraRarity.LIGHT_RED, 5000);

    /** Right-click a room to check whether it is valid Terraria housing. */
    public static final RegistryObject<HousingQueryItem> HOUSING_QUERY = ModItems.register("housing_query", TabGroup.TOOLS_ARMOR,
        HousingQueryItem::new, p -> WeaponProperties.stats(p.stacksTo(1), CoreItems.stats(TerraRarity.WHITE, 0)));

    /** Dryad's Purification Powder: cleanses Corruption/Crimson blocks around where it is thrown. */
    public static final RegistryObject<com.terracraft.npc.PurificationPowderItem> PURIFICATION_POWDER = ModItems.register("purification_powder",
        TabGroup.CONSUMABLES, com.terracraft.npc.PurificationPowderItem::new, p -> WeaponProperties.stats(p.stacksTo(99), CoreItems.stats(TerraRarity.WHITE, 75)));

    private NpcContent() {}

    public static void init() {
        com.terracraft.TerraCraft.modBus().addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> {
            for (RegistryObject<EntityType<TownNpc>> type : ALL) {
                event.put(type.get(), TownNpc.attributes().build());
            }
        });
    }

    public static List<RegistryObject<EntityType<TownNpc>>> all() {
        return ALL;
    }

    private static RegistryObject<com.terracraft.item.tool.ClentaminatorItem.SolutionItem> solution(String name,
            com.terracraft.item.tool.ClentaminatorItem.SolutionItem.Kind kind, int color) {
        return ModItems.register(name, TabGroup.TOOLS_ARMOR, p -> new com.terracraft.item.tool.ClentaminatorItem.SolutionItem(p, kind, color),
            p -> com.terracraft.item.weapon.WeaponProperties.stats(p, CoreItems.stats(com.terracraft.item.TerraRarity.ORANGE, 2500)));
    }

    private static RegistryObject<EntityType<TownNpc>> npc(String name) {
        RegistryObject<EntityType<TownNpc>> type = ModEntities.ENTITY_TYPES.register(name,
            () -> EntityType.Builder.of(TownNpc::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .build(ModEntities.ENTITY_TYPES.key(name)));
        ALL.add(type);
        return type;
    }
}
