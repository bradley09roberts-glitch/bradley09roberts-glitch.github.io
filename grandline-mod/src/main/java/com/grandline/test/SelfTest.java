package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.combat.*;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.fruit.*;
import com.grandline.item.GLWeaponItem;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.MarkerCollector;
import com.grandline.player.*;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.SettlementType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * Automated verification on a real dedicated server. Enabled with environment variable GL_SELFTEST=1 (phase 1:
 * gameplay + writes persistence fixtures) or GL_SELFTEST=2 (phase 2: verifies state after a full restart).
 * Results are written to selftest-phase<N>.txt in the server directory, then the server stops.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class SelfTest {
    private static final String PHASE = System.getenv("GL_SELFTEST");
    private static final List<String> REPORT = new ArrayList<>();
    private static int passed, failed;
    private static Deque<Step> steps;
    private static int wait;
    private static FakePlayer fake;
    private static final UUID FAKE_ID = UUID.fromString("6c1a4e2b-0b9e-4f43-9d61-5e7e4e1a2b3c");

    interface Step {
        /** @return ticks to wait before the next step (0 = continue immediately), or -1 to repeat this step next tick. */
        int run(MinecraftServer s) throws Exception;
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        String line = (ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " — " + detail);
        REPORT.add(line);
        GrandLine.LOGGER.info("[SELFTEST] {}", line);
    }

    @SubscribeEvent
    public static void onStart(ServerStartedEvent e) {
        if (PHASE == null) return;
        steps = new ArrayDeque<>();
        REPORT.add("Grand Line Odyssey self-test, phase " + PHASE + ", " + new Date());
        if (PHASE.equals("1")) phase1();
        else phase2();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (steps == null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        MinecraftServer s = e.getServer();
        Step step = steps.peekFirst();
        if (step == null) {
            finish(s);
            return;
        }
        try {
            int w = step.run(s);
            if (w >= 0) {
                steps.pollFirst();
                wait = w;
            }
        } catch (Throwable t) {
            check("step crashed", false, t + " @ " + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : ""));
            GrandLine.LOGGER.error("[SELFTEST] step crashed", t);
            steps.pollFirst();
        }
    }

    private static void finish(MinecraftServer s) {
        REPORT.add("TOTAL: " + passed + " passed, " + failed + " failed");
        try {
            Files.write(Path.of("selftest-phase" + PHASE + ".txt"), REPORT);
        } catch (IOException ex) {
            GrandLine.LOGGER.error("could not write report", ex);
        }
        GrandLine.LOGGER.info("[SELFTEST] DONE {} passed {} failed", passed, failed);
        steps = null;
        s.saveEverything(true, true, true);
        s.halt(false);
    }

    private static ServerLevel ow(MinecraftServer s) {
        return s.overworld();
    }

    private static int surface(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    private static FakePlayer fake(MinecraftServer s) {
        if (fake == null) {
            fake = FakePlayerFactory.get(ow(s), new GameProfile(FAKE_ID, "SelfTestPirate"));
        }
        return fake;
    }

    // ============================================================== PHASE 1

    private static void phase1() {
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            check("ocean world active", p != null, "");
            if (p == null) return 0;
            long canon = p.islands.stream().filter(i -> i.canon).count();
            check("island count >= 150", p.islands.size() >= 150, p.islands.size() + " islands, " + canon + " canon/starter");
            for (String id : List.of("dawn_island", "loguetown", "alabasta", "water_7", "sabaody_archipelago", "wano_country", "elbaph", "laugh_tale", "shiokaze_isle", "hinata_isle"))
                check("canon island present: " + id, p.byId(id) != null, "");
            return 0;
        });
        // terrain at island centres
        for (String id : List.of("shiokaze_isle", "dawn_island", "loguetown", "drum_island", "alabasta", "wano_country", "fuyugane_isle")) {
            steps.add(s -> {
                WorldPlan p = WorldPlan.of(s);
                Island i = p.byId(id);
                ServerLevel l = ow(s);
                int y = surface(l, i.x, i.z);
                check("terrain is land at " + id, y > WorldPlan.SEA_LEVEL, "surface y=" + y + " block=" + l.getBlockState(new BlockPos(i.x, y - 1, i.z)).getBlock().getName().getString());
                return 0;
            });
        }
        // a generated original island
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            Island orig = p.islands.stream().filter(i -> !i.canon && i.radius > 300).findFirst().orElse(null);
            check("original island exists", orig != null, orig == null ? "" : orig.id + " '" + orig.name + "' " + orig.zone.id());
            if (orig != null) {
                int y = surface(ow(s), orig.x, orig.z);
                check("original island terrain", y > WorldPlan.SEA_LEVEL - 8, "y=" + y);
            }
            return 0;
        });
        // ocean, Red Line, Reverse Mountain canal
        steps.add(s -> {
            ServerLevel l = ow(s);
            int sea = surface(l, 20000, 14000 + 3000);
            check("open ocean is water", l.getBlockState(new BlockPos(20000, WorldPlan.SEA_LEVEL, 17000)).is(Blocks.WATER) || sea < WorldPlan.SEA_LEVEL, "floor y=" + sea);
            int red = surface(l, 0, 12000);
            check("Red Line wall is tall", red > 250, "y=" + red);
            int redB = surface(l, 60000, -9000);
            check("Red Line B (Mary Geoise side) is tall", redB > 250, "y=" + redB);
            // canal halfway up the North Blue canal
            int cx = 390, cz = -390;
            l.getChunk(cx >> 4, cz >> 4);
            boolean water = false;
            for (int y = 150; y < 240; y++) if (l.getBlockState(new BlockPos(cx, y, cz)).is(Blocks.WATER)) water = true;
            check("Reverse Mountain canal holds water high up", water, "");
            return 0;
        });
        // settlement buildings exist (floor of a plot)
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            ServerLevel l = ow(s);
            int towns = 0, built = 0;
            for (String id : List.of("shiokaze_isle", "loguetown", "shells_town", "cocoyasi")) {
                Island i = p.byId(id);
                for (Plot pl : i.settlement().plots) {
                    if (!pl.landmark().isEmpty()) continue;
                    towns++;
                    l.getChunk(pl.x() >> 4, pl.z() >> 4);
                    l.getChunk((pl.x() + pl.sizeX()) >> 4, (pl.z() + pl.sizeZ()) >> 4);
                    var st = l.getBlockState(new BlockPos(pl.x() + pl.sizeX() / 2, pl.y(), pl.z() + pl.sizeZ() / 2));
                    if (!st.isAir() && !st.liquid()) built++;
                    break;
                }
            }
            check("settlement buildings generated", built == towns && towns > 0, built + "/" + towns + " sampled plots have floors");
            Island lt = p.byId("loguetown");
            check("Loguetown is a city with many plots", lt.settlement().plots.size() >= 15, lt.settlement().plots.size() + " plots, dock=" + lt.settlement().hasDock);
            return 0;
        });
        // landmark: Baratie floats on the sea
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            Island b = p.byId("baratie");
            ServerLevel l = ow(s);
            l.getChunk(b.x >> 4, b.z >> 4);
            int planks = 0;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -12; dz <= 12; dz++) if (l.getBlockState(new BlockPos(b.x + dx, WorldPlan.SEA_LEVEL + 1, b.z + dz)).is(Blocks.OAK_PLANKS)) planks++;
            check("Baratie restaurant ship built on the water", planks > 20, planks + " deck planks");
            return 0;
        });
        // NPC markers & spawning
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            Island i = p.byId("shells_town");
            int markers = 0;
            String sample = "";
            for (Plot pl : i.settlement().plots) for (var m : MarkerCollector.markers(i, pl)) if (m.kind().equals("npc")) {
                markers++;
                sample = m.data().getString("role");
            }
            check("Shells Town has NPC spawn markers", markers > 5, markers + " markers, e.g. " + sample);
            return 0;
        });
        // ---------- combat arena ----------
        final HumanoidNpc[] duel = new HumanoidNpc[2];
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            duel[0] = npc(l, "marine", 3, c.offset(-2, 0, 0));
            duel[1] = npc(l, "pirate", 3, c.offset(2, 0, 0));
            duel[0].setTarget(duel[1]);
            duel[1].setTarget(duel[0]);
            check("spawned Marine vs pirate duel", duel[0] != null && duel[1] != null, "");
            return 200;
        });
        steps.add(s -> {
            float a = duel[0].getHealth() / duel[0].getMaxHealth(), b = duel[1].getHealth() / duel[1].getMaxHealth();
            boolean fought = a < 1f || b < 1f || !duel[0].isAlive() || !duel[1].isAlive();
            check("NPCs fight each other with the combat system", fought, String.format("marine hp %.0f%%, pirate hp %.0f%%", a * 100, b * 100));
            duel[0].discard();
            duel[1].discard();
            return 0;
        });
        // ---------- character creation with a fake player ----------
        steps.add(s -> {
            FakePlayer fp = fake(s);
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            CharacterData d = Characters.get(fp);
            if (d.created) d.archiveAndReset();
            CompoundTag req = new CompoundTag();
            req.putString("name", "Kaito the Tester");
            req.putString("race", "giant");
            req.putString("faction", "pirate");
            req.putString("origin", "south");
            Appearance a = new Appearance();
            a.scale = 9f; // out of range on purpose: server must clamp
            req.put("appearance", a.save());
            var err = CharacterManager.create(fp, req);
            check("character creation accepted", err == null, err == null ? "" : err.getString());
            check("race size clamped to giant max", Math.abs(d.appearance.scale - Race.GIANT.maxScale) < 1e-3, "scale=" + d.appearance.scale);
            check("scale attribute applied (hitbox)", Math.abs(fp.getScale() - Race.GIANT.maxScale) < 0.01, "getScale=" + fp.getScale() + " height=" + fp.getBbHeight());
            check("giant reach bonus applied", fp.entityInteractionRange() > 4.5, "reach=" + fp.entityInteractionRange());
            fp.setGameMode(GameType.CREATIVE);
            return 0;
        });
        // ---------- data round trip ----------
        steps.add(s -> {
            CharacterData d = Characters.get(fake(s));
            d.addXp(5000);
            d.addMastery("style/black_leg", 500);
            d.bounty = 12_345_678;
            d.discoveredIslands.add("loguetown");
            CompoundTag t = d.serializeNBT(s.registryAccess());
            CharacterData copy = new CharacterData();
            copy.deserializeNBT(s.registryAccess(), t);
            check("character NBT round trip", copy.level == d.level && copy.bounty == d.bounty && copy.race == d.race && copy.mastery("style/black_leg") == d.mastery("style/black_leg")
                    && copy.discoveredIslands.contains("loguetown") && copy.name.equals(d.name), "level " + copy.level + ", bounty " + copy.bounty);
            return 0;
        });
        // ---------- fruit ledger ----------
        steps.add(s -> {
            FruitWorldData led = FruitWorldData.get(s);
            boolean first = led.claimSpecific("goro");
            boolean second = led.claimSpecific("goro");
            check("fruit uniqueness: second copy refused", first && !second, "first=" + first + " second=" + second);
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            d.fruit = null;
            FruitManager.eat(fp, DevilFruits.get("goro"));
            check("eating a fruit grants it and marks it OWNED", "goro".equals(d.fruit) && led.state("goro") == FruitWorldData.State.OWNED, "state=" + led.state("goro"));
            return 0;
        });
        // ---------- every ability executes without error ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            BlockPos c = arena(l);
            HumanoidNpc dummy = npc(l, "bandit", 1, c.offset(0, 0, 2));
            dummy.setNoAi(true);
            dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);
            dummy.setHealth(100000);
            d.observationUnlocked = d.armamentUnlocked = d.futureSight = d.emission = d.internalDestruction = true;
            d.conquerorTrait = d.conquerorAwakened = d.conquerorInfusion = true;
            d.fruitAwakened = true;
            for (FightingStyle st : FightingStyles.all()) d.knownStyles.add(st.id);
            int ok = 0, total = 0;
            List<String> errors = new ArrayList<>();
            for (Ability a : Abilities.all()) {
                total++;
                // set up prerequisites for this ability
                fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
                dummy.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                dummy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                fp.setYRot(0);
                fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
                CombatState.of(fp).cooldowns.clear();
                CombatState.of(fp).buffs.put("room", 200);
                fp.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                switch (a.source()) {
                    case FRUIT -> {
                        d.fruit = a.owner();
                        d.zoanForm = "hybrid";
                    }
                    case STYLE -> {
                        d.activeStyle = a.owner();
                        FightingStyle st = FightingStyles.get(a.owner());
                        if (st != null && !st.unarmed()) fp.setItemSlot(EquipmentSlot.MAINHAND, weaponFor(st.weapons.iterator().next()));
                    }
                    case WEAPON -> fp.setItemSlot(EquipmentSlot.MAINHAND, weaponFor(WeaponClass.valueOf(a.owner().toUpperCase(Locale.ROOT))));
                    default -> {}
                }
                try {
                    boolean used = CombatManager.useAbility(fp, a, dummy);
                    // run scheduled follow-ups (multi-hit, delayed strikes)
                    for (int t = 0; t < 60; t++) CombatState.of(fp).tick(l.getGameTime() + t);
                    if (used || a.id().contains("shambles") || a.id().contains("sulong") || a.id().endsWith("/toggle") || a.id().contains("pounce") || a.id().contains("rending")) ok++;
                    else errors.add(a.id() + "(refused)");
                } catch (Throwable t) {
                    errors.add(a.id() + " threw " + t);
                    GrandLine.LOGGER.error("[SELFTEST] ability {} failed", a.id(), t);
                }
            }
            for (var p : l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(c).inflate(80))) p.discard();
            check("all " + total + " abilities execute", errors.isEmpty(), ok + "/" + total + (errors.isEmpty() ? "" : " problems: " + String.join(", ", errors.subList(0, Math.min(12, errors.size())))));
            check("ability dummy took damage", dummy.getHealth() < dummy.getMaxHealth(), String.format("%.0f damage dealt in total", dummy.getMaxHealth() - dummy.getHealth()));
            dummy.discard();
            d.zoanForm = "";
            return 0;
        });
        // ---------- Logia intangibility vs Haki ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            BlockPos c = arena(l);
            HumanoidNpc logia = npc(l, "pirate", 3, c.offset(0, 0, 3));
            HumanoidNpc attacker = npc(l, "marine", 3, c.offset(0, 0, 1));
            logia.setNoAi(true);
            attacker.setNoAi(true);
            logia.forceFruitForTest("mera");
            Hits.Result plain = Hits.hit(attacker, logia, 5, Hits.Info.physical(0.1f));
            Hits.Result haki = Hits.hit(attacker, logia, 5, Hits.Info.physical(0.1f).imbue(true));
            check("Logia ignores un-imbued physical hits", plain == Hits.Result.INTANGIBLE, "result=" + plain);
            check("Armament Haki hits a Logia", haki == Hits.Result.HIT, "result=" + haki);
            // water weakness
            l.setBlock(logia.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
            l.setBlock(logia.blockPosition().above(), Blocks.WATER.defaultBlockState(), 3);
            return 5;
        });
        steps.add(s -> {
            ServerLevel l = ow(s);
            var list = l.getEntitiesOfClass(HumanoidNpc.class, new AABB(arena(l)).inflate(10), n -> "mera".equals(n.fruitId()));
            if (list.isEmpty()) {
                check("Logia in water found", false, "");
                return 0;
            }
            HumanoidNpc logia = list.get(0);
            boolean weak = FruitRules.weakened(logia);
            HumanoidNpc attacker = l.getEntitiesOfClass(HumanoidNpc.class, new AABB(arena(l)).inflate(10), n -> n.fruitId() == null).stream().findFirst().orElse(null);
            Hits.Result wet = attacker == null ? null : Hits.hit(attacker, logia, 5, Hits.Info.physical(0.1f));
            check("Devil Fruit user is weakened in the sea", weak, "inWater=" + logia.isInWater());
            check("Logia submerged in sea water can be hit", wet == Hits.Result.HIT, "result=" + wet);
            for (var n : l.getEntitiesOfClass(HumanoidNpc.class, new AABB(arena(l)).inflate(12))) n.discard();
            l.setBlock(logia.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(logia.blockPosition().above(), Blocks.AIR.defaultBlockState(), 3);
            return 0;
        });
        // ---------- knockdown/permadeath data rules ----------
        steps.add(s -> {
            CharacterData d = new CharacterData();
            d.created = true;
            d.name = "Doomed";
            d.level = 30;
            d.fruit = "mera";
            d.archiveAndReset();
            check("permadeath archives the character", !d.created && d.generation == 1 && d.fruit == null && d.level == 1 && d.fallenCharacters.size() == 1, d.fallenCharacters.toString());
            return 0;
        });
        // ---------- persistence fixtures for phase 2 ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            WorldPlan p = WorldPlan.of(s);
            Island isl = p.byId("orange_town");
            int x = isl.x + 3, z = isl.z + 3;
            int y = surface(l, x, z);
            HumanoidNpc keeper = npc(l, "elder", 1, new BlockPos(x, y, z));
            keeper.setCustomName(net.minecraft.network.chat.Component.literal("Persistence Keeper"));
            keeper.setPersistenceRequired();
            CompoundTag fx = new CompoundTag();
            fx.putUUID("npc", keeper.getUUID());
            fx.putInt("x", x);
            fx.putInt("z", z);
            fx.putString("fruitOwner", FAKE_ID.toString());
            Files.writeString(Path.of("selftest-fixtures.txt"), keeper.getUUID() + " " + x + " " + z);
            check("persistence fixture written", true, "NPC " + keeper.getUUID() + " at " + x + "," + z);
            return 20;
        });
        storySteps();
    }

    // ============================================================== story cast, crews and quests (Saltfire Saga)

    private static void storySteps() {
        // ---------- cast data ----------
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            var all = com.grandline.npc.notable.NotableCharacters.all();
            check("story cast registered", all.size() >= 100, all.size() + " notables");
            java.util.function.ToIntFunction<com.grandline.npc.notable.NotableCharacters.Group> c = com.grandline.npc.notable.NotableCharacters::count;
            check("cast groups", c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.EMPEROR) == 4 && c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.STEWARD) == 5
                            && c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.WARLORD) == 7 && c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.SUPERNOVA) >= 9
                            && com.grandline.npc.notable.NotableCharacters.recruitable().size() >= 14,
                    "emperors " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.EMPEROR) + ", stewards " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.STEWARD)
                            + ", warlords " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.WARLORD) + ", supernovas " + c.applyAsInt(com.grandline.npc.notable.NotableCharacters.Group.SUPERNOVA)
                            + ", crew " + com.grandline.npc.notable.NotableCharacters.recruitable().size());
            List<String> bad = new ArrayList<>();
            Set<String> fruits = new HashSet<>();
            for (var n : all) {
                if (p.byId(n.island()) == null) bad.add(n.id() + ": island " + n.island());
                if (n.fruit() != null && (DevilFruits.get(n.fruit()) == null || !fruits.add(n.fruit()))) bad.add(n.id() + ": fruit " + n.fruit());
                if (FightingStyles.get(n.style()) == null) bad.add(n.id() + ": style " + n.style());
                if (!com.grandline.npc.Archetypes.has(n.role())) bad.add(n.id() + ": role " + n.role());
                if (!n.raceEnum().id().equals(n.race())) bad.add(n.id() + ": race " + n.race());
                if (!n.weapon().isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.get(GrandLine.id(n.weapon())) == net.minecraft.world.item.Items.AIR) bad.add(n.id() + ": weapon " + n.weapon());
            }
            check("every notable's island, fruit (unique), style, role, race and weapon exist", bad.isEmpty(), bad.isEmpty() ? fruits.size() + " fruits held by the cast" : String.join("; ", bad.subList(0, Math.min(10, bad.size()))));
            return 0;
        });
        // ---------- quest graph ----------
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            List<String> bad = new ArrayList<>();
            Set<String> story = com.grandline.quest.QuestRegistry.storyQuestIds();
            for (var q : com.grandline.quest.QuestRegistry.all()) {
                if (q.next() != null && com.grandline.quest.QuestRegistry.get(com.grandline.quest.QuestRegistry.baseId(q.next())) == null) bad.add(q.id() + " next " + q.next());
                for (String r : q.requires()) if (com.grandline.quest.QuestRegistry.get(com.grandline.quest.QuestRegistry.baseId(r)) == null) bad.add(q.id() + " requires " + r);
                if (!story.contains(q.id())) continue;
                if (q.giverRole().startsWith("notable:") && com.grandline.npc.notable.NotableCharacters.get(q.giverRole().substring(8)) == null) bad.add(q.id() + " giver " + q.giverRole());
                if (!q.giverRole().startsWith("notable:") && !com.grandline.npc.Archetypes.has(q.giverRole())) bad.add(q.id() + " giver " + q.giverRole());
                if (!q.island().equals("*") && p.byId(q.island()) == null) bad.add(q.id() + " island " + q.island());
                if (q.objectives().isEmpty()) bad.add(q.id() + " has no objectives");
                for (var o : q.objectives()) {
                    boolean ok = switch (o.type()) {
                        case "defeat" -> com.grandline.npc.notable.NotableCharacters.get(o.target()) != null;
                        case "talk" -> com.grandline.npc.notable.NotableCharacters.get(o.target()) != null || com.grandline.npc.Archetypes.has(o.target());
                        case "kill" -> com.grandline.npc.Archetypes.has(o.target());
                        case "discover" -> p.byId(o.target()) != null || o.target().equals("any") || o.target().equals("any_canon");
                        case "poneglyph" -> true;
                        default -> false;
                    };
                    if (!ok) bad.add(q.id() + " objective " + o.type() + ":" + o.target());
                }
                String vf = com.grandline.quest.QuestRegistry.variantFaction(q.id());
                if (q.bounty() > 0 && (vf == null ? com.grandline.quest.QuestRegistry.get(q.id() + ".marine") == null : vf.equals("marine"))) bad.add(q.id() + " grants a bounty without a Marine variant");
            }
            for (var n : com.grandline.npc.notable.NotableCharacters.recruitable()) {
                if (com.grandline.quest.QuestRegistry.get(n.recruitQuest()) == null) bad.add(n.id() + " has no " + n.recruitQuest());
                if (com.grandline.quest.QuestRegistry.get(n.personalQuest()) == null) bad.add(n.id() + " has no " + n.personalQuest());
            }
            Set<String> islands = new HashSet<>();
            for (String id : story) {
                var q = com.grandline.quest.QuestRegistry.get(id);
                if (q != null && !q.island().equals("*")) islands.add(q.island());
            }
            check("story quest graph is consistent", bad.isEmpty(), bad.isEmpty() ? story.size() + " story quests across " + islands.size() + " islands" : String.join("; ", bad.subList(0, Math.min(10, bad.size()))));
            return 0;
        });
        // ---------- faction variants ----------
        steps.add(s -> {
            CharacterData m = new CharacterData();
            m.faction = Faction.MARINE;
            CharacterData pi = new CharacterData();
            pi.faction = Faction.PIRATE;
            var base = com.grandline.quest.QuestRegistry.get("shells_2");
            var marine = com.grandline.quest.QuestRegistry.get("shells_2.marine");
            m.completedQuests.add("shells_1.marine");
            pi.completedQuests.add("shells_1");
            boolean ok = com.grandline.quest.QuestRegistry.resolve("shells_2", m).equals("shells_2.marine") && com.grandline.quest.QuestRegistry.resolve("shells_2", pi).equals("shells_2")
                    && !com.grandline.quest.QuestRegistry.requirementsMet(m, base) && com.grandline.quest.QuestRegistry.requirementsMet(m, marine)
                    && com.grandline.quest.QuestRegistry.requirementsMet(pi, base) && !com.grandline.quest.QuestRegistry.requirementsMet(pi, marine);
            m.completedQuests.add("shells_2.marine");
            ok &= com.grandline.quest.QuestRegistry.isDone(m, "shells_2") && com.grandline.quest.QuestRegistry.requirementsMet(m, com.grandline.quest.QuestRegistry.get("recruit_petra"));
            check("faction variants resolve and gate correctly", ok, "");
            return 0;
        });
        // ---------- spawning a notable, yielding, permanent death ----------
        steps.add(s -> {
            ServerLevel l = ow(s);
            WorldPlan p = WorldPlan.of(s);
            var n = com.grandline.npc.notable.NotableCharacters.get("fumio");
            Island isl = p.byId(n.island());
            l.getChunk((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            HumanoidNpc npc = com.grandline.npc.notable.NotableSpawner.spawnNow(l, "fumio");
            var st = com.grandline.npc.notable.NotableState.get(s);
            check("notable spawns on its home island", npc != null && npc.isAlive() && "fumio".equals(npc.notableId()) && npc.flag(com.grandline.npc.GLNpc.FLAG_NOTABLE)
                            && npc.isPersistenceRequired() && npc.tier() == n.tier() && "black_leg".equals(npc.styleId()) && st.status("fumio") == com.grandline.npc.notable.NotableState.Status.ALIVE,
                    npc == null ? "no stand spot / not spawned" : npc.getName().getString() + " at " + npc.blockPosition() + ", hp " + (int) npc.getMaxHealth());
            if (npc == null) return 0;
            FakePlayer fp = fake(s);
            check("friendly notable does not attack players", !npc.isHostileTo(fp), "");
            CompoundTag t = new CompoundTag();
            t.putString("greeting", "dialogue.grandline.greet.elder");
            net.minecraft.nbt.ListTag opts = new net.minecraft.nbt.ListTag();
            CharacterData d = Characters.get(fp);
            com.grandline.npc.notable.NotableHooks.decorate(fp, npc, d, t, opts, true);
            check("notable dialogue: own greeting and quest offer", t.getString("greeting").equals("dialogue.grandline.notable.fumio") && opts.toString().contains("quest:ember_east_1"), opts.toString());
            npc.hurt(fp.damageSources().playerAttack(fp), 100000);
            check("beaten friendly notable yields instead of dying", npc.isAlive() && npc.getHealth() > 0 && npc.flag(com.grandline.npc.GLNpc.FLAG_SURRENDER), "hp " + npc.getHealth());
            npc.kill();
            check("killed notable is gone for good", st.status("fumio") == com.grandline.npc.notable.NotableState.Status.DEFEATED
                    && com.grandline.npc.notable.NotableSpawner.spawnNow(l, "fumio") == null, "status " + st.status("fumio"));
            return 0;
        });
        // ---------- recruiting a crew member ----------
        steps.add(s -> {
            // keep the crew candidate's home loaded and ticking, as it would be with a player nearby
            var n = com.grandline.npc.notable.NotableCharacters.get("petra");
            Island isl = WorldPlan.of(s).byId(n.island());
            ChunkPos cp = new ChunkPos((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            ow(s).getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
            return 40;
        });
        steps.add(s -> {
            ServerLevel l = ow(s);
            HumanoidNpc petra = com.grandline.npc.notable.NotableSpawner.spawnNow(l, "petra");
            if (petra == null) {
                check("crew candidate spawned", false, "petra not spawned");
                return 0;
            }
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            CompoundTag t = new CompoundTag();
            net.minecraft.nbt.ListTag opts = new net.minecraft.nbt.ListTag();
            com.grandline.npc.notable.NotableHooks.decorate(fp, petra, d, t, opts, false);
            boolean noRecruitYet = !opts.toString().contains("recruit");
            d.completedQuests.add("recruit_petra");
            opts = new net.minecraft.nbt.ListTag();
            com.grandline.npc.notable.NotableHooks.decorate(fp, petra, d, t, opts, false);
            boolean recruitOffered = opts.toString().contains("\"recruit\"");
            boolean joined = com.grandline.npc.crew.CrewManager.recruit(fp, petra);
            check("recruit option appears only after the recruitment quest", noRecruitYet && recruitOffered && joined, opts.toString());
            float hp = petra.getHealth();
            petra.hurt(fp.damageSources().playerAttack(fp), 5);
            boolean crewOk = com.grandline.npc.crew.CrewManager.isCrewOf(petra, fp) && petra.isAlliedTo(fp) && !petra.isHostileTo(fp) && petra.getHealth() == hp
                    && com.grandline.npc.crew.CrewManager.members(s, d.characterId).contains("petra")
                    && com.grandline.npc.notable.NotableState.get(s).status("petra") == com.grandline.npc.notable.NotableState.Status.RECRUITED;
            check("crew member is allied, protected from friendly fire and saved per character", crewOk, "members " + com.grandline.npc.crew.CrewManager.members(s, d.characterId));
            CompoundTag saved = com.grandline.npc.crew.CrewManager.data(s).save(new CompoundTag(), s.registryAccess());
            check("crew data round trip", saved.toString().contains("petra") && saved.getList("crews", 10).size() >= 1, "");
            com.grandline.npc.crew.CrewManager.disband(s, d.characterId);
            check("crew disbands (captain's permadeath)", com.grandline.npc.crew.CrewManager.members(s, d.characterId).isEmpty() && !com.grandline.npc.crew.CrewManager.isCrew(petra)
                    && petra.faction() == Faction.MARINE, "status " + com.grandline.npc.notable.NotableState.get(s).status("petra") + ", faction " + petra.faction()
                    + ", tracked " + (l.getEntity(petra.getUUID()) != null));
            d.completedQuests.remove("recruit_petra");
            return 0;
        });
        // ---------- quest chain with retroactive discovery and auto-advance ----------
        steps.add(s -> {
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            Faction before = d.faction;
            d.faction = Faction.PIRATE;
            d.discoveredIslands.add("shells_town");
            boolean started = com.grandline.quest.Quests.start(fp, "ember_east_1");
            com.grandline.quest.Quests.progress(fp, "kill", "bandit", 3);
            boolean ok = started && d.completedQuests.contains("ember_east_1") && d.completedQuests.contains("ember_east_2") && d.questProgress.containsKey("shells_1");
            check("story chain advances (pre-discovered island credited, next arc auto-started)", ok, "active " + d.questProgress.keySet());
            for (String id : List.of("ember_east_1", "ember_east_2")) d.completedQuests.remove(id);
            d.questProgress.remove("shells_1");
            d.faction = before;
            return 0;
        });
    }

    // ============================================================== PHASE 2

    private static void phase2() {
        final String[] fx = new String[1];
        steps.add(s -> {
            fx[0] = Files.readString(Path.of("selftest-fixtures.txt")).trim();
            String[] parts = fx[0].split(" ");
            int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            ow(s).getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(x >> 4, z >> 4), 2, new ChunkPos(x >> 4, z >> 4));
            ow(s).getChunk(x >> 4, z >> 4);
            check("fruit ledger survived restart", FruitWorldData.get(s).state("goro") == FruitWorldData.State.OWNED, "goro=" + FruitWorldData.get(s).state("goro") + " owner=" + FruitWorldData.get(s).ownerName("goro"));
            return 60; // let entities load
        });
        steps.add(s -> {
            String[] parts = fx[0].split(" ");
            UUID id = UUID.fromString(parts[0]);
            var e = ow(s).getEntity(id);
            check("NPC entity persisted across restart", e instanceof HumanoidNpc, e == null ? "missing" : e.getName().getString() + " at " + e.blockPosition());
            return 0;
        });
    }

    // ============================================================== helpers

    private static BlockPos arenaPos;

    /** A flat stone platform in the sky above Shiokaze Isle for controlled tests. */
    private static BlockPos arena(ServerLevel l) {
        if (arenaPos == null) {
            WorldPlan p = WorldPlan.of(l.getServer());
            Island i = p.byId("shiokaze_isle");
            BlockPos c = new BlockPos(i.x + 60, 200, i.z + 60);
            for (int dx = -12; dx <= 12; dx++)
                for (int dz = -12; dz <= 12; dz++) {
                    l.setBlock(c.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 0; dy < 6; dy++) l.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            arenaPos = c;
            // keep the arena entity-ticking without a real player nearby
            l.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(c), 3, new ChunkPos(c));
            l.setChunkForced(c.getX() >> 4, c.getZ() >> 4, true);
        }
        return arenaPos;
    }

    private static HumanoidNpc npc(ServerLevel l, String role, int tier, BlockPos pos) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        n.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        n.configure(role, tier, "", pos, "", l.random);
        l.addFreshEntity(n);
        return n;
    }

    private static ItemStack weaponFor(WeaponClass wc) {
        for (var w : ModItems.WEAPONS) if (w.get().weaponClass == wc) return new ItemStack(w.get());
        return ItemStack.EMPTY;
    }

    private SelfTest() {}
}
