package com.grandline.mixin;

import com.grandline.core.PartEntityIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

/**
 * Entity queries only look at the multipart pieces (ship hull parts) that can reach the query box, instead of every
 * part in the level. See {@link PartEntityIndex}.
 */
@Mixin(Level.class)
public abstract class LevelPartEntitiesMixin {
    @Unique
    private final PartEntityIndex grandline$parts = new PartEntityIndex();

    @WrapOperation(
            method = {
                    "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                    "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getPartEntities()Ljava/util/Collection;"),
            require = 2)
    private Collection<PartEntity<?>> grandline$partsNear(Level self, Operation<Collection<PartEntity<?>>> original, @Local(argsOnly = true) AABB box) {
        return grandline$parts.near(self, original.call(self), box);
    }
}
