package com.terracraft.player;

import com.terracraft.TerraCraft;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Data attached to entities: every player carries a {@link TerraPlayerData}, saved with the player. */
public final class TerraAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TerraCraft.MODID);

    /** Death and dimension changes are handled by PlayerEvents (accessories survive death, mana refills). */
    public static final Supplier<AttachmentType<TerraPlayerData>> PLAYER_DATA = TYPES.register("player_data",
        () -> AttachmentType.serializable(TerraPlayerData::new).build());

    private TerraAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
