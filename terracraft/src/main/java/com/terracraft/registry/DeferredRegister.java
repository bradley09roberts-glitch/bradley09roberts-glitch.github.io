package com.terracraft.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/**
 * Thin wrapper over NeoForge's DeferredRegister that hands out {@link RegistryObject}s and can build the
 * {@link ResourceKey} of an entry before it is registered (items, blocks and entity types need their key at
 * construction time).
 */
public final class DeferredRegister<R> {
    private final net.neoforged.neoforge.registries.DeferredRegister<R> inner;
    private final ResourceKey<? extends Registry<R>> registryKey;
    private final String namespace;

    private DeferredRegister(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
        this.inner = net.neoforged.neoforge.registries.DeferredRegister.create(registryKey, namespace);
        this.registryKey = registryKey;
        this.namespace = namespace;
    }

    public static <R> DeferredRegister<R> create(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    public <T extends R> RegistryObject<T> register(String name, Supplier<? extends T> supplier) {
        DeferredHolder<R, T> holder = inner.register(name, supplier);
        return new RegistryObject<>(holder);
    }

    /** The key the entry {@code name} will have. */
    public ResourceKey<R> key(String name) {
        return ResourceKey.create(registryKey, Identifier.fromNamespaceAndPath(namespace, name));
    }

    public void register(IEventBus modBus) {
        inner.register(modBus);
    }
}
