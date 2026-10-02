package com.terracraft.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** A damage source carrying Terraria hit information through vanilla's damage pipeline. */
public class TerraDamageSource extends DamageSource {
    private final TerraHit hit;

    public TerraDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, TerraHit hit) {
        super(type, directEntity, causingEntity);
        this.hit = hit;
    }

    public TerraHit hit() {
        return hit;
    }
}
