package com.grandline.combat.style;

import com.grandline.combat.Ability;
import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.combat.Strike;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.player.Race;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** All fighting styles: 17 canon + 3 original. */
public final class FightingStyles {
    private static final Map<String, FightingStyle> ALL = new LinkedHashMap<>();
    private static boolean booted;

    private static FightingStyle def(String id, boolean canon, Set<WeaponClass> w, @Nullable Race race, Strike heavy, Strike... light) {
        FightingStyle s = new FightingStyle(id, canon, w, race, List.of(light), heavy);
        ALL.put(id, s);
        return s;
    }

    @Nullable
    public static FightingStyle get(String id) {
        bootstrap();
        return ALL.get(id);
    }

    public static Collection<FightingStyle> all() {
        bootstrap();
        return ALL.values();
    }

    private static final Set<WeaponClass> NONE = EnumSet.noneOf(WeaponClass.class);

    // ---- reusable technique shapes ----
    private static Ability.Executor dashStrike(double dist, float dmg, Element e, float kb) {
        return (u, c) -> {
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, c.look().multiply(1, 0.2, 1), dist);
            for (LivingEntity t : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.1))
                Hits.hit(u, t, FruitKit.dmg(c, dmg) * Hits.power(u) / 4f, Hits.Info.of(e, kb).withPoise(40));
            FruitKit.fx(u, "dash_line", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            return true;
        };
    }

    private static Ability.Executor burst(double radius, float dmg, Element e, float kb, int stun) {
        return (u, c) -> {
            FruitKit.aoe(c, u.position().add(0, 1, 0), radius, FruitKit.dmg(c, dmg) * Hits.power(u) / 4f, Hits.Info.of(e, kb).withStun(stun).withPoise(45));
            FruitKit.fx(u, "shockwave", u.position().add(0, 0.2, 0), (float) radius, 0, 0);
            return true;
        };
    }

    private static Ability.Executor coneHit(double range, double arc, float dmg, Element e, float kb, int stun) {
        return (u, c) -> {
            FruitKit.cone(c, range, arc, FruitKit.dmg(c, dmg) * Hits.power(u) / 4f, Hits.Info.of(e, kb).withStun(stun).withPoise(40));
            FruitKit.fx(u, "swing_arc", u.position().add(0, 1, 0), (float) range, (float) arc, 0);
            return true;
        };
    }

    private static Ability.Executor projectile(String kind, float size, double speed, float dmg, Element e, boolean pierce) {
        return (u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, kind, size, speed, FruitKit.dmg(c, dmg) * Hits.power(u) / 4f, Hits.Info.of(e, 0.8f).withPoise(30));
            p.pierce = pierce;
            return true;
        };
    }

    private static Ability.Executor buff(String id, int ticks, MobEffectInstance... effects) {
        return (u, c) -> {
            CombatState.of(u).buffs.put(id, ticks);
            for (MobEffectInstance m : effects) u.addEffect(new MobEffectInstance(m));
            FruitKit.fx(u, "buff_" + id, u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        };
    }

    public static synchronized void bootstrap() {
        if (booted) return;
        booted = true;
        // ---------------- unarmed ----------------
        FightingStyle brawler = def("brawler", true, NONE, null,
                Strike.of(2.2f, 2.8, 70, 1.6f, 8, 14, "heavy_punch").lunge(0.6),
                Strike.of(1.0f, 2.6, 70, 0.3f, 2, 4, "jab_right"), Strike.of(1.0f, 2.6, 70, 0.3f, 2, 4, "jab_left"),
                Strike.of(1.2f, 2.6, 80, 0.5f, 3, 6, "hook_right"), Strike.of(1.5f, 2.8, 80, 1.2f, 4, 9, "uppercut"));
        brawler.abilities.add(brawler.ability("shoulder_charge").cooldown(60).stamina(15).anim("dash").exec(dashStrike(6, 6, Element.BLUNT, 2f)));
        brawler.abilities.add(brawler.ability("ground_pound").unlock(10).cooldown(100).stamina(20).anim("ground_slam").exec(burst(4, 6, Element.BLUNT, 1.2f, 15)));
        brawler.abilities.add(brawler.ability("headbutt").unlock(20).cooldown(80).stamina(12).anim("headbutt").exec(coneHit(2.6, 50, 7, Element.BLUNT, 1f, 40)));

        FightingStyle blackLeg = def("black_leg", true, NONE, null,
                Strike.of(2.6f, 3.2, 90, 2.2f, 8, 15, "kick_heavy").lunge(0.8),
                Strike.of(1.2f, 3.0, 90, 0.4f, 3, 5, "kick_low"), Strike.of(1.2f, 3.0, 90, 0.5f, 3, 5, "kick_mid"),
                Strike.of(1.4f, 3.2, 100, 0.7f, 3, 6, "kick_high"), Strike.of(1.8f, 3.4, 360, 1.2f, 5, 11, "spin_kick"));
        blackLeg.abilities.add(blackLeg.ability("collier_shoot").cooldown(50).stamina(14).anim("kick_high").exec(coneHit(3.2, 50, 8, Element.BLUNT, 1.5f, 30)));
        blackLeg.abilities.add(blackLeg.ability("party_table").unlock(8).cooldown(90).stamina(22).anim("handstand_spin").exec(burst(4, 6, Element.BLUNT, 1.6f, 0)));
        blackLeg.abilities.add(blackLeg.ability("concasse").unlock(15).cooldown(80).stamina(18).anim("axe_kick").exec((u, c) -> {
            FruitKit.impulse(u, new Vec3(c.look().x * 0.6, 0.9, c.look().z * 0.6), true);
            CombatState.of(u).schedule(c.level.getGameTime() + 14, () -> FruitKit.aoe(c, u.position().add(0, 0.5, 0), 3.2, FruitKit.dmg(c, 10) * Hits.power(u) / 4f, Hits.Info.of(Element.BLUNT, 2f).withStun(25)));
            return true;
        }));
        blackLeg.abilities.add(blackLeg.ability("diable_jambe").unlock(40).cooldown(400).stamina(30).anim("spin_kick").exec(buff("diable_jambe", 20 * 25, new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 25, 1, false, false))));
        blackLeg.abilities.add(blackLeg.ability("sky_walk").unlock(60).cooldown(20).stamina(10).anim("air_step").exec((u, c) -> {
            FruitKit.impulse(u, new Vec3(c.look().x * 0.5, 0.75, c.look().z * 0.5), true);
            return true;
        }));

        FightingStyle roku = def("rokushiki", true, NONE, null,
                Strike.of(2.4f, 3.0, 40, 0.6f, 6, 12, "finger_pistol").element(Element.PIERCE),
                Strike.of(1.1f, 2.8, 60, 0.3f, 2, 4, "jab_right"), Strike.of(1.1f, 2.8, 60, 0.3f, 2, 4, "jab_left"),
                Strike.of(1.6f, 3.0, 30, 0.5f, 3, 7, "finger_pistol").element(Element.PIERCE));
        roku.abilities.add(roku.ability("soru").cooldown(30).stamina(10).anim("dash").exec((u, c) -> {
            FruitKit.blink(u, FruitKit.aimDir(c).multiply(1, 0.15, 1), 9);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 4;
            return true;
        }));
        roku.abilities.add(roku.ability("geppo").cooldown(12).stamina(8).anim("air_step").exec((u, c) -> {
            FruitKit.impulse(u, new Vec3(c.look().x * 0.4, 0.7, c.look().z * 0.4), true);
            return true;
        }));
        roku.abilities.add(roku.ability("tekkai").unlock(5).cooldown(160).stamina(20).anim("iron_body").exec(buff("tekkai", 20 * 6,
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 6, 2, false, false), new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 6, 3, false, false))));
        roku.abilities.add(roku.ability("shigan").unlock(10).cooldown(40).stamina(12).anim("finger_pistol").exec(coneHit(3.0, 25, 9, Element.PIERCE, 0.4f, 10)));
        roku.abilities.add(roku.ability("rankyaku").unlock(20).cooldown(60).stamina(16).anim("kick_high").exec(projectile("air_blade", 1.2f, 2.0, 9, Element.SLASH, true)));
        roku.abilities.add(roku.ability("kami_e").unlock(30).cooldown(200).stamina(15).anim("sway").exec(buff("kami_e", 20 * 5)));
        roku.abilities.add(roku.ability("rokuogan").unlock(80).cooldown(600).stamina(50).anim("double_palm").exec((u, c) -> {
            FruitKit.cone(c, 4, 40, FruitKit.dmg(c, 22) * Hits.power(u) / 4f, Hits.Info.of(Element.IMPACT, 3f).asUnblockable().withStun(40));
            FruitKit.fx(u, "shockwave", u.getEyePosition().add(u.getLookAngle().scale(2)), 3, 0, 0);
            return true;
        }));

        FightingStyle fmk = def("fishman_karate", true, NONE, null,
                Strike.of(2.6f, 3.0, 70, 2.0f, 8, 15, "heavy_punch").lunge(0.6),
                Strike.of(1.3f, 2.8, 70, 0.6f, 3, 5, "jab_right"), Strike.of(1.3f, 2.8, 70, 0.6f, 3, 5, "jab_left"),
                Strike.of(1.6f, 3.0, 80, 1.4f, 4, 9, "palm_strike"));
        fmk.abilities.add(fmk.ability("water_heart").cooldown(80).stamina(18).anim("palm_strike").exec((u, c) -> {
            float bonus = u.isInWaterOrRain() ? 1.6f : 1f;
            FruitKit.cone(c, 4.5, 60, FruitKit.dmg(c, 9 * bonus) * Hits.power(u) / 4f, Hits.Info.of(Element.WATER, 2f).withStun(20).asUnblockable());
            FruitKit.fx(u, "water_ripple", u.getEyePosition().add(u.getLookAngle().scale(2)), 2, 0, 0);
            return true;
        }));
        fmk.abilities.add(fmk.ability("shark_tile_fist").unlock(10).cooldown(60).stamina(16).anim("heavy_punch").exec(coneHit(3.2, 60, 10, Element.BLUNT, 2.5f, 15)));
        fmk.abilities.add(fmk.ability("water_shot").unlock(15).cooldown(40).stamina(12).anim("throw").exec(projectile("water_shot", 0.5f, 2.6, 7, Element.WATER, true)));
        fmk.abilities.add(fmk.ability("thousand_brick_fist").unlock(40).cooldown(240).stamina(35).anim("heavy_punch").exec((u, c) -> {
            FruitKit.cone(c, 4, 50, FruitKit.dmg(c, 18) * Hits.power(u) / 4f, Hits.Info.of(Element.IMPACT, 3.5f).asUnblockable().withPoise(100));
            return true;
        }));

        FightingStyle fmj = def("fishman_jujutsu", true, NONE, null,
                Strike.of(2.0f, 3.2, 80, 1.6f, 8, 14, "palm_strike"),
                Strike.of(1.1f, 2.8, 80, 0.5f, 3, 5, "palm_strike"), Strike.of(1.2f, 2.8, 80, 0.6f, 3, 6, "hook_right"));
        fmj.abilities.add(fmj.ability("uchimizu").cooldown(30).stamina(10).anim("throw").exec((u, c) -> {
            for (int i = 0; i < 3; i++) {
                AbilityProjectile p = FruitKit.shoot(c, "water_shot", 0.3f, 2.8, FruitKit.dmg(c, 3) * Hits.power(u) / 4f, Hits.Info.of(Element.WATER, 0.4f));
                p.setDeltaMovement(p.getDeltaMovement().add((i - 1) * 0.15, 0, (i - 1) * 0.15));
            }
            return true;
        }));
        fmj.abilities.add(fmj.ability("water_whip").unlock(10).cooldown(60).stamina(14).anim("whip_crack").exec((u, c) -> {
            FruitKit.line(c, 8, 0.8, FruitKit.dmg(c, 6) * Hits.power(u) / 4f, Hits.Info.of(Element.WATER, 1f).withStun(10));
            return true;
        }));
        fmj.abilities.add(fmj.ability("undertow_grip").unlock(20).cooldown(100).stamina(18).anim("grab").exec((u, c) -> {
            for (LivingEntity t : Hits.cone(u, 8, 40)) {
                FruitKit.impulse(t, u.position().subtract(t.position()).normalize().scale(1.2).add(0, 0.2, 0), true);
                Hits.hit(u, t, FruitKit.dmg(c, 4), Hits.Info.of(Element.WATER, 0).withStun(25));
            }
            return true;
        }));

        FightingStyle okama = def("okama_kenpo", true, NONE, null,
                Strike.of(2.4f, 3.4, 60, 2.0f, 8, 14, "kick_heavy"),
                Strike.of(1.2f, 3.2, 80, 0.5f, 3, 5, "kick_high"), Strike.of(1.2f, 3.2, 80, 0.5f, 3, 5, "kick_mid"),
                Strike.of(1.5f, 3.4, 360, 0.8f, 4, 8, "spin_kick"));
        okama.abilities.add(okama.ability("swan_kick").cooldown(40).stamina(12).anim("kick_high").exec(coneHit(3.6, 40, 8, Element.BLUNT, 1.6f, 0)));
        okama.abilities.add(okama.ability("pirouette").unlock(10).cooldown(90).stamina(18).anim("spin_kick").exec(burst(3.6, 6, Element.BLUNT, 1.4f, 10)));
        okama.abilities.add(okama.ability("ballet_dash").unlock(20).cooldown(60).stamina(14).anim("dash").exec(dashStrike(8, 6, Element.BLUNT, 1.5f)));

        FightingStyle electro = def("electro", true, NONE, Race.MINK,
                Strike.of(2.4f, 3.0, 80, 1.6f, 7, 13, "heavy_punch").element(Element.LIGHTNING),
                Strike.of(1.2f, 2.8, 80, 0.4f, 2, 4, "claw_right").element(Element.LIGHTNING),
                Strike.of(1.2f, 2.8, 80, 0.4f, 2, 4, "claw_left").element(Element.LIGHTNING),
                Strike.of(1.5f, 3.0, 100, 0.8f, 3, 7, "kick_high").element(Element.LIGHTNING));
        electro.abilities.add(electro.ability("electro_claw").cooldown(30).stamina(10).anim("claw_flurry").exec(coneHit(3.2, 80, 7, Element.LIGHTNING, 0.8f, 20)));
        electro.abilities.add(electro.ability("electro_burst").unlock(10).cooldown(90).stamina(20).anim("cast_ground").exec(burst(4.5, 7, Element.LIGHTNING, 1f, 25)));
        electro.abilities.add(electro.ability("sulong").unlock(25).cooldown(1200).stamina(10).anim("roar").exec((u, c) -> {
            long day = c.level.getDayTime() % 24000L;
            boolean night = day > 13000 && day < 23000;
            boolean full = c.level.getMoonPhase() == 0;
            if (!(night && full && c.level.canSeeSky(u.blockPosition()))) {
                if (u instanceof net.minecraft.server.level.ServerPlayer sp) sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("grandline.sulong.need_moon"), true);
                return false;
            }
            CombatState.of(u).buffs.put("sulong", 20 * 60);
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 2));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 2));
            u.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 60, 2));
            FruitKit.fx(u, "sulong", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));

        FightingStyle ryuo = def("ryuo", true, NONE, null,
                Strike.of(2.8f, 3.0, 60, 2.4f, 8, 15, "palm_strike"),
                Strike.of(1.3f, 2.8, 60, 0.6f, 3, 5, "palm_strike"), Strike.of(1.4f, 2.8, 60, 0.8f, 3, 6, "palm_strike"));
        ryuo.abilities.add(ryuo.ability("flowing_palm").cooldown(60).stamina(16).haki(10).anim("palm_strike").exec((u, c) -> {
            CombatState.of(u).buffs.put("internal_destruction", 6);
            FruitKit.cone(c, 3.4, 50, FruitKit.dmg(c, 10) * Hits.power(u) / 4f, Hits.Info.of(Element.IMPACT, 2.2f).imbue(true).withStun(20));
            return true;
        }));
        ryuo.abilities.add(ryuo.ability("dragon_flow_burst").unlock(30).cooldown(160).stamina(25).haki(20).anim("double_palm").exec((u, c) -> {
            FruitKit.aoe(c, u.position().add(0, 1, 0), 4.5, FruitKit.dmg(c, 10) * Hits.power(u) / 4f, Hits.Info.of(Element.IMPACT, 2.8f).imbue(true).withPoise(70));
            FruitKit.fx(u, "ryuo_burst", u.position().add(0, 1, 0), 4.5f, 0, 0);
            return true;
        }));

        FightingStyle hasshoken = def("hasshoken", true, NONE, null,
                Strike.of(2.6f, 3.0, 70, 2.2f, 8, 15, "heavy_punch"),
                Strike.of(1.2f, 2.8, 70, 0.6f, 3, 5, "jab_right"), Strike.of(1.4f, 2.8, 70, 1.0f, 3, 6, "heavy_punch"));
        hasshoken.abilities.add(hasshoken.ability("vibration_strike").cooldown(50).stamina(15).anim("heavy_punch").exec((u, c) -> {
            FruitKit.cone(c, 3.2, 50, FruitKit.dmg(c, 9) * Hits.power(u) / 4f, Hits.Info.of(Element.IMPACT, 1.5f).asUnblockable().withPoise(50));
            return true;
        }));
        hasshoken.abilities.add(hasshoken.ability("quake_palm").unlock(20).cooldown(150).stamina(25).anim("ground_slam").exec(burst(5, 8, Element.IMPACT, 2f, 25)));

        FightingStyle life = def("life_return", true, NONE, null,
                Strike.of(2.0f, 3.0, 70, 1.4f, 8, 14, "heavy_punch"),
                Strike.of(1.1f, 2.8, 70, 0.4f, 3, 5, "jab_right"), Strike.of(1.1f, 2.8, 70, 0.4f, 3, 5, "jab_left"));
        life.abilities.add(life.ability("vital_restore").cooldown(400).stamina(40).anim("focus").exec((u, c) -> {
            u.heal(6 + c.mastery / 10f);
            u.removeEffect(MobEffects.POISON);
            FruitKit.fx(u, "heal", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));
        life.abilities.add(life.ability("iron_hair_guard").unlock(15).cooldown(200).stamina(20).anim("iron_body").exec(buff("iron_hair", 20 * 8, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 8, 1, false, false))));

        // ---------------- swords ----------------
        Set<WeaponClass> swords = FightingStyle.swords();
        FightingStyle itto = def("ittoryu", true, swords, null,
                Strike.of(2.6f, 3.8, 120, 1.2f, 8, 15, "heavy_overhead"),
                Strike.of(1.2f, 3.4, 110, 0.3f, 3, 6, "slash_right").element(Element.SLASH),
                Strike.of(1.2f, 3.4, 110, 0.3f, 3, 6, "slash_left").element(Element.SLASH),
                Strike.of(1.7f, 3.8, 50, 0.8f, 4, 10, "thrust").element(Element.PIERCE).lunge(0.7));
        itto.abilities.add(itto.ability("lion_strike").cooldown(60).stamina(16).anim("iai_dash").exec(dashStrike(7, 9, Element.SLASH, 0.8f)));
        itto.abilities.add(itto.ability("flying_slash").unlock(10).cooldown(45).stamina(14).anim("slash_right").exec(projectile("air_blade", 1.0f, 2.2, 8, Element.SLASH, true)));
        itto.abilities.add(itto.ability("tornado_slash").unlock(25).cooldown(120).stamina(24).anim("spin_slash").exec(burst(4.2, 8, Element.SLASH, 1.4f, 0)));

        FightingStyle nito = def("nitoryu", true, EnumSet.of(WeaponClass.KATANA, WeaponClass.CUTLASS, WeaponClass.DUAL_BLADES), null,
                Strike.of(2.4f, 3.6, 120, 1.4f, 8, 14, "dual_x_cut").lunge(0.8),
                Strike.of(1.0f, 3.2, 110, 0.3f, 2, 5, "dual_slash_right").element(Element.SLASH),
                Strike.of(1.0f, 3.2, 110, 0.3f, 2, 5, "dual_slash_left").element(Element.SLASH),
                Strike.of(1.4f, 3.4, 130, 0.6f, 3, 8, "dual_cross").element(Element.SLASH));
        nito.abilities.add(nito.ability("rhino_charge").cooldown(60).stamina(18).anim("dual_dash").exec(dashStrike(8, 10, Element.SLASH, 1.2f)));
        nito.abilities.add(nito.ability("twin_flying_slash").unlock(12).cooldown(60).stamina(18).anim("dual_cross").exec((u, c) -> {
            for (int i = -1; i <= 1; i += 2) {
                AbilityProjectile p = FruitKit.shoot(c, "air_blade", 0.9f, 2.2, FruitKit.dmg(c, 6) * Hits.power(u) / 4f, Hits.Info.of(Element.SLASH, 0.6f));
                p.setDeltaMovement(p.getDeltaMovement().add(-c.look().z * 0.12 * i, 0, c.look().x * 0.12 * i));
                p.pierce = true;
            }
            return true;
        }));

        FightingStyle santo = def("santoryu", true, EnumSet.of(WeaponClass.KATANA), null,
                Strike.of(2.8f, 3.8, 140, 1.6f, 9, 16, "triple_cut").lunge(0.8),
                Strike.of(1.1f, 3.4, 120, 0.4f, 3, 5, "dual_slash_right").element(Element.SLASH),
                Strike.of(1.1f, 3.4, 120, 0.4f, 3, 5, "dual_slash_left").element(Element.SLASH),
                Strike.of(1.6f, 3.6, 140, 0.8f, 4, 9, "triple_cut").element(Element.SLASH));
        santo.abilities.add(santo.ability("oni_giri").cooldown(60).stamina(18).anim("triple_dash").exec(dashStrike(8, 12, Element.SLASH, 1.0f)));
        santo.abilities.add(santo.ability("tatsumaki").unlock(15).cooldown(120).stamina(26).anim("spin_slash").exec((u, c) -> {
            FruitKit.aoe(c, u.position().add(0, 1, 0), 5, FruitKit.dmg(c, 9) * Hits.power(u) / 4f, Hits.Info.of(Element.SLASH, 0.5f).withPoise(50));
            for (LivingEntity e : Hits.sphere(u, u.position(), 5)) if (Hits.hostileTo(u, e)) FruitKit.impulse(e, new Vec3(0, 1.0, 0), false);
            FruitKit.fx(u, "tornado", u.position(), 5, 0, 0);
            return true;
        }));
        santo.abilities.add(santo.ability("pound_cannon").unlock(30).cooldown(90).stamina(24).anim("triple_cut").exec(projectile("air_blade_big", 1.8f, 2.0, 14, Element.SLASH, true)));
        santo.abilities.add(santo.ability("asura").unlock(80).cooldown(900).stamina(60).haki(30).anim("roar").exec(buff("asura", 20 * 20,
                new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 20, 3, false, false), new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 20, 1, false, false))));

        FightingStyle iaido = def("iaido", true, EnumSet.of(WeaponClass.KATANA, WeaponClass.NODACHI), null,
                Strike.of(3.2f, 4.2, 120, 1.0f, 12, 18, "iai_slash"),
                Strike.of(1.4f, 3.6, 100, 0.4f, 4, 8, "iai_slash").element(Element.SLASH),
                Strike.of(1.4f, 3.6, 100, 0.4f, 4, 8, "slash_left").element(Element.SLASH));
        iaido.abilities.add(iaido.ability("quick_draw").cooldown(40).stamina(12).anim("iai_slash").exec(coneHit(4.6, 100, 11, Element.SLASH, 0.6f, 10)));
        iaido.abilities.add(iaido.ability("sheathed_counter").unlock(10).cooldown(100).stamina(10).anim("iai_stance").exec((u, c) -> {
            CombatState s = CombatState.of(u);
            s.blocking = true;
            s.blockStart = c.level.getGameTime() + 6; // extended parry window
            s.schedule(c.level.getGameTime() + 14, () -> s.blocking = false);
            return true;
        }));
        iaido.abilities.add(iaido.ability("moon_cut").unlock(35).cooldown(160).stamina(28).anim("iai_slash").exec(projectile("crescent", 2.0f, 2.6, 15, Element.SLASH, true)));

        // ---------------- ranged ----------------
        FightingStyle gun = def("gunslinger", true, EnumSet.of(WeaponClass.FLINTLOCK), null,
                Strike.of(1.2f, 2.6, 70, 0.8f, 4, 8, "pistol_whip"), Strike.of(1.0f, 2.6, 70, 0.5f, 3, 6, "pistol_whip"));
        gun.abilities.add(gun.ability("fan_the_hammer").cooldown(80).stamina(16).anim("rapid_fire").exec((u, c) -> {
            for (int i = 0; i < 5; i++) CombatState.of(u).schedule(c.level.getGameTime() + i * 3L, () -> FruitKit.shoot(c, "bullet", 0.15f, 3.5, Hits.power(u) * 1.2f, Hits.Info.of(Element.PIERCE, 0.3f)));
            return true;
        }));
        gun.abilities.add(gun.ability("trick_shot").unlock(15).cooldown(60).stamina(12).anim("aim").exec(projectile("bullet", 0.18f, 4.5, 10, Element.PIERCE, true)));
        gun.abilities.add(gun.ability("dodge_shot").unlock(25).cooldown(70).stamina(16).anim("dodge_back").exec((u, c) -> {
            FruitKit.impulse(u, c.look().multiply(-1.2, 0, -1.2).add(0, 0.4, 0), true);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 8;
            FruitKit.shoot(c, "bullet", 0.15f, 3.8, Hits.power(u) * 1.8f, Hits.Info.of(Element.PIERCE, 0.4f));
            return true;
        }));

        FightingStyle sniper = def("sniper", true, EnumSet.of(WeaponClass.SLINGSHOT, WeaponClass.RIFLE, WeaponClass.BOW), null,
                Strike.of(1.0f, 2.6, 70, 0.5f, 4, 8, "jab_right"), Strike.of(0.8f, 2.6, 70, 0.3f, 3, 6, "jab_right"));
        sniper.abilities.add(sniper.ability("lead_star").cooldown(30).stamina(8).anim("aim").exec(projectile("pellet", 0.2f, 3.2, 7, Element.IMPACT, false)));
        sniper.abilities.add(sniper.ability("fire_bird_star").unlock(15).cooldown(100).stamina(18).anim("aim").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "fire_bird", 0.9f, 2.0, FruitKit.dmg(c, 10) * Hits.power(u) / 4f, Hits.Info.of(Element.FIRE, 1f));
            p.explosion = 2.5f;
            return true;
        }));
        sniper.abilities.add(sniper.ability("pop_green_thicket").unlock(25).cooldown(160).stamina(20).anim("throw").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            for (LivingEntity e : Hits.sphere(u, at, 4)) {
                if (!Hits.hostileTo(u, e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
                Hits.hit(u, e, FruitKit.dmg(c, 3), Hits.Info.of(Element.PIERCE, 0).withStun(40));
            }
            FruitKit.fx(u, "vines", at, 4, 0, 0);
            return true;
        }));

        // ---------------- ORIGINAL styles ----------------
        FightingStyle tide = def("tide_grapple", false, NONE, null,
                Strike.of(2.4f, 2.8, 70, 2.6f, 9, 16, "slam"),
                Strike.of(1.1f, 2.6, 70, 0.4f, 3, 5, "palm_strike"), Strike.of(1.3f, 2.6, 70, 0.6f, 3, 6, "grab"));
        tide.abilities.add(tide.ability("riptide_throw").cooldown(70).stamina(18).anim("throw_grab").exec((u, c) -> {
            LivingEntity t = Hits.cone(u, 3, 60).stream().findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 7) * Hits.power(u) / 4f, Hits.Info.of(Element.BLUNT, 0).withStun(25));
            FruitKit.impulse(t, c.look().scale(-1.4).add(0, 0.8, 0), true);
            return true;
        }));
        tide.abilities.add(tide.ability("undertow").unlock(10).cooldown(100).stamina(18).anim("grab").exec((u, c) -> {
            for (LivingEntity t : Hits.sphere(u, u.position(), 6)) {
                if (!Hits.hostileTo(u, t)) continue;
                FruitKit.impulse(t, u.position().subtract(t.position()).normalize().scale(0.9), true);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
            }
            return true;
        }));
        tide.abilities.add(tide.ability("breaking_wave").unlock(25).cooldown(150).stamina(26).anim("ground_slam").exec(burst(5, 10, Element.WATER, 2.4f, 20)));

        FightingStyle storm = def("storm_dance", false, EnumSet.of(WeaponClass.SPEAR, WeaponClass.NAGINATA, WeaponClass.TRIDENT_POLE), null,
                Strike.of(2.6f, 5.0, 40, 1.6f, 9, 16, "heavy_thrust").lunge(1.2),
                Strike.of(1.1f, 4.6, 140, 0.5f, 3, 6, "polearm_sweep"), Strike.of(1.1f, 4.6, 140, 0.5f, 3, 6, "polearm_sweep_back"),
                Strike.of(1.5f, 4.8, 360, 1.0f, 5, 10, "polearm_spin"));
        storm.abilities.add(storm.ability("gale_lunge").cooldown(50).stamina(15).anim("heavy_thrust").exec(dashStrike(9, 9, Element.PIERCE, 1.2f)));
        storm.abilities.add(storm.ability("whirl_dance").unlock(10).cooldown(100).stamina(22).anim("polearm_spin").exec(burst(5.2, 8, Element.WIND, 1.8f, 0)));
        storm.abilities.add(storm.ability("lightning_rod_thrust").unlock(30).cooldown(200).stamina(30).anim("spear_thrust").exec((u, c) -> {
            FruitKit.line(c, 10, 1.0, FruitKit.dmg(c, 13) * Hits.power(u) / 4f, Hits.Info.of(Element.LIGHTNING, 1.4f).withStun(30));
            FruitKit.fx(u, "lightning_trail", c.eye(), (float) (c.eye().x + c.look().x * 10), (float) (c.eye().y + c.look().y * 10), (float) (c.eye().z + c.look().z * 10));
            return true;
        }));

        FightingStyle anchor = def("anchor_brawl", false, EnumSet.of(WeaponClass.ANCHOR_CHAIN, WeaponClass.GAUNTLETS), null,
                Strike.of(3.0f, 5.5, 60, 2.8f, 12, 22, "anchor_throw"),
                Strike.of(1.4f, 4.6, 120, 1.2f, 6, 11, "chain_swing"), Strike.of(1.6f, 4.6, 360, 1.4f, 7, 12, "chain_spin"));
        anchor.abilities.add(anchor.ability("anchor_drop").cooldown(80).stamina(20).anim("ground_slam").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 8);
            FruitKit.aoe(c, at, 3.5, FruitKit.dmg(c, 12) * Hits.power(u) / 4f, Hits.Info.of(Element.BLUNT, 2.4f).withStun(25));
            FruitKit.fx(u, "shockwave", at, 3.5f, 0, 0);
            return true;
        }));
        anchor.abilities.add(anchor.ability("chain_sweep").unlock(10).cooldown(90).stamina(20).anim("chain_spin").exec(burst(6, 7, Element.BLUNT, 2.2f, 0)));
        anchor.abilities.add(anchor.ability("harbor_pull").unlock(20).cooldown(80).stamina(14).anim("chain_swing").exec((u, c) -> {
            LivingEntity t = Hits.cone(u, 10, 30).stream().findFirst().orElse(null);
            if (t == null) return false;
            FruitKit.impulse(t, u.position().subtract(t.position()).normalize().scale(1.5).add(0, 0.3, 0), true);
            Hits.hit(u, t, FruitKit.dmg(c, 4), Hits.Info.of(Element.BLUNT, 0).withStun(20));
            return true;
        }));
    }

    private FightingStyles() {}
}
