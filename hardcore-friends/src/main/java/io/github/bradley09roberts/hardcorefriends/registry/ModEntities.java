package io.github.bradley09roberts.hardcorefriends.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> COMPANION_KEY =
		ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "companion"));
	public static final EntityType<CompanionEntity> COMPANION = Registry.register(BuiltInRegistries.ENTITY_TYPE, COMPANION_KEY,
		EntityType.Builder.<CompanionEntity>of(CompanionEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.clientTrackingRange(10)
			.build(COMPANION_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(COMPANION, CompanionEntity.createAttributes());
	}
}
