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
    private static final boolean CREATURES_ONLY = "creatures".equals(System.getenv("GL_CLIENTTEST"));
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
        else if ("naval".equals(MODE)) {
            createCharacter("Tester", "human");
            cmd("grandline test env", 10);
            cmd("grandline char level 60", 10);
            buildNaval();
            add("done", ClientSelfTest::finish, 0);
        } else buildSolo();
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
        if (CREATURES_ONLY) { // GL_CLIENTTEST=creatures: just the creature shots
            creatureShots();
            add("done", ClientSelfTest::finish, 0);
            return;
        }
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

        // NPC line-up, facing south, on a stone stage above the town: the town's layout changes with the world seed and
        // stepping along the line must never put the tester inside a wall (suffocating while knocked down ends the story)
        cmd("fill ~-10 ~40 ~-14 ~24 ~40 ~14 minecraft:smooth_stone", 10);
        cmd("fill ~-10 ~41 ~-14 ~24 ~46 ~14 minecraft:air", 10);
        cmd("tp @s ~ ~41 ~ 0 0", 20);
        cmd("effect give @s minecraft:resistance 900 3 true", 5);
        cmd("effect give @s minecraft:regeneration 900 2 true", 5);
        for (String role : new String[]{"marine_captain", "pirate_captain", "fishman_brute", "giant_warrior", "kuja_warrior"}) {
            cmd("grandline npc " + role + " 5", 5);
            cmd("tp @s ~3 ~ ~ 0 0", 5);
        }
        // they pose for the camera and stand as targets: five elite NPCs at once would finish the tester in seconds
        cmd("execute as @e[type=grandline:npc,distance=..30] run data merge entity @s {NoAI:1b}", 10);
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
        add("open wanted poster", () -> {
            var t = new net.minecraft.nbt.CompoundTag();
            t.putString("name", "Selftest");
            t.putLong("bounty", 120_000_000L);
            t.putString("faction", "pirate");
            t.putUUID("uuid", mc.player.getUUID());
            com.grandline.client.screen.WantedPosterScreen.open(t);
        }, 20);
        shot("14b_wanted_poster");
        add("open sea chart", () -> PacketDistributor.sendToServer(new GLNetwork.UiAction("open_chart", new net.minecraft.nbt.CompoundTag())), 40);
        shot("14c_sea_chart");
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
        buildNaval();

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
        // the painted paintings on a wall over the sea
        cmd("execute in minecraft:overworld run tp @s 36025 104 20044 0 5", 120);
        cmd("fill 36000 99 20060 36052 106 20060 minecraft:spruce_planks", 20);
        String[] ps = {"red_line", "reverse_mountain", "knock_up_stream", "white_sea", "bubble_city", "sabaody_grove", "calm_belt", "last_island", "sea_train", "cyclone"};
        for (int i = 0; i < ps.length; i++)
            cmd("summon minecraft:painting " + (36002 + i * 5) + " 101 20059 {facing:2b,variant:\"grandline:" + ps[i] + "\"}", 4);
        cmd("tp @s 36014 103 20046 0 5", 60);
        shot("34_paintings_a");
        cmd("tp @s 36039 103 20046 0 5", 40);
        shot("35_paintings_b");
        creatureShots();
        add("done", ClientSelfTest::finish, 0);
    }

    /** The ship the scripted player sails in the naval section (remembered while at its helm). */
    private static int navalShip = -1;

    /** Fallback: the ship ridden, else the nearest ship named after the tester. */
    private static int ownShip(Minecraft mc) {
        if (mc.player.getVehicle() instanceof com.grandline.ship.ShipEntity s) return s.getId();
        com.grandline.ship.ShipEntity best = null;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof com.grandline.ship.ShipEntity s && s.shipName().startsWith("Tester") && (best == null || s.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) best = s;
        return best == null ? -1 : best.getId();
    }

    /** Spectator camera at a point in the remembered ship's frame, looking at another point in that frame. */
    private static void shipCam(String name, double lx, double ly, double lz, double tx, double ty, double tz, int after) {
        add("camera " + name, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.level.getEntity(navalShip) instanceof com.grandline.ship.ShipEntity)) navalShip = ownShip(mc);
            if (!(mc.level.getEntity(navalShip) instanceof com.grandline.ship.ShipEntity s)) {
                LOG.add("  naval ship not visible for " + name);
                return;
            }
            Vec3 c = s.toWorld(lx, ly, lz), t = s.toWorld(tx, ty, tz);
            double dx = t.x - c.x, dy = t.y - c.y, dz = t.z - c.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", c.x, c.y, c.z, yaw, pitch));
        }, after);
    }

    /** Spectator camera astern of a grappled pair (or the player's ship and the nearest other), looking down at the gap. */
    private static void battleCam(int after) {
        add("battle camera", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.level.getEntity(navalShip) instanceof com.grandline.ship.ShipEntity)) navalShip = ownShip(mc);
            if (!(mc.level.getEntity(navalShip) instanceof com.grandline.ship.ShipEntity s)) return;
            com.grandline.ship.ShipEntity other = s.linkedId() >= 0 && mc.level.getEntity(s.linkedId()) instanceof com.grandline.ship.ShipEntity o ? o : null;
            if (other == null) for (var e : mc.level.entitiesForRendering())
                if (e instanceof com.grandline.ship.ShipEntity o && o != s && (other == null || o.distanceToSqr(s) < other.distanceToSqr(s))) other = o;
            Vec3 mid = other == null ? s.position() : s.position().add(other.position()).scale(0.5);
            Vec3 c = mid.subtract(s.heading().scale(24)).add(0, 13, 0);
            double dx = mid.x - c.x, dy = mid.y + 2 - c.y, dz = mid.z - c.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", c.x, c.y, c.z, yaw, pitch));
            LOG.add(String.format("  battle: %s hull %.0f rig %.0f%% vs %s hull %.0f rig %.0f%%, linked=%s", s.shipName(), s.hull(), s.rigging() * 100,
                    other == null ? "-" : other.shipName(), other == null ? 0 : other.hull(), other == null ? 0 : other.rigging() * 100, s.linkedId() >= 0));
        }, after);
    }

    /** Naval combat and customisation: figurehead and emblem close-ups, a figurehead parade, a gun duel, grapple and boarders. */
    private static void buildNaval() {
        Minecraft mc = Minecraft.getInstance();
        cmd("gamemode survival", 10);
        cmd("grandline island tp shiokaze_isle", 120);
        cmd("grandline test ship galleon helm", 80);
        // wait until the client knows it is riding the new ship (the passenger packet can lag behind on a slow client)
        steps.add(new Step("remember ship", () -> mc.player.getVehicle() instanceof com.grandline.ship.ShipEntity,
                () -> navalShip = mc.player.getVehicle() instanceof com.grandline.ship.ShipEntity s ? s.getId() : -1, 5));
        double bow = 31 / 2.0; // galleon
        cmd("ship test look lion skull 0,0,0,5", 20);
        cmd("grandline test anchor false", 5);
        cmd("grandline test sail 2", 260);
        cmd("grandline test anchor true", 5);
        cmd("grandline test sail 3", 60); // anchored with sails set: the emblem shows on the main sail
        cmd("ship test parade", 60);
        cmd("gamemode spectator", 10);
        shipCam("41_figurehead_lion", 4.5, 4.2, bow + 7, 0, 3.4, bow + 1, 80);
        shot("41_figurehead_lion");
        shipCam("42_sail_emblem_flag", -9, 9, bow + 15, 0, 11, 0, 80);
        shot("42_sail_emblem_flag");
        shipCam("43_figurehead_parade", 0, 9, bow + 2, 0, 1, bow + 30, 80);
        shot("43_figurehead_parade");
        cmd("ship test clear", 20);
        cmd("gamemode creative", 10); // boarders go for the hired gun crew, not a creative captain
        cmd("ship test helm", 20);
        cmd("grandline test sail 0", 10);
        cmd("ship test gunners 6", 20);
        cmd("ship ammo chain_shot", 5); // shred her rigging rather than sink her before the boarding
        cmd("ship test enemy galleon pirate", 140);
        camera(CameraType.THIRD_PERSON_BACK);
        shot("44_naval_gun_crews");
        camera(CameraType.FIRST_PERSON);
        cmd("ship status", 5);
        cmd("ship test grapple", 120);
        cmd("ship status", 5);
        cmd("gamemode spectator", 10);
        battleCam(50);
        shot("45_grappled_boarding");
        shipCam("46_boarders_on_deck", 10, 8, -8, 0, 2, 3, 50);
        shot("46_boarders_on_deck");
        cmd("ship test clear", 10);
        cmd("kill @e[type=grandline:npc,distance=..120]", 10);
        cmd("grandline island tp shiokaze_isle", 60);
        cmd("gamemode spectator", 10); // the solo script continues with spectator fly-bys
        add("open shipyard customise page", () -> {
            CompoundTag d = new CompoundTag();
            d.putLong("beli", 125000);
            d.putBoolean("customisePage", true);
            CompoundTag cur = new CompoundTag();
            cur.putString("name", "Tester's Galleon");
            cur.putString("cls", "galleon");
            cur.putString("look", "dragon|flame|ff8a20|5a2a7a");
            cur.putInt("sail", 0xF4EEDC);
            cur.putInt("hullc", 0x2A2A2A);
            d.put("current", cur);
            mc.setScreen(new com.grandline.client.screen.ShipyardScreen(d));
        }, 20);
        shot("47_shipyard_customise");
        add("close screen", () -> mc.setScreen(null), 10);
    }

    /** Sea Kings beside a ship in the Calm Belt, a Sea King attack, and Zoan full-beast forms (players and NPCs). */
    private static void creatureShots() {
        // the earlier fights can leave the tester dead or knocked down: respawn and recreate the character if needed
        add("respawn for creature shots", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player.isDeadOrDying() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) mc.player.respawn();
        }, 60);
        add("recreate character if needed", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (!ClientState.LOCAL.created) {
                CompoundTag t = new CompoundTag();
                t.putString("name", "Tester");
                t.putString("race", "fishman");
                t.putString("faction", "pirate");
                t.putString("origin", "east");
                t.put("appearance", new Appearance().save());
                PacketDistributor.sendToServer(new GLNetwork.CreateCharacter(t));
            }
            mc.setScreen(null);
        }, 60);
        steps.add(new Step("wait for character", () -> ClientState.LOCAL.created && Minecraft.getInstance().player.isAlive(), () -> Minecraft.getInstance().setScreen(null), 20));
        cmd("gamemode creative", 10);
        cmd("grandline test env", 10);
        cmd("grandline test calmbelt", 10);
        cmd("tp @s ~ ~ ~ 0 10", 260);
        cmd("grandline test ship galleon helm", 80);   // seated at the helm; the ship's bow points south
        look(0, 4);
        cmd("grandline test seaking serpent 2 hold 36", 80);
        shot("27_seaking_from_helm");
        cmd("gamemode spectator", 10);
        cmd("tp @s ~30 ~18 ~-14 0 26", 50);            // off the starboard beam, clear of the sails
        shot("28_seaking_and_ship_aerial");
        cmd("kill @e[type=grandline:sea_king]", 10);
        cmd("tp @s ~ 82 ~ 0 28", 20);
        cmd("grandline test seaking maw 3 hold 44", 80);
        shot("29_seaking_maw_colossal");
        cmd("kill @e[type=grandline:sea_king]", 10);
        cmd("tp @s ~ 74 ~ 0 26", 20);
        cmd("grandline test seaking horned 1 hold 22", 80);
        shot("30_seaking_horned");
        cmd("kill @e[type=grandline:sea_king]", 10);
        cmd("tp @s ~ 63.6 ~ 0 -8", 20);                // just under the surface
        cmd("grandline test seaking serpent 3 hold 40", 80);
        shot("31_seaking_from_below");
        cmd("kill @e[type=grandline:sea_king]", 10);
        if (CREATURES_ONLY) { // head close-ups of each variant
            for (String v : new String[]{"serpent", "maw", "horned"}) {
                cmd("grandline test seaking " + v + " 1 hold 20", 40);
                cmd("execute at @e[type=grandline:sea_king,limit=1] run tp @s ~-6 ~6 ~7 facing entity @e[type=grandline:sea_king,limit=1] feet", 30);
                shot("closeup_seaking_" + v);
                cmd("kill @e[type=grandline:sea_king]", 10);
            }
        }
        cmd("tp @s @e[type=grandline:ship_galleon,limit=1,sort=nearest]", 10);
        cmd("tp @s ~-22 ~30 ~-22 -45 48", 20);
        cmd("grandline test seaking horned 2", 240);
        shot("32_seaking_attack_1");
        add("wait", () -> {}, 90);
        shot("33_seaking_attack_2");
        cmd("kill @e[type=grandline:sea_king]", 10);
        // Zoan full-beast forms, on a flat stage above Shiokaze Isle
        cmd("gamemode creative", 10);
        cmd("grandline island tp shiokaze_isle", 220);
        cmd("tp @s ~ ~30 ~ 0 0", 20);
        cmd("fill ~-26 ~-1 ~-6 ~26 ~-1 ~30 minecraft:smooth_stone", 10);
        cmd("fill ~-26 ~ ~-6 ~26 ~14 ~30 minecraft:air", 20);
        cmd("tp @s ~ ~ ~ 0 0", 10);
        camera(CameraType.THIRD_PERSON_FRONT);
        for (String[] z : new String[][]{{"inu_wolf", "34_zoan_wolf"}, {"ryu_allosaurus", "35_zoan_allosaurus"}, {"uo_seiryu", "36_zoan_seiryu"}}) {
            cmd("grandline test zoan none", 5);
            cmd("grandline fruit set " + z[0], 10);
            cmd("grandline test zoan full", 30);
            look(-35, -4);
            add("wait", () -> {}, 20);
            shot(z[1]);
        }
        cmd("grandline test zoan none", 10);
        cmd("grandline fruit remove", 10);
        camera(CameraType.FIRST_PERSON);
        cmd("tp @s ~ ~ ~ 0 14", 10);
        String[][] lineups = {{"tori_phoenix", "zou_elephant", "tako_kraken", "hebi_orochi"}, {"neko_leopard", "ushi_bison", "ryu_pteranodon", "ryu_spinosaurus"},
                {"inu_wolf", "ryu_allosaurus", "uo_seiryu"}};
        String[] names = {"37_zoan_npcs_mythical", "38_zoan_npcs_beasts", "39_zoan_npcs_more"};
        for (int li = 0; li < lineups.length; li++) {
            int off = lineups[li].length == 4 ? -12 : -9;
            for (String f : lineups[li]) {
                cmd("grandline test beastnpc " + f + " " + off, 5);
                off += lineups[li].length == 4 ? 8 : 9;
            }
            add("wait", () -> {}, 40);
            shot(names[li]);
            cmd("kill @e[type=grandline:npc,distance=..40]", 10);
        }
        // island wildlife
        cmd("tp @s ~ ~ ~ 0 10", 10);
        String[] wild = {"kungfu_dugong", "lapahn", "banana_gator", "sea_cat"};
        for (int i = 0; i < wild.length; i++) cmd("grandline test wildlife " + wild[i] + " " + (-9 + i * 6) + " 9", 5);
        add("wait", () -> {}, 40);
        shot("40_island_wildlife");
        cmd("kill @e[type=!minecraft:player,distance=..40]", 10);
        if (CREATURES_ONLY) { // close-up of every beast form
            String[] all = {"inu_wolf", "neko_leopard", "ushi_bison", "tori_phoenix", "zou_elephant", "ryu_allosaurus", "ryu_pteranodon", "ryu_spinosaurus", "uo_seiryu", "hebi_orochi", "tako_kraken"};
            int[] dist = {4, 4, 5, 5, 7, 8, 6, 9, 9, 7, 7};
            look(0, 8);
            for (int i = 0; i < all.length; i++) {
                cmd("grandline test beastnpc " + all[i] + " 0 " + dist[i], 40);
                shot("zoo_" + all[i]);
                cmd("kill @e[type=grandline:npc,distance=..40]", 30);
            }
        }
    }

    private static void look(float yaw, float pitch) {
        add("look " + yaw + "/" + pitch, () -> {
            var p = Minecraft.getInstance().player;
            p.setYRot(yaw);
            p.setXRot(pitch);
            p.yRotO = yaw;
            p.xRotO = pitch;
        }, 10);
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
