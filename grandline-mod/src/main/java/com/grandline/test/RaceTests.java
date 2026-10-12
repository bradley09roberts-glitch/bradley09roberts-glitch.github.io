package com.grandline.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.combat.Loadouts;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Race;
import com.grandline.race.RacePowers;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.grandline.test.SelfTest.check;

/** Self-test steps for racial powers: registry, Sulong's full moon, Long-Arm reach, swim speed, foresight, loadouts, NPC use. */
final class RaceTests {
    private static final UUID FAKE_ID = UUID.fromString("6c1a4e2b-0b9e-4f43-9d61-5e7e4e1a2b3c"); // SelfTest's fake player
    private static BlockPos pad;

    private static FakePlayer fake(MinecraftServer s) {
        return FakePlayerFactory.get(s.overworld(), new GameProfile(FAKE_ID, "SelfTestPirate"));
    }

    /** A private stone pad in the open sky (away from the main arena so it never blocks that arena's sky). */
    private static BlockPos pad(ServerLevel l) {
        if (pad == null) {
            Island i = WorldPlan.of(l.getServer()).byId("shiokaze_isle");
            BlockPos c = new BlockPos(i.x + 110, 200, i.z + 60);
            for (int dx = -10; dx <= 10; dx++)
                for (int dz = -16; dz <= 16; dz++) {
                    l.setBlock(c.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 0; dy < 6; dy++) l.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            l.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(c), 3, new ChunkPos(c));
            l.setChunkForced(c.getX() >> 4, c.getZ() >> 4, true);
            pad = c;
        }
        return pad;
    }

    private static HumanoidNpc npc(ServerLevel l, String role, int tier, BlockPos pos) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        n.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        n.configure(role, tier, "", pos, "", l.random);
        n.setNoAi(true);
        l.addFreshEntity(n);
        return n;
    }

    private static void reset(FakePlayer fp, BlockPos at) {
        CombatState s = CombatState.of(fp);
        s.cooldowns.clear();
        s.buffs.clear();
        s.iframesUntil = 0;
        fp.removeAllEffects();
        fp.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
    }

    private static Ability ab(String id) {
        return Objects.requireNonNull(Abilities.get(id), id);
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- registry, animations, names ----------
        steps.add(s -> {
            List<String> bad = new ArrayList<>();
            int total = 0;
            for (Race r : Race.values()) {
                int n = Abilities.of(Ability.Source.RACE, r.id()).size();
                total += n;
                if (n < 2 || n > 4) bad.add(r.id() + "=" + n);
            }
            check("every race has 2-4 racial abilities", bad.isEmpty(), total + " race abilities for " + Race.values().length + " races" + (bad.isEmpty() ? "" : "; bad: " + bad));
            JsonObject anims = json("/assets/grandline/animations/humanoid.animation.json");
            JsonObject lang = json("/assets/grandline/lang/en_us.json");
            List<String> noAnim = new ArrayList<>(), noName = new ArrayList<>();
            for (Ability a : Abilities.all()) {
                if (a.source() != Ability.Source.RACE) continue;
                if (anims == null || !anims.getAsJsonObject("animations").has("animation.grandline." + a.anim())) noAnim.add(a.id() + ":" + a.anim());
                if (lang == null || !lang.has(a.translationKey()) || !lang.has("mastery.grandline." + a.masteryKey().replace('/', '.'))) noName.add(a.id());
            }
            check("race ability animations exist", noAnim.isEmpty(), noAnim.isEmpty() ? "" : String.join(", ", noAnim));
            check("race abilities and masteries are translated", noName.isEmpty(), noName.isEmpty() ? "" : String.join(", ", noName.subList(0, Math.min(10, noName.size()))));
            return 0;
        });
        // ---------- Mink Sulong: only at night under a visible full moon ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            Race before = d.race;
            BlockPos at = pad(l);
            reset(fp, at);
            d.race = Race.MINK;
            Ability sulong = ab("race/mink/sulong");
            long time = l.getDayTime();
            long fullMoonDay = (time / 192000L + 1) * 192000L; // a day whose moon phase is 0 (full)
            l.setDayTime(fullMoonDay + 6000);
            boolean noon = CombatManager.useAbility(fp, sulong, null);
            CombatState.of(fp).cooldowns.clear();
            l.setDayTime(fullMoonDay + 24000 + 18000); // next night: waning gibbous
            boolean gibbous = CombatManager.useAbility(fp, sulong, null);
            CombatState.of(fp).cooldowns.clear();
            l.setDayTime(fullMoonDay + 18000);
            boolean fullMoon = CombatManager.useAbility(fp, sulong, null);
            boolean buffed = CombatState.of(fp).hasBuff("sulong") && fp.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
            l.setDayTime(time);
            check("Mink Sulong only at night under a full moon", !noon && !gibbous && fullMoon && buffed,
                    "noon=" + noon + " gibbousNight=" + gibbous + " fullMoonNight=" + fullMoon + " buffed=" + buffed);
            reset(fp, at);
            d.race = before;
            return 0;
        });
        // ---------- Long-Arm grab reach, swim speed, foresight ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            Race before = d.race;
            BlockPos at = pad(l);
            reset(fp, at);
            HumanoidNpc dummy = npc(l, "bandit", 1, at.offset(0, 0, 8));
            dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            dummy.setHealth(1000);
            Ability grab = ab("race/long_arm/long_grab");
            d.race = Race.HUMAN;
            boolean humanCan = CombatManager.canUse(fp, grab);
            d.race = Race.LONG_ARM;
            fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
            double far = dummy.distanceTo(fp);
            boolean pulled = CombatManager.useAbility(fp, grab, dummy);
            double after = dummy.distanceTo(fp);
            CombatState.of(fp).cooldowns.clear();
            double reach = 9 * Math.max(1, fp.getScale()); // the grab reaches 9 blocks, scaled up for big bodies
            dummy.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5 + reach + 3);
            boolean tooFar = CombatManager.useAbility(fp, grab, dummy);
            check("Long-Arm grab pulls a foe from beyond melee reach", !humanCan && pulled && after < 3.5 && !tooFar,
                    String.format("humanCanUse=%s from %.1f to %.1f blocks, at %.0f blocks used=%s", humanCan, far, after, reach + 3, tooFar));

            // swim speed (NeoForge swim_speed attribute; the passive runs from the player tick)
            d.race = Race.HUMAN;
            RacePowers.tick(fp, d);
            double human = fp.getAttributeValue(NeoForgeMod.SWIM_SPEED);
            d.race = Race.FISHMAN;
            RacePowers.tick(fp, d);
            double fish = fp.getAttributeValue(NeoForgeMod.SWIM_SPEED);
            d.race = Race.MERFOLK;
            RacePowers.tick(fp, d);
            double mer = fp.getAttributeValue(NeoForgeMod.SWIM_SPEED);
            check("Fish-Man swims faster under water, Merfolk fastest", Math.abs(human - 1) < 1e-6 && fish >= 1.8 && mer > fish,
                    String.format("swim speed human %.2f, fish-man %.2f, merfolk %.2f", human, fish, mer));

            // NPCs of the sea races swim like the players of those races
            HumanoidNpc fishNpc = npc(l, "resident", 1, at.offset(3, 0, 0));
            fishNpc.setRace(Race.FISHMAN);
            fishNpc.tickCount = 20;
            com.grandline.race.RacePowers.tickNpc(fishNpc);
            var npcSwim = fishNpc.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);
            double npcSwimV = npcSwim == null ? 0 : npcSwim.getValue();
            check("Fish-Man NPCs get the racial swim speed", npcSwimV >= 1.8, String.format("swim speed %.2f", npcSwimV));
            fishNpc.discard();

            // Three-Eye foresight: every hit in the window is dodged; without it the same hit lands
            HumanoidNpc att = npc(l, "marine", 3, at.offset(0, 0, 2));
            att.setTarget(fp);
            fp.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            String fruit = d.fruit;
            d.fruit = null; // no Logia body in the way
            d.race = Race.HUMAN;
            // earlier steps can leave Observation on, a future-sight buff or i-frames: clear them for the baseline
            CombatState fcs = CombatState.of(fp);
            boolean obs = fcs.observation;
            fcs.observation = false;
            fcs.iframesUntil = 0;
            fcs.buffs.remove("future_sight");
            Hits.Result plain = Hits.hit(att, fp, 4, Hits.Info.physical(0.1f));
            fcs.observation = obs;
            d.race = Race.THREE_EYE;
            CombatState.of(fp).iframesUntil = 0;
            boolean used = CombatManager.useAbility(fp, ab("race/three_eye/foresight"), att);
            Hits.Result first = Hits.hit(att, fp, 4, Hits.Info.physical(0.1f));
            Hits.Result second = Hits.hit(att, fp, 4, Hits.Info.physical(0.1f).asUnblockable());
            check("Three-Eye Foresight dodges every hit in its window", plain == Hits.Result.HIT && used && first == Hits.Result.DODGED && second == Hits.Result.DODGED,
                    "without=" + plain + " used=" + used + " with=" + first + "," + second);
            d.fruit = fruit;
            dummy.discard();
            att.discard();
            reset(fp, at);
            d.race = before;
            RacePowers.applySwim(fp, before);
            return 0;
        });
        // ---------- loadout set 4 holds race abilities, gated by race mastery ----------
        steps.add(s -> {
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            Race before = d.race;
            Integer xpBefore = d.masteryXp.get("race/fishman");
            fp.setGameMode(GameType.SURVIVAL);
            d.race = Race.FISHMAN;
            d.masteryXp.remove("race/fishman");
            Loadouts.resetDefaults(fp, d);
            List<String> fresh = slots(d);
            d.masteryXp.put("race/fishman", 10500); // mastery 25
            Loadouts.refresh(fp, d);
            List<String> trained = slots(d);
            check("race loadout set holds unlocked racial abilities", fresh.equals(List.of("race/fishman/riptide_dash"))
                            && trained.size() == 3 && trained.contains("race/fishman/buraikan") && !trained.contains("race/fishman/inner_ripple"),
                    "mastery 0: " + fresh + "; mastery " + d.mastery("race/fishman") + ": " + trained);
            if (xpBefore == null) d.masteryXp.remove("race/fishman");
            else d.masteryXp.put("race/fishman", xpBefore);
            d.race = before;
            fp.setGameMode(GameType.CREATIVE);
            Loadouts.refresh(fp, d);
            return 0;
        });
        // ---------- NPCs fight with their race's moves; racial body traits ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos at = pad(l);
            HumanoidNpc brute = npc(l, "fishman_brute", 4, at.offset(-4, 0, -6));
            HumanoidNpc vic = npc(l, "pirate", 3, at.offset(-4, 0, -3));
            brute.setTarget(vic);
            List<String> race = brute.techniques().stream().map(Ability::id).filter(id -> id.startsWith("race/")).toList();
            int mastery = Math.min(100, brute.tier() * 10);
            long expected = Abilities.of(Ability.Source.RACE, "fishman").stream().filter(a -> a.unlockMastery() <= mastery).count();
            boolean volley = CombatManager.useAbility(brute, ab("race/fishman/droplet_volley"), vic);
            boolean humanRefused = !CombatManager.canUse(vic, ab("race/fishman/riptide_dash"));
            check("Fish-Man NPCs use Fish-Man racial moves", race.size() == expected && expected >= 2 && race.stream().allMatch(id -> id.startsWith("race/fishman/")) && volley && humanRefused,
                    "tier " + brute.tier() + " techniques " + race + ", volley=" + volley + ", human NPC refused=" + humanRefused);

            // Stonehide Living Fortress: 70% less damage and melee attackers are hurt
            HumanoidNpc att = npc(l, "marine", 3, at.offset(4, 0, -6));
            HumanoidNpc rock = npc(l, "pirate", 3, at.offset(4, 0, -5));
            att.setTarget(rock);
            rock.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            rock.setHealth(500);
            float h0 = rock.getHealth();
            Hits.hit(att, rock, 20, Hits.Info.physical(0f));
            float plain = h0 - rock.getHealth();
            rock.setRace(Race.STONEHIDE);
            boolean fort = CombatManager.useAbility(rock, ab("race/stonehide/living_fortress"), att);
            float a0 = att.getHealth();
            h0 = rock.getHealth();
            CombatState.of(rock).iframesUntil = 0;
            Hits.hit(att, rock, 20, Hits.Info.physical(0f));
            float fortified = h0 - rock.getHealth();
            check("Stonehide Living Fortress cuts damage and punishes melee", fort && fortified < plain * 0.5f && att.getHealth() < a0,
                    String.format("took %.1f -> %.1f, attacker hp %.1f -> %.1f", plain, fortified, a0, att.getHealth()));

            // Human Last Stand: a would-be killing blow leaves 1 HP
            HumanoidNpc last = npc(l, "pirate", 3, at.offset(4, 0, -3));
            att.setTarget(last);
            last.setHealth(6);
            boolean stand = CombatManager.useAbility(last, ab("race/human/last_stand"), att);
            Hits.hit(att, last, 200, Hits.Info.physical(0f));
            check("Human Last Stand survives a killing blow", stand && last.isAlive() && last.getHealth() >= 1, "used=" + stand + " hp=" + last.getHealth());

            // Merfolk Call of the Sea: a Drowned hunting the merfolk turns on the merfolk's foe
            FakePlayer fp = fake(s);
            CharacterData d = Characters.get(fp);
            Race before = d.race;
            reset(fp, at);
            d.race = Race.MERFOLK;
            Drowned drowned = EntityType.DROWNED.create(l);
            boolean turned = false;
            if (drowned != null) {
                drowned.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
                drowned.setNoAi(true);
                l.addFreshEntity(drowned);
                drowned.setTarget(fp);
                boolean called = CombatManager.useAbility(fp, ab("race/merfolk/call_of_the_sea"), vic);
                turned = called && drowned.getTarget() == vic;
                drowned.discard();
            }
            check("Merfolk Call of the Sea turns sea creatures on the foe", turned, "drowned target=" + (drowned == null ? "none" : String.valueOf(drowned.getTarget())));
            d.race = before;
            reset(fp, at);
            for (LivingEntity e : l.getEntitiesOfClass(HumanoidNpc.class, new AABB(at).inflate(20))) e.discard();
            return 0;
        });
    }

    private static List<String> slots(CharacterData d) {
        List<String> out = new ArrayList<>();
        for (String id : d.loadout[3]) if (id != null) out.add(id);
        return out;
    }

    private static JsonObject json(String path) {
        try (InputStream in = RaceTests.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private RaceTests() {}
}
