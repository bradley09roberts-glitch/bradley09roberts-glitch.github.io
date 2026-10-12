package com.grandline.combat.weapon;

import com.grandline.combat.Ability;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.impl.FruitKit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Weapon techniques unlocked by weapon mastery (two to three per weapon family). */
public final class WeaponArts {
    private static boolean booted;

    private static Ability.Builder art(WeaponClass wc, String name) {
        return Ability.builder("weapon/" + wc.id() + "/" + name, Ability.Source.WEAPON, wc.id());
    }

    private static float d(LivingEntity u, float mult) {
        return Hits.power(u) * mult;
    }

    public static synchronized void bootstrap() {
        if (booted) return;
        booted = true;
        for (WeaponClass wc : WeaponClass.values()) {
            switch (wc) {
                case KATANA, CUTLASS, RAPIER, NODACHI, DUAL_BLADES -> {
                    art(wc, "rising_slash").cooldown(50).stamina(14).anim("rising_slash").exec((u, c) -> {
                        for (LivingEntity t : Hits.cone(u, 3.6, 90)) {
                            if (Hits.hit(u, t, d(u, 1.6f) * c.masteryScale(), Hits.Info.of(Element.SLASH, 0.2f).withStun(15)) == Hits.Result.HIT)
                                FruitKit.impulse(t, new Vec3(0, 0.9, 0), false);
                        }
                        return true;
                    });
                    art(wc, "piercing_dash").unlock(10).cooldown(70).stamina(16).anim("thrust").exec((u, c) -> {
                        Vec3 s = u.position();
                        Vec3 e = FruitKit.blink(u, c.look().multiply(1, 0.2, 1), 7);
                        for (LivingEntity t : Hits.line(u, s.add(0, 1, 0), e.subtract(s), s.distanceTo(e) + 1.5, 1))
                            Hits.hit(u, t, d(u, 2f) * c.masteryScale(), Hits.Info.of(Element.PIERCE, 1f).withPoise(40));
                        return true;
                    });
                    art(wc, "air_slash").unlock(25).cooldown(70).stamina(16).anim("slash_right").exec((u, c) -> {
                        AbilityProjectile p = FruitKit.shoot(c, "air_blade", 0.9f, 2.2, d(u, 1.8f) * c.masteryScale(), Hits.Info.of(Element.SLASH, 0.5f));
                        p.pierce = true;
                        return true;
                    });
                }
                case SPEAR, NAGINATA, TRIDENT_POLE -> {
                    art(wc, "sweeping_arc").cooldown(60).stamina(16).anim("polearm_spin").exec((u, c) -> {
                        FruitKit.aoe(c, u.position().add(0, 1, 0), 4.6, d(u, 1.5f) * c.masteryScale(), Hits.Info.of(wc.element, 1.4f));
                        return true;
                    });
                    art(wc, "pole_vault").unlock(10).cooldown(60).stamina(12).anim("vault").exec((u, c) -> {
                        FruitKit.impulse(u, c.look().multiply(1.3, 0, 1.3).add(0, 0.9, 0), true);
                        return true;
                    });
                    art(wc, "javelin").unlock(20).cooldown(90).stamina(14).anim("throw").exec((u, c) -> {
                        AbilityProjectile p = FruitKit.shoot(c, "spear", 0.5f, 2.4, d(u, 2.4f) * c.masteryScale(), Hits.Info.of(Element.PIERCE, 1.2f));
                        p.gravity = 0.03;
                        return true;
                    });
                }
                case BO_STAFF, KANABO, HAMMER, TONFA, GAUNTLETS, ANCHOR_CHAIN -> {
                    art(wc, "earthshaker").cooldown(80).stamina(18).anim("ground_slam").exec((u, c) -> {
                        FruitKit.aoe(c, u.position().add(0, 0.5, 0), 4, d(u, 1.7f) * c.masteryScale(), Hits.Info.of(Element.BLUNT, 1.8f).withStun(20));
                        FruitKit.fx(u, "shockwave", u.position(), 4, 0, 0);
                        return true;
                    });
                    art(wc, "guard_crusher").unlock(10).cooldown(70).stamina(16).anim("heavy_overhead").exec((u, c) -> {
                        FruitKit.cone(c, 3.4, 60, d(u, 2.2f) * c.masteryScale(), Hits.Info.of(Element.BLUNT, 2f).asUnblockable().withPoise(80));
                        return true;
                    });
                }
                case KNIFE, WHIP, JITTE -> {
                    art(wc, "lash").cooldown(40).stamina(10).anim("whip_crack").exec((u, c) -> {
                        FruitKit.line(c, wc == WeaponClass.WHIP ? 8 : 4, 0.6, d(u, 1.4f) * c.masteryScale(), Hits.Info.of(wc.element, 0.4f).withStun(10));
                        return true;
                    });
                    if (wc == WeaponClass.JITTE) art(wc, "seastone_bind").unlock(10).cooldown(120).stamina(14).anim("thrust").exec((u, c) -> {
                        LivingEntity t = Hits.cone(u, 3, 50).stream().findFirst().orElse(null);
                        if (t == null) return false;
                        Hits.hit(u, t, d(u, 1.2f), Hits.Info.of(Element.BLUNT, 0.2f).asSeastone().withStun(30));
                        com.grandline.fruit.FruitRules.applySeastoneContact(t, 120);
                        return true;
                    });
                    else art(wc, "bleeding_cuts").unlock(10).cooldown(90).stamina(14).anim("slash_left").exec((u, c) -> {
                        for (LivingEntity t : Hits.cone(u, 3, 90)) {
                            if (Hits.hit(u, t, d(u, 1.0f), Hits.Info.of(Element.SLASH, 0.1f)) == Hits.Result.HIT)
                                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                        }
                        return true;
                    });
                }
                case FLINTLOCK, RIFLE, BOW, SLINGSHOT, THROWING_KNIVES -> {
                    art(wc, "aimed_shot").cooldown(40).stamina(8).anim("aim").exec((u, c) -> {
                        AbilityProjectile p = FruitKit.shoot(c, wc == WeaponClass.BOW ? "arrow" : wc == WeaponClass.SLINGSHOT ? "pellet" : wc == WeaponClass.THROWING_KNIVES ? "knife" : "bullet",
                                0.18f, 4.0, d(u, 3f) * c.masteryScale(), Hits.Info.of(Element.PIERCE, 0.6f).withPoise(30));
                        p.pierce = wc == WeaponClass.RIFLE;
                        return true;
                    });
                    art(wc, "volley").unlock(15).cooldown(100).stamina(16).anim("rapid_fire").exec((u, c) -> {
                        for (int i = -2; i <= 2; i++) {
                            AbilityProjectile p = FruitKit.shoot(c, wc == WeaponClass.BOW ? "arrow" : "bullet", 0.15f, 3.0, d(u, 1.2f) * c.masteryScale(), Hits.Info.of(Element.PIERCE, 0.3f));
                            p.setDeltaMovement(p.getDeltaMovement().add(-c.look().z * 0.1 * i, 0, c.look().x * 0.1 * i));
                        }
                        return true;
                    });
                }
                case BAZOOKA, HAND_CANNON -> art(wc, "explosive_shell").cooldown(120).stamina(18).anim("aim").exec((u, c) -> {
                    AbilityProjectile p = FruitKit.shoot(c, "cannonball", 0.45f, 2.0, d(u, 3f) * c.masteryScale(), Hits.Info.of(Element.IMPACT, 2f));
                    p.explosion = 3f;
                    p.gravity = 0.02;
                    p.breaksBlocks = true;
                    return true;
                });
                case WEATHER_STAFF -> {
                    art(wc, "thunder_tempo").cooldown(100).stamina(18).anim("cast_up").exec((u, c) -> {
                        Vec3 at = FruitKit.aimPoint(c, 24);
                        var lb = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(c.level);
                        if (lb != null) {
                            lb.moveTo(at);
                            lb.setVisualOnly(true);
                            c.level.addFreshEntity(lb);
                        }
                        FruitKit.aoe(c, at.add(0, 1, 0), 3, d(u, 2.5f) * c.masteryScale(), Hits.Info.of(Element.LIGHTNING, 0.6f).withStun(20));
                        return true;
                    });
                    art(wc, "mirage_tempo").unlock(10).cooldown(200).stamina(14).anim("cast_forward").exec((u, c) -> {
                        u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0));
                        CombatState.of(u).buffs.put("kami_e", 100);
                        FruitKit.fx(u, "mirage", u.position().add(0, 1, 0), 0, 0, 0);
                        return true;
                    });
                }
                case DIAL -> art(wc, "impact_release").cooldown(160).stamina(20).anim("palm_strike").exec((u, c) -> {
                    FruitKit.cone(c, 3, 50, d(u, 3.5f) * c.masteryScale(), Hits.Info.of(Element.IMPACT, 3f).asUnblockable());
                    u.hurt(u.damageSources().generic(), 2f); // recoil, as in the source
                    return true;
                });
            }
        }
    }

    private WeaponArts() {}
}
