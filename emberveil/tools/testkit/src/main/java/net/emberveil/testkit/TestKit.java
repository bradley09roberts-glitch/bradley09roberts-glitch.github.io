package net.emberveil.testkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scripted client automation for testing the Emberveil pack in a real client.
 * Activated only by {@code -Demberveil.testkit=/path/to/script.txt}; otherwise it registers nothing.
 * Each script line is one command; results go to {@code <gameDir>/testkit/results.jsonl} and the log.
 */
@Mod(value = "emberveil_testkit", dist = Dist.CLIENT)
public final class TestKit {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ResourceLocation ALMANAC = ResourceLocation.fromNamespaceAndPath("emberveil", "almanac");

    private final List<String> script = new ArrayList<>();
    private int pc = 0;
    private int waitTicks = 0;
    private int stepTicks = 0;
    private int phase = 0;
    private Object scratch;
    private final Path outDir;
    private int passes = 0, fails = 0;

    public TestKit(IEventBus modBus) {
        String scriptPath = System.getProperty("emberveil.testkit");
        outDir = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "testkit");
        if (scriptPath == null || scriptPath.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(outDir.resolve("shots"));
            for (String line : Files.readAllLines(Path.of(scriptPath), StandardCharsets.UTF_8)) {
                String t = line.strip();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    script.add(t);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("testkit: cannot read script " + scriptPath, e);
        }
        log("script loaded: " + script.size() + " commands");
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    // ------------------------------------------------------------------ plumbing

    private void log(String msg) {
        System.out.println("[TESTKIT] " + msg);
    }

    private void result(String name, boolean pass, String detail) {
        if (pass) passes++; else fails++;
        log((pass ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " :: " + detail));
        JsonObject o = new JsonObject();
        o.addProperty("check", name);
        o.addProperty("result", pass ? "PASS" : "FAIL");
        o.addProperty("detail", detail);
        o.addProperty("time", System.currentTimeMillis());
        appendLine(outDir.resolve("results.jsonl"), o.toString());
    }

    private void note(String name, String detail) {
        log("INFO " + name + " :: " + detail);
        JsonObject o = new JsonObject();
        o.addProperty("check", name);
        o.addProperty("result", "INFO");
        o.addProperty("detail", detail);
        appendLine(outDir.resolve("results.jsonl"), o.toString());
    }

    private static void appendLine(Path p, String line) {
        try {
            Files.writeString(p, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[TESTKIT] cannot write " + p + ": " + e);
        }
    }

    private void writeJson(String file, Object json) {
        try (Writer w = Files.newBufferedWriter(outDir.resolve(file), StandardCharsets.UTF_8)) {
            GSON.toJson(json, w);
        } catch (IOException e) {
            log("cannot write " + file + ": " + e);
        }
    }

    /** Advance to the next command. */
    private void next() {
        pc++;
        stepTicks = 0;
        phase = 0;
        scratch = null;
    }

    private void tick(ClientTickEvent.Post event) {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (pc >= script.size()) {
            return;
        }
        String line = script.get(pc);
        String[] a = line.split("\\s+");
        String cmd = a[0];
        String rest = line.length() > cmd.length() ? line.substring(cmd.length()).strip() : "";
        stepTicks++;
        if (stepTicks == 1) {
            log(">> " + line);
        }
        try {
            if (run(cmd, a, rest)) {
                next();
            } else if (stepTicks > timeoutFor(cmd)) {
                result("timeout:" + line, false, "command did not complete in time; screen=" + screenName());
                next();
            }
        } catch (Throwable t) {
            result("error:" + line, false, t.toString());
            t.printStackTrace();
            next();
        }
    }

    private int timeoutFor(String cmd) {
        return switch (cmd) {
            case "create_world", "load_world", "wait_ingame" -> 20 * 60 * 12; // world gen on llvmpipe is slow
            case "wait_title" -> 20 * 60 * 5;
            case "locate_tp" -> 20 * 60 * 6;
            default -> 20 * 60 * 3;
        };
    }

    private String screenName() {
        Screen s = Minecraft.getInstance().screen;
        return s == null ? "none" : s.getClass().getName();
    }

    // ------------------------------------------------------------------ commands

    private boolean run(String cmd, String[] a, String rest) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        switch (cmd) {
            case "log" -> { log(rest); return true; }
            case "wait" -> { waitTicks = Integer.parseInt(a[1]); return true; }
            case "wait_title" -> { return mc.screen instanceof TitleScreen || screenName().contains("AccessibilityOnboarding"); }
            case "dismiss_onboarding" -> {
                if (screenName().contains("AccessibilityOnboarding")) {
                    mc.options.onboardAccessibility = false;
                    mc.options.save();
                    mc.setScreen(new TitleScreen(true));
                }
                return mc.screen instanceof TitleScreen;
            }
            case "create_world" -> { return createWorld(mc, a[1], a.length > 2 ? a[2] : ""); }
            case "load_world" -> {
                if (phase == 0) {
                    mc.createWorldOpenFlows().openWorld(a[1], () -> mc.setScreen(new TitleScreen()));
                    phase = 1;
                    return false;
                }
                return inGame(mc);
            }
            case "wait_ingame" -> {
                if (!inGame(mc)) return false;
                if (scratch == null) scratch = 0;
                scratch = (Integer) scratch + 1;
                return (Integer) scratch >= (a.length > 1 ? Integer.parseInt(a[1]) : 100);
            }
            case "screenshot" -> { screenshot(mc, a[1], false); return true; }
            case "screenshot_nohud" -> {
                // Hide the HUD, let a few frames render without it, then capture.
                if (phase == 0) { scratch = mc.options.hideGui; mc.options.hideGui = true; phase = 1; waitTicks = 4; return false; }
                screenshot(mc, a[1], false);
                mc.options.hideGui = (Boolean) scratch;
                return true;
            }
            case "hud" -> { mc.options.hideGui = !"off".equals(a[1]); return true; }
            case "key" -> {
                KeyMapping km = findKey(mc, a[1]);
                if (km == null) { result("key:" + a[1], false, "no such key mapping"); return true; }
                KeyMapping.click(km.getKey());
                return true;
            }
            case "assert_screen" -> {
                boolean ok = mc.screen != null && mc.screen.getClass().getName().contains(a[1]);
                result("screen contains " + a[1], ok, screenName());
                return true;
            }
            case "assert_no_screen" -> { result("no screen open", mc.screen == null, screenName()); return true; }
            case "close_screen" -> { mc.setScreen(null); return true; }
            case "esc" -> { if (mc.screen != null) mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); return true; }
            case "backspace" -> { if (mc.screen != null) mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0); return true; }
            case "server" -> { serverCommand(mc, rest, true); return true; }
            case "chat_command" -> {
                // Sends as the player would type it (client commands are intercepted client-side first).
                if (mc.player != null) mc.player.connection.sendCommand(rest);
                return true;
            }
            case "dump_client" -> { dumpClient(mc, a[1]); return true; }
            case "dump_server" -> { return dumpServer(mc, a[1]); }
            case "book" -> {
                vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(ALMANAC, ResourceLocation.parse(a[1]), a.length > 2 ? Integer.parseInt(a[2]) : 0);
                return true;
            }
            case "book_landing" -> { vazkii.patchouli.api.PatchouliAPI.get().openBookGUI(ALMANAC); return true; }
            case "press_widget" -> { return pressWidget(mc, a[1], Integer.parseInt(a[2])); }
            case "type_search" -> { return typeSearch(mc, rest); }
            case "count_widgets" -> {
                int n = widgets(mc, a[1]).size();
                note("widgets " + a[1], String.valueOf(n));
                return true;
            }
            case "assert_almanac_count" -> {
                int n = almanacCount(mc);
                result("almanac count == " + a[1], n == Integer.parseInt(a[1]), "found " + n);
                return true;
            }
            case "assert_almanac_dropped" -> { return assertAlmanacDropped(mc); }
            case "reset_almanac_flag" -> { resetAlmanacFlag(mc); return true; }
            case "respawn" -> {
                if (mc.screen instanceof DeathScreen && mc.player != null) {
                    mc.player.respawn();
                    mc.setScreen(null);
                    return true;
                }
                return mc.player != null && mc.player.isAlive() && mc.screen == null;
            }
            case "save_quit" -> {
                if (phase == 0) {
                    if (mc.level != null) {
                        mc.level.disconnect();
                        mc.disconnect(new GenericMessageScreen(Component.literal("Saving world")));
                        mc.setScreen(new TitleScreen());
                    }
                    phase = 1;
                    return false;
                }
                return mc.level == null && mc.screen instanceof TitleScreen;
            }
            case "look" -> {
                if (mc.player != null) { mc.player.setYRot(Float.parseFloat(a[1])); mc.player.setXRot(Float.parseFloat(a[2])); }
                return true;
            }
            case "tp_surface" -> { return tpSurface(mc, Integer.parseInt(a[1]), Integer.parseInt(a[2])); }
            case "locate_tp" -> { return locateTp(mc, a[1], a.length > 2 ? a[2] : "top"); }
            case "iris" -> { irisSet(a[1].equals("on")); return true; }
            case "iris_options" -> { irisOptions(a.length > 1 ? a[1] : "", rest.contains(" ") ? rest.substring(rest.indexOf(' ') + 1) : ""); return true; }
            case "set_render_distance" -> { mc.options.renderDistance().set(Integer.parseInt(a[1])); mc.options.save(); return true; }
            case "wait_render" -> {
                // Wait until the chunk renderer reports no pending work, or a fixed number of ticks.
                if (scratch == null) scratch = 0;
                scratch = (Integer) scratch + 1;
                boolean done = false;
                try { done = mc.levelRenderer.hasRenderedAllSections(); } catch (Throwable ignored) { }
                return ((Integer) scratch > 40 && done) || (Integer) scratch > (a.length > 1 ? Integer.parseInt(a[1]) : 600);
            }
            case "summary" -> {
                result("testkit summary", fails == 0, passes + " passed, " + fails + " failed");
                return true;
            }
            case "quit" -> { mc.stop(); return true; }
            default -> { result("unknown command " + cmd, false, ""); return true; }
        }
    }

    private static boolean inGame(Minecraft mc) {
        return mc.level != null && mc.player != null && (mc.screen == null || mc.screen instanceof DeathScreen);
    }

    private boolean createWorld(Minecraft mc, String name, String seed) {
        if (phase == 0) {
            CreateWorldScreen.openFresh(mc, mc.screen);
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (!(mc.screen instanceof CreateWorldScreen cws)) return false;
            WorldCreationUiState ui = cws.getUiState();
            ui.setName(name);
            if (!seed.isEmpty()) ui.setSeed(seed);
            ui.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
            ui.setDifficulty(Difficulty.NORMAL);
            // Cheats stay OFF, as for a normal player; the kit runs checks with server-side permissions.
            ui.setAllowCommands(false);
            for (GuiEventListener l : cws.children()) {
                if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                        && tc.getKey().equals("selectWorld.create")) {
                    note("create_world", "pressing Create New World for '" + name + "' seed='" + seed + "'");
                    b.onPress();
                    phase = 2;
                    return false;
                }
            }
            result("create_world button", false, "Create button not found");
            return true;
        }
        return inGame(mc);
    }

    private void screenshot(Minecraft mc, String name, boolean noHud) {
        boolean prev = mc.options.hideGui;
        if (noHud) mc.options.hideGui = true;
        // Minecraft writes to <dir>/screenshots/<name>; the folder is created by Screenshot.grab.
        File dir = outDir.toFile();
        Screenshot.grab(dir, name + ".png", mc.getMainRenderTarget(), msg -> { });
        mc.options.hideGui = prev;
        note("screenshot", name + ".png screen=" + screenName());
    }

    private static KeyMapping findKey(Minecraft mc, String name) {
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.getName().equals(name)) return km;
        }
        return null;
    }

    private void serverCommand(Minecraft mc, String command, boolean record) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { result("server:" + command, false, "no integrated server"); return; }
        List<String> out = new ArrayList<>();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            CommandSourceStack src = captureSource(server, sp, out);
            try {
                server.getCommands().performPrefixedCommand(src, command);
                f.complete(1);
            } catch (Throwable t) {
                out.add("EXCEPTION " + t);
                f.complete(0);
            }
        });
        try { f.get(60, TimeUnit.SECONDS); } catch (Exception e) { out.add("TIMEOUT " + e); }
        if (record) note("server:" + command, String.join(" | ", out));
        scratch = out;
    }

    private static CommandSourceStack captureSource(MinecraftServer server, ServerPlayer sp, List<String> out) {
        CommandSource sink = new CommandSource() {
            @Override public void sendSystemMessage(Component c) { out.add(c.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        ServerLevel lvl = sp != null ? sp.serverLevel() : server.overworld();
        Vec3 pos = sp != null ? sp.position() : Vec3.ZERO;
        Vec2 rot = sp != null ? sp.getRotationVector() : Vec2.ZERO;
        return new CommandSourceStack(sink, pos, rot, lvl, 4, "testkit", Component.literal("testkit"), server, sp);
    }

    private List<AbstractWidget> widgets(Minecraft mc, String classSubstring) {
        List<AbstractWidget> list = new ArrayList<>();
        if (mc.screen == null) return list;
        for (GuiEventListener l : mc.screen.children()) {
            if (l instanceof AbstractWidget w && w.getClass().getName().contains(classSubstring) && w.visible) list.add(w);
        }
        return list;
    }

    private boolean pressWidget(Minecraft mc, String cls, int index) {
        List<AbstractWidget> ws = widgets(mc, cls);
        if (index >= ws.size()) { result("press_widget " + cls + "#" + index, false, "only " + ws.size() + " widgets on " + screenName()); return true; }
        AbstractWidget w = ws.get(index);
        // Same path as a real click: mouseClicked at the widget centre on the current screen.
        double x = w.getX() + w.getWidth() / 2.0, y = w.getY() + w.getHeight() / 2.0;
        boolean handled = mc.screen.mouseClicked(x, y, 0);
        mc.screen.mouseReleased(x, y, 0);
        note("press_widget", cls + "#" + index + " -> handled=" + handled + " now=" + screenName());
        return true;
    }

    private boolean typeSearch(Minecraft mc, String text) {
        if (mc.screen == null) { result("type_search", false, "no screen"); return true; }
        for (GuiEventListener l : mc.screen.children()) {
            if (l instanceof EditBox box) {
                box.setFocused(true);
                for (char c : text.toCharArray()) mc.screen.charTyped(c, 0);
                note("type_search", "typed '" + text + "' into search; value=" + box.getValue());
                return true;
            }
        }
        result("type_search", false, "no EditBox on " + screenName());
        return true;
    }

    private static boolean isAlmanac(ItemStack s) {
        if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("patchouli:guide_book")) return false;
        for (var e : s.getComponents()) {
            if (String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(e.type())).equals("patchouli:book")
                    && String.valueOf(e.value()).equals(ALMANAC.toString())) return true;
        }
        return false;
    }

    private static int almanacCount(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return -1;
        ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (sp == null) return -1;
        int n = 0;
        for (ItemStack s : sp.getInventory().items) if (isAlmanac(s)) n += s.getCount();
        for (ItemStack s : sp.getInventory().offhand) if (isAlmanac(s)) n += s.getCount();
        return n;
    }

    private boolean assertAlmanacDropped(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
        List<ItemEntity> items = sp.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(sp.blockPosition()).inflate(6));
        long n = items.stream().filter(e -> isAlmanac(e.getItem())).count();
        result("almanac dropped at feet when inventory full", n >= 1, n + " almanac item entities nearby, inventory count=" + almanacCount(mc));
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resetAlmanacFlag(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        AttachmentType type = NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.fromNamespaceAndPath("emberveil", "received_almanac"));
        if (type == null) { result("reset_almanac_flag", false, "attachment type not registered"); return; }
        CompletableFuture<Void> f = new CompletableFuture<>();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            sp.setData(type, Boolean.FALSE);
            f.complete(null);
        });
        try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) { }
        note("reset_almanac_flag", "received_almanac=false");
    }

    private boolean tpSurface(Minecraft mc, int x, int z) {
        MinecraftServer server = mc.getSingleplayerServer();
        CompletableFuture<String> f = new CompletableFuture<>();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel lvl = sp.serverLevel();
            lvl.getChunk(x >> 4, z >> 4);
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            sp.teleportTo(lvl, x + 0.5, y + 1, z + 0.5, sp.getYRot(), sp.getXRot());
            f.complete(x + " " + y + " " + z);
        });
        try { note("tp_surface", f.get(120, TimeUnit.SECONDS)); } catch (Exception e) { result("tp_surface", false, e.toString()); }
        return true;
    }

    /** /locate the structure (as the server), then teleport to its bounding box (top or centre). */
    @SuppressWarnings("unchecked")
    private boolean locateTp(Minecraft mc, String structureId, String where) {
        MinecraftServer server = mc.getSingleplayerServer();
        serverCommand(mc, "locate structure " + structureId, false);
        List<String> out = (List<String>) scratch;
        String joined = String.join(" | ", out);
        Matcher m = Pattern.compile("\\[(-?\\d+), ~?(-?\\d+)?,? ?(-?\\d+)\\]").matcher(joined);
        if (!m.find()) {
            Matcher m2 = Pattern.compile("\\[(-?\\d+), ~, (-?\\d+)\\]").matcher(joined);
            if (!m2.find()) { result("locate " + structureId, false, joined); return true; }
            m = m2;
        }
        int x = Integer.parseInt(m.group(1));
        int z = Integer.parseInt(m.group(m.groupCount()));
        CompletableFuture<String> f = new CompletableFuture<>();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel lvl = sp.serverLevel();
            var chunk = lvl.getChunk(x >> 4, z >> 4);
            Optional<Holder.Reference<Structure>> sh = lvl.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolder(ResourceLocation.parse(structureId));
            BoundingBox bb = null;
            if (sh.isPresent()) {
                StructureStart start = lvl.structureManager().getStartForStructure(
                        net.minecraft.core.SectionPos.of(new BlockPos(x, 0, z)), sh.get().value(), chunk);
                if (start != null && start.isValid()) bb = start.getBoundingBox();
            }
            int tx = x, tz = z, ty;
            if (bb != null) {
                tx = bb.getCenter().getX();
                tz = bb.getCenter().getZ();
            }
            lvl.getChunk(tx >> 4, tz >> 4);
            int surface = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
            if (bb != null && where.equals("inside")) ty = bb.getCenter().getY();
            else if (bb != null && where.equals("top")) ty = Math.max(surface, bb.maxY()) + 2;
            else ty = surface + 1;
            sp.teleportTo(lvl, tx + 0.5, ty, tz + 0.5, sp.getYRot(), 30f);
            f.complete("found " + structureId + " at " + x + "," + z + " bbox=" + (bb == null ? "?" : bb.toString()) + " -> tp " + tx + " " + ty + " " + tz);
        });
        try { result("locate " + structureId, true, f.get(240, TimeUnit.SECONDS)); } catch (Exception e) { result("locate " + structureId, false, e.toString()); }
        return true;
    }

    // ------------------------------------------------------------------ dumps

    private void dumpClient(Minecraft mc, String file) {
        JsonObject root = new JsonObject();
        JsonArray keys = new JsonArray();
        for (KeyMapping km : mc.options.keyMappings) {
            JsonObject k = new JsonObject();
            k.addProperty("name", km.getName());
            k.addProperty("category", km.getCategory());
            k.addProperty("default", km.getDefaultKey().getName());
            k.addProperty("current", km.getKey().getName());
            k.addProperty("modifier", String.valueOf(km.getKeyModifier()));
            k.addProperty("default_modifier", String.valueOf(km.getDefaultKeyModifier()));
            k.addProperty("context", String.valueOf(km.getKeyConflictContext()));
            keys.add(k);
        }
        root.add("key_mappings", keys);
        JsonArray mods = new JsonArray();
        ModList.get().getMods().forEach(mi -> mods.add(mi.getModId() + " " + mi.getVersion()));
        root.add("mods", mods);
        writeJson(file, root);
        note("dump_client", file + " (" + keys.size() + " key mappings, " + mods.size() + " mods)");
    }

    private boolean dumpServer(Minecraft mc, String file) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { result("dump_server", false, "no server"); return true; }
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        server.execute(() -> {
            JsonObject root = new JsonObject();
            var ra = server.registryAccess();
            root.add("structures", ids(ra.registryOrThrow(Registries.STRUCTURE).keySet()));
            root.add("structure_sets", ids(ra.registryOrThrow(Registries.STRUCTURE_SET).keySet()));
            root.add("biomes", ids(ra.registryOrThrow(Registries.BIOME).keySet()));
            root.add("loot_tables", ids(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE)));
            JsonObject recipes = new JsonObject();
            for (var holder : server.getRecipeManager().getRecipes()) {
                ItemStack res = holder.value().getResultItem(ra);
                recipes.addProperty(holder.id().toString(), BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType())
                        + " -> " + (res.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(res.getItem()).toString()));
            }
            root.add("recipes", recipes);
            JsonArray adv = new JsonArray();
            server.getAdvancements().getAllAdvancements().forEach(h -> adv.add(h.id().toString()));
            root.add("advancements", adv);
            // Items with the stats a player sees: durability, rarity, attack/armour modifiers.
            JsonObject items = new JsonObject();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                ItemStack st = new ItemStack(item);
                JsonObject o = new JsonObject();
                o.addProperty("name", st.getHoverName().getString());
                if (st.getMaxDamage() > 0) o.addProperty("durability", st.getMaxDamage());
                o.addProperty("rarity", st.getRarity().name());
                o.addProperty("stack", st.getMaxStackSize());
                ItemAttributeModifiers mods = st.getAttributeModifiers();
                JsonArray am = new JsonArray();
                mods.modifiers().forEach(e -> am.add(e.attribute().getRegisteredName() + " " + e.modifier().operation().name()
                        + " " + e.modifier().amount() + " @" + e.slot().name()));
                if (!am.isEmpty()) o.add("modifiers", am);
                var food = st.get(net.minecraft.core.component.DataComponents.FOOD);
                if (food != null) o.addProperty("food", food.nutrition() + " / " + food.saturation());
                items.add(id.toString(), o);
            }
            root.add("items", items);
            JsonObject ents = new JsonObject();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                JsonObject o = new JsonObject();
                o.addProperty("name", type.getDescription().getString());
                o.addProperty("category", type.getCategory().getName());
                o.addProperty("width", type.getWidth());
                o.addProperty("height", type.getHeight());
                o.addProperty("fire_immune", type.fireImmune());
                if (DefaultAttributes.hasSupplier(type)) {
                    @SuppressWarnings("unchecked")
                    AttributeSupplier sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
                    for (Map.Entry<String, Holder<Attribute>> e : Map.of(
                            "max_health", Attributes.MAX_HEALTH, "armor", Attributes.ARMOR, "armor_toughness", Attributes.ARMOR_TOUGHNESS,
                            "attack_damage", Attributes.ATTACK_DAMAGE, "movement_speed", Attributes.MOVEMENT_SPEED,
                            "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE, "follow_range", Attributes.FOLLOW_RANGE,
                            "flying_speed", Attributes.FLYING_SPEED).entrySet()) {
                        if (sup.hasAttribute(e.getValue())) o.addProperty(e.getKey(), sup.getBaseValue(e.getValue()));
                    }
                }
                ents.add(id.toString(), o);
            }
            root.add("entities", ents);
            f.complete(root);
        });
        try {
            JsonObject root = f.get(120, TimeUnit.SECONDS);
            writeJson(file, root);
            note("dump_server", file + " written");
        } catch (Exception e) {
            result("dump_server", false, e.toString());
        }
        return true;
    }

    private static JsonArray ids(Iterable<ResourceLocation> keys) {
        List<String> l = new ArrayList<>();
        keys.forEach(k -> l.add(k.toString()));
        l.sort(String::compareTo);
        JsonArray arr = new JsonArray();
        l.forEach(arr::add);
        return arr;
    }

    // ------------------------------------------------------------------ Iris (via reflection; optional)

    private void irisSet(boolean on) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            Object cfg = api.getMethod("getConfig").invoke(inst);
            cfg.getClass().getMethod("setShadersEnabledAndApply", boolean.class).invoke(cfg, on);
            Object inUse = api.getMethod("isShaderPackInUse").invoke(inst);
            note("iris", "shaders " + (on ? "on" : "off") + " -> packInUse=" + inUse);
        } catch (Throwable t) {
            result("iris " + on, false, t.toString());
        }
    }

    /** Select a pack (by file name) and write its option file, then reload Iris. */
    private void irisOptions(String pack, String opts) {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Path dir = (Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
            StringBuilder sb = new StringBuilder();
            for (String kv : opts.split("\\s+")) if (kv.contains("=")) sb.append(kv).append('\n');
            Files.writeString(dir.resolve(pack + ".txt"), sb.toString(), StandardCharsets.UTF_8);
            Object cfg = iris.getMethod("getIrisConfig").invoke(null);
            cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, pack);
            cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, true);
            cfg.getClass().getMethod("save").invoke(cfg);
            iris.getMethod("reload").invoke(null);
            Object packName = iris.getMethod("getCurrentPackName").invoke(null);
            note("iris_options", pack + " [" + opts + "] -> current=" + packName);
        } catch (Throwable t) {
            result("iris_options", false, t.toString());
        }
    }
}
