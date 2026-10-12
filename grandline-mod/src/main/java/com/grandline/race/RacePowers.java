package com.grandline.race;

import com.grandline.GrandLine;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.net.GLNetwork;
import com.grandline.player.CharacterData;
import com.grandline.player.Race;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Racial passives that need more than a status effect: swim speed, evasion (Three-Eye foresight, Snakeneck sway,
 * Tontatta size), incoming/outgoing damage traits (stone plating, coral thorns, Electro fur, flame wings, impact dial
 * storage...), and the buffs that race abilities leave behind. {@link #tryEvade} and {@link #modifyDamage} are called
 * from {@link Hits#hit} for every entity (players and NPCs); {@link #tick} runs for player characters.
 */
public final class RacePowers {
    private static final ResourceLocation SWIM = GrandLine.id("race_swim");
    /** Impact stored in a Sky Islander's Impact Dial (released by the Impact Dial technique). */
    private static final Map<LivingEntity, Float> IMPACT = Collections.synchronizedMap(new WeakHashMap<>());
    /** Players whose Sulong was up last second (to apply the exhaustion when it fades). */
    private static final Map<LivingEntity, Boolean> SULONG_WAS = Collections.synchronizedMap(new WeakHashMap<>());

    // ------------------------------------------------------------------ swimming

    /** Swim-speed multiplier bonus (ADD_MULTIPLIED_BASE on NeoForge's swim_speed) for sea races. */
    public static double swimBonus(Race r) {
        return switch (r) {
            case MERFOLK -> 1.6;     // unmatched: a mermaid outswims anything in the sea
            case FISHMAN -> 0.9;     // several times faster than a human under water
            case ABYSSAL -> 0.6;
            case REEFBORN -> 0.5;
            case WHALEFOLK -> 0.45;
            default -> 0;
        };
    }

    /** Apply (or clear) the racial swim-speed modifier. Cheap; called once a second. */
    public static void applySwim(LivingEntity e, Race r) {
        AttributeInstance inst = e.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (inst == null) return;
        double bonus = swimBonus(r);
        AttributeModifier cur = inst.getModifier(SWIM);
        if (bonus <= 0) {
            if (cur != null) inst.removeModifier(SWIM);
        } else if (cur == null || cur.amount() != bonus) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(SWIM, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // ------------------------------------------------------------------ player tick

    /** Per-tick racial passives for player characters (throttled internally). */
    public static void tick(ServerPlayer sp, CharacterData d) {
        if (sp.tickCount % 20 != 0) return;
        Race r = d.race;
        applySwim(sp, r);
        CombatState s = CombatState.of(sp);
        boolean sulong = s.hasBuff("sulong");
        if (Boolean.TRUE.equals(SULONG_WAS.put(sp, sulong)) && !sulong) {
            // the moon's power fades and leaves the body spent
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0, false, true));
            RaceKit.tell(sp, "grandline.race.sulong_end");
        }
        switch (r) {
            case HUMAN -> {
                // Indomitable: humans fight hardest with their backs to the wall
                if (sp.getHealth() < sp.getMaxHealth() * 0.3f) eff(sp, MobEffects.DAMAGE_RESISTANCE, 0);
            }
            case SKY_PEOPLE -> {
                // born above the clouds: light on their feet in thin air
                if (sp.level().dimension() == com.grandline.world.SkyWorld.LEVEL || sp.getY() > 150) {
                    eff(sp, MobEffects.MOVEMENT_SPEED, 0);
                    eff(sp, MobEffects.JUMP, 0);
                }
            }
            case LONG_LEG -> {
                if (sp.isSprinting()) eff(sp, MobEffects.MOVEMENT_SPEED, 0);
            }
            case STORMWING -> {
                // storm-borne: rain and thunder lift the feathers
                if (sp.level().isRainingAt(sp.blockPosition().above())) {
                    eff(sp, MobEffects.MOVEMENT_SPEED, 0);
                    eff(sp, MobEffects.JUMP, 0);
                }
            }
            case EMBERKIN -> {
                float temp = sp.level().getBiome(sp.blockPosition()).value().getBaseTemperature();
                if (temp >= 1.0f) eff(sp, MobEffects.DAMAGE_BOOST, 0);
                if (sp.isOnFire() || sp.isInLava()) eff(sp, MobEffects.REGENERATION, 0);
            }
            case GIANT, BUCCANEER -> {
                // their sheer size keeps them standing through most blows
                if (s.poise < 100) s.poise = Math.min(100, s.poise + 4);
            }
            default -> {}
        }
    }

    private static void eff(ServerPlayer sp, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int amp) {
        sp.addEffect(new MobEffectInstance(e, 60, amp, true, false));
    }

    // ------------------------------------------------------------------ evasion

    /**
     * Racial evasion, checked in {@link Hits#hit} right after Observation Haki. Three-Eye foresight sees the blow
     * before it lands; a Snakeneck's swaying neck and a Tontatta's size make them hard to hit.
     */
    public static boolean tryEvade(LivingEntity victim, LivingEntity attacker, Hits.Info info) {
        CombatState s = CombatState.of(victim);
        Race r = RaceKit.raceOf(victim);
        double chance = 0;
        boolean counter = false;
        if (s.hasBuff("race_counter")) {
            counter = true;
            chance = 1;
        } else if (s.hasBuff("race_foresight")) chance = 1;
        else if (s.hasBuff("race_sway")) chance = info.unblockable() ? 0.25 : 0.5;
        else if (!info.unblockable() && !s.blocking && r != null) {
            chance = switch (r) {
                case THREE_EYE -> 0.06 + mastery(victim, r) * 0.0009;   // up to 15%
                case SNAKENECK -> 0.07;
                case TONTATTA -> attacker.getScale() > victim.getScale() * 2 ? 0.12 : 0.04;
                default -> 0;
            };
        }
        if (chance <= 0 || victim.getRandom().nextDouble() >= chance) return false;
        Vec3 p = victim.position();
        GLNetwork.fx(victim, "race_evade", p.x, p.y + 1, p.z, r == null ? 0 : r.ordinal(), 0, 0);
        GLNetwork.anim(victim, victim.getRandom().nextBoolean() ? "dodge_left" : "dodge_right");
        if (counter) {
            // Precognitive Counter: the dodge flows straight into a strike at the attacker
            s.buffs.remove("race_counter");
            float dmg = Math.max(4f, Hits.power(victim) * 2.2f) * (1f + mastery(victim, r) / 100f);
            s.schedule(victim.level().getGameTime() + 2, () -> {
                if (!victim.isAlive() || !attacker.isAlive() || attacker.distanceTo(victim) > 7) return;
                Hits.hit(victim, attacker, dmg, Hits.Info.of(Element.BLUNT, 1.6f).withStun(25).withPoise(60).asUnblockable());
                Vec3 a = attacker.position();
                GLNetwork.fx(victim, "race_counter", a.x, a.y + 1, a.z, 0, 0, 0);
            });
            GLNetwork.anim(victim, "palm_strike");
        }
        Hits.train(victim, "race/" + (r == null ? "human" : r.id()), 1);
        return true;
    }

    private static int mastery(LivingEntity e, Race r) {
        if (r == null) return 0;
        if (e instanceof net.minecraft.world.entity.player.Player p) return com.grandline.player.Characters.get(p).mastery("race/" + r.id());
        if (e instanceof com.grandline.npc.GLNpc n) return Math.min(100, n.tier() * 10);
        return 0;
    }

    // ------------------------------------------------------------------ damage

    /**
     * Racial damage traits, applied in {@link Hits#hit} after the Devil Fruit modifiers. Attacker side first (buffs from
     * race techniques and racial strengths), then the victim's body (plating, blubber, flammable leaves...), then
     * retaliation that never recurses through {@link Hits#hit}.
     */
    public static float modifyDamage(LivingEntity attacker, LivingEntity victim, float damage, Hits.Info info) {
        if (damage <= 0) return damage;
        Race ar = RaceKit.raceOf(attacker), vr = RaceKit.raceOf(victim);
        CombatState as = CombatState.of(attacker), vs = CombatState.of(victim);
        Element el = info.element();
        boolean melee = attacker.distanceTo(victim) < 4.5 + attacker.getBbWidth() + victim.getBbWidth();
        boolean phys = el.physical();

        // ---- attacker ----
        if (as.hasBuff("race_hidden")) {
            // a Tontatta striking from hiding
            damage *= 1.6f;
            as.buffs.remove("race_hidden");
            attacker.removeEffect(MobEffects.INVISIBILITY);
            Vec3 p = victim.position();
            GLNetwork.fx(attacker, "race_ambush", p.x, p.y + 1, p.z, 0, 0, 0);
        }
        if (as.hasBuff("sulong")) damage *= 1.25f;
        if ((as.hasBuff("race_electro") || as.hasBuff("sulong") && ar == Race.MINK) && phys && melee) {
            damage *= 1.2f;
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            Vec3 p = victim.position();
            GLNetwork.fx(attacker, "race_electro_hit", p.x, p.y + victim.getBbHeight() * 0.6, p.z, 0, 0, 0);
        }
        if (as.hasBuff("race_rage")) damage *= 1.2f;
        if (ar != null) {
            switch (ar) {
                case LONG_ARM -> {
                    if (phys && attacker.distanceTo(victim) > 3.5) damage *= 1.1f;   // leverage of a long arm
                }
                case ABYSSAL -> {
                    if (attacker.level().getMaxLocalRawBrightness(attacker.blockPosition()) < 7) damage *= 1.12f;
                }
                case STARBORN -> {
                    if (RaceKit.starlit(attacker)) damage *= 1.1f;
                }
                case EMBERKIN -> {
                    if (phys && melee && attacker.getRandom().nextFloat() < 0.15f) victim.igniteForSeconds(2);
                }
                default -> {}
            }
        }

        // ---- victim ----
        if (vs.hasBuff("race_exposed")) damage *= 1.2f;
        if (vs.hasBuff("race_fortress")) {
            float before = damage;
            damage *= 0.3f;
            if (melee && phys) thorns(victim, attacker, before * 0.3f);
        }
        if (vs.hasBuff("race_flame_wings") && melee) attacker.igniteForSeconds(3);
        if (vr != null) {
            switch (vr) {
                case GIANT -> {
                    if (el == Element.PIERCE) damage *= 0.8f;                   // bullets and arrows are pinpricks
                }
                case LUNARIAN -> {
                    if (el == Element.FIRE || el == Element.MAGMA) damage *= 0.5f;
                }
                case BUCCANEER, WHALEFOLK -> {
                    if (el == Element.BLUNT || el == Element.IMPACT) damage *= 0.9f;
                    if (vr == Race.WHALEFOLK && el == Element.ICE) damage *= 0.5f; // blubber
                }
                case MERFOLK -> {
                    if (victim.isInWater()) damage *= 0.85f;
                }
                case STONEHIDE -> {
                    if (el == Element.SLASH || el == Element.PIERCE) damage *= 0.85f;
                    else if (el == Element.IMPACT) damage *= 1.1f;              // shock cracks stone
                }
                case STORMWING -> {
                    if (el == Element.LIGHTNING || el == Element.WIND) damage *= 0.5f;
                }
                case VINEFOLK -> {
                    if (el == Element.FIRE || el == Element.MAGMA) damage *= 1.25f;
                }
                case REEFBORN -> {
                    if (melee && phys) thorns(victim, attacker, 1f);           // coral skin
                }
                case MINK -> {
                    // Electro fur: touching a Mink can shock you
                    if (melee && phys && victim.getRandom().nextFloat() < (vs.hasBuff("race_electro") || vs.hasBuff("sulong") ? 0.6f : 0.2f)) {
                        thorns(victim, attacker, 1.5f);
                        Vec3 p = attacker.position();
                        GLNetwork.fx(victim, "race_electro_hit", p.x, p.y + attacker.getBbHeight() * 0.6, p.z, 0, 0, 0);
                    }
                }
                case SKY_PEOPLE -> {
                    // the Impact Dial on the arm soaks up part of every blow
                    if (el == Element.BLUNT || el == Element.IMPACT || el == Element.PHYSICAL) {
                        float soaked = damage * 0.15f;
                        damage -= soaked;
                        IMPACT.merge(victim, soaked * 2f, (x, y) -> Math.min(40f, x + y));
                    }
                }
                default -> {}
            }
        }
        // Human Last Stand: nothing short of a finishing blow drops them while it lasts
        // (capped with headroom for the Armament multiplier applied after this hook)
        if (vs.hasBuff("race_last_stand") && damage * 1.6f >= victim.getHealth() - 1f) damage = Math.max(0, (victim.getHealth() - 1f) / 1.6f);
        return damage;
    }

    private static void thorns(LivingEntity victim, LivingEntity attacker, float amount) {
        if (amount <= 0 || !attacker.isAlive()) return;
        attacker.invulnerableTime = 0;
        attacker.hurt(victim.damageSources().thorns(victim), amount);
    }

    /** Take (and empty) the impact stored in a Sky Islander's dial. */
    public static float takeImpact(LivingEntity e) {
        Float f = IMPACT.remove(e);
        return f == null ? 0 : f;
    }

    public static float storedImpact(LivingEntity e) {
        return IMPACT.getOrDefault(e, 0f);
    }

    private RacePowers() {}
}
