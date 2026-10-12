package com.grandline.race;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.impl.CanonKit;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.fruit.impl.PowerKit;
import com.grandline.player.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static com.grandline.race.CanonRaceAbilities.allies;
import static com.grandline.race.CanonRaceAbilities.dashHit;
import static com.grandline.race.CanonRaceAbilities.noTarget;
import static com.grandline.race.RaceKit.*;

/**
 * Active abilities of the eight original races. Each kit grows out of the race's body and existing traits:
 * coral and tides (Reefborn), wind and lightning (Stormwing), living stone (Stonehide), the lightless deep (Abyssal),
 * volcanic heat (Emberkin), roots and sunlight (Vinefolk), whale-song and sheer mass (Whalefolk), starlight (Starborn).
 */
final class OriginalRaceAbilities {

    static void register() {
        reefborn();
        stormwing();
        stonehide();
        abyssal();
        emberkin();
        vinefolk();
        whalefolk();
        starborn();
    }

    // ================================================================== Reefborn — coral-skinned water-breathers

    private static void reefborn() {
        Race R = Race.REEFBORN;
        // Coral Spikes: coral bursts from the ground around you, piercing and pinning foes in place.
        ability(R, "coral_spikes").cooldown(70).stamina(12).anim("cast_ground").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 4))
                if (PowerKit.hit(u, e, dmg(c, 5), info(Element.PIERCE, 0.3f).withPoise(35))) eff(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 3);
            fx(u, "race_coral", u.position().add(0, 0.2, 0), 4, 0, 0);
            return true;
        });
        // Reef Shell: grow a coral carapace (and knit wounds while submerged).
        ability(R, "reef_shell").unlock(10).cooldown(400).stamina(14).anim("harden").exec((u, c) -> {
            eff(u, MobEffects.ABSORPTION, 200, 1);
            eff(u, MobEffects.DAMAGE_RESISTANCE, 200, 0);
            if (u.isInWater()) eff(u, MobEffects.REGENERATION, 100, 1);
            fx(u, "race_coral", u.position().add(0, 1, 0), 1.2f, 1, 0);
            return true;
        });
        // Tidepool: a pool of living sea that heals allies standing in it (twice as fast in water) and slows foes.
        ability(R, "tidepool").unlock(22).cooldown(600).stamina(18).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position();
            for (int i = 0; i < 8; i++) {
                later(u, i * 20, () -> {
                    for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(4, 2, 4), LivingEntity::isAlive)) {
                        if (CanonRaceAbilities.ally(u, e)) e.heal(e.isInWater() ? 3f : 1.5f);
                        else if (Hits.hostileTo(u, e)) eff(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
                    }
                    fx(u, "race_tidepool", center.add(0, 0.15, 0), 4, 0, 0);
                });
            }
            return true;
        });
        // Coral Lance: a spear of sharpened coral that passes through a line of foes and lodges, slowing them.
        ability(R, "coral_lance").unlock(38).cooldown(120).stamina(16).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "race_coral_lance", 0.35f, 2.6, dmg(c, 9), info(Element.PIERCE, 0.8f).withPoise(40));
            p.pierce = true;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) eff(hit, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            };
            return true;
        });
    }

    // ================================================================== Stormwing — feathered storm-gliders

    private static void stormwing() {
        Race R = Race.STORMWING;
        // Gale Burst: one beat of the wings blows foes away and lifts you back out of reach.
        ability(R, "gale_burst").cooldown(50).stamina(10).anim("cast_forward").exec((u, c) -> {
            FruitKit.cone(c, 5.5, 70, dmg(c, 4), info(Element.WIND, 2.6f).withPoise(30));
            Vec3 f = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(-f.x * 0.5, 0.25, -f.z * 0.5), false);
            fx(u, "race_gale", c.eye(), (float) f.x, 0, (float) f.z);
            PowerKit.sound(c, u.position(), SoundEvents.PHANTOM_FLAP, 1.2f, 0.8f);
            return true;
        });
        // Updraft: ride a column of wind high into the air, flinging nearby foes up with you.
        ability(R, "updraft").unlock(8).cooldown(120).stamina(12).anim("cast_up").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 3.2))
                if (PowerKit.hit(u, e, dmg(c, 3), info(Element.WIND, 0.2f).withPoise(25))) FruitKit.impulse(e, new Vec3(0, 0.9, 0), false);
            FruitKit.impulse(u, new Vec3(0, 1.4, 0), true);
            eff(u, MobEffects.SLOW_FALLING, 100, 0);
            fx(u, "updraft", u.position(), 3, 0, 0);
            return true;
        });
        // Feather Volley: a fan of steel-hard flight feathers.
        ability(R, "feather_volley").unlock(18).cooldown(70).stamina(14).anim("throw").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = 0; i < 5; i++) {
                Vec3 d = PowerKit.yaw(dir, (i - 2) * 7);
                AbilityProjectile p = AbilityProjectile.shoot(u, "race_feather", 0.18f, c.eye().add(d.scale(0.6)).add(0, -0.15, 0), d, 3.0,
                        dmg(c, 3), info(Element.PIERCE, 0.3f).withPoise(12));
                p.life = 30;
            }
            return true;
        });
        // Thunderhead: call lightning down out of the sky onto a foe — wider and harder in a storm.
        ability(R, "thunderhead").unlock(35).cooldown(300).stamina(24).anim("point").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 24);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 24);
            boolean storm = c.level.isRainingAt(BlockPos.containing(at).above()) || c.level.isThundering();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(c.level);
            if (bolt != null) {
                bolt.moveTo(at.x, at.y, at.z);
                bolt.setVisualOnly(true);
                c.level.addFreshEntity(bolt);
            }
            double r = storm ? 4 : 2.5;
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), r))
                PowerKit.hit(u, e, dmg(c, storm ? 15 : 10), info(Element.LIGHTNING, 1f).withStun(25).withPoise(60));
            fx(u, "race_thunder", at.add(0, 0.2, 0), (float) r, 0, 0);
            return true;
        });
    }

    // ================================================================== Stonehide — living-stone mountain folk

    private static void stonehide() {
        Race R = Race.STONEHIDE;
        // Stone Skin: the plates lock together — far tougher, a little slower.
        ability(R, "stone_skin").cooldown(300).stamina(12).anim("harden").exec((u, c) -> {
            eff(u, MobEffects.DAMAGE_RESISTANCE, 120, 1);
            eff(u, MobEffects.MOVEMENT_SLOWDOWN, 120, 0);
            fx(u, "race_stone", u.position().add(0, 1, 0), 1, 0, 0);
            return true;
        });
        // Rockslide Charge: a stone body at full tilt.
        ability(R, "rockslide_charge").unlock(10).cooldown(90).stamina(16).anim("dash").exec((u, c) -> {
            dashHit(u, c, c.look().multiply(1, 0.15, 1), 6, dmg(c, 7), info(Element.BLUNT, 3f).withStun(15).withPoise(60), "race_rockslide");
            return true;
        });
        // Earthen Spine: stomp, and a ridge of stone spikes tears out of the ground toward the enemy (temporary).
        ability(R, "earthen_spine").unlock(22).cooldown(160).stamina(20).anim("ground_slam").exec((u, c) -> {
            Vec3 f = PowerKit.flatAim(c);
            Vec3 from = u.position().add(0, 0.5, 0);
            for (LivingEntity e : Hits.line(u, from, f, 9, 1.0))
                if (PowerKit.hit(u, e, dmg(c, 8), info(Element.PIERCE, 0.5f).withPoise(60))) FruitKit.impulse(e, new Vec3(0, 0.7, 0), false);
            for (int i = 2; i <= 9; i += 2) {
                Vec3 p = from.add(f.scale(i));
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(u.getY()), (int) Math.floor(p.z));
                if (g != null && !c.level.getBlockState(g).liquid()) PowerKit.placeFree(c.level, g.above(), Blocks.DRIPSTONE_BLOCK.defaultBlockState());
            }
            Vec3 to = from.add(f.scale(9));
            fx(u, "race_fissure", from, (float) to.x, (float) to.y, (float) to.z);
            PowerKit.sound(c, u.position(), SoundEvents.STONE_BREAK, 1.5f, 0.6f);
            return true;
        });
        // Living Fortress: root yourself like a cliff — 70% less damage, and melee attackers break themselves on you.
        ability(R, "living_fortress").unlock(40).cooldown(600).stamina(20).anim("iron_body").exec((u, c) -> {
            buff(u, "race_fortress", 160);
            eff(u, MobEffects.MOVEMENT_SLOWDOWN, 160, 5);
            fx(u, "race_stone", u.position().add(0, 1, 0), 1.6f, 1, 0);
            PowerKit.sound(c, u.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
            return true;
        });
    }

    // ================================================================== Abyssal — glowing-eyed deep-sea people

    private static void abyssal() {
        Race R = Race.ABYSSAL;
        // Lure Light: the anglerfish lure — a hypnotic glow that draws a foe in, dazed.
        ability(R, "lure_light").cooldown(120).stamina(12).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 12);
            if (t == null) return noTarget(u);
            PowerKit.hit(u, t, dmg(c, 2), info(Element.LIGHT, 0f).withPoise(20));
            eff(t, MobEffects.CONFUSION, 80, 0);
            eff(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            PowerKit.pull(t, u.position(), 1.0, 0.1);
            fx(u, "race_lure", c.eye(), (float) t.getX(), (float) t.getEyeY(), (float) t.getZ());
            return true;
        });
        // Ink Cloud: a black cloud — foes are blinded and lose you; you slip away faster.
        ability(R, "ink_cloud").unlock(10).cooldown(300).stamina(12).anim("dodge_back").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 5)) {
                eff(e, MobEffects.BLINDNESS, 80, 0);
                PowerKit.loseTrack(u, e);
            }
            eff(u, MobEffects.MOVEMENT_SPEED, 60, 1);
            fx(u, "race_ink", u.position().add(0, 1, 0), 5, 0, 0);
            return true;
        });
        // Crushing Depths: the pressure of the deep sea, pressed onto one foe (heavier in water).
        ability(R, "crushing_depths").unlock(22).cooldown(140).stamina(18).anim("double_palm").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 8);
            if (t == null) return noTarget(u);
            float bonus = t.isInWater() || u.isInWater() ? 1.5f : 1f;
            if (PowerKit.hit(u, t, dmg(c, 9 * bonus), info(Element.WATER, 0.2f).withPoise(60).asUnblockable())) eff(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            fx(u, "race_pressure", t.position().add(0, t.getBbHeight() * 0.5, 0), t.getBbWidth() + 0.5f, 0, 0);
            return true;
        });
        // Abyssal Maw: from darkness or water, appear behind the prey and bite; in the light it is only a lunge.
        ability(R, "abyssal_maw").unlock(40).cooldown(240).stamina(22).anim("pounce").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return noTarget(u);
            boolean dark = c.level.getMaxLocalRawBrightness(t.blockPosition()) < 8 || t.isInWater() || u.isInWater();
            Vec3 start = u.position();
            if (dark) {
                Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(t.getBbWidth() * 0.5 + 1));
                PowerKit.place(u, behind);
            } else {
                Vec3 d = t.position().subtract(u.position());
                if (d.length() > 2) FruitKit.blink(u, d, d.length() - 1.5);
            }
            if (PowerKit.hit(u, t, dmg(c, dark ? 12 : 9), info(Element.PIERCE, 0.8f).withPoise(70))) eff(t, MobEffects.WITHER, 60, 0);
            fx(u, "race_maw", t.position().add(0, t.getBbHeight() * 0.6, 0), dark ? 1 : 0, 0, 0);
            fx(u, "race_ink", start.add(0, 1, 0), 1, 0, 0);
            return true;
        });
    }

    // ================================================================== Emberkin — volcanic islanders

    private static void emberkin() {
        Race R = Race.EMBERKIN;
        // Ember Flare: the ember veins flare and scorch everything close.
        ability(R, "ember_flare").cooldown(60).stamina(10).anim("roar").exec((u, c) -> {
            FruitKit.aoe(c, u.position().add(0, 1, 0), 3.5, dmg(c, 5), info(Element.FIRE, 0.8f).withPoise(25));
            fx(u, "race_ember", u.position().add(0, 1, 0), 3.5f, 0, 0);
            return true;
        });
        // Magma Horns: lower the horns and charge, trailing fire.
        ability(R, "magma_horns").unlock(10).cooldown(90).stamina(14).anim("headbutt").exec((u, c) -> {
            dashHit(u, c, c.look().multiply(1, 0.15, 1), 6, dmg(c, 7), info(Element.FIRE, 2f).withPoise(50), "fire_trail");
            return true;
        });
        // Cinder Shot: flick a ball of molten cinder.
        ability(R, "cinder_shot").unlock(20).cooldown(50).stamina(12).anim("throw").exec((u, c) -> {
            FruitKit.shoot(c, "fire_bullet", 0.4f, 2.4, dmg(c, 7), info(Element.FIRE, 0.6f).withPoise(25));
            return true;
        });
        // Eruption: the ground under the foe glows, then bursts in a column of magma.
        ability(R, "eruption").unlock(40).cooldown(300).stamina(26).anim("ground_slam").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 18);
            fx(u, "race_eruption_warn", at.add(0, 0.1, 0), 2.5f, 0, 0);
            later(u, 15, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 2.8))
                    if (PowerKit.hit(u, e, dmg(c, 13), info(Element.MAGMA, 0.5f).withPoise(70))) FruitKit.impulse(e, new Vec3(0, 1.0, 0), false);
                fx(u, "race_eruption", at, 2.8f, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.7f);
            });
            return true;
        });
    }

    // ================================================================== Vinefolk — plant-blooded people

    private static void vinefolk() {
        Race R = Race.VINEFOLK;
        // Vine Lash: a whip of living vine that drags the first foe it catches toward you.
        ability(R, "vine_lash").cooldown(40).stamina(10).anim("whip_crack").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (LivingEntity e : Hits.line(u, c.eye(), dir, 7, 0.9)) {
                if (!Hits.hostileTo(u, e)) continue;
                if (PowerKit.hit(u, e, dmg(c, 5), info(Element.BLUNT, 0f).withPoise(30))) PowerKit.pull(e, u.position(), 0.9, 0.15);
                break;
            }
            fx(u, "race_vine", c.eye(), (float) (c.eye().x + dir.x * 7), (float) (c.eye().y + dir.y * 7), (float) (c.eye().z + dir.z * 7));
            return true;
        });
        // Entangle: roots burst from the ground and bind a foe's legs.
        ability(R, "entangle").unlock(10).cooldown(160).stamina(14).anim("cast_ground").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 12);
            if (t == null) return noTarget(u);
            PowerKit.hit(u, t, dmg(c, 3), info(Element.BLUNT, 0f).withPoise(30));
            eff(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 4);
            eff(t, MobEffects.WEAKNESS, 60, 0);
            t.setDeltaMovement(Vec3.ZERO);
            fx(u, "race_roots", t.position().add(0, 0.1, 0), t.getBbWidth() + 0.4f, t.getBbHeight(), 0);
            return true;
        });
        // Photosynthesis: drink in the light and heal — much more in full sun, little at night.
        ability(R, "photosynthesis").unlock(20).cooldown(500).stamina(0).anim("meditate").exec((u, c) -> {
            boolean sun = sunlit(u);
            float mult = sun ? 2f : night(c.level) ? 0.5f : 1f;
            u.heal(4f * mult);
            eff(u, MobEffects.REGENERATION, sun ? 100 : 60, sun ? 1 : 0);
            if (c.data != null) c.data.stamina = Math.min(c.data.maxStamina(), c.data.stamina + c.data.maxStamina() * 0.2f * mult);
            fx(u, "race_bloom", u.position().add(0, 1, 0), sun ? 1 : 0, 0, 0);
            return true;
        });
        // Thorn Burst: thorns erupt from the whole body; they are mildly poisonous.
        ability(R, "thorn_burst").unlock(38).cooldown(220).stamina(22).anim("roar").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 5))
                if (PowerKit.hit(u, e, dmg(c, 7), info(Element.PIERCE, 1f).withPoise(40))) eff(e, MobEffects.POISON, 60, 0);
            fx(u, "race_thorns", u.position().add(0, 1, 0), 5, 0, 0);
            return true;
        });
    }

    // ================================================================== Whalefolk — huge, gentle sea people

    private static void whalefolk() {
        Race R = Race.WHALEFOLK;
        // Blowhole Geyser: a roaring spout that throws everyone around you skyward.
        ability(R, "blowhole_geyser").cooldown(80).stamina(12).anim("cast_up").exec((u, c) -> {
            boolean water = u.isInWater();
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), water ? 4.5 : 3.2))
                if (PowerKit.hit(u, e, dmg(c, 5), info(Element.WATER, 0.2f).withPoise(35))) FruitKit.impulse(e, new Vec3(0, water ? 1.2 : 1.0, 0), false);
            fx(u, "race_geyser", u.position().add(0, u.getBbHeight(), 0), water ? 4.5f : 3.2f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_SPLASH, 1.5f, 0.5f);
            return true;
        });
        // Whale Song: a low song felt more than heard — allies mend and harden, foes grow sluggish.
        ability(R, "whale_song").unlock(10).cooldown(600).stamina(16).anim("meditate").exec((u, c) -> {
            for (LivingEntity a : allies(u, 12)) {
                a.heal(3);
                eff(a, MobEffects.DAMAGE_RESISTANCE, 200, 0);
            }
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 10)) eff(e, MobEffects.MOVEMENT_SLOWDOWN, 100, 0);
            fx(u, "race_whale_song", u.position().add(0, 1, 0), 12, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.CONDUIT_AMBIENT, 2f, 0.5f);
            return true;
        });
        // Breach Slam: breach like a whale and come down on everything below (bigger out of water).
        ability(R, "breach_slam").unlock(22).cooldown(200).stamina(22).anim("slam").exec((u, c) -> {
            boolean water = u.isInWater();
            FruitKit.impulse(u, new Vec3(0, water ? 1.4 : 0.9, 0), true);
            later(u, 14, () -> {
                if (!u.isAlive()) return;
                double r = water ? 6 : 4.5;
                Vec3 p = u.position();
                for (LivingEntity e : PowerKit.foes(u, p.add(0, 0.5, 0), r))
                    PowerKit.hit(u, e, dmg(c, 10), info(Element.BLUNT, 2.6f).withStun(20).withPoise(80));
                fx(u, "race_wave", p.add(0, 0.2, 0), (float) r, 0, 0);
                u.fallDistance = 0;
            });
            return true;
        });
        // Sounding Dive: a whale's dive — an unstoppable body-charge (any direction under water).
        ability(R, "sounding_dive").unlock(40).cooldown(160).stamina(20).anim("dash").exec((u, c) -> {
            boolean water = u.isInWater();
            dashHit(u, c, water ? FruitKit.aimDir(c) : c.look().multiply(1, 0.15, 1), water ? 14 : 10, dmg(c, 9), info(Element.BLUNT, 3.5f).withPoise(90), "race_riptide");
            return true;
        });
    }

    // ================================================================== Starborn — star-freckled wanderers

    private static void starborn() {
        Race R = Race.STARBORN;
        // Starfall: point at the sky; a shard of starlight falls where you point (brighter under the open night sky).
        ability(R, "starfall").cooldown(80).stamina(12).anim("point").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 20);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 20);
            float mult = starlit(u) ? 1.4f : 1f;
            fx(u, "race_star_warn", at.add(0, 0.1, 0), 2.8f, 0, 0);
            later(u, 12, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.8, 0), 2.8)) PowerKit.hit(u, e, dmg(c, 7 * mult), info(Element.LIGHT, 1f).withPoise(40));
                fx(u, "race_starfall", at, 2.8f, 0, 0);
            });
            return true;
        });
        // Astral Step: dissolve into stardust and re-form ten blocks away.
        ability(R, "astral_step").unlock(10).cooldown(60).stamina(10).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, FruitKit.aimDir(c).multiply(1, 0.3, 1), 10);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            fx(u, "race_stardust", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            return true;
        });
        // Constellation Bind: pin a foe between points of starlight — it cannot act for a moment.
        ability(R, "constellation_bind").unlock(22).cooldown(220).stamina(16).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return noTarget(u);
            PowerKit.hit(u, t, dmg(c, 3), info(Element.LIGHT, 0f).withPoise(30));
            CanonKit.disable(t, starlit(u) ? 40 : 30, 3);
            eff(t, MobEffects.GLOWING, 100, 0);
            fx(u, "race_constellation", t.position().add(0, t.getBbHeight() * 0.5, 0), t.getBbWidth() + 0.8f, t.getBbHeight(), 0);
            return true;
        });
        // Supernova: burn every star-freckle at once — a blinding blast that leaves you drained.
        ability(R, "supernova").unlock(45).cooldown(600).stamina(30).anim("cast_up").exec((u, c) -> {
            boolean lit = starlit(u);
            double r = lit ? 8 : 6;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), r)) {
                if (PowerKit.hit(u, e, dmg(c, lit ? 15 : 12), info(Element.LIGHT, 2f).withPoise(70))) eff(e, MobEffects.BLINDNESS, 60, 0);
            }
            eff(u, MobEffects.WEAKNESS, 100, 0);
            fx(u, "race_supernova", u.position().add(0, 1, 0), (float) r, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 1.8f);
            return true;
        });
    }

    private OriginalRaceAbilities() {}
}
