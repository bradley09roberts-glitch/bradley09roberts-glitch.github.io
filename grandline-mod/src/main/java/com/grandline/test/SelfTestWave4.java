package com.grandline.test;

import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.Zoan;
import com.grandline.fruit.impl.HataFruit;
import com.grandline.fruit.impl.ShinjuFruit;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Self-test checks for the fourth wave of ORIGINAL Devil Fruits (phase 1): every new fruit damages a dummy with its
 * own abilities, the three Logia are intangible to plain hits (Haki connects) and keep their own rules, the five Zoans
 * transform and refuse form-only abilities in human form, every awakening ability is refused before awakening and
 * works after, plus signature mechanics (Kagi lock, Fusen inflate, Kai impact store, Shinju nacre, Hata brand).
 */
final class SelfTestWave4 {
    static final List<String> FRUITS = List.of("hai", "yako", "shinku", "kai", "fusen", "shinju", "kagi", "hanabi", "hata", "taru",
            "kujira_bakekujira", "itachi_raiju", "sasori_seascorpion", "tonbo_meganeura", "kaba_hippo");
    static final List<String> ZOANS = List.of("kujira_bakekujira", "itachi_raiju", "sasori_seascorpion", "tonbo_meganeura", "kaba_hippo");
    /** A form-only ability of each Zoan (refused in human form). */
    static final List<String> GATED = List.of("bone_breach", "thunder_claw", "pincer_crush", "wing_slash", "gaping_jaws");
    /** The fake player's fruit before these checks (restored at the end so later checks see the same state). */
    private static String fruitBefore;

    private static void check(String name, boolean ok, String detail) {
        SelfTest.check(name, ok, detail);
    }

    /** Forget everything the fake player still has pending and revert the arena's temporary terrain. */
    private static void reset(FakePlayer fp, ServerLevel l) {
        synchronized (CombatState.of(fp).scheduled) {
            CombatState.of(fp).scheduled.clear();
        }
        CombatState.of(fp).cooldowns.clear();
        CombatState.of(fp).buffs.clear();
        CombatState.of(fp).staggeredUntil = 0;
        CombatState.of(fp).iframesUntil = 0;
        TerrainChanges.revertAll(l.getServer());
    }

    private static void runScheduled(FakePlayer fp, ServerLevel l, int ticks) {
        for (int t = 0; t <= ticks; t++) CombatState.of(fp).tick(l.getGameTime() + t);
    }

    private static void runScheduled(HumanoidNpc n, ServerLevel l, int ticks) {
        for (int t = 0; t <= ticks; t++) CombatState.of(n).tick(l.getGameTime() + t);
    }

    private static HumanoidNpc dummy(ServerLevel l, String role, BlockPos at) {
        HumanoidNpc n = SelfTest.npc(l, role, 3, at);
        n.setNoAi(true);
        n.forceFruitForTest("");
        n.clearHakiForTest(); // a tier-3 NPC may roll Armament, which it uses whenever it has a target
        return n;
    }

    private static void heal(HumanoidNpc n) {
        n.setHealth(n.getMaxHealth());
        n.removeAllEffects();
        n.clearFire();
        CombatState.of(n).buffs.clear();
        CombatState.of(n).staggeredUntil = 0;
        CombatState.of(n).iframesUntil = 0;
        CombatState.of(n).poise = 100;
        CombatState.of(n).blocking = false;
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- every wave-4 fruit damages a dummy with its own abilities ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            boolean awakenedBefore = d.fruitAwakened;
            fruitBefore = d.fruit;
            d.fruitAwakened = true;
            for (String id : FRUITS) {
                DevilFruit f = DevilFruits.get(id);
                if (f == null || !DevilFruits.implemented(f)) {
                    check("Wave 4 fruit " + id + " damages a dummy", false, "fruit missing or without abilities");
                    continue;
                }
                reset(fp, l);
                HumanoidNpc dummy = dummy(l, "bandit", c.offset(0, 0, 2));
                dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                dummy.setHealth(1000);
                d.fruit = id;
                int used = 0;
                List<String> refused = new ArrayList<>();
                float dealt = 0;
                for (Ability a : f.abilities()) {
                    fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
                    dummy.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                    dummy.setDeltaMovement(Vec3.ZERO);
                    dummy.removeEffect(MobEffects.LEVITATION);
                    fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
                    d.zoanForm = f.type.isZoan() ? "hybrid" : "";
                    CombatState.of(fp).cooldowns.clear();
                    float before = dummy.getHealth();
                    if (CombatManager.useAbility(fp, a, dummy)) used++;
                    else refused.add(a.id().substring(a.id().lastIndexOf('/') + 1));
                    runScheduled(fp, l, 60);
                    dealt += Math.max(0, before - dummy.getHealth());
                    if (dummy.getHealth() < 200) dummy.setHealth(1000);
                }
                check("Wave 4 fruit " + id + " damages a dummy", dealt > 0 && refused.isEmpty(),
                        String.format("%d/%d abilities used, %.0f damage%s", used, f.abilities().size(), dealt, refused.isEmpty() ? "" : ", refused: " + refused));
                dummy.discard();
            }
            d.zoanForm = "";
            Zoan.applyStats(fp, "");
            d.fruitAwakened = awakenedBefore;
            reset(fp, l);
            for (var p : l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(c).inflate(60))) p.discard();
            return 0;
        });

        // ---------- the three Logia: intangible to plain hits, Haki connects, and their own rules ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            HumanoidNpc att = dummy(l, "marine", c.offset(-6, 0, 6));
            HumanoidNpc vic = dummy(l, "pirate", c.offset(-6, 0, 8));
            att.setTarget(vic);
            vic.setTarget(att);
            Hits.Info punch = Hits.Info.physical(0.1f).asUnblockable();
            for (String id : List.of("hai", "yako", "shinku")) {
                heal(vic);
                vic.forceFruitForTest(id);
                Hits.Result plain = Hits.hit(att, vic, 4, punch);
                Hits.Result haki = Hits.hit(att, vic, 4, punch.imbue(true));
                check("Wave 4 Logia " + id + " is intangible to plain hits, Haki connects", plain == Hits.Result.INTANGIBLE && haki == Hits.Result.HIT, "plain=" + plain + " haki=" + haki);
            }
            // Hai: fire passes through the ash; water turns it to slurry and then even plain blows connect
            heal(vic);
            vic.forceFruitForTest("hai");
            float h0 = vic.getHealth();
            Hits.Result fire = Hits.hit(att, vic, 6, Hits.Info.of(Element.FIRE, 0f).asUnblockable().imbue(true));
            float fireTook = h0 - vic.getHealth();
            Hits.Result water = Hits.hit(att, vic, 3, Hits.Info.of(Element.WATER, 0f).asUnblockable());
            Hits.Result after = Hits.hit(att, vic, 3, punch);
            check("Hai: fire cannot burn ash, water makes it solid slurry", fireTook <= 0.01f && water == Hits.Result.HIT && after == Hits.Result.HIT,
                    "fire=" + fire + " took " + fireTook + ", water=" + water + ", plain after water=" + after);
            // Yako: darkness smothers the glow
            heal(vic);
            vic.forceFruitForTest("yako");
            Hits.Result dark = Hits.hit(att, vic, 3, Hits.Info.of(Element.DARKNESS, 0f).asUnblockable());
            check("Yako: darkness always connects", dark == Hits.Result.HIT, "darkness=" + dark);
            // Shinku: impact rushes into the void; sound cannot cross it even with Haki
            heal(vic);
            vic.forceFruitForTest("shinku");
            Hits.Result impact = Hits.hit(att, vic, 3, Hits.Info.of(Element.IMPACT, 0f).asUnblockable());
            h0 = vic.getHealth();
            Hits.Result sound = Hits.hit(att, vic, 5, Hits.Info.of(Element.SOUND, 0f).asUnblockable().imbue(true));
            check("Shinku: impact always connects, sound cannot reach the void", impact == Hits.Result.HIT && vic.getHealth() >= h0,
                    "impact=" + impact + " sound=" + sound + " hp " + h0 + " -> " + vic.getHealth());
            att.discard();
            vic.discard();
            return 0;
        });

        // ---------- the five Zoans transform; form-only abilities wait for the form ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            HumanoidNpc dummy = dummy(l, "bandit", c.offset(0, 0, 2));
            for (int i = 0; i < ZOANS.size(); i++) {
                String id = ZOANS.get(i);
                reset(fp, l);
                fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
                dummy.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                heal(dummy);
                fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
                d.fruit = id;
                d.zoanForm = "";
                Zoan.applyStats(fp, "");
                Ability gated = Abilities.get("fruit/" + id + "/" + GATED.get(i));
                boolean refusedHuman = gated != null && !CombatManager.useAbility(fp, gated, dummy);
                boolean transformed = CombatManager.useAbility(fp, Abilities.get("fruit/" + id + "/transform"), null) && "hybrid".equals(d.zoanForm);
                CombatState.of(fp).cooldowns.clear();
                float h0 = dummy.getHealth();
                boolean worksInForm = gated != null && CombatManager.useAbility(fp, gated, dummy);
                runScheduled(fp, l, 40);
                boolean hurt = dummy.getHealth() < h0;
                check("Wave 4 Zoan " + id + " transforms; " + GATED.get(i) + " needs the form", refusedHuman && transformed && worksInForm && hurt,
                        "refusedHuman=" + refusedHuman + " transformed=" + transformed + " (form=" + d.zoanForm + ") inForm=" + worksInForm + " hurt=" + hurt);
                Zoan.setForm(fp, "");
            }
            dummy.discard();
            reset(fp, l);
            return 0;
        });

        // ---------- awakening abilities are refused before awakening and work after ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            boolean awakenedBefore = d.fruitAwakened;
            HumanoidNpc dummy = dummy(l, "bandit", c.offset(0, 0, 2));
            dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            List<String> bad = new ArrayList<>();
            int ok = 0;
            for (String id : FRUITS) {
                DevilFruit f = DevilFruits.get(id);
                Ability aw = f == null ? null : f.abilities().stream().filter(Ability::awakened).findFirst().orElse(null);
                if (aw == null) {
                    bad.add(id + "(no awakening ability)");
                    continue;
                }
                reset(fp, l);
                fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
                dummy.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                heal(dummy);
                dummy.setHealth(1000);
                fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
                d.fruit = id;
                d.zoanForm = f.type.isZoan() ? "hybrid" : "";
                d.fruitAwakened = false;
                boolean refused = !CombatManager.useAbility(fp, aw, dummy);
                d.fruitAwakened = true;
                boolean works = CombatManager.useAbility(fp, aw, dummy);
                if (refused && works) ok++;
                else bad.add(id + " refused=" + refused + " works=" + works);
            }
            check("Wave 4 awakening abilities refused before awakening, work after", bad.isEmpty(), ok + "/" + FRUITS.size() + (bad.isEmpty() ? "" : " problems: " + bad));
            d.fruitAwakened = awakenedBefore;
            d.zoanForm = "";
            Zoan.applyStats(fp, "");
            dummy.discard();
            reset(fp, l);
            for (var p : l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(c).inflate(60))) p.discard();
            return 0;
        });

        // ---------- signature mechanics ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            reset(fp, l);
            d.zoanForm = "";
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
            HumanoidNpc a = dummy(l, "pirate", c.offset(0, 0, 3));
            HumanoidNpc b = dummy(l, "marine", c.offset(0, 0, 5));
            a.setTarget(b);
            b.setTarget(a);
            fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, a.getEyePosition());
            Hits.Info punch = Hits.Info.physical(0f).asUnblockable();

            // Kagi — Lock Away: the target's own fruit techniques are locked (on cooldown, refused).
            heal(a);
            a.forceFruitForTest("mera");
            d.fruit = "kagi";
            boolean lockUsed = CombatManager.useAbility(fp, Abilities.get("fruit/kagi/lock_away"), a);
            long now = l.getGameTime();
            boolean locked = CombatState.of(a).onCooldown("fruit/mera/fire_fist", now);
            boolean refused = !CombatManager.useAbility(a, Abilities.get("fruit/mera/fire_fist"), b);
            check("Kagi Lock Away locks the target's fruit techniques", lockUsed && locked && refused, "used=" + lockUsed + " onCooldown=" + locked + " refused=" + refused);
            a.forceFruitForTest("");
            CombatState.of(a).cooldowns.clear();

            // Fusen — Inflate: the target floats up helplessly.
            heal(a);
            d.fruit = "fusen";
            boolean inflated = CombatManager.useAbility(fp, Abilities.get("fruit/fusen/inflate"), a);
            check("Fusen Inflate makes the target float", inflated && a.hasEffect(MobEffects.LEVITATION), "used=" + inflated + " levitating=" + a.hasEffect(MobEffects.LEVITATION));
            synchronized (CombatState.of(fp).scheduled) {
                CombatState.of(fp).scheduled.clear();
            }
            a.removeEffect(MobEffects.LEVITATION);
            a.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 3.5);

            // Kai — Impact Dial: blows are soaked into the shell, then released on whoever stands in front.
            heal(a);
            heal(b);
            a.forceFruitForTest("kai");
            a.setYRot(0);
            a.setYHeadRot(0);
            a.setYBodyRot(0);
            b.teleportTo(a.getX(), a.getY(), a.getZ() + 2);
            boolean dial = CombatManager.useAbility(a, Abilities.get("fruit/kai/impact_dial"), b);
            float a0 = a.getHealth();
            Hits.hit(b, a, 8, punch);
            float soaked = a0 - a.getHealth();
            float b0 = b.getHealth();
            runScheduled(a, l, 52);
            float released = b0 - b.getHealth();
            check("Kai Impact Dial soaks a blow and releases it", dial && soaked > 0 && soaked < 8 * 0.5f && released > 0,
                    String.format("used=%s took %.1f of 8, released %.1f", dial, soaked, released));
            a.forceFruitForTest("");

            // Shinju — Nacre Armour: a layer of pearl halves the next blow.
            heal(a);
            a.forceFruitForTest("shinju");
            a0 = a.getHealth();
            Hits.hit(b, a, 8, punch);
            float bare = a0 - a.getHealth();
            boolean armour = CombatManager.useAbility(a, Abilities.get("fruit/shinju/nacre_armor"), b);
            int layers = ShinjuFruit.layers(a);
            heal(a);
            a0 = a.getHealth();
            Hits.hit(b, a, 8, punch);
            float armoured = a0 - a.getHealth();
            check("Shinju nacre layers halve a blow", armour && layers >= 3 && armoured > 0 && armoured < bare * 0.7f,
                    String.format("used=%s layers=%d, bare %.1f vs armoured %.1f", armour, layers, bare, armoured));
            a.forceFruitForTest("");

            // Hata — Jolly Roger: a branded enemy takes 20% more from anyone.
            heal(a);
            heal(b);
            a0 = a.getHealth();
            Hits.hit(b, a, 8, punch);
            float plain = a0 - a.getHealth();
            heal(a);
            HataFruit.brand(a, 100);
            a0 = a.getHealth();
            Hits.hit(b, a, 8, punch);
            float branded = a0 - a.getHealth();
            check("Hata Jolly Roger brand makes everyone hit harder", plain > 0 && branded > plain * 1.1f, String.format("plain %.2f, branded %.2f", plain, branded));

            a.discard();
            b.discard();
            reset(fp, l);
            d.fruit = fruitBefore;
            return 0;
        });
    }

    private SelfTestWave4() {}
}
