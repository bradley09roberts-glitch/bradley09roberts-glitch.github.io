package com.terracraft.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Optional;
import java.util.function.Supplier;

/** A registered (or soon to be registered) entry: wraps NeoForge's {@link DeferredHolder}. */
public final class RegistryObject<T> implements Supplier<T> {
    private final DeferredHolder<? super T, T> holder;

    RegistryObject(DeferredHolder<? super T, T> holder) {
        this.holder = holder;
    }

    @Override
    public T get() {
        return holder.get();
    }

    public Identifier getId() {
        return holder.getId();
    }

    public boolean isPresent() {
        return holder.isBound();
    }

    /** The registry holder, for APIs that take a {@code Holder<T>} (mob effects, sounds...). */
    @SuppressWarnings("unchecked")
    public Optional<Holder<T>> getHolder() {
        return Optional.of((Holder<T>) (Holder<?>) holder);
    }
}
