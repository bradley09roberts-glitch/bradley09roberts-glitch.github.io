package com.grandline.test;

import com.grandline.GrandLine;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server half of the automated client test (env GL_SELFTEST=client on the dedicated server): grants op to joining
 * players so the scripted client can drive /grandline test commands. Inactive otherwise.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class ClientTestSupport {
    static final boolean ACTIVE = "client".equals(System.getenv("GL_SELFTEST"));

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!ACTIVE || !(e.getEntity() instanceof ServerPlayer sp)) return;
        sp.server.getPlayerList().op(sp.getGameProfile());
        GrandLine.LOGGER.info("[clienttest] opped {}", sp.getGameProfile().getName());
    }

    private ClientTestSupport() {}
}
