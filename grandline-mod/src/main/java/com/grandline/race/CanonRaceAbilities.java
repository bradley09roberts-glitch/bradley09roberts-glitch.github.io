package com.grandline.race;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.impl.CanonKit;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.fruit.impl.PowerKit;
import com.grandline.npc.GLNpc;
import com.grandline.player.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import static com.grandline.race.RaceKit.*;

/**
 * Active abilities of the 13 canon races. Every technique trains the race mastery ("race/&lt;race&gt;") and unlocks
 * with it; NPCs of the race use them through {@code HumanoidNpc.techniques()}.
 */
final class CanonRaceAbilities {

    static void register() {
        human();
        fishman();
        merfolk();
        mink();
        skyPeople();
        longArm();
        longLeg();
        threeEye();
        tontatta();
        giant();
        lunarian();
        buccaneer();
        snakeneck();
    }

    // ================================================================== shared shapes

    /** Blink along {@code dir} and hit everything on the path. */
    static Vec3 dashHit(LivingEntity u, AbilityContext c, Vec3 dir, double dist, float dmg, Hits.Info info, String fxKind) {
        Vec3 start = u.position();
        Vec3 end = FruitKit.blink(u, dir, dist);
        for (LivingEntity t : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.1)) Hits.hit(u, t, dmg, info);
        fx(u, fxKind, start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
        return end;
    }

    static boolean noTarget(LivingEntity u) {
        tell(u, "grandline.race.no_target");
        return false;
    }

    /** Living things on the user's side (self, crewmates, the user's crew NPCs, same-faction NPCs). */
    static boolean ally(LivingEntity u, LivingEntity e) {
        if (e == u) return true;
        if (!e.isAlive()) return false;
        if (u instanceof Player pu && e instanceof Player pe) {
            var a = com.grandline.player.Characters.get(pu).crewId;
            return a != null && a.equals(com.grandline.player.Characters.get(pe).crewId);
        }
        if (e instanceof GLNpc n && com.grandline.npc.crew.CrewManager.allied(n, u)) return true;
        if (u instanceof GLNpc n && com.grandline.npc.crew.CrewManager.allied(n, e)) return true;
        if (u instanceof GLNpc nu && e instanceof GLNpc ne) return nu.faction() == ne.faction() && !nu.isHostileTo(ne) && !ne.isHostileTo(nu);
        return false;
    }

    static java.util.List<LivingEntity> allies(LivingEntity u, double r) {
        return u.level().getEntitiesOfClass(LivingEntity.class, u.getBoundingBox().inflate(r), e -> ally(u, e));
    }

    // ================================================================== Human

    private static void human() {
        Race R = Race.HUMAN;
        // Second Wind: grit alone — catch your breath mid-fight.
        ability(R, "second_wind").cooldown(600).stamina(0).anim("focus").exec((u, c) -> {
            if (c.data != null) c.data.stamina = Math.min(c.data.maxStamina(), c.data.stamina + c.data.maxStamina() * 0.35f);
            u.heal(2f + c.mastery / 20f);
            u.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            u.removeEffect(MobEffects.WEAKNESS);
            CombatState.of(u).poise = 100;
            fx(u, "race_second_wind", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        });
        // Rallying Cry: a captain's shout that steadies the whole crew.
        ability(R, "rallying_cry").unlock(10).cooldown(900).stamina(15).anim("cheer").exec((u, c) -> {
            int amp = c.mastery >= 60 ? 1 : 0;
            for (LivingEntity a : allies(u, 10)) {
                eff(a, MobEffects.DAMAGE_BOOST, 200, amp);
                eff(a, MobEffects.MOVEMENT_SPEED, 200, 0);
                fx(u, "race_rally", a.position().add(0, a.getBbHeight() + 0.3, 0), 0, 0, 0);
            }
            PowerKit.sound(c, u.position(), SoundEvents.RAID_HORN.value(), 0.6f, 1.4f);
            return true;
        });
        // Dirty Trick: sand in the eyes and a boot to the shin — humans fight with whatever works.
        ability(R, "dirty_trick").unlock(25).cooldown(200).stamina(12).anim("throw").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 4.5, 70)) {
                if (Hits.hit(u, e, dmg(c, 2.5f), info(Element.PHYSICAL, 0.3f).withPoise(45)) != Hits.Result.HIT) continue;
                eff(e, MobEffects.BLINDNESS, 50, 0);
                eff(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
                PowerKit.loseTrack(u, e);
            }
            fx(u, "race_sand_throw", c.eye().add(c.look().scale(1.5)), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            return true;
        });
        // Last Stand: for eight seconds nothing short of a finishing blow can drop you.
        ability(R, "last_stand").unlock(45).cooldown(2400).stamina(0).anim("roar").exec((u, c) -> {
            boolean low = CanonKit.hp(u) < 0.4f;
            buff(u, "race_last_stand", 160);
            eff(u, MobEffects.DAMAGE_BOOST, 160, low ? 1 : 0);
            eff(u, MobEffects.DAMAGE_RESISTANCE, 160, 0);
            fx(u, "race_last_stand", u.position().add(0, 1, 0), 0, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PLAYER_LEVELUP, 0.8f, 0.6f);
            return true;
        });
    }

    // ================================================================== Fish-Man

    private static void fishman() {
        Race R = Race.FISHMAN;
        // Riptide Dash: a torpedo through the water (any direction when submerged), a shorter lunge on land.
        ability(R, "riptide_dash").cooldown(80).stamina(14).anim("dash").exec((u, c) -> {
            boolean sub = u.isInWater();
            Vec3 dir = sub ? FruitKit.aimDir(c) : c.look().multiply(1, 0.15, 1);
            dashHit(u, c, dir, sub ? 13 : 6, dmg(c, sub ? 8 : 5), info(Element.BLUNT, 1.8f).withPoise(40), "race_riptide");
            return true;
        });
        // Droplet Volley: flicked drops of water that hit like bullets. Five near water, three drawn from the body.
        ability(R, "droplet_volley").unlock(8).cooldown(50).stamina(12).anim("throw").exec((u, c) -> {
            boolean wet = waterNear(u, 3);
            int n = wet ? 5 : 3;
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = 0; i < n; i++) {
                Vec3 d = PowerKit.yaw(dir, (i - (n - 1) / 2.0) * 5);
                AbilityProjectile p = AbilityProjectile.shoot(u, "water_shot", 0.22f, c.eye().add(d.scale(0.6)).add(0, -0.15, 0), d, 3.2,
                        dmg(c, wet ? 4f : 3f), info(Element.WATER, 0.5f).withPoise(15));
                p.pierce = true;
                p.life = 25;
            }
            if (!wet && c.data != null) c.data.masteryXp.merge("race/dryness", 20, Integer::sum); // costs the body's moisture
            return true;
        });
        // Buraikan: hurl a mass of water that bursts over everything around the target.
        ability(R, "buraikan").unlock(22).cooldown(200).stamina(26).anim("double_palm").exec((u, c) -> {
            boolean wet = waterNear(u, 4);
            float d = dmg(c, wet ? 11 : 7);
            Hits.Info inf = info(Element.WATER, 2.6f).withPoise(70);
            AbilityProjectile p = FruitKit.shoot(c, "water_shot", wet ? 1.4f : 0.9f, 1.7, d, inf);
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, wet ? 3.5 : 2.5)) {
                    if (e == hit) continue;
                    if (PowerKit.hit(u, e, d * 0.6f, inf)) PowerKit.push(e, pos, 0.9, 0.3);
                }
                fx(u, "race_wave", pos, wet ? 3.5f : 2.5f, 0, 0);
            };
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_SPLASH, 1.2f, 0.7f);
            return true;
        });
        // Water Heart: Inner Ripple — a palm that strikes the water inside the body. Goes through guards, and since it
        // moves the water within, a Logia body cannot let it pass.
        ability(R, "inner_ripple").unlock(45).cooldown(260).stamina(24).anim("palm_strike").exec((u, c) -> {
            float bonus = wet(u) ? 1.4f : 1f;
            for (LivingEntity e : Hits.cone(u, 4.2, 50)) {
                if (Hits.hit(u, e, dmg(c, 10 * bonus), info(Element.WATER, 2f).withStun(30).withPoise(80).asUnblockable().imbue(true)) == Hits.Result.HIT)
                    eff(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            }
            fx(u, "water_ripple", c.eye().add(c.look().scale(2)), 2.5f, 0, 0);
            return true;
        });
    }

    // ================================================================== Merfolk

    static boolean seaCreature(Mob m) {
        MobCategory cat = m.getType().getCategory();
        if (cat == MobCategory.WATER_CREATURE || cat == MobCategory.WATER_AMBIENT || cat == MobCategory.UNDERGROUND_WATER_CREATURE || cat == MobCategory.AXOLOTLS) return true;
        if (m instanceof Guardian || m instanceof Drowned) return true;
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath();
        if (path.contains("sea_king") || path.contains("seaking")) return true;
        return m instanceof GLNpc n && n.role().contains("sea_king");
    }

    private static void merfolk() {
        Race R = Race.MERFOLK;
        // Tail Whirl: in the water a whirlpool that drags foes in; on land a heavy tail sweep.
        ability(R, "tail_whirl").cooldown(60).stamina(12).anim("spin_kick").exec((u, c) -> {
            boolean water = u.isInWater();
            double r = water ? 6 : 3.5;
            Vec3 center = u.position();
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 0.5, 0), r)) {
                if (PowerKit.hit(u, e, dmg(c, water ? 6 : 4), info(water ? Element.WATER : Element.BLUNT, water ? 0.4f : 1.4f).withPoise(30)) && water)
                    PowerKit.swirl(e, center, 0.5, 0.6, 0.05);
            }
            fx(u, water ? "race_whirlpool" : "race_tail_sweep", center.add(0, 0.4, 0), (float) r, 0, 0);
            return true;
        });
        // Siren Song: an enchanting voice — foes falter, beasts forget their prey.
        ability(R, "siren_song").unlock(10).cooldown(400).stamina(16).anim("cast_up").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 10)) {
                eff(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 1);
                eff(e, MobEffects.WEAKNESS, 120, 0);
                if (e instanceof Player) eff(e, MobEffects.CONFUSION, 80, 0);
                else if (!(e instanceof GLNpc n && n.tier() >= 8)) PowerKit.loseTrack(u, e);
            }
            fx(u, "race_song", u.position().add(0, 1.6, 0), 10, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.6f);
            return true;
        });
        // Call of the Sea: the voice that sea creatures obey. Every sea creature nearby (Guardians, Drowned, Sea Kings,
        // dolphins and fish) turns on your foe, and a school of fish rams it again and again.
        ability(R, "call_of_the_sea").unlock(25).cooldown(600).stamina(24).anim("cast_up").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 24);
            int answered = 0;
            for (Mob m : c.level.getEntitiesOfClass(Mob.class, u.getBoundingBox().inflate(32), m -> m != u && m != t && m.isAlive() && seaCreature(m))) {
                if (t != null && m.canAttack(t)) m.setTarget(t);
                else if (m.getTarget() == u) m.setTarget(null);
                answered++;
            }
            if (t == null && answered == 0) return noTarget(u);
            if (t != null) {
                int strikes = Math.min(9, 3 + answered + (u.isInWater() ? 2 : 0));
                float d = dmg(c, 2.5f);
                for (int i = 0; i < strikes; i++) {
                    later(u, 4 + i * 4, () -> {
                        if (!t.isAlive()) return;
                        Hits.hit(u, t, d, info(Element.WATER, 0.4f).withPoise(12));
                        fx(u, "race_fish_strike", t.position().add(0, t.getBbHeight() * 0.5, 0), 0, 0, 0);
                    });
                }
            }
            fx(u, "race_song", u.position().add(0, 1.6, 0), 24, 1, 0);
            PowerKit.sound(c, u.position(), SoundEvents.CONDUIT_ACTIVATE, 1.5f, 1.2f);
            return true;
        });
        // Mermaid's Embrace: share the sea — you and your allies breathe water and swim like merfolk.
        ability(R, "mermaid_embrace").unlock(40).cooldown(900).stamina(20).anim("cast_up").exec((u, c) -> {
            for (LivingEntity a : allies(u, 8)) {
                eff(a, MobEffects.WATER_BREATHING, 1800, 0);
                eff(a, MobEffects.DOLPHINS_GRACE, 600, 0);
                eff(a, MobEffects.REGENERATION, 100, 0);
                fx(u, "race_bubbles", a.position().add(0, 1, 0), 0, 0, 0);
            }
            return true;
        });
    }

    // ================================================================== Mink

    private static void mink() {
        Race R = Race.MINK;
        // Electro: charge the fur. A crackling burst now, and melee blows shock for the next ten seconds.
        ability(R, "electro").cooldown(300).stamina(14).anim("roar").exec((u, c) -> {
            boolean sulong = CombatState.of(u).hasBuff("sulong");
            double r = sulong ? 4 : 2.5;
            buff(u, "race_electro", 200);
            FruitKit.aoe(c, u.position().add(0, 1, 0), r, dmg(c, sulong ? 5 : 3), info(Element.LIGHTNING, 0.8f).withStun(10).withPoise(25));
            fx(u, "discharge", u.position().add(0, 1, 0), (float) r, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.8f);
            return true;
        });
        // Electro Pounce: a lightning-fast leap onto the prey, claws crackling.
        ability(R, "electro_pounce").unlock(10).cooldown(90).stamina(16).anim("pounce").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            Vec3 dir = t != null ? t.position().subtract(u.position()) : PowerKit.flatAim(c).scale(6);
            double dist = Math.max(0, dir.length() - 1.4);
            if (dist > 0.3) FruitKit.blink(u, dir.multiply(1, 0.2, 1), dist);
            FruitKit.aoe(c, u.position().add(0, 1, 0), 2.8, dmg(c, 7), info(Element.LIGHTNING, 1.2f).withStun(20).withPoise(45));
            fx(u, "lightning_trail", u.position().add(0, 1, 0), (float) (u.getX() - dir.x), (float) u.getY() + 1, (float) (u.getZ() - dir.z));
            fx(u, "race_electro_hit", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        });
        // Sulong: under a full moon a Mink's eyes and fur turn white and their power explodes. Only at night, with the
        // full moon in view — and the body pays for it when the form fades.
        ability(R, "sulong").unlock(25).cooldown(1200).stamina(10).anim("roar").exec((u, c) -> {
            if (!fullMoonVisible(c.level, u.blockPosition())) {
                tell(u, "grandline.sulong.need_moon");
                return false;
            }
            int ticks = 20 * 60;
            buff(u, "sulong", ticks);
            buff(u, "race_electro", ticks);
            eff(u, MobEffects.DAMAGE_BOOST, ticks, 1);
            eff(u, MobEffects.MOVEMENT_SPEED, ticks, 1);
            eff(u, MobEffects.JUMP, ticks, 1);
            eff(u, MobEffects.DAMAGE_RESISTANCE, ticks, 0);
            u.heal(6);
            fx(u, "sulong", u.position().add(0, 1, 0), 0, 0, 0);
            fx(u, "race_moon_roar", u.position().add(0, 1, 0), 0, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.WOLF_HOWL, 1.5f, 0.7f);
            return true;
        });
        // Electro Storm: release everything at once; lightning arcs on to further foes (wider and fiercer in Sulong).
        ability(R, "electro_storm").unlock(45).cooldown(400).stamina(30).anim("cast_ground").exec((u, c) -> {
            boolean sulong = CombatState.of(u).hasBuff("sulong");
            double r = sulong ? 8 : 5;
            float d = dmg(c, sulong ? 12 : 9);
            Vec3 center = u.position().add(0, 1, 0);
            java.util.List<LivingEntity> hit = new java.util.ArrayList<>();
            for (LivingEntity e : PowerKit.foes(u, center, r)) if (PowerKit.hit(u, e, d, info(Element.LIGHTNING, 1.4f).withStun(30).withPoise(60))) hit.add(e);
            int arcs = 0;
            for (LivingEntity from : hit) {
                for (LivingEntity e : PowerKit.foes(u, from.position(), 6)) {
                    if (hit.contains(e) || arcs >= 3) continue;
                    if (PowerKit.hit(u, e, d * 0.5f, info(Element.LIGHTNING, 0.6f).withStun(15))) {
                        fx(u, "lightning_trail", from.position().add(0, 1, 0), (float) e.getX(), (float) e.getY() + 1, (float) e.getZ());
                        arcs++;
                    }
                }
            }
            fx(u, "discharge", center, (float) r, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
            return true;
        });
    }

    // ================================================================== Sky People

    private static void skyPeople() {
        Race R = Race.SKY_PEOPLE;
        // Jet Dial: a blast of jet wind — a dash, or straight up when looking up.
        ability(R, "jet_dial").cooldown(60).stamina(10).anim("dash").exec((u, c) -> {
            if (c.look().y > 0.55) {
                FruitKit.impulse(u, new Vec3(c.look().x * 0.3, 1.3, c.look().z * 0.3), true);
                eff(u, MobEffects.SLOW_FALLING, 60, 0);
                fx(u, "race_jet", u.position(), (float) u.getX(), (float) u.getY() - 4, (float) u.getZ());
            } else {
                dashHit(u, c, c.look().multiply(1, 0.2, 1), 9, dmg(c, 4), info(Element.WIND, 1.2f).withPoise(30), "race_jet");
            }
            return true;
        });
        // Flame Dial: the stored heat of the sun, released in a roaring jet.
        ability(R, "flame_dial").unlock(8).cooldown(80).stamina(12).anim("cast_forward").exec((u, c) -> {
            FruitKit.cone(c, 5.5, 40, dmg(c, 6), info(Element.FIRE, 0.8f).withPoise(25));
            Vec3 d = c.look();
            fx(u, "race_flame_jet", c.eye(), (float) d.x, (float) d.y, (float) d.z);
            PowerKit.sound(c, u.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.9f);
            return true;
        });
        // Impact Dial: release every blow the dial has soaked up (the dial absorbs part of each hit you take).
        // The recoil hurts the user a little.
        ability(R, "impact_dial").unlock(20).cooldown(120).stamina(16).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 3.8 * Math.max(1, u.getScale()));
            if (t == null) return noTarget(u);
            float stored = RacePowers.takeImpact(u);
            PowerKit.hit(u, t, dmg(c, 7) + stored * 1.5f, info(Element.IMPACT, 2.6f).withPoise(80).asUnblockable());
            float recoil = 1f + stored * 0.1f;
            if (u.getHealth() > recoil + 1) u.hurt(u.damageSources().generic(), recoil);
            fx(u, "race_impact", t.position().add(0, t.getBbHeight() * 0.6, 0), 1 + stored / 10f, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.6f);
            return true;
        });
        // Reject Dial: ten times an Impact Dial — devastating, and it can break the user's own arm.
        ability(R, "reject_dial").unlock(45).cooldown(900).stamina(30).anim("double_palm").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 4 * Math.max(1, u.getScale()));
            if (t == null) return noTarget(u);
            float stored = RacePowers.takeImpact(u);
            PowerKit.hit(u, t, dmg(c, 20) + stored * 2f, info(Element.IMPACT, 4f).withStun(50).withPoise(150).asUnblockable());
            float recoil = u.getMaxHealth() * 0.2f;
            if (u.getHealth() > recoil + 1) u.hurt(u.damageSources().generic(), recoil);
            eff(u, MobEffects.MOVEMENT_SLOWDOWN, 100, 1);
            eff(u, MobEffects.WEAKNESS, 100, 0);
            fx(u, "race_impact", t.position().add(0, t.getBbHeight() * 0.6, 0), 4, 1, 0);
            PowerKit.sound(c, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
            return true;
        });
    }

    // ================================================================== Long-Arm Tribe

    private static void longArm() {
        Race R = Race.LONG_ARM;
        // Long Grab: an arm with an extra joint snatches a foe from far beyond normal reach and yanks it in. Something
        // far bigger than you is not pulled — you are pulled to it instead.
        ability(R, "long_grab").cooldown(100).stamina(14).anim("grab").exec((u, c) -> {
            double reach = 9 * Math.max(1, u.getScale());
            LivingEntity t = PowerKit.target(c, reach);
            if (t == null || t.distanceTo(u) > reach) return noTarget(u);
            Vec3 from = t.position();
            Vec3 front = PowerKit.flatAim(c);
            if (t.getScale() > u.getScale() * 2.5f) {
                PowerKit.place(u, from.subtract(from.subtract(u.position()).multiply(1, 0, 1).normalize().scale(t.getBbWidth() * 0.5 + 1.2)));
            } else {
                PowerKit.place(t, u.position().add(front.scale(1.2 + u.getBbWidth() * 0.5 + t.getBbWidth() * 0.5)));
                t.setDeltaMovement(Vec3.ZERO);
            }
            PowerKit.hit(u, t, dmg(c, 3), info(Element.BLUNT, 0.2f).withStun(25).withPoise(40));
            fx(u, "race_long_arm", c.eye(), (float) from.x, (float) from.y + t.getBbHeight() * 0.6f, (float) from.z);
            return true;
        });
        // Extended Flurry: four rapid jabs that land from six blocks away.
        ability(R, "extended_flurry").unlock(10).cooldown(60).stamina(14).anim("gatling").exec((u, c) -> {
            float d = dmg(c, 2.5f);
            for (int i = 0; i < 4; i++) {
                later(u, i * 3, () -> {
                    if (!u.isAlive()) return;
                    FruitKit.line(c, 6.5 * Math.max(1, u.getScale()), 0.8, d, info(Element.BLUNT, 0.3f).withPoise(14));
                    Vec3 a = FruitKit.aimDir(c);
                    fx(u, "race_long_arm", c.eye(), (float) (c.eye().x + a.x * 6), (float) (c.eye().y + a.y * 6), (float) (c.eye().z + a.z * 6));
                });
            }
            return true;
        });
        // Windmill Sweep: both long arms spin like a windmill, clearing a wide circle.
        ability(R, "windmill_sweep").unlock(22).cooldown(120).stamina(18).anim("chain_spin").exec((u, c) -> {
            double r = 5.5 * Math.max(1, u.getScale());
            FruitKit.aoe(c, u.position().add(0, 1, 0), r, dmg(c, 6), info(Element.BLUNT, 2.2f).withPoise(45));
            fx(u, "race_windmill", u.position().add(0, 1.2, 0), (float) r, 0, 0);
            return true;
        });
        // High Snatch: pluck the weapon right out of a foe's hand from afar (NPCs get it back after ten seconds;
        // players' grip is numbed instead).
        ability(R, "high_snatch").unlock(40).cooldown(300).stamina(16).anim("throw_grab").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 7 * Math.max(1, u.getScale()));
            if (t == null) return noTarget(u);
            PowerKit.hit(u, t, dmg(c, 3), info(Element.BLUNT, 0.3f).withStun(15));
            if (t instanceof Mob m && !m.getMainHandItem().isEmpty()) {
                ItemStack held = m.getMainHandItem().copy();
                m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                CanonKit.later(u, m, 200, () -> {
                    if (m.isAlive() && m.getMainHandItem().isEmpty()) m.setItemSlot(EquipmentSlot.MAINHAND, held);
                });
            } else {
                eff(t, MobEffects.WEAKNESS, 120, 1);
                eff(t, MobEffects.DIG_SLOWDOWN, 120, 1);
            }
            fx(u, "race_long_arm", c.eye(), (float) t.getX(), (float) t.getY() + t.getBbHeight() * 0.6f, (float) t.getZ());
            return true;
        });
    }

    // ================================================================== Long-Leg Tribe

    private static void longLeg() {
        Race R = Race.LONG_LEG;
        // Stilt Kick: a kick from a leg as long as a spear.
        ability(R, "stilt_kick").cooldown(40).stamina(10).anim("kick_high").exec((u, c) -> {
            double reach = 6.5 * Math.max(1, u.getScale() * 0.85);
            FruitKit.cone(c, reach, 45, dmg(c, 6), info(Element.BLUNT, 2f).withPoise(40));
            fx(u, "swing_arc", c.eye().add(c.look().scale(reach * 0.6)), (float) reach, 45, 0);
            return true;
        });
        // Sky Vault: one bound of those legs clears a house.
        ability(R, "sky_vault").unlock(8).cooldown(80).stamina(12).anim("vault").exec((u, c) -> {
            Vec3 f = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(f.x * 0.9, 1.35, f.z * 0.9), true);
            eff(u, MobEffects.SLOW_FALLING, 50, 0);
            fx(u, "race_vault", u.position(), 0, 0, 0);
            return true;
        });
        // Crescent Sweep: a low spinning sweep that takes the legs out from everyone around.
        ability(R, "crescent_sweep").unlock(18).cooldown(110).stamina(16).anim("spin_kick").exec((u, c) -> {
            double r = 5 * Math.max(1, u.getScale() * 0.85);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), r)) {
                if (PowerKit.hit(u, e, dmg(c, 5), info(Element.BLUNT, 0.8f).withPoise(50))) {
                    eff(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
                    FruitKit.impulse(e, new Vec3(0, 0.35, 0), false);
                }
            }
            fx(u, "shockwave", u.position().add(0, 0.3, 0), (float) r, 0, 0);
            return true;
        });
        // Heaven Drop: leap high and bring a heel down like a falling mast.
        ability(R, "heaven_drop").unlock(35).cooldown(220).stamina(22).anim("axe_kick").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            Vec3 f = t != null ? t.position().subtract(u.position()).multiply(1, 0, 1).scale(0.09) : PowerKit.flatAim(c).scale(0.5);
            FruitKit.impulse(u, new Vec3(f.x, 1.0, f.z), true);
            later(u, 16, () -> {
                if (!u.isAlive()) return;
                Vec3 p = u.position();
                for (LivingEntity e : PowerKit.foes(u, p.add(0, 0.5, 0), 3.5))
                    PowerKit.hit(u, e, dmg(c, 12), info(Element.BLUNT, 2f).withStun(30).withPoise(80));
                fx(u, "shockwave", p.add(0, 0.2, 0), 3.5f, 0, 0);
                u.fallDistance = 0;
            });
            return true;
        });
    }

    // ================================================================== Three-Eye Tribe

    private static void threeEye() {
        Race R = Race.THREE_EYE;
        // Foresight: the third eye sees a moment ahead — every attack in the next few seconds is already dodged.
        ability(R, "foresight").cooldown(360).stamina(12).anim("focus").exec((u, c) -> {
            buff(u, "race_foresight", 40 + c.mastery / 4);
            fx(u, "race_third_eye", u.getEyePosition(), 0, 0, 0);
            return true;
        });
        // Piercing Gaze: read a foe's every weakness — they glow and take 20% more damage from everyone.
        ability(R, "piercing_gaze").unlock(12).cooldown(200).stamina(10).anim("point").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 20);
            if (t == null) return noTarget(u);
            buff(t, "race_exposed", 160);
            eff(t, MobEffects.GLOWING, 160, 0);
            fx(u, "race_gaze", c.eye(), (float) t.getX(), (float) t.getEyeY(), (float) t.getZ());
            return true;
        });
        // Precognitive Counter: wait for the blow you already saw — dodge it and strike back in the same instant.
        ability(R, "precognitive_counter").unlock(25).cooldown(300).stamina(16).anim("sway").exec((u, c) -> {
            buff(u, "race_counter", 60);
            fx(u, "race_third_eye", u.getEyePosition(), 1, 0, 0);
            return true;
        });
    }

    // ================================================================== Tontatta

    private static void tontatta() {
        Race R = Race.TONTATTA;
        // Blur Dash: too small and too fast to follow — dart through the enemy line.
        ability(R, "blur_dash").cooldown(40).stamina(10).anim("dash").exec((u, c) -> {
            dashHit(u, c, FruitKit.aimDir(c).multiply(1, 0.15, 1), 7, dmg(c, 3), info(Element.BLUNT, 0.6f).withPoise(20), "race_blur");
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            return true;
        });
        // Vanish: hide in plain sight. Invisible, forgotten, and the first strike from hiding hits 60% harder.
        ability(R, "vanish").unlock(8).cooldown(400).stamina(12).anim("dodge_back").exec((u, c) -> {
            eff(u, MobEffects.INVISIBILITY, 160, 0);
            eff(u, MobEffects.MOVEMENT_SPEED, 160, 1);
            buff(u, "race_hidden", 160);
            for (Mob m : c.level.getEntitiesOfClass(Mob.class, u.getBoundingBox().inflate(24), m -> m.getTarget() == u)) m.setTarget(null);
            fx(u, "race_vanish", u.position().add(0, 0.3, 0), 0, 0, 0);
            return true;
        });
        // Mighty Lift: tiny, yet strong enough to hoist a giant overhead and hurl it.
        ability(R, "mighty_lift").unlock(18).cooldown(160).stamina(18).anim("throw_grab").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 3.5);
            if (t == null) return noTarget(u);
            if (!PowerKit.hit(u, t, dmg(c, 6), info(Element.BLUNT, 0f).withStun(20).withPoise(60))) return true;
            Vec3 f = PowerKit.flatAim(c);
            FruitKit.impulse(t, new Vec3(f.x * 1.6, 0.95, f.z * 1.6), true);
            float d = dmg(c, 4);
            CanonKit.later(u, t, 14, () -> {
                if (t.isAlive() && u.isAlive()) {
                    Hits.hit(u, t, d, info(Element.BLUNT, 0.5f).withPoise(30));
                    fx(u, "shockwave", t.position(), 1.5f, 0, 0);
                }
            });
            fx(u, "race_lift", t.position().add(0, t.getBbHeight() * 0.5, 0), 0, 0, 0);
            return true;
        });
        // Tiny Fist Flurry: a hailstorm of little punches, each one far heavier than it looks.
        ability(R, "tiny_fist_flurry").unlock(35).cooldown(200).stamina(22).anim("gatling").exec((u, c) -> {
            float d = dmg(c, 1.6f);
            for (int i = 0; i < 8; i++) {
                later(u, i * 2, () -> {
                    if (!u.isAlive()) return;
                    FruitKit.cone(c, 3, 70, d, info(Element.BLUNT, 0.1f).withPoise(9));
                    fx(u, "race_flurry", c.eye().add(c.look().scale(1.2)), 0, 0, 0);
                });
            }
            return true;
        });
    }

    // ================================================================== Giant

    private static void giant() {
        Race R = Race.GIANT;
        // Giant Stomp: the ground jumps; anything much smaller than you is knocked off its feet.
        ability(R, "giant_stomp").cooldown(80).stamina(14).anim("ground_slam").exec((u, c) -> {
            double r = 3 + u.getScale() * 1.5;
            Vec3 p = u.position();
            for (LivingEntity e : PowerKit.foes(u, p.add(0, 0.5, 0), r)) {
                if (Math.abs(e.getY() - p.y) > 3) continue;
                boolean small = e.getScale() < u.getScale() * 0.6f;
                PowerKit.hit(u, e, dmg(c, 5), info(Element.BLUNT, 1f).withPoise(50).withStun(small ? 20 : 0));
            }
            fx(u, "shockwave", p.add(0, 0.2, 0), (float) r, 0, 0);
            PowerKit.sound(c, p, SoundEvents.RAVAGER_STEP, 2f, 0.5f);
            return true;
        });
        // Battle Cry of Elbaph: the warrior's roar — allies take heart, enemies lose theirs.
        ability(R, "battle_cry").unlock(10).cooldown(900).stamina(16).anim("roar").exec((u, c) -> {
            for (LivingEntity a : allies(u, 16)) {
                eff(a, MobEffects.DAMAGE_BOOST, 300, 0);
                eff(a, MobEffects.DAMAGE_RESISTANCE, 300, 0);
            }
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 10)) {
                if (e instanceof GLNpc n && n.tier() >= 8) continue;
                eff(e, MobEffects.WEAKNESS, 100, 0);
                eff(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            }
            fx(u, "race_war_cry", u.position().add(0, 1.5, 0), 16, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 2f, 0.6f);
            return true;
        });
        // Elbaph Smash: a two-handed hammer blow that craters the ground.
        ability(R, "elbaph_smash").unlock(22).cooldown(160).stamina(22).anim("giant_punch").exec((u, c) -> {
            double reach = 4 + u.getScale() * 1.5;
            FruitKit.cone(c, reach, 70, dmg(c, 11), info(Element.BLUNT, 3f).withPoise(90));
            Vec3 at = u.position().add(PowerKit.flatAim(c).scale(reach * 0.6));
            TerrainChanges.crater(c.level, BlockPos.containing(at).below(), 2, u);
            fx(u, "giant_fist", at.add(0, 0.5, 0), 2.5f, 0, 0);
            PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            return true;
        });
        // Boulder Hurl: rip up a slab of rock and throw it.
        ability(R, "boulder_hurl").unlock(38).cooldown(240).stamina(24).anim("throw").exec((u, c) -> {
            float d = dmg(c, 12);
            Hits.Info inf = info(Element.BLUNT, 2.5f).withPoise(80);
            AbilityProjectile p = FruitKit.shoot(c, "race_boulder", 1.1f, 1.5, d, inf);
            p.gravity = 0.025;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 2.5)) if (e != hit) PowerKit.hit(u, e, d * 0.6f, inf);
                fx(u, "race_rubble", pos, 2.5f, 0, 0);
            };
            return true;
        });
    }

    // ================================================================== Lunarian (NPC race)

    private static void lunarian() {
        Race R = Race.LUNARIAN;
        // Flame Wings: set the black wings alight. Melee attackers burn and the heat lifts you.
        ability(R, "flame_wings").cooldown(400).stamina(14).anim("cast_up").exec((u, c) -> {
            buff(u, "race_flame_wings", 300);
            eff(u, MobEffects.DAMAGE_RESISTANCE, 300, 0);
            eff(u, MobEffects.SLOW_FALLING, 100, 0);
            FruitKit.impulse(u, new Vec3(0, 0.6, 0), false);
            fx(u, "race_flame_wings", u.position().add(0, 1.4, 0), 0, 0, 0);
            return true;
        });
        // Blaze Burst: the Lunarian body pours out fire (hotter with the wings lit).
        ability(R, "blaze_burst").unlock(10).cooldown(90).stamina(14).anim("cast_forward").exec((u, c) -> {
            boolean lit = CombatState.of(u).hasBuff("race_flame_wings");
            FruitKit.cone(c, lit ? 7 : 6, 50, dmg(c, lit ? 9 : 7), info(Element.FIRE, 1f).withPoise(30));
            Vec3 d = c.look();
            fx(u, "race_flame_jet", c.eye(), (float) d.x, (float) d.y, (float) d.z);
            PowerKit.sound(c, u.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
            return true;
        });
        // Wing Dive: a burning dive from above onto the foe.
        ability(R, "wing_dive").unlock(22).cooldown(160).stamina(20).anim("rocket").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            Vec3 goal = t != null ? t.position() : FruitKit.aimPoint(c, 10);
            FruitKit.impulse(u, new Vec3(0, 0.9, 0), true);
            CanonKit.later(u, t, 10, () -> {
                if (!u.isAlive()) return;
                Vec3 from = u.position();
                Vec3 dir = goal.subtract(from);
                if (dir.length() > 0.5) FruitKit.blink(u, dir, Math.max(0, dir.length() - 1));
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 3))
                    PowerKit.hit(u, e, dmg(c, 10), info(Element.FIRE, 2f).withPoise(60));
                fx(u, "fire_trail", from.add(0, 1, 0), (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
                fx(u, "fire_ring", u.position().add(0, 0.3, 0), 3, 0, 0);
            });
            return true;
        });
        // Lunarian Fortitude: the legendary Lunarian body — almost nothing gets through for six seconds.
        ability(R, "lunarian_fortitude").unlock(40).cooldown(600).stamina(18).anim("iron_body").exec((u, c) -> {
            eff(u, MobEffects.DAMAGE_RESISTANCE, 120, 2);
            eff(u, MobEffects.MOVEMENT_SLOWDOWN, 120, 0);
            u.clearFire();
            fx(u, "race_fortitude", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        });
    }

    // ================================================================== Buccaneer (NPC race)

    private static void buccaneer() {
        Race R = Race.BUCCANEER;
        // Bull Rush: a charging wall of muscle.
        ability(R, "bull_rush").cooldown(70).stamina(14).anim("dash").exec((u, c) -> {
            dashHit(u, c, c.look().multiply(1, 0.15, 1), 7, dmg(c, 6), info(Element.BLUNT, 3f).withPoise(60), "dash_line");
            return true;
        });
        // Bone Crusher: seize a foe and squeeze.
        ability(R, "bone_crusher").unlock(12).cooldown(140).stamina(18).anim("grab").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 3.5 * Math.max(1, u.getScale()));
            if (t == null) return noTarget(u);
            CanonKit.disable(t, 30, 2);
            float d = dmg(c, 3.5f);
            for (int i = 0; i < 3; i++) {
                CanonKit.later(u, t, 2 + i * 8, () -> {
                    if (t.isAlive() && u.isAlive()) Hits.hit(u, t, d, info(Element.BLUNT, 0f).withPoise(30).asUnblockable());
                });
            }
            fx(u, "race_lift", t.position().add(0, t.getBbHeight() * 0.5, 0), 0, 0, 0);
            return true;
        });
        // Ground Breaker: a fist into the earth that tears a fissure toward the enemy.
        ability(R, "ground_breaker").unlock(25).cooldown(180).stamina(22).anim("slam").exec((u, c) -> {
            Vec3 f = PowerKit.flatAim(c);
            Vec3 from = u.position().add(0, 0.5, 0);
            for (LivingEntity e : Hits.line(u, from, f, 9, 1.4))
                if (PowerKit.hit(u, e, dmg(c, 9), info(Element.IMPACT, 2f).withPoise(70))) FruitKit.impulse(e, new Vec3(0, 0.6, 0), false);
            Vec3 to = from.add(f.scale(9));
            fx(u, "race_fissure", from, (float) to.x, (float) to.y, (float) to.z);
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.5f);
            return true;
        });
        // Unchained: the strength of the Buccaneer bloodline, unleashed.
        ability(R, "unchained").unlock(45).cooldown(900).stamina(20).anim("roar").exec((u, c) -> {
            buff(u, "race_rage", 240);
            eff(u, MobEffects.DAMAGE_BOOST, 240, 0);
            eff(u, MobEffects.DAMAGE_RESISTANCE, 240, 0);
            fx(u, "race_rage", u.position().add(0, 1, 0), 0, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.8f);
            return true;
        });
    }

    // ================================================================== Snakeneck Tribe (NPC race)

    private static void snakeneck() {
        Race R = Race.SNAKENECK;
        // Neck Lash: the long neck whips the head forward like a flail.
        ability(R, "neck_lash").cooldown(40).stamina(10).anim("headbutt").exec((u, c) -> {
            FruitKit.cone(c, 5.5, 30, dmg(c, 6), info(Element.BLUNT, 1.5f).withPoise(40));
            fx(u, "swing_arc", c.eye().add(c.look().scale(3)), 5.5f, 30, 0);
            return true;
        });
        // Coil Strike: the neck coils and strikes like a serpent — a piercing bite at range.
        ability(R, "coil_strike").unlock(12).cooldown(90).stamina(14).anim("pounce").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (LivingEntity e : Hits.line(u, c.eye(), dir, 7, 0.8)) {
                if (!Hits.hostileTo(u, e)) continue;
                if (PowerKit.hit(u, e, dmg(c, 8), info(Element.PIERCE, 0.8f).withPoise(40))) eff(e, MobEffects.POISON, 60, 0);
                break;
            }
            fx(u, "race_coil", c.eye(), (float) (c.eye().x + dir.x * 7), (float) (c.eye().y + dir.y * 7), (float) (c.eye().z + dir.z * 7));
            return true;
        });
        // Serpent Sway: the neck weaves like a cobra — half of all attacks miss for four seconds.
        ability(R, "serpent_sway").unlock(25).cooldown(300).stamina(14).anim("sway").exec((u, c) -> {
            buff(u, "race_sway", 80);
            fx(u, "race_evade", u.position().add(0, 1.5, 0), Race.SNAKENECK.ordinal(), 0, 0);
            return true;
        });
    }

    private CanonRaceAbilities() {}
}
