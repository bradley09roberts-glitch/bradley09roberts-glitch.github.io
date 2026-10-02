package com.terracraft.client.model;

import net.minecraft.client.renderer.entity.state.UndeadRenderState;

/** Render state shared by all TerraCraft 3D models (humanoid fields come from the vanilla parent). */
public class TerraRenderState extends UndeadRenderState {
    /** Texture/model variant suffix ("" = base form). */
    public String variant = "";
    public boolean airborne;
    public float verticalSpeed;
    /** Extra roll in degrees (spinning bosses). */
    public float spin;
    /** Zombies hold their arms out. */
    public boolean armsForward;
    public boolean aggressive;
    public float healthFraction = 1.0F;
    public boolean healthBar;
}
