package com.grandline.test;

import com.grandline.boss.*;
import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.fruit.FruitWorldData;
import com.grandline.fruit.TerrainChanges;
import com.grandline.haki.Haki;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.player.Knockdown;
import com.grandline.player.Race;
import com.grandline.quest.Quests;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.grandline.test.SelfTest.check;

/**
 * Self-test steps for the boss fights (added to phase 1 by {@link SelfTest}; quick run: GL_SELFTEST=bosses). A boss
 * shows its bar to the players in the fight; health thresholds start a transition and swap the move set (and change
 * the arena); a telegraphed attack hits whoever stays in its marker and misses whoever left it; Logia bosses ignore
 * blows without Haki; damage taken scales with the player count; a boss neither finishes nor arrests a downed player
 * while someone else still fights; bosses shrug off knock-outs and long crowd control and read blows until staggered;
 * the enrage timer; Conqueror's clashes; defeat (death, yield, arrest) drops the boss's one relic, pays scrolls and
 * bounty and credits the story; and every boss's signature attacks, arena and phase powers run without errors.
 */
public final class BossTests {
    private static final UUID A_ID = UUID.fromString("b0550000-0000-4000-8000-0000000000a1");
    private static final UUID B_ID = UUID.fromString("b0550000-0000-4000-8000-0000000000b2");
    private static final UUID C_ID = UUID.fromString("b0550000-0000-4000-8000-0000000000c3");
    private static final Set<String> EXPECTED = Set.of("hrolf", "kurogane", "barnum", "aurelia", "constance", "zenith", "nadir", "meridian",
            "sterling", "hisui", "jinpachi", "leonhart", "saltmarsh", "lucrezia", "pipistrello", "nox", "vespera", "sigrun", "hagane",
            "aldric", "morwenna", "cassius", "ottavia", "leopold");
    private static BlockPos center;
    /** Story state of the notables the tests borrow, restored at the end. */
    private static final Map<String, NotableState.Entry> SAVED = new LinkedHashMap<>();
    private static final List<LivingEntity> SPAWNED = new ArrayList<>();

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- the roster ----------
        steps.add(s -> {
            List<String> problems = new ArrayList<>();
            Set<String> ids = new TreeSet<>();
            String lang = resource("/assets/grandline/lang/en_us.json");
            for (BossDef d : Bosses.all()) {
                ids.add(d.id);
                if (NotableCharacters.get(d.id) == null) problems.add(d.id + ": not in the cast");
                int n = d.moves().size();
                if (n < 2 || n > 3) problems.add(d.id + ": " + n + " signature attacks");
                for (int p = 1; p <= d.phaseCount(); p++) if (d.movesFor(p).size() < 2) problems.add(d.id + ": phase " + p + " has " + d.movesFor(p).size() + " moves");
                if (d.movesFor(1).equals(d.movesFor(2))) problems.add(d.id + ": phase 2 does not change the move set");
                if (lang == null) continue;
                for (BossMove m : d.moves()) if (!lang.contains("\"" + m.nameKey() + "\"")) problems.add(m.nameKey() + " untranslated");
                for (String k : List.of(d.relicNameKey(), d.relicNameKey() + ".desc", d.phaseLineKey(2), d.phaseLineKey(3)))
                    if (!lang.contains("\"" + k + "\"")) problems.add(k + " untranslated");
                Item base = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("grandline", d.relicItem()));
                if (base == Items.AIR) problems.add(d.id + ": relic base " + d.relicItem() + " missing");
                for (int p = 2; p <= 3; p++) for (String ab : d.phasePowers(p)) if (com.grandline.combat.Abilities.get(ab) == null) problems.add(d.id + ": power " + ab + " missing");
                for (BossMove m : d.moves()) if (!m.reuse().isEmpty() && com.grandline.combat.Abilities.get(m.reuse()) == null) problems.add(m.id() + ": reuse missing");
            }
            check("boss roster: Emperors, Admirals, Warlords, Stewards and final-arc villains", ids.containsAll(EXPECTED) && ids.size() == EXPECTED.size(),
                    ids.size() + " bosses " + ids);
            check("every boss has 2-3 signature attacks per phase, a new set in phase 2, names, relic and lines", problems.isEmpty() && lang != null,
                    problems.isEmpty() ? Bosses.all().size() * 3 + " signature attacks" : String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
            return 0;
        });

        // ---------- the boss bar ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE), far = player(s, C_ID, "BossTesterC", Faction.PIRATE);
            a.moveTo(c.getX() + 6.5, c.getY(), c.getZ() + 0.5);
            far.moveTo(c.getX() + 300.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc k = boss(l, "kurogane", c);
            BossFight f = BossManager.start(k);
            check("a boss fight starts for a boss", f != null && BossManager.fightOf(k) == f, "");
            if (f == null) return 0;
            f.engage(a);
            f.engage(far);
            f.refreshNow();
            boolean shown = f.bar().getPlayers().contains(a), farShown = f.bar().getPlayers().contains(far);
            check("the boss bar shows to the players in the fight nearby", shown && !farShown && f.bar().isVisible(),
                    "near=" + shown + " far=" + farShown + " name=" + f.bar().getName().getString() + " progress=" + f.bar().getProgress());
            check("the boss bar names the boss and title", barKey(f).equals("grandline.boss.bar") && barArgs(f).contains("notable.grandline.kurogane")
                    && barArgs(f).contains("notable.grandline.kurogane.title"), barKey(f) + " " + barArgs(f));
            k.setHealth(k.getMaxHealth() * 0.5f);
            f.tickForTest();
            check("the boss bar follows the boss's health", Math.abs(f.bar().getProgress() - 0.5f) < 0.02f, "progress " + f.bar().getProgress());
            far.moveTo(c.getX() + 0.5, c.getY() + 40, c.getZ() + 0.5);
            k.discard();
            return 1;
        });
        steps.add(s -> {
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE);
            boolean gone = BossManager.fights().stream().noneMatch(f -> f.bar().getPlayers().contains(a));
            check("the boss bar goes away with the boss", gone, "");
            return 0;
        });

        // ---------- phases: thresholds, invulnerable transition, move-set swap, arena change ----------
        final HumanoidNpc[] nad = new HumanoidNpc[1];
        final Set<String>[] sets = new Set[3];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE);
            a.moveTo(c.getX() + 6.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc n = boss(l, "nadir", c);
            nad[0] = n;
            BossFight f = BossManager.start(n);
            f.engage(a);
            float max = n.getMaxHealth();
            sets[0] = ids(f.def.movesFor(1));
            check("a fresh boss starts in phase 1", f.phase() == 1 && !f.transitioning(), "phase " + f.phase());
            // one huge blow cannot carry a boss through a phase: it stops on the threshold and the roar begins
            n.invulnerableTime = 0;
            n.hurt(l.damageSources().playerAttack(a), 5000);
            float h1 = n.getHealth();
            check("damage stops on the 70% threshold", Math.abs(h1 - max * 0.70f) < 1.5f && n.isAlive(), String.format("health %.1f / %.1f", h1, max));
            check("crossing the threshold starts the phase transition", f.transitioning() && f.phase() == 1, "transitioning=" + f.transitioning());
            n.invulnerableTime = 0;
            n.hurt(l.damageSources().playerAttack(a), 50);
            Hits.Result r = Hits.hit(a, n, 30, Hits.Info.physical(0.1f).imbue(true).withPoise(0));
            check("the boss is untouchable while it roars", Math.abs(n.getHealth() - h1) < 0.01f, "health " + n.getHealth() + " after vanilla + " + r);
            return 60;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            HumanoidNpc n = nad[0];
            BossFight f = BossManager.fightOf(n);
            if (f == null) {
                check("phase 2 reached", false, "fight ended");
                return 0;
            }
            sets[1] = ids(f.def.movesFor(f.phase()));
            check("after the roar the boss is in phase 2", f.phase() == 2 && !f.transitioning(), "phase " + f.phase());
            check("phase 2 swaps the move set", f.phase() == 2 && !sets[0].equals(sets[1]), sets[0] + " -> " + sets[1]);
            check("phase 2 changes the arena (Nadir's darkness spreads)", !f.hazards().isEmpty() && "dark".equals(f.hazards().get(0).style()), f.hazards().size() + " hazards");
            int before = f.hazards().size();
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE);
            n.invulnerableTime = 0;
            n.hurt(l.damageSources().playerAttack(a), 5000);
            check("damage stops on the 35% threshold", Math.abs(n.getHealth() - n.getMaxHealth() * 0.35f) < 1.5f && f.transitioning(), String.format("health %.1f", n.getHealth()));
            nad[0].getPersistentData().putInt("hz", before);
            return 60;
        });
        steps.add(s -> {
            HumanoidNpc n = nad[0];
            BossFight f = BossManager.fightOf(n);
            if (f == null) {
                check("phase 3 reached", false, "fight ended");
                return 0;
            }
            sets[2] = ids(f.def.movesFor(f.phase()));
            check("after the second roar the boss is in phase 3 with its full move set", f.phase() == 3 && sets[2].size() == 3 && !sets[2].equals(sets[1]), sets[2].toString());
            check("phase 3 changes the arena again", f.hazards().size() > n.getPersistentData().getInt("hz"), f.hazards().size() + " hazards");
            n.discard();
            return 0;
        });

        // ---------- a telegraphed attack: stay in the marker and get hit, leave it and be missed ----------
        final Telegraph[] tele = new Telegraph[1];
        final HumanoidNpc[] dum = new HumanoidNpc[3];
        final float[] hp = new float[2];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            HumanoidNpc v = boss(l, "vespera", c.offset(-8, 0, 0));
            dum[2] = v;
            HumanoidNpc stay = dummy(l, c.offset(0, 0, 0)), leave = dummy(l, c.offset(2, 0, 0));
            dum[0] = stay;
            dum[1] = leave;
            BossFight f = BossManager.start(v);
            f.engage(stay);
            f.engage(leave);
            BossMove gosh = f.def.moves().stream().filter(m -> m.id().equals("vespera/goshikito")).findFirst().orElseThrow();
            Telegraph t = f.startMove(gosh, stay, new Vec3(c.getX() + 1.5, c.getY(), c.getZ() + 0.5), true);
            tele[0] = t;
            boolean bothInside = t.contains(stay) && t.contains(leave);
            double speed = v.getAttributeValue(Attributes.MOVEMENT_SPEED);
            check("the boss stands rooted while it winds up", speed < 1e-3, "movement speed " + speed);
            hp[0] = stay.getHealth();
            hp[1] = leave.getHealth();
            leave.teleportTo(c.getX() + 12.5, c.getY(), c.getZ() + 0.5); // steps out before it lands
            check("a telegraphed attack marks its area before it lands", bothInside && !t.done() && t.hitAt - t.start >= 10 && f.busy(),
                    "both inside=" + bothInside + " windup=" + (t.hitAt - t.start) + " busy=" + f.busy());
            return gosh.windup() + 8;
        });
        steps.add(s -> {
            Telegraph t = tele[0];
            HumanoidNpc stay = dum[0], leave = dum[1];
            boolean stayHit = stay.getHealth() < hp[0] && t.victims().contains(stay);
            boolean leftMissed = leave.getHealth() >= hp[1] && !t.victims().contains(leave) && !t.contains(leave);
            check("the telegraphed attack hits the one who stayed in the marker", t.done() && stayHit, String.format("hp %.1f -> %.1f, results %s", hp[0], stay.getHealth(), t.results().values()));
            check("...and misses the one who moved out before the hit", leftMissed, String.format("hp %.1f -> %.1f", hp[1], leave.getHealth()));
            BossFight f = BossManager.fightOf(dum[2]);
            check("the boss is free again after its attack", f != null && !f.busy(), "");
            for (LivingEntity e : dum) e.discard();
            return 0;
        });

        // ---------- left to itself, a boss fights with its signature attacks ----------
        final HumanoidNpc[] live = new HumanoidNpc[2];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            HumanoidNpc leo = boss(l, "leonhart", c.offset(-4, 0, 0));
            leo.setNoAi(false);
            HumanoidNpc foe = dummy(l, c.offset(3, 0, 0));
            live[0] = leo;
            live[1] = foe;
            leo.setTarget(foe);
            BossFight f = BossManager.start(leo);
            f.engage(foe);
            return 240;
        });
        steps.add(s -> {
            BossFight f = BossManager.fightOf(live[0]);
            int started = f == null ? 0 : f.movesStarted();
            check("left to its AI, a boss uses its telegraphed signature attacks", started >= 1 && live[1].getHealth() < live[1].getMaxHealth(),
                    started + " attacks started, foe at " + (int) live[1].getHealth() + "/" + (int) live[1].getMaxHealth());
            for (LivingEntity e : live) e.discard();
            return 0;
        });

        // ---------- shapes: every marker shape contains what it should ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            HumanoidNpc h = boss(l, "hrolf", c.offset(-6, 0, 0));
            HumanoidNpc near = dummy(l, c.offset(-6, 0, 4)), mid = dummy(l, c.offset(-6, 0, 12)), behind = dummy(l, c.offset(-6, 0, -10));
            BossFight f = BossManager.start(h);
            for (LivingEntity e : List.of(near, mid, behind)) f.engage(e);
            List<String> bad = new ArrayList<>();
            for (BossMove m : f.def.moves()) {
                Telegraph t = f.startMove(m, mid, null, false);
                boolean nearIn = t.contains(near), midIn = t.contains(mid), behindIn = t.contains(behind);
                boolean ok = switch (m.shape()) {
                    case SELF -> nearIn && !midIn && !behindIn;           // a slam around the giant's feet
                    case CONE -> nearIn && midIn && !behindIn;            // a swing towards the target
                    case RING -> !nearIn && midIn && behindIn;            // the safe spot is close to him
                    default -> true;
                };
                if (!ok) bad.add(m.id() + " near=" + nearIn + " mid=" + midIn + " behind=" + behindIn);
            }
            f.releaseAll();
            check("marker shapes: slam around the feet, cone in front, ring with a safe centre", bad.isEmpty(), String.join("; ", bad));
            for (LivingEntity e : List.of(h, near, mid, behind)) e.discard();
            return 0;
        });

        // ---------- Logia bosses ignore blows without Haki ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            HumanoidNpc z = boss(l, "zenith", c);
            z.forceFruitForTest("pika");
            HumanoidNpc att = dummy(l, c.offset(2, 0, 0));
            CombatState.of(att).armament = false;
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE);
            a.moveTo(c.getX() - 1.5, c.getY(), c.getZ() + 0.5);
            CombatState.of(a).armament = false;
            Hits.Result plain = Hits.hit(a, z, 10, Hits.Info.physical(0.1f).withPoise(0));
            float h0 = z.getHealth();
            z.invulnerableTime = 0;
            z.hurt(l.damageSources().mobAttack(att), 10);
            float afterVanilla = z.getHealth();
            BossFight f = BossManager.fightOf(z);
            CombatState.of(att).armament = true;
            z.invulnerableTime = 0;
            z.hurt(l.damageSources().mobAttack(att), 10);
            float afterHaki = z.getHealth();
            check("a Logia boss is intangible to blows without Haki (combat system)", plain == Hits.Result.INTANGIBLE, "result " + plain);
            check("...and to plain vanilla blows", f != null && afterVanilla == h0, String.format("%.1f -> %.1f", h0, afterVanilla));
            check("Armament Haki lands on a Logia boss", afterHaki < afterVanilla, String.format("%.1f -> %.1f", afterVanilla, afterHaki));
            CombatState.of(att).armament = false;
            long awakened = z.techniques().stream().filter(com.grandline.combat.Ability::awakened).count();
            check("a boss never uses its awakened powers unannounced (only as telegraphed attacks)", awakened == 0 && !z.techniques().isEmpty(),
                    awakened + " awakened among " + z.techniques().size() + " techniques");
            z.discard();
            att.discard();
            return 0;
        });

        // ---------- scaling with the number of players ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE), b = player(s, B_ID, "BossTesterB", Faction.PIRATE),
                    cc = player(s, C_ID, "BossTesterC", Faction.PIRATE);
            a.moveTo(c.getX() + 5.5, c.getY(), c.getZ() + 0.5);
            b.moveTo(c.getX() - 4.5, c.getY(), c.getZ() + 0.5);
            cc.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 5.5);
            HumanoidNpc h = boss(l, "hrolf", c);
            h.getAttribute(Attributes.ARMOR).setBaseValue(0);
            h.setHealth(h.getMaxHealth() * 0.95f);
            BossFight f = BossManager.start(h);
            f.engage(a);
            f.refreshNow();
            int solo = f.players();
            float h0 = h.getHealth();
            h.invulnerableTime = 0;
            h.hurt(l.damageSources().playerAttack(a), 20);
            float lossSolo = h0 - h.getHealth();
            float dmgSolo = f.damageScale();
            f.engage(b);
            f.engage(cc);
            f.refreshNow();
            int trio = f.players();
            h0 = h.getHealth();
            h.invulnerableTime = 0;
            h.hurt(l.damageSources().playerAttack(a), 20);
            float lossTrio = h0 - h.getHealth();
            float ratio = lossTrio <= 0 ? 0 : lossSolo / lossTrio;
            check("the fight counts the players in it (1-4)", solo == 1 && trio == 3, solo + " then " + trio);
            check("a boss takes less damage per blow with more players (health scaling)", Math.abs(ratio - 2.5f) < 0.1f,
                    String.format("solo %.2f, three players %.2f, ratio %.2f (expected 2.5)", lossSolo, lossTrio, ratio));
            check("a boss hits harder with more players", f.damageScale() > dmgSolo, String.format("%.2f -> %.2f", dmgSolo, f.damageScale()));
            h.discard();
            return 0;
        });

        // ---------- everyone leaves: the boss recovers and the fight starts over ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            HumanoidNpc m = boss(l, "meridian", c);
            HumanoidNpc foe = dummy(l, c.offset(3, 0, 0));
            m.setHealth(m.getMaxHealth() * 0.5f);
            BossFight f = BossManager.start(m);
            f.engage(foe);
            int phase = f.phase();
            foe.discard();
            f.idleForTest(20 * 31);
            f.tickForTest();
            check("when everyone has left, the boss recovers and its fight ends", f.ended() && m.getHealth() >= m.getMaxHealth() - 0.01f && BossManager.fightOf(m) == null
                    && f.bar().getPlayers().isEmpty(), "phase was " + phase + ", ended=" + f.ended() + " health " + m.getHealth());
            m.discard();
            return 0;
        });

        // ---------- fair permadeath: no finishing blow while someone else still fights ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE), b = player(s, B_ID, "BossTesterB", Faction.PIRATE);
            a.moveTo(c.getX() + 3.5, c.getY(), c.getZ() + 0.5);
            b.moveTo(c.getX() - 3.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc k = boss(l, "kurogane", c.offset(0, 0, 3));
            BossFight f = BossManager.start(k);
            f.engage(a);
            f.engage(b);
            check("Kurogane is a killer (finishes downed players when alone)", k.killsDowned(), "");
            Knockdown.knockDown(a, l.damageSources().mobAttack(k));
            CharacterData da = Characters.get(a);
            check("a player knocked down by the boss while another still fights is left for later", da.isKnockedDown() && f.deferredCount() == 1, "deferred " + f.deferredCount());
            k.setTarget(a);
            f.tickForTest();
            check("the boss turns to the next foe still standing", k.getTarget() == b, "target " + (k.getTarget() == null ? "none" : k.getTarget().getName().getString()));
            LivingIncomingDamageEvent spared = new LivingIncomingDamageEvent(a, new DamageContainer(l.damageSources().mobAttack(k), 60));
            NeoForge.EVENT_BUS.post(spared);
            check("the boss cannot finish the downed player while another fights", spared.isCanceled(), "");
            Knockdown.knockDown(b, l.damageSources().mobAttack(k));
            LivingIncomingDamageEvent finish = new LivingIncomingDamageEvent(a, new DamageContainer(l.damageSources().mobAttack(k), 60));
            NeoForge.EVENT_BUS.post(finish);
            f.tickForTest();
            check("once nobody is left standing, the usual rules apply again", !finish.isCanceled() && f.deferredCount() == 0,
                    "finish canceled=" + finish.isCanceled() + " deferred=" + f.deferredCount());
            Knockdown.standUp(a, da, 1f);
            Knockdown.standUp(b, Characters.get(b), 1f);
            k.discard();
            return 0;
        });
        steps.add(s -> {
            // a Marine boss holds its arrest of a downed player until nobody else is fighting
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE), b = player(s, B_ID, "BossTesterB", Faction.PIRATE);
            a.moveTo(c.getX() + 3.5, c.getY(), c.getZ() + 0.5);
            b.moveTo(c.getX() - 3.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc con = boss(l, "constance", c.offset(0, 0, 3));
            BossFight f = BossManager.start(con);
            f.engage(a);
            f.engage(b);
            Knockdown.knockDown(a, l.damageSources().mobAttack(con));
            boolean heldBack = Characters.get(a).isKnockedDown() && !Characters.get(a).flags.contains("imprisoned");
            Knockdown.knockDown(b, l.damageSources().mobAttack(con)); // the last one standing falls: arrested at once
            f.tickForTest(); // ...and now the first one too
            boolean bothJailed = Characters.get(a).flags.contains("imprisoned") && Characters.get(b).flags.contains("imprisoned");
            check("a Marine boss does not arrest a downed player while another still fights; it does once nobody does", heldBack && bothJailed,
                    "held back=" + heldBack + " both arrested after=" + bothJailed);
            con.discard();
            for (FakePlayer p : List.of(a, b)) player(s, p.getUUID(), p.getGameProfile().getName(), Faction.PIRATE); // out of the cells
            return 0;
        });

        // ---------- crowd control, foresight, enrage, Conqueror's clash ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            HumanoidNpc au = boss(l, "aurelia", c);
            au.forceFruitForTest("hoshi");
            HumanoidNpc att = dummy(l, c.offset(2, 0, 0));
            BossFight f = BossManager.start(au);
            f.engage(att);
            au.addEffect(new MobEffectInstance(ModEffects.KNOCKED_OUT, 200, 0));
            au.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 5));
            f.tickForTest();
            MobEffectInstance slow = au.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            check("a boss cannot be knocked out", !au.hasEffect(ModEffects.KNOCKED_OUT), "");
            check("a boss never bursts its Conqueror's Haki at random (only as a phase begins)",
                    au.hakiConqueror() > 0 && au.techniques().stream().noneMatch(a -> a.id().equals("haki/conqueror/burst")), "coq " + au.hakiConqueror());
            check("long, strong crowd control is cut short on a boss", slow != null && slow.getDuration() <= 40 && slow.getAmplifier() <= 1,
                    slow == null ? "none" : slow.getDuration() + " ticks, amplifier " + slow.getAmplifier());
            Hits.Info poke = Hits.Info.physical(0f).withPoise(0).imbue(true);
            FakePlayer pa = player(s, A_ID, "BossTesterA", Faction.PIRATE); // a player's blows (an NPC bystander's are ignored)
            pa.moveTo(c.getX() - 1.5, c.getY(), c.getZ() + 0.5);
            int dodged = 0, landed = 0;
            for (int i = 0; i < 60; i++) {
                au.invulnerableTime = 0;
                Hits.Result r = Hits.hit(pa, au, 0.5f, poke);
                if (r == Hits.Result.DODGED) dodged++;
                else if (r == Hits.Result.HIT) landed++;
            }
            long now = l.getGameTime();
            CombatState.of(au).staggeredUntil = now + 10;
            f.tickForTest();
            int dodgedAfter = 0, landedAfter = 0;
            for (int i = 0; i < 60; i++) {
                au.invulnerableTime = 0;
                Hits.Result r = Hits.hit(pa, au, 0.5f, poke);
                if (r == Hits.Result.DODGED) dodgedAfter++;
                else if (r == Hits.Result.HIT) landedAfter++;
            }
            check("advanced Observation: the boss reads blows coming", dodged >= 8 && landed + dodged == 60, dodged + "/60 dodged, " + landed + " landed");
            check("a stagger breaks the boss's foresight", dodgedAfter == 0 && landedAfter == 60 && f.foresightBreaks() >= 1 && f.foresightBrokenUntil() > now + 100,
                    dodgedAfter + "/60 dodged after the stagger (" + landedAfter + " landed), breaks " + f.foresightBreaks());
            // the enrage timer
            float before = f.damageScale();
            f.ageForTest(f.def.enrageTicks() + 20);
            f.tickForTest();
            check("the enrage timer runs out: the boss hits harder", f.enraged() && Math.abs(f.damageScale() / before - 1.5f) < 0.01f
                    && barKey(f).equals("grandline.boss.bar.enraged") && f.bar().shouldDarkenScreen(), String.format("enraged=%s %.2f -> %.2f %s", f.enraged(), before, f.damageScale(), barKey(f)));
            au.discard();
            att.discard();
            return 0;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE), b = player(s, B_ID, "BossTesterB", Faction.PIRATE);
            CharacterData da = Characters.get(a);
            da.conquerorTrait = da.conquerorAwakened = true;
            da.addMastery(Haki.COQ, 20000);
            Characters.get(b).conquerorTrait = Characters.get(b).conquerorAwakened = false;
            a.moveTo(c.getX() + 4.5, c.getY(), c.getZ() + 0.5);
            b.moveTo(c.getX() - 4.5, c.getY(), c.getZ() + 0.5);
            a.removeAllEffects();
            b.removeAllEffects();
            HumanoidNpc h = boss(l, "hrolf", c.offset(0, 0, 3));
            BossFight f = BossManager.start(h);
            f.engage(a);
            f.engage(b);
            h.invulnerableTime = 0;
            h.hurt(l.damageSources().playerAttack(a), 5000); // into phase 2: the roar is a Conqueror's burst
            boolean bAffected = b.hasEffect(ModEffects.KNOCKED_OUT) || b.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
            check("a phase begins with the boss's Conqueror's Haki", f.transitioning() && bAffected, "transitioning=" + f.transitioning() + " other player affected=" + bAffected);
            check("a player with Conqueror's Haki meets it in a clash", f.clashes() >= 1 && !a.hasEffect(ModEffects.KNOCKED_OUT), "clashes " + f.clashes());
            da.conquerorTrait = da.conquerorAwakened = false;
            a.removeAllEffects();
            b.removeAllEffects();
            h.discard();
            return 0;
        });

        // ---------- defeat: the relic, scrolls, bounty and the story ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE), b = player(s, B_ID, "BossTesterB", Faction.PIRATE);
            a.moveTo(c.getX() + 4.5, c.getY(), c.getZ() + 0.5);
            b.moveTo(c.getX() - 4.5, c.getY(), c.getZ() + 0.5);
            CharacterData da = Characters.get(a), db = Characters.get(b);
            da.completedQuests.add("oni_1");
            da.questProgress.remove("oni_2");
            da.completedQuests.remove("oni_2");
            boolean started = Quests.start(a, "oni_2");
            db.completedQuests.add("oni_1");
            db.questProgress.remove("oni_2");
            db.completedQuests.remove("oni_2");
            Quests.start(b, "oni_2");
            long bountyA = da.bounty, bountyB = db.bounty;
            int scrollsA = count(a, BossItems.HAKI_SCROLL.get());
            HumanoidNpc k = boss(l, "kurogane", c.offset(0, 0, 4));
            k.setHealth(k.getMaxHealth() * 0.3f);
            BossFight f = BossManager.start(k);
            f.engage(a);
            f.engage(b);
            check("a wounded boss picks up in the matching phase", f.phase() == 3, "phase " + f.phase());
            Vec3 at = k.position();
            k.invulnerableTime = 0;
            k.hurt(l.damageSources().playerAttack(a), 5000);
            List<ItemEntity> drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4), e -> "kurogane".equals(BossRewards.relicOf(e.getItem())));
            ItemStack relic = drops.isEmpty() ? ItemStack.EMPTY : drops.get(0).getItem();
            check("defeating a boss drops its unique relic", !k.isAlive() && drops.size() == 1 && relic.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("grandline", "iron_kanabo"))),
                    drops.size() + " relics, " + (relic.isEmpty() ? "" : relic.getHoverName().getString()));
            check("the relic is stronger than its base weapon", attack(relic) > attack(new ItemStack(relic.getItem())), attack(relic) + " vs " + attack(new ItemStack(relic.getItem())));
            check("everyone in the fight gets a Haki scroll and a style scroll", count(a, BossItems.HAKI_SCROLL.get()) == scrollsA + 1 && count(b, BossItems.HAKI_SCROLL.get()) >= 1
                    && count(a, ModItems.STYLE_SCROLL.get()) >= 1, "A haki " + count(a, BossItems.HAKI_SCROLL.get()) + ", B haki " + count(b, BossItems.HAKI_SCROLL.get()));
            check("defeating an Emperor raises the bounty a lot", da.bounty - bountyA >= 1_000_000_000L && db.bounty - bountyB >= 1_000_000_000L,
                    String.format("A +%,d, B +%,d", da.bounty - bountyA, db.bounty - bountyB));
            check("the story objective is credited (killer and group)", started && da.completedQuests.contains("oni_2") && db.completedQuests.contains("oni_2"),
                    "started=" + started + " A done=" + da.completedQuests.contains("oni_2") + " B done=" + db.completedQuests.contains("oni_2"));
            check("the boss is gone for good in the story", NotableState.get(s).status("kurogane") == NotableState.Status.DEFEATED, NotableState.get(s).status("kurogane").toString());
            keepFate(s, "kurogane");
            for (ItemEntity e : drops) e.discard();
            return 0;
        });
        steps.add(s -> {
            // a rival yields instead of dying: the relic still drops (once), the story still counts
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE);
            a.moveTo(c.getX() + 4.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc h = boss(l, "hrolf", c.offset(0, 0, 4));
            h.setHealth(h.getMaxHealth() * 0.2f);
            BossFight f = BossManager.start(h);
            f.engage(a);
            h.invulnerableTime = 0;
            h.hurt(l.damageSources().playerAttack(a), 5000);
            f.tickForTest();
            Vec3 at = h.position();
            List<ItemEntity> drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4), e -> "hrolf".equals(BossRewards.relicOf(e.getItem())));
            check("a rival boss yields and pays out its relic", h.isAlive() && h.flag(GLNpc.FLAG_SURRENDER) && drops.size() == 1 && f.ended(),
                    "alive=" + h.isAlive() + " yielded=" + h.flag(GLNpc.FLAG_SURRENDER) + " relics=" + drops.size());
            for (ItemEntity e : drops) e.discard();
            h.discard();
            return 0;
        });
        steps.add(s -> {
            // seastone cuffs only close on a beaten boss; an arrest pays out too
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.MARINE);
            a.moveTo(c.getX() + 1.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc st = boss(l, "sterling", c.offset(0, 0, 2));
            BossFight f = BossManager.start(st);
            f.engage(a);
            long beli = Characters.get(a).beli;
            a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SEASTONE_CUFFS.get(), 4));
            CombatState.of(st).staggeredUntil = l.getGameTime() + 10; // staggered, but far from beaten
            a.interactOn(st, InteractionHand.MAIN_HAND);
            boolean resisted = st.isAlive() && !st.isRemoved() && !st.flag(GLNpc.FLAG_ARRESTED);
            st.setHealth(st.getMaxHealth() * 0.2f);
            Vec3 at = st.position();
            a.interactOn(st, InteractionHand.MAIN_HAND);
            boolean arrested = st.isRemoved() && st.flag(GLNpc.FLAG_ARRESTED);
            List<ItemEntity> drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4), e -> "sterling".equals(BossRewards.relicOf(e.getItem())));
            check("seastone cuffs do not close on a boss that is not beaten down", resisted, "");
            check("arresting a beaten boss counts as defeating it (relic, reward)", arrested && drops.size() == 1 && Characters.get(a).beli > beli
                    && NotableState.get(s).status("sterling") == NotableState.Status.ARRESTED,
                    "arrested=" + arrested + " relics=" + drops.size() + " beli " + beli + " -> " + Characters.get(a).beli);
            keepFate(s, "sterling");
            for (ItemEntity e : drops) e.discard();
            a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return 0;
        });
        steps.add(s -> {
            // a Haki scroll teaches
            FakePlayer a = player(s, A_ID, "BossTesterA", Faction.PIRATE);
            CharacterData d = Characters.get(a);
            d.armamentUnlocked = false;
            String first = HakiScrollItem.read(a, d, "armament");
            int m0 = d.mastery(Haki.ARM);
            String second = HakiScrollItem.read(a, d, "armament");
            check("a Haki scroll awakens a sleeping Haki, then trains it", d.armamentUnlocked && "grandline.haki_scroll.awakened".equals(first)
                    && "grandline.haki_scroll.trained".equals(second) && d.mastery(Haki.ARM) > m0, "mastery " + m0 + " -> " + d.mastery(Haki.ARM));
            return 0;
        });

        // ---------- every boss: signature attacks, phase powers and arena run without errors ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            BlockPos c = arena(l);
            List<String> errors = new ArrayList<>(), harmless = new ArrayList<>();
            int moves = 0, extras = 0;
            BossManager.errors(); // (cleared below)
            int errorsBefore = BossManager.errors().size();
            for (BossDef d : Bosses.all()) {
                HumanoidNpc b = boss(l, d.id, c.offset(-3, 0, 0));
                HumanoidNpc dmy = dummy(l, c.offset(1, 0, 0));
                BossFight f = BossManager.start(b);
                f.engage(dmy);
                float dealt = 0;
                try {
                    for (int p = 1; p <= d.phaseCount(); p++) {
                        f.forcePhaseForTest(p);
                        for (BossMove m : d.movesFor(p)) {
                            dmy.setHealth(dmy.getMaxHealth());
                            dmy.teleportTo(c.getX() + 1.5, c.getY(), c.getZ() + 0.5);
                            float h0 = dmy.getHealth();
                            f.startMove(m, dmy, null, true);
                            f.releaseAll();
                            for (int t = 0; t < 120; t++) CombatState.of(b).tick(l.getGameTime() + t); // follow-ups of reused powers
                            dealt += Math.max(0, h0 - dmy.getHealth());
                            moves++;
                        }
                        f.arenaTickForTest(30);
                        f.tickHazardsForTest();
                        f.releaseAll();
                    }
                    for (BossMove m : Bosses.extraMoves(d.id)) {
                        dmy.setHealth(dmy.getMaxHealth());
                        f.startMove(m, dmy, dmy.position(), false);
                        f.releaseAll();
                        for (int t = 0; t < 120; t++) CombatState.of(b).tick(l.getGameTime() + t);
                        extras++;
                    }
                } catch (Throwable t) {
                    errors.add(d.id + ": " + t);
                    com.grandline.GrandLine.LOGGER.error("[SELFTEST] boss {} failed", d.id, t);
                }
                if (dealt <= 0) harmless.add(d.id);
                f.releaseAll();
                b.discard();
                dmy.discard();
            }
            List<String> caught = BossManager.errors();
            if (caught.size() > errorsBefore) errors.addAll(caught.subList(errorsBefore, caught.size()));
            for (var p : l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(c).inflate(80))) p.discard();
            TerrainChanges.revertAll(s);
            check("every boss's signature attacks, phase powers and arena run without errors", errors.isEmpty(),
                    moves + " attacks, " + extras + " arena/phase moves" + (errors.isEmpty() ? "" : ": " + String.join("; ", errors.subList(0, Math.min(8, errors.size())))));
            check("every boss's signature attacks land on a foe in the marker", harmless.isEmpty(), harmless.isEmpty() ? Bosses.all().size() + " bosses" : "no damage from " + harmless);
            return 0;
        });

        // ---------- clean-up ----------
        steps.add(s -> {
            for (LivingEntity e : SPAWNED) if (!e.isRemoved()) e.discard();
            SPAWNED.clear();
            NotableState st = NotableState.get(s);
            long now = s.overworld().getGameTime();
            for (var e : SAVED.entrySet()) { // as before the tests, or the fate a test produced (Kurogane fell, Sterling was arrested)
                NotableState.Entry was = e.getValue();
                st.set(e.getKey(), was.status, was.entity, was.fate, now);
            }
            check("story fates the tests produced are kept", st.status("kurogane") == NotableState.Status.DEFEATED && st.status("sterling") == NotableState.Status.ARRESTED,
                    "kurogane " + st.status("kurogane") + ", sterling " + st.status("sterling"));
            for (UUID id : List.of(A_ID, B_ID, C_ID)) {
                FakePlayer p = FakePlayerFactory.get(s.overworld(), new GameProfile(id, "x"));
                p.setGameMode(GameType.CREATIVE);
                p.moveTo(arena(s.overworld()).getX() + 0.5, arena(s.overworld()).getY() + 30, arena(s.overworld()).getZ() + 0.5);
            }
            check("boss fights leave no errors behind", BossManager.errors().isEmpty(), String.join("; ", BossManager.errors().subList(0, Math.min(5, BossManager.errors().size()))));
            return 0;
        });
    }

    /** After a restart: which relics dropped is remembered (each exists once per world), and so are the fates. */
    static void phase2(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            BossRecords rec = BossRecords.get(s);
            check("boss relic records persist across a restart", rec.relicDropped("kurogane") && rec.relicDropped("hrolf") && rec.relicDropped("sterling")
                    && rec.claimed(A_ID, "kurogane"), "kurogane=" + rec.relicDropped("kurogane") + " hrolf=" + rec.relicDropped("hrolf"));
            check("a defeated boss stays defeated after a restart", NotableState.get(s).status("kurogane") == NotableState.Status.DEFEATED,
                    NotableState.get(s).status("kurogane").toString());
            return 0;
        });
    }

    // ------------------------------------------------------------------ helpers

    /** A flat platform high above Shiokaze Isle, aligned to a chunk centre and kept loaded. */
    static BlockPos arena(ServerLevel l) {
        if (center == null) {
            Island i = WorldPlan.of(l.getServer()).byId("shiokaze_isle");
            int cx = ((i.x + 60) >> 4 << 4) + 8, cz = ((i.z + 180) >> 4 << 4) + 8;
            BlockPos c = new BlockPos(cx, 214, cz);
            for (int ox = -1; ox <= 1; ox++)
                for (int oz = -1; oz <= 1; oz++) l.setChunkForced((cx >> 4) + ox, (cz >> 4) + oz, true);
            for (int dx = -18; dx <= 18; dx++)
                for (int dz = -18; dz <= 18; dz++) {
                    l.setBlock(c.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 0; dy < 8; dy++) l.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            center = c;
        }
        return center;
    }

    static FakePlayer player(MinecraftServer s, UUID id, String name, Faction faction) {
        FakePlayer fp = FakePlayerFactory.get(s.overworld(), new GameProfile(id, name));
        CharacterData d = Characters.get(fp);
        if (!d.created) {
            d.created = true;
            d.name = name;
            d.race = Race.HUMAN;
            d.level = 40;
            d.knownStyles.add("brawler");
        }
        d.faction = faction;
        d.knockdownTicks = 0;
        d.knockedBy = null;
        d.flags.remove("imprisoned");
        d.flags.removeIf(f -> f.startsWith("cell:"));
        fp.setGameMode(GameType.SURVIVAL);
        fp.setHealth(fp.getMaxHealth());
        fp.setForcedPose(null);
        return fp;
    }

    /** A boss as the story spawns it (its notable data), at a spot of the test platform, without AI. */
    public static HumanoidNpc boss(ServerLevel l, String id, BlockPos at) {
        MinecraftServer s = l.getServer();
        NotableCharacters.Notable n = NotableCharacters.get(id);
        NotableState st = NotableState.get(s);
        if (!SAVED.containsKey(id)) SAVED.put(id, copy(st.entry(id)));
        HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(l);
        npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        npc.configure(n.role(), n.tier(), "", at, "", l.random);
        String rolled = npc.fruitId();
        if (rolled != null) FruitWorldData.get(s).release(s, rolled, npc.getUUID());
        npc.makeNotable(id, n.fruit(), n.obs(), n.arm(), n.coq(), n.style());
        npc.setNotableBody(n.tier(), n.race());
        npc.setAllegiance(n.faction(), at);
        npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, n.maxHealth()));
        npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2 + n.tier() * 1.4);
        npc.getAttribute(Attributes.ARMOR).setBaseValue(Math.min(30, 2 + n.tier() * 1.6));
        if (Math.abs(n.scale() - 1f) > 1e-3) npc.getAttribute(Attributes.SCALE).setBaseValue(n.scale());
        npc.setHealth(npc.getMaxHealth());
        Item w = n.weapon().isEmpty() ? Items.AIR : BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("grandline", n.weapon()));
        npc.setItemSlot(EquipmentSlot.MAINHAND, w == Items.AIR ? ItemStack.EMPTY : new ItemStack(w));
        npc.setCustomName(NotableCharacters.name(n));
        npc.setNoAi(true);
        npc.refreshDimensions();
        st.set(id, NotableState.Status.ALIVE, npc.getUUID(), "", l.getGameTime());
        l.addFreshEntity(npc);
        SPAWNED.add(npc);
        return npc;
    }

    private static NotableState.Entry copy(NotableState.Entry cur) {
        NotableState.Entry c = new NotableState.Entry();
        c.status = cur.status;
        c.entity = cur.entity;
        c.fate = cur.fate;
        c.since = cur.since;
        return c;
    }

    /** Keep the story fate a test produced (later tests borrow the same character; phase 2 checks the fate). */
    private static void keepFate(MinecraftServer s, String id) {
        SAVED.put(id, copy(NotableState.get(s).entry(id)));
    }

    /** A sturdy target that stands still. */
    static HumanoidNpc dummy(ServerLevel l, BlockPos at) {
        HumanoidNpc n = SelfTest.npc(l, "bandit", 1, at);
        n.setNoAi(true);
        if (n.fruitId() != null) { // a plain target: any fruit it rolled goes back to the world
            FruitWorldData.get(l.getServer()).release(l.getServer(), n.fruitId(), n.getUUID());
            n.forceFruitForTest("");
        }
        n.getAttribute(Attributes.MAX_HEALTH).setBaseValue(600);
        n.getAttribute(Attributes.ARMOR).setBaseValue(0);
        n.setHealth(600);
        SPAWNED.add(n);
        return n;
    }

    /** The bar's text key (names are translated on the client, so the test reads the keys). */
    private static String barKey(BossFight f) {
        return f.bar().getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "";
    }

    /** Translation keys found anywhere in the bar's arguments. */
    private static Set<String> barArgs(BossFight f) {
        Set<String> out = new TreeSet<>();
        if (f.bar().getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)
            for (Object o : tc.getArgs()) if (o instanceof net.minecraft.network.chat.Component c) keys(c, out);
        return out;
    }

    private static void keys(net.minecraft.network.chat.Component c, Set<String> out) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            out.add(tc.getKey());
            for (Object o : tc.getArgs()) if (o instanceof net.minecraft.network.chat.Component a) keys(a, out);
        }
        for (net.minecraft.network.chat.Component s : c.getSiblings()) keys(s, out);
    }

    private static Set<String> ids(List<BossMove> moves) {
        Set<String> out = new TreeSet<>();
        for (BossMove m : moves) out.add(m.id());
        return out;
    }

    private static int count(FakePlayer p, Item item) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(item)) n += st.getCount();
        return n;
    }

    private static double attack(ItemStack st) {
        double[] sum = {0};
        st.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.value() == Attributes.ATTACK_DAMAGE.value()) sum[0] += mod.amount();
        });
        return sum[0];
    }

    private static String resource(String path) {
        try (InputStream in = BossTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private BossTests() {}
}
