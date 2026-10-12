package com.grandline.test;

import com.grandline.combat.Abilities;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.impl.AburaFruit;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModEntities;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Deque;

/**
 * Self-test checks for signature mechanics of the third wave of ORIGINAL Devil Fruits (phase 1). Kept in its own
 * class so the shared {@link SelfTest} only needs a one-line call.
 */
final class SelfTestOriginals {

    /** Use a fruit ability as the fake player (creative, so mastery and stamina never refuse it). */
    private static boolean use(FakePlayer fp, String fruit, String ability, net.minecraft.world.entity.LivingEntity target) {
        CharacterData d = Characters.get(fp);
        d.fruit = fruit;
        d.zoanForm = "";
        CombatState.of(fp).cooldowns.clear();
        CombatState.of(fp).staggeredUntil = 0;
        return CombatManager.useAbility(fp, Abilities.get("fruit/" + fruit + "/" + ability), target);
    }

    /** Run the fake player's scheduled follow-ups (multi-pulse abilities) for {@code ticks} ticks. */
    private static void runScheduled(FakePlayer fp, ServerLevel l, int ticks) {
        for (int t = 0; t <= ticks; t++) CombatState.of(fp).tick(l.getGameTime() + t);
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- Tenbin, Kusari, Nemu: abilities used by the fake player against NPCs ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            FakePlayer fp = SelfTest.fake(s);
            // cancel what is still pending from the ability sweep (mines, sentries, storms...) so it cannot interfere
            synchronized (CombatState.of(fp).scheduled) {
                CombatState.of(fp).scheduled.clear();
            }
            TerrainChanges.revertAll(s);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
            HumanoidNpc a = SelfTest.npc(l, "bandit", 1, c.offset(0, 0, 3));
            HumanoidNpc b = SelfTest.npc(l, "bandit", 1, c.offset(2, 0, 4));
            for (HumanoidNpc n : new HumanoidNpc[]{a, b}) {
                n.setNoAi(true);
                n.forceFruitForTest("");
            }

            // Tenbin — Equilibrium: a badly hurt user and a healthy target meet in the middle.
            float max = fp.getMaxHealth();
            fp.setHealth(max * 0.3f);
            float uBefore = fp.getHealth(), tBefore = a.getHealth();
            boolean eq = use(fp, "tenbin", "equilibrium", a);
            check("Tenbin Equilibrium moves health from the healthier target to the hurt user", eq && fp.getHealth() > uBefore + 0.5f && a.getHealth() < tBefore - 0.5f,
                    String.format("used=%s user %.1f->%.1f, target %.1f->%.1f", eq, uBefore, fp.getHealth(), tBefore, a.getHealth()));
            fp.setHealth(max);
            a.setHealth(a.getMaxHealth());

            // Kusari — Shackle Link: damage to one chained enemy is echoed onto the other.
            boolean link = use(fp, "kusari", "shackle_link", a);
            a.setHealth(a.getMaxHealth());
            b.setHealth(b.getMaxHealth());
            float a0 = a.getHealth(), b0 = b.getHealth();
            Hits.hit(fp, a, 8, Hits.Info.physical(0f).asUnblockable());
            float da = a0 - a.getHealth(), db = b0 - b.getHealth();
            check("Kusari Shackle Link echoes damage onto the chained partner", link && da > 0 && db > 0 && db < da,
                    String.format("used=%s hit %.1f, echoed %.1f", link, da, db));

            // Nemu — Sandman's Touch: the target falls asleep (helpless) and wakes when hurt.
            b.setHealth(b.getMaxHealth());
            boolean touch = use(fp, "nemu", "sandman_touch", b);
            boolean slept = b.hasEffect(ModEffects.KNOCKED_OUT);
            Hits.hit(fp, b, 2, Hits.Info.physical(0f).asUnblockable());
            boolean woke = !b.hasEffect(ModEffects.KNOCKED_OUT);
            check("Nemu sleep knocks the target out until it is hurt", touch && slept && woke, "used=" + touch + " asleep=" + slept + " woke=" + woke);
            a.discard();
            b.discard();
            return 0;
        });

        // ---------- Funa — Hull Mend repairs the user's ship ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            FakePlayer fp = SelfTest.fake(s);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
            ShipEntity ship = ModEntities.SHIPS.get(ShipClass.SLOOP).get().create(l);
            if (ship == null) {
                check("Funa Hull Mend repairs the user's ship", false, "could not create a ship");
                return 0;
            }
            ship.moveTo(c.getX() + 8.5, c.getY(), c.getZ() + 0.5, 0, 0);
            ship.owner = fp.getUUID();
            ship.setHull(40);
            l.addFreshEntity(ship);
            boolean mend = use(fp, "funa", "hull_mend", null);
            runScheduled(fp, l, 110);
            check("Funa Hull Mend repairs the user's ship", mend && ship.hull() > 60, String.format("used=%s hull 40 -> %.0f / %.0f", mend, ship.hull(), ship.maxHull()));
            ship.discard();
            return 0;
        });

        // ---------- Tsuchi grounded intangibility, Abura oil + fire ----------
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = SelfTest.arena(l);
            HumanoidNpc att = SelfTest.npc(l, "marine", 3, c.offset(-6, 0, -8));
            HumanoidNpc vic = SelfTest.npc(l, "pirate", 3, c.offset(-6, 0, -5));
            att.setNoAi(true);
            vic.setNoAi(true);
            att.forceFruitForTest("");

            // Tsuchi: the soil body is intangible only while it touches the ground
            vic.forceFruitForTest("tsuchi");
            vic.setOnGround(true);
            Hits.Result grounded = Hits.hit(att, vic, 4, Hits.Info.physical(0.1f).asUnblockable());
            vic.setOnGround(false);
            Hits.Result airborne = Hits.hit(att, vic, 4, Hits.Info.physical(0.1f).asUnblockable());
            check("Tsuchi is intangible on the ground, solid in the air", grounded == Hits.Result.INTANGIBLE && airborne == Hits.Result.HIT, "grounded=" + grounded + " airborne=" + airborne);

            // Abura: an oiled body takes far more from fire (any fire, not only Abura's)
            vic.forceFruitForTest("");
            vic.setHealth(vic.getMaxHealth());
            vic.clearFire();
            float h0 = vic.getHealth();
            Hits.hit(att, vic, 6, Hits.Info.of(Element.FIRE, 0f).asUnblockable());
            float plain = h0 - vic.getHealth();
            vic.setHealth(vic.getMaxHealth());
            vic.clearFire();
            AburaFruit.oil(vic, 100);
            h0 = vic.getHealth();
            Hits.hit(att, vic, 6, Hits.Info.of(Element.FIRE, 0f).asUnblockable());
            float oiled = h0 - vic.getHealth();
            check("Abura oil makes fire hit 75% harder", plain > 0 && oiled > plain * 1.5f, String.format("fire %.2f, on oil %.2f", plain, oiled));
            att.discard();
            vic.discard();
            for (var p : l.getEntitiesOfClass(com.grandline.entity.AbilityProjectile.class, new AABB(c).inflate(40))) p.discard();
            return 0;
        });
    }

    private static void check(String name, boolean ok, String detail) {
        SelfTest.check(name, ok, detail);
    }

    private SelfTestOriginals() {}
}
