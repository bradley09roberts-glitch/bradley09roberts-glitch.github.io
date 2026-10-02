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
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Town NPC entity types and housing tools. */
public final class NpcContent {
    private static final List<RegistryObject<EntityType<TownNpc>>> ALL = new ArrayList<>();

    public static final RegistryObject<EntityType<TownNpc>> GUIDE = npc("guide");
    public static final RegistryObject<EntityType<TownNpc>> MERCHANT = npc("merchant");
    public static final RegistryObject<EntityType<TownNpc>> NURSE = npc("nurse");
    public static final RegistryObject<EntityType<TownNpc>> DEMOLITIONIST = npc("demolitionist");

    /** Right-click a room to check whether it is valid Terraria housing. */
    public static final RegistryObject<HousingQueryItem> HOUSING_QUERY = ModItems.register("housing_query", TabGroup.TOOLS_ARMOR,
        HousingQueryItem::new, p -> WeaponProperties.stats(p.stacksTo(1), CoreItems.stats(TerraRarity.WHITE, 0)));

    private NpcContent() {}

    public static void init() {
        EntityAttributeCreationEvent.BUS.addListener(event -> {
            for (RegistryObject<EntityType<TownNpc>> type : ALL) {
                event.put(type.get(), TownNpc.attributes().build());
            }
        });
    }

    public static List<RegistryObject<EntityType<TownNpc>>> all() {
        return ALL;
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
