package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.entity.AbilityProjectile;
import com.grandline.npc.HumanoidNpc;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, GrandLine.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AbilityProjectile>> ABILITY_PROJECTILE = TYPES.register("ability_projectile",
            () -> EntityType.Builder.<AbilityProjectile>of(AbilityProjectile::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("ability_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<HumanoidNpc>> HUMANOID_NPC = TYPES.register("npc",
            () -> EntityType.Builder.of(HumanoidNpc::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("npc"));

    public static final java.util.Map<com.grandline.ship.ShipClass, DeferredHolder<EntityType<?>, EntityType<com.grandline.ship.ShipEntity>>> SHIPS = new java.util.EnumMap<>(com.grandline.ship.ShipClass.class);

    static {
        for (com.grandline.ship.ShipClass c : com.grandline.ship.ShipClass.values()) {
            SHIPS.put(c, TYPES.register("ship_" + c.id(), () -> EntityType.Builder.<com.grandline.ship.ShipEntity>of((t, l) -> new com.grandline.ship.ShipEntity(t, l, c), MobCategory.MISC)
                    .sized(Math.min(4f, c.beam), c.freeboard + c.draft).clientTrackingRange(16).updateInterval(1).build("ship_" + c.id())));
        }
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(HUMANOID_NPC.get(), HumanoidNpc.createAttributes().build());
    }

    private ModEntities() {}
}
