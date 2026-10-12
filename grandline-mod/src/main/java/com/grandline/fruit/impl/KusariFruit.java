package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Kusari Kusari no Mi — ORIGINAL chain Paramecia. The user produces and controls iron chains: whips, a grappling
 * hook that drags a foe in (or the user to a wall), binding chains, a whirling chain cyclone, and the signature
 * Shackle Link — two enemies chained together share their pain (half of the damage one takes is echoed onto the
 * other) and cannot get far apart. At awakening every enemy in the area is bound into one chain gang.
 * Passive: enemies held in the user's chains take 20% more damage from the user.
 */
public final class KusariFruit {
    /** A chain between enemies: who is linked to whom, until when, and how much damage echoes. */
    record Link(LivingEntity owner, List<LivingEntity> others, long until, float ratio) {}

    static final Map<LivingEntity, Link> LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean echoing;

    static boolean linked(LivingEntity e) {
        Link l = LINKS.get(e);
        return l != null && l.until > e.level().getGameTime();
    }

    /** Chain a group together (each member echoes to all the others). */
    static void link(LivingEntity owner, List<LivingEntity> group, int ticks, float ratio) {
        long now = owner.level().getGameTime(), until = now + ticks;
        synchronized (LINKS) {
            LINKS.entrySet().removeIf(en -> en.getValue().until <= now || en.getKey().isRemoved());
        }
        for (LivingEntity e : group) {
            List<LivingEntity> others = new ArrayList<>(group);
            others.remove(e);
            LINKS.put(e, new Link(owner, others, until, ratio));
        }
    }

    /** Called after damage lands on any entity: pass part of it along the chain. */
    public static void echo(LivingEntity victim, float amount) {
        if (echoing || amount <= 0.5f) return;
        Link l = LINKS.get(victim);
        if (l == null) return;
        if (l.until <= victim.level().getGameTime() || !l.owner.isAlive()) {
            LINKS.remove(victim);
            return;
        }
        echoing = true;
        try {
            for (LivingEntity o : l.others) {
                if (!o.isAlive() || o.distanceTo(victim) > 24) continue;
                Hits.hit(l.owner, o, amount * l.ratio, FruitKit.info(Element.BLUNT, 0f).withPoise(5).asUnblockable());
                FruitKit.fx(victim, "kusari_link", victim.position().add(0, 1, 0), (float) o.getX(), (float) o.getY() + 1, (float) o.getZ());
            }
        } finally {
            echoing = false;
        }
    }

    public static void init(DevilFruit f) {
        Hits.Info chain = FruitKit.info(Element.BLUNT, 0.8f);

        // Chain whip: a lashing chain along a line; the first enemy struck is yanked a little closer.
        f.add(f.ability("chain_whip").cooldown(20).stamina(9).anim("whip_crack").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.3, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 9 + u.getScale());
            boolean first = true;
            for (LivingEntity e : Hits.line(u, from, dir, len, 0.8)) {
                if (!Hits.hostileTo(u, e)) continue;
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 6), chain.withPoise(25)) && first) PowerKit.pull(e, u.position(), 0.6, 0.1);
                first = false;
            }
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "kusari_chain", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, from, SoundEvents.CHAIN_BREAK, 1f, 1.3f);
            return true;
        }));

        // Chain hook: hook a target and haul it to the user — or, with nobody in reach, hook a wall and pull the
        // user there.
        f.add(f.ability("chain_hook").unlock(5).cooldown(70).stamina(14).anim("chain_swing").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            Vec3 from = c.eye().add(0, -0.3, 0);
            if (t != null) {
                Vec3 was = t.position().add(0, 1, 0);
                Vec3 dir = PowerKit.flatAim(c);
                double reach = Math.max(0.8, PowerKit.rayLength(u, u.position().add(0, 0.5, 0), dir, 1.6 + u.getScale() * 0.5) - 0.3);
                PowerKit.place(t, u.position().add(dir.scale(reach)));
                Hits.hit(u, t, FruitKit.dmg(c, 5), chain.withStun(20).withPoise(40));
                FruitKit.fx(u, "kusari_chain", from, (float) was.x, (float) was.y, (float) was.z);
            } else {
                double len = PowerKit.rayLength(u, from, c.look(), 20);
                if (len >= 20) return false;
                Vec3 end = from.add(c.look().scale(len));
                FruitKit.impulse(u, end.subtract(u.position()).normalize().scale(Math.min(2.2, len * 0.15)).add(0, 0.4, 0), true);
                FruitKit.fx(u, "kusari_chain", from, (float) end.x, (float) end.y, (float) end.z);
            }
            PowerKit.sound(c, from, SoundEvents.CHAIN_PLACE, 1.2f, 0.8f);
            return true;
        }));

        // Shackle Link: chain the target to the nearest other enemy (or to the ground, if alone). Half of all damage
        // one of them takes is echoed onto the other, and the chain will not let them drift apart.
        f.add(f.ability("shackle_link").unlock(15).cooldown(300).stamina(24).anim("throw").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            if (t == null) return false;
            LivingEntity partner = null;
            for (LivingEntity e : PowerKit.foes(u, t.position().add(0, 1, 0), 9)) if (e != t) { partner = e; break; }
            int dur = 200 + c.mastery;
            Hits.hit(u, t, FruitKit.dmg(c, 4), chain.withPoise(20));
            if (partner != null) {
                LivingEntity p = partner;
                link(u, List.of(t, p), dur, 0.5f);
                OriginalKit.pulses(c, 0, 5, dur / 5, n -> {
                    if (!t.isAlive() || !p.isAlive()) return;
                    if (t.distanceTo(p) > 6) {
                        Vec3 mid = t.position().add(p.position()).scale(0.5);
                        PowerKit.pull(t, mid, 0.5, 0.05);
                        PowerKit.pull(p, mid, 0.5, 0.05);
                    }
                    if (n % 4 == 0) FruitKit.fx(u, "kusari_link", t.position().add(0, 1, 0), (float) p.getX(), (float) p.getY() + 1, (float) p.getZ());
                });
            } else {
                Vec3 anchor = t.position();
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur / 2, 2));
                OriginalKit.pulses(c, 0, 5, dur / 10, n -> {
                    if (t.isAlive() && t.position().distanceTo(anchor) > 3) PowerKit.pull(t, anchor, 0.6, 0);
                    if (n % 4 == 0) FruitKit.fx(u, "kusari_link", t.position().add(0, 1, 0), (float) anchor.x, (float) anchor.y, (float) anchor.z);
                });
                LINKS.put(t, new Link(u, List.of(), c.level.getGameTime() + dur, 0f));
            }
            PowerKit.sound(c, t.position(), SoundEvents.CHAIN_PLACE, 1.5f, 0.6f);
            return true;
        }));

        // Chain bind: chains wrap a target from head to toe — it cannot move or act for a moment.
        f.add(f.ability("chain_bind").unlock(20).cooldown(220).stamina(24).anim("chain_spin").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 7), chain.withStun(50 + c.mastery / 4).withPoise(80).asUnblockable());
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            CombatState.of(t).blocking = false;
            if (!linked(t)) LINKS.put(t, new Link(u, List.of(), c.level.getGameTime() + 60, 0f));
            FruitKit.fx(u, "kusari_bind", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.CHAIN_PLACE, 1.5f, 0.5f);
            return true;
        }));

        // Chain cyclone: chains whirl around the user, battering everyone close and reeling them in.
        f.add(f.ability("chain_cyclone").unlock(30).cooldown(160).stamina(30).anim("chain_spin").exec((u, c) -> {
            OriginalKit.pulses(c, 0, 5, 5, n -> {
                Vec3 at = u.position();
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 6 + u.getScale() * 0.5)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 4), chain.withPoise(20));
                    PowerKit.swirl(e, at, 0.4, 0.3, 0.05);
                }
                FruitKit.fx(u, "kusari_cyclone", at.add(0, 1, 0), 6, n, 0);
            });
            PowerKit.sound(c, u.position(), SoundEvents.CHAIN_BREAK, 1.5f, 0.7f);
            return true;
        }));

        // Awakening — Chain Gang: chains burst from the ground and bind every enemy in the area into one gang — all
        // rooted, and a third of any damage one takes is shared out to all the others.
        f.add(f.ability("chain_gang").unlock(55).cooldown(1100).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 at = u.position();
            List<LivingEntity> gang = PowerKit.foes(u, at.add(0, 1, 0), 12);
            for (LivingEntity e : gang) {
                Hits.hit(u, e, FruitKit.dmg(c, 10), chain.withStun(40).withPoise(80).asUnblockable());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 5));
                FruitKit.fx(u, "kusari_bind", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
            }
            if (gang.size() > 1) link(u, gang, 240, 0.34f);
            else for (LivingEntity e : gang) LINKS.put(e, new Link(u, List.of(), c.level.getGameTime() + 240, 0f));
            FruitKit.fx(u, "kusari_cyclone", at.add(0, 1, 0), 12, 0, 1);
            PowerKit.sound(c, at, SoundEvents.CHAIN_BREAK, 2f, 0.4f);
            return true;
        }));

        // Passive: what the chains hold, the chains punish — enemies held by the user's chains take 20% more.
        f.outgoingHook = (u, v, amount, info) -> {
            Link l = LINKS.get(v);
            return l != null && l.owner == u && l.until > u.level().getGameTime() ? amount * 1.2f : amount;
        };
    }

    private KusariFruit() {}
}
