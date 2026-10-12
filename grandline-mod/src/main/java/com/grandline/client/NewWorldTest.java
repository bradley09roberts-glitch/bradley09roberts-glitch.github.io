package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.world.WorldPlan;
import com.grandline.world.gen.ArchipelagoChunkGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * GL_CLIENTTEST=newworld: what a player does on a fresh install. Opens Singleplayer > Create New World from the menus,
 * records which World Type the screen offers by default, presses "Create New World" without touching anything, and
 * checks that the integrated server generated the Grand Line ocean world. Writes clienttest-report.txt and stops.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class NewWorldTest {
    private static final boolean ACTIVE = "newworld".equals(System.getenv("GL_CLIENTTEST"));
    private static final List<String> LOG = new ArrayList<>();
    private static int stage, ticks;

    private NewWorldTest() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (stage) {
                case 0 -> { // on the menus (the dev run's quick-play connection has no server to reach)
                    if (ticks < 100 || mc.level != null || mc.screen == null) return;
                    LOG.add("menu screen: " + mc.screen.getClass().getSimpleName());
                    CreateWorldScreen.openFresh(mc, mc.screen);
                    next();
                }
                case 1 -> { // the Create World screen loads its world settings asynchronously
                    if (!(mc.screen instanceof CreateWorldScreen s) || ticks < 60) {
                        if (ticks > 2400) fail("the Create World screen never opened");
                        return;
                    }
                    var type = s.getUiState().getWorldType().preset();
                    String key = type == null ? "custom" : type.unwrapKey().map(k -> k.location().toString()).orElse("?");
                    LOG.add((key.equals("grandline:ocean_world") ? "PASS" : "FAIL") + " default World Type on a fresh Create World screen: " + key);
                    Screenshot.grab(mc.gameDirectory, "clienttest_newworld_create_screen.png", mc.getMainRenderTarget(), m -> {});
                    for (var child : s.children())
                        if (child instanceof Button b && b.getMessage().equals(Component.translatable("selectWorld.create"))) {
                            LOG.add("pressed Create New World, nothing else changed");
                            b.onPress();
                            next();
                            return;
                        }
                    fail("no Create New World button");
                }
                case 2 -> { // the integrated server generates and the player joins
                    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
                        if (ticks > 20 * 600) fail("the new world never loaded");
                        return;
                    }
                    if (ticks < 200) return;
                    ServerLevel ow = mc.getSingleplayerServer().overworld();
                    boolean ocean = ow.getChunkSource().getGenerator() instanceof ArchipelagoChunkGenerator;
                    WorldPlan plan = WorldPlan.of(mc.getSingleplayerServer());
                    LOG.add((ocean ? "PASS" : "FAIL") + " the new world's overworld uses the Grand Line generator: "
                            + ow.getChunkSource().getGenerator().getClass().getSimpleName()
                            + (plan != null ? ", world plan with " + plan.islands.size() + " islands" : ", no world plan"));
                    LOG.add("  player at " + mc.player.blockPosition().toShortString() + ", screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
                    Screenshot.grab(mc.gameDirectory, "clienttest_newworld_joined.png", mc.getMainRenderTarget(), m -> {});
                    next();
                }
                case 3 -> {
                    if (ticks < 40) return;
                    finish(mc);
                }
                default -> {}
            }
        } catch (Throwable t) {
            GrandLine.LOGGER.error("[newworld] stage {} failed", stage, t);
            fail("exception at stage " + stage + ": " + t);
        }
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    private static void fail(String why) {
        LOG.add("FAIL " + why);
        finish(Minecraft.getInstance());
    }

    private static void finish(Minecraft mc) {
        stage = 99;
        try {
            Files.write(mc.gameDirectory.toPath().resolve("clienttest-report.txt"), LOG);
        } catch (IOException ex) {
            GrandLine.LOGGER.error("[newworld] could not write report", ex);
        }
        GrandLine.LOGGER.info("[newworld] finished: {}", LOG);
        mc.stop();
    }
}
