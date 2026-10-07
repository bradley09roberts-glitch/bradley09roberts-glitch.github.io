package io.github.bradley09roberts.hardcorefriends.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

import io.github.bradley09roberts.hardcorefriends.client.render.CompanionRenderer;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;

public class HardcoreFriendsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.COMPANION, CompanionRenderer::new);
	}
}
