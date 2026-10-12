package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.client.screen.CharacterCreationScreen;
import com.grandline.client.screen.CharacterScreen;
import com.grandline.combat.CombatInput;
import com.grandline.net.GLNetwork;
import com.grandline.player.Appearance;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Scripted client run used to verify rendering, HUD, screens and client/server sync on a real client
 * (env GL_CLIENTTEST=1, joined to a dedicated server started with GL_SELFTEST=client). It creates a character,
 * visits islands, spawns NPCs and ships, uses powers and saves screenshots to screenshots/clienttest_*.png, then
 * writes clienttest-report.txt (chat feedback, frame rate, step log) and quits. Inactive otherwise.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class ClientSelfTest {
    private static final boolean ACTIVE = System.getenv("GL_CLIENTTEST") != null;
    private static final List<String> LOG = new ArrayList<>();
    private static Deque<Step> steps;
    private static int wait;
    private static int stepTicks;

    private record Step(String name, BooleanSupplier until, Runnable action, int after) {}

    private static void add(String name, Runnable action, int after) {
        steps.add(new Step(name, () -> true, action, after));
    }

    private static void cmd(String c, int after) {
        add("/" + c, () -> Minecraft.getInstance().player.connection.sendCommand(c), after);
    }

    private static void shot(String name) {
        add("shot " + name, () -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, "clienttest_" + name + ".png", mc.getMainRenderTarget(), m -> {});
            var p = mc.player;
            LOG.add(String.format("  shot %s at %.0f %.0f %.0f fps=%d entities=%d", name, p.getX(), p.getY(), p.getZ(), mc.getFps(), mc.level.getEntityCount()));
        }, 10);
    }

    private static void camera(CameraType t) {
        add("camera " + t, () -> Minecraft.getInstance().options.setCameraType(t), 10);
    }

    private static void combat(byte action, int a0) {
        add("input " + action + "/" + a0, () -> PacketDistributor.sendToServer(new GLNetwork.CombatInput(action, (byte) a0, (byte) 0, -1, 0)), 10);
    }

    private static final String MODE = System.getenv("GL_CLIENTTEST");

    private static void build() {
        steps = new ArrayDeque<>();
        if ("mp_a".equals(MODE)) buildCaptain();
        else if ("mp_b".equals(MODE)) buildMate();
        else buildSolo();
    }

    private static void createCharacter(String name, String race) {
        Minecraft mc = Minecraft.getInstance();
        steps.add(new Step("wait for world", () -> mc.player != null && mc.level != null && ClientState.hasData, () -> {}, 60));
        steps.add(new Step("wait for creation screen", () -> mc.screen instanceof CharacterCreationScreen || ClientState.LOCAL.created, () -> {}, 40));
        shot("01_creation_screen");
        add("create character", () -> {
            if (ClientState.LOCAL.created) return;
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("race", race);
            t.putString("faction", MODE != null && MODE.startsWith("mp") ? "civilian" : "pirate");
            t.putString("origin", "east");
            t.put("appearance", new Appearance().save());
            PacketDistributor.sendToServer(new GLNetwork.CreateCharacter(t));
            mc.setScreen(null);
        }, 60);
        steps.add(new Step("wait for created", () -> ClientState.LOCAL.created, () -> {}, 40));
    }

    private static net.minecraft.world.entity.player.Player other() {
        Minecraft mc = Minecraft.getInstance();
        for (var p : mc.level.players()) if (p != mc.player) return p;
        return null;
    }

    private static com.grandline.ship.ShipEntity nearestShip(double range) {
        Minecraft mc = Minecraft.getInstance();
        com.grandline.ship.ShipEntity best = null;
        double bd = range * range;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof com.grandline.ship.ShipEntity s && s.distanceToSqr(mc.player) < bd) {
                bd = s.distanceToSqr(mc.player);
                best = s;
            }
        return best;
    }

    private static void logRelative(String who, net.minecraft.world.entity.player.Player p) {
        var ship = nearestShip(200);
        if (ship == null || p == null) {
            LOG.add("  " + who + ": ship or player not visible");
            return;
        }
        double h = Math.hypot(p.getX() - ship.getX(), p.getZ() - ship.getZ());
        LOG.add(String.format("  %s: ship at %.1f %.1f speed %.2f | player off-centre %.1f, height above ship %.2f, onDeck=%s",
                who, ship.getX(), ship.getZ(), ship.speed(), h, p.getY() - ship.getY(), ship.carries(p)));
    }

    /** Two-player test, captain: sails a galleon with the mate standing on deck. */
    private static void buildCaptain() {
        createCharacter("Captain", "human");
        cmd("grandline test env", 10);
        cmd("grandline island tp shiokaze_isle", 120);
        cmd("grandline test ship galleon helm", 60);
        cmd("grandline test anchor true", 20);
        camera(CameraType.THIRD_PERSON_BACK);
        steps.add(new Step("wait for the mate to come aboard", () -> {
            var o = other();
            var ship = nearestShip(100);
            return o != null && ship != null && ship.carries(o);
        }, () -> {}, 60));
        shot("mp_a_mate_aboard");
        cmd("grandline test anchor false", 5);
        cmd("grandline test sail 3", 20);
        for (int i = 0; i < 12; i++) {
            add("observe mate", () -> logRelative("captain sees mate", other()), 50);
            if (i == 6) shot("mp_a_sailing_with_mate");
        }
        cmd("grandline test sail 0", 60);
        shot("mp_a_end");
        add("done", ClientSelfTest::finish, 0);
    }

    /** Two-player test, mate: boards the captain's ship and stands on deck while it sails. */
    private static void buildMate() {
        createCharacter("Mate", "mink");
        add("let the captain launch the ship", () -> {}, 500);
        cmd("tp @s Tester", 80); // the captain's login name (character "Captain")
        steps.add(new Step("see the captain", () -> other() != null, () -> {}, 20));
        steps.add(new Step("see the ship", () -> nearestShip(120) != null, () -> {
            var ship = nearestShip(120);
            Vec3 deck = ship.toWorld(0, ship.cls.freeboard + 0.3, -4);
            Minecraft.getInstance().player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", deck.x, deck.y, deck.z));
        }, 60));
        shot("mp_b_on_deck_start");
        for (int i = 0; i < 14; i++) {
            add("stand on deck", () -> logRelative("mate", Minecraft.getInstance().player), 50);
            if (i == 7) shot("mp_b_deck_while_sailing");
        }
        camera(CameraType.THIRD_PERSON_BACK);
        shot("mp_b_third_person");
        add("done", ClientSelfTest::finish, 0);
    }

    private static void buildSolo() {
        Minecraft mc = Minecraft.getInstance();
        createCharacter("Tester", "fishman");
        cmd("grandline test env", 10);
        cmd("grandline char level 60", 10);
        cmd("tp @s ~ ~ ~ 0 5", 120);
        shot("02_spawn_first_person");
        camera(CameraType.THIRD_PERSON_FRONT);
        shot("03_player_fishman_front");
        camera(CameraType.FIRST_PERSON);

        cmd("grandline island tp shiokaze_isle", 200);
        cmd("tp @s ~ ~ ~ 0 5", 20);
        shot("04_town_street");
        cmd("gamemode spectator", 10);
        cmd("tp @s ~ ~45 ~-70 0 30", 220);
        shot("05_town_aerial");
        cmd("gamemode survival", 10);
        cmd("grandline island tp shiokaze_isle", 80);

        // NPC line-up, facing south
        cmd("tp @s ~ ~ ~ 0 0", 10);
        for (String role : new String[]{"marine_captain", "pirate_captain", "fishman_brute", "giant_warrior", "kuja_warrior"}) {
            cmd("grandline npc " + role + " 5", 5);
            cmd("tp @s ~3 ~ ~ 0 0", 5);
        }
        cmd("tp @s ~-9 ~ ~-3 0 8", 40);
        shot("06_npc_lineup");
        camera(CameraType.THIRD_PERSON_BACK);
        add("combat mode on", () -> {
            ClientState.combatMode = true;
            PacketDistributor.sendToServer(new GLNetwork.CombatInput(CombatInput.TOGGLE_COMBAT, (byte) 1, (byte) 0, 0, 0));
        }, 30);
        shot("07_combat_mode_hud");
        combat(CombatInput.LIGHT, 0);
        add("wait", () -> {}, 4);
        shot("08_light_attack");

        cmd("grandline fruit set mera", 20);
        cmd("grandline test ability fruit/mera/fire_fist", 5);
        shot("09_mera_fire_fist");
        add("wait", () -> {}, 60);
        cmd("grandline fruit remove", 10);
        cmd("grandline fruit set hie", 20);
        cmd("grandline test ability fruit/hie/ice_age", 12);
        shot("10_hie_ice_age");
        cmd("grandline fruit remove", 10);
        cmd("grandline fruit set goro", 20);
        cmd("grandline test ability fruit/goro/el_thor", 8);
        shot("11_goro_el_thor");
        cmd("grandline char haki", 10);
        combat(CombatInput.HAKI_TOGGLE, 0);
        add("wait", () -> {}, 20);
        shot("12_observation_glow");
        combat(CombatInput.HAKI_TOGGLE, 1);
        add("wait", () -> {}, 20);
        shot("13_armament");
        add("open character screen", () -> mc.setScreen(new CharacterScreen()), 20);
        shot("14_character_screen");
        add("close screen", () -> mc.setScreen(null), 10);
        add("combat mode off", () -> {
            ClientState.combatMode = false;
            PacketDistributor.sendToServer(new GLNetwork.CombatInput(CombatInput.TOGGLE_COMBAT, (byte) 0, (byte) 0, 0, 0));
        }, 10);
        cmd("kill @e[type=grandline:npc,distance=..60]", 20);
        cmd("grandline terrain revert", 20);

        // ships
        cmd("grandline island tp shiokaze_isle", 60);
        cmd("grandline test ship galleon helm", 80);
        shot("15_ship_at_helm");
        add("wait while sailing", () -> {}, 300);
        shot("16_ship_sailing");
        cmd("gamemode spectator", 10);
        cmd("tp @s ~-25 ~22 ~-25 -45 35", 60);
        shot("17_ship_from_above");

        // canon set pieces and terrain (spectator fly-bys)
        shot2("18_baratie", "tp @s 15470 100 13440 0 40");
        shot2("19_reverse_mountain", "tp @s 80 330 200 160 30");
        shot2("20_red_line", "tp @s 140 150 2000 90 10");
        shot2("21_drum_castle", "tp @s 14000 280 2130 0 40");
        shot2("22_marineford", "tp @s 54200 150 -2570 0 30");
        shot2("23_water7", "tp @s 34500 140 330 0 40");
        shot2("24_alabasta", "tp @s 19650 120 -700 0 25");
        shot2("25_wano_cliffs", "tp @s -27400 200 1200 90 15");
        shot2("26_original_island", "grandline island tp shiokaze_isle");
        // the White Sea and the deep sea
        shot2("27_heavens_gate", "execute in grandline:sky run tp @s -960 85 -40 30 15");
        shot2("28_angel_island", "execute in grandline:sky run tp @s -450 120 -20 0 30");
        shot2("29_upper_yard", "execute in grandline:sky run tp @s 300 150 -330 20 20");
        shot2("30_fishman_island_inside", "execute in grandline:deep_sea run tp @s 170 110 40 110 15");
        shot2("31_fishman_island_bubble", "execute in grandline:deep_sea run tp @s 330 160 0 90 15");
        shot2("32_deep_sea_route", "execute in grandline:deep_sea run tp @s -1400 70 10 -90 0");
        // weather
        cmd("execute in minecraft:overworld run tp @s 36000 120 20000", 60);
        cmd("grandline test storm", 260);
        shot("33_storm_at_sea");
        add("done", ClientSelfTest::finish, 0);
    }

    private static void shot2(String name, String tp) {
        cmd(tp, 260);
        shot(name);
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent e) {
        if (ACTIVE) LOG.add("  chat: " + e.getMessage().getString());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        if (steps == null) {
            if (mc.player == null) return;
            build();
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = steps.peekFirst();
        if (s == null) return;
        stepTicks++;
        if (!s.until().getAsBoolean()) {
            if (stepTicks > 20 * 120) {
                LOG.add("TIMEOUT " + s.name());
                finish();
            }
            return;
        }
        steps.pollFirst();
        stepTicks = 0;
        try {
            if (mc.player != null || s.name().equals("done")) s.action().run();
            LOG.add("ok " + s.name());
        } catch (Throwable t) {
            LOG.add("ERROR " + s.name() + ": " + t);
            GrandLine.LOGGER.error("[clienttest] step {} failed", s.name(), t);
        }
        wait = s.after();
    }

    private static void finish() {
        steps.clear();
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.write(mc.gameDirectory.toPath().resolve("clienttest-report.txt"), LOG);
        } catch (IOException ex) {
            GrandLine.LOGGER.error("[clienttest] could not write report", ex);
        }
        GrandLine.LOGGER.info("[clienttest] finished, {} log lines", LOG.size());
        mc.stop();
    }

    private ClientSelfTest() {}
}
