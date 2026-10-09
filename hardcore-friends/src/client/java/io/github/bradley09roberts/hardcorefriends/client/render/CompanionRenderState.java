package io.github.bradley09roberts.hardcorefriends.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state for a friend. Deliberately not an AvatarRenderState: those are routed to the player renderer. */
public class CompanionRenderState extends HumanoidRenderState {
	public int skinId;
	/** The skin is drawn for slim arms, so the slim player model is used. */
	public boolean slim;
}
