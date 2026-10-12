package com.grandline.combat;

import com.grandline.player.CharacterData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Everything an ability executor needs. {@code data} is null for NPC users. */
public final class AbilityContext {
    public final ServerLevel level;
    public final LivingEntity user;
    @Nullable public final CharacterData data;
    @Nullable public final LivingEntity target;
    public final int mastery;
    public final boolean awakened;
    /** Damage scale from level/stats/mastery (1.0 = baseline). */
    public final float power;

    public AbilityContext(ServerLevel level, LivingEntity user, @Nullable CharacterData data, @Nullable LivingEntity target, int mastery, boolean awakened, float power) {
        this.level = level;
        this.user = user;
        this.data = data;
        this.target = target;
        this.mastery = mastery;
        this.awakened = awakened;
        this.power = power;
    }

    public Vec3 look() {
        return user.getLookAngle();
    }

    public Vec3 eye() {
        return user.getEyePosition();
    }

    /** Mastery-scaled multiplier: 1.0 at mastery 0 up to 2.0 at 100. */
    public float masteryScale() {
        return 1f + mastery / 100f;
    }
}
