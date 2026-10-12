package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Goro Goro no Mi — lightning Logia. */
public final class GoroFruit {
    public static void init(DevilFruit f) {
        Hits.Info bolt = FruitKit.info(Element.LIGHTNING, 0.6f).withStun(10);
        f.add(f.ability("el_thor").cooldown(70).stamina(22).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 32);
            LightningBolt lb = EntityType.LIGHTNING_BOLT.create(c.level);
            if (lb != null) {
                lb.moveTo(at);
                lb.setVisualOnly(true);
                c.level.addFreshEntity(lb);
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 3, () -> FruitKit.aoe(c, at.add(0, 1, 0), 3.5, FruitKit.dmg(c, 11), bolt.withPoise(50)));
            return true;
        }));
        f.add(f.ability("vari").cooldown(60).stamina(20).anim("cast_ground").exec((u, c) -> {
            FruitKit.aoe(c, u.position().add(0, 1, 0), 5.5, FruitKit.dmg(c, 6), bolt);
            FruitKit.fx(u, "discharge", u.position().add(0, 1, 0), 5.5f, 0, 0);
            return true;
        }));
        f.add(f.ability("sango").unlock(10).cooldown(40).stamina(16).anim("punch_extend").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "lightning_bolt", 0.7f, 3.5, FruitKit.dmg(c, 8), bolt);
            p.pierce = true;
            return true;
        }));
        f.add(f.ability("lightning_step").unlock(15).cooldown(50).stamina(14).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, c.look(), 18);
            FruitKit.fx(u, "lightning_trail", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            return true;
        }));
        f.add(f.ability("raigo").unlock(60).cooldown(800).stamina(80).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 60);
            FruitKit.fx(u, "storm_cloud", at.add(0, 18, 0), 12, 0, 0);
            for (int i = 0; i < 10; i++) {
                long when = c.level.getGameTime() + 40 + i * 4L;
                double ox = (u.getRandom().nextDouble() - 0.5) * 16, oz = (u.getRandom().nextDouble() - 0.5) * 16;
                CombatState.of(u).schedule(when, () -> {
                    Vec3 p = at.add(ox, 0, oz);
                    LightningBolt lb = EntityType.LIGHTNING_BOLT.create(c.level);
                    if (lb != null) {
                        lb.moveTo(p);
                        lb.setVisualOnly(true);
                        c.level.addFreshEntity(lb);
                    }
                    FruitKit.aoe(c, p.add(0, 1, 0), 4, FruitKit.dmg(c, 14), bolt.withPoise(80).asUnblockable());
                });
            }
            return true;
        }));
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.LIGHTNING ? 0 : amount;
    }

    private GoroFruit() {}
}
