package com.grandline.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The one criterion of the journey advancements: {@code "trigger": "grandline:milestone", "conditions": {"id": "..."}}.
 * Game code fires it with a milestone id from the hook where the thing happens ({@link Journey#milestone}); no polling.
 */
public final class MilestoneTrigger extends SimpleCriterionTrigger<MilestoneTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String id) {
        trigger(player, i -> i.id().equals(id));
    }

    public record Instance(Optional<ContextAwarePredicate> player, String id) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(b -> b.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("id").forGetter(Instance::id)).apply(b, Instance::new));
    }
}
