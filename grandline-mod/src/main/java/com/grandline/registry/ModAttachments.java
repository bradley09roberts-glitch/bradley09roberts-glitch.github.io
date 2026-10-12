package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.player.CharacterData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GrandLine.MODID);

    /** Not copyOnDeath: a real death is permadeath. The Clone handler archives and carries the legacy only. */
    public static final Supplier<AttachmentType<CharacterData>> CHARACTER = ATTACHMENTS.register("character",
            () -> AttachmentType.serializable(CharacterData::new).build());

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private ModAttachments() {}
}
